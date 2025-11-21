package com.mustakim.bokbok.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri

/**
 * Helper class to manage battery optimization and autostart settings for reliable background notifications.
 * Supports manufacturer-specific autostart detection and guidance for aggressive battery management.
 */
object BatteryOptimizationHelper {

    /**
     * Check if the app is ignoring battery optimizations
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
        }
        return true // Not applicable on older Android versions
    }

    /**
     * Request battery optimization exemption
     * Opens system settings for the user to grant exemption
     */
    fun requestBatteryOptimizationExemption(context: Context): Intent {
        val intent = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            intent.action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            intent.data = "package:${context.packageName}".toUri()
        }
        return intent
    }

    /**
     * Get device manufacturer
     */
    fun getManufacturer(): String {
        return Build.MANUFACTURER.lowercase()
    }

    /**
     * Check if the device manufacturer requires special autostart setup
     */
    fun needsAutoStartGuidance(): Boolean {
        val manufacturer = getManufacturer()
        return manufacturer in listOf(
            "xiaomi",
            "huawei",
            "honor",
            "oppo",
            "vivo",
            "realme",
            "oneplus",
            "samsung",
            "asus",
            "letv",
            "nokia"
        )
    }

    /**
     * Get autostart instructions for the current device manufacturer
     */
    fun getAutoStartInstructions(): AutoStartInstructions? {
        return when (getManufacturer()) {
            "xiaomi" -> AutoStartInstructions(
                manufacturer = "Xiaomi (MIUI)",
                steps = listOf(
                    "Open Security app",
                    "Tap 'Permissions'",
                    "Tap 'Autostart'",
                    "Find 'BokBok' and enable autostart",
                    "Go back to 'Permissions' → 'Other Permissions'",
                    "Find 'BokBok' → Enable 'Start in background'"
                ),
                alternativeSteps = listOf(
                    "Settings → Apps → Manage apps",
                    "Find and tap 'BokBok'",
                    "Tap 'Autostart' and enable it",
                    "Tap 'Battery saver' → Choose 'No restrictions'"
                )
            )

            "huawei", "honor" -> AutoStartInstructions(
                manufacturer = "Huawei/Honor (EMUI)",
                steps = listOf(
                    "Open Phone Manager or Optimizer app",
                    "Tap 'App launch' or 'Startup manager'",
                    "Find 'BokBok'",
                    "Toggle to 'Manual management'",
                    "Enable all three options: Auto-launch, Secondary launch, Run in background"
                ),
                alternativeSteps = listOf(
                    "Settings → Apps → Apps",
                    "Find and tap 'BokBok'",
                    "Tap 'Battery' → Select 'Allow'"
                )
            )

            "oppo", "realme" -> AutoStartInstructions(
                manufacturer = "Oppo/Realme (ColorOS)",
                steps = listOf(
                    "Settings → Battery → App Battery Management",
                    "Find 'BokBok'",
                    "Disable Battery optimization",
                    "Settings → Privacy → Permission Manager",
                    "Tap 'Autostart' → Enable for 'BokBok'"
                ),
                alternativeSteps = listOf(
                    "Settings → Apps → App Management",
                    "Find 'BokBok' → Tap it",
                    "Tap 'Battery usage' → Select 'Don't optimize'"
                )
            )

            "vivo" -> AutoStartInstructions(
                manufacturer = "Vivo (FuntouchOS)",
                steps = listOf(
                    "Settings → Battery",
                    "Tap 'Background apps management' or 'High background power consumption'",
                    "Find 'BokBok' and allow it to run in background",
                    "Settings → More settings → Applications",
                    "Tap 'Autostart' → Enable for 'BokBok'"
                ),
                alternativeSteps = listOf(
                    "i Manager → App Manager → App list",
                    "Find 'BokBok'",
                    "Enable 'Auto-launch'"
                )
            )

            "oneplus" -> AutoStartInstructions(
                manufacturer = "OnePlus (OxygenOS)",
                steps = listOf(
                    "Settings → Battery → Battery optimization",
                    "Tap 'All apps'",
                    "Find 'BokBok' → Select 'Don't optimize'",
                    "Settings → Apps → BokBok",
                    "Tap 'Battery' → Select 'Don't optimize'"
                ),
                alternativeSteps = listOf(
                    "Recent apps button → Lock the BokBok app to prevent killing"
                )
            )

            "samsung" -> AutoStartInstructions(
                manufacturer = "Samsung (OneUI)",
                steps = listOf(
                    "Settings → Apps → BokBok",
                    "Tap 'Battery'",
                    "Select 'Unrestricted' for background battery usage",
                    "Turn off 'Put app to sleep'",
                    "Settings → Battery → Background usage limits",
                    "Make sure 'Put unused apps to sleep' doesn't affect BokBok"
                ),
                alternativeSteps = listOf(
                    "Settings → Device care → Battery",
                    "Tap 'App power management'",
                    "Disable 'Put unused apps to sleep'",
                    "Add 'BokBok' to 'Apps that won't be put to sleep'"
                )
            )

            "asus" -> AutoStartInstructions(
                manufacturer = "Asus (ZenUI)",
                steps = listOf(
                    "Mobile Manager → PowerMaster",
                    "Tap 'Auto-start Manager'",
                    "Find 'BokBok' and enable autostart",
                    "Tap 'Enable Auto-start' when prompted"
                ),
                alternativeSteps = listOf(
                    "Settings → Power management → Auto-start manager",
                    "Enable 'BokBok'"
                )
            )

            "nokia" -> AutoStartInstructions(
                manufacturer = "Nokia",
                steps = listOf(
                    "Settings → Apps & notifications → Advanced",
                    "Tap 'Special app access'",
                    "Tap 'Battery optimization'",
                    "Select 'All apps' from dropdown",
                    "Find 'BokBok' → Select 'Don't optimize'"
                )
            )

            else -> null
        }
    }

    /**
     * Data class representing autostart instructions for a specific manufacturer
     */
    data class AutoStartInstructions(
        val manufacturer: String,
        val steps: List<String>,
        val alternativeSteps: List<String>? = null
    )
}
