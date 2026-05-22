package com.mustakim.bokbok.ui.screens.gameboost.games

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.zIndex
import com.mustakim.bokbok.data.model.*
import com.mustakim.bokbok.ui.theme.GoogleSansFlex
import com.mustakim.bokbok.viewmodel.GameSpaceViewModel
import com.mustakim.bokbok.viewmodel.LaunchState
import com.mustakim.bokbok.utils.AppIcon
import org.json.JSONObject
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing

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
    
    val tweaksByCategory = remember { TweakCatalog.getFilteredTweaks().groupBy { it.category } }
    val launchState by viewModel.launchState.collectAsState()
    val shizukuActive by viewModel.shizukuActive.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = Color.Transparent,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(game.label, fontFamily = GoogleSansFlex, fontWeight = FontWeight.ExtraBold) },
                navigationIcon = {
                    BokBokIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent
                ),
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
        }
    ) { innerPadding ->
        // Capture M3 Expressive colors from theme
        val color1 = MaterialTheme.colorScheme.primary
        val color2 = MaterialTheme.colorScheme.secondary
        val color3 = MaterialTheme.colorScheme.tertiary
        val color4 = MaterialTheme.colorScheme.primaryContainer
        val color5 = MaterialTheme.colorScheme.secondaryContainer
        val surfaceColor = MaterialTheme.colorScheme.surface

        Box(modifier = Modifier.fillMaxSize()) {
            // M3E Mesh gradient background layer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f)
                    .align(Alignment.TopCenter)
                    .zIndex(-1f)
                    .drawWithCache {
                        val width = this.size.width
                        val height = this.size.height

                        val brush1 = Brush.radialGradient(
                            colors = listOf(
                                color1.copy(alpha = 0.38f),
                                color1.copy(alpha = 0.24f),
                                color1.copy(alpha = 0.14f),
                                color1.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.15f, height * 0.1f),
                            radius = width * 0.55f
                        )

                        val brush2 = Brush.radialGradient(
                            colors = listOf(
                                color2.copy(alpha = 0.34f),
                                color2.copy(alpha = 0.2f),
                                color2.copy(alpha = 0.11f),
                                color2.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.85f, height * 0.2f),
                            radius = width * 0.65f
                        )

                        val brush3 = Brush.radialGradient(
                            colors = listOf(
                                color3.copy(alpha = 0.3f),
                                color3.copy(alpha = 0.17f),
                                color3.copy(alpha = 0.09f),
                                color3.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.3f, height * 0.45f),
                            radius = width * 0.6f
                        )

                        val brush4 = Brush.radialGradient(
                            colors = listOf(
                                color4.copy(alpha = 0.26f),
                                color4.copy(alpha = 0.14f),
                                color4.copy(alpha = 0.08f),
                                color4.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.7f, height * 0.5f),
                            radius = width * 0.7f
                        )

                        val brush5 = Brush.radialGradient(
                            colors = listOf(
                                color5.copy(alpha = 0.22f),
                                color5.copy(alpha = 0.12f),
                                color5.copy(alpha = 0.06f),
                                color5.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.5f, height * 0.75f),
                            radius = width * 0.8f
                        )

                        val overlayBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                surfaceColor.copy(alpha = 0.22f),
                                surfaceColor.copy(alpha = 0.55f),
                                surfaceColor
                            ),
                            startY = height * 0.4f,
                            endY = height
                        )

                        onDrawBehind {
                            drawRect(brush = brush1)
                            drawRect(brush = brush2)
                            drawRect(brush = brush3)
                            drawRect(brush = brush4)
                            drawRect(brush = brush5)
                            drawRect(brush = overlayBrush)
                        }
                    }
            )
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
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
                            Text("Launch Game", fontWeight = FontWeight.ExtraBold, fontFamily = GoogleSansFlex)
                        }
                    }
                }
            }

            // Maintenance & Performance
            item {
                val isCompiling by viewModel.isCompiling.collectAsState()
                
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = "Maintenance & Performance",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).fillMaxWidth()
                    )
                    
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.secondaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Memory,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Pre-Optimize Engine (AOT)", fontWeight = FontWeight.Bold)
                                    Text(
                                        "Force-compile code for maximum execution speed. Run this once per game update.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            
                            Button(
                                onClick = { viewModel.preOptimizeGame(game.packageName) },
                                enabled = !isCompiling && shizukuActive,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondary,
                                    contentColor = MaterialTheme.colorScheme.onSecondary
                                )
                            ) {
                                if (isCompiling) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onSecondary
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text("OPTIMIZING...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Default.Build, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("RUN PRE-OPTIMIZATION", fontWeight = FontWeight.Bold)
                                }
                            }
                            
                            if (!shizukuActive) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Requires Shizuku for system-level optimization.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }


            // Stealth Mode (Moved Up)
            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = "Application Settings",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp).fillMaxWidth()
                    )
                    Box(modifier = Modifier.padding(vertical = 4.dp)) {
                        TweakToggle(
                            title = "Stealth Mode",
                            description = "Hide from launcher system-wide",
                            checked = game.isHiddenFromLauncher,
                            onCheckedChange = { viewModel.toggleLauncherVisibility(game) }
                        )
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
                            isShizukuActive = shizukuActive,
                            onValueChange = { viewModel.updateCustomTweak(game.packageName, tweak.id, it) }
                        )
                    }
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
                            LaunchState.OPTIMIZING -> "OPTIMIZING SYSTEM..."
                            LaunchState.LAUNCHING -> "STARTING GAME..."
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
                        text = "Prepare for impact",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
            }
        }
    }
}

@Composable
fun TweakControl(
    tweak: TweakDef,
    value: String,
    isShizukuActive: Boolean,
    onValueChange: (String) -> Unit
) {
    val isLocked = tweak.requiresAdb && !isShizukuActive

    Surface(
        modifier = Modifier.alpha(if (isLocked) 0.6f else 1f),
        shape = RoundedCornerShape(24.dp),
        color = if (isLocked) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surfaceContainerHigh,
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
                            "Memory & Processes" -> Icons.Default.Memory
                            else -> Icons.Default.Bolt
                        },
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            tweak.title, 
                            fontWeight = FontWeight.Bold, 
                            style = MaterialTheme.typography.bodyLarge,
                            fontFamily = GoogleSansFlex,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (tweak.requiresAdb) {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    "ADB", 
                                    fontSize = 10.sp, 
                                    fontWeight = FontWeight.Black, 
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                    Text(
                        tweak.description, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isLocked) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lock, 
                                null, 
                                modifier = Modifier.size(14.dp), 
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "Shizuku not running or permission missing",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else if (tweak.warning != null) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.PriorityHigh, 
                                null, 
                                modifier = Modifier.size(14.dp), 
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = tweak.warning,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (tweak.type == TweakType.TOGGLE) {
                    Switch(
                        enabled = !isLocked,
                        checked = value == "true",
                        onCheckedChange = { onValueChange(it.toString()) }
                    )
                }
            }

            if (tweak.type == TweakType.SELECT && tweak.options != null) {
                Spacer(Modifier.height(12.dp))
                // Use a scrollable Row to prevent button truncation on small screens
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tweak.options.forEach { option ->
                        val isSelected = value == option
                        Surface(
                            modifier = Modifier
                                .widthIn(min = 60.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = !isLocked) { onValueChange(option) },
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    option, 
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = GoogleSansFlex,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            if (tweak.type == TweakType.INPUT) {
                Spacer(Modifier.height(12.dp))
                
                // --- FIXED: Local state management for smooth typing ---
                var text by remember(value) { mutableStateOf(value) }
                val scope = rememberCoroutineScope()
                var debounceJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

                OutlinedTextField(
                    enabled = !isLocked,
                    value = text,
                    onValueChange = { newText ->
                        text = newText
                        // Debounce updates to avoid rapid DB writes and jitter
                        debounceJob?.cancel()
                        debounceJob = scope.launch {
                            delay(500)
                            onValueChange(newText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter value...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = if (tweak.id.contains("wm_")) 
                            androidx.compose.ui.text.input.KeyboardType.Number // Use Number for Size/Density
                        else 
                            androidx.compose.ui.text.input.KeyboardType.Text
                    )
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