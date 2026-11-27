package com.lakhyadeep.papai.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakhyadeep.papai.data.local.db.DownloadStatus
import com.lakhyadeep.papai.data.local.db.ModelDao
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.data.local.prefs.SettingsRepository
import com.lakhyadeep.papai.data.remote.ActiveDownloadState
import com.lakhyadeep.papai.data.remote.HfGgufVariant
import com.lakhyadeep.papai.data.remote.HfModelInfo
import com.lakhyadeep.papai.data.remote.HuggingFaceRepository
import com.lakhyadeep.papai.data.remote.ModelDownloadManager
import com.lakhyadeep.papai.data.remote.ModelImportManager
import com.lakhyadeep.papai.domain.device.CompatibilityStatus
import com.lakhyadeep.papai.domain.device.DeviceCapabilities
import com.lakhyadeep.papai.domain.device.DeviceProfiler
import com.lakhyadeep.papai.domain.device.GgufInspector
import com.lakhyadeep.papai.domain.device.ModelRecommendation
import com.lakhyadeep.papai.domain.device.ModelRecommendationEngine
import com.lakhyadeep.papai.domain.inference.InferenceEngine
import com.lakhyadeep.papai.domain.inference.ModelLoadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class ModelsUiState(
    val localModels: List<ModelEntity> = emptyList(),
    val activeDownloads: Map<String, ActiveDownloadState> = emptyMap(),
    val loadState: ModelLoadState = ModelLoadState.Unloaded,
    val searchQuery: String = "",
    val hfSearchResults: List<HfModelInfo> = emptyList(),
    val isSearchingHf: Boolean = false,
    val selectedRepoDetails: HfModelInfo? = null,
    val isLoadingRepoDetails: Boolean = false,
    val importProgressText: String? = null,
    val offlineMode: Boolean = false,
    val deviceCapabilities: DeviceCapabilities? = null,
    val contextLength: Int = 4096,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val pendingMemoryWarning: PendingMemoryWarning? = null,
    val pendingDownloadConfirm: Pair<HfModelInfo, HfGgufVariant>? = null
)

class ModelsViewModel(
    private val modelDao: ModelDao,
    private val huggingFaceRepository: HuggingFaceRepository,
    private val downloadManager: ModelDownloadManager,
    private val importManager: ModelImportManager,
    private val inferenceEngine: InferenceEngine,
    private val deviceProfiler: DeviceProfiler,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val hfResults = MutableStateFlow<List<HfModelInfo>>(emptyList())
    private val isSearchingHf = MutableStateFlow(false)
    private val selectedRepo = MutableStateFlow<HfModelInfo?>(null)
    private val isLoadingRepo = MutableStateFlow(false)
    private val importProgress = MutableStateFlow<String?>(null)
    private val errorMsg = MutableStateFlow<String?>(null)
    private val statusMsg = MutableStateFlow<String?>(null)
    private val pendingMemWarning = MutableStateFlow<PendingMemoryWarning?>(null)
    private val pendingDownloadConfirm = MutableStateFlow<Pair<HfModelInfo, HfGgufVariant>?>(null)
    private val deviceCaps = MutableStateFlow(deviceProfiler.snapshot())

    val uiState: StateFlow<ModelsUiState> = combine(
        modelDao.observeAllModels(),
        downloadManager.activeDownloads,
        inferenceEngine.loadState,
        searchQuery,
        hfResults,
        isSearchingHf,
        selectedRepo,
        isLoadingRepo,
        importProgress,
        settingsRepository.preferencesFlow,
        errorMsg,
        statusMsg,
        pendingMemWarning,
        pendingDownloadConfirm
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val models = values[0] as List<ModelEntity>
        @Suppress("UNCHECKED_CAST")
        val downloads = values[1] as Map<String, ActiveDownloadState>
        val load = values[2] as ModelLoadState
        val query = values[3] as String
        @Suppress("UNCHECKED_CAST")
        val hf = values[4] as List<HfModelInfo>
        val searching = values[5] as Boolean
        val repo = values[6] as HfModelInfo?
        val loadingRepo = values[7] as Boolean
        val importTxt = values[8] as String?
        val prefs = values[9] as com.lakhyadeep.papai.data.local.prefs.AppPreferences
        val err = values[10] as String?
        val status = values[11] as String?
        val memWarn = values[12] as PendingMemoryWarning?
        @Suppress("UNCHECKED_CAST")
        val dlConfirm = values[13] as Pair<HfModelInfo, HfGgufVariant>?

        ModelsUiState(
            localModels = models,
            activeDownloads = downloads,
            loadState = load,
            searchQuery = query,
            hfSearchResults = hf,
            isSearchingHf = searching,
            selectedRepoDetails = repo,
            isLoadingRepoDetails = loadingRepo,
            importProgressText = importTxt,
            offlineMode = prefs.offlineMode,
            deviceCapabilities = deviceCaps.value,
            contextLength = prefs.inference.contextLength,
            errorMessage = err,
            statusMessage = status,
            pendingMemoryWarning = memWarn,
            pendingDownloadConfirm = dlConfirm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ModelsUiState()
    )

    fun refreshDeviceStats() {
        deviceCaps.value = deviceProfiler.snapshot()
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun searchHuggingFace(customQuery: String = searchQuery.value) {
        viewModelScope.launch {
            val prefs = settingsRepository.preferencesFlow.first()
            if (prefs.offlineMode) {
                errorMsg.value = "Offline Mode is enabled. Disable Offline Mode in Settings to search Hugging Face."
                return@launch
            }
            val caps = deviceProfiler.snapshot()
            deviceCaps.value = caps
            if (!caps.isNetworkConnected) {
                errorMsg.value = "No internet connection. Local downloaded/imported models continue to work offline."
                return@launch
            }

            isSearchingHf.value = true
            errorMsg.value = null
            val res = huggingFaceRepository.searchModels(customQuery)
            isSearchingHf.value = false

            res.onSuccess { list ->
                hfResults.value = list
                if (list.isEmpty()) {
                    statusMsg.value = "No GGUF repositories matched '$customQuery'."
                }
            }.onFailure { err ->
                errorMsg.value = "Hugging Face search failed: ${err.message ?: "Network error"}"
            }
        }
    }

    fun openRepoDetails(repo: HfModelInfo) {
        viewModelScope.launch {
            selectedRepo.value = repo
            isLoadingRepo.value = true
            errorMsg.value = null
            val res = huggingFaceRepository.fetchModelVariants(repo.repoId)
            isLoadingRepo.value = false
            res.onSuccess { detailed ->
                selectedRepo.value = detailed
            }.onFailure { err ->
                errorMsg.value = "Could not load GGUF files for ${repo.repoId}: ${err.message}"
            }
        }
    }

    fun closeRepoDetails() {
        selectedRepo.value = null
    }

    fun requestDownloadVariant(repo: HfModelInfo, variant: HfGgufVariant) {
        refreshDeviceStats()
        pendingDownloadConfirm.value = repo to variant
    }

    fun dismissDownloadConfirm() {
        pendingDownloadConfirm.value = null
    }

    fun confirmAndStartDownload(repo: HfModelInfo, variant: HfGgufVariant) {
        pendingDownloadConfirm.value = null
        viewModelScope.launch {
            val id = "${repo.repoId}/${variant.fileName}"
            val existing = modelDao.getModelById(id)
            val entity = existing?.copy(
                downloadUrl = variant.downloadUrl,
                fileSizeBytes = variant.fileSizeBytes.takeIf { it > 0L } ?: existing.fileSizeBytes,
                downloadError = null
            ) ?: ModelEntity(
                id = id,
                name = "${repo.displayName} (${variant.quantization})",
                repoId = repo.repoId,
                fileName = variant.fileName,
                fileSizeBytes = variant.fileSizeBytes,
                quantization = variant.quantization,
                parameterSizeLabel = GgufInspector.inferParamSizeFromText(repo.displayName),
                contextLength = 4096,
                architecture = "gguf",
                description = repo.description,
                capabilities = repo.capabilities,
                downloadUrl = variant.downloadUrl,
                isDownloaded = false,
                downloadStatus = DownloadStatus.DOWNLOADING.name
            )
            modelDao.upsertModel(entity)
            downloadManager.startOrResumeDownload(entity)
            selectedRepo.value = null
            statusMsg.value = "Download started for ${entity.name}."
        }
    }

    fun pauseDownload(modelId: String) = downloadManager.pauseDownload(modelId)
    fun resumeOrRetryDownload(model: ModelEntity) = downloadManager.startOrResumeDownload(model)
    fun cancelDownload(modelId: String) = downloadManager.cancelDownload(modelId)

    fun importLocalGguf(uri: Uri) {
        viewModelScope.launch {
            importProgress.value = "Validating and copying GGUF model..."
            errorMsg.value = null
            val res = importManager.importGgufFromUri(uri) { copied, total ->
                val pct = if (total > 0L) ((copied * 100L) / total).toInt() else 0
                importProgress.value = "Importing GGUF... $pct% (${ModelRecommendationEngine.formatBytes(copied)})"
            }
            importProgress.value = null
            res.onSuccess { model ->
                statusMsg.value = "Imported '${model.name}' successfully."
            }.onFailure { err ->
                errorMsg.value = err.message ?: "Failed to import GGUF model."
            }
        }
    }

    fun evaluateRecommendation(model: ModelEntity): ModelRecommendation {
        val caps = deviceCaps.value
        val ctx = uiState.value.contextLength
        return ModelRecommendationEngine.evaluateModel(model, ctx, caps)
    }

    fun evaluateVariant(variant: HfGgufVariant): ModelRecommendation {
        val caps = deviceCaps.value
        val ctx = uiState.value.contextLength
        return ModelRecommendationEngine.evaluate(
            modelSizeBytes = variant.fileSizeBytes,
            quantization = variant.quantization,
            contextLength = ctx,
            device = caps,
            isAlreadyDownloaded = false
        )
    }

    fun requestLoadModel(model: ModelEntity, forceLoad: Boolean = false) {
        val path = model.localPath
        if (path.isNullOrBlank()) {
            errorMsg.value = "Model file not found locally."
            return
        }
        val caps = deviceProfiler.snapshot()
        deviceCaps.value = caps
        val rec = ModelRecommendationEngine.evaluateModel(model, uiState.value.contextLength, caps)

        if (!forceLoad && (rec.status == CompatibilityStatus.MEMORY_WARNING ||
                rec.status == CompatibilityStatus.INSUFFICIENT_RAM ||
                rec.status == CompatibilityStatus.ARCH_UNSUPPORTED)
        ) {
            pendingMemWarning.value = PendingMemoryWarning(model, rec)
            return
        }

        pendingMemWarning.value = null
        viewModelScope.launch {
            val prefs = settingsRepository.preferencesFlow.first()
            val res = inferenceEngine.loadModel(model.id, model.name, path, prefs.inference)
            if (res.isSuccess) {
                modelDao.clearAllLoadedFlags()
                modelDao.markModelLoaded(model.id)
                statusMsg.value = "Loaded ${model.name} in ${res.getOrNull()} ms."
                errorMsg.value = null
            } else {
                errorMsg.value = res.exceptionOrNull()?.message ?: "Model loading failed."
            }
        }
    }

    fun dismissMemoryWarning() {
        pendingMemWarning.value = null
    }

    fun unloadModel() {
        viewModelScope.launch {
            inferenceEngine.unloadModel()
            modelDao.clearAllLoadedFlags()
            statusMsg.value = "Model unloaded from memory."
        }
    }

    fun renameModel(modelId: String, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            modelDao.renameModel(modelId, clean)
        }
    }

    fun deleteModel(model: ModelEntity) {
        viewModelScope.launch {
            val loadedId = (inferenceEngine.loadState.value as? ModelLoadState.Loaded)?.modelId
            if (loadedId == model.id) {
                inferenceEngine.unloadModel()
            }
            downloadManager.cancelDownload(model.id)
            model.localPath?.let { path ->
                runCatching { File(path).delete() }
            }
            modelDao.deleteModelById(model.id)
            statusMsg.value = "Deleted '${model.name}'."
        }
    }

    fun dismissError() {
        errorMsg.value = null
    }

    fun dismissStatus() {
        statusMsg.value = null
    }
}
