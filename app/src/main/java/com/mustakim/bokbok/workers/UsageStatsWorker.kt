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
import kotlinx.coroutines.withContext
import java.util.Calendar

@HiltWorker
class UsageStatsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val usageStatsDao: UsageStatsDao
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val usageStatsManager = applicationContext.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val networkStatsManager = applicationContext.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
            val packageManager = applicationContext.packageManager

            // Fetch for the current day
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val startTime = calendar.timeInMillis

            val events = usageStatsManager.queryEvents(startTime, endTime)
            val usageMap = HashMap<String, AppUsageTempData>()
            val event = UsageEvents.Event()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val packageName = event.packageName
                val data = usageMap.getOrPut(packageName) { AppUsageTempData(packageName) }

                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        data.lastStartTime = event.timeStamp
                        if (data.lastUsedTime < event.timeStamp) {
                            data.lastUsedTime = event.timeStamp
                        }
                        data.timesOpened++
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED, 
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        if (data.lastStartTime > 0) {
                            data.screenTime += (event.timeStamp - data.lastStartTime)
                            data.lastStartTime = 0
                        }
                    }
                }
            }

            // Handle still running
            usageMap.values.forEach { data ->
                if (data.lastStartTime > 0) {
                    data.screenTime += (endTime - data.lastStartTime)
                }
            }

            // FETCH NETWORK USAGE IN BATCH (Massive performance gain)
            val wifiUsageMap = getNetworkUsageBatch(networkStatsManager, ConnectivityManager.TYPE_WIFI, startTime, endTime)
            val mobileUsageMap = getNetworkUsageBatch(networkStatsManager, ConnectivityManager.TYPE_MOBILE, startTime, endTime)

            var totalScreenTime = 0L
            val entities = mutableListOf<UsageStatsEntity>()

            // Batch fetch ApplicationInfo to avoid missing labels or slow lookups in loop
            val installedApps = packageManager.getInstalledPackages(0).associateBy { it.packageName }

            usageMap.values.forEach { data ->
                if (data.screenTime > 0 || data.timesOpened > 0) {
                    try {
                        val packageInfo = installedApps[data.packageName] ?: return@forEach
                        val appInfo = packageInfo.applicationInfo ?: return@forEach
                        
                        val label = pmGetLabel(packageManager, appInfo)
                        val uid = appInfo.uid
                        
                        val wifiData = wifiUsageMap[uid] ?: 0L
                        val mobileData = mobileUsageMap[uid] ?: 0L
                        val batteryEst = (data.screenTime.toDouble() / 3600000.0) * 10.0

                        entities.add(
                            UsageStatsEntity(
                                packageName = data.packageName,
                                appLabel = label,
                                screenTime = data.screenTime,
                                timesOpened = data.timesOpened,
                                lastUsedTime = data.lastUsedTime,
                                mobileDataUsage = mobileData,
                                wifiDataUsage = wifiData,
                                batteryUsage = batteryEst,
                                usagePercentage = 0f // Calculate later
                            )
                        )
                        totalScreenTime += data.screenTime
                    } catch (_: Exception) {}
                }
            }

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

    private fun pmGetLabel(pm: PackageManager, info: android.content.pm.ApplicationInfo): String {
        return try {
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            info.packageName
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
        var lastStartTime: Long = 0
    )

    companion object {
        const val WORK_NAME = "usage_stats_worker"
    }
}
