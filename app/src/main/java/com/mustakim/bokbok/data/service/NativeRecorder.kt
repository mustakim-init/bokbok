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
        outputPath: String
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
}
