package com.mustakim.bokbok.data.service

import android.view.Surface

/**
 * Kotlin bridge to the C++ Screen Recording Engine.
 * Handles AMediaCodec, AMediaMuxer, and Oboe mixing in the NDK layer.
 */
class NativeRecorder {
    companion object {
        init {
            System.loadLibrary("native-recorder")
        }
    }

    /**
     * Placeholder to verify JNI connectivity.
     */
    external fun stringFromJNI(): String

    /**
     * Initializes the recorder with professional parameters.
     */
    external fun setup(
        width: Int,
        height: Int,
        bitrate: Int,
        fps: Int,
        useHevc: Boolean,
        audioEnabled: Boolean, 
        videoPath: String,
        micPath: String,
        internalPath: String,
        audioSampleRate: Int,
        audioBitrate: Int
    ): Boolean

    /**
     * Provides the Surface that MediaProjection should draw to.
     */
    external fun getInputSurface(): Surface?

    /**
     * Starts the NDK encoding and muxing loop.
     */
    external fun start(): Boolean

    /**
     * Pauses the native muxer (handles gapless timestamp adjustment).
     */
    external fun pause(): Boolean

    /**
     * Resumes the native muxer.
     */
    external fun resume(): Boolean

    /**
     * Gracefully stops the engine and finalizes the MP4 container.
     */
    external fun stop(): Boolean

    /**
     * Processes the raw mic, internal, and video recordings into a final output file.
     */
    external fun processRecording(
        videoFd: Int,
        videoWidth: Int,
        videoHeight: Int,
        micPath: String,
        internalPath: String,
        outputPath: String,
        micExportPath: String,   // Added
        internalExportPath: String, // Added
        modelPath: String,
        enableBleed: Boolean,
        enableNoise: Boolean,
        enableStudioMaster: Boolean,
        micGain: Float,
        internalGain: Float,
        exportMic: Boolean,
        exportInternal: Boolean,
        isMono: Boolean,
        audioSampleRate: Int,
        audioBitrate: Int
    ): Boolean

    // Listener for progress updates
    var onProgressUpdate: ((Float, String) -> Unit)? = null

    // Called from JNI
    fun onProcessProgress(progress: Float, message: String) {
        onProgressUpdate?.invoke(progress, message)
    }

    /**
     * Captures a still frame from the current surface buffer.
     */
    external fun captureScreenshot(): Boolean

    /**
     * Configures the Oboe audio engine.
     */
    external fun configureAudio(
        sampleRate: Int,
        channelCount: Int,
        includeMic: Boolean,
        includeInternal: Boolean
    ): Boolean

    /**
     * Releases all native resources.
     */
    /**
     * Releases all native resources.
     */
    external fun release()

    /**
     * Sends raw PCM audio data (16-bit interleaved stereo) to the native encoder.
     */
    external fun writeAudioBuffer(data: ShortArray, length: Int): Boolean
    
    /**
     * Writes separate mic and internal audio samples for AEC processing.
     */
    external fun writeAudioSamples(micData: ShortArray, internalData: ShortArray, length: Int): Boolean

    /**
     * Gets current audio levels for UI display.
     * Returns FloatArray: [micRms, micPeak, internalRms, internalPeak]
     */
    external fun getAudioLevels(): FloatArray
    
    /**
     * Sets the mix ratio for internal and mic audio.
     * Both values are 0.0 to 1.0.
     * @param internalRatio Volume of internal/game audio (1.0 = full, 0.0 = muted)
     * @param micRatio Volume of processed mic audio (1.0 = full, 0.0 = muted)
     */
    external fun setMixRatio(internalRatio: Float, micRatio: Float)
}
