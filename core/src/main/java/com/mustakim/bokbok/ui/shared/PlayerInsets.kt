package com.mustakim.bokbok.ui.shared
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

val MiniPlayerHeight = 80.dp
val LocalPlayerAwareWindowInsets = compositionLocalOf { WindowInsets(0.dp, 0.dp, 0.dp, 0.dp) }
