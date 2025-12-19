package com.mustakim.bokbok.ui.screens.gameboost.devicemonitor

import android.os.Build
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mustakim.bokbok.data.model.BatteryInfo
import com.mustakim.bokbok.data.model.CpuInfo
import com.mustakim.bokbok.data.model.GpuInfo
import com.mustakim.bokbok.data.model.ProcessInfo
import com.mustakim.bokbok.data.model.RamInfo
import com.mustakim.bokbok.data.model.StorageBreakdown
import com.mustakim.bokbok.data.model.StorageInfo
import com.mustakim.bokbok.viewmodel.DeviceMonitorViewModel
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceMonitorScreen(
    viewModel: DeviceMonitorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showProcessSheet by remember { mutableStateOf(false) }
    var showStorageSheet by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // We ONLY stop here. Starting is handled by GameBoostScreen's pager state 
                // to ensure we don't monitor while hidden in another tab.
                Lifecycle.Event.ON_PAUSE -> viewModel.stopMonitoring()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopMonitoring()
        }
    }

    Scaffold(
        containerColor = Color.Transparent
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Memory Overview (RAM & Swap)
            item { 
                RamCard(
                    ramInfo = uiState.ramInfo,
                    onClick = { 
                        showProcessSheet = true 
                        viewModel.refreshProcesses()
                    }
                ) 
            }

            // GPU Overview
            item { GpuCard(uiState.gpuInfo) }
            
            // CPU Overview (with Top Processes)
            item { 
                CpuCard(
                    cpuInfo = uiState.cpuInfo,
                    topProcesses = uiState.processList.take(5)
                ) 
            }
            
            // Battery & Storage Row
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) { BatteryCard(uiState.batteryInfo) }
                    Box(modifier = Modifier.weight(1f)) { 
                        StorageCard(
                            storageInfo = uiState.storageInfo,
                            onClick = { 
                                showStorageSheet = true 
                                viewModel.refreshStorageBreakdown()
                            }
                        ) 
                    }
                }
            }

            // System Info
            item { SystemInfoCard() }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        if (showProcessSheet) {
            ProcessListModal(
                processes = uiState.processList,
                onDismiss = { showProcessSheet = false }
            )
        }

        if (showStorageSheet) {
            StorageDetailModal(
                info = uiState.storageInfo,
                breakdown = uiState.storageBreakdown,
                hasUsagePermission = uiState.hasUsagePermission,
                onDismiss = { showStorageSheet = false }
            )
        }
    }
}

@Composable
fun CircularMetric(
    progress: Float,
    label: String,
    valueText: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(120.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = color.copy(alpha = 0.1f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = valueText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun MonitorCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    iconColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            if (title != null && icon != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
            }
            content()
        }
    }
}

@Composable
fun RamCard(ramInfo: RamInfo, onClick: () -> Unit) {
    MonitorCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CircularMetric(
                progress = ramInfo.usagePercent / 100f,
                label = "Memory",
                valueText = "${ramInfo.usagePercent.roundToInt()}%",
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RAM  ${ramInfo.usagePercent.roundToInt()}% (${(ramInfo.totalMb / 1024f).format(0)}GB)",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                MetricProgressBar(progress = ramInfo.usagePercent / 100f, color = MaterialTheme.colorScheme.primary, height = 8.dp)
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = "Swaps  ${ramInfo.swapUsagePercent.roundToInt()}% (${(ramInfo.swapTotalMb / 1024f).format(1)}GB)",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                MetricProgressBar(progress = ramInfo.swapUsagePercent / 100f, color = MaterialTheme.colorScheme.secondary, height = 8.dp)
                
                Spacer(Modifier.height(12.dp))
                Row {
                    Text("Used ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${ramInfo.usagePercent.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Text("SwapCached ${ramInfo.swapCachedMb}MB", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun GpuCard(gpuInfo: GpuInfo) {
    MonitorCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            CircularMetric(
                progress = (gpuInfo.loadPercent ?: 0) / 100f,
                label = "GPU",
                valueText = if (gpuInfo.loadPercent != null) "${gpuInfo.loadPercent}%" else "--%",
                color = Color(0xFF9C27B0)
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Load: ${gpuInfo.loadPercent ?: "--"}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (gpuInfo.frequencyMhz != null && gpuInfo.frequencyMhz!! > 0) {
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "${gpuInfo.frequencyMhz} MHz",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (gpuInfo.temperatureCelsius != null) {
                        Spacer(Modifier.width(12.dp))
                        Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = " ${gpuInfo.temperatureCelsius.roundToInt()}°C",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Text(
                    text = gpuInfo.renderer ?: "Qualcomm Adreno",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                if (gpuInfo.model != null || gpuInfo.apiVersion != null) {
                    Text(
                        text = "${gpuInfo.model ?: ""} ${gpuInfo.apiVersion ?: ""}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}

@Composable
fun CpuCard(cpuInfo: CpuInfo, topProcesses: List<ProcessInfo>) {
    MonitorCard {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Left Side: Process List (Matches reference image)
            Column(modifier = Modifier.weight(1.2f)) {
                topProcesses.forEach { proc ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tux icon placeholder or app icon if we had it
                        Text("🐧", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = proc.name.takeLast(18),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${proc.cpuUsage.format(1)}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            // Divider
            Box(modifier = Modifier.width(1.dp).height(120.dp).background(MaterialTheme.colorScheme.outlineVariant))
            
            Spacer(Modifier.width(16.dp))
            
            // Right Side: CPU Load Info
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${cpuInfo.loadPercent.roundToInt()}%", style = MaterialTheme.typography.titleLarge)
                Text("CPU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(Modifier.height(12.dp))
                
                // Micro bars
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    cpuInfo.coreLoads.take(8).forEach { load ->
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (load > 5) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Text("Load: ${cpuInfo.loadPercent.roundToInt()}%", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun BatteryCard(batteryInfo: BatteryInfo) {
    MonitorCard(
        title = "Battery",
        icon = Icons.Outlined.BatteryFull,
        iconColor = if (batteryInfo.level > 20) Color(0xFF4CAF50) else Color(0xFFF44336)
    ) {
        Column {
            Text(
                text = "${batteryInfo.powerW?.format(2) ?: "--"}W",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BatteryFull, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${batteryInfo.level}%  ${batteryInfo.voltageV.format(2)}V", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("${batteryInfo.temperatureCelsius}°C", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun StorageCard(storageInfo: StorageInfo, onClick: () -> Unit) {
    MonitorCard(
        title = "Storage",
        icon = Icons.Default.SdStorage,
        iconColor = MaterialTheme.colorScheme.tertiary,
        onClick = onClick
    ) {
        Text(
            text = "${storageInfo.usagePercent.roundToInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${storageInfo.usedGb.format(0)}/${storageInfo.totalGb.format(0)} GB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SystemInfoCard() {
    MonitorCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow("Android ${Build.VERSION.RELEASE}", Icons.Default.Info)
            val uptime = SystemClock.elapsedRealtime()
            val hours = TimeUnit.MILLISECONDS.toHours(uptime)
            val minutes = TimeUnit.MILLISECONDS.toMinutes(uptime) % 60
            val seconds = TimeUnit.MILLISECONDS.toSeconds(uptime) % 60
            InfoRow("Up time  %02d:%02d:%02d".format(hours, minutes, seconds), Icons.Default.Speed)
        }
    }
}

@Composable
fun InfoRow(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcessListModal(processes: List<ProcessInfo>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    "Top Processes",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
            items(processes) { proc ->
                ListItem(
                    headlineContent = { Text(proc.name) },
                    supportingContent = { Text("PID: ${proc.pid} | User: ${proc.user}") },
                    trailingContent = {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${proc.ramUsageMb.roundToInt()} MB", fontWeight = FontWeight.Bold)
                            Text("${proc.cpuUsage}% CPU", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDetailModal(info: StorageInfo, breakdown: StorageBreakdown, hasUsagePermission: Boolean, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text("Storage Details", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(24.dp))
            
            if (!hasUsagePermission) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Permission Needed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "To show accurate App usage, please grant Usage Access permission.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.Button(
                            onClick = { 
                                try {
                                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                } catch (_: Exception) {}
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant Permission", color = MaterialTheme.colorScheme.onError)
                        }
                    }
                }
            }
            
            StorageCategory("Apps", breakdown.appsGb, MaterialTheme.colorScheme.primary)
            StorageCategory("System", breakdown.systemGb, MaterialTheme.colorScheme.secondary)
            StorageCategory("Media", breakdown.imagesGb + breakdown.videosGb + breakdown.audioGb, MaterialTheme.colorScheme.tertiary)
            StorageCategory("Documents", breakdown.documentsGb, Color(0xFF4CAF50))
            StorageCategory("Other", breakdown.otherGb, MaterialTheme.colorScheme.outline)
            
            Spacer(Modifier.height(32.dp))
            Text("Total Used: ${info.usedGb.format(1)} GB / ${info.totalGb.format(1)} GB", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun StorageCategory(label: String, sizeGb: Float, color: Color) {
    Row(modifier = Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text("${sizeGb.format(2)} GB", fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MetricProgressBar(
    progress: Float,
    color: Color,
    height: androidx.compose.ui.unit.Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")
    LinearProgressIndicator(
        progress = animatedProgress,
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2)),
        color = color,
        trackColor = color.copy(alpha = 0.12f)
    )
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)
