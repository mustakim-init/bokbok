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
    val profile: RecordingProfile = RecordingProfile.CUSTOM,
    val useCountdown: Boolean = true,
    val showFacecam: Boolean = false,
    val exportMicOnly: Boolean = false,
    val exportInternalOnly: Boolean = false,
    val autoStopDurationMinutes: Int = 0, // 0 = disabled
    val autoStopBatteryLevel: Int = 0,    // 0 = disabled
    val useWatermarkText: Boolean = false,
    val watermarkText: String = "BokBok Screen Recorder",
    val useWatermarkImage: Boolean = false,
    val watermarkImagePath: String = "",
    val stopOnScreenOff: Boolean = true,
    val stopOnShake: Boolean = false,
    val autoLaunchPackage: String = "",
    val setVolumeOnStart: Boolean = false,
    val startVolumeLevel: Int = 100,
    val showTouches: Boolean = false,
    val orientationLock: String = "Auto", // Auto, Portrait, Landscape
    val facecamShape: String = "Circle", // Circle, Square
    val audioSampleRate: Int = 48000,
    val audioBitrate: Int = 128000,
    val shakeSensitivity: Float = 20f,
    val resolutionName: String = "1080p",
    val bitrateName: String = "20 Mbps",
    val powerMode: PowerMode = PowerMode.BALANCED,
    val internalAudioRatio: Float = 1.0f,  // 0.0 to 1.0 (1.0 = full volume)
    val micAudioRatio: Float = 1.0f,       // 0.0 to 1.0 (1.0 = full volume)
    
    // Kapture Parity Overlay Settings
    val menuStyle: Int = 0,               // 0: Horizontal, 1: Vertical
    val showShortcuts: Boolean = false,
    val shortcuts: List<String> = emptyList(),
    val minimizingSide: Int = 0,          // 0: Right, 1: Left
    val startMinimized: Boolean = false,
    val showPauseResumeOnMenu: Boolean = true,
    val showCameraButtonOnMenu: Boolean = true,
    val showDrawButtonOnMenu: Boolean = true,
    val showScreenshotButtonOnMenu: Boolean = true,
    val showTimeOnMenu: Boolean = true,
    val showTimeLimitOnMenu: Boolean = false,
    val isMono: Boolean = false,
    val outputFormat: String = "mp4" // mp4, mkv, etc (currently only mp4 supported via native)
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
