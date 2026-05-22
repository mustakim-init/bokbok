package com.mustakim.bokbok.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.adb.ResurrectionSetupState
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.viewmodel.AdbSetupViewModel
import com.mustakim.bokbok.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExperimentalScreen(
    navController: NavController,
    adbSetupViewModel: AdbSetupViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val setupState by adbSetupViewModel.setupState.collectAsState()
    val isPaired = setupState is ResurrectionSetupState.Active

    val watchdogEnabled by settingsViewModel.watchdogEnabled.collectAsState()
    val heartbeatEnabled by settingsViewModel.heartbeatEnabled.collectAsState()
    val overlayEnabled by settingsViewModel.overlayEnabled.collectAsState()
    val crashReportEnabled by settingsViewModel.crashReportEnabled.collectAsState()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { 
                    Text(
                        text = "Experimental Features",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    BokBokIconButton(onClick = { navController.navigateUp() }) {
                        Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ExperimentalPreferenceHeader(title = "ADB Resurrection (Shizuku)")

            if (isPaired) {
                ExperimentalPreferenceEntry(
                    title = "ADB Pairing Status",
                    subtitle = "Successfully paired and active",
                    icon = Icons.Default.CheckCircle,
                    onClick = {}
                )
            } else {
                ExperimentalPreferenceEntry(
                    title = "Setup ADB Pairing",
                    subtitle = "Pair your device to allow BokBok to restart its services",
                    icon = Icons.Default.DeveloperMode,
                    onClick = {
                        // Launch setup or navigate to setup screen if exists
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            ExperimentalPreferenceHeader(title = "Advanced Features")

            ExperimentalPreferenceSwitch(
                title = "Watchdog Service",
                subtitle = "Keep background processes alive",
                icon = Icons.Default.Visibility,
                isChecked = watchdogEnabled,
                onCheckedChange = { settingsViewModel.toggleWatchdog(it) }
            )

            ExperimentalPreferenceSwitch(
                title = "Heartbeat Monitor",
                subtitle = "Periodically check service health",
                icon = Icons.Default.Favorite,
                isChecked = heartbeatEnabled,
                onCheckedChange = { settingsViewModel.toggleHeartbeat(it) }
            )

            ExperimentalPreferenceSwitch(
                title = "Overlay Enable",
                subtitle = "Allow drawing over other apps for game boost features",
                icon = Icons.Default.Layers,
                isChecked = overlayEnabled,
                onCheckedChange = { settingsViewModel.toggleOverlay(it) }
            )

            ExperimentalPreferenceSwitch(
                title = "Crash Reporting",
                subtitle = "Automatically collect and send crash logs",
                icon = Icons.Default.BugReport,
                isChecked = crashReportEnabled,
                onCheckedChange = { settingsViewModel.toggleCrashReport(it) }
            )
        }
    }
}

@Composable
fun ExperimentalPreferenceHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun ExperimentalPreferenceEntry(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ExperimentalPreferenceSwitch(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange
        )
    }
}
