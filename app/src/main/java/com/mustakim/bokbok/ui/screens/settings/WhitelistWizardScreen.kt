package com.mustakim.bokbok.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhitelistWizardScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Persistence Wizard") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    "Ensure BokBok stays active",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Aggressive battery savers can kill the game booster. Please follow these steps for your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                WizardStep(
                    number = 1,
                    title = "Lock in Recents",
                    description = "Open your Recent Apps (Multitasking), find BokBok, and tap the 'Lock' icon. This prevents the OS from clearing it when you tap 'Clear All'."
                )
            }

            item {
                WizardStep(
                    number = 2,
                    title = "Enable Auto-start",
                    description = "Go to App Info > Permissions > Auto-start (or Background execution) and toggle it ON. This allows the booster to start after a reboot."
                )
            }

            item {
                WizardStep(
                    number = 3,
                    title = "Disable Battery Optimization",
                    description = "Go to App Info > Battery > Unrestricted. This ensures the watchdog service isn't throttled during gaming."
                )
            }

            item {
                FilledTonalButton(
                    onClick = { /* Could open system settings here */ },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open System Settings")
                }
            }
        }
    }
}

@Composable
fun WizardStep(number: Int, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
