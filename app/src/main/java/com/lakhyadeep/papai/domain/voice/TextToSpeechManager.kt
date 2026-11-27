package com.lakhyadeep.papai.domain.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class TtsVoiceOption(
    val name: String,
    val displayName: String,
    val localeTag: String,
    val isOffline: Boolean
)

data class TtsState(
    val isReady: Boolean = false,
    val isSpeaking: Boolean = false,
    val isPaused: Boolean = false,
    val activeMessageId: String? = null,
    val selectedVoiceName: String = "",
    val speechSpeed: Float = 1.0f,
    val availableVoices: List<TtsVoiceOption> = emptyList(),
    val errorMessage: String? = null
)

class TextToSpeechManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var chunks: List<String> = emptyList()
    private var currentChunkIndex: Int = 0
    private var currentMessageId: String? = null

    private val _state = MutableStateFlow(TtsState())
    val state: StateFlow<TtsState> = _state.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                engine.language = Locale.getDefault()
                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (!_state.value.isPaused && _state.value.isSpeaking) {
                            currentChunkIndex++
                            if (currentChunkIndex < chunks.size) {
                                speakNextChunk()
                            } else {
                                _state.value = _state.value.copy(
                                    isSpeaking = false,
                                    isPaused = false,
                                    activeMessageId = null
                                )
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _state.value = _state.value.copy(
                            isSpeaking = false,
                            isPaused = false,
                            errorMessage = "Text-to-Speech playback error."
                        )
                    }
                })

                val voiceOptions = engine.voices
                    ?.filter { it.locale != null }
                    ?.sortedWith(
                        compareByDescending<Voice> { !it.isNetworkConnectionRequired }
                            .thenByDescending { it.locale.language == Locale.getDefault().language }
                            .thenBy { it.name }
                    )
                    ?.take(30)
                    ?.map { v ->
                        val offlineLabel = if (v.isNetworkConnectionRequired) "Online" else "Offline"
                        TtsVoiceOption(
                            name = v.name,
                            displayName = "${v.locale.displayName} • $offlineLabel",
                            localeTag = v.locale.toLanguageTag(),
                            isOffline = !v.isNetworkConnectionRequired
                        )
                    }
                    .orEmpty()

                val defaultVoice = voiceOptions.firstOrNull { it.isOffline }?.name
                    ?: voiceOptions.firstOrNull()?.name.orEmpty()

                _state.value = _state.value.copy(
                    isReady = true,
                    selectedVoiceName = _state.value.selectedVoiceName.ifEmpty { defaultVoice },
                    availableVoices = voiceOptions
                )
            } else {
                _state.value = _state.value.copy(
                    isReady = false,
                    errorMessage = "Android Text-to-Speech engine is unavailable."
                )
            }
        }
    }

    fun applyConfig(voiceName: String, speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(clampedSpeed)
        if (voiceName.isNotBlank()) {
            val match = tts?.voices?.firstOrNull { it.name == voiceName }
            if (match != null) {
                tts?.voice = match
            }
        }
        _state.value = _state.value.copy(
            selectedVoiceName = voiceName,
            speechSpeed = clampedSpeed
        )
    }

    fun speak(messageId: String, text: String) {
        if (!_state.value.isReady) {
            _state.value = _state.value.copy(errorMessage = "TTS engine is still initializing or unavailable.")
            return
        }
        val cleanText = stripMarkdownForSpeech(text)
        if (cleanText.isBlank()) return

        stop()
        currentMessageId = messageId
        chunks = splitIntoSpeakableChunks(cleanText)
        currentChunkIndex = 0

        tts?.setSpeechRate(_state.value.speechSpeed)
        _state.value = _state.value.copy(
            isSpeaking = true,
            isPaused = false,
            activeMessageId = messageId,
            errorMessage = null
        )
        speakNextChunk()
    }

    fun pause() {
        if (_state.value.isSpeaking && !_state.value.isPaused) {
            tts?.stop()
            _state.value = _state.value.copy(isPaused = true, isSpeaking = false)
        }
    }

    fun resume() {
        if (_state.value.isPaused && currentChunkIndex < chunks.size) {
            _state.value = _state.value.copy(isPaused = false, isSpeaking = true)
            speakNextChunk()
        }
    }

    fun stop() {
        tts?.stop()
        chunks = emptyList()
        currentChunkIndex = 0
        currentMessageId = null
        _state.value = _state.value.copy(
            isSpeaking = false,
            isPaused = false,
            activeMessageId = null
        )
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }

    private fun speakNextChunk() {
        val chunk = chunks.getOrNull(currentChunkIndex) ?: return
        val params = Bundle()
        tts?.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, "papi_tts_$currentChunkIndex")
    }

    private fun stripMarkdownForSpeech(raw: String): String {
        return raw
            .replace(Regex("```[\\s\\S]*?```"), " Code block omitted. ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("[#*_~>]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun splitIntoSpeakableChunks(text: String): List<String> {
        val sentences = text.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        if (sentences.isEmpty()) return listOf(text)
        val result = mutableListOf<String>()
        val buf = StringBuilder()
        for (s in sentences) {
            if (buf.length + s.length > 350 && buf.isNotEmpty()) {
                result.add(buf.toString().trim())
                buf.clear()
            }
            buf.append(s).append(" ")
        }
        if (buf.isNotEmpty()) result.add(buf.toString().trim())
        return result
    }
}
