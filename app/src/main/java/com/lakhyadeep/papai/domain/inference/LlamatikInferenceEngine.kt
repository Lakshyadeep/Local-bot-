package com.lakhyadeep.papai.domain.inference

import com.llamatik.library.platform.GenStream
import com.llamatik.library.platform.LlamaBridge
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class LlamatikInferenceEngine(
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.IO
) : InferenceEngine {

    private val engineMutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Unloaded)
    override val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    override val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    override fun capabilities(): EngineCapabilities {
        return EngineCapabilities(
            engineName = "llama.cpp (Llamatik 0.15.0 JNI)",
            supportsStreaming = true,
            supportsGpuOffload = false,
            gpuUnavailableReason = "CPU-only ARM/x86 native build (OpenMP + NEON). GPU backend not bundled.",
            supportsSeedControl = false,
            supportsStopSequences = true,
            supportsMmap = true
        )
    }

    override suspend fun loadModel(
        modelId: String,
        modelName: String,
        modelPath: String,
        settings: InferenceSettings
    ): Result<Long> = withContext(inferenceDispatcher) {
        engineMutex.withLock {
            val file = File(modelPath)
            if (!file.exists() || !file.isFile || file.length() < 24L) {
                val msg = "Model file missing or corrupted at $modelPath"
                _loadState.value = ModelLoadState.Error(msg, modelId)
                return@withContext Result.failure(IllegalStateException(msg))
            }

            if (_isGenerating.value) {
                stopGeneration()
            }

            // Unload previous model cleanly before loading new one
            if (_loadState.value is ModelLoadState.Loaded) {
                runCatching { LlamaBridge.shutdown() }
            }

            _loadState.value = ModelLoadState.Loading(modelId, modelName)
            val startNs = System.nanoTime()
            return@withContext try {
                LlamaBridge.updateGenerateParams(
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    topP = settings.topP,
                    topK = settings.topK,
                    repeatPenalty = settings.repeatPenalty
                )
                val initialized = LlamaBridge.initGenerateModel(modelPath)
                if (!initialized) {
                    val msg = "Native llama.cpp engine failed to initialize '$modelName'. The GGUF quantization or architecture may be unsupported or RAM may be insufficient."
                    _loadState.value = ModelLoadState.Error(msg, modelId)
                    Result.failure(IllegalStateException(msg))
                } else {
                    val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    _loadState.value = ModelLoadState.Loaded(
                        modelId = modelId,
                        modelName = modelName,
                        modelPath = modelPath,
                        loadTimeMs = elapsedMs
                    )
                    Result.success(elapsedMs)
                }
            } catch (t: Throwable) {
                val msg = "Native load error: ${t.message ?: t::class.simpleName}"
                _loadState.value = ModelLoadState.Error(msg, modelId)
                Result.failure(t)
            }
        }
    }

    override suspend fun unloadModel() = withContext(inferenceDispatcher) {
        engineMutex.withLock {
            if (_isGenerating.value) {
                cancelRequested.set(true)
                runCatching { LlamaBridge.nativeCancelGenerate() }
            }
            runCatching { LlamaBridge.shutdown() }
            _isGenerating.value = false
            _loadState.value = ModelLoadState.Unloaded
        }
    }

    override fun generateStream(
        formattedPrompt: String,
        settings: InferenceSettings,
        estimatedPromptTokens: Int
    ): Flow<InferenceStreamEvent> = callbackFlow {
        val currentState = _loadState.value
        if (currentState !is ModelLoadState.Loaded) {
            trySend(InferenceStreamEvent.Failed("No GGUF model is loaded. Please load a model in Model Manager first."))
            close()
            return@callbackFlow
        }

        engineMutex.withLock {
            cancelRequested.set(false)
            _isGenerating.value = true

            val startNs = System.nanoTime()
            var firstTokenNs = 0L
            var tokenCount = 0
            val accumulated = StringBuilder()

            val stopSeqs = settings.stopSequences
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            try {
                LlamaBridge.updateGenerateParams(
                    temperature = settings.temperature,
                    maxTokens = settings.maxTokens,
                    topP = settings.topP,
                    topK = settings.topK,
                    repeatPenalty = settings.repeatPenalty
                )

                var stopTriggered = false
                val completionLock = Object()
                var finished = false

                LlamaBridge.generateStream(
                    prompt = formattedPrompt,
                    callback = object : GenStream {
                        override fun onDelta(text: String) {
                            if (cancelRequested.get() || stopTriggered) return

                            val nowNs = System.nanoTime()
                            if (firstTokenNs == 0L) {
                                firstTokenNs = nowNs
                            }

                            accumulated.append(text)
                            tokenCount++

                            // Check custom stop sequences
                            val currentText = accumulated.toString()
                            val hitStop = stopSeqs.firstOrNull { currentText.endsWith(it) }
                            if (hitStop != null) {
                                stopTriggered = true
                                val trimmedLen = currentText.length - hitStop.length
                                accumulated.setLength(trimmedLen.coerceAtLeast(0))
                                runCatching { LlamaBridge.nativeCancelGenerate() }
                                return
                            }

                            val genSeconds = ((nowNs - firstTokenNs).coerceAtLeast(1L)) / 1_000_000_000.0
                            val tps = if (genSeconds > 0.05) (tokenCount / genSeconds).toFloat() else 0f

                            trySend(
                                InferenceStreamEvent.Token(
                                    text = text,
                                    tokenCount = tokenCount,
                                    currentTps = tps
                                )
                            )
                        }

                        override fun onComplete() {
                            val endNs = System.nanoTime()
                            val totalMs = ((endNs - startNs) / 1_000_000L).coerceAtLeast(1L)
                            val prefillMs = if (firstTokenNs > startNs) {
                                ((firstTokenNs - startNs) / 1_000_000L).coerceAtLeast(1L)
                            } else {
                                totalMs
                            }
                            val decodeNs = if (firstTokenNs > 0L) (endNs - firstTokenNs).coerceAtLeast(1L) else (endNs - startNs).coerceAtLeast(1L)

                            val promptTps = if (estimatedPromptTokens > 0) {
                                (estimatedPromptTokens / (prefillMs / 1000.0)).toFloat()
                            } else 0f

                            val genTps = if (tokenCount > 0) {
                                (tokenCount / (decodeNs / 1_000_000_000.0)).toFloat()
                            } else 0f

                            if (cancelRequested.get() && !stopTriggered) {
                                trySend(InferenceStreamEvent.Cancelled(accumulated.toString(), tokenCount))
                            } else {
                                trySend(
                                    InferenceStreamEvent.Completed(
                                        fullText = accumulated.toString(),
                                        tokenCount = tokenCount,
                                        promptEvalSpeedTps = promptTps,
                                        tokenGenSpeedTps = genTps,
                                        totalTimeMs = totalMs
                                    )
                                )
                            }
                            synchronized(completionLock) {
                                finished = true
                                completionLock.notifyAll()
                            }
                        }

                        override fun onError(message: String) {
                            if (cancelRequested.get() || stopTriggered) {
                                trySend(InferenceStreamEvent.Cancelled(accumulated.toString(), tokenCount))
                            } else {
                                trySend(InferenceStreamEvent.Failed(message.ifBlank { "Native inference failed." }))
                            }
                            synchronized(completionLock) {
                                finished = true
                                completionLock.notifyAll()
                            }
                        }
                    }
                )

                // Wait safely if native generateStream dispatches asynchronously
                synchronized(completionLock) {
                    while (!finished) {
                        completionLock.wait(250L)
                    }
                }
            } catch (t: Throwable) {
                trySend(InferenceStreamEvent.Failed("Inference error: ${t.message ?: t::class.simpleName}"))
            } finally {
                _isGenerating.value = false
                close()
            }
        }

        awaitClose {
            if (_isGenerating.value) {
                stopGeneration()
            }
        }
    }.flowOn(inferenceDispatcher)

    override fun stopGeneration() {
        cancelRequested.set(true)
        runCatching { LlamaBridge.nativeCancelGenerate() }
    }
}
