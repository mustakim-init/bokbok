package com.mustakim.bokbok.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_stats")
data class UsageStatsEntity(
    @PrimaryKey val packageName: String,
    val appLabel: String,
    val screenTime: Long,
    val timesOpened: Int,
    val lastUsedTime: Long,
    val mobileDataUsage: Long,
    val wifiDataUsage: Long,
    val batteryUsage: Double,
    val usagePercentage: Float
)
