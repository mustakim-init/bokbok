package com.mustakim.bokbok.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import rikka.shizuku.Shizuku
import moe.shizuku.server.IShizukuService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appDao: AppDao,
    private val configDao: com.mustakim.bokbok.data.local.dao.SystemConfigDao
) {
    private val packageManager: PackageManager = context.packageManager
    private val workManager = WorkManager.getInstance(context)

    fun observeApps(): Flow<List<AppItem>> {
        return appDao.getAllApps().map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun observeApp(packageName: String): Flow<AppItem?> {
        return appDao.getAppByPackage(packageName).map { it?.toModel() }
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
        category = category,
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
    
    suspend fun clearAppData(packageName: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                // pm clear resets EVERYTHING (data + cache)
                executeShellCommand("pm clear $packageName")
                Result.success(Unit)
            } else {
                 Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearAppCache(packageName: String): Result<Unit> {
        // Technically, pm clear is the main tool. Specific cache deletion
        // usually needs internal APIs. We'll reuse pm clear for consistency if needed,
        // or just let it be a placeholder for now as pm clear handles everything.
        return clearAppData(packageName)
    }

    suspend fun disableApp(packageName: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                executeShellCommand("pm disable-user --user 0 $packageName")
                appDao.updateAppEnabledState(packageName, false)
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
                appDao.updateAppEnabledState(packageName, true)
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
                // Grant Overlay permission (SYSTEM_ALERT_WINDOW)
                executeShellCommand("appops set $pkg android:system_alert_window allow")
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
                // System.err log storm typically happens here on Vivo if not properly handled
            }
        }
        return null // Fallback to basic size
    }

    /**
     * 🚀 ON-DEMAND ENRICHMENT: Fetch deep metadata only when needed.
     * This avoids cold-start lag by delaying Binder-heavy operations (paths, sizes).
     */
    suspend fun fetchFullAppDetails(packageName: String) = withContext(Dispatchers.IO) {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val sizes = getAppSize(packageName) ?: Triple(0L, 0L, 0L)
            
            val hasLauncher = packageManager.queryIntentActivities(
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
                0
            ).isNotEmpty()

            appDao.updateAppDetails(
                packageName = packageName,
                apkPath = appInfo.sourceDir ?: "",
                dataPath = appInfo.dataDir ?: "",
                apkSize = sizes.first,
                dataSize = sizes.second,
                cacheSize = sizes.third,
                hasLauncher = hasLauncher
            )
        } catch (_: Exception) {}
    }

    /**
     * Gamer Power Tools: List components of an app (Services, Receivers, Activities)
     */
    fun getAppComponents(packageName: String, type: ComponentType): List<com.mustakim.bokbok.data.model.AppComponent> {
        val flags = PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or 
                    PackageManager.GET_ACTIVITIES or PackageManager.GET_DISABLED_COMPONENTS
        
        val packageInfo = try {
            packageManager.getPackageInfo(packageName, flags)
        } catch (e: Exception) {
            return emptyList()
        }

        val componentList = when (type) {
            ComponentType.SERVICE -> packageInfo.services?.toList()
            ComponentType.RECEIVER -> packageInfo.receivers?.toList()
            ComponentType.ACTIVITY -> packageInfo.activities?.toList()
        } ?: return emptyList()

        return componentList.map { info ->
            // Use explicit cast if necessary, though it should be inferred as ComponentInfo
            val comp = info as android.content.pm.ComponentInfo
            com.mustakim.bokbok.data.model.AppComponent(
                packageName = packageName,
                name = comp.name,
                label = comp.loadLabel(packageManager).toString(),
                isEnabled = packageManager.getComponentEnabledSetting(
                    android.content.ComponentName(packageName, comp.name)
                ).let { setting ->
                    when(setting) {
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                        else -> comp.enabled
                    }
                },
                isExported = comp.exported,
                processName = comp.processName
            )
        }
    }

    suspend fun toggleComponent(packageName: String, componentName: String, enabled: Boolean): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                val state = if (enabled) "enable" else "disable"
                executeShellCommand("pm $state $packageName/$componentName")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun togglePermission(packageName: String, permission: String, grant: Boolean): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                val action = if (grant) "grant" else "revoke"
                executeShellCommand("pm $action $packageName $permission")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Gamer Power Tools: Handle App Ops (Battery, Overlay, Background)
     */
    suspend fun setAppOp(packageName: String, op: String, mode: String): Result<Unit> {
        return try {
            if (checkShizukuPermission()) {
                executeShellCommand("appops set $packageName $op $mode")
                Result.success(Unit)
            } else {
                Result.failure(SecurityException("Shizuku permission not granted"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setBatteryOptimization(packageName: String, optimize: Boolean): Result<Unit> {
        val mode = if (optimize) "allow" else "ignore"
        // This is a bit complex as it might involve different op codes/commands depending on OS
        return setAppOp(packageName, "RUN_IN_BACKGROUND", if (optimize) "allow" else "ignore")
    }

    suspend fun setOverlayPermission(packageName: String, allow: Boolean): Result<Unit> {
        val mode = if (allow) "allow" else "deny"
        return setAppOp(packageName, "SYSTEM_ALERT_WINDOW", mode)
    }

    /**
     * App Analysis Helpers
     */
    fun getPermissionCount(packageName: String): Int {
        return try {
            val flags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES or android.content.pm.PackageManager.GET_PERMISSIONS
            } else {
                @Suppress("DEPRECATION")
                android.content.pm.PackageManager.GET_SIGNATURES or android.content.pm.PackageManager.GET_PERMISSIONS
            }
            val pi = packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            pi.requestedPermissions?.size ?: 0
        } catch (e: Exception) { 0 }
    }

    fun getComponentCount(packageName: String): Triple<Int, Int, Int> {
        return try {
            val flags = PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_ACTIVITIES or PackageManager.GET_DISABLED_COMPONENTS
            val pi = packageManager.getPackageInfo(packageName, flags)
            Triple(
                pi.services?.size ?: 0,
                pi.receivers?.size ?: 0,
                pi.activities?.size ?: 0
            )
        } catch (e: Exception) { Triple(0, 0, 0) }
    }

    fun getAppPermissions(packageName: String): List<com.mustakim.bokbok.data.model.AppPermissionDetail> {
        return try {
            val pi = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            val requested = pi.requestedPermissions ?: return emptyList()
            val flags = pi.requestedPermissionsFlags ?: IntArray(requested.size)

            requested.mapIndexed { index, perm ->
                val info = try { packageManager.getPermissionInfo(perm, 0) } catch (e: Exception) { null }
                val label = info?.loadLabel(packageManager)?.toString() ?: perm.substringAfterLast(".")
                val isGranted = (flags[index] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                
                com.mustakim.bokbok.data.model.AppPermissionDetail(
                    permission = perm,
                    label = label,
                    isGranted = isGranted,
                    isRuntime = info?.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    enum class ComponentType { SERVICE, RECEIVER, ACTIVITY }

    suspend fun syncBloatwareDatabase(): Boolean = withContext(Dispatchers.IO) {
        BloatwareDatabase.sync(context)
    }

    private var isShizukuAuthorizedCached: Boolean? = null

    private fun checkShizukuPermission(): Boolean {
        if (isShizukuAuthorizedCached == true) return true
        
        try {
             if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return false
             val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
             
             if (isShizukuAuthorizedCached == null) {
                 // Try to load from cache once
                 MainScope().launch(Dispatchers.IO) {
                     val cached = configDao.getString("shizuku_authorized")
                     if (cached == "true") isShizukuAuthorizedCached = true
                 }
             }

             if (granted) {
                 if (isShizukuAuthorizedCached != true) {
                     isShizukuAuthorizedCached = true
                     MainScope().launch(Dispatchers.IO) {
                         configDao.putString("shizuku_authorized", "true")
                     }
                 }
                 return true
             }
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
