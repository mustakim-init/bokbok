package com.mustakim.bokbok.data.model

import androidx.compose.runtime.Stable

@Stable
data class GameItem(
    val packageName: String,
    val label: String,
    val isHiddenFromLauncher: Boolean = false,
    val isUserAdded: Boolean = false,
    val installedTime: Long = 0L,
    val apkSize: Long = 0L,
    val customSettingsJson: String = "{}"
)
