package com.lakhyadeep.papai.domain.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class VoiceInputState(
    val isAvailable: Boolean = false,
    val isOfflineCapable: Boolean = false,
    val isListening: Boolean = false,
    val partialText: String = "",
    val errorMessage: String? = null,
    val statusNote: String = ""
)

class VoiceInputManager(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _state = MutableStateFlow(
        VoiceInputState(
            isAvailable = SpeechRecognizer.isRecognitionAvailable(context),
            isOfflineCapable = checkOfflineSupport(),
            statusNote = buildStatusNote()
        )
    )
    val state: StateFlow<VoiceInputState> = _state.asStateFlow()

    private fun checkOfflineSupport(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        } else {
            false
        }
    }

    private fun buildStatusNote(): String {
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        if (!available) return "Speech recognition service unavailable on this device."
        return if (checkOfflineSupport()) {
            "On-device offline speech recognition supported."
        } else {
            "Android Speech service may require an internet connection on this device."
        }
    }

    fun startListening(onFinalResult: (String) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            _state.value = _state.value.copy(
                errorMessage = "No speech recognition service is installed on this device."
            )
            return
        }

        stopListening()

        val sr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
        recognizer = sr

        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _state.value = _state.value.copy(
                    isListening = true,
                    partialText = "",
                    errorMessage = null
                )
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                _state.value = _state.value.copy(isListening = false)
            }

            override fun onError(error: Int) {
                val msg = mapRecognizerError(error)
                _state.value = _state.value.copy(
                    isListening = false,
                    errorMessage = msg
                )
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val best = matches?.firstOrNull()?.trim().orEmpty()
                _state.value = _state.value.copy(
                    isListening = false,
                    partialText = ""
                )
                if (best.isNotEmpty()) {
                    onFinalResult(best)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val partial = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                _state.value = _state.value.copy(partialText = partial)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }

        runCatching {
            sr.startListening(intent)
        }.onFailure { err ->
            _state.value = _state.value.copy(
                isListening = false,
                errorMessage = "Could not start microphone recognition: ${err.message}"
            )
        }
    }

    fun stopListening() {
        runCatching {
            recognizer?.stopListening()
            recognizer?.destroy()
        }
        recognizer = null
        _state.value = _state.value.copy(isListening = false)
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun mapRecognizerError(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech client stopped."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech service requires internet on this device (offline language pack unavailable)."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that. Please try speaking again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Try again shortly."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected."
        else -> "Speech recognition error (code $code)."
    }
}
