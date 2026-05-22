package com.mustakim.bokbok.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import com.mustakim.bokbok.ui.theme.DarkMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppearanceSettingsScreen(
    navController: NavController,
    themeViewModel: ThemeViewModel,
    scrollBehavior: TopAppBarScrollBehavior
) {
    val pureBlack by themeViewModel.pureBlack.collectAsState()
    val darkMode by themeViewModel.darkMode.collectAsState()
    val useSystemFont by themeViewModel.useSystemFont.collectAsState()
    val disableBlur by themeViewModel.disableBlur.collectAsState()
    val blurRadius by themeViewModel.blurRadius.collectAsState()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        text = "Appearance",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    BokBokIconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            SettingsGroupTitle(title = "Theme")
            
            // Dark Mode Selection
            EnumListPreference(
                title = "Theme Mode",
                subtitle = when(darkMode) {
                    DarkMode.ON -> "Dark"
                    DarkMode.OFF -> "Light"
                    DarkMode.AUTO -> "Follow System"
                },
                icon = Icons.Default.Visibility,
                value = darkMode,
                onValueChange = { themeViewModel.setDarkMode(it) },
                entries = DarkMode.values().associateWith { 
                    when(it) {
                        DarkMode.ON -> "Dark"
                        DarkMode.OFF -> "Light"
                        DarkMode.AUTO -> "Follow System"
                    }
                }
            )

            SwitchPreference(
                title = "Pure Black",
                subtitle = "Use absolute black for dark theme",
                icon = Icons.Default.Visibility,
                checked = pureBlack,
                onCheckedChange = { themeViewModel.setPureBlack(it) }
            )

            SettingsGroupTitle(title = "Personalization")

            PreferenceEntry(
                title = "Theme Color",
                subtitle = "Pick a seed color for the UI",
                icon = Icons.Default.Palette,
                onClick = { navController.navigate("settings/appearance/palette_picker") }
            )

            SwitchPreference(
                title = "Use System Font",
                subtitle = "Use the device's system font",
                icon = Icons.Default.Visibility,
                checked = useSystemFont,
                onCheckedChange = { themeViewModel.setUseSystemFont(it) }
            )

            SettingsGroupTitle(title = "Effects")

            SwitchPreference(
                title = "Disable Blur",
                subtitle = "Turn off mesh and glass blur effects",
                icon = Icons.Default.Visibility,
                checked = disableBlur,
                onCheckedChange = { themeViewModel.setDisableBlur(it) }
            )

            if (!disableBlur) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        text = "Blur Radius",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Slider(
                        value = blurRadius,
                        onValueChange = { themeViewModel.setBlurRadius(it) },
                        valueRange = 5f..100f
                    )
                }
            }
            
            // Add Music Player specific settings as a sub-section
            SettingsGroupTitle(title = "Music Player")
            PreferenceEntry(
                title = "Player Styling",
                subtitle = "Customize the music player appearance",
                icon = Icons.Default.Palette,
                onClick = { /* This will be handled by music module's sub-routes */ }
            )
        }
    }
}

@Composable
fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
fun SwitchPreference(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun PreferenceEntry(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun <T> EnumListPreference(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: T,
    onValueChange: (T) -> Unit,
    entries: Map<T, String>
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable { showDialog = true }
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                Column {
                    entries.forEach { (entryValue, entryName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(entryValue)
                                    showDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (entryValue == value),
                                onClick = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(entryName)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
