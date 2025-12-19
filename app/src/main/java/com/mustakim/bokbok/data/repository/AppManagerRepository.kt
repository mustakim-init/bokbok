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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.core.content.pm.PackageInfoCompat
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.File
import javax.inject.Inject

class AppManagerRepository @Inject constructor(
    private val context: Context
) {
    private val packageManager: PackageManager = context.packageManager

    fun getInstalledApps(includeSystem: Boolean = true): Flow<List<AppItem>> = flow {
        // Pre-load bloatware database
        BloatwareDatabase.load(context)
        
        val apps = mutableListOf<AppItem>()
        // Use MATCH_UNINSTALLED_PACKAGES to find apps that can be restored (installed for user 0 but not current user)
        val flags = PackageManager.GET_META_DATA or PackageManager.MATCH_UNINSTALLED_PACKAGES
        val packages = packageManager.getInstalledPackages(flags)

        for (packageInfo in packages) {
            val appInfo = packageInfo.applicationInfo ?: continue
            
            // Check if installed for current user
            val isInstalled = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            
            // Filter logic:
            // 1. If not installed (restorable), show it.
            // 2. If installed, respect includeSystem flag.
            if (isInstalled && !includeSystem && isSystemApp) continue

            // Get bloatware info
            val bloatwareInfo = BloatwareDatabase.getBloatwareInfo(context, packageInfo.packageName)
            val isBloatware = bloatwareInfo != null
            val removalSafety = bloatwareInfo?.getRemovalSafety() ?: RemovalSafety.UNKNOWN
            
            // Get min SDK
            val minSdk =
                appInfo.minSdkVersion

            // Get app size (try StorageStatsManager first)
            // If app is not installed (restorable), size will likely be 0 or null, which is fine.
            val sizeInfo = if (isInstalled) getAppSize(packageInfo.packageName) else null
            val (apkSize, dataSize, cacheSize) = if (sizeInfo != null) {
                sizeInfo
            } else {
                // Fallback or uninstalled
                Triple(File(appInfo.sourceDir).length(), 0L, 0L)
            }

            val appItem = AppItem(
                packageName = packageInfo.packageName,
                label = packageManager.getApplicationLabel(appInfo).toString(),
                versionName = packageInfo.versionName,
                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                icon = packageManager.getApplicationIcon(appInfo), // Icon might be default android if uninstalled, but often still cached or accessible if apk exists
                isSystemApp = isSystemApp,
                isEnabled = appInfo.enabled,
                isInstalled = isInstalled, // Important: pass correctly
                uid = appInfo.uid,
                targetSdk = appInfo.targetSdkVersion,
                minSdk = minSdk,
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime,
                apkSize = apkSize,
                dataSize = dataSize,
                cacheSize = cacheSize,
                hasActivities = packageManager.getLaunchIntentForPackage(packageInfo.packageName) != null,
                isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                // Bloatware fields
                isBloatware = isBloatware,
                removalSafety = removalSafety,
                bloatwareInfo = bloatwareInfo,
                bloatwareType = bloatwareInfo?.type,
                bloatwareWarning = bloatwareInfo?.warning,
                bloatwareDescription = bloatwareInfo?.description,
                // Paths
                apkPath = appInfo.sourceDir ?: "",
                dataPath = appInfo.dataDir ?: ""
            )
            apps.add(appItem)
        }
        emit(apps.sortedBy { it.label.lowercase() })
    }.flowOn(Dispatchers.IO)

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
