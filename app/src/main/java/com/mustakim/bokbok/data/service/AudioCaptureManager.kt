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
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren

/**
 * Manages audio capture from Microphone and Internal System Audio.
 * Mixes both sources in real-time and passes PCM data to the NDK layer for encoding.
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
    private var mixerJob: Job? = null
    
    private val micQueue = ConcurrentLinkedQueue<ShortArray>()
    private val internalQueue = ConcurrentLinkedQueue<ShortArray>()
    private val MAX_QUEUE_SIZE = 50 // Approx 1 second of audio at 20ms blocks
    
    private var micRecord: AudioRecord? = null
    private var internalRecord: AudioRecord? = null
    
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var automaticGainControl: AutomaticGainControl? = null

    // Residue buffers for synchronization
    private val micResidue = mutableListOf<Short>()
    private val internalResidue = mutableListOf<Short>()

    // Audio configuration (Highest fidelity for mobile)
    private val SAMPLE_RATE = 48000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    
    // Software processing constants
    private val INTERNAL_GAIN = 0.5f        // Lowered game volume to prevent drowning out voice
    private val SILENCE_THRESHOLD_RMS = 30  // Slightly more sensitive for CAMCORDER
    private val LIMITER_THRESHOLD = 28000   // Peak threshold before compression
    
    // Noise Gate Hysteresis (Hold/Release)
    private var gateHoldFrames = 0
    private val GATE_HOLD_LIMIT = 10        // ~200ms hold (Slightly longer for natural speech)
    private var isGateOpen = false
    
    // HPF State (Butterworth 80Hz at 48kHz)
    @Volatile private var hpfLastInput = 0f
    @Volatile private var hpfLastOutput = 0f
    private val hpfAlpha = 0.989f // Approx 80Hz cutoff at 48kHz
    
    // 10ms block size for AEC3 compatibility at 48kHz
    private val BUFFER_SIZE_SAMPLES = 480 * 2 // 10ms @ 48kHz Stereo

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
                // Prefer UNPROCESSED for rawer audio when available (Phase 2.1)
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val audioSource = if (audioManager.getProperty(android.media.AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) != null) {
                    Timber.i("Using UNPROCESSED audio source for highest fidelity")
                    MediaRecorder.AudioSource.UNPROCESSED
                } else {
                    Timber.i("UNPROCESSED not available, falling back to CAMCORDER")
                    MediaRecorder.AudioSource.CAMCORDER
                }

                val minBufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
                micRecord = AudioRecord(
                    audioSource,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    max(minBufSize, BUFFER_SIZE_SAMPLES * 2)
                )
                
                if (micRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    val sessionId = micRecord!!.audioSessionId
                    
                    // Enable Echo Cancellation if available
                    if (AcousticEchoCanceler.isAvailable()) {
                        echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply {
                            enabled = true
                        }
                        Timber.d("Echo Canceler enabled")
                    }
                    
                    // Enable Noise Suppression if available
                    if (NoiseSuppressor.isAvailable()) {
                        noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply {
                            enabled = true
                        }
                        Timber.d("Noise Suppressor enabled")
                    }
                    
                    // Hardware gain control is disabled to ensure "normal" raw capture
                    // We handle amplification manually in NDK after cleaning the signal.
                    /*
                    if (AutomaticGainControl.isAvailable()) {
                        automaticGainControl = AutomaticGainControl.create(sessionId)?.apply {
                            enabled = true
                        }
                        Timber.d("Hardware AGC enabled")
                    }
                    */
 
                    micRecord?.startRecording()
                    micJob = serviceScope.launch {
                        captureSource(micRecord!!, true)
                    }
                }
            } catch (e: Exception) { Timber.e(e, "Error initializing mic") }
        }

        // Setup Internal Audio (Android 10+)
        if (includeInternal && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && mediaProjection != null) {
            try {
                internalRecord = AudioRecord.Builder()
                    .setAudioFormat(AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build())
                    .setBufferSizeInBytes(max(AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT), BUFFER_SIZE_SAMPLES * 2))
                    .setAudioPlaybackCaptureConfig(AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .build())
                    .build()

                if (internalRecord?.state == AudioRecord.STATE_INITIALIZED) {
                    internalRecord?.startRecording()
                    internalJob = serviceScope.launch {
                        captureSource(internalRecord!!, false)
                    }
                }
            } catch (e: Exception) { Timber.e(e, "Error initializing internal audio") }
        }

        // 3. Start Mixer Job if both are present
        mixerJob = serviceScope.launch(Dispatchers.Default) {
            runMixerLoop(includeMic && includeInternal)
        }
    }

    private suspend fun captureSource(record: AudioRecord, isMic: Boolean) {
        val buffer = ShortArray(BUFFER_SIZE_SAMPLES)
        while (isRecording.get()) {
            if (isPaused.get()) {
                delay(100)
                continue
            }
            val read = record.read(buffer, 0, BUFFER_SIZE_SAMPLES)
            if (read > 0) {
                val data = buffer.copyOf(read)
                val queue = if (isMic) micQueue else internalQueue
                
                if (queue.size >= MAX_QUEUE_SIZE) {
                    queue.poll() // Drop oldest to prevent OOM
                }
                queue.offer(data)
            }
        }
    }

    private suspend fun runMixerLoop(isDualSource: Boolean) {
        while (isRecording.get()) {
            if (isDualSource) {
                // Populate residues from queues
                while (!micQueue.isEmpty()) {
                    micQueue.poll()?.let { micResidue.addAll(it.toList()) }
                }
                while (!internalQueue.isEmpty()) {
                    internalQueue.poll()?.let { internalResidue.addAll(it.toList()) }
                }

                // Process in 10ms blocks (960 shorts for stereo 48kHz)
                while (micResidue.size >= BUFFER_SIZE_SAMPLES || internalResidue.size >= BUFFER_SIZE_SAMPLES) {
                    val micBlock = ShortArray(BUFFER_SIZE_SAMPLES)
                    val intBlock = ShortArray(BUFFER_SIZE_SAMPLES)
                    
                    // Fallback: If one stream is missing, pad with silence
                    val hasMic = micResidue.size >= BUFFER_SIZE_SAMPLES
                    val hasInternal = internalResidue.size >= BUFFER_SIZE_SAMPLES

                    // Only process if at least ONE stream has data, AND we aren't drifting too far.
                    // If we have ONLY mic and it's getting huge, process it. 
                    // If we have BOTH, process them together (standard sync).
                    if (hasMic && hasInternal) {
                        for (i in 0 until BUFFER_SIZE_SAMPLES) {
                            micBlock[i] = micResidue[i]
                            intBlock[i] = internalResidue[i]
                        }
                        micResidue.subList(0, BUFFER_SIZE_SAMPLES).clear()
                        internalResidue.subList(0, BUFFER_SIZE_SAMPLES).clear()
                    } else if (hasMic && micResidue.size > BUFFER_SIZE_SAMPLES * 3) {
                        // Mic is way ahead (Internal stalled), mix mic with silence
                        for (i in 0 until BUFFER_SIZE_SAMPLES) {
                            micBlock[i] = micResidue[i]
                            intBlock[i] = 0
                        }
                        micResidue.subList(0, BUFFER_SIZE_SAMPLES).clear()
                        Timber.w("Internal audio lagging/silent, mixing Mic with silence")
                    } else if (hasInternal && internalResidue.size > BUFFER_SIZE_SAMPLES * 3) {
                        // Internal is way ahead (Mic stalled), mix internal with silence
                        for (i in 0 until BUFFER_SIZE_SAMPLES) {
                            micBlock[i] = 0
                            intBlock[i] = internalResidue[i]
                        }
                        internalResidue.subList(0, BUFFER_SIZE_SAMPLES).clear()
                        Timber.w("Microphone lagging/silent, mixing Internal with silence")
                    } else {
                        // Not enough data yet to decide if we should fallback
                        break
                    }
                    
                    nativeRecorder.writeAudioSamples(micBlock, intBlock, BUFFER_SIZE_SAMPLES)
                }
                
                // If nothing to process, sleep briefly
                delay(5)
            } else {
                // Just drain whichever one is active
                val data = micQueue.poll() ?: internalQueue.poll()
                if (data != null) {
                    nativeRecorder.writeAudioBuffer(data, data.size)
                } else {
                    delay(10)
                }
            }
        }
    }

    private fun processAndWrite(mic: ShortArray, internal: ShortArray) {
        val length = Math.min(mic.size, internal.size)
        // Send both to NDK for software AEC + Mixing
        nativeRecorder.writeAudioSamples(mic, internal, length)
    }

    fun stopCapture() {
        isRecording.set(false)
        isPaused.set(false)
        serviceScope.coroutineContext.cancelChildren()
        
        micQueue.clear()
        internalQueue.clear()
        micResidue.clear()
        internalResidue.clear()
        
        listOf(micRecord, internalRecord).forEach {
            try {
                it?.stop()
                it?.release()
            } catch (_: Exception) {}
        }
        
        echoCanceler?.release()
        noiseSuppressor?.release()
        automaticGainControl?.release()
        echoCanceler = null
        noiseSuppressor = null
        automaticGainControl = null
        
        micRecord = null
        internalRecord = null
    }

    fun pauseCapture() {
        isPaused.set(true)
        micQueue.clear()
        internalQueue.clear()
    }

    fun resumeCapture() {
        isPaused.set(false)
    }
}
