package com.lakhyadeep.papai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.lakhyadeep.papai.AppContainer

class PapiViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(ChatViewModel::class.java) -> ChatViewModel(
                conversationDao = container.database.conversationDao(),
                messageDao = container.database.messageDao(),
                assistantDao = container.database.assistantDao(),
                modelDao = container.database.modelDao(),
                settingsRepository = container.settingsRepository,
                inferenceEngine = container.inferenceEngine,
                deviceProfiler = container.deviceProfiler,
                localToolRegistry = container.localToolRegistry,
                voiceInputManager = container.voiceInputManager,
                ttsManager = container.ttsManager
            ) as T

            modelClass.isAssignableFrom(ModelsViewModel::class.java) -> ModelsViewModel(
                modelDao = container.database.modelDao(),
                huggingFaceRepository = container.huggingFaceRepository,
                downloadManager = container.modelDownloadManager,
                importManager = container.modelImportManager,
                inferenceEngine = container.inferenceEngine,
                deviceProfiler = container.deviceProfiler,
                settingsRepository = container.settingsRepository
            ) as T

            modelClass.isAssignableFrom(AssistantsViewModel::class.java) -> AssistantsViewModel(
                assistantDao = container.database.assistantDao(),
                modelDao = container.database.modelDao(),
                conversationDao = container.database.conversationDao(),
                settingsRepository = container.settingsRepository
            ) as T

            modelClass.isAssignableFrom(BenchmarkViewModel::class.java) -> BenchmarkViewModel(
                benchmarkDao = container.database.benchmarkDao(),
                modelDao = container.database.modelDao(),
                inferenceEngine = container.inferenceEngine,
                deviceProfiler = container.deviceProfiler,
                settingsRepository = container.settingsRepository
            ) as T

            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(
                settingsRepository = container.settingsRepository,
                inferenceEngine = container.inferenceEngine,
                deviceProfiler = container.deviceProfiler,
                ttsManager = container.ttsManager
            ) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
