package com.mustakim.bokbok.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mustakim.bokbok.data.bloatware.RemovalSafety

@Entity(tableName = "apps")
data class AppEntity(
    @PrimaryKey val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isInstalled: Boolean = true,
    val uid: Int,
    val targetSdk: Int,
    val minSdk: Int,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val dataSize: Long = 0L,
    val cacheSize: Long = 0L,
    val apkSize: Long = 0L,
    val hasActivities: Boolean = false,
    val isDebuggable: Boolean = false,
    
    // Bloatware detection fields
    val isBloatware: Boolean = false,
    val removalSafety: RemovalSafety = RemovalSafety.UNKNOWN,
    val bloatwareType: String? = null,
    val bloatwareWarning: String? = null,
    val bloatwareDescription: String? = null,
    
    // Performance features
    val category: Int = -1,
    val isUserApp: Boolean = false,

    // App paths
    val apkPath: String = "",
    val dataPath: String = ""
)
