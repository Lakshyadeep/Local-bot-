package com.lakhyadeep.papai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakhyadeep.papai.data.local.db.BenchmarkDao
import com.lakhyadeep.papai.data.local.db.BenchmarkEntity
import com.lakhyadeep.papai.data.local.db.ModelDao
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.data.local.prefs.SettingsRepository
import com.lakhyadeep.papai.domain.device.DeviceCapabilities
import com.lakhyadeep.papai.domain.device.DeviceProfiler
import com.lakhyadeep.papai.domain.device.ModelRecommendationEngine
import com.lakhyadeep.papai.domain.inference.InferenceEngine
import com.lakhyadeep.papai.domain.inference.InferenceStreamEvent
import com.lakhyadeep.papai.domain.inference.ModelLoadState
import com.lakhyadeep.papai.domain.inference.PromptFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class BenchmarkUiState(
    val benchmarks: List<BenchmarkEntity> = emptyList(),
    val downloadedModels: List<ModelEntity> = emptyList(),
    val loadState: ModelLoadState = ModelLoadState.Unloaded,
    val isRunning: Boolean = false,
    val progressLabel: String = "",
    val liveTps: Float = 0f,
    val deviceCapabilities: DeviceCapabilities? = null,
    val errorMessage: String? = null
)

class BenchmarkViewModel(
    private val benchmarkDao: BenchmarkDao,
    private val modelDao: ModelDao,
    private val inferenceEngine: InferenceEngine,
    private val deviceProfiler: DeviceProfiler,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val isRunning = MutableStateFlow(false)
    private val progressLabel = MutableStateFlow("")
    private val liveTps = MutableStateFlow(0f)
    private val errorMessage = MutableStateFlow<String?>(null)
    private val deviceCaps = MutableStateFlow(deviceProfiler.snapshot())

    val uiState: StateFlow<BenchmarkUiState> = combine(
        benchmarkDao.observeAllBenchmarks(),
        modelDao.observeDownloadedModels(),
        inferenceEngine.loadState,
        isRunning,
        progressLabel,
        liveTps,
        errorMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val benchmarks = values[0] as List<BenchmarkEntity>
        @Suppress("UNCHECKED_CAST")
        val models = values[1] as List<ModelEntity>
        val load = values[2] as ModelLoadState
        val running = values[3] as Boolean
        val progress = values[4] as String
        val tps = values[5] as Float
        val err = values[6] as String?

        BenchmarkUiState(
            benchmarks = benchmarks,
            downloadedModels = models,
            loadState = load,
            isRunning = running,
            progressLabel = progress,
            liveTps = tps,
            deviceCapabilities = deviceCaps.value,
            errorMessage = err
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BenchmarkUiState()
    )

    fun runBenchmark(targetModel: ModelEntity) {
        if (isRunning.value || inferenceEngine.isGenerating.value) return
        val path = targetModel.localPath
        if (path.isNullOrBlank()) {
            errorMessage.value = "Model file missing for '${targetModel.name}'."
            return
        }

        viewModelScope.launch {
            isRunning.value = true
            errorMessage.value = null
            liveTps.value = 0f

            val caps = deviceProfiler.snapshot()
            deviceCaps.value = caps
            val prefs = settingsRepository.preferencesFlow.first()
            val benchSettings = prefs.inference.copy(
                temperature = 0.0f,
                maxTokens = 96
            )

            // Always perform a clean reload when measuring true model loading time
            progressLabel.value = "Measuring model load time for ${targetModel.name}..."
            val loadRes = inferenceEngine.loadModel(targetModel.id, targetModel.name, path, benchSettings)
            val loadMs = loadRes.getOrElse { err ->
                isRunning.value = false
                progressLabel.value = ""
                errorMessage.value = err.message ?: "Failed to load model for benchmark."
                return@launch
            }
            modelDao.clearAllLoadedFlags()
            modelDao.markModelLoaded(targetModel.id)

            progressLabel.value = "Running real prompt & token generation benchmark..."
            val benchmarkPromptText =
                "Explain three practical engineering trade-offs between hash tables and balanced binary search trees in concise bullet points."
            val formatted = PromptFormatter.formatConversation(
                systemPrompt = "You are a concise computer science reference.",
                history = listOf(
                    com.lakhyadeep.papai.data.local.db.MessageEntity(
                        id = "bench_prompt",
                        conversationId = "bench",
                        role = "USER",
                        content = benchmarkPromptText
                    )
                ),
                modelNameOrArch = targetModel.name,
                templateType = benchSettings.chatTemplate,
                contextLength = benchSettings.contextLength,
                maxResponseTokens = benchSettings.maxTokens
            )

            var promptSpeed = 0f
            var genSpeed = 0f
            var totalGenMs = 0L
            var tokenCount = 0
            var failedMsg: String? = null

            inferenceEngine.generateStream(
                formattedPrompt = formatted.prompt,
                settings = benchSettings,
                estimatedPromptTokens = formatted.estimatedTokens
            ).collect { event ->
                when (event) {
                    is InferenceStreamEvent.Token -> {
                        liveTps.value = event.currentTps
                        progressLabel.value = "Generating tokens (${event.tokenCount}/96) • ${String.format("%.1f", event.currentTps)} tok/s"
                    }
                    is InferenceStreamEvent.Completed -> {
                        promptSpeed = event.promptEvalSpeedTps
                        genSpeed = event.tokenGenSpeedTps
                        totalGenMs = event.totalTimeMs
                        tokenCount = event.tokenCount
                    }
                    is InferenceStreamEvent.Cancelled -> {
                        failedMsg = "Benchmark cancelled before completion."
                    }
                    is InferenceStreamEvent.Failed -> {
                        failedMsg = event.errorMessage
                    }
                }
            }

            if (failedMsg != null) {
                errorMessage.value = failedMsg
            } else {
                val rec = ModelRecommendationEngine.evaluateModel(targetModel, benchSettings.contextLength, caps)
                val entity = BenchmarkEntity(
                    id = UUID.randomUUID().toString(),
                    modelId = targetModel.id,
                    modelName = targetModel.name,
                    quantization = targetModel.quantization,
                    loadTimeMs = loadMs,
                    promptEvalSpeedTps = promptSpeed,
                    tokenGenSpeedTps = genSpeed,
                    totalGenTimeMs = totalGenMs,
                    tokensGenerated = tokenCount,
                    estimatedRamMb = rec.estimatedMemoryMb,
                    deviceModel = "${caps.manufacturer} ${caps.deviceModel}".trim(),
                    cpuAbi = caps.primaryAbi,
                    totalRamMb = caps.totalRamMb,
                    threadsUsed = caps.cpuCores,
                    contextLength = benchSettings.contextLength
                )
                benchmarkDao.insertBenchmark(entity)
            }

            isRunning.value = false
            progressLabel.value = ""
        }
    }

    fun deleteBenchmark(id: String) {
        viewModelScope.launch {
            benchmarkDao.deleteBenchmarkById(id)
        }
    }

    fun clearBenchmarks() {
        viewModelScope.launch {
            benchmarkDao.clearAllBenchmarks()
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }
}
