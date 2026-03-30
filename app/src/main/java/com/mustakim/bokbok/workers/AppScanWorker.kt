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
    private val appDao: AppDao,
    private val gameRepository: com.mustakim.bokbok.data.repository.GameRepository
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val pm = applicationContext.packageManager
            BloatwareDatabase.load(applicationContext)
            
            val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES
            val packages = pm.getInstalledPackages(flags)
            
            // Optimization: Load existing apps to check for changes and skip redundant IPC calls
            val existingAppsMap = appDao.getAppsOneShot().associateBy { it.packageName }
            
            // Optimization: Process apps in parallel to avoid sequential binder bottlenecks
            val entities = packages.chunked(25).flatMap { chunk ->
                coroutineScope {
                    chunk.map { packageInfo ->
                        async(Dispatchers.IO) {
                            val appInfo = packageInfo.applicationInfo ?: return@async null
                            val packageName = packageInfo.packageName
                            val isInstalled = (appInfo.flags and ApplicationInfo.FLAG_INSTALLED) != 0
                            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            
                            val existing = existingAppsMap[packageName]
                            val bloatwareInfo = BloatwareDatabase.getBloatwareInfo(applicationContext, packageName)
                            
                            val shouldRefreshSize = existing == null || 
                                                  existing.lastUpdateTime != packageInfo.lastUpdateTime ||
                                                  existing.dataSize == 0L
                            
                            val (apkSize, dataSize, cacheSize) = if (isInstalled) {
                                if (shouldRefreshSize) {
                                    getAppSize(applicationContext, packageName) ?: Triple(0L, 0L, 0L)
                                } else {
                                    Triple(existing!!.apkSize, existing.dataSize, existing.cacheSize)
                                }
                            } else Triple(0L, 0L, 0L)

                            val hasLauncher = if (isInstalled) {
                                if (existing != null && existing.lastUpdateTime == packageInfo.lastUpdateTime) {
                                    existing.hasActivities
                                } else {
                                    pm.queryIntentActivities(
                                        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(packageName),
                                        0
                                    ).isNotEmpty()
                                }
                            } else false

                            AppEntity(
                                packageName = packageName,
                                label = pm.getApplicationLabel(appInfo).toString(),
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
                                apkPath = appInfo.sourceDir ?: "",
                                dataPath = appInfo.dataDir ?: ""
                            )
                        }
                    }.mapNotNull { it.await() }
                }
            }
            
            appDao.refreshApps(entities)
            BloatwareDatabase.clear() // Free huge in-memory map after processing
            
            // Sync game list to shell after scan to ensure newly discovered games are monitored
            gameRepository.syncGameListToShell()
            
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
