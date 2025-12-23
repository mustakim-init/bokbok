package com.mustakim.bokbok.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import com.mustakim.bokbok.data.local.dao.GameDao
import com.mustakim.bokbok.data.local.entity.GameEntity
import com.mustakim.bokbok.data.model.GameItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import javax.inject.Inject

class GameRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gameDao: GameDao
) {
    private val packageManager = context.packageManager

    private var cachedInstalledPackages: List<PackageInfo>? = null
    private var lastPackageScanTime: Long = 0

    fun getGames(): Flow<List<GameItem>> {
        return gameDao.getAllGames().map { entities ->
            // Optimization: Cache installed app list for 1 minute to avoid heavy IPC on every flow emission.
            // When user switches tabs or toggles a profile, the DB flow emits. 
            // We don't want to re-scan 300+ apps just because a DB row changed.
            val currentTime = System.currentTimeMillis()
            if (cachedInstalledPackages == null || currentTime - lastPackageScanTime > 300000) { // 5 mins fallback
                cachedInstalledPackages = packageManager.getInstalledPackages(0)
                lastPackageScanTime = currentTime
            }
            
            val installedApps = cachedInstalledPackages!!
            val gameItems = mutableListOf<GameItem>()

            val entityMap = entities.associateBy { it.packageName }
            
            installedApps.forEach { packageInfo ->
                val appInfo = packageInfo.applicationInfo ?: return@forEach
                val packageName = packageInfo.packageName
                val entity = entityMap[packageName]
                
                // CRITICAL SELECTION LOGIC:
                // 1. If it's in our DB (User added it or we previously found it), we keep it unless manually removed.
                // 2. If it's NOT in DB, we only scan it if it's a USER app (not a core system app).
                // This eliminates scanning 300+ system processes.
                val isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || 
                              (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                
                val shouldEvaluationAsGame = entity != null || isUserApp
                
                if (shouldEvaluationAsGame && entity?.isManuallyRemoved != true) {
                    if (entity != null || isGame(packageInfo)) {
                        gameItems.add(
                            GameItem(
                                packageName = packageName,
                                label = appInfo.loadLabel(packageManager).toString(),
                                isHiddenFromLauncher = entity?.isHiddenFromLauncher ?: false,
                                isUserAdded = entity?.isUserAdded ?: false,
                                installedTime = packageInfo.firstInstallTime,
                                apkSize = appInfo.sourceDir?.let { File(it).length() } ?: 0L,
                                optimizationProfile = entity?.optimizationProfile ?: com.mustakim.bokbok.data.model.OptimizationProfile.BALANCED,
                                customSettingsJson = entity?.customSettingsJson ?: "{}"
                            )
                        )
                    }
                }
            }
            gameItems
        }.map { items -> 
            items.sortedBy { it.label } 
        }.flowOn(Dispatchers.IO)
    }

    fun invalidateCache() {
        cachedInstalledPackages = null
        lastPackageScanTime = 0
    }

    private fun isGame(packageInfo: PackageInfo): Boolean {
        // 1. Play Store category (Android O+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (packageInfo.applicationInfo?.category == ApplicationInfo.CATEGORY_GAME) return true
        }
        // 2. Known game package prefixes
        val prefixes = listOf(
            "com.miHoYo.", "com.tencent.", "com.supercell.",
            "com.king.", "com.ea.", "com.gameloft.", "com.roblox.",
            "com.mojang.", "com.activision.", "com.netease.", "com.garena.",
            "com.epicgames.", "com.riotgames.", "com.square_enix.", "com.bandainamcoent."
        )
        return prefixes.any { packageInfo.packageName.startsWith(it, ignoreCase = true) }
    }

    suspend fun launchGame(packageName: String) {
        withContext(Dispatchers.IO) {
            // Check if app is disabled, enable it first
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, PackageManager.MATCH_DISABLED_COMPONENTS)
                if (!appInfo.enabled) {
                    executeShizukuCommand("pm enable --user 0 $packageName")
                }
            } catch (e: Exception) {
                android.util.Log.e("GameRepository", "Failed to launch game $packageName", e)
            }

            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        }
    }

    suspend fun hideFromLauncher(packageName: String): Boolean {
        executeShizukuCommand("pm disable-user --user 0 $packageName")
        // Check if actually disabled
        val isHidden = !isAppEnabled(packageName)
        if (isHidden) {
            updateGameEntity(packageName) { it.copy(isHiddenFromLauncher = true) }
        }
        return isHidden
    }

    suspend fun showInLauncher(packageName: String): Boolean {
        executeShizukuCommand("pm enable --user 0 $packageName")
        // Check if actually enabled
        val isShown = isAppEnabled(packageName)
        if (isShown) {
            updateGameEntity(packageName) { it.copy(isHiddenFromLauncher = false) }
        }
        return isShown
    }

    private suspend fun isAppEnabled(packageName: String): Boolean {
        val output = executeShizukuCommandAndGet("pm list packages -e $packageName")
        return output.contains(packageName)
    }

    suspend fun addGameManually(packageName: String) {
        updateGameEntity(packageName) { it.copy(isUserAdded = true, isManuallyRemoved = false) }
    }

    suspend fun removeFromGameList(packageName: String) {
        // If it's naturally detected as a game (isGame), we can't just delete from DB.
        // We mark it as manually removed instead.
        updateGameEntity(packageName) { it.copy(isManuallyRemoved = true) }
    }

    suspend fun updateGameEntity(packageName: String, update: (GameEntity) -> GameEntity) {
        val current = gameDao.getGameByPackage(packageName) ?: GameEntity(packageName)
        gameDao.upsertGame(update(current))
    }

    suspend fun batchUpdateGames(packageNames: List<String>, update: (GameEntity) -> GameEntity) {
        val updates = packageNames.map { pkg ->
            val current = gameDao.getGameByPackage(pkg) ?: GameEntity(pkg)
            update(current)
        }
        gameDao.upsertGames(updates)
    }

    private val prefs = context.getSharedPreferences("game_mode_snapshots", Context.MODE_PRIVATE)

    private fun sanitizeCommand(command: String): String {
        // High-security sanitization: 
        // 1. Block command chaining characters: ; & | > < `
        // 2. Block command substitution: $( ... )
        // 3. Block newlines and carriage returns
        // 4. Block obvious dangerous paths/commands in user inputs
        return command
            .replace(Regex("[;&|><`]"), "")
            .replace(Regex("\\$\\(.*\\)"), "") 
            .replace("\n", "")
            .replace("\r", "")
            .trim()
    }

    private suspend fun executeShizukuCommand(command: String) {
        val sanitized = sanitizeCommand(command)
        if (sanitized.isEmpty()) return
        executeShizukuCommandAndGet(sanitized)
    }

    suspend fun applyOptimization(tweakId: String, value: String, packageName: String? = null) {
        // Track modified packages for efficient reversion later
        packageName?.let { pkg ->
            val set = prefs.getStringSet("affected_packages", emptySet()) ?: emptySet()
            if (!set.contains(pkg)) {
                prefs.edit().putStringSet("affected_packages", set + pkg).apply()
            }
        }

        val command = when (tweakId) {
            "window_animation_scale", "transition_animation_scale", "animator_duration_scale" -> {
                // Allowlist: only digits and dot
                if (value.matches(Regex("^[0-9.]+$"))) {
                    saveSnapshot("global", tweakId, "settings get global $tweakId")
                    "settings put global $tweakId $value"
                } else null
            }
            "disable_window_blurs" -> {
                saveSnapshot("global", "disable_window_blurs", "settings get global disable_window_blurs")
                "settings put global disable_window_blurs ${if (value == "true") 1 else 0}"
            }
            "force_gpu_rendering" -> {
                saveSnapshot("global", tweakId, "settings get global force_gpu_rendering")
                "settings put global force_gpu_rendering ${if (value == "true") 1 else 0}"
            }
            "disable_hw_overlays" -> {
                "service call SurfaceFlinger 1008 i32 ${if (value == "true") 1 else 0}"
            }
            "game_driver_all_apps" -> {
                saveSnapshot("global", tweakId, "settings get global game_driver_all_apps")
                "settings put global game_driver_all_apps ${if (value == "true") 1 else 0}"
            }
            "wifi_scan_always_enabled" -> {
                saveSnapshot("global", tweakId, "settings get global wifi_scan_always_enabled")
                "settings put global wifi_scan_always_enabled ${if (value == "true") 0 else 1}"
            }
            "wifi_power_save" -> {
                saveSnapshot("global", "wifi_power_save", "settings get global wifi_power_save")
                "settings put global wifi_power_save ${if (value == "true") 0 else 1}"
            }
            "cellular_data_throttle" -> {
                saveSnapshot("global", "cellular_data_throttle", "settings get global cellular_data_throttle")
                "settings put global cellular_data_throttle ${if (value == "true") 0 else 1}"
            }
            "max_phantom_processes" -> {
                saveSnapshot("config", "activity_manager:max_phantom_processes", "device_config get activity_manager max_phantom_processes")
                "device_config put activity_manager max_phantom_processes ${if (value == "true") 1024 else 32}"
            }
            "native_game_mode" -> {
                if (packageName != null) "cmd game mode performance $packageName" else null
            }
            "game_downscale" -> {
                if (packageName != null && value != "1.0 (Native)") "cmd game downscale $value $packageName" else null
            }
            "long_press_timeout" -> {
                saveSnapshot("secure", "long_press_timeout", "settings get secure long_press_timeout")
                if (value == "Default") "settings delete secure long_press_timeout" else "settings put secure long_press_timeout $value"
            }
            "tap_duration_threshold" -> {
                saveSnapshot("secure", "tap_duration_threshold", "settings get secure tap_duration_threshold")
                "settings put secure tap_duration_threshold ${if (value == "true") 0.0 else 0.1}"
            }
            "touch_blocking_period" -> {
                saveSnapshot("secure", "touch_blocking_period", "settings get secure touch_blocking_period")
                "settings put secure touch_blocking_period ${if (value == "true") 0.0 else 0.1}"
            }
            "zen_mode" -> {
                saveSnapshot("global", tweakId, "settings get global zen_mode")
                "settings put global zen_mode ${if (value == "true") 2 else 0}"
            }
            "low_power_disable" -> {
                saveSnapshot("global", "low_power", "settings get global low_power")
                "settings put global low_power ${if (value == "true") 0 else 1}"
            }
            "wm_size" -> {
                if (value.isBlank() || value == "reset") {
                    "wm size reset"
                } else {
                    if (value.matches(Regex("^\\d{3,4}x\\d{3,5}$"))) {
                        val parts = value.split("x")
                        val w = parts[0].toIntOrNull() ?: 0
                        val h = parts[1].toIntOrNull() ?: 0
                        
                        // Dynamic Upper Bound: Check physical display size if possible
                        val metrics = context.resources.displayMetrics
                        val maxW = (metrics.widthPixels * 1.5).toInt().coerceAtLeast(3840)
                        val maxH = (metrics.heightPixels * 1.5).toInt().coerceAtLeast(3840)

                        if (w in 320..maxW && h in 320..maxH) {
                            saveSnapshot("wm", tweakId, "wm size")
                            "wm size $value"
                        } else null
                    } else null
                }
            }
            "wm_density" -> {
                if (value.isBlank()) {
                    "wm density reset"
                } else {
                    val dens = value.toIntOrNull() ?: 0
                    if (dens in 72..1000) {
                        saveSnapshot("wm", tweakId, "wm density")
                        "wm density $value"
                    } else null
                }
            }
            "peak_refresh_rate" -> {
                // Dynamic Check: Get highest supported refresh rate instead of hardcoded 120
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
                }
                val maxRefresh = display?.supportedModes?.maxByOrNull { it.refreshRate }?.refreshRate?.toString() ?: "120.0"
                
                saveSnapshot("system", "peak_refresh_rate", "settings get system peak_refresh_rate")
                saveSnapshot("system", "min_refresh_rate", "settings get system min_refresh_rate")
                executeShizukuCommand("settings put system peak_refresh_rate $maxRefresh")
                "settings put system min_refresh_rate $maxRefresh"
            }
            "vulkan_renderer" -> {
                saveSnapshot("prop", "debug.hwui.renderer", "getprop debug.hwui.renderer")
                "setprop debug.hwui.renderer skiavk"
            }
            "app_standby_active" -> {
                if (packageName != null) "cmd usage-stats set-state $packageName active" else null
            }
            "fixed_performance_mode" -> {
                saveSnapshot("cmd", "performance_mode", "echo true")
                "cmd power set-fixed-performance-mode-enabled true"
            }
            "adaptive_connectivity" -> {
                saveSnapshot("global", "adaptive_connectivity_enabled", "settings get global adaptive_connectivity_enabled")
                "settings put global adaptive_connectivity_enabled 0"
            }
            "disable_gos" -> {
                val manufacturer = Build.MANUFACTURER.lowercase()
                if (manufacturer.contains("samsung")) {
                    saveSnapshot("pm", "com.samsung.android.game.gos", "echo disabled")
                    "pm disable-user --user 0 com.samsung.android.game.gos"
                } else null
            }
            "bg_process_limit" -> {
                saveSnapshot("activity", tweakId, "dumpsys activity settings | grep process_limit")
                if (value == "Standard") "activity clear-process-limit" else "activity set-process-limit $value"
            }
            else -> null
        }
        command?.let { executeShizukuCommand(it) }
    }

    suspend fun compileApp(packageName: String, mode: String) {
        executeShizukuCommand("cmd package compile -m $mode -f $packageName")
    }

    suspend fun killBackgroundApps() {
        // Simple am kill-all for now as requested
        executeShizukuCommand("am kill-all")
    }

    private suspend fun saveSnapshot(namespace: String, key: String, getCommand: String) {
        val compositeKey = "$namespace|$key" // Use pipe as delimiter to avoid collision with colon in keys
        if (!prefs.contains(compositeKey)) {
            val current = executeShizukuCommandAndGet(getCommand).trim()
            if (current.isNotEmpty() && !current.contains("Error") && !current.contains("not found")) {
                prefs.edit().putString(compositeKey, current).apply()
            }
        }
    }

    fun hasActiveSnapshots(): Boolean = prefs.all.isNotEmpty()

    suspend fun revertAllOptimizations() {
        val allSnapshots = prefs.all
        allSnapshots.forEach { (compositeKey, valueObj) ->
            val value = valueObj.toString()
            if (compositeKey == "affected_packages") return@forEach
            
            val namespace = compositeKey.substringBefore("|")
            val key = compositeKey.substringAfter("|")

            val command = when (namespace) {
                "wm" -> {
                    // "wm size" output is "Physical size: 1080x2400"
                    if (value.contains("Physical") || value.contains("Override")) {
                        "wm ${if(key == "wm_size") "size" else "density"} reset"
                    } else {
                        "wm ${if(key == "wm_size") "size" else "density"} $value"
                    }
                }
                "activity" -> {
                    if (key == "bg_process_limit") "activity clear-process-limit" else null
                }
                "config" -> {
                    // key is "namespace:key" (e.g. "activity_manager:max_phantom_processes")
                    val cfgNamespace = key.substringBefore(":")
                    val cfgKey = key.substringAfter(":")
                    "device_config put $cfgNamespace $cfgKey $value"
                }
                "prop" -> {
                    "setprop $key $value"
                }
                "cmd" -> {
                    if (key == "performance_mode") "cmd power set-fixed-performance-mode-enabled false" else null
                }
                "pm" -> {
                    "pm enable --user 0 $key"
                }
                else -> "settings put $namespace $key $value"
            }
            command?.let { executeShizukuCommand(it) }
        }
        
        // Final cleanup for things that might not have a saved value but were forced
        executeShizukuCommand("service call SurfaceFlinger 1008 i32 0") 
        executeShizukuCommand("activity clear-process-limit")
        
        // Efficient cleanup: Reset game mode/downscale ONLY for packages we actually modified
        val affectedPackages = prefs.getStringSet("affected_packages", emptySet()) ?: emptySet()
        affectedPackages.forEach { pkg ->
             executeShizukuCommand("cmd game mode standard $pkg")
             executeShizukuCommand("cmd game downscale reset $pkg")
        }
        
        prefs.edit().clear().apply()
        
        // Re-enable common bloat that might have been disabled
        val bloat = listOf("com.miui.analytics", "com.miui.msa.global", "com.xiaomi.joyose")
        bloat.forEach { executeShizukuCommand("pm enable --user 0 $it") }
    }

    suspend fun executeShizukuCommandAndGet(command: String): String {
        return withContext(Dispatchers.IO) {
            try {
                if (Shizuku.pingBinder()) {
                    val binder = Shizuku.getBinder()
                    if (binder != null) {
                        val service = IShizukuService.Stub.asInterface(binder)
                        val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
                        val pfd = process?.inputStream
                        val output = if (pfd != null) {
                            ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
                        } else ""
                        process?.waitFor()
                        return@withContext output
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("GameRepository", "Shizuku command failed: $command", e)
            }
            return@withContext ""
        }
    }

}
