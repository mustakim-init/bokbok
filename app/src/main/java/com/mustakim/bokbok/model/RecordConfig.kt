package com.mustakim.bokbok.model

/**
 * Configuration for the screen recording session.
 */
data class RecordConfig(
    val width: Int = 1080,
    val height: Int = 2400,
    val bitrate: Int = 20_000_000,
    val fps: Int = 60,
    val useHevc: Boolean = true,
    val includeMic: Boolean = true,
    val includeInternal: Boolean = true,
    val useCountdown: Boolean = true,
    val resolutionName: String = "1080p",
    val bitrateName: String = "20 Mbps"
)
