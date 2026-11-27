package com.lakhyadeep.papai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lakhyadeep.papai.data.local.prefs.AppPreferences
import com.lakhyadeep.papai.data.local.prefs.SettingsRepository
import com.lakhyadeep.papai.domain.device.DeviceCapabilities
import com.lakhyadeep.papai.domain.device.DeviceProfiler
import com.lakhyadeep.papai.domain.inference.EngineCapabilities
import com.lakhyadeep.papai.domain.inference.InferenceEngine
import com.lakhyadeep.papai.domain.inference.InferenceSettings
import com.lakhyadeep.papai.domain.inference.InferenceSettingsValidator
import com.lakhyadeep.papai.domain.voice.TextToSpeechManager
import com.lakhyadeep.papai.domain.voice.TtsVoiceOption
import com.lakhyadeep.papai.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: AppPreferences = AppPreferences(),
    val deviceCapabilities: DeviceCapabilities? = null,
    val engineCapabilities: EngineCapabilities? = null,
    val availableTtsVoices: List<TtsVoiceOption> = emptyList(),
    val validationErrors: List<String> = emptyList(),
    val statusMessage: String? = null
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val inferenceEngine: InferenceEngine,
    private val deviceProfiler: DeviceProfiler,
    private val ttsManager: TextToSpeechManager
) : ViewModel() {

    private val validationErrors = MutableStateFlow<List<String>>(emptyList())
    private val statusMessage = MutableStateFlow<String?>(null)
    private val deviceCaps = deviceProfiler.snapshot()
    private val engineCaps = inferenceEngine.capabilities()

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.preferencesFlow,
        ttsManager.state,
        validationErrors,
        statusMessage
    ) { prefs, ttsState, errors, status ->
        SettingsUiState(
            preferences = prefs,
            deviceCapabilities = deviceCaps,
            engineCapabilities = engineCaps,
            availableTtsVoices = ttsState.availableVoices,
            validationErrors = errors,
            statusMessage = status
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            deviceCapabilities = deviceCaps,
            engineCapabilities = engineCaps
        )
    )

    fun saveInferenceSettings(updated: InferenceSettings) {
        val validation = InferenceSettingsValidator.validate(
            settings = updated,
            hasGpuSupport = engineCaps.supportsGpuOffload,
            maxCpuCores = deviceCaps.cpuCores
        )
        validationErrors.value = validation.errors
        viewModelScope.launch {
            settingsRepository.updateInferenceSettings(validation.sanitized)
            if (validation.isValid) {
                statusMessage.value = "Inference settings saved."
            }
        }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            validationErrors.value = emptyList()
            settingsRepository.resetInferenceDefaults()
            statusMessage.value = "Reset inference settings to recommended defaults."
        }
    }

    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOfflineMode(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setHuggingFaceToken(token: String) {
        viewModelScope.launch {
            settingsRepository.setHuggingFaceToken(token)
            statusMessage.value = if (token.isBlank()) "Hugging Face token cleared." else "Hugging Face token saved locally."
        }
    }

    fun setTtsSettings(voiceName: String, speed: Float) {
        viewModelScope.launch {
            settingsRepository.setTtsSettings(voiceName, speed)
        }
    }

    fun setEnableLocalTools(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setEnableLocalTools(enabled)
        }
    }

    fun setAutoScrollChat(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoScrollChat(enabled)
        }
    }

    fun clearStatus() {
        statusMessage.value = null
    }
}
