package com.mustakim.bokbok.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mustakim.bokbok.ui.theme.AppTheme
import com.mustakim.bokbok.viewmodel.ThemeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    themeViewModel: ThemeViewModel
) {
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // APPEARANCE SECTION (Working)
            item {
                AppearanceSection(
                    selectedTheme = selectedTheme,
                    onThemeSelected = { themeViewModel.setTheme(it) }
                )
            }

            // NOTIFICATION PREFERENCES (Placeholder)
            item {
                SettingsSection(
                    title = "Notification Preferences",
                    items = listOf(
                        "Push Notifications",
                        "Notification Sounds",
                        "Vibration",
                        "Battery Optimization" // Add link to battery settings
                    ),
                    onItemClick = { index ->
                        if (index == 3) { // Battery Optimization clicked
                            navController.navigate("battery_optimization")
                        }
                    }
                )
            }

            // AUDIO & VOICE (Placeholder)
            item {
                SettingsSection(
                    title = "Audio & Voice",
                    items = listOf(
                        "Audio Quality",
                        "Noise Cancellation",
                        "Push-to-Talk Settings"
                    )
                )
            }

            // PRIVACY (Placeholder)
            item {
                SettingsSection(
                    title = "Privacy",
                    items = listOf(
                        "Who Can Call Me",
                        "Online Status Visibility",
                        "Blocked Users"
                    )
                )
            }
        }
    }
}

@Composable
fun AppearanceSection(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Column {
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,  // ← Added
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Theme Selector
        Text(
            text = "Color Theme",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,  // ← Added
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Theme Chips Grid
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val themes = AppTheme.entries
            val rows = themes.chunked(2)

            rows.forEach { rowThemes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowThemes.forEach { theme ->
                        ThemeChip(
                            theme = theme,
                            isSelected = theme == selectedTheme,
                            onSelected = { onThemeSelected(theme) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Fill empty space if odd number
                    if (rowThemes.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeChip(
    theme: AppTheme,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val themeName = when (theme) {
        AppTheme.CATPPUCCIN -> "Catppuccin"
        AppTheme.GRUVBOX -> "Gruvbox"
        AppTheme.MONOCHROME -> "Monochrome"
        AppTheme.NORD -> "Nord"
        AppTheme.TOKYO_NIGHT -> "Tokyo Night"
        AppTheme.DRACULA -> "Dracula"
        AppTheme.SOLARIZED -> "Solarized"
        AppTheme.ROSE_PINE -> "Rosé Pine"
        AppTheme.ONE_DARK -> "One Dark"
        AppTheme.MATERIAL_CLASSIC -> "Material"
    }

    FilterChip(
        selected = isSelected,
        onClick = onSelected,
        label = {
            Text(
                text = themeName,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier,
        enabled = true,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            FilterChipDefaults.filterChipBorder(
                enabled = true,
                selected = false
            )
        }
    )
}

@Composable
fun SettingsSection(
    title: String,
    items: List<String>,
    onItemClick: ((Int) -> Unit)? = null
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,  // ← Added
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface  // ← Added
            )
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick?.invoke(index) }
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item,
                            color = MaterialTheme.colorScheme.onSurface  // ← Added
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Navigate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant  // ← Added
                        )
                    }
                    if (index < items.size - 1) {
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
