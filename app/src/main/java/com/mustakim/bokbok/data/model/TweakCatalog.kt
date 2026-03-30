package com.mustakim.bokbok.data.model

import android.os.Build

object TweakCatalog {
    const val CAT_DISPLAY = "Display & Animation"
    const val CAT_GPU = "GPU & Graphics"
    const val CAT_MEMORY = "Memory & Processes"
    const val CAT_NETWORK = "Network & Latency"
    const val CAT_SYSTEM = "System & Utility"
    const val CAT_INPUT = "Touch & Response"

    val allTweaks = listOf(
        // --- Display & Animation ---
        TweakDef(
            id = "peak_refresh_rate",
            title = "Enforce Smooth Display",
            description = "Forces your screen to run at its highest speed (like 120Hz) for the smoothest gaming experience.",
            category = CAT_DISPLAY,
            minSdk = Build.VERSION_CODES.Q
        ),
        TweakDef(
            id = "window_animation_scale",
            title = "Window Speed Boost",
            description = "Makes menu windows open and close much faster or instantly.",
            type = TweakType.SELECT,
            isGlobal = true,
            options = listOf("0", "0.25", "0.5", "0.75", "1.0"),
            category = CAT_DISPLAY
        ),
        TweakDef(
            id = "transition_animation_scale",
            title = "Transition Speed Boost",
            description = "Makes the switch between different screens feel much faster.",
            type = TweakType.SELECT,
            isGlobal = true,
            options = listOf("0", "0.25", "0.5", "0.75", "1.0"),
            category = CAT_DISPLAY
        ),
        TweakDef(
            id = "animator_duration_scale",
            title = "Overall Animation Speed",
            description = "Speeds up every single animation in the entire system for a snappier feel.",
            type = TweakType.SELECT,
            isGlobal = true,
            options = listOf("0", "0.25", "0.5", "0.75", "1.0"),
            category = CAT_DISPLAY
        ),
        TweakDef(
            id = "disable_window_blurs",
            title = "Disable Fancy Blurs",
            description = "Turns off see-through blur effects in menus to give your graphics chip more power for the game.",
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
            title = "Clear Background Apps",
            description = "Forcefully closes other apps running in the background to free up memory for your game.",
            category = CAT_MEMORY
        ),
        TweakDef(
            id = "max_phantom_processes",
            title = "Stability Boost (Phantom)",
            description = "Allows the game to use more sub-processes. Essential for preventing crashes in heavy modern games.",
            category = CAT_MEMORY,
            minSdk = Build.VERSION_CODES.S
        ),
        TweakDef(
            id = "app_standby_active",
            title = "Prevent Game Sleep",
            description = "Stops the system from putting your game in 'standby' mode while you are playing.",
            category = CAT_MEMORY,
            minSdk = Build.VERSION_CODES.P
        ),
        TweakDef(
            id = "bg_process_limit",
            title = "Background Limit",
            description = "Limits how many other apps can stay alive while you play. Fewer apps means more power for the game.",
            type = TweakType.SELECT,
            options = listOf("Standard", "0", "1", "2", "3", "4"),
            category = CAT_MEMORY
        ),

        // --- Touch & Response ---
        TweakDef(
            id = "long_press_timeout",
            title = "Hold Response Boost",
            description = "Reduces the time you need to hold your finger down to trigger a long-press action.",
            type = TweakType.SELECT,
            options = listOf("Default", "250", "300", "400"),
            category = CAT_INPUT
        ),
        TweakDef(
            id = "tap_duration_threshold",
            title = "Instant Tap Response",
            description = "Removes the system's wait time to confirm a tap, making your inputs feel much faster.",
            category = CAT_INPUT
        ),
        TweakDef(
            id = "touch_blocking_period",
            title = "Ignore Ghost Touches",
            description = "Stops the system from accidentally ignoring your rapid taps during intense gameplay.",
            category = CAT_INPUT
        ),

        // --- Network & Latency ---
        TweakDef(
            id = "wifi_power_save",
            title = "Extreme Wi-Fi Mode",
            description = "Prevents the Wi-Fi chip from ever sleeping, ensuring your connection is always ready and stable.",
            category = CAT_NETWORK
        ),
        TweakDef(
            id = "cellular_data_throttle",
            title = "Unleash Mobile Data",
            description = "Stops the system from slowing down your mobile data speed to save power.",
            category = CAT_NETWORK
        ),
        TweakDef(
            id = "wifi_scan_always_enabled",
            title = "Reduce Ping Spikes",
            description = "Stops the phone from searching for other Wi-Fi networks in the background while you play.",
            category = CAT_NETWORK
        ),
        TweakDef(
            id = "adaptive_connectivity",
            title = "Stable Network Lock",
            description = "Prevents the phone from switching between 5G and Wi-Fi mid-game, which often causes lag.",
            category = CAT_NETWORK,
            minSdk = Build.VERSION_CODES.R
        ),

        // --- System & Utility ---
        TweakDef(
            id = "native_game_mode",
            title = "Android Game Engine",
            description = "Activates the official hidden performance mode built into Android for games.",
            category = CAT_SYSTEM,
            minSdk = Build.VERSION_CODES.S
        ),
        TweakDef(
            id = "fixed_performance_mode",
            title = "Maximum Clock Lock",
            description = "Forces your CPU to run at its highest speed constantly for zero lag.",
            category = CAT_SYSTEM,
            minSdk = Build.VERSION_CODES.R,
            warning = "Device may get hot. Use only if you have good cooling."
        ),
        TweakDef(
            id = "disable_gos",
            title = "Kill Throttling (Samsung)",
            description = "Stops Samsung phones from intentionally slowing down the game to save heat.",
            category = CAT_SYSTEM,
            manufacturer = "samsung"
        ),
        TweakDef(
            id = "zen_mode",
            title = "Notification Blocker",
            description = "Automatically hides all calls and notifications so you aren't interrupted.",
            category = CAT_SYSTEM
        ),
        TweakDef(
            id = "low_power_disable",
            title = "Force High Power",
            description = "Ensures your phone doesn't enter battery saver mode while gaming.",
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
