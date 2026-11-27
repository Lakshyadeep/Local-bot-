package com.lakhyadeep.papai.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lakhyadeep.papai.domain.inference.ChatTemplateType
import com.lakhyadeep.papai.domain.inference.InferenceSettings
import com.lakhyadeep.papai.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "papi_ai_settings")

data class AppPreferences(
    val inference: InferenceSettings = InferenceSettings(),
    val offlineMode: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val huggingFaceToken: String = "",
    val preferredTtsVoice: String = "",
    val ttsSpeed: Float = 1.0f,
    val enableLocalTools: Boolean = true,
    val autoScrollChat: Boolean = true,
    val activeAssistantId: String = "pal_general"
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val TEMPERATURE = floatPreferencesKey("inf_temperature")
        val TOP_P = floatPreferencesKey("inf_top_p")
        val TOP_K = intPreferencesKey("inf_top_k")
        val REPEAT_PENALTY = floatPreferencesKey("inf_repeat_penalty")
        val MAX_TOKENS = intPreferencesKey("inf_max_tokens")
        val CONTEXT_LENGTH = intPreferencesKey("inf_context_length")
        val NUM_THREADS = intPreferencesKey("inf_num_threads")
        val GPU_LAYERS = intPreferencesKey("inf_gpu_layers")
        val SEED = intPreferencesKey("inf_seed")
        val SYSTEM_PROMPT = stringPreferencesKey("inf_system_prompt")
        val CHAT_TEMPLATE = stringPreferencesKey("inf_chat_template")
        val STOP_SEQUENCES = stringPreferencesKey("inf_stop_sequences")
        val USE_MMAP = booleanPreferencesKey("inf_use_mmap")
        val FLASH_ATTENTION = booleanPreferencesKey("inf_flash_attention")

        val OFFLINE_MODE = booleanPreferencesKey("app_offline_mode")
        val THEME_MODE = stringPreferencesKey("app_theme_mode")
        val HF_TOKEN = stringPreferencesKey("app_hf_token")
        val TTS_VOICE = stringPreferencesKey("app_tts_voice")
        val TTS_SPEED = floatPreferencesKey("app_tts_speed")
        val ENABLE_TOOLS = booleanPreferencesKey("app_enable_tools")
        val AUTO_SCROLL = booleanPreferencesKey("app_auto_scroll")
        val ACTIVE_ASSISTANT_ID = stringPreferencesKey("app_active_assistant_id")
    }

    private val defaultThreads: Int =
        (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 6)

    val preferencesFlow: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        val templateName = prefs[Keys.CHAT_TEMPLATE] ?: ChatTemplateType.AUTO.name
        val template = runCatching { ChatTemplateType.valueOf(templateName) }
            .getOrDefault(ChatTemplateType.AUTO)

        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val themeMode = runCatching { ThemeMode.valueOf(themeName) }
            .getOrDefault(ThemeMode.SYSTEM)

        val defaults = InferenceSettings(numThreads = defaultThreads)

        AppPreferences(
            inference = InferenceSettings(
                temperature = prefs[Keys.TEMPERATURE] ?: defaults.temperature,
                topP = prefs[Keys.TOP_P] ?: defaults.topP,
                topK = prefs[Keys.TOP_K] ?: defaults.topK,
                repeatPenalty = prefs[Keys.REPEAT_PENALTY] ?: defaults.repeatPenalty,
                maxTokens = prefs[Keys.MAX_TOKENS] ?: defaults.maxTokens,
                contextLength = prefs[Keys.CONTEXT_LENGTH] ?: defaults.contextLength,
                numThreads = prefs[Keys.NUM_THREADS] ?: defaults.numThreads,
                gpuLayers = prefs[Keys.GPU_LAYERS] ?: defaults.gpuLayers,
                seed = prefs[Keys.SEED] ?: defaults.seed,
                systemPrompt = prefs[Keys.SYSTEM_PROMPT] ?: defaults.systemPrompt,
                chatTemplate = template,
                stopSequences = prefs[Keys.STOP_SEQUENCES] ?: defaults.stopSequences,
                useMmap = prefs[Keys.USE_MMAP] ?: defaults.useMmap,
                flashAttention = prefs[Keys.FLASH_ATTENTION] ?: defaults.flashAttention
            ),
            offlineMode = prefs[Keys.OFFLINE_MODE] ?: false,
            themeMode = themeMode,
            huggingFaceToken = prefs[Keys.HF_TOKEN] ?: "",
            preferredTtsVoice = prefs[Keys.TTS_VOICE] ?: "",
            ttsSpeed = prefs[Keys.TTS_SPEED] ?: 1.0f,
            enableLocalTools = prefs[Keys.ENABLE_TOOLS] ?: true,
            autoScrollChat = prefs[Keys.AUTO_SCROLL] ?: true,
            activeAssistantId = prefs[Keys.ACTIVE_ASSISTANT_ID] ?: "pal_general"
        )
    }

    suspend fun updateInferenceSettings(settings: InferenceSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TEMPERATURE] = settings.temperature
            prefs[Keys.TOP_P] = settings.topP
            prefs[Keys.TOP_K] = settings.topK
            prefs[Keys.REPEAT_PENALTY] = settings.repeatPenalty
            prefs[Keys.MAX_TOKENS] = settings.maxTokens
            prefs[Keys.CONTEXT_LENGTH] = settings.contextLength
            prefs[Keys.NUM_THREADS] = settings.numThreads
            prefs[Keys.GPU_LAYERS] = settings.gpuLayers
            prefs[Keys.SEED] = settings.seed
            prefs[Keys.SYSTEM_PROMPT] = settings.systemPrompt
            prefs[Keys.CHAT_TEMPLATE] = settings.chatTemplate.name
            prefs[Keys.STOP_SEQUENCES] = settings.stopSequences
            prefs[Keys.USE_MMAP] = settings.useMmap
            prefs[Keys.FLASH_ATTENTION] = settings.flashAttention
        }
    }

    suspend fun setOfflineMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.OFFLINE_MODE] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setHuggingFaceToken(token: String) {
        context.dataStore.edit { it[Keys.HF_TOKEN] = token.trim() }
    }

    suspend fun setTtsSettings(voiceName: String, speed: Float) {
        context.dataStore.edit {
            it[Keys.TTS_VOICE] = voiceName
            it[Keys.TTS_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }

    suspend fun setEnableLocalTools(enabled: Boolean) {
        context.dataStore.edit { it[Keys.ENABLE_TOOLS] = enabled }
    }

    suspend fun setAutoScrollChat(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_SCROLL] = enabled }
    }

    suspend fun setActiveAssistantId(assistantId: String) {
        context.dataStore.edit { it[Keys.ACTIVE_ASSISTANT_ID] = assistantId }
    }

    suspend fun resetInferenceDefaults() {
        updateInferenceDefaults(InferenceSettings(numThreads = defaultThreads))
    }

    private suspend fun updateInferenceDefaults(defaults: InferenceSettings) {
        updateInferenceSettings(defaults)
    }
}
