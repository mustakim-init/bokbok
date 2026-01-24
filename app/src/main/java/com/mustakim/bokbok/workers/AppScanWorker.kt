package com.mustakim.bokbok.workers

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mustakim.bokbok.data.bloatware.BloatwareDatabase
import com.mustakim.bokbok.data.local.dao.AppDao
import com.mustakim.bokbok.data.local.entity.AppEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.File

@HiltWorker
class AppScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val appDao: AppDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pm = applicationContext.packageManager
            BloatwareDatabase.load(applicationContext)
            
            val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
            val packages = pm.getInstalledPackages(flags)
            
            // Optimization: Load existing apps to check for changes and skip redundant IPC calls
            val existingAppsMap = appDao.getAppsOneShot().associateBy { it.packageName }
            
            // Optimization: Process apps in smaller chunks to avoid Binder saturation and main thread jank
            val entities = packages.chunked(10).flatMap { chunk ->
                coroutineScope {
                    chunk.map { packageInfo ->
                        async(Dispatchers.IO) {
                            val appInfo = packageInfo.applicationInfo ?: return@async null
                            val packageName = packageInfo.packageName
                            val isInstalled = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            
                            val existing = existingAppsMap[packageName]
                            val isUpToDate = existing != null && existing.lastUpdateTime == packageInfo.lastUpdateTime
                            
                            // 🚀 SMART SCAN: Cache label to avoid Vivo theme engine exception storms
                            val label = if (isUpToDate && existing!!.label.isNotEmpty()) {
                                existing.label
                            } else {
                                try {
                                    pm.getApplicationLabel(appInfo).toString()
                                } catch (_: Exception) {
                                    packageName
                                }
                            }

                            // 🚀 SMART SCAN: Delay expensive metadata (size, paths, launcher)
                            // We use cached values from the DB if they exist, otherwise 0/empty.
                            // These will be "enriched" when the user clicks the app.
                            val apkSize = existing?.apkSize ?: 0L
                            val dataSize = existing?.dataSize ?: 0L
                            val cacheSize = existing?.cacheSize ?: 0L
                            val apkPath = existing?.apkPath ?: ""
                            val dataPath = existing?.dataPath ?: ""
                            val hasLauncher = existing?.hasActivities ?: false

                            val bloatwareInfo = BloatwareDatabase.getBloatwareInfo(applicationContext, packageName)

                            AppEntity(
                                packageName = packageName,
                                label = label,
                                versionName = packageInfo.versionName,
                                versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                                isSystemApp = isSystemApp,
                                isInstalled = isInstalled,
                                uid = appInfo.uid,
                                targetSdk = appInfo.targetSdkVersion,
                                minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 21,
                                isEnabled = appInfo.enabled,
                                firstInstallTime = packageInfo.firstInstallTime,
                                lastUpdateTime = packageInfo.lastUpdateTime,
                                dataSize = dataSize,
                                cacheSize = cacheSize,
                                apkSize = apkSize,
                                hasActivities = hasLauncher,
                                isDebuggable = (appInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
                                isBloatware = bloatwareInfo != null,
                                removalSafety = bloatwareInfo?.getRemovalSafety() ?: com.mustakim.bokbok.data.bloatware.RemovalSafety.UNKNOWN,
                                bloatwareType = bloatwareInfo?.type,
                                bloatwareWarning = bloatwareInfo?.warning,
                                bloatwareDescription = bloatwareInfo?.description,
                                category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) appInfo.category else -1,
                                isUserApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0,
                                apkPath = apkPath,
                                dataPath = dataPath
                            )
                        }
                    }.mapNotNull { it.await() }
                }
            }
            
            appDao.refreshApps(entities)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun getAppSize(context: Context, packageName: String): Triple<Long, Long, Long>? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
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
            } catch (_: Exception) {}
        }
        return null
    }

    companion object {
        const val WORK_NAME = "app_scan_worker"
    }
}
