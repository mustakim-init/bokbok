package com.mustakim.bokbok.ui.screens.gameboost.games

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.mustakim.bokbok.data.model.*
import com.mustakim.bokbok.ui.theme.GoogleSansFlex
import com.mustakim.bokbok.viewmodel.GameSpaceViewModel
import com.mustakim.bokbok.viewmodel.LaunchState
import com.mustakim.bokbok.utils.AppIcon
import org.json.JSONObject
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    game: GameItem,
    viewModel: GameSpaceViewModel,
    onBack: () -> Unit
) {
    val customSettings = remember(game.customSettingsJson) { 
        try { JSONObject(game.customSettingsJson) } catch(_: Exception) { JSONObject() }
    }
    
    var infoTweak by remember { mutableStateOf<TweakDef?>(null) }
    val tweaksByCategory = remember { TweakCatalog.getFilteredTweaks().groupBy { it.category } }
    val launchState by viewModel.launchState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Power House", fontFamily = GoogleSansFlex, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Immersive Header (Simplified to avoid overlap)
            item {
                Card(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            modifier = Modifier.size(80.dp),
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 2.dp
                        ) {
                            AsyncImage(
                                model = AppIcon(game.packageName),
                                contentDescription = null,
                                modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = game.label,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            fontFamily = GoogleSansFlex
                        )
                        Text(
                            text = game.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(Modifier.height(24.dp))
                        
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(if (isPressed) 0.95f else 1f, label = "")

                        Button(
                            onClick = { viewModel.launchGame(game) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .scale(scale),
                            shape = RoundedCornerShape(16.dp),
                            interactionSource = interactionSource
                        ) {
                            Icon(Icons.Default.RocketLaunch, null)
                            Spacer(Modifier.width(12.dp))
                            Text("IGNITE ENGINE", fontWeight = FontWeight.ExtraBold, fontFamily = GoogleSansFlex)
                        }
                    }
                }
            }

            // Profile Selector
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text(
                        text = "Optimization Profile",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OptimizationProfile.entries.forEach { profile ->
                                val isSelected = game.optimizationProfile == profile
                                val bgColor by animateColorAsState(
                                    if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    label = ""
                                )
                                val contentColor by animateColorAsState(
                                    if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    label = ""
                                )

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(bgColor)
                                        .clickable { viewModel.updateGameProfile(game.packageName, profile) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = profile.name.take(3).uppercase(), 
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = contentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Dynamic Tweaks Categories
            tweaksByCategory.forEach { (category, tweaks) ->
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 4.dp).fillMaxWidth()
                    )
                }
                
                items(
                    count = tweaks.size,
                    key = { tweaks[it].id }
                ) { index ->
                    val tweak = tweaks[index]
                    Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                        val value = customSettings.optString(tweak.id, if(tweak.type == TweakType.TOGGLE) "false" else "")
                        TweakControl(
                            tweak = tweak,
                            value = value,
                            onValueChange = { viewModel.updateCustomTweak(game.packageName, tweak.id, it) },
                            onInfoClick = { infoTweak = tweak }
                        )
                    }
                }
            }
            
            // App Settings
            item {
                Text(
                    text = "Application Settings",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 32.dp, top = 12.dp, bottom = 4.dp).fillMaxWidth()
                )
            }
            item {
                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)) {
                    TweakToggle(
                        title = "Stealth Mode",
                        description = "Hide from launcher system-wide",
                        checked = game.isHiddenFromLauncher,
                        onCheckedChange = { viewModel.toggleLauncherVisibility(game) }
                    )
                }
            }
        }

        // Launch Progress Overlay
        AnimatedVisibility(
            visible = launchState != LaunchState.NONE,
            enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
            exit = fadeOut() + shrinkOut(shrinkTowards = Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .clickable(enabled = false) {}, // Block interaction
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        strokeWidth = 6.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Text(
                        text = when(launchState) {
                            LaunchState.OPTIMIZING -> "RECALIBRATING SYSTEM..."
                            LaunchState.COMPILING -> "AOT ENGINE COMPILING..."
                            LaunchState.LAUNCHING -> "IGNITING ENGINE..."
                            else -> ""
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontFamily = GoogleSansFlex,
                        letterSpacing = 2.sp
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = when(launchState) {
                            LaunchState.COMPILING -> "This may take a minute for large games"
                            else -> "Prepare for impact"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    if (infoTweak != null) {
        TweakInfoDialog(tweak = infoTweak!!, onDismiss = { infoTweak = null })
    }
}

@Composable
fun TweakControl(
    tweak: TweakDef,
    value: String,
    onValueChange: (String) -> Unit,
    onInfoClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when(tweak.category) {
                            "Display & Animation" -> Icons.Default.Animation
                            "GPU & Graphics" -> Icons.Default.Cyclone
                            "AOT Compilation" -> Icons.Default.Memory
                            else -> Icons.Default.Bolt
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tweak.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        if (tweak.requiresAdb) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Text(
                                    "ADB", 
                                    fontSize = 9.sp, 
                                    fontWeight = FontWeight.Black, 
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    Text(
                        tweak.description, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tweak.warning != null) {
                        Icon(
                            Icons.Default.Warning, 
                            null, 
                            modifier = Modifier.size(18.dp), 
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = onInfoClick) {
                        Icon(Icons.Outlined.Info, null, modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }

                if (tweak.type == TweakType.TOGGLE) {
                    Switch(
                        checked = value == "true",
                        onCheckedChange = { onValueChange(it.toString()) }
                    )
                }
            }

            if (tweak.type == TweakType.SELECT && tweak.options != null) {
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tweak.options.forEach { option ->
                        val isSelected = value == option
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onValueChange(option) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    option, 
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (tweak.type == TweakType.INPUT) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter value...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}

@Composable
fun TweakInfoDialog(tweak: TweakDef, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tweak.title, fontFamily = GoogleSansFlex, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(tweak.description)
                
                tweak.warning?.let { warning ->
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (tweak.requiresAdb) {
                    Spacer(Modifier.height(12.dp))
                    Text("⚠️ Requires Shizuku permission.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = { 
            TextButton(onClick = onDismiss) { Text("OK") } 
        }
    )
}

@Composable
fun TweakToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
