package com.lakhyadeep.papai.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.lakhyadeep.papai.data.local.db.DownloadStatus
import com.lakhyadeep.papai.data.local.db.ModelDao
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.domain.device.DeviceProfiler
import com.lakhyadeep.papai.domain.device.GgufInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

sealed class ImportProgress {
    data class Copying(val bytesCopied: Long, val totalBytes: Long) : ImportProgress()
    data class Completed(val model: ModelEntity) : ImportProgress()
    data class Failed(val reason: String) : ImportProgress()
}

class ModelImportManager(
    private val context: Context,
    private val modelDao: ModelDao,
    private val deviceProfiler: DeviceProfiler
) {

    suspend fun importGgufFromUri(
        uri: Uri,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): Result<ModelEntity> = withContext(Dispatchers.IO) {
        try {
            val (displayName, reportedSize) = queryUriMetadata(uri)
            val safeFileName = sanitizeFileName(displayName)

            val modelsDir = deviceProfiler.getModelsDirectory()
            val caps = deviceProfiler.snapshot()
            if (reportedSize > 0L && caps.availableStorageBytes < reportedSize + 100L * 1024L * 1024L) {
                return@withContext Result.failure(
                    IllegalStateException("Insufficient device storage to import $safeFileName.")
                )
            }

            // Validate GGUF magic bytes from the start of the stream before copying gigabytes
            context.contentResolver.openInputStream(uri)?.use { headerStream ->
                val magic = ByteArray(4)
                val n = headerStream.read(magic)
                if (n < 4 || !GgufInspector.hasValidMagicBytes(magic)) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Selected file is not a valid GGUF model (missing GGUF signature).")
                    )
                }
            } ?: return@withContext Result.failure(
                IllegalStateException("Cannot open selected file URI.")
            )

            var destFile = File(modelsDir, safeFileName)
            if (destFile.exists()) {
                val base = safeFileName.removeSuffix(".gguf")
                destFile = File(modelsDir, "${base}_${System.currentTimeMillis() % 10000}.gguf")
            }

            val partFile = File(modelsDir, "${destFile.name}.import.part")
            var totalCopied = 0L
            val buffer = ByteArray(64 * 1024)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(partFile).use { output ->
                    var lastReport = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        totalCopied += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport >= 250L) {
                            lastReport = now
                            onProgress(totalCopied, reportedSize.coerceAtLeast(totalCopied))
                        }
                    }
                    output.flush()
                }
            } ?: run {
                partFile.delete()
                return@withContext Result.failure(IllegalStateException("Failed to read source file stream."))
            }

            val ggufMeta = GgufInspector.inspectFile(partFile)
            if (!ggufMeta.isValidGguf) {
                partFile.delete()
                return@withContext Result.failure(
                    IllegalArgumentException(ggufMeta.errorReason ?: "Imported file is corrupted or unsupported.")
                )
            }

            if (!partFile.renameTo(destFile)) {
                partFile.delete()
                return@withContext Result.failure(IllegalStateException("Failed to save imported model to app storage."))
            }

            val cleanDisplay = (ggufMeta.modelName?.takeIf { it.isNotBlank() }
                ?: destFile.name.removeSuffix(".gguf"))
                .replace('_', ' ')
                .trim()

            val entity = ModelEntity(
                id = "local_${UUID.randomUUID()}",
                name = cleanDisplay,
                repoId = "local/imported",
                fileName = destFile.name,
                localPath = destFile.absolutePath,
                fileSizeBytes = destFile.length(),
                quantization = GgufInspector.inferQuantizationFromFilename(destFile.name),
                parameterSizeLabel = GgufInspector.inferParamSizeFromText(destFile.name),
                contextLength = ggufMeta.contextLength ?: 4096,
                architecture = ggufMeta.architecture ?: "llama",
                description = "Imported from device storage via Storage Access Framework.",
                capabilities = "Offline Chat • Local GGUF",
                downloadUrl = "",
                isDownloaded = true,
                isImported = true,
                isLoaded = false,
                downloadStatus = DownloadStatus.COMPLETED.name,
                downloadedBytes = destFile.length(),
                ggufVerified = true
            )

            modelDao.upsertModel(entity)
            onProgress(destFile.length(), destFile.length())
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun queryUriMetadata(uri: Uri): Pair<String, Long> {
        var name = "imported_model.gguf"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst()) {
                if (nameIdx >= 0) {
                    cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                    size = cursor.getLong(sizeIdx)
                }
            }
        }
        return name to size
    }

    private fun sanitizeFileName(raw: String): String {
        val cleaned = raw.substringAfterLast('/').replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (cleaned.endsWith(".gguf", ignoreCase = true)) cleaned else "$cleaned.gguf"
    }
}
