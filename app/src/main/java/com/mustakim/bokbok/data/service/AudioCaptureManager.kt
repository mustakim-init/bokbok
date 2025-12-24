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
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Manages audio capture from Microphone and Internal System Audio.
 * Mixes both sources in real-time and passes PCM data to the NDK layer for encoding.
 */
class AudioCaptureManager(
    private val context: Context,
    private val nativeRecorder: NativeRecorder
) {
    private var isRecording = AtomicBoolean(false)
    private var micJob: Job? = null
    private var internalJob: Job? = null
    
    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null

    // Audio configuration (Highest fidelity for mobile)
    private val SAMPLE_RATE = 48000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    // Smaller buffer size for lower latency and more frequent NDK pushes
    private val BUFFER_SIZE_SAMPLES = 1024 * 2 // ~21ms at 48kHz

    @SuppressLint("MissingPermission")
    fun startCapture(
        includeMic: Boolean,
        includeInternal: Boolean,
        mediaProjection: MediaProjection?
    ) {
        if (isRecording.getAndSet(true)) return

        // Setup Microphone
        if (includeMic && ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            try {
                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                micRecord = AudioRecord(
                    MediaRecorder.AudioSource.CAMCORDER, // High quality / Camcorder tuning
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    max(minBufSize, BUFFER_SIZE_SAMPLES * 2)
                )
                if (micRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    micRecord?.startRecording()
                    micJob = CoroutineScope(Dispatchers.IO).launch {
                        captureSource(micRecord!!, true)
                    }
                }
            } catch (e: Exception) { Timber.e(e, "Error initializing mic") }
        }

        // Setup Internal Audio (Android 10+)
        if (includeInternal && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && mediaProjection != null) {
            try {
                val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .build()

                internalRecord = AudioRecord.Builder()
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                    .setBufferSizeInBytes(max(AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT), BUFFER_SIZE_SAMPLES * 2))
                    .setAudioPlaybackCaptureConfig(playbackConfig)
                    .build()

                if (internalRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    internalRecord?.startRecording()
                    internalJob = CoroutineScope(Dispatchers.IO).launch {
                        captureSource(internalRecord!!, false)
                    }
                }
            } catch (e: Exception) { Timber.e(e, "Error initializing internal audio") }
        }
    }

    private val mixerLock = Any()
    // Separate buffers for independent capture
    private var micPending: ShortArray? = null
    private var internalPending: ShortArray? = null

    /**
     * Captures a single source. To prevent the sequential blocking bottleneck, 
     * each source runs in its own thread and pushes mixed samples as they arrive.
     */
    private suspend fun captureSource(record: AudioRecord, isMic: Boolean) {
        val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
        
        while (isRecording.get()) {
            val read = record.read(buffer, 0, BUFFER_SIZE_SAMPLES)
            if (read > 0) {
                synchronized(mixerLock) {
                    if (micRecord != null && internalRecord != null) {
                        // Both active: Wait for buddy and mix
                        if (isMic) {
                            micPending = buffer.clone()
                            internalPending?.let { intBuf ->
                                performMixAndWrite(buffer, intBuf, read)
                                micPending = null
                                internalPending = null
                            }
                        } else {
                            internalPending = buffer.clone()
                            micPending?.let { micBuf ->
                                performMixAndWrite(micBuf, buffer, read)
                                micPending = null
                                internalPending = null
                            }
                        }
                    } else {
                        // Only one active: Direct write
                        nativeRecorder.writeAudioBuffer(buffer.copyOf(read), read)
                    }
                }
            }
        }
    }

    private fun performMixAndWrite(mic: ShortArray, internal: ShortArray, length: Int) {
        val mixed = ShortArray(length)
        for (i in 0 until length) {
            val m = mic[i].toInt()
            val n = internal[i].toInt()
            // High fidelity mixing: Simple sum with overflow protection
            mixed[i] = max(Short.MIN_VALUE.toInt(), min(Short.MAX_VALUE.toInt(), m + n)).toShort()
        }
        nativeRecorder.writeAudioBuffer(mixed, length)
    }

    fun stopCapture() {
        isRecording.set(false)
        micJob?.cancel()
        internalJob?.cancel()
        
        listOf(micRecord, internalRecord).forEach {
            try {
                it?.stop()
                it?.release()
            } catch (_: Exception) {}
        }
        micRecord = null
        internalRecord = null
    }
}
