package com.lakhyadeep.papai

import android.app.Application
import android.content.ComponentCallbacks2
import com.lakhyadeep.papai.data.local.db.PapiDatabase
import com.lakhyadeep.papai.data.local.db.PredefinedData
import com.lakhyadeep.papai.data.local.prefs.SettingsRepository
import com.lakhyadeep.papai.data.remote.HuggingFaceRepository
import com.lakhyadeep.papai.data.remote.ModelDownloadManager
import com.lakhyadeep.papai.data.remote.ModelImportManager
import com.lakhyadeep.papai.domain.device.DeviceProfiler
import com.lakhyadeep.papai.domain.inference.InferenceEngine
import com.lakhyadeep.papai.domain.inference.LlamatikInferenceEngine
import com.lakhyadeep.papai.domain.tools.LocalToolRegistry
import com.lakhyadeep.papai.domain.voice.TextToSpeechManager
import com.lakhyadeep.papai.domain.voice.VoiceInputManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(application: Application) {
    val database: PapiDatabase = PapiDatabase.getInstance(application)
    val settingsRepository: SettingsRepository = SettingsRepository(application)
    val deviceProfiler: DeviceProfiler = DeviceProfiler(application)
    val inferenceEngine: InferenceEngine = LlamatikInferenceEngine()
    val localToolRegistry: LocalToolRegistry = LocalToolRegistry()
    val voiceInputManager: VoiceInputManager = VoiceInputManager(application)
    val ttsManager: TextToSpeechManager = TextToSpeechManager(application)

    @Volatile
    var cachedHfToken: String = ""

    val huggingFaceRepository: HuggingFaceRepository = HuggingFaceRepository { cachedHfToken }
    val modelDownloadManager: ModelDownloadManager = ModelDownloadManager(
        modelDao = database.modelDao(),
        deviceProfiler = deviceProfiler,
        tokenProvider = { cachedHfToken }
    )
    val modelImportManager: ModelImportManager = ModelImportManager(
        context = application,
        modelDao = database.modelDao(),
        deviceProfiler = deviceProfiler
    )
}

class PapiAiApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        appScope.launch {
            // Ensure predefined assistants exist even on existing/upgraded DBs
            container.database.assistantDao().insertAllIgnore(PredefinedData.assistants())

            // Reset stale loaded/downloading flags from any previous crash or restart
            container.database.modelDao().clearAllLoadedFlags()

            // Keep HF token cached for OkHttp/Retrofit interceptors and configure TTS
            container.settingsRepository.preferencesFlow.collect { prefs ->
                container.cachedHfToken = prefs.huggingFaceToken
                container.ttsManager.applyConfig(prefs.preferredTtsVoice, prefs.ttsSpeed)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
            level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE
        ) {
            handleMemoryPressure()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        handleMemoryPressure()
    }

    private fun handleMemoryPressure() {
        if (container.inferenceEngine.isGenerating.value) {
            container.inferenceEngine.stopGeneration()
        }
        appScope.launch {
            container.inferenceEngine.unloadModel()
            container.database.modelDao().clearAllLoadedFlags()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        container.ttsManager.shutdown()
    }
}
