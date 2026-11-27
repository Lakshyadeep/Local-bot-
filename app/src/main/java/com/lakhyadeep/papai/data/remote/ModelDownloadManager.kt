package com.lakhyadeep.papai.data.remote

import com.lakhyadeep.papai.data.local.db.DownloadStatus
import com.lakhyadeep.papai.data.local.db.ModelDao
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.domain.device.DeviceProfiler
import com.lakhyadeep.papai.domain.device.GgufInspector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class ActiveDownloadState(
    val modelId: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val status: DownloadStatus,
    val supportsResume: Boolean,
    val error: String? = null
)

class ModelDownloadManager(
    private val modelDao: ModelDao,
    private val deviceProfiler: DeviceProfiler,
    private val tokenProvider: () -> String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()

    private val _activeDownloads = MutableStateFlow<Map<String, ActiveDownloadState>>(emptyMap())
    val activeDownloads: StateFlow<Map<String, ActiveDownloadState>> = _activeDownloads.asStateFlow()

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun startOrResumeDownload(model: ModelEntity) {
        if (model.downloadUrl.isBlank()) {
            scope.launch {
                modelDao.updateDownloadProgress(
                    id = model.id,
                    status = DownloadStatus.FAILED.name,
                    bytes = model.downloadedBytes,
                    error = "Missing download URL for this model."
                )
            }
            return
        }

        if (activeJobs[model.id]?.isActive == true) return

        val job = scope.launch {
            executeDownload(model)
        }
        activeJobs[model.id] = job
    }

    fun pauseDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        scope.launch {
            val current = modelDao.getModelById(modelId) ?: return@launch
            val partFile = getPartFile(current)
            val bytes = if (partFile.exists()) partFile.length() else current.downloadedBytes
            modelDao.updateDownloadProgress(
                id = modelId,
                status = DownloadStatus.PAUSED.name,
                bytes = bytes,
                error = null
            )
            updateDownloadState(
                ActiveDownloadState(
                    modelId = modelId,
                    bytesDownloaded = bytes,
                    totalBytes = current.fileSizeBytes,
                    speedBytesPerSec = 0L,
                    status = DownloadStatus.PAUSED,
                    supportsResume = true
                )
            )
        }
    }

    fun cancelDownload(modelId: String) {
        activeJobs.remove(modelId)?.cancel()
        scope.launch {
            val current = modelDao.getModelById(modelId)
            if (current != null) {
                getPartFile(current).delete()
                modelDao.updateDownloadProgress(
                    id = modelId,
                    status = DownloadStatus.CANCELLED.name,
                    bytes = 0L,
                    error = null
                )
            }
            removeDownloadState(modelId)
        }
    }

    private suspend fun executeDownload(initialModel: ModelEntity) {
        val model = modelDao.getModelById(initialModel.id) ?: initialModel
        val targetFile = getFinalFile(model)
        val partFile = getPartFile(model)
        targetFile.parentFile?.mkdirs()

        val existingBytes = if (partFile.exists()) partFile.length() else 0L
        val capabilities = deviceProfiler.snapshot()
        val remainingExpected = (model.fileSizeBytes - existingBytes).coerceAtLeast(0L)
        if (model.fileSizeBytes > 0L && capabilities.availableStorageBytes < remainingExpected + 100L * 1024L * 1024L) {
            val msg = "Insufficient storage space to download ${model.name}."
            modelDao.updateDownloadProgress(model.id, DownloadStatus.FAILED.name, existingBytes, msg)
            updateDownloadState(
                ActiveDownloadState(model.id, existingBytes, model.fileSizeBytes, 0L, DownloadStatus.FAILED, true, msg)
            )
            return
        }

        modelDao.updateDownloadProgress(model.id, DownloadStatus.DOWNLOADING.name, existingBytes, null)
        updateDownloadState(
            ActiveDownloadState(model.id, existingBytes, model.fileSizeBytes, 0L, DownloadStatus.DOWNLOADING, true)
        )

        try {
            val reqBuilder = Request.Builder()
                .url(model.downloadUrl)
                .header("User-Agent", "PapiAI-Android/1.0")
            val token = tokenProvider().trim()
            if (token.isNotEmpty()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }

            downloadClient.newCall(reqBuilder.build()).execute().use { response ->
                if (response.code == 401 || response.code == 403) {
                    throw IOException("Authentication required (${response.code}). Add a Hugging Face access token in Settings for gated models.")
                }
                if (!response.isSuccessful && response.code != 416) {
                    throw IOException("Server returned HTTP ${response.code}: ${response.message}")
                }

                // If server returned 416 (range not satisfiable), restart fresh
                val isPartial = response.code == 206
                val appendMode = isPartial && existingBytes > 0L
                if (!appendMode && partFile.exists()) {
                    partFile.delete()
                }

                val body = response.body ?: throw IOException("Empty response body from server.")
                val contentLength = body.contentLength()
                val totalBytes = when {
                    isPartial && contentLength > 0L -> existingBytes + contentLength
                    contentLength > 0L -> contentLength
                    else -> model.fileSizeBytes
                }

                var downloaded = if (appendMode) existingBytes else 0L
                val buffer = ByteArray(64 * 1024) // 64 KB streaming buffer - never loads file into RAM
                var lastDbUpdateMs = System.currentTimeMillis()
                var lastSpeedCheckMs = lastDbUpdateMs
                var bytesSinceSpeedCheck = 0L
                var currentSpeed = 0L

                body.byteStream().use { input ->
                    FileOutputStream(partFile, appendMode).use { output ->
                        while (scope.isActive) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            bytesSinceSpeedCheck += read

                            val now = System.currentTimeMillis()
                            val speedElapsed = now - lastSpeedCheckMs
                            if (speedElapsed >= 700L) {
                                currentSpeed = (bytesSinceSpeedCheck * 1000L) / speedElapsed.coerceAtLeast(1L)
                                lastSpeedCheckMs = now
                                bytesSinceSpeedCheck = 0L
                            }

                            if (now - lastDbUpdateMs >= 450L) {
                                lastDbUpdateMs = now
                                updateDownloadState(
                                    ActiveDownloadState(
                                        modelId = model.id,
                                        bytesDownloaded = downloaded,
                                        totalBytes = totalBytes,
                                        speedBytesPerSec = currentSpeed,
                                        status = DownloadStatus.DOWNLOADING,
                                        supportsResume = isPartial || response.header("Accept-Ranges") == "bytes"
                                    )
                                )
                                modelDao.updateDownloadProgress(
                                    id = model.id,
                                    status = DownloadStatus.DOWNLOADING.name,
                                    bytes = downloaded,
                                    error = null
                                )
                            }
                        }
                        output.flush()
                    }
                }

                // Verify GGUF signature and integrity
                val meta = GgufInspector.inspectFile(partFile)
                if (!meta.isValidGguf) {
                    partFile.delete()
                    throw IOException(meta.errorReason ?: "Downloaded file failed GGUF header verification.")
                }

                if (totalBytes > 0L && partFile.length() < totalBytes * 0.99) {
                    throw IOException("Incomplete download (${partFile.length()} of $totalBytes bytes). Tap Retry to resume.")
                }

                if (targetFile.exists()) targetFile.delete()
                if (!partFile.renameTo(targetFile)) {
                    throw IOException("Failed to finalize model file in app storage.")
                }

                val updatedModel = model.copy(
                    localPath = targetFile.absolutePath,
                    fileSizeBytes = targetFile.length(),
                    architecture = meta.architecture ?: model.architecture,
                    contextLength = meta.contextLength ?: model.contextLength,
                    isDownloaded = true,
                    downloadStatus = DownloadStatus.COMPLETED.name,
                    downloadedBytes = targetFile.length(),
                    downloadError = null,
                    ggufVerified = true
                )
                modelDao.upsertModel(updatedModel)
                removeDownloadState(model.id)
            }
        } catch (ce: CancellationException) {
            // Pause or cancel handled explicitly
            throw ce
        } catch (e: Exception) {
            val currentBytes = if (partFile.exists()) partFile.length() else existingBytes
            val message = e.message ?: "Download failed due to network error."
            modelDao.updateDownloadProgress(model.id, DownloadStatus.FAILED.name, currentBytes, message)
            updateDownloadState(
                ActiveDownloadState(
                    modelId = model.id,
                    bytesDownloaded = currentBytes,
                    totalBytes = model.fileSizeBytes,
                    speedBytesPerSec = 0L,
                    status = DownloadStatus.FAILED,
                    supportsResume = true,
                    error = message
                )
            )
        } finally {
            activeJobs.remove(model.id)
        }
    }

    private fun getFinalFile(model: ModelEntity): File {
        val safeName = model.fileName.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(deviceProfiler.getModelsDirectory(), safeName)
    }

    private fun getPartFile(model: ModelEntity): File {
        val finalFile = getFinalFile(model)
        return File(finalFile.parentFile, "${finalFile.name}.part")
    }

    private fun updateDownloadState(state: ActiveDownloadState) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            put(state.modelId, state)
        }
    }

    private fun removeDownloadState(modelId: String) {
        _activeDownloads.value = _activeDownloads.value.toMutableMap().apply {
            remove(modelId)
        }
    }
}
