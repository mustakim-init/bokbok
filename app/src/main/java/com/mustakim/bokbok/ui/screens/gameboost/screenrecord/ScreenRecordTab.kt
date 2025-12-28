package com.mustakim.bokbok.ui.screens.gameboost.screenrecord

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mustakim.bokbok.viewmodel.ScreenRecordViewModel
import com.mustakim.bokbok.ui.screens.gameboost.screenrecord.PermissionGate
import com.mustakim.bokbok.model.RecordConfig
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenRecordTab(
    viewModel: ScreenRecordViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val isCountingDown by viewModel.isCountingDown.collectAsState()
    val countdownValue by viewModel.countdownValue.collectAsState()
    val config by viewModel.config.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Show error messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }
    
    // Refresh permissions when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startRecording(result.resultCode, result.data!!)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent
        ) { paddingValues ->
            // Check permissions first
            if (!permissionsGranted) {
                PermissionGate(
                    onAllPermissionsGranted = {
                        viewModel.refreshPermissions()
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 16.dp)
                ) {
                    item {
                        Text(
                            text = "Engine State",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Recording Status Card
                    item {
                        RecordingStatusCard(
                            isRecording = isRecording,
                            isPaused = isPaused,
                            config = config,
                            onStartStop = {
                                if (isRecording) {
                                    viewModel.stopRecording()
                                } else {
                                    val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                                    projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                                }
                            },
                            onPauseResume = {
                                if (isPaused) viewModel.resumeRecording() else viewModel.pauseRecording()
                            }
                        )
                    }

                    item {
                        Text(
                            text = "Advanced Configuration",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Settings Group
                    item {
                        SettingsGroup {
                            SettingPicker(
                                icon = Icons.Default.VideoSettings,
                                title = "Resolution",
                                value = config.resolutionName,
                                options = listOf("480p", "720p", "1080p", "2K", "4K"),
                                onSelected = { name ->
                                    val (w, h) = calculateResolution(context, name)
                                    viewModel.updateConfig(config.copy(width = w, height = h, resolutionName = name))
                                }
                            )
                            
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            SettingPicker(
                                icon = Icons.Default.Speed,
                                title = "Bitrate",
                                value = config.bitrateName,
                                options = listOf("4 Mbps", "8 Mbps", "12 Mbps", "20 Mbps", "50 Mbps"),
                                onSelected = { name ->
                                    val bps = name.replace(" Mbps", "").toInt() * 1_000_000
                                    viewModel.updateConfig(config.copy(bitrate = bps, bitrateName = name))
                                }
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            SettingPicker(
                                icon = Icons.Default.Timer,
                                title = "Frame Rate",
                                value = "${config.fps} FPS",
                                options = listOf("30 FPS", "60 FPS", "90 FPS", "120 FPS"),
                                onSelected = { name ->
                                    val fps = name.replace(" FPS", "").toInt()
                                    viewModel.updateConfig(config.copy(fps = fps))
                                }
                            )
                        }
                    }

                    item {
                        SettingsGroup {
                            ToggleSetting(
                                icon = Icons.Default.Mic,
                                title = "Include Microphone",
                                checked = config.includeMic,
                                onCheckedChange = { viewModel.updateConfig(config.copy(includeMic = it)) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ToggleSetting(
                                icon = Icons.Default.Audiotrack,
                                title = "Internal Audio",
                                checked = config.includeInternal,
                                onCheckedChange = { viewModel.updateConfig(config.copy(includeInternal = it)) }
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ToggleSetting(
                                icon = Icons.Default.AvTimer,
                                title = "Start Countdown",
                                checked = config.useCountdown,
                                onCheckedChange = { viewModel.updateConfig(config.copy(useCountdown = it)) }
                            )
                        }
                    }

                    // Audio Mix Ratio Slider (only visible when both audio sources enabled)
                    if (config.includeMic && config.includeInternal) {
                        item {
                            Text(
                                text = "Audio Mix Balance",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item {
                            SettingsGroup {
                                AudioMixRatioSlider(
                                    internalRatio = config.internalAudioRatio,
                                    micRatio = config.micAudioRatio,
                                    onRatioChange = { internal, mic ->
                                        viewModel.updateConfig(config.copy(
                                            internalAudioRatio = internal,
                                            micAudioRatio = mic
                                        ))
                                    }
                                )
                            }
                        }
                    }

                    item {
                        SettingsGroup {
                            ToggleSetting(
                                icon = Icons.Default.HighQuality,
                                title = "HEVC Encoding",
                                description = "Higher quality, smaller file size",
                                checked = config.useHevc,
                                onCheckedChange = { viewModel.updateConfig(config.copy(useHevc = it)) }
                            )
                        }
                    }
                }
            }
        }

        // Countdown Overlay
        AnimatedVisibility(
            visible = isCountingDown,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(160.dp),
                    tonalElevation = 12.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = countdownValue.toString(),
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordingStatusCard(
    isRecording: Boolean,
    isPaused: Boolean,
    config: RecordConfig,
    onStartStop: () -> Unit,
    onPauseResume: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = animateColorAsState(
            targetValue = when {
                isPaused -> MaterialTheme.colorScheme.tertiaryContainer
                isRecording -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ).value,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) Color.Red else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (isRecording) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isPaused -> "Recording Paused"
                            isRecording -> "Capturing Screen"
                            else -> "Engine: High Performance"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isRecording) "${config.resolutionName} • ${config.fps} FPS" 
                               else "Tap start to begin capturing",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Button(
                    onClick = onStartStop,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(if (isRecording) "Stop" else "Start")
                }
            }

            AnimatedVisibility(visible = isRecording) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onPauseResume,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingPicker(
    icon: ImageVector,
    title: String,
    value: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ToggleSetting(
    icon: ImageVector,
    title: String,
    description: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Calculates correct resolution while preserving the device's actual aspect ratio.
 */
private fun calculateResolution(context: Context, preset: String): Pair<Int, Int> {
    val metrics = context.resources.displayMetrics
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels
    
    // Most phones are portrait (height > width)
    val isPortrait = screenHeight > screenWidth
    val aspectRatio = if (isPortrait) {
        screenWidth.toFloat() / screenHeight.toFloat()
    } else {
        screenHeight.toFloat() / screenWidth.toFloat()
    }

    val targetHeight = when (preset) {
        "480p" -> 854
        "720p" -> 1280
        "1080p" -> 1920
        "2K" -> 2560
        "4K" -> 3840
        else -> 1920
    }

    // Adjust width based on actual aspect ratio
    var calculatedWidth = (targetHeight * aspectRatio).toInt()
    
    // Ensure width is even for video encoding compatibility
    if (calculatedWidth % 2 != 0) calculatedWidth++
    
    return calculatedWidth to targetHeight
}

/**
 * Audio Volume Sliders for independent control of internal (game) audio
 * and microphone (voice) audio levels. Each slider goes from 0% to 100%.
 * - 0% = No audio from this source in the final mix
 * - 100% = Full volume from this source in the final mix
 */
@Composable
fun AudioMixRatioSlider(
    internalRatio: Float,
    micRatio: Float,
    onRatioChange: (internal: Float, mic: Float) -> Unit
) {
    var internalVolume by remember(internalRatio) { mutableStateOf(internalRatio) }
    var micVolume by remember(micRatio) { mutableStateOf(micRatio) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Internal Audio (Game) Slider
        AudioVolumeSlider(
            icon = Icons.Default.Audiotrack,
            title = "Game Audio",
            description = "Internal sound volume",
            volume = internalVolume,
            onVolumeChange = { newVolume ->
                internalVolume = newVolume
                onRatioChange(internalVolume, micVolume)
            }
        )
        
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        
        // Microphone (Voice) Slider
        AudioVolumeSlider(
            icon = Icons.Default.Mic,
            title = "Voice Audio",
            description = "Microphone volume",
            volume = micVolume,
            onVolumeChange = { newVolume ->
                micVolume = newVolume
                onRatioChange(internalVolume, micVolume)
            }
        )
    }
}

/**
 * Individual audio volume slider component
 */
@Composable
private fun AudioVolumeSlider(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Volume percentage badge
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Slider(
            value = volume,
            onValueChange = onVolumeChange,
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

