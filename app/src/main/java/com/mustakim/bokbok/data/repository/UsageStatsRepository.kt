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

class UsageStatsRepository(private val context: Context) {

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

    suspend fun getUsageStats(
        intervalType: IntervalType,
        date: Long,
        sortOrder: UsageSortOrder
    ): Pair<List<AppUsageInfo>, Long> = withContext(Dispatchers.IO) {
        if (!hasUsageStatsPermission()) {
            return@withContext Pair(emptyList(), 0L)
        }

        val (startTime, endTime) = getTimeRange(intervalType, date)
        
        // We use UsageEvents for accuracy
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
        
        // Handle case where app is still running
        val clampTime = if (System.currentTimeMillis() < endTime) System.currentTimeMillis() else endTime
        usageMap.values.forEach { data ->
            if (data.lastStartTime > 0 && data.lastStartTime < clampTime) {
                data.screenTime += (clampTime - data.lastStartTime)
                data.lastStartTime = 0
            }
        }

        var totalScreenTime = 0L
        val processedList = ArrayList<AppUsageInfo>()
        
        usageMap.values.forEach { data ->
            if (data.screenTime > 0 || data.timesOpened > 0) {
                try {
                    val appInfo = packageManager.getApplicationInfo(data.packageName, 0)
                    val label = appInfo.loadLabel(packageManager).toString()
                    val icon = appInfo.loadIcon(packageManager)
                    val uid = appInfo.uid
                    
                    val (wifiData, mobileData) = getNetworkUsage(uid, startTime, endTime)
                    
                    // Simple battery estimation: 
                    // Baseline: 1 hour of screen time = ~10% battery
                    // This is very rough but provides a visual "score" for battery impact
                    val batteryEst = (data.screenTime.toDouble() / (3600000.0)) * 10.0

                    processedList.add(
                        AppUsageInfo(
                            packageName = data.packageName,
                            appLabel = label,
                            icon = icon,
                            screenTime = data.screenTime,
                            timesOpened = data.timesOpened,
                            lastUsedTime = data.lastUsedTime,
                            mobileDataUsage = mobileData,
                            wifiDataUsage = wifiData,
                            batteryUsage = batteryEst,
                            usagePercentage = 0f // Calculated below
                        )
                    )
                    totalScreenTime += data.screenTime
                } catch (e: PackageManager.NameNotFoundException) {
                    // App might have been uninstalled
                } catch (e: Exception) {
                    // Other errors (security, etc)
                }
            }
        }
        
        // Calculate percentages and Sort
        val finalSortedList = processedList.map { 
            it.copy(usagePercentage = if (totalScreenTime > 0) it.screenTime.toFloat() / totalScreenTime * 100f else 0f)
        }.let { list ->
            when (sortOrder) {
                UsageSortOrder.SCREEN_TIME -> list.sortedByDescending { it.screenTime }
                UsageSortOrder.TIMES_OPENED -> list.sortedByDescending { it.timesOpened }
                UsageSortOrder.LAST_USED -> list.sortedByDescending { it.lastUsedTime }
                UsageSortOrder.APP_NAME -> list.sortedBy { it.appLabel.lowercase() }
                UsageSortOrder.BATTERY_USAGE -> list.sortedByDescending { it.batteryUsage }
                UsageSortOrder.DATA_USAGE -> list.sortedByDescending { it.mobileDataUsage + it.wifiDataUsage }
            }
        }

        Pair(finalSortedList, totalScreenTime)
    }

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

    private fun getTimeRange(intervalType: IntervalType, date: Long): Pair<Long, Long> {
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
