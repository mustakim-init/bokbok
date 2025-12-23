package com.mustakim.bokbok.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.net.ConnectivityManager
import com.mustakim.bokbok.data.model.AppUsageInfo
import com.mustakim.bokbok.viewmodel.IntervalType
import com.mustakim.bokbok.viewmodel.UsageSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Collections
import java.util.Locale

import androidx.work.*
import com.mustakim.bokbok.data.local.dao.UsageStatsDao
import com.mustakim.bokbok.data.local.entity.UsageStatsEntity
import com.mustakim.bokbok.workers.UsageStatsWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageStatsDao: UsageStatsDao
) {
    private val workManager = WorkManager.getInstance(context)

    private val usageStatsManager: UsageStatsManager by lazy {
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    }

    private val networkStatsManager: NetworkStatsManager by lazy {
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager
    }

    private val packageManager: PackageManager by lazy {
        context.packageManager
    }

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    
    fun requestUsageStatsPermission() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun observeUsageStats(): Flow<List<AppUsageInfo>> {
        return usageStatsDao.getUsageStats().map { entities ->
            entities.map { it.toModel() }
        }
    }

    fun refreshUsageStats(startTime: Long, endTime: Long) {
        val inputData = Data.Builder()
            .putLong("start_time", startTime)
            .putLong("end_time", endTime)
            .build()

        val request = OneTimeWorkRequestBuilder<UsageStatsWorker>()
            .setInputData(inputData)
            .build()
            
        workManager.enqueueUniqueWork(
            UsageStatsWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE, // Change to REPLACE to trigger new fetch immediately
            request
        )
    }

    fun observeWorkStatus(): Flow<WorkInfo.State?> {
        return workManager.getWorkInfosForUniqueWorkFlow(UsageStatsWorker.WORK_NAME)
            .map { it.firstOrNull()?.state }
    }

    private fun UsageStatsEntity.toModel() = AppUsageInfo(
        packageName = packageName,
        appLabel = appLabel,
        screenTime = screenTime,
        timesOpened = timesOpened,
        lastUsedTime = lastUsedTime,
        mobileDataUsage = mobileDataUsage,
        wifiDataUsage = wifiDataUsage,
        batteryUsage = batteryUsage,
        usagePercentage = usagePercentage
    )

    private fun getNetworkUsage(uid: Int, startTime: Long, endTime: Long): Pair<Long, Long> {
        var wifiData = 0L
        var mobileData = 0L
        
        try {
            // Wifi
            val wifiStats = networkStatsManager.queryDetailsForUid(
                ConnectivityManager.TYPE_WIFI,
                null,
                startTime,
                endTime,
                uid
            )
            val bucket = NetworkStats.Bucket()
            while (wifiStats.hasNextBucket()) {
                wifiStats.getNextBucket(bucket)
                wifiData += bucket.rxBytes + bucket.txBytes
            }
            wifiStats.close()
            
            // Mobile
            val mobileStats = networkStatsManager.queryDetailsForUid(
                ConnectivityManager.TYPE_MOBILE,
                null,
                startTime,
                endTime,
                uid
            )
            while (mobileStats.hasNextBucket()) {
                mobileStats.getNextBucket(bucket)
                mobileData += bucket.rxBytes + bucket.txBytes
            }
            mobileStats.close()
        } catch (e: Exception) {
            // Permission or other issue
        }
        
        return Pair(wifiData, mobileData)
    }

    private data class AppUsageTempData(
        val packageName: String,
        var screenTime: Long = 0,
        var timesOpened: Int = 0,
        var lastUsedTime: Long = 0,
        var lastStartTime: Long = 0
    )

    fun getTimeRange(intervalType: IntervalType, date: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = date
        
        val startTime: Long
        val endTime: Long

        when (intervalType) {
            IntervalType.DAILY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                startTime = calendar.timeInMillis
                
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                endTime = calendar.timeInMillis
            }
            IntervalType.WEEKLY -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                startTime = calendar.timeInMillis
                
                calendar.add(Calendar.DAY_OF_YEAR, 6)
                calendar.set(Calendar.HOUR_OF_DAY, 23)
                calendar.set(Calendar.MINUTE, 59)
                calendar.set(Calendar.SECOND, 59)
                calendar.set(Calendar.MILLISECOND, 999)
                endTime = calendar.timeInMillis
            }
        }
        
        return Pair(startTime, endTime)
    }
}
