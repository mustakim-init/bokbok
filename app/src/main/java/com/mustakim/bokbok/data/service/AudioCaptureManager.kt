package com.mustakim.bokbok.data.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Manages raw audio capture from Microphone and Internal System Audio.
 * Writes raw PCM 16-bit streams directly to storage via NativeRecorder.
 */
class AudioCaptureManager(
    private val context: Context,
    private val nativeRecorder: NativeRecorder
) {
    private var isRecording = AtomicBoolean(false)
    private var isPaused = AtomicBoolean(false)
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var micJob: Job? = null
    private var internalJob: Job? = null
    
    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    
    // Audio configuration (Highest fidelity for mobile)
    private val SAMPLE_RATE = 48000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    // Buffer size (can be larger now since latency is less critical)
    private val BUFFER_SIZE_SAMPLES = 2048 

    @SuppressLint("MissingPermission")
    fun startCapture(
        includeMic: Boolean,
        includeInternal: Boolean,
        forceInternalRef: Boolean, // New parameter to force internal capture for AEC reference
        mediaProjection: MediaProjection?
    ) {
        if (isRecording.getAndSet(true)) return

        // Setup Microphone
        if (includeMic && ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                // Use MIC source - most reliable across devices
                val audioSource = MediaRecorder.AudioSource.MIC

                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val bufferSize = max(minBufSize, BUFFER_SIZE_SAMPLES * 8)
                
                micRecord = AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                
                if (micRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Timber.w("MIC source failed to initialize, trying VOICE_RECOGNITION")
                    micRecord?.release()
                    micRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                }
                
                if (micRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    micRecord?.startRecording()
                    Timber.i("Mic recording started successfully")
                    micJob = serviceScope.launch {
                        captureLoop(micRecord!!, true)
                    }
                } else {
                    Timber.e("Failed to initialize any mic source")
                }
            } catch (e: Exception) { Timber.e(e, "Error initializing mic") }
        }

        // Setup Internal Audio (Android 10+)
        // Logic: Start internal capture if requested OR if forced as an AEC reference
        val internalNeeded = (includeInternal || forceInternalRef)
        if (internalNeeded && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && mediaProjection != null) {
            try {
                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                val bufferSize = max(minBufSize, BUFFER_SIZE_SAMPLES * 8)

                internalRecord = AudioRecord.Builder()
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build())
                    .build()

                if (internalRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    internalRecord?.startRecording()
                    Timber.i("Internal audio recording started successfully (RefOnly=$forceInternalRef)")
                    internalJob = serviceScope.launch {
                        captureLoop(internalRecord!!, false)
                    }
                } else {
                    Timber.e("Failed to initialize internal audio record")
                }
            } catch (e: Exception) { Timber.e(e, "Error initializing internal audio") }
        }
    }

    private suspend fun captureLoop(record: AudioRecord, isMic: Boolean) {
        val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
        val emptyInternal = ShortArray(0) 
        val emptyMic = ShortArray(0)

        while (isRecording.get()) {
            if (isPaused.get()) {
                // To maintain perfect A/V sync, we MUST continue writing samples,
                // otherwise the audio/wall-clock time will drift away from video timestamps.
                // We write a buffer of silence (zeros).
                val silence = ShortArray(BUFFER_SIZE_SAMPLES) { 0 }
                if (isMic) {
                    nativeRecorder.writeAudioSamples(silence, emptyInternal, BUFFER_SIZE_SAMPLES)
                } else {
                    nativeRecorder.writeAudioSamples(emptyMic, silence, BUFFER_SIZE_SAMPLES)
                }
                // Sleep for the duration of a frame to avoid CPU thrashing
                delay(20) // ~48kHz * 1024 samples is roughly 21.3ms
                continue
            }
            
            // Blocking read (efficient)
            val read = record.read(buffer, 0, BUFFER_SIZE_SAMPLES)
            if (read > 0) {
                if (isMic) {
                    nativeRecorder.writeAudioSamples(buffer, emptyInternal, read)
                } else {
                    nativeRecorder.writeAudioSamples(emptyMic, buffer, read)
                }
            }
        }
    }

    fun stopCapture() {
        val wasRecording = isRecording.getAndSet(false)
        if (!wasRecording) return
        
        isPaused.set(false)
        serviceScope.coroutineContext.cancelChildren()
        
        listOf(micRecord, internalRecord).forEach {
            try {
                if (it?.state == AudioRecord.STATE_INITIALIZED) {
                    it.stop()
                    it.release()
                }
            } catch (e: Exception) { Timber.e(e, "Error stopping AudioRecord") }
        }
        
        micRecord = null
        internalRecord = null
    }

    fun pauseCapture() {
        isPaused.set(true)
    }

    fun resumeCapture() {
        isPaused.set(false)
    }
}
