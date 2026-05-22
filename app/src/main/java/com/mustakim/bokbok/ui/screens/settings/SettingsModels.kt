package com.mustakim.bokbok.ui.screens.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter

data class SettingsQuickAction(
    val icon: Painter,
    val label: String,
    val onClick: () -> Unit,
    val accentColor: Color,
)

data class SettingsItem(
    val icon: Painter,
    val title: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val showUpdateIndicator: Boolean = false,
    val accentColor: Color = Color.Unspecified,
    val keywords: List<String> = emptyList(),
    val onClick: () -> Unit,
)

sealed class SettingsGroupItem {
    data class Nav(val item: SettingsItem) : SettingsGroupItem()
    data class Toggle(
        val icon: Painter,
        val title: String,
        val subtitle: String? = null,
        val accentColor: Color = Color.Unspecified,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
    ) : SettingsGroupItem()
}

data class SettingsGroup(
    val title: String,
    val items: List<SettingsGroupItem>,
)

