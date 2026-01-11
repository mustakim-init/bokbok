package com.mustakim.bokbok.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

class SpeechManager(private val context: Context) : RecognitionListener, TextToSpeech.OnInitListener {

    private val speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
    private var textToSpeech: TextToSpeech? = null
    
    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()
    
    // Callback for speech results
    var onSpeechResult: ((String) -> Unit)? = null
    var onSpeechError: ((String) -> Unit)? = null

    init {
        speechRecognizer.setRecognitionListener(this)
        textToSpeech = TextToSpeech(context, this)
    }

    fun startListening() {
        if (_isListening.value) return
        
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        
        try {
            speechRecognizer.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            onSpeechError?.invoke("Could not start listening: ${e.message}")
            _isListening.value = false
        }
    }

    fun stopListening() {
        if (!_isListening.value) return
        
        try {
            speechRecognizer.stopListening()
        } catch (e: Exception) {
            // Ignore
        }
        _isListening.value = false
    }

    fun speak(text: String) {
        textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }
    
    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    fun destroy() {
        speechRecognizer.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    // RecognitionListener
    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}
    override fun onEndOfSpeech() {
        _isListening.value = false
    }

    override fun onError(error: Int) {
        _isListening.value = false
        val errorMessage = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_NETWORK -> "Network error"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permission denied"
            else -> "Speech error: $error"
        }
        // Don't report "No match" as it's common when silence
        if (error != SpeechRecognizer.ERROR_NO_MATCH) {
             onSpeechError?.invoke(errorMessage)
        }
    }

    override fun onResults(results: Bundle?) {
        _isListening.value = false
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
            onSpeechResult?.invoke(matches[0])
        }
    }

    override fun onPartialResults(partialResults: Bundle?) {}
    override fun onEvent(eventType: Int, params: Bundle?) {}

    // TextToSpeech.OnInitListener
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            textToSpeech?.language = Locale.getDefault()
        }
    }
}
