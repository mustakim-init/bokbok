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
        forceInternalRef: Boolean,
        mediaProjection: MediaProjection?,
        isMono: Boolean = false
    ) {
        if (isRecording.getAndSet(true)) return

        try {
            // 1. Setup Microphone
            if (includeMic && ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val micChannelConfig = AudioFormat.CHANNEL_IN_MONO
                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, micChannelConfig, AUDIO_FORMAT)
                val bufferSize = max(minBufSize, BUFFER_SIZE_SAMPLES * 8)
                
                micRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    micChannelConfig,
                    AUDIO_FORMAT,
                    bufferSize
                )
                
                if (micRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    micRecord?.release()
                    micRecord = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        micChannelConfig,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                }
                
                if (micRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    micRecord?.startRecording()
                    Timber.i("Mic recording initialized")
                }
            }

            // 2. Setup Internal Audio
            val internalNeeded = (includeInternal || forceInternalRef)
            if (internalNeeded && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && mediaProjection != null) {
                val intChannelConfig = if (isMono) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, intChannelConfig, AUDIO_FORMAT)
                val bufferSize = max(minBufSize, BUFFER_SIZE_SAMPLES * 8)

                internalRecord = AudioRecord.Builder()
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(intChannelConfig)
                        .build())
                    .setBufferSizeInBytes(bufferSize)
                    .setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build())
                    .build()

                if (internalRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    internalRecord?.startRecording()
                    Timber.i("Internal audio initialized")
                }
            }

            // 3. Start Unified Capture Loop
            val intChannels = if (isMono) 1 else 2
            micJob = serviceScope.launch {
                captureLoopSync(intChannels)
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to start unified audio capture")
            isRecording.set(false)
        }
    }

    private suspend fun captureLoopSync(internalChannels: Int) {
        val micBufferSize = BUFFER_SIZE_SAMPLES // Always mono for mic
        val intBufferSize = BUFFER_SIZE_SAMPLES * internalChannels
        
        val micBuffer = ShortArray(micBufferSize)
        val intBuffer = ShortArray(intBufferSize)
        
        val zeroMic = ShortArray(micBufferSize) { 0 }
        val zeroInt = ShortArray(intBufferSize) { 0 }

        Timber.i("Unified audio loop started. Mic=${micRecord != null}, Int=${internalRecord != null}")

        while (isRecording.get()) {
            if (isPaused.get()) {
                // Write digital silence to both channels to lock A/V clock
                nativeRecorder.writeAudioSamples(zeroMic, zeroInt, BUFFER_SIZE_SAMPLES)
                delay(20) // ~21ms for 1024 samples at 48kHz
                continue
            }

            var micRead = 0
            var intRead = 0

            // 1. Read Mic
            if (micRecord != null && micRecord?.state == AudioRecord.STATE_INITIALIZED) {
                micRead = micRecord!!.read(micBuffer, 0, micBufferSize)
            }

            // 2. Read Internal
            if (internalRecord != null && internalRecord?.state == AudioRecord.STATE_INITIALIZED) {
                intRead = internalRecord!!.read(intBuffer, 0, intBufferSize)
            }

            // 3. Synchronized Dispatch
            // We use the requested BUFFER_SIZE_SAMPLES to maintain strict time alignment
            // even if one source returned fewer samples (padding)
            val finalMic = if (micRead > 0) micBuffer else zeroMic
            val finalInt = if (intRead > 0) intBuffer else zeroInt

            // We pass the maximum read amount or a fixed chunk to keep native side stable
            nativeRecorder.writeAudioSamples(finalMic, finalInt, BUFFER_SIZE_SAMPLES)
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
