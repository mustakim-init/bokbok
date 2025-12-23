package com.mustakim.bokbok.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.mustakim.bokbok.data.bloatware.BloatwareDatabase
import com.mustakim.bokbok.data.bloatware.RemovalSafety
import com.mustakim.bokbok.data.model.AppItem
import kotlinx.coroutines.Dispatchers
import androidx.work.*
import com.mustakim.bokbok.data.local.dao.AppDao
import com.mustakim.bokbok.data.local.entity.AppEntity
import com.mustakim.bokbok.workers.AppScanWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import rikka.shizuku.Shizuku
import moe.shizuku.server.IShizukuService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDao: AppDao
) {
    private val packageManager: PackageManager = context.packageManager
    private val workManager = WorkManager.getInstance(context)

    fun observeApps(): Flow<List<AppItem>> {
        return appDao.getAllApps().map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun refreshApps() {
        val request = OneTimeWorkRequestBuilder<AppScanWorker>()
            .build()
        workManager.enqueueUniqueWork(
            AppScanWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun AppEntity.toModel() = AppItem(
        packageName = packageName,
        label = label,
        versionName = versionName,
        versionCode = versionCode,
        isSystemApp = isSystemApp,
        isEnabled = isEnabled,
        isInstalled = isInstalled,
        uid = uid,
        targetSdk = targetSdk,
        minSdk = minSdk,
        firstInstallTime = firstInstallTime,
        lastUpdateTime = lastUpdateTime,
        dataSize = dataSize,
        cacheSize = cacheSize,
        apkSize = apkSize,
        hasActivities = hasActivities,
        isDebuggable = isDebuggable,
        isBloatware = isBloatware,
        removalSafety = removalSafety,
        bloatwareType = bloatwareType,
        bloatwareWarning = bloatwareWarning,
        bloatwareDescription = bloatwareDescription,
        apkPath = apkPath,
        dataPath = dataPath
    )

    fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun requestUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
    
    /**
     * Uninstall app via ADB using Shizuku.
     * Uses "pm uninstall -k --user 0" which keeps data and is reversible.
     */
    suspend fun uninstallViaAdb(packageName: String, keepData: Boolean = true): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                val command = if (keepData) {
                    "pm uninstall -k --user 0 $packageName"
                } else {
                    "pm uninstall --user 0 $packageName"
                }
                executeShellCommand(command)
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Reinstall a previously uninstalled (but kept) app
     */
    suspend fun reinstallApp(packageName: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                executeShellCommand("pm install-existing $packageName")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ADB commands using Shizuku
    suspend fun forceStopApp(packageName: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                executeShellCommand("am force-stop $packageName")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun clearAppCache(packageName: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                executeShellCommand("pm clear $packageName")
                Result.success(Unit)
            } else {
                 Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun disableApp(packageName: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                executeShellCommand("pm disable-user --user 0 $packageName")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun enableApp(packageName: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                executeShellCommand("pm enable --user 0 $packageName")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Grants the necessary permissions to this app using Shizuku.
     * specifically PACKAGE_USAGE_STATS for accurate app size calculation.
     */
    suspend fun grantSelfPermissions(): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                val pkg = context.packageName
                // Grant Usage Stats permission
                executeShellCommand("pm grant $pkg android.permission.PACKAGE_USAGE_STATS")
                executeShellCommand("appops set $pkg android:get_usage_stats allow")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get accurate app size using StorageStatsManager (requires PACKAGE_USAGE_STATS)
     */
    fun getAppSize(packageName: String): Triple<Long, Long, Long>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Check if we have permission first
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, 
                    android.os.Process.myUid(), 
                    context.packageName
                )
                
                if (mode == android.app.AppOpsManager.MODE_ALLOWED) {
                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as android.app.usage.StorageStatsManager
                    val storageStats = storageStatsManager.queryStatsForPackage(
                        android.os.storage.StorageManager.UUID_DEFAULT,
                        packageName,
                        android.os.Process.myUserHandle()
                    )
                    return Triple(storageStats.appBytes, storageStats.dataBytes, storageStats.cacheBytes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null // Fallback to basic size
    }

    suspend fun syncBloatwareDatabase(): Boolean = withContext(Dispatchers.IO) {
        BloatwareDatabase.sync(context)
    }

    private fun checkShizukuPermission(): Boolean {
        try {
             if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return false
             if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
             if (Shizuku.shouldShowRequestPermissionRationale()) {
                 return false
             }
             // Request permission if not granted
             Shizuku.requestPermission(0)
             return false
        } catch (_: Throwable) {
            return false
        }
    }

    private suspend fun executeShellCommand(command: String) = withContext(Dispatchers.IO) {
        try {
            var binder = Shizuku.getBinder()
            var retry = 0
            while (binder == null && retry < 10) {
                delay(200)
                binder = Shizuku.getBinder()
                retry++
            }
            
            if (binder == null) {
                throw IllegalStateException("Shizuku binder is null after retries. Is Shizuku running and authorized?")
            }
            
            val service = IShizukuService.Stub.asInterface(binder)
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
}
