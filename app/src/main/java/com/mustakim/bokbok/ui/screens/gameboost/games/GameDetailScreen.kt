package com.mustakim.bokbok.ui.screens.gameboost.games

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mustakim.bokbok.data.model.GameItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    game: GameItem,
    onBack: () -> Unit,
    onLaunch: () -> Unit,
    onToggleLauncher: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Game Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    game.icon?.let {
                        androidx.compose.foundation.Image(
                            bitmap = it.toBitmap().asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(20.dp))
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = game.label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = game.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = onLaunch,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("BOOT & LAUNCH", fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Settings Sections
            Text(
                text = "General Settings",
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            TweakToggle(
                title = "Hide from Launcher",
                description = "Remove app icon from home screen",
                icon = Icons.Default.Settings,
                checked = game.isHiddenFromLauncher,
                onCheckedChange = { onToggleLauncher() }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Optimization Tweaks (Coming Soon)",
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 12.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )

            // Placeholders
            TweakPlaceholder(
                title = "Gaming DND",
                description = "Block notifications during gameplay",
                icon = Icons.Default.DoNotDisturb
            )
            TweakPlaceholder(
                title = "Smooth Animations",
                description = "Force 120Hz and tweak window scales",
                icon = Icons.Default.Animation
            )
            TweakPlaceholder(
                title = "Resolution Tweak",
                description = "Change DPI for better visibility",
                icon = Icons.Default.Monitor
            )
        }
    }
}

@Composable
fun TweakToggle(
    title: String,
    description: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}

@Composable
fun TweakPlaceholder(
    title: String,
    description: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.padding(vertical = 4.dp).alpha(0.6f),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
            Text("PRO", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}
