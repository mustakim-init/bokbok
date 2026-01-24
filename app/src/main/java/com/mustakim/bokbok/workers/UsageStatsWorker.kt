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
                                    val batteryEst = (data.screenTime.toDouble() / 3600000.0) * 10.0

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
        var lastStartTime: Long = 0
    )

    companion object {
        const val WORK_NAME = "usage_stats_worker"
    }
}
