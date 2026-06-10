package com.mustakim.bokbok.workers

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mustakim.bokbok.data.local.dao.UsageStatsDao
import com.mustakim.bokbok.data.local.entity.UsageStatsEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar

@HiltWorker
class UsageStatsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val usageStatsDao: UsageStatsDao,
    private val appDao: com.mustakim.bokbok.data.local.dao.AppDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val networkStatsManager = applicationContext.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val packageManager = applicationContext.packageManager

            // Fetch for specific range (passed via inputData)
            val startTime = inputData.getLong("start_time", 0L)
            val endTime = inputData.getLong("end_time", 0L)
            
            if (startTime == 0L || endTime == 0L) {
                return@withContext Result.failure()
            }

            val events = usageStatsManager.queryEvents(startTime, endTime)
            val usageMap = HashMap<String, AppUsageTempData>()
            val event = UsageEvents.Event()
            var currentlyActivePackage: String? = null

            // Helper to pause a package
            fun pausePackage(pkgName: String, timestamp: Long) {
                val data = usageMap[pkgName] ?: return
                if (data.lastStartTime > 0) {
                    data.screenTime += (timestamp - data.lastStartTime)
                    data.lastStartTime = 0
                }
                data.activeActivities.clear()
            }

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val packageName = event.packageName ?: continue
                val eventType = event.eventType

                // Handle global screen events
                if (eventType == UsageEvents.Event.SCREEN_NON_INTERACTIVE || 
                    eventType == UsageEvents.Event.KEYGUARD_SHOWN) {
                    currentlyActivePackage?.let { activePkg ->
                        pausePackage(activePkg, event.timeStamp)
                    }
                    currentlyActivePackage = null
                    continue
                }

                val data = usageMap.getOrPut(packageName) { AppUsageTempData(packageName) }

                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED,
                    UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                        // Heuristic: If another package was active, force-pause it
                        if (currentlyActivePackage != null && currentlyActivePackage != packageName) {
                            pausePackage(currentlyActivePackage, event.timeStamp)
                        }
                        currentlyActivePackage = packageName

                        val activityKey = event.className ?: "default_activity"
                        if (data.activeActivities.isEmpty()) {
                            data.lastStartTime = event.timeStamp
                            data.timesOpened++
                        }
                        data.activeActivities.add(activityKey)
                        if (data.lastUsedTime < event.timeStamp) {
                            data.lastUsedTime = event.timeStamp
                        }
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                        val activityKey = event.className ?: "default_activity"
                        data.activeActivities.remove(activityKey)
                        if (data.activeActivities.isEmpty()) {
                            if (data.lastStartTime > 0) {
                                data.screenTime += (event.timeStamp - data.lastStartTime)
                                data.lastStartTime = 0
                            }
                            if (currentlyActivePackage == packageName) {
                                currentlyActivePackage = null
                            }
                        }
                    }
                }
            }

            // Handle still running
            currentlyActivePackage?.let { activePkg ->
                pausePackage(activePkg, endTime)
            }

            // FETCH NETWORK USAGE IN BATCH (Massive performance gain)
            val wifiUsageMap = getNetworkUsageBatch(networkStatsManager, ConnectivityManager.TYPE_WIFI, startTime, endTime)
            val mobileUsageMap = getNetworkUsageBatch(networkStatsManager, ConnectivityManager.TYPE_MOBILE, startTime, endTime)

            // 🚀 SMART SCAN: Use cached labels from AppDao to avoid Vivo theme engine exceptions
            val cachedAppLabels = appDao.getAppsOneShot().associate { it.packageName to it.label }
            val installedAppsUid = packageManager.getInstalledPackages(0).associate { it.packageName to it.applicationInfo?.uid }

            // Parallel processing for metadata to speed up huge lists
            val entities = usageMap.values.chunked(25).flatMap { chunk ->
                coroutineScope {
                    chunk.map { data ->
                        async {
                            if (data.screenTime > 0 || data.timesOpened > 0) {
                                try {
                                    val uid = installedAppsUid[data.packageName] ?: return@async null
                                    val label = cachedAppLabels[data.packageName] ?: data.packageName
                                    
                                    val wifiData = wifiUsageMap[uid] ?: 0L
                                    val mobileData = mobileUsageMap[uid] ?: 0L
                                    val batteryEst = minOf(100.0, (data.screenTime.toDouble() / 3600000.0) * 10.0)

                                    UsageStatsEntity(
                                        packageName = data.packageName,
                                        appLabel = label,
                                        screenTime = data.screenTime,
                                        timesOpened = data.timesOpened,
                                        lastUsedTime = data.lastUsedTime,
                                        mobileDataUsage = mobileData,
                                        wifiDataUsage = wifiData,
                                        batteryUsage = batteryEst,
                                        usagePercentage = 0f
                                    )
                                } catch (_: Exception) { null }
                            } else null
                        }
                    }.mapNotNull { it.await() }
                }
            }

            val totalScreenTime = entities.sumOf { it.screenTime }
            val finalEntities = entities.map { 
                it.copy(usagePercentage = if (totalScreenTime > 0) it.screenTime.toFloat() / totalScreenTime * 100f else 0f)
            }

            usageStatsDao.refreshUsageStats(finalEntities)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun getNetworkUsageBatch(
        manager: NetworkStatsManager, 
        networkType: Int, 
        startTime: Long, 
        endTime: Long
    ): Map<Int, Long> {
        val usageMap = mutableMapOf<Int, Long>()
        try {
            val stats = manager.querySummary(networkType, null, startTime, endTime)
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                usageMap[uid] = (usageMap[uid] ?: 0L) + bytes
            }
            stats.close()
        } catch (_: Exception) {}
        return usageMap
    }

    private data class AppUsageTempData(
        val packageName: String,
        var screenTime: Long = 0,
        var timesOpened: Int = 0,
        var lastUsedTime: Long = 0,
        var lastStartTime: Long = 0,
        val activeActivities: MutableSet<String> = HashSet()
    )

    companion object {
        const val WORK_NAME = "usage_stats_worker"
    }
}
