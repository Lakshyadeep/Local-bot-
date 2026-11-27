package com.lakhyadeep.papai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakhyadeep.papai.data.local.db.AssistantDao
import com.lakhyadeep.papai.data.local.db.AssistantEntity
import com.lakhyadeep.papai.data.local.db.ConversationDao
import com.lakhyadeep.papai.data.local.db.ConversationEntity
import com.lakhyadeep.papai.data.local.db.MessageDao
import com.lakhyadeep.papai.data.local.db.MessageEntity
import com.lakhyadeep.papai.data.local.db.MessageRole
import com.lakhyadeep.papai.data.local.db.ModelDao
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.data.local.prefs.AppPreferences
import com.lakhyadeep.papai.data.local.prefs.SettingsRepository
import com.lakhyadeep.papai.domain.device.CompatibilityStatus
import com.lakhyadeep.papai.domain.device.DeviceCapabilities
import com.lakhyadeep.papai.domain.device.DeviceProfiler
import com.lakhyadeep.papai.domain.device.ModelRecommendation
import com.lakhyadeep.papai.domain.device.ModelRecommendationEngine
import com.lakhyadeep.papai.domain.inference.InferenceEngine
import com.lakhyadeep.papai.domain.inference.InferenceSettings
import com.lakhyadeep.papai.domain.inference.InferenceStreamEvent
import com.lakhyadeep.papai.domain.inference.ModelLoadState
import com.lakhyadeep.papai.domain.inference.PromptFormatter
import com.lakhyadeep.papai.domain.tools.LocalToolRegistry
import com.lakhyadeep.papai.domain.tools.ToolCallRequest
import com.lakhyadeep.papai.domain.voice.TextToSpeechManager
import com.lakhyadeep.papai.domain.voice.TtsState
import com.lakhyadeep.papai.domain.voice.VoiceInputManager
import com.lakhyadeep.papai.domain.voice.VoiceInputState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class PendingMemoryWarning(
    val model: ModelEntity,
    val recommendation: ModelRecommendation
)

data class ChatUiState(
    val activeConversationId: String? = null,
    val conversations: List<ConversationEntity> = emptyList(),
    val searchQuery: String = "",
    val messages: List<MessageEntity> = emptyList(),
    val assistants: List<AssistantEntity> = emptyList(),
    val activeAssistant: AssistantEntity? = null,
    val downloadedModels: List<ModelEntity> = emptyList(),
    val loadState: ModelLoadState = ModelLoadState.Unloaded,
    val isGenerating: Boolean = false,
    val streamingText: String = "",
    val liveTokensPerSecond: Float = 0f,
    val liveTokenCount: Int = 0,
    val preferences: AppPreferences = AppPreferences(),
    val voiceInput: VoiceInputState = VoiceInputState(),
    val ttsState: TtsState = TtsState(),
    val deviceCapabilities: DeviceCapabilities? = null,
    val errorBanner: String? = null,
    val pendingMemoryWarning: PendingMemoryWarning? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val assistantDao: AssistantDao,
    private val modelDao: ModelDao,
    private val settingsRepository: SettingsRepository,
    private val inferenceEngine: InferenceEngine,
    private val deviceProfiler: DeviceProfiler,
    private val localToolRegistry: LocalToolRegistry,
    private val voiceInputManager: VoiceInputManager,
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    private val activeConversationId = MutableStateFlow<String?>(null)
    private val searchQuery = MutableStateFlow("")
    private val streamingText = MutableStateFlow("")
    private val liveTps = MutableStateFlow(0f)
    private val liveTokens = MutableStateFlow(0)
    private val errorBanner = MutableStateFlow<String?>(null)
    private val pendingMemoryWarning = MutableStateFlow<PendingMemoryWarning?>(null)
    private val deviceCaps = MutableStateFlow(deviceProfiler.snapshot())

    private var generationJob: Job? = null

    private val conversationsFlow = searchQuery.flatMapLatest { q ->
        if (q.isBlank()) conversationDao.observeAllConversations()
        else conversationDao.searchConversations(q.trim())
    }

    private val messagesFlow = activeConversationId.flatMapLatest { convId ->
        if (convId == null) flowOf(emptyList())
        else messageDao.observeMessagesForConversation(convId)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        activeConversationId,
        conversationsFlow,
        searchQuery,
        messagesFlow,
        assistantDao.observeAllAssistants(),
        modelDao.observeDownloadedModels(),
        inferenceEngine.loadState,
        inferenceEngine.isGenerating,
        streamingText,
        liveTps,
        liveTokens,
        settingsRepository.preferencesFlow,
        voiceInputManager.state,
        ttsManager.state,
        errorBanner,
        pendingMemoryWarning
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val convId = values[0] as String?
        @Suppress("UNCHECKED_CAST")
        val convs = values[1] as List<ConversationEntity>
        val query = values[2] as String
        @Suppress("UNCHECKED_CAST")
        val msgs = values[3] as List<MessageEntity>
        @Suppress("UNCHECKED_CAST")
        val pals = values[4] as List<AssistantEntity>
        @Suppress("UNCHECKED_CAST")
        val models = values[5] as List<ModelEntity>
        val load = values[6] as ModelLoadState
        val generating = values[7] as Boolean
        val stream = values[8] as String
        val tps = values[9] as Float
        val tokCount = values[10] as Int
        val prefs = values[11] as AppPreferences
        val voice = values[12] as VoiceInputState
        val tts = values[13] as TtsState
        val err = values[14] as String?
        val memWarn = values[15] as PendingMemoryWarning?

        val currentConv = convs.firstOrNull { it.id == convId }
        val resolvedAssistantId = currentConv?.assistantId ?: prefs.activeAssistantId
        val activePal = pals.firstOrNull { it.id == resolvedAssistantId } ?: pals.firstOrNull()

        ChatUiState(
            activeConversationId = convId,
            conversations = convs,
            searchQuery = query,
            messages = msgs,
            assistants = pals,
            activeAssistant = activePal,
            downloadedModels = models,
            loadState = load,
            isGenerating = generating,
            streamingText = stream,
            liveTokensPerSecond = tps,
            liveTokenCount = tokCount,
            preferences = prefs,
            voiceInput = voice,
            ttsState = tts,
            deviceCapabilities = deviceCaps.value,
            errorBanner = err,
            pendingMemoryWarning = memWarn
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatUiState()
    )

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectConversation(conversationId: String?) {
        if (inferenceEngine.isGenerating.value) {
            stopGeneration()
        }
        activeConversationId.value = conversationId
        errorBanner.value = null
    }

    fun startNewChat(assistantId: String? = null) {
        if (inferenceEngine.isGenerating.value) {
            stopGeneration()
        }
        viewModelScope.launch {
            if (assistantId != null) {
                settingsRepository.setActiveAssistantId(assistantId)
            }
            activeConversationId.value = null
            errorBanner.value = null
        }
    }

    fun selectAssistant(assistant: AssistantEntity) {
        viewModelScope.launch {
            settingsRepository.setActiveAssistantId(assistant.id)
            val convId = activeConversationId.value
            if (convId != null) {
                val existing = conversationDao.getConversationById(convId)
                if (existing != null) {
                    conversationDao.upsertConversation(
                        existing.copy(assistantId = assistant.id, updatedAt = System.currentTimeMillis())
                    )
                }
            }
            // If assistant specifies a preferred downloaded model that isn't loaded yet, offer/load if safe
            val prefModelId = assistant.preferredModelId
            if (prefModelId != null) {
                val currentLoadedId = (inferenceEngine.loadState.value as? ModelLoadState.Loaded)?.modelId
                if (currentLoadedId != prefModelId) {
                    val model = modelDao.getModelById(prefModelId)
                    if (model != null && model.isDownloaded) {
                        requestLoadModel(model)
                    }
                }
            }
        }
    }

    fun renameConversation(conversationId: String, newTitle: String) {
        val clean = newTitle.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            conversationDao.renameConversation(conversationId, clean)
        }
    }

    fun deleteConversation(conversationId: String) {
        viewModelScope.launch {
            if (activeConversationId.value == conversationId) {
                if (inferenceEngine.isGenerating.value) stopGeneration()
                activeConversationId.value = null
            }
            conversationDao.deleteConversationById(conversationId)
        }
    }

    fun clearCurrentConversation() {
        val convId = activeConversationId.value ?: return
        if (inferenceEngine.isGenerating.value) stopGeneration()
        viewModelScope.launch {
            messageDao.clearConversationMessages(convId)
        }
    }

    fun requestLoadModel(model: ModelEntity, forceLoad: Boolean = false) {
        val path = model.localPath
        if (path.isNullOrBlank()) {
            errorBanner.value = "Model '${model.name}' has no valid local file path."
            return
        }
        val caps = deviceProfiler.snapshot()
        deviceCaps.value = caps
        val settings = uiState.value.preferences.inference
        val rec = ModelRecommendationEngine.evaluateModel(model, settings.contextLength, caps)

        if (!forceLoad && (rec.status == CompatibilityStatus.MEMORY_WARNING ||
                rec.status == CompatibilityStatus.INSUFFICIENT_RAM ||
                rec.status == CompatibilityStatus.ARCH_UNSUPPORTED)
        ) {
            pendingMemoryWarning.value = PendingMemoryWarning(model, rec)
            return
        }

        pendingMemoryWarning.value = null
        viewModelScope.launch {
            val result = inferenceEngine.loadModel(model.id, model.name, path, settings)
            if (result.isSuccess) {
                modelDao.clearAllLoadedFlags()
                modelDao.markModelLoaded(model.id)
                errorBanner.value = null
            } else {
                errorBanner.value = result.exceptionOrNull()?.message ?: "Failed to load model."
            }
        }
    }

    fun dismissMemoryWarning() {
        pendingMemoryWarning.value = null
    }

    fun unloadCurrentModel() {
        viewModelScope.launch {
            inferenceEngine.unloadModel()
            modelDao.clearAllLoadedFlags()
        }
    }

    fun sendMessage(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return
        if (inferenceEngine.isGenerating.value) return

        val loadedState = inferenceEngine.loadState.value
        if (loadedState !is ModelLoadState.Loaded) {
            errorBanner.value = "No GGUF model is currently loaded. Go to Models to download or load a local model first."
            return
        }

        errorBanner.value = null
        viewModelScope.launch {
            val state = uiState.value
            val pal = state.activeAssistant
            val convId = state.activeConversationId ?: createConversationInternal(
                title = buildTitleFromPrompt(trimmed),
                assistantId = pal?.id ?: "pal_general",
                modelId = loadedState.modelId
            ).also { activeConversationId.value = it }

            // Auto-title if still default
            val conv = conversationDao.getConversationById(convId)
            if (conv != null && conv.title == "New Chat") {
                conversationDao.renameConversation(convId, buildTitleFromPrompt(trimmed))
            }

            val userMsg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.USER.name,
                content = trimmed,
                createdAt = System.currentTimeMillis()
            )
            messageDao.upsertMessage(userMsg)
            runInferenceForConversation(convId, loadedState, pal)
        }
    }

    fun editUserMessageAndResend(message: MessageEntity, newContent: String) {
        val clean = newContent.trim()
        if (clean.isEmpty() || inferenceEngine.isGenerating.value) return
        val loadedState = inferenceEngine.loadState.value
        if (loadedState !is ModelLoadState.Loaded) {
            errorBanner.value = "Load a model before regenerating a response."
            return
        }

        viewModelScope.launch {
            messageDao.updateMessageContent(message.id, clean)
            messageDao.deleteMessagesAfter(message.conversationId, message.createdAt)
            runInferenceForConversation(message.conversationId, loadedState, uiState.value.activeAssistant)
        }
    }

    fun regenerateResponse(assistantMessage: MessageEntity) {
        if (inferenceEngine.isGenerating.value) return
        val loadedState = inferenceEngine.loadState.value
        if (loadedState !is ModelLoadState.Loaded) {
            errorBanner.value = "Load a model before regenerating a response."
            return
        }

        viewModelScope.launch {
            val convId = assistantMessage.conversationId
            messageDao.deleteMessagesAfter(convId, assistantMessage.createdAt - 1L)
            runInferenceForConversation(convId, loadedState, uiState.value.activeAssistant)
        }
    }

    fun runDirectLocalTool(toolName: String, arguments: Map<String, String>) {
        viewModelScope.launch {
            val state = uiState.value
            val pal = state.activeAssistant
            val convId = state.activeConversationId ?: createConversationInternal(
                title = "Local Tool: $toolName",
                assistantId = pal?.id ?: "pal_general",
                modelId = (state.loadState as? ModelLoadState.Loaded)?.modelId
            ).also { activeConversationId.value = it }

            val result = localToolRegistry.execute(ToolCallRequest(toolName, arguments))
            val msg = MessageEntity(
                id = UUID.randomUUID().toString(),
                conversationId = convId,
                role = MessageRole.TOOL.name,
                content = "**${result.toolName}**\n\n${result.output}",
                toolCallsSummary = result.toolName,
                isError = !result.success,
                createdAt = System.currentTimeMillis()
            )
            messageDao.upsertMessage(msg)
        }
    }

    private fun runInferenceForConversation(
        conversationId: String,
        loadedState: ModelLoadState.Loaded,
        assistant: AssistantEntity?
    ) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val prefs = uiState.value.preferences
            val baseSettings = prefs.inference
            val effectiveSettings = if (assistant != null) {
                baseSettings.copy(
                    temperature = assistant.temperature,
                    topP = assistant.topP,
                    topK = assistant.topK,
                    repeatPenalty = assistant.repeatPenalty,
                    contextLength = assistant.contextLength,
                    maxTokens = assistant.maxTokens,
                    systemPrompt = assistant.systemPrompt
                )
            } else {
                baseSettings
            }

            val history = messageDao.getMessagesForConversationOnce(conversationId)
            val toolsBlock = if (prefs.enableLocalTools) localToolRegistry.buildToolsSystemInstruction() else null

            val formatted = PromptFormatter.formatConversation(
                systemPrompt = effectiveSettings.systemPrompt,
                history = history,
                modelNameOrArch = loadedState.modelName,
                templateType = effectiveSettings.chatTemplate,
                contextLength = effectiveSettings.contextLength,
                maxResponseTokens = effectiveSettings.maxTokens,
                customStopSequences = effectiveSettings.stopSequences,
                toolsPromptBlock = toolsBlock
            )

            streamingText.value = ""
            liveTps.value = 0f
            liveTokens.value = 0

            var finalContent = ""
            var finalTokens = 0
            var finalTps = 0f
            var totalMs = 0L
            var isErr = false
            var toolSummary: String? = null

            inferenceEngine.generateStream(
                formattedPrompt = formatted.prompt,
                settings = effectiveSettings,
                estimatedPromptTokens = formatted.estimatedTokens
            ).collect { event ->
                when (event) {
                    is InferenceStreamEvent.Token -> {
                        streamingText.value += event.text
                        liveTokens.value = event.tokenCount
                        liveTps.value = event.currentTps
                    }
                    is InferenceStreamEvent.Completed -> {
                        finalContent = event.fullText.trim()
                        finalTokens = event.tokenCount
                        finalTps = event.tokenGenSpeedTps
                        totalMs = event.totalTimeMs
                    }
                    is InferenceStreamEvent.Cancelled -> {
                        finalContent = event.partialText.trim().ifEmpty { "[Generation stopped]" }
                        finalTokens = event.tokenCount
                    }
                    is InferenceStreamEvent.Failed -> {
                        finalContent = "Generation failed: ${event.errorMessage}"
                        isErr = true
                        errorBanner.value = event.errorMessage
                    }
                }
            }

            // Check if model requested a safe local tool call
            if (!isErr && prefs.enableLocalTools) {
                val toolReq = localToolRegistry.parseToolCall(finalContent)
                if (toolReq != null) {
                    val toolRes = localToolRegistry.execute(toolReq)
                    toolSummary = "${toolRes.toolName}: ${toolRes.output}"

                    val updatedHistory = history + MessageEntity(
                        id = "temp_tool_call",
                        conversationId = conversationId,
                        role = MessageRole.ASSISTANT.name,
                        content = "Tool result [${toolRes.toolName}]: ${toolRes.output}"
                    )
                    val followUpPrompt = PromptFormatter.formatConversation(
                        systemPrompt = effectiveSettings.systemPrompt + "\nUse the tool result above to answer clearly.",
                        history = updatedHistory,
                        modelNameOrArch = loadedState.modelName,
                        templateType = effectiveSettings.chatTemplate,
                        contextLength = effectiveSettings.contextLength,
                        maxResponseTokens = effectiveSettings.maxTokens,
                        customStopSequences = effectiveSettings.stopSequences
                    )

                    streamingText.value = ""
                    inferenceEngine.generateStream(
                        formattedPrompt = followUpPrompt.prompt,
                        settings = effectiveSettings,
                        estimatedPromptTokens = followUpPrompt.estimatedTokens
                    ).collect { event ->
                        when (event) {
                            is InferenceStreamEvent.Token -> {
                                streamingText.value += event.text
                                liveTokens.value = event.tokenCount
                                liveTps.value = event.currentTps
                            }
                            is InferenceStreamEvent.Completed -> {
                                finalContent = event.fullText.trim()
                                finalTokens += event.tokenCount
                                finalTps = event.tokenGenSpeedTps
                                totalMs += event.totalTimeMs
                            }
                            is InferenceStreamEvent.Cancelled -> {
                                finalContent = event.partialText.trim().ifEmpty { toolRes.output }
                            }
                            is InferenceStreamEvent.Failed -> {
                                finalContent = "Tool Result (${toolRes.toolName}): ${toolRes.output}"
                            }
                        }
                    }
                }
            }

            streamingText.value = ""
            if (finalContent.isNotBlank()) {
                val assistantMsg = MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = conversationId,
                    role = MessageRole.ASSISTANT.name,
                    content = finalContent,
                    modelName = loadedState.modelName,
                    tokensPerSecond = finalTps,
                    tokenCount = finalTokens,
                    generationTimeMs = totalMs,
                    toolCallsSummary = toolSummary,
                    isError = isErr,
                    createdAt = System.currentTimeMillis()
                )
                messageDao.upsertMessage(assistantMsg)
                conversationDao.getConversationById(conversationId)?.let { conv ->
                    conversationDao.upsertConversation(
                        conv.copy(modelId = loadedState.modelId, updatedAt = System.currentTimeMillis())
                    )
                }
            }
        }
    }

    fun stopGeneration() {
        inferenceEngine.stopGeneration()
    }

    fun startVoiceInput(onTranscriptReady: (String) -> Unit) {
        voiceInputManager.startListening(onTranscriptReady)
    }

    fun stopVoiceInput() {
        voiceInputManager.stopListening()
    }

    fun clearVoiceError() {
        voiceInputManager.clearError()
    }

    fun speakMessage(messageId: String, text: String) {
        ttsManager.speak(messageId, text)
    }

    fun pauseTts() = ttsManager.pause()
    fun resumeTts() = ttsManager.resume()
    fun stopTts() = ttsManager.stop()

    fun dismissError() {
        errorBanner.value = null
    }

    private suspend fun createConversationInternal(
        title: String,
        assistantId: String,
        modelId: String?
    ): String {
        val id = UUID.randomUUID().toString()
        val conv = ConversationEntity(
            id = id,
            title = title,
            assistantId = assistantId,
            modelId = modelId
        )
        conversationDao.upsertConversation(conv)
        return id
    }

    private fun buildTitleFromPrompt(prompt: String): String {
        val singleLine = prompt.replace('\n', ' ').trim()
        return if (singleLine.length <= 36) singleLine else singleLine.take(33).trimEnd() + "..."
    }
}
