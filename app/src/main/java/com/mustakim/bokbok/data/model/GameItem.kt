package com.mustakim.bokbok.data.model

import android.graphics.drawable.Drawable
import androidx.compose.runtime.Stable

@Stable
data class GameItem(
    val packageName: String,
    val label: String,
    val icon: Drawable?,
    val isHiddenFromLauncher: Boolean = false,
    val isUserAdded: Boolean = false,
    val installedTime: Long = 0L,
    val apkSize: Long = 0L
)
