package com.mustakim.bokbok.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import com.mustakim.bokbok.data.fps.FpsDaemonManager
import com.mustakim.bokbok.data.local.dao.GameDao
import com.mustakim.bokbok.data.local.entity.AppEntity
import com.mustakim.bokbok.data.local.entity.GameEntity
import com.mustakim.bokbok.data.model.GameItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val gameDao: GameDao,
    private val appDao: com.mustakim.bokbok.data.local.dao.AppDao
) {
    private val packageManager = context.packageManager

    private var cachedInstalledPackages: List<PackageInfo>? = null
    private var lastPackageScanTime: Long = 0

    fun getGames(): Flow<List<GameItem>> {
        return gameDao.getAllGames().map { entities ->
            val entityMap = entities.associateBy { it.packageName }
            
            // 🚀 SMART SCAN: Use cached app data from DB instead of live scan.
            // This prevents Vivo theme engine exception storms and lags.
            val allApps = appDao.getAppsOneShot().filter { it.isInstalled }
            
            allApps.mapNotNull { app ->
                val entity = entityMap[app.packageName]
                
                // Keep if it's explicitly user-added, or if it's naturally a game and NOT removed
                val shouldShow = entity?.isUserAdded == true || 
                               (app.isUserApp && !app.isSystemApp && entity?.isManuallyRemoved != true && isGameLegacy(app.packageName, app.category))
                
                if (shouldShow) {
                    GameItem(
                        packageName = app.packageName,
                        label = app.label, // Cached label
                        isHiddenFromLauncher = entity?.isHiddenFromLauncher ?: false,
                        isUserAdded = entity?.isUserAdded ?: false,
                        installedTime = app.firstInstallTime,
                        apkSize = app.apkSize,
                        customSettingsJson = entity?.customSettingsJson ?: "{}"
                    )
                } else null
            }
        }.map { items -> 
            items.sortedBy { it.label.lowercase() } 
        }.flowOn(Dispatchers.IO)
    }

    private fun isGameLegacy(packageName: String, category: Int): Boolean {
        if (category == ApplicationInfo.CATEGORY_GAME) return true
        val prefixes = listOf(
            "com.miHoYo.", "com.tencent.", "com.supercell.",
            "com.king.", "com.ea.", "com.gameloft.", "com.roblox.",
            "com.mojang.", "com.activision.", "com.netease.", "com.garena.",
            "com.epicgames.", "com.riotgames.", "com.square_enix.", "com.bandainamcoent.",
            "com.dts."
        )
        return prefixes.any { packageName.startsWith(it, ignoreCase = true) }
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
            syncGameListToShell()
        }
        return isHidden
    }

    suspend fun showInLauncher(packageName: String): Boolean {
        executeShizukuCommand("pm enable --user 0 $packageName")
        // Check if actually enabled
        val isShown = isAppEnabled(packageName)
        if (isShown) {
            updateGameEntity(packageName) { it.copy(isHiddenFromLauncher = false) }
            syncGameListToShell()
        }
        return isShown
    }

    private suspend fun isAppEnabled(packageName: String): Boolean {
        val output = executeShizukuCommandAndGet("pm list packages -e $packageName")
        return output.contains(packageName)
    }

    suspend fun addGameManually(packageName: String) {
        updateGameEntity(packageName) { it.copy(isUserAdded = true, isManuallyRemoved = false) }
        syncGameListToShell()
    }

    suspend fun removeFromGameList(packageName: String) {
        // If it's naturally detected as a game (isGame), we can't just delete from DB.
        // We mark it as manually removed instead.
        updateGameEntity(packageName) { it.copy(isManuallyRemoved = true) }
        syncGameListToShell()
    }

    suspend fun updateGameEntity(packageName: String, update: (GameEntity) -> GameEntity) {
        val current = gameDao.getGameByPackage(packageName) ?: GameEntity(packageName)
        gameDao.upsertGame(update(current))
    }

    suspend fun getGameEntity(packageName: String): GameEntity? {
        return gameDao.getGameByPackage(packageName)
    }

    suspend fun applyOptimizations(packageName: String) {
        val entity = getGameEntity(packageName) ?: return
        val json = try { org.json.JSONObject(entity.customSettingsJson) } catch (e: Exception) { return }
        
        // Ensure we preserve the clean state before applying anything
        checkAndCreateMasterSnapshot()

        // 1. Kill background apps if requested
        if (json.optString("kill_bg_apps") == "true") {
            killBackgroundApps()
        }

        // 2. Apply each tweak from JSON
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "kill_bg_apps") continue
            val value = json.optString(key)
            if (value.isNotEmpty()) {
                applyOptimization(key, value, packageName)
            }
        }
    }

    private val prefs = context.getSharedPreferences("game_mode_snapshots", Context.MODE_PRIVATE)

    suspend fun syncGameListToShell() {
        withContext(Dispatchers.IO) {
            // Use the same logic as getGames() to ensure consistency
            val games = getGames().first()
            if (games.isEmpty()) {
                android.util.Log.d("GameRepository", "No games to sync.")
                executeShizukuCommandAndGet("rm /data/local/tmp/bokbok_games.list")
                return@withContext
            }
            val packageList = games.joinToString("\\n") { it.packageName }
            android.util.Log.d("GameRepository", "Syncing ${games.size} games to shell list.")
            // Use direct execution to bypass sanitization for this internal trusted command
            executeShizukuCommandAndGet("echo -e \"$packageList\" > /data/local/tmp/bokbok_games.list")
            executeShizukuCommandAndGet("chmod 644 /data/local/tmp/bokbok_games.list")
        }
    }

    private fun checkAndCreateMasterSnapshot() {
        // If master snapshot already exists, DO NOTHING.
        // The master snapshot represents the "Clean" state of the phone before ANY game was launched.
        if (prefs.getBoolean("has_master_snapshot", false)) {
            android.util.Log.d("GameRepository", "Master snapshot exists. Skipping new snapshot.")
            return
        }
        
        // We don't actually "save" the whole state here, we just mark that subsequent
        // individual saveSnapshot calls are allowed to write to the 'master' record.
        // However, since we use the same keys, we just need to ensure we don't OVERWRITE 
        // existing keys if they are already there.
        // The saveSnapshot function already has a check: if (!prefs.contains(compositeKey))
        // So simply setting this flag acts as a semantic marker.
        prefs.edit().putBoolean("has_master_snapshot", true).apply()
        android.util.Log.d("GameRepository", "Marking start of MASTER snapshot session...")
    }

    private fun sanitizeCommand(command: String): String {
        // Relaxed sanitization: allow redirection but still block chaining and subshells
        // Chaining: ; & | ` 
        // Subshell: $( )
        return command
            .replace(Regex("[;&|`]"), "")
            .replace(Regex("\\$\\(.*\\)"), "") 
            .replace("\n", "")
            .replace("\r", "")
            .trim()
    }

    suspend fun executeShizukuCommand(command: String) {
        val sanitized = sanitizeCommand(command)
        if (sanitized.isEmpty()) return
        executeShizukuCommandAndGet(sanitized)
    }

    suspend fun executeShizukuCommandRaw(command: String): String {
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
            } catch (e: Exception) {}
            return@withContext ""
        }
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
            "wm_density" -> {
                if (value.isBlank() || value == "reset") {
                    "wm density reset"
                } else {
                    val dens = value.toIntOrNull() ?: 0
                    // SAFETY: Most devices brick/glitch if density is < 120 or > 720
                    if (dens in 120..720) {
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

    suspend fun killBackgroundApps(activeGamePackage: String? = null) {
        // Smart Memory Cleanup: Only target apps that are actually running in the background.
        val myPackage = "com.mustakim.bokbok"
        val exemptPackages = mutableSetOf(myPackage, "com.android.systemui", "android")
        activeGamePackage?.let { exemptPackages.add(it) }

        try {
            // Get running processes. We look for 'proc' lines in dumpsys.
            // Format: proc #idx: [app_name]/[uid]
            val output = executeShizukuCommandAndGet("dumpsys activity processes | grep -E \"^\\s*proc\\s#\"").trim()
            
            val runningPackages = output.lines()
                .mapNotNull { line ->
                    // Extract package name. Usually looks like: proc #10: com.google.android.youtube/u0a123
                    val match = Regex("proc\\s+#\\d+:\\s+([^/\\s:]+)").find(line)
                    match?.groupValues?.get(1)
                }
                .filter { pkg -> 
                    pkg.contains(".") && !exemptPackages.any { pkg.startsWith(it) }
                }
                .distinct()

            if (runningPackages.isNotEmpty()) {
                // Use 'am kill' for background processes (gentler than force-stop, frees RAM)
                // or 'am force-stop' for more aggressive cleanup if 'am kill' isn't enough.
                // We'll use force-stop for the specific targeted background apps.
                runningPackages.chunked(10).forEach { chunk ->
                    val chunkCmd = chunk.joinToString(" ; ") { "am force-stop $it" }
                    executeShizukuCommand(chunkCmd)
                }
            }
        } catch (_: Exception) {
            // Silent fallback
        }
    }


    /**
     * Parses PROFILEDATA from "dumpsys gfxinfo <pkg> framestats"
     * Column index 13 = FRAME_COMPLETED nanosecond timestamp
     * Counts frames completed within the last 1 second window
     */
    private fun parseFramestatsFps(raw: String): Float {
        android.util.Log.d("FPS-DEBUG", "=== RAW OUTPUT ===")
        android.util.Log.d("FPS-DEBUG", raw.take(2000)) // First 2000 chars

        val lines = raw.lines()
        val startIdx = lines.indexOfFirst { it.trimStart().startsWith("---PROFILEDATA---") }
        val endIdx   = lines.indexOfLast  { it.trimStart().startsWith("---PROFILEDATA---") }

        android.util.Log.d("FPS-DEBUG", "startIdx=$startIdx endIdx=$endIdx totalLines=${lines.size}")

        if (startIdx < 0 || endIdx <= startIdx) {
            android.util.Log.d("FPS-DEBUG", "PROFILEDATA block not found!")
            return 0f
        }

        val frameLines = lines.subList(startIdx + 2, endIdx)
        android.util.Log.d("FPS-DEBUG", "frameLines count=${frameLines.size}")
        android.util.Log.d("FPS-DEBUG", "first frameline: ${frameLines.firstOrNull()}")
        android.util.Log.d("FPS-DEBUG", "last frameline: ${frameLines.lastOrNull()}")

        val timestamps = frameLines.mapNotNull { line ->
            line.split(",").getOrNull(13)?.trim()?.toLongOrNull()
        }.filter { it > 0 }

        android.util.Log.d("FPS-DEBUG", "valid timestamps count=${timestamps.size}")
        android.util.Log.d("FPS-DEBUG", "last timestamp=${timestamps.lastOrNull()}")

        if (timestamps.size < 2) return 0f

        val nowNs          = timestamps.last()
        val oneSecAgoNs    = nowNs - 1_000_000_000L
        val framesInWindow = timestamps.count { it >= oneSecAgoNs }

        android.util.Log.d("FPS-DEBUG", "framesInWindow=$framesInWindow")
        return if (framesInWindow > 0) framesInWindow.toFloat() else 0f
    }

    /**
     * Parses "dumpsys SurfaceFlinger --latency" output.
     * Each line: <desired-present-ns> <actual-present-ns> <frame-ready-ns>
     * We use column 2 (actual present time) to count frames in last 1s.
     */
    private fun parseSurfaceFlingerLatencyFps(raw: String): Float {
        val timestamps = raw.lines().mapNotNull { line ->
            line.trim().split("\\s+".toRegex()).getOrNull(1)?.toLongOrNull()
        }.filter { it > 0 && it != Long.MAX_VALUE }

        if (timestamps.size < 2) return 0f

        val nowNs          = timestamps.last()
        val oneSecAgoNs    = nowNs - 1_000_000_000L
        val framesInWindow = timestamps.count { it in oneSecAgoNs..nowNs }

        return if (framesInWindow > 0) framesInWindow.toFloat() else 0f
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
                    if (value.contains("Physical") || value.contains("Override")) {
                        "wm density reset"
                    } else {
                        "wm density $value"
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
        // Reset the master snapshot flag
        // prefs.edit().putBoolean("has_master_snapshot", false).apply() // clear() already does this
        
        // Re-enable common bloat that might have been disabled
        val bloat = listOf("com.miui.analytics", "com.miui.msa.global", "com.xiaomi.joyose")
        bloat.forEach { executeShizukuCommand("pm enable --user 0 $it") }
    }

    suspend fun restoreToMasterSnapshot() {
        // Reverts settings but KEEPS the snapshot data so we can apply new optimizations 
        // on top of a clean slate.
        val allSnapshots = prefs.all
        allSnapshots.forEach { (compositeKey, valueObj) ->
            val value = valueObj.toString()
            if (compositeKey == "affected_packages" || compositeKey == "has_master_snapshot") return@forEach
            
            val namespace = compositeKey.substringBefore("|")
            val key = compositeKey.substringAfter("|")
            
            val command = when (namespace) {
                "wm" -> if (value.contains("Physical") || value.contains("Override")) "wm density reset" else "wm density $value"
                "activity" -> if (key == "bg_process_limit") "activity clear-process-limit" else null
                "config" -> "device_config put ${key.substringBefore(":")} ${key.substringAfter(":")} $value"
                "prop" -> "setprop $key $value"
                "cmd" -> if (key == "performance_mode") "cmd power set-fixed-performance-mode-enabled false" else null
                "pm" -> "pm enable --user 0 $key"
                else -> "settings put $namespace $key $value"
            }
            command?.let { executeShizukuCommand(it) }
        }
        
        // Reset game specific commands for affected packages
        val affectedPackages = prefs.getStringSet("affected_packages", emptySet()) ?: emptySet()
        affectedPackages.forEach { pkg ->
             executeShizukuCommand("cmd game mode standard $pkg")
             executeShizukuCommand("cmd game downscale reset $pkg")
        }
        
        // DO NOT clear prefs or has_master_snapshot
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

    suspend fun batchUpdateGames(packageNames: List<String>, transform: (com.mustakim.bokbok.data.local.entity.GameEntity) -> com.mustakim.bokbok.data.local.entity.GameEntity) {
        withContext(Dispatchers.IO) {
            val entities = packageNames.mapNotNull { gameDao.getGameByPackage(it) }
            val updated = entities.map { transform(it) }
            gameDao.upsertGames(updated)
        }
    }

}
