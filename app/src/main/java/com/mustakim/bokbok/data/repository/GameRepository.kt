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
import java.io.File

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
                            apkSize = File(packageInfo.applicationInfo?.sourceDir ?: "").length()
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

    private suspend fun updateGameEntity(packageName: String, update: (GameEntity) -> GameEntity) {
        val current = gameDao.getAllGames().first().find { it.packageName == packageName }
            ?: GameEntity(packageName)
        gameDao.upsertGame(update(current))
    }

    private suspend fun executeShizukuCommand(command: String) {
        withContext(Dispatchers.IO) {
            try {
                if (Shizuku.pingBinder()) {
                    val binder = Shizuku.getBinder()
                    if (binder != null) {
                        val service = IShizukuService.Stub.asInterface(binder)
                        service.newProcess(arrayOf("sh", "-c", command), null, null).waitFor()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
