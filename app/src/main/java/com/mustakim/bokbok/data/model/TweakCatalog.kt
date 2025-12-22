package com.mustakim.bokbok.data.model

import android.os.Build

object TweakCatalog {
    const val CAT_DISPLAY = "Display & Animation"
    const val CAT_GPU = "GPU & Graphics"
    const val CAT_MEMORY = "Memory & Processes"
    const val CAT_COMPILER = "AOT Compilation"
    const val CAT_NETWORK = "Network & Latency"
    const val CAT_SYSTEM = "System & Utility"
    const val CAT_INPUT = "Touch & Response"

    val allTweaks = listOf(
        // --- Display & Animation ---
        TweakDef(
            id = "peak_refresh_rate",
            title = "Force Peak Refresh Rate",
            description = "Force the display to stay at maximum frequency (e.g. 120Hz).",
            category = CAT_DISPLAY,
            minSdk = Build.VERSION_CODES.Q
        ),
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
        TweakDef(
            id = "disable_window_blurs",
            title = "Disable UI Blurs",
            description = "Reduces GPU load by disabling background blurs in menus.",
            category = CAT_DISPLAY,
            minSdk = Build.VERSION_CODES.S // Android 12
        ),
        
        // --- GPU & Graphics ---
        TweakDef(
            id = "force_gpu_rendering",
            title = "Force GPU Rendering",
            description = "Forces use of GPU for 2D drawing.",
            category = CAT_GPU,
            maxSdk = Build.VERSION_CODES.P
        ),
        TweakDef(
            id = "vulkan_renderer",
            title = "Vulkan HWUI Engine",
            description = "Switch UI renderer to Vulkan for better efficiency.",
            category = CAT_GPU,
            minSdk = Build.VERSION_CODES.Q
        ),
        TweakDef(
            id = "game_downscale",
            title = "Resolution Downscaler",
            description = "Reduces game internal resolution to boost FPS.",
            type = TweakType.SELECT,
            options = listOf("1.0 (Native)", "0.9", "0.8", "0.7", "0.6", "0.5"),
            category = CAT_GPU,
            minSdk = Build.VERSION_CODES.S,
            warning = "Lowering resolution may make characters look blurry but drastically increases FPS."
        ),
        TweakDef(
            id = "disable_hw_overlays",
            title = "Disable HW Overlays",
            description = "Always use GPU for screen compositing. Improves stability.",
            category = CAT_GPU
        ),

        // --- Memory & Processes ---
        TweakDef(
            id = "kill_bg_apps",
            title = "Aggressive Background Kill",
            description = "Kills background processes before launching the game.",
            category = CAT_MEMORY
        ),
        TweakDef(
            id = "max_phantom_processes",
            title = "Phantom Process Boost",
            description = "Increase limit for sub-processes. Prevents crashes in tools.",
            category = CAT_MEMORY,
            minSdk = Build.VERSION_CODES.S
        ),
        TweakDef(
            id = "app_standby_active",
            title = "Force Active State",
            description = "Prevents OS from putting the game in standby mode.",
            category = CAT_MEMORY,
            minSdk = Build.VERSION_CODES.P
        ),
        TweakDef(
            id = "bg_process_limit",
            title = "Background Process Limit",
            description = "Restricts number of processes kept in background.",
            type = TweakType.SELECT,
            options = listOf("Standard", "0", "1", "2", "3", "4"),
            category = CAT_MEMORY
        ),

        // --- Touch & Response ---
        TweakDef(
            id = "long_press_timeout",
            title = "Long Press Latency",
            description = "Reduces time to trigger long-press actions.",
            type = TweakType.SELECT,
            options = listOf("Default", "250", "300", "400"),
            category = CAT_INPUT
        ),
        TweakDef(
            id = "tap_duration_threshold",
            title = "Tap Speed Boost",
            description = "Reduces confirmation delay for taps.",
            category = CAT_INPUT
        ),
        TweakDef(
            id = "touch_blocking_period",
            title = "Zero Touch Block",
            description = "Prevents system from ignoring accidental touches.",
            category = CAT_INPUT
        ),

        // --- Network & Latency ---
        TweakDef(
            id = "wifi_power_save",
            title = "Disable Wi-Fi Power Save",
            description = "Ensures network chip never sleeps during gameplay. Reduces lag.",
            category = CAT_NETWORK
        ),
        TweakDef(
            id = "cellular_data_throttle",
            title = "Disable Data Throttling",
            description = "Prevents carrier speed limits during active usage.",
            category = CAT_NETWORK
        ),
        TweakDef(
            id = "wifi_scan_always_enabled",
            title = "Disable WiFi Scanning",
            description = "Reduces ping spikes by disabling background scanning.",
            category = CAT_NETWORK
        ),
        TweakDef(
            id = "adaptive_connectivity",
            title = "Disable Adaptive Network",
            description = "Prevents mid-game network switching (5G/WiFi).",
            category = CAT_NETWORK,
            minSdk = Build.VERSION_CODES.R
        ),

        // --- System & Utility ---
        TweakDef(
            id = "native_game_mode",
            title = "OS Game Mode",
            description = "Enables Android's official internal performance profile.",
            category = CAT_SYSTEM,
            minSdk = Build.VERSION_CODES.S
        ),
        TweakDef(
            id = "fixed_performance_mode",
            title = "Fixed Performance Mode",
            description = "Locks CPU/GPU clocks for sustained performance.",
            category = CAT_SYSTEM,
            minSdk = Build.VERSION_CODES.R,
            warning = "Device may get hot. Use only if you have good cooling."
        ),
        TweakDef(
            id = "disable_gos",
            title = "Kill Game Optimizer (GOS)",
            description = "Disables Samsung's built-in throttling service.",
            category = CAT_SYSTEM,
            manufacturer = "samsung"
        ),
        TweakDef(
            id = "zen_mode",
            title = "Gaming DND",
            description = "Block all notifications and calls while gaming.",
            category = CAT_SYSTEM
        ),
        // ... compilation, resolution, dpi remain same but omitted for brevity in response if needed
        TweakDef(
            id = "compile_speed",
            title = "Speed Compilation (AOT)",
            description = "Compiles the app for maximum execution speed.",
            category = CAT_COMPILER
        ),
        TweakDef(
            id = "low_power_disable",
            title = "Disable Battery Saver",
            description = "Forces battery saver off for maximum power.",
            category = CAT_SYSTEM
        ),
        TweakDef(
            id = "wm_size",
            title = "Resolution Overrider",
            description = "Downscale resolution to improve FPS (e.g. 720p).",
            type = TweakType.INPUT,
            category = CAT_SYSTEM,
            warning = "May shift some UI icons. Use 'Reset' if screen looks weird."
        ),
        TweakDef(
            id = "wm_density",
            title = "DPI Overrider",
            description = "Set custom DPI for better button sizes.",
            type = TweakType.INPUT,
            category = CAT_SYSTEM
        )
    )

    fun getFilteredTweaks(): List<TweakDef> {
        val sdk = Build.VERSION.SDK_INT
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        return allTweaks.filter { tweak ->
            val sdkOk = sdk >= tweak.minSdk && sdk <= tweak.maxSdk
            val manuOk = tweak.manufacturer == null || manufacturer.contains(tweak.manufacturer)
            sdkOk && manuOk
        }
    }
}
