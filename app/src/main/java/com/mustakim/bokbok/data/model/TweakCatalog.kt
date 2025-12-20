package com.mustakim.bokbok.data.model

object TweakCatalog {
    const val CAT_DISPLAY = "Display & Animation"
    const val CAT_GPU = "GPU & Graphics"
    const val CAT_MEMORY = "Memory & Processes"
    const val CAT_COMPILER = "AOT Compilation"
    const val CAT_NETWORK = "Network & Latency"
    const val CAT_SYSTEM = "System & Utility"

    val allTweaks = listOf(
        // Display
        TweakDef(
            id = "window_animation_scale",
            title = "Window Animation Scale",
            description = "Adjust speed of window animations. Lower is faster.",
            type = TweakType.SELECT,
            isGlobal = true,
            options = listOf("0", "0.25", "0.5", "0.75", "1.0"),
            category = CAT_DISPLAY
        ),
        TweakDef(
            id = "transition_animation_scale",
            title = "Transition Animation Scale",
            description = "Adjust speed of transition animations. 0 is instant.",
            type = TweakType.SELECT,
            isGlobal = true,
            options = listOf("0", "0.25", "0.5", "0.75", "1.0"),
            category = CAT_DISPLAY
        ),
        TweakDef(
            id = "animator_duration_scale",
            title = "Animator Duration Scale",
            description = "Adjust duration of overall system animations.",
            type = TweakType.SELECT,
            isGlobal = true,
            options = listOf("0", "0.25", "0.5", "0.75", "1.0"),
            category = CAT_DISPLAY
        ),
        
        // GPU
        TweakDef(
            id = "force_gpu_rendering",
            title = "Force GPU Rendering",
            description = "Forces use of GPU for 2D drawing.",
            category = CAT_GPU
        ),
        TweakDef(
            id = "disable_hw_overlays",
            title = "Disable HW Overlays",
            description = "Always use GPU for screen compositing. Can improve smoothness.",
            category = CAT_GPU
        ),
        TweakDef(
            id = "game_driver_all_apps",
            title = "Global Game Driver",
            description = "Enable optimized game drivers for all apps.",
            category = CAT_GPU
        ),

        // Memory
        TweakDef(
            id = "kill_bg_apps",
            title = "Aggressive Background Kill",
            description = "Kills background processes before launching the game.",
            category = CAT_MEMORY
        ),
        TweakDef(
            id = "bg_process_limit",
            title = "Background Process Limit",
            description = "Restricts number of processes kept in background.",
            type = TweakType.SELECT,
            options = listOf("Standard", "0", "1", "2", "3", "4"),
            category = CAT_MEMORY
        ),

        // Compilation
        TweakDef(
            id = "compile_speed",
            title = "Speed Compilation (AOT)",
            description = "Compiles the app for maximum execution speed. Takes a few minutes.",
            category = CAT_COMPILER
        ),
        
        // Network
        TweakDef(
            id = "wifi_scan_always_enabled",
            title = "Disable WiFi Scanning",
            description = "Reduces ping spikes by disabling background WiFi scanning.",
            category = CAT_NETWORK
        ),

        // System
        TweakDef(
            id = "zen_mode",
            title = "Gaming DND",
            description = "Block all notifications and calls while gaming.",
            category = CAT_SYSTEM
        ),
        TweakDef(
            id = "low_power_disable",
            title = "Performance Power Mode",
            description = "Ensures battery saver is off and performance is maxed.",
            category = CAT_SYSTEM
        ),
        TweakDef(
            id = "wm_size",
            title = "Resolution Overrider",
            description = "Downscale resolution to improve FPS (e.g. 720p).",
            type = TweakType.INPUT,
            category = CAT_SYSTEM
        ),
        TweakDef(
            id = "wm_density",
            title = "DPI Overrider",
            description = "Set custom DPI for better button sizes.",
            type = TweakType.INPUT,
            category = CAT_SYSTEM
        )
    )
}
