package com.mustakim.bokbok.model

import kotlinx.serialization.Serializable

/**
 * A user-created custom recording profile.
 * Stores a subset of RecordConfig fields that define the "core" quality settings.
 */
@Serializable
data class CustomRecordingProfile(
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val width: Int,
    val height: Int,
    val resolutionName: String,
    val bitrate: Int,
    val bitrateName: String,
    val fps: Int,
    val useHevc: Boolean,
    val includeMic: Boolean,
    val includeInternal: Boolean,
    val micAudioRatio: Float,
    val internalAudioRatio: Float,
    val isMono: Boolean
) {
    /**
     * Apply this profile to a RecordConfig, returning a new config with these settings.
     */
    fun applyTo(config: RecordConfig): RecordConfig {
        return config.copy(
            width = width,
            height = height,
            resolutionName = resolutionName,
            bitrate = bitrate,
            bitrateName = bitrateName,
            fps = fps,
            useHevc = useHevc,
            includeMic = includeMic,
            includeInternal = includeInternal,
            micAudioRatio = micAudioRatio,
            internalAudioRatio = internalAudioRatio,
            isMono = isMono,
            profile = RecordingProfile.CUSTOM
        )
    }

    companion object {
        /**
         * Create a CustomRecordingProfile from a RecordConfig.
         */
        fun fromConfig(name: String, config: RecordConfig): CustomRecordingProfile {
            return CustomRecordingProfile(
                name = name,
                width = config.width,
                height = config.height,
                resolutionName = config.resolutionName,
                bitrate = config.bitrate,
                bitrateName = config.bitrateName,
                fps = config.fps,
                useHevc = config.useHevc,
                includeMic = config.includeMic,
                includeInternal = config.includeInternal,
                micAudioRatio = config.micAudioRatio,
                internalAudioRatio = config.internalAudioRatio,
                isMono = config.isMono
            )
        }
    }
}
