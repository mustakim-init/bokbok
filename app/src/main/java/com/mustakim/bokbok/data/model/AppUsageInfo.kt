package com.mustakim.bokbok.data.model

import android.graphics.drawable.Drawable

data class AppUsageInfo(
    val packageName: String,
    val appLabel: String,
    val icon: Drawable?,
    val screenTime: Long,           // in milliseconds
    val timesOpened: Int,
    val lastUsedTime: Long,         // timestamp
    val mobileDataUsage: Long,      // bytes
    val wifiDataUsage: Long,        // bytes
    val batteryUsage: Double,       // Estimated percentage or mAh
    val usagePercentage: Float      // 0-100 relative to total screen time
)
