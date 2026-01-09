package com.mustakim.bokbok.model

/**
 * Predefined recording profiles for different use cases.
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
    GAMING(
        title = "Pro Gaming",
        description = "High FPS, high bitrate, internal audio priority.",
        resolutionName = "1080p",
        width = 1080,
        height = 1920,
        bitrate = 20_000_000,
        bitrateName = "20 Mbps",
        fps = 60,
        useHevc = true,
        includeMic = true,
        includeInternal = true,
        micRatio = 0.6f,
        internalRatio = 1.0f
    ),
    VOICE_OVER(
        title = "Voice Over",
        description = "Balanced quality, microphone priority.",
        resolutionName = "1080p",
        width = 1080,
        height = 1920,
        bitrate = 12_000_000,
        bitrateName = "12 Mbps",
        fps = 30,
        useHevc = false,
        includeMic = true,
        includeInternal = true,
        micRatio = 1.0f,
        internalRatio = 0.4f
    ),
    EFFICIENCY(
        title = "Battery Saver",
        description = "Lower resolution and FPS to save power.",
        resolutionName = "720p",
        width = 720,
        height = 1280,
        bitrate = 6_000_000,
        bitrateName = "6 Mbps",
        fps = 30,
        useHevc = true,
        includeMic = true,
        includeInternal = true
    ),
    CUSTOM(
        title = "Custom",
        description = "Use your own fine-tuned settings.",
        resolutionName = "720p",
        width = 720,
        height = 1280,
        bitrate = 8_000_000,
        bitrateName = "8 Mbps",
        fps = 60,
        useHevc = false,
        includeMic = true,
        includeInternal = true
    )
}
