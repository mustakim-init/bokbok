package com.mustakim.bokbok.data.webrtc

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log

class AudioRouteController(context: Context) {

    private val tag = "AudioRouteController"
    private val audioManager: AudioManager =
        context.getSystemService(AudioManager::class.java)

    private var focusRequest: AudioFocusRequest? = null
    private var started = false
    private var speakerOn = true

    fun start(defaultToSpeaker: Boolean = true) {
        if (started) return
        started = true
        speakerOn = defaultToSpeaker

        requestAudioFocus()

        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.w(tag, "Failed to set MODE_IN_COMMUNICATION: ${e.message}")
        }

        applySpeakerState()
        Log.d(tag, "Audio routing started, speakerOn=$speakerOn")
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        speakerOn = enabled
        applySpeakerState()
    }

    fun stop() {
        try {
            // Reset speaker and audio mode
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            Log.w(tag, "Error resetting audio mode: ${e.message}")
        }

        abandonAudioFocus()
        started = false
        Log.d(tag, "Audio routing stopped")
    }

    private fun applySpeakerState() {
        try {
            audioManager.isSpeakerphoneOn = speakerOn
            Log.d(tag, "setSpeakerphoneOn($speakerOn)")
        } catch (e: Exception) {
            Log.w(tag, "Failed to set speakerphoneOn: ${e.message}")
        }
    }

    private fun requestAudioFocus() {
        try {
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val afr = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { /* no-op */ }
                    .build()
                focusRequest = afr
                audioManager.requestAudioFocus(afr)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }
            Log.d(tag, "requestAudioFocus result=$result")
        } catch (e: Exception) {
            Log.w(tag, "Error requesting audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error abandoning audio focus: ${e.message}")
        }
        focusRequest = null
    }
}
