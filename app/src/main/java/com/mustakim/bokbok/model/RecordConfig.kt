package com.mustakim.bokbok.model

/**
 * Power mode preset for screen recording.
 */
enum class PowerMode(val fps: Int, val bitrate: Int, val description: String) {
    POWER_SAVER(30, 10_000_000, "30 FPS / 10 Mbps"),
    BALANCED(60, 20_000_000, "60 FPS / 20 Mbps"),
    STUDIO(60, 50_000_000, "60 FPS / 50 Mbps (HEVC)")
}

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
    val bitrateName: String = "20 Mbps",
    val powerMode: PowerMode = PowerMode.BALANCED,
    val internalAudioRatio: Float = 1.0f,  // 0.0 to 1.0 (1.0 = full volume)
    val micAudioRatio: Float = 1.0f        // 0.0 to 1.0 (1.0 = full volume)
) {
    companion object {
        /**
         * Create a config from a power mode preset.
         */
        fun fromPowerMode(mode: PowerMode, width: Int, height: Int, includeMic: Boolean = true, includeInternal: Boolean = true): RecordConfig {
            return RecordConfig(
                width = width,
                height = height,
                bitrate = mode.bitrate,
                fps = mode.fps,
                useHevc = mode == PowerMode.STUDIO, // HEVC for studio quality
                includeMic = includeMic,
                includeInternal = includeInternal,
                resolutionName = "${height}p",
                bitrateName = "${mode.bitrate / 1_000_000} Mbps",
                powerMode = mode
            )
        }
    }
}
