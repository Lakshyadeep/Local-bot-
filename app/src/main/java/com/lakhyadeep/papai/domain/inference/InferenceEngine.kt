package com.lakhyadeep.papai.domain.inference

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class ChatTemplateType(val displayName: String) {
    AUTO("Auto Detect"),
    CHATML("ChatML (Qwen / Hermes)"),
    LLAMA3("Llama 3 Instruct"),
    PHI3("Phi-3 / Phi-3.5"),
    GEMMA("Gemma Instruct"),
    ALPACA("Alpaca"),
    RAW("Raw Prompt")
}

data class InferenceSettings(
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 512,
    val contextLength: Int = 4096,
    val numThreads: Int = 4,
    val gpuLayers: Int = 0,
    val seed: Int = -1,
    val systemPrompt: String = "You are Papi AI, a helpful private on-device AI assistant.",
    val chatTemplate: ChatTemplateType = ChatTemplateType.AUTO,
    val stopSequences: String = "",
    val useMmap: Boolean = true,
    val flashAttention: Boolean = false
)

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val sanitized: InferenceSettings
)

object InferenceSettingsValidator {
    fun validate(settings: InferenceSettings, hasGpuSupport: Boolean, maxCpuCores: Int): ValidationResult {
        val errors = mutableListOf<String>()

        if (settings.temperature !in 0.0f..2.0f) {
            errors += "Temperature must be between 0.0 and 2.0."
        }
        if (settings.topP !in 0.05f..1.0f) {
            errors += "Top P must be between 0.05 and 1.0."
        }
        if (settings.topK !in 1..200) {
            errors += "Top K must be between 1 and 200."
        }
        if (settings.repeatPenalty !in 0.8f..2.0f) {
            errors += "Repeat penalty must be between 0.8 and 2.0."
        }
        if (settings.maxTokens !in 32..8192) {
            errors += "Max generated tokens must be between 32 and 8192."
        }
        if (settings.contextLength !in 512..32768) {
            errors += "Context length must be between 512 and 32768."
        }
        if (settings.numThreads !in 1..maxCpuCores.coerceAtLeast(16)) {
            errors += "CPU threads must be between 1 and ${maxCpuCores.coerceAtLeast(16)}."
        }
        if (!hasGpuSupport && settings.gpuLayers != 0) {
            errors += "GPU layer offloading is unavailable on this CPU-only native backend."
        }

        val sanitized = settings.copy(
            temperature = settings.temperature.coerceIn(0.0f, 2.0f),
            topP = settings.topP.coerceIn(0.05f, 1.0f),
            topK = settings.topK.coerceIn(1, 200),
            repeatPenalty = settings.repeatPenalty.coerceIn(0.8f, 2.0f),
            maxTokens = settings.maxTokens.coerceIn(32, 8192),
            contextLength = settings.contextLength.coerceIn(512, 32768),
            numThreads = settings.numThreads.coerceIn(1, maxCpuCores.coerceAtLeast(1)),
            gpuLayers = if (hasGpuSupport) settings.gpuLayers.coerceIn(0, 128) else 0
        )

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            sanitized = sanitized
        )
    }
}

sealed class ModelLoadState {
    data object Unloaded : ModelLoadState()
    data class Loading(val modelId: String, val modelName: String) : ModelLoadState()
    data class Loaded(
        val modelId: String,
        val modelName: String,
        val modelPath: String,
        val loadTimeMs: Long
    ) : ModelLoadState()
    data class Error(val message: String, val modelId: String? = null) : ModelLoadState()
}

sealed class InferenceStreamEvent {
    data class Token(val text: String, val tokenCount: Int, val currentTps: Float) : InferenceStreamEvent()
    data class Completed(
        val fullText: String,
        val tokenCount: Int,
        val promptEvalSpeedTps: Float,
        val tokenGenSpeedTps: Float,
        val totalTimeMs: Long
    ) : InferenceStreamEvent()
    data class Failed(val errorMessage: String) : InferenceStreamEvent()
    data class Cancelled(val partialText: String, val tokenCount: Int) : InferenceStreamEvent()
}

data class EngineCapabilities(
    val engineName: String,
    val supportsStreaming: Boolean,
    val supportsGpuOffload: Boolean,
    val gpuUnavailableReason: String?,
    val supportsSeedControl: Boolean,
    val supportsStopSequences: Boolean,
    val supportsMmap: Boolean
)

interface InferenceEngine {
    val loadState: StateFlow<ModelLoadState>
    val isGenerating: StateFlow<Boolean>

    fun capabilities(): EngineCapabilities

    suspend fun loadModel(
        modelId: String,
        modelName: String,
        modelPath: String,
        settings: InferenceSettings
    ): Result<Long>

    suspend fun unloadModel()

    fun generateStream(
        formattedPrompt: String,
        settings: InferenceSettings,
        estimatedPromptTokens: Int
    ): Flow<InferenceStreamEvent>

    fun stopGeneration()
}
