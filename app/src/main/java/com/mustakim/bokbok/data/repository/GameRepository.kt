package com.mustakim.bokbok.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import com.mustakim.bokbok.data.local.BokBokDatabase
import com.mustakim.bokbok.data.local.entity.GameEntity
import com.mustakim.bokbok.data.model.GameItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.core.content.edit

class GameRepository(private val context: Context) {
    private val packageManager = context.packageManager
    private val database = BokBokDatabase.getInstance(context)
    private val gameDao = database.gameDao()

    fun getGames(): Flow<List<GameItem>> {
        return gameDao.getAllGames().map { entities ->
            val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_DISABLED_COMPONENTS
            val installedApps = packageManager.getInstalledPackages(flags)
            val gameItems = mutableListOf<GameItem>()

            val entityMap = entities.associateBy { it.packageName }
            
            installedApps.forEach { packageInfo ->
                val packageName = packageInfo.packageName
                val entity = entityMap[packageName]
                
                if (entity != null || isGame(packageInfo)) {
                    gameItems.add(
                        GameItem(
                            packageName = packageName,
                            label = packageInfo.applicationInfo?.loadLabel(packageManager)?.toString() ?: packageName,
                            icon = packageInfo.applicationInfo?.loadIcon(packageManager),
                            isHiddenFromLauncher = entity?.isHiddenFromLauncher ?: false,
                            isUserAdded = entity?.isUserAdded ?: false,
                            installedTime = packageInfo.firstInstallTime,
                            apkSize = File(packageInfo.applicationInfo?.sourceDir ?: "").length(),
                            optimizationProfile = entity?.optimizationProfile ?: com.mustakim.bokbok.data.model.OptimizationProfile.BALANCED,
                            customSettingsJson = entity?.customSettingsJson ?: "{}"
                        )
                    )
                }
            }
            gameItems.sortedBy { it.label }
        }.flowOn(Dispatchers.IO)
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
                e.printStackTrace()
            }

            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
        }
    }

    suspend fun hideFromLauncher(packageName: String) {
        executeShizukuCommand("pm disable-user --user 0 $packageName")
        updateGameEntity(packageName) { it.copy(isHiddenFromLauncher = true) }
    }

    suspend fun showInLauncher(packageName: String) {
        executeShizukuCommand("pm enable --user 0 $packageName")
        updateGameEntity(packageName) { it.copy(isHiddenFromLauncher = false) }
    }

    suspend fun addGameManually(packageName: String) {
        updateGameEntity(packageName) { it.copy(isUserAdded = true) }
    }

    suspend fun removeFromGameList(packageName: String) {
        gameDao.removeGame(packageName)
    }

    suspend fun updateGameEntity(packageName: String, update: (GameEntity) -> GameEntity) {
        val current = gameDao.getAllGames().first().find { it.packageName == packageName }
            ?: GameEntity(packageName)
        gameDao.upsertGame(update(current))
    }

    private val prefs = context.getSharedPreferences("game_mode_snapshots", Context.MODE_PRIVATE)

    suspend fun applyOptimization(tweakId: String, value: String) {
        val command = when (tweakId) {
            "window_animation_scale", "transition_animation_scale", "animator_duration_scale" -> {
                saveSnapshot(tweakId, "settings get global $tweakId")
                "settings put global $tweakId $value"
            }
            "force_gpu_rendering" -> {
                saveSnapshot(tweakId, "settings get global force_gpu_rendering")
                "settings put global force_gpu_rendering ${if (value == "true") 1 else 0}"
            }
            "disable_hw_overlays" -> {
                // service call SurfaceFlinger 1008 i32 1 (Disable) or 0 (Enable)
                "service call SurfaceFlinger 1008 i32 ${if (value == "true") 1 else 0}"
            }
            "game_driver_all_apps" -> {
                saveSnapshot(tweakId, "settings get global game_driver_all_apps")
                "settings put global game_driver_all_apps ${if (value == "true") 1 else 0}"
            }
            "wifi_scan_always_enabled" -> {
                saveSnapshot(tweakId, "settings get global wifi_scan_always_enabled")
                "settings put global wifi_scan_always_enabled ${if (value == "true") 0 else 1}" // Inverted for "Disable"
            }
            "zen_mode" -> {
                saveSnapshot(tweakId, "settings get global zen_mode")
                "settings put global zen_mode ${if (value == "true") 2 else 0}"
            }
            "low_power_disable" -> {
                saveSnapshot(tweakId, "settings get global low_power")
                "settings put global low_power ${if (value == "true") 0 else 1}"
            }
            "wm_size" -> {
                saveSnapshot(tweakId, "wm size")
                if (value.isBlank()) "wm size reset" else "wm size $value"
            }
            "wm_density" -> {
                saveSnapshot(tweakId, "wm density")
                if (value.isBlank()) "wm density reset" else "wm density $value"
            }
            "bg_process_limit" -> {
                // Settings.Global.MAX_TOTAL_PROCESS_LIMIT is hard to set via shell reliably across versions,
                // but "activity set-process-limit" is the standard way.
                saveSnapshot(tweakId, "dumpsys activity settings | grep process_limit") // Rough snapshot
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

    private suspend fun saveSnapshot(key: String, getCommand: String) {
        if (!prefs.contains(key)) {
            val current = executeShizukuCommandAndGet(getCommand).trim()
            if (current.isNotEmpty() && !current.contains("Error")) {
                // Handle complex output from wm size/density if necessary, but keep original string
                prefs.edit { putString(key, current) }
            }
        }
    }

    fun hasActiveSnapshots(): Boolean = prefs.all.isNotEmpty()

    suspend fun revertAllOptimizations() {
        val allSnapshots = prefs.all
        allSnapshots.forEach { (key, valueObj) ->
            val value = valueObj.toString()
            val command = when (key) {
                "wm_size", "wm_density" -> {
                    // "wm size" output is "Physical size: 1080x2400"
                    if (value.contains("Physical")) {
                        val type = if (key == "wm_size") "size" else "density"
                        "wm $type reset"
                    } else {
                        val type = if (key == "wm_size") "size" else "density"
                        "wm $type $value"
                    }
                }
                "bg_process_limit" -> "activity clear-process-limit"
                "disable_hw_overlays" -> "service call SurfaceFlinger 1008 i32 0"
                else -> {
                    val namespace = when {
                        key.contains("refresh_rate") || 
                        key.contains("pointer_speed") || 
                        key.contains("accelerometer_rotation") -> "system"
                        
                        key.contains("install_non_market_apps") ||
                        key.contains("location_mode") ||
                        key.contains("enabled_accessibility_services") -> "secure"
                        
                        else -> "global"
                    }
                    "settings put $namespace $key $value"
                }
            }
            executeShizukuCommand(command)
        }
        
        // Final cleanup for things that might not have a saved value but were forced
        executeShizukuCommand("service call SurfaceFlinger 1008 i32 0") // Force enable overlays
        executeShizukuCommand("activity clear-process-limit")
        
        prefs.edit { clear() }
        
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
                e.printStackTrace()
            }
            return@withContext ""
        }
    }

    private suspend fun executeShizukuCommand(command: String) {
        executeShizukuCommandAndGet(command)
    }
}
