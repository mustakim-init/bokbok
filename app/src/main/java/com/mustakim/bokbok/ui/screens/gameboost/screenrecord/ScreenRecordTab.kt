package com.mustakim.bokbok.ui.screens.gameboost.screenrecord

import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AppShortcut
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.AvTimer
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EdgesensorHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FilePresent
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.HdrOff
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TimerOff
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideoSettings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.model.RecordConfig
import com.mustakim.bokbok.viewmodel.ScreenRecordViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenRecordTab(
    navController: NavHostController,
    viewModel: ScreenRecordViewModel = hiltViewModel()
) {
    // TODO: Add multi-language support in the future (Phase 7 roadmap)
    // Use stringResource(R.string.screen_record_title) etc.
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val isCountingDown by viewModel.isCountingDown.collectAsState()
    val countdownValue by viewModel.countdownValue.collectAsState()
    val config by viewModel.config.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val permissionsGranted by viewModel.permissionsGranted.collectAsState()
    
    val allRecordings by viewModel.allRecordings.collectAsState(initial = emptyList())
    val recorderSettings by viewModel.recorderSettings.collectAsState(initial = emptyMap())
    val installedApps by viewModel.installedApps.collectAsState()
    val processingProgress by viewModel.processingProgress.collectAsState()
    val modelState by viewModel.modelState.collectAsState()
    val modelDownloadProgress by viewModel.modelDownloadProgress.collectAsState()
    val wifiIpAddress by viewModel.wifiIpAddress.collectAsState()
    val wifiPin by viewModel.wifiPin.collectAsState()
    
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Recordings", "Settings")
    
    val snackbarHostState = remember { SnackbarHostState() }

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
        PermissionGate(
            onAllPermissionsGranted = {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                        PrimaryTabRow(
                            selectedTabIndex = selectedTabIndex,
                            containerColor = Color.Transparent,
                            divider = {},
                            indicator = {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(selectedTabIndex),
                                    width = 64.dp,
                                    shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                                )
                            }
                        ) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = selectedTabIndex == index,
                                    onClick = { selectedTabIndex = index },
                                    text = { 
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Crossfade(
                            targetState = selectedTabIndex,
                            animationSpec = tween(300),
                            modifier = Modifier.weight(1f)
                        ) { targetIndex ->
                            when (targetIndex) {
                                0 -> {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 20.dp),
                                        verticalArrangement = Arrangement.spacedBy(20.dp),
                                        contentPadding = PaddingValues(bottom = 32.dp, top = 20.dp)
                                    ) {
                                        // Recording Status Controller
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

                                        // FULL RECORDING HISTORY
                                        item {
                                            RecordingsHistorySection(
                                                recordings = allRecordings,
                                                processingProgress = processingProgress,
                                                wifiIpAddress = wifiIpAddress,
                                                wifiPin = wifiPin,
                                                onPlay = { path ->
                                                    navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.VideoPlayer.createRoute(path))
                                                },
                                                onProcess = { viewModel.processRecording(it) },
                                                onDelete = { viewModel.deleteRecording(it) },
                                                onToggleWifiShare = { viewModel.toggleWifiShare() }
                                            )
                                        }
                                    }
                                }
                1 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(bottom = 32.dp, top = 20.dp)
                    ) {
                        item {
                            Text(
                                text = "Advanced Configuration",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        item { OverlaySettingsGroup(config, viewModel) }


                        item {
                            val customProfiles by viewModel.customProfiles.collectAsState(initial = emptyList())
                            val selectedProfileName by viewModel.selectedProfileName.collectAsState()
                            var showSaveDialog by remember { mutableStateOf(false) }
                            var showManageDialog by remember { mutableStateOf(false) }

                            val profileOptions = remember(customProfiles) {
                                listOf("Default") + customProfiles.map { it.name } + listOf("Add New Profile...", "Manage Profiles...")
                            }

                            SettingsGroup(title = "Recording Profile", icon = Icons.Default.DashboardCustomize) {
                                SettingPicker(
                                    title = "Active Profile",
                                    icon = Icons.Default.AutoAwesome,
                                    value = selectedProfileName ?: "Default",
                                    options = profileOptions,
                                    onSelected = { selected ->
                                        when (selected) {
                                            "Default" -> viewModel.updateConfig(config.copy(profile = com.mustakim.bokbok.model.RecordingProfile.DEFAULT), preserveProfileName = "Default")
                                            "Add New Profile..." -> showSaveDialog = true
                                            "Manage Profiles..." -> showManageDialog = true
                                            else -> {
                                                customProfiles.find { it.name == selected }?.let {
                                                    viewModel.loadCustomProfile(it)
                                                }
                                            }
                                        }
                                    }
                                )
                            }

                            // Save Profile Dialog
                            if (showSaveDialog) {
                                var profileName by remember { mutableStateOf("") }
                                AlertDialog(
                                    onDismissRequest = { showSaveDialog = false },
                                    title = { Text("Save Profile") },
                                    text = {
                                        Column {
                                            Text("Enter a name for this profile:", style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            OutlinedTextField(
                                                value = profileName,
                                                onValueChange = { profileName = it.take(20) },
                                                label = { Text("Profile Name") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(
                                            onClick = {
                                                if (profileName.isNotBlank()) {
                                                    viewModel.saveCurrentAsProfile(profileName.trim())
                                                    showSaveDialog = false
                                                }
                                            },
                                            enabled = profileName.isNotBlank()
                                        ) {
                                            Text("Save")
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showSaveDialog = false }) {
                                            Text("Cancel")
                                        }
                                    }
                                )
                            }

                            // Manage Profiles Dialog
                            if (showManageDialog) {
                                AlertDialog(
                                    onDismissRequest = { showManageDialog = false },
                                    title = { Text("Manage Profiles") },
                                    text = {
                                        if (customProfiles.isEmpty()) {
                                            Text("No custom profiles available.", style = MaterialTheme.typography.bodyMedium)
                                        } else {
                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                customProfiles.forEach { profile ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 4.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                                                        IconButton(onClick = { viewModel.deleteCustomProfile(profile.name) }) {
                                                            Icon(
                                                                Icons.Default.Delete,
                                                                contentDescription = "Delete ${profile.name}",
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showManageDialog = false }) {
                                            Text("Done")
                                        }
                                    }
                                )
                            }
                        }

                        // Capture Configuration
                        item {
                            // Get encoder capabilities for dynamic options
                            val mimeType = if (config.useHevc) "video/hevc" else "video/avc"
                            val supportedResolutions = remember(mimeType) { 
                                com.mustakim.bokbok.util.EncoderCapabilities.getSupportedResolutions(mimeType).ifEmpty { 
                                    listOf("480p", "720p", "1080p") // Fallback
                                }
                            }
                            val supportedFps = remember(mimeType, config.width, config.height) {
                                com.mustakim.bokbok.util.EncoderCapabilities.getSupportedFpsList(mimeType, config.width, config.height).map { "$it FPS" }.ifEmpty {
                                    listOf("30 FPS", "60 FPS")
                                }
                            }
                            val isHevcSupported = remember { com.mustakim.bokbok.util.EncoderCapabilities.isHevcSupported() }
                            
                            SettingsGroup(title = "Capture Settings", icon = Icons.Default.Settings) {
                                SettingPicker(
                                    icon = Icons.Default.VideoSettings,
                                    title = "Resolution",
                                    value = config.resolutionName,
                                    options = supportedResolutions,
                                    onSelected = { name ->
                                        val (w, h) = calculateResolution(context, name, config.orientationLock)
                                        viewModel.updateConfig(config.copy(width = w, height = h, resolutionName = name))
                                    }
                                )
                                
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                SettingPicker(
                                    icon = Icons.Default.ScreenRotation,
                                    title = "Record Orientation",
                                    value = config.orientationLock,
                                    options = listOf("Auto", "Portrait", "Landscape"),
                                    onSelected = { orientation ->
                                        val (w, h) = calculateResolution(context, config.resolutionName, orientation)
                                        viewModel.updateConfig(config.copy(width = w, height = h, orientationLock = orientation))
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
                                    options = supportedFps,
                                    onSelected = { name ->
                                        val fps = name.replace(" FPS", "").toInt()
                                        viewModel.updateConfig(config.copy(fps = fps))
                                    }
                                )
                                
                                // HEVC Toggle (only if supported)
                                if (isHevcSupported) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                    ToggleSetting(
                                        icon = Icons.Default.HighQuality,
                                        title = "Use HEVC (H.265)",
                                        subtitle = "Better quality at lower bitrate",
                                        checked = config.useHevc,
                                        onCheckedChange = { viewModel.updateConfig(config.copy(useHevc = it)) }
                                    )
                                }
                            }
                        }

                        item {
                            SettingsGroup(title = "Features", icon = Icons.Default.Extension) {
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
                                    title = "Countdown Timer",
                                    subtitle = "3-second delay before starting",
                                    checked = config.useCountdown,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(useCountdown = it)) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    icon = Icons.Default.Videocam,
                                    title = "Facecam Overlay",
                                    checked = config.showFacecam,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(showFacecam = it)) }
                                )
                                if (config.showFacecam) {
                                    SettingPicker(
                                        icon = Icons.Default.Circle,
                                        title = "Facecam Shape",
                                        value = config.facecamShape,
                                        options = listOf("Circle", "Square"),
                                        onSelected = { viewModel.updateConfig(config.copy(facecamShape = it)) }
                                    )
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    icon = Icons.Default.AudioFile,
                                    title = "Export Mic-Only Track",
                                    checked = config.exportMicOnly,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(exportMicOnly = it)) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    icon = Icons.Default.AudioFile,
                                    title = "Export Internal-Only Track",
                                    checked = config.exportInternalOnly,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(exportInternalOnly = it)) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    icon = Icons.Default.Lock,
                                    title = "Require Password for Share",
                                    subtitle = "Secure Wi-Fi sharing with PIN",
                                    checked = recorderSettings["wifiShareRequirePassword"] as? Boolean ?: false,
                                    onCheckedChange = { viewModel.toggleWifiSharePasswordRequired() }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    icon = Icons.Default.SettingsVoice,
                                    title = "Mono Audio",
                                    subtitle = "Capture single channel audio",
                                    checked = config.isMono,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(isMono = it)) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                SettingPicker(
                                    icon = Icons.Default.FilePresent,
                                    title = "Output Format",
                                    value = config.outputFormat.uppercase(),
                                    options = listOf("MP4", "MKV (Not Supported)"),
                                    onSelected = { viewModel.updateConfig(config.copy(outputFormat = it.lowercase())) }
                                )
                            }
                        }

                        item {
                            SettingsGroup(title = "Auto-Stop Options", icon = Icons.Default.TimerOff) {
                                SettingPicker(
                                    title = "Time Limit",
                                    icon = Icons.Default.HourglassBottom,
                                    value = if (config.autoStopDurationMinutes == 0) "Disabled" else "${config.autoStopDurationMinutes} min",
                                    options = listOf("Disabled", "5 min", "10 min", "30 min", "1 hour"),
                                    onSelected = { value ->
                                        val mins = when (value) {
                                            "5 min" -> 5
                                            "10 min" -> 10
                                            "30 min" -> 30
                                            "1 hour" -> 60
                                            else -> 0
                                        }
                                        viewModel.updateConfig(config.copy(autoStopDurationMinutes = mins))
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                SettingPicker(
                                    title = "Battery Limit",
                                    icon = Icons.Default.BatteryChargingFull,
                                    value = if (config.autoStopBatteryLevel == 0) "Disabled" else "${config.autoStopBatteryLevel}%",
                                    options = listOf("Disabled", "10%", "15%", "20%", "30%"),
                                    onSelected = { value ->
                                        val pct = value.replace("%", "").toIntOrNull() ?: 0
                                        viewModel.updateConfig(config.copy(autoStopBatteryLevel = pct))
                                    }
                                )
                            }
                        }

                        item {
                            SettingsGroup(title = "Automation & Triggers", icon = Icons.Default.Vibration) {
                                ToggleSetting(
                                    title = "Stop on Screen Off",
                                    subtitle = "Recording stops when you lock the phone",
                                    checked = config.stopOnScreenOff,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(stopOnScreenOff = it)) },
                                    icon = Icons.Default.HdrOff
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    title = "Stop on Shake",
                                    subtitle = "Shake your phone to stop recording",
                                    checked = config.stopOnShake,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(stopOnShake = it)) },
                                    icon = Icons.Default.EdgesensorHigh
                                )
                                if (config.stopOnShake) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text("Shake Sensitivity: ${config.shakeSensitivity.toInt()}", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = config.shakeSensitivity,
                                            onValueChange = { viewModel.updateConfig(config.copy(shakeSensitivity = it)) },
                                            valueRange = 10f..40f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                var showAppPicker by remember { mutableStateOf(false) }
                                val installedApps by viewModel.installedApps.collectAsState()
                                
                                SettingPicker(
                                    title = "Auto-Launch App",
                                    icon = Icons.Default.RocketLaunch,
                                    value = if (config.autoLaunchPackage.isEmpty()) "Disabled" 
                                            else try {
                                                context.packageManager.getApplicationLabel(
                                                    context.packageManager.getApplicationInfo(config.autoLaunchPackage, 0)
                                                ).toString()
                                            } catch (_: Exception) { "Unknown App" },
                                    options = listOf("Select App...", "Disable"),
                                    onSelected = { 
                                        if (it == "Disable") viewModel.updateConfig(config.copy(autoLaunchPackage = ""))
                                        else showAppPicker = true
                                    }
                                )

                                if (showAppPicker) {
                                    AppPickerDialog(
                                        apps = installedApps,
                                        selectedPackages = if (config.autoLaunchPackage.isEmpty()) emptyList() else listOf(config.autoLaunchPackage),
                                        onAppSelected = { pkg ->
                                            viewModel.updateConfig(config.copy(autoLaunchPackage = pkg))
                                            showAppPicker = false
                                        },
                                        onDismiss = { showAppPicker = false }
                                    )
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                ToggleSetting(
                                    title = "Set Volume on Start",
                                    subtitle = "Auto-adjust music volume when starting",
                                    checked = config.setVolumeOnStart,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(setVolumeOnStart = it)) },
                                    icon = Icons.Default.VolumeUp
                                )
                                if (config.setVolumeOnStart) {
                                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                        Text("Start Volume: ${config.startVolumeLevel}%", style = MaterialTheme.typography.bodySmall)
                                        Slider(
                                            value = config.startVolumeLevel.toFloat(),
                                            onValueChange = { viewModel.updateConfig(config.copy(startVolumeLevel = it.toInt())) },
                                            valueRange = 0f..100f,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                
                                ToggleSetting(
                                    title = "Show Touches",
                                    subtitle = "Blink dot on screen touches (Dev Mode)",
                                    checked = config.showTouches,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(showTouches = it)) },
                                    icon = Icons.Default.TouchApp
                                )
                            }
                        }

                        // Watermark Options
                        item {
                            SettingsGroup(title = "Watermark Options", icon = Icons.Default.Palette) {
                                ToggleSetting(
                                    title = "Text Watermark",
                                    subtitle = "Show personalized text during recording",
                                    checked = config.useWatermarkText,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(useWatermarkText = it)) },
                                    icon = Icons.Default.TextFields
                                )
                                if (config.useWatermarkText) {
                                    OutlinedTextField(
                                        value = config.watermarkText,
                                        onValueChange = { viewModel.updateConfig(config.copy(watermarkText = it)) },
                                        label = { Text("Watermark Text") },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        singleLine = true
                                    )
                                }
                                
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                ToggleSetting(
                                    title = "Image Watermark",
                                    subtitle = "Show an image overlay during recording",
                                    checked = config.useWatermarkImage,
                                    onCheckedChange = { viewModel.updateConfig(config.copy(useWatermarkImage = it)) },
                                    icon = Icons.Default.Image
                                )
                                if (config.useWatermarkImage) {
                                    OutlinedTextField(
                                        value = config.watermarkImagePath,
                                        onValueChange = { viewModel.updateConfig(config.copy(watermarkImagePath = it)) },
                                        label = { Text("Image Path") },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        singleLine = true,
                                        placeholder = { Text("/sdcard/.../logo.png") },
                                        trailingIcon = {
                                            IconButton(onClick = { /* TODO: File Picker */ }) {
                                                Icon(Icons.Default.FolderOpen, null)
                                            }
                                        }
                                    )
                                }
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
                            Text(
                                text = "Processing Engine",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // ENGINE SETTINGS
                        item {
                            SettingsGroup(title = "Audio Engine", icon = Icons.Default.Memory) {
                                ToggleSetting(
                                    icon = Icons.Default.AutoMode,
                                    title = "Auto-Process",
                                    subtitle = "Process audio immediately after recording",
                                    checked = recorderSettings["autoProcess"] as? Boolean ?: true,
                                    onCheckedChange = { 
                                        viewModel.updateRecorderSettings(
                                            autoProcess = it,
                                            noiseReduction = recorderSettings["noiseReduction"] as? Boolean ?: true,
                                            bleedReduction = recorderSettings["bleedReduction"] as? Boolean ?: true,
                                            qualityMode = recorderSettings["qualityMode"] as? Int ?: 1,
                                            studioMaster = recorderSettings["studioMaster"] as? Boolean ?: true
                                        )
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    icon = Icons.Default.RecordVoiceOver,
                                    title = "Voice Isolation",
                                    subtitle = "Remove game bleed from mic (Software)",
                                    checked = recorderSettings["bleedReduction"] as? Boolean ?: true,
                                    onCheckedChange = { 
                                        viewModel.updateRecorderSettings(
                                            autoProcess = recorderSettings["autoProcess"] as? Boolean ?: true,
                                            noiseReduction = recorderSettings["noiseReduction"] as? Boolean ?: true,
                                            bleedReduction = it,
                                            qualityMode = recorderSettings["qualityMode"] as? Int ?: 1,
                                            studioMaster = recorderSettings["studioMaster"] as? Boolean ?: true
                                        )
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                // AI Section integrated into Engine
                                if (modelState == com.mustakim.bokbok.data.repository.ModelRepository.ModelState.READY) {
                                    ToggleSetting(
                                        icon = Icons.Default.Hearing,
                                        title = "AI Noise Reduction",
                                        subtitle = "Remove background noise (DeepFilterNet)",
                                        checked = recorderSettings["noiseReduction"] as? Boolean ?: true,
                                        onCheckedChange = { 
                                            viewModel.updateRecorderSettings(
                                                autoProcess = recorderSettings["autoProcess"] as? Boolean ?: true,
                                                noiseReduction = it,
                                                bleedReduction = recorderSettings["bleedReduction"] as? Boolean ?: true,
                                                qualityMode = recorderSettings["qualityMode"] as? Int ?: 1,
                                                studioMaster = recorderSettings["studioMaster"] as? Boolean ?: true
                                            )
                                        }
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.deleteModels() },
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Delete AI Models", style = MaterialTheme.typography.labelMedium)
                                    }
                                } else {
                                    // Compact Download Card (Integrated)
                                    Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text("Enable AI Enhancement", style = MaterialTheme.typography.titleSmall)
                                                }
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text("Download DeepFilterNet (~50MB) for neural noise suppression.", style = MaterialTheme.typography.bodySmall)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                
                                                if (modelState == com.mustakim.bokbok.data.repository.ModelRepository.ModelState.DOWNLOADING) {
                                                    LinearProgressIndicator(
                                                        progress = { modelDownloadProgress },
                                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                                    )
                                                    Text("Downloading... ${(modelDownloadProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                                                } else {
                                                    Button(
                                                        onClick = { viewModel.downloadModels() },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Download Models")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                SettingPicker(
                                    icon = Icons.Default.Hardware,
                                    title = "Efficiency Mode",
                                    value = if ((recorderSettings["qualityMode"] as? Int ?: 1) == 1) "Quality" else "Balanced",
                                    options = listOf("Balanced", "Quality"),
                                    onSelected = { name ->
                                        viewModel.updateRecorderSettings(
                                            autoProcess = recorderSettings["autoProcess"] as? Boolean ?: true,
                                            noiseReduction = recorderSettings["noiseReduction"] as? Boolean ?: true,
                                            bleedReduction = recorderSettings["bleedReduction"] as? Boolean ?: true,
                                            qualityMode = if (name == "Quality") 1 else 0,
                                            studioMaster = recorderSettings["studioMaster"] as? Boolean ?: true
                                        )
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                ToggleSetting(
                                    icon = Icons.Default.GraphicEq,
                                    title = "Studio Master",
                                    subtitle = "Professional EQ & Dynamics suite",
                                    checked = recorderSettings["studioMaster"] as? Boolean ?: false,
                                    onCheckedChange = { 
                                        viewModel.updateRecorderSettings(
                                            autoProcess = recorderSettings["autoProcess"] as? Boolean ?: true,
                                            noiseReduction = recorderSettings["noiseReduction"] as? Boolean ?: true,
                                            bleedReduction = recorderSettings["bleedReduction"] as? Boolean ?: true,
                                            qualityMode = recorderSettings["qualityMode"] as? Int ?: 1,
                                            studioMaster = it
                                        )
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                SettingPicker(
                                    icon = Icons.Default.GraphicEq,
                                    title = "Audio Sample Rate",
                                    value = "${config.audioSampleRate / 1000} kHz",
                                    options = listOf("44.1 kHz", "48 kHz"),
                                    onSelected = { 
                                        val rate = if (it == "48 kHz") 48000 else 44100
                                        viewModel.updateConfig(config.copy(audioSampleRate = rate))
                                    }
                                )
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                SettingPicker(
                                    icon = Icons.Default.SettingsVoice,
                                    title = "Audio Quality",
                                    value = "${config.audioBitrate / 1000} kbps",
                                    options = listOf("64 kbps", "128 kbps", "192 kbps", "256 kbps"),
                                    onSelected = { 
                                        val bps = it.replace(" kbps", "").toInt() * 1000
                                        viewModel.updateConfig(config.copy(audioBitrate = bps))
                                    }
                                )
                            }
                        }
                    }
                }
            }
                        }
                }
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
        )

        // Countdown Overlay handled by ScreenRecordService (Floating)
    }
}

/**
 * Custom modifier for Material 3 Expressive interactions.
 * Scales down slightly and increases corner radius on press.
 */
@Composable
fun Modifier.expressiveClickable(
    initialRadius: androidx.compose.ui.unit.Dp = 24.dp,
    pressedRadius: androidx.compose.ui.unit.Dp = 32.dp,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "scale"
    )
    
    val cornerRadius by animateDpAsState(
        targetValue = if (isPressed) pressedRadius else initialRadius,
        animationSpec = tween(durationMillis = 150),
        label = "radius"
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(RoundedCornerShape(cornerRadius))
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

@Composable
fun RecordingStatusCard(
    isRecording: Boolean,
    isPaused: Boolean,
    config: RecordConfig,
    onStartStop: () -> Unit,
    onPauseResume: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.98f else 1f, label = "cardScale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            // Main Control Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(
                            if (isRecording) MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.FiberManualRecord else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRecording) "Recording in Progress" else "Recorder: Ready",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isRecording) "${config.resolutionName} • ${config.fps} FPS" 
                               else "Configure settings below and start",
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
                    Text(
                        text = if (isRecording) "Stop" else "Start",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expanded Controls when Recording (Pause/Resume)
            AnimatedVisibility(
                visible = isRecording,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    FilledTonalButton(
                        onClick = onPauseResume,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, 
                            contentDescription = null, 
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (isPaused) "Resume Recording" else "Pause Recording",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SettingsGroup(
    title: String? = null,
    icon: ImageVector? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        if (title != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
        ) {
            Column(content = content)
        }
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
            .expressiveClickable(initialRadius = 0.dp, pressedRadius = 12.dp) { expanded = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        
        Box {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
            
            DropdownMenu(
                expanded = expanded, 
                onDismissRequest = { expanded = false },
                offset = androidx.compose.ui.unit.DpOffset(x = 0.dp, y = 4.dp)
            ) {
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
}

@Composable
fun ToggleSetting(
    title: String,
    subtitle: String? = null,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(initialRadius = 0.dp, pressedRadius = 12.dp) { onCheckedChange(!checked) }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun calculateResolution(context: Context, preset: String, orientationLock: String = "Auto"): Pair<Int, Int> {
    val metrics = context.resources.displayMetrics
    val screenWidth = metrics.widthPixels
    val screenHeight = metrics.heightPixels
    
    val isPortrait = when (orientationLock) {
        "Portrait" -> true
        "Landscape" -> false
        else -> screenHeight > screenWidth
    }
    
    val aspectRatio = if (screenHeight > screenWidth) {
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

    var calculatedWidth = (targetHeight * aspectRatio).toInt()
    if (calculatedWidth % 2 != 0) calculatedWidth++
    
    return if (isPortrait) {
        calculatedWidth to targetHeight
    } else {
        targetHeight to calculatedWidth
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerDialog(
    apps: List<android.content.pm.PackageInfo>,
    selectedPackages: List<String> = emptyList(),
    onAppSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val pm = LocalContext.current.packageManager
    var searchQuery by remember { mutableStateOf("") }
    val filteredApps = remember(searchQuery, apps) {
        apps.filter { it.applicationInfo?.loadLabel(pm).toString().contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select App Shortcuts") },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search Games/Apps") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(filteredApps) { app ->
                        val isSelected = selectedPackages.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .expressiveClickable(initialRadius = 12.dp, pressedRadius = 16.dp) { onAppSelected(app.packageName) }
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val info = try { app.applicationInfo } catch (_: Exception) { null }
                            val icon = info?.loadIcon(pm)
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.AppShortcut, 
                                    null, 
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, 
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(info?.loadLabel(pm)?.toString() ?: "Unknown", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

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
        
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        
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

@Composable
private fun AudioVolumeSlider(
    icon: ImageVector,
    title: String,
    description: String,
    volume: Float,
    onVolumeChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(value = volume, onValueChange = onVolumeChange, valueRange = 0f..1f, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun RecordingsHistorySection(
    recordings: List<com.mustakim.bokbok.data.local.entity.RecordingEntity>,
    processingProgress: Map<Long, Float>,
    wifiIpAddress: String? = null,
    wifiPin: String? = null,
    onPlay: (String) -> Unit,
    onProcess: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onToggleWifiShare: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recording History",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            FilledTonalButton(
                onClick = onToggleWifiShare,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.WifiTethering, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (wifiIpAddress != null) "Sharing On" else "Wi-Fi Share",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        
        if (wifiIpAddress != null) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Available on PC/Browser:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(wifiIpAddress, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        if (wifiPin != null) {
                            Text("PIN: $wifiPin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    IconButton(onClick = onToggleWifiShare) {
                        Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        if (recordings.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No recordings found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            recordings.forEach { recording ->
                RecordingHistoryCard(
                    recording = recording,
                    progress = processingProgress[recording.id],
                    onPlay = { onPlay(recording.videoPath) },
                    onProcess = { onProcess(recording.id) },
                    onDelete = { onDelete(recording.id) }
                )
            }
        }
    }
}

@Composable
fun RecordingHistoryCard(
    recording: com.mustakim.bokbok.data.local.entity.RecordingEntity,
    progress: Float? = null,
    onPlay: () -> Unit,
    onProcess: () -> Unit,
    onDelete: () -> Unit
) {
    val status = recording.status
    val isProcessed = status == com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSED
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(
                initialRadius = 24.dp,
                pressedRadius = 28.dp,
                onClick = if (isProcessed) onPlay else if (status != com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSING) onProcess else ({})
            ),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isProcessed) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) 
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isProcessed) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.secondaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when(status) {
                        com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED -> Icons.Default.Error
                        com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSING -> Icons.Default.Sync
                        com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSED -> Icons.Default.PlayCircle
                        else -> Icons.Default.Queue
                    },
                    contentDescription = null,
                    tint = when(status) {
                        com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED -> MaterialTheme.colorScheme.error
                        com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSED -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.secondary
                    },
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Recording ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(recording.id))}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = when(status) {
                            com.mustakim.bokbok.data.local.entity.RecordingStatus.PENDING -> "Ready to Process"
                            com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED -> "Processing Failed"
                            com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSING -> {
                                if (progress != null) "Processing: ${(progress * 100).toInt()}%" else "Processing..."
                            }
                            com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSED -> "Finished"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when(status) {
                            com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED -> MaterialTheme.colorScheme.error
                            com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSED -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    if (recording.durationMs > 0) {
                        Text(
                            text = " • ${com.mustakim.bokbok.utils.formatDuration(recording.durationMs)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            if (!isProcessed && status != com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSING) {
                Button(
                    onClick = onProcess,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Process", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f))
            ) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
            }
        }
    }
}
@Composable
fun OverlaySettingsGroup(config: RecordConfig, viewModel: ScreenRecordViewModel) {
    val installedApps by viewModel.installedApps.collectAsState()
    SettingsGroup(title = "Overlay Customization") {
        // Menu Style
        SettingPicker(
            title = "Menu Style",
            icon = Icons.Default.DashboardCustomize,
            value = if (config.menuStyle == 0) "Horizontal" else "Vertical",
            options = listOf("Horizontal", "Vertical"),
            onSelected = { 
                viewModel.updateConfig(config.copy(menuStyle = if (it == "Horizontal") 0 else 1))
            }
        )

        // Minimization
        ToggleSetting(
            title = "Start Minimized",
            icon = Icons.Default.Minimize,
            checked = config.startMinimized,
            onCheckedChange = { viewModel.updateConfig(config.copy(startMinimized = it)) }
        )

        SettingPicker(
            title = "Minimizing Side",
            icon = Icons.Default.AlignHorizontalLeft,
            value = if (config.minimizingSide == 0) "Right" else "Left",
            options = listOf("Right", "Left"),
            onSelected = { 
                viewModel.updateConfig(config.copy(minimizingSide = if (it == "Right") 0 else 1))
            }
        )

        // Button Toggles
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(
            "Visible Buttons",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontWeight = FontWeight.ExtraBold
        )

        ToggleSetting(
            title = "Show Pause/Resume",
            icon = Icons.Default.PauseCircle,
            checked = config.showPauseResumeOnMenu,
            onCheckedChange = { viewModel.updateConfig(config.copy(showPauseResumeOnMenu = it)) }
        )
        if (!config.showFacecam) {
            ToggleSetting(
                title = "Show Camera Toggle",
                icon = Icons.Default.Videocam,
                checked = config.showCameraButtonOnMenu,
                onCheckedChange = { viewModel.updateConfig(config.copy(showCameraButtonOnMenu = it)) }
            )
        }
        ToggleSetting(
            title = "Show Drawing Tools",
            icon = Icons.Default.Brush,
            checked = config.showDrawButtonOnMenu,
            onCheckedChange = { viewModel.updateConfig(config.copy(showDrawButtonOnMenu = it)) }
        )
        ToggleSetting(
            title = "Show Screenshot",
            icon = Icons.Default.Screenshot,
            checked = config.showScreenshotButtonOnMenu,
            onCheckedChange = { viewModel.updateConfig(config.copy(showScreenshotButtonOnMenu = it)) }
        )
        ToggleSetting(
            title = "Show Timer",
            icon = Icons.Default.Timer,
            checked = config.showTimeOnMenu,
            onCheckedChange = { viewModel.updateConfig(config.copy(showTimeOnMenu = it)) }
        )

        // Shortcuts
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        ToggleSetting(
            title = "Enable App Shortcuts",
            icon = Icons.Default.Apps,
            checked = config.showShortcuts,
            onCheckedChange = { viewModel.updateConfig(config.copy(showShortcuts = it)) }
        )
        
        if (config.showShortcuts) {
            var showAppPicker by remember { mutableStateOf(false) }

            SettingPicker(
                title = "Configure Shortcuts",
                icon = Icons.Default.Edit,
                value = "${config.shortcuts.size} Apps Selected",
                options = listOf("Configure..."),
                onSelected = { showAppPicker = true }
            )

            if (showAppPicker) {
                AppPickerDialog(
                    apps = installedApps,
                    selectedPackages = config.shortcuts,
                    onAppSelected = { pkg ->
                        val newList = if (config.shortcuts.contains(pkg)) {
                            config.shortcuts.filter { it != pkg }
                        } else {
                            (config.shortcuts + pkg).take(4) // Max 4 shortcuts
                        }
                        viewModel.updateConfig(config.copy(shortcuts = newList))
                    },
                    onDismiss = { showAppPicker = false }
                )
            }
        }
    }
}

