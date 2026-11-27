package com.lakhyadeep.papai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakhyadeep.papai.data.local.db.AssistantDao
import com.lakhyadeep.papai.data.local.db.AssistantEntity
import com.lakhyadeep.papai.data.local.db.ConversationDao
import com.lakhyadeep.papai.data.local.db.ConversationEntity
import com.lakhyadeep.papai.data.local.db.ModelDao
import com.lakhyadeep.papai.data.local.db.ModelEntity
import com.lakhyadeep.papai.data.local.prefs.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class AssistantsUiState(
    val assistants: List<AssistantEntity> = emptyList(),
    val activeAssistantId: String = "pal_general",
    val downloadedModels: List<ModelEntity> = emptyList(),
    val conversationCountsByAssistant: Map<String, Int> = emptyMap()
)

class AssistantsViewModel(
    private val assistantDao: AssistantDao,
    private val modelDao: ModelDao,
    private val conversationDao: ConversationDao,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val uiState: StateFlow<AssistantsUiState> = combine(
        assistantDao.observeAllAssistants(),
        modelDao.observeDownloadedModels(),
        conversationDao.observeAllConversations(),
        settingsRepository.preferencesFlow
    ) { assistants, models, convs: List<ConversationEntity>, prefs ->
        val counts = convs.groupingBy { it.assistantId }.eachCount()
        AssistantsUiState(
            assistants = assistants,
            activeAssistantId = prefs.activeAssistantId,
            downloadedModels = models,
            conversationCountsByAssistant = counts
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AssistantsUiState()
    )

    fun saveAssistant(
        existingId: String?,
        name: String,
        description: String,
        avatarKey: String,
        colorHex: Long,
        systemPrompt: String,
        preferredModelId: String?,
        temperature: Float,
        contextLength: Int,
        maxTokens: Int
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return

        viewModelScope.launch {
            val existing = existingId?.let { assistantDao.getAssistantById(it) }
            val entity = AssistantEntity(
                id = existing?.id ?: "pal_${UUID.randomUUID()}",
                name = cleanName,
                description = description.trim().ifEmpty { "Custom AI personality." },
                avatarKey = avatarKey,
                colorHex = colorHex,
                systemPrompt = systemPrompt.trim().ifEmpty { "You are a helpful AI assistant." },
                preferredModelId = preferredModelId,
                temperature = temperature.coerceIn(0f, 2f),
                contextLength = contextLength.coerceIn(512, 32768),
                maxTokens = maxTokens.coerceIn(64, 4096),
                isPredefined = existing?.isPredefined ?: false,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            assistantDao.upsertAssistant(entity)
        }
    }

    fun duplicateAssistant(assistant: AssistantEntity) {
        viewModelScope.launch {
            val copy = assistant.copy(
                id = "pal_${UUID.randomUUID()}",
                name = "${assistant.name} (Copy)",
                isPredefined = false,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            assistantDao.upsertAssistant(copy)
        }
    }

    fun deleteAssistant(assistant: AssistantEntity) {
        viewModelScope.launch {
            assistantDao.deleteAssistantById(assistant.id)
            if (uiState.value.activeAssistantId == assistant.id) {
                settingsRepository.setActiveAssistantId("pal_general")
            }
        }
    }

    fun setActiveAssistant(assistantId: String) {
        viewModelScope.launch {
            settingsRepository.setActiveAssistantId(assistantId)
        }
    }
}
