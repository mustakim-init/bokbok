package com.mustakim.bokbok.model
import com.mustakim.bokbok.model.RecordingProfile

/**
 * Recording profiles for screen recording.
 * Now simplified to a single DEFAULT profile, allowing users to build their own custom ones.
 */
enum class RecordingProfile(
    val title: String,
    val description: String,
    val resolutionName: String,
    val width: Int,
    val height: Int,
    val bitrate: Int,
    val bitrateName: String,
    val fps: Int,
    val useHevc: Boolean,
    val includeMic: Boolean,
    val includeInternal: Boolean,
    val micRatio: Float = 1.0f,
    val internalRatio: Float = 1.0f
) {
    DEFAULT(
        title = "Default",
        description = "Balanced high-quality settings for most use cases.",
        resolutionName = "1080p",
        width = 1080,
        height = 1920,
        bitrate = 12_000_000,
        bitrateName = "12 Mbps",
        fps = 60,
        useHevc = true,
        includeMic = true,
        includeInternal = true,
        micRatio = 1.0f,
        internalRatio = 1.0f
    )
}
