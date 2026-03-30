package com.mustakim.bokbok.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NoiseAware
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mustakim.bokbok.ui.theme.AppTheme
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import com.mustakim.bokbok.viewmodel.AdbSetupViewModel
import com.mustakim.bokbok.data.adb.ResurrectionSetupState
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    adbSetupViewModel: AdbSetupViewModel
) {
    val selectedTheme by themeViewModel.selectedTheme.collectAsState()
    val adbSetupState by adbSetupViewModel.setupState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = "Settings",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // APPEARANCE SECTION
            item {
                AppearanceSection(
                    selectedTheme = selectedTheme,
                    onThemeSelected = { themeViewModel.setTheme(it) }
                )
            }

            // NOTIFICATION PREFERENCES
            item {
                SettingsSection(
                    title = "Notifications",
                    icon = Icons.Default.Notifications,
                    iconTint = MaterialTheme.colorScheme.error,
                    items = listOf(
                        SettingsItem(
                            title = "Push Notifications",
                            subtitle = "Get notified about messages",
                            icon = Icons.Default.Notifications
                        ),
                        SettingsItem(
                            title = "Notification Sounds",
                            subtitle = "Customize alert sounds",
                            icon = Icons.Default.MusicNote
                        ),
                        SettingsItem(
                            title = "Vibration",
                            subtitle = "Haptic feedback for alerts",
                            icon = Icons.Default.Vibration
                        ),
                        SettingsItem(
                            title = "Battery Optimization",
                            subtitle = "Improve background reliability",
                            icon = Icons.Default.BatteryStd
                        )
                    ),
                    onItemClick = { index ->
                        if (index == 3) {
                            navController.navigate("battery_optimization")
                        }
                    }
                )
            }

            // AUDIO & VOICE
            item {
                SettingsSection(
                    title = "Audio & Voice",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    items = listOf(
                        SettingsItem(
                            title = "Audio Quality",
                            subtitle = "High quality voice calls",
                            icon = Icons.AutoMirrored.Filled.VolumeUp
                        ),
                        SettingsItem(
                            title = "Noise Cancellation",
                            subtitle = "Reduce background noise",
                            icon = Icons.Default.NoiseAware
                        ),
                        SettingsItem(
                            title = "Push-to-Talk",
                            subtitle = "Voice activation settings",
                            icon = Icons.Default.Mic
                        )
                    )
                )
            }

            // ADB RESURRECTION SECTION
            item {
                AdbResurrectionSection(
                    setupState = adbSetupState,
                    onStartSetup = { adbSetupViewModel.runSetup() },
                    onStartPairing = { adbSetupViewModel.initiatePairing() },
                    onSelfAuthorize = { adbSetupViewModel.selfAuthorizeViaShizuku() },
                    onPairAction = { port, code -> adbSetupViewModel.submitPairingCode(port, code) },
                    onResetSetup = { adbSetupViewModel.resetSetup() },
                    onGetPublicKey = { adbSetupViewModel.getAdbPublicKey() }
                )
            }
            
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

data class SettingsItem(
    val title: String,
    val subtitle: String? = null,
    val icon: ImageVector
)

@Composable
fun AppearanceSection(
    selectedTheme: AppTheme,
    onThemeSelected: (AppTheme) -> Unit
) {
    Column {
        // Section Header with Icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Customize your look",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Theme Grid
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val themes = AppTheme.entries
                val rows = themes.chunked(2)

                rows.forEach { rowThemes ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            ThemeChip(
                                theme = theme,
                                isSelected = theme == selectedTheme,
                                onSelected = { onThemeSelected(theme) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowThemes.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
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

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) 
            MaterialTheme.colorScheme.primaryContainer 
        else 
            MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipBackground"
    )

    val contentColor by animateColorAsState(
        targetValue = if (isSelected) 
            MaterialTheme.colorScheme.onPrimaryContainer 
        else 
            MaterialTheme.colorScheme.onSurface,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "chipContent"
    )

    Surface(
        onClick = onSelected,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
        border = if (isSelected) 
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
        else 
            null
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = themeName,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = contentColor
            )
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    items: List<SettingsItem>,
    onItemClick: ((Int) -> Unit)? = null
) {
    Column {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SettingsRow(
                        item = item,
                        onClick = { onItemClick?.invoke(index) },
                        showDivider = index < items.size - 1
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    item: SettingsItem,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (item.subtitle != null) {
                        Text(
                            text = item.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 54.dp, end = 16.dp)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun AdbResurrectionSection(
    setupState: ResurrectionSetupState,
    onStartSetup: () -> Unit,
    onStartPairing: () -> Unit,
    onSelfAuthorize: () -> Unit,
    onPairAction: (Int, String) -> Unit,
    onResetSetup: () -> Unit,
    onGetPublicKey: () -> String
) {
    val clipboardManager = LocalClipboardManager.current
    
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "ADB Resurrection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Automatic reboot persistence",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (setupState) {
                    is ResurrectionSetupState.NotSetup -> {
                        Text(
                            text = "ADB Resurrection allows BokBok to automatically restart Shizuku and optimizations after every reboot.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Only show Self-Authorize if Shizuku is running
                            val isShizukuRunning = remember {
                                try { rikka.shizuku.Shizuku.getBinder() != null } catch (e: Exception) { false }
                            }
                            
                            Button(
                                onClick = onStartPairing,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.BugReport, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Wireless ADB Pairing (Recommended)")
                            }

                            if (isShizukuRunning) {
                                OutlinedButton(
                                    onClick = onSelfAuthorize,
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Self-Authorize via Shizuku (Experimental)")
                                }
                            }

                            OutlinedButton(
                                onClick = onStartSetup,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Connect (Already Authorized)")
                            }
                        }
                    }
                    is ResurrectionSetupState.Discovering -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Finding Wireless ADB...", style = MaterialTheme.typography.bodyMedium)
                        }
                        Text(
                            text = "Make sure Wireless Debugging is enabled and 'Pair device with pairing code' is open if you clicked Pair.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    is ResurrectionSetupState.PairingCodeRequired -> {
                        var code by remember { mutableStateOf("") }
                        AlertDialog(
                            onDismissRequest = onResetSetup,
                            title = { Text("Enter Pairing Code") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Enter the 6-digit code shown in Wireless Debugging.")
                                    OutlinedTextField(
                                        value = code,
                                        onValueChange = { if (it.length <= 6) code = it },
                                        label = { Text("Pairing Code") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { onPairAction(setupState.port, code) },
                                    enabled = code.length == 6
                                ) {
                                    Text("Pair")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = onResetSetup) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }
                    is ResurrectionSetupState.Connecting -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Connecting and deploying...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is ResurrectionSetupState.Active -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Resurrection is Active",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF4CAF50)
                            )
                        }
                        Text(
                            text = "BokBok is now set to persist through reboots. If it stops working, you can reset and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onResetSetup,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Reset Setup")
                        }
                    }
                    is ResurrectionSetupState.Error -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Setup Failed",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        Text(
                            text = "Device rejected connection. You must authorize this app to use ADB.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Manual Auth Section
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "Manual Authorization (Permanent)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                "Run this command on a PC (with Wireless Debugging enabled) to trust this app forever:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            val pubKey = onGetPublicKey()
                            val command = "adb shell \"echo '$pubKey' >> /data/misc/adb/adb_keys\""
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(8.dp)
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(command))
                                    }
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "adb shell \"echo '${pubKey.take(20)}...' >> /data/misc/adb/adb_keys\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }

                        Button(
                            onClick = onStartPairing,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Try Again")
                        }
                    }
                }
                
                // Info block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        .padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Once paired, BokBok will use its own ADB bridge to keep optimizations active without needing a PC or manual Shizuku start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
