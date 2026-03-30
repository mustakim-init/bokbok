package com.mustakim.bokbok.data.model

import androidx.compose.runtime.Stable
import com.mustakim.bokbok.data.bloatware.DebloatObject
import com.mustakim.bokbok.data.bloatware.RemovalSafety

@Stable
data class AppItem(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val isInstalled: Boolean = true,
    val uid: Int,
    val targetSdk: Int,
    val minSdk: Int = 0,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val dataSize: Long = 0L,
    val cacheSize: Long = 0L,
    val apkSize: Long = 0L,
    val hasActivities: Boolean = false,
    val isDebuggable: Boolean = false,
    val category: Int = -1,
    // Selection state for batch operations
    val isSelected: Boolean = false,
    
    // Bloatware detection fields
    val isBloatware: Boolean = false,
    val removalSafety: RemovalSafety = RemovalSafety.UNKNOWN,
    val bloatwareInfo: DebloatObject? = null,
    val bloatwareType: String? = null, // aosp, carrier, google, oem, misc
    val bloatwareWarning: String? = null,
    val bloatwareDescription: String? = null,
    
    // App paths
    val apkPath: String = "",
    val dataPath: String = ""
)

