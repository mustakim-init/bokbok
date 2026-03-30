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

    // Opaque handle to the native RecorderContext
    private var nativeHandle: Long = 0

    /**
     * Placeholder to verify JNI connectivity.
     */
    external fun stringFromJNI(): String

    /**
     * Initializes the recorder with professional parameters.
     * Returns a pointer to the native context.
     */
    private external fun nativeSetup(
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
    ): Long

    fun setup(
        width: Int, height: Int, bitrate: Int, fps: Int, useHevc: Boolean,
        audioEnabled: Boolean, videoPath: String, micPath: String,
        internalPath: String, audioSampleRate: Int, audioBitrate: Int
    ): Boolean {
        nativeHandle = nativeSetup(
            width, height, bitrate, fps, useHevc, audioEnabled,
            videoPath, micPath, internalPath, audioSampleRate, audioBitrate
        )
        return nativeHandle != 0L
    }

    /**
     * Provides the Surface that MediaProjection should draw to.
     */
    external fun getInputSurface(handle: Long): Surface?
    fun getInputSurface(): Surface? = if (nativeHandle != 0L) getInputSurface(nativeHandle) else null

    /**
     * Starts the NDK encoding and muxing loop.
     */
    external fun start(handle: Long): Boolean
    fun start(): Boolean = if (nativeHandle != 0L) start(nativeHandle) else false

    /**
     * Pauses the native muxer (handles gapless timestamp adjustment).
     */
    external fun pause(handle: Long): Boolean
    fun pause(): Boolean = if (nativeHandle != 0L) pause(nativeHandle) else false

    /**
     * Resumes the native muxer.
     */
    external fun resume(handle: Long): Boolean
    fun resume(): Boolean = if (nativeHandle != 0L) resume(nativeHandle) else false

    /**
     * Gracefully stops the engine and finalizes the MP4 container.
     */
    external fun stop(handle: Long): Boolean
    fun stop(): Boolean {
        if (nativeHandle == 0L) return false
        val result = stop(nativeHandle)
        nativeHandle = 0 // Handle is invalidated after stop/release
        return result
    }

    /**
     * Processes the raw mic, internal, and video recordings into a final output file.
     * This is a static-like process, it doesn't necessarily need the handle if it's stateless,
     * but we'll include it for consistency or keep it as is if it creates its own temporary context.
     * Looking at PostProcessor.cpp, it doesn't use gCtx.
     */
    external fun processRecording(
        videoFd: Int,
        videoWidth: Int,
        videoHeight: Int,
        micPath: String,
        internalPath: String,
        outputPath: String,
        micExportPath: String,
        internalExportPath: String,
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
    external fun captureScreenshot(handle: Long): Boolean
    fun captureScreenshot(): Boolean = if (nativeHandle != 0L) captureScreenshot(nativeHandle) else false

    /**
     * Releases all native resources.
     */
    external fun nativeRelease(handle: Long)
    fun release() {
        if (nativeHandle != 0L) {
            nativeRelease(nativeHandle)
            nativeHandle = 0
        }
    }

    /**
     * Sends raw PCM audio data (16-bit interleaved stereo) to the native encoder.
     */
    external fun writeAudioBuffer(handle: Long, data: ShortArray, length: Int): Boolean
    fun writeAudioBuffer(data: ShortArray, length: Int): Boolean = 
        if (nativeHandle != 0L) writeAudioBuffer(nativeHandle, data, length) else false
    
    /**
     * Writes separate mic and internal audio samples for AEC processing.
     */
    external fun writeAudioSamples(handle: Long, micData: ShortArray, internalData: ShortArray, length: Int): Boolean
    fun writeAudioSamples(micData: ShortArray, internalData: ShortArray, length: Int): Boolean = 
        if (nativeHandle != 0L) writeAudioSamples(nativeHandle, micData, internalData, length) else false

    /**
     * Gets current audio levels for UI display.
     */
    external fun getAudioLevels(handle: Long): FloatArray
    fun getAudioLevels(): FloatArray = if (nativeHandle != 0L) getAudioLevels(nativeHandle) else floatArrayOf(0f, 0f, 0f, 0f)
    
    /**
     * Sets the mix ratio for internal and mic audio.
     */
    external fun setMixRatio(handle: Long, internalRatio: Float, micRatio: Float)
    fun setMixRatio(internalRatio: Float, micRatio: Float) {
        if (nativeHandle != 0L) setMixRatio(nativeHandle, internalRatio, micRatio)
    }
}
