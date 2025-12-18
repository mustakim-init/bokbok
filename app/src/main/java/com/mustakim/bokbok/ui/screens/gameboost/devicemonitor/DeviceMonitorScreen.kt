package com.mustakim.bokbok.ui.screens.gameboost.devicemonitor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mustakim.bokbok.data.model.*
import com.mustakim.bokbok.viewmodel.DeviceMonitorViewModel
import kotlin.math.roundToInt

@Composable
fun DeviceMonitorScreen(
    viewModel: DeviceMonitorViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.startMonitoring()
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
            item { CpuCard(uiState.cpuInfo) }
            item { RamCard(uiState.ramInfo) }
            
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.weight(1f)) { BatteryCard(uiState.batteryInfo) }
                    Box(modifier = Modifier.weight(1f)) { StorageCard(uiState.storageInfo) }
                }
            }

            if (uiState.gpuInfo.available) {
                item { GpuCard(uiState.gpuInfo) }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun MonitorCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun CpuCard(cpuInfo: CpuInfo) {
    MonitorCard(
        title = "CPU",
        icon = Icons.Default.Speed,
        iconColor = MaterialTheme.colorScheme.primary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "${cpuInfo.loadPercent.roundToInt()}%",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            val avgFreq = cpuInfo.frequencies.filter { it > 0 }.let { if (it.isEmpty()) 0L else it.average().toLong() }
            Text(
                text = "${(avgFreq / 1000f).format(1)} GHz",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        MetricProgressBar(
            progress = cpuInfo.loadPercent / 100f,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Cores grid
        val columns = 4
        val rows = (cpuInfo.coreLoads.size + columns - 1) / columns
        
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (r in 0 until rows) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        if (index < cpuInfo.coreLoads.size) {
                            CoreMiniBar(
                                index = index,
                                load = cpuInfo.coreLoads[index],
                                isOnline = cpuInfo.onlineStatus.getOrElse(index) { true },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RamCard(ramInfo: RamInfo) {
    MonitorCard(
        title = "RAM",
        icon = Icons.Default.Memory,
        iconColor = MaterialTheme.colorScheme.secondary
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Column {
                Text(
                    text = "${ramInfo.usagePercent.roundToInt()}%",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${ramInfo.usedMb}MB / ${ramInfo.totalMb}MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        MetricProgressBar(
            progress = ramInfo.usagePercent / 100f,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
fun BatteryCard(batteryInfo: BatteryInfo) {
    MonitorCard(
        title = "Battery",
        icon = Icons.Outlined.BatteryFull,
        iconColor = if (batteryInfo.level > 20) Color(0xFF4CAF50) else Color(0xFFF44336)
    ) {
        Text(
            text = "${batteryInfo.level}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${batteryInfo.temperatureCelsius}°C",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (batteryInfo.isCharging) {
            Text(
                text = "⚡ Charging",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF4CAF50),
                fontWeight = FontWeight.Bold
            )
        }
        if (batteryInfo.currentMa != null) {
            Text(
                text = "${if (batteryInfo.currentMa > 0) "+" else ""}${batteryInfo.currentMa}mA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun StorageCard(storageInfo: StorageInfo) {
    MonitorCard(
        title = "Storage",
        icon = Icons.Default.SdStorage,
        iconColor = MaterialTheme.colorScheme.tertiary
    ) {
        Text(
            text = "${storageInfo.usagePercent.roundToInt()}%",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "${storageInfo.usedGb.format(1)} / ${storageInfo.totalGb.format(1)} GB",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun GpuCard(gpuInfo: GpuInfo) {
    MonitorCard(
        title = "GPU",
        icon = Icons.Outlined.DynamicFeed,
        iconColor = Color(0xFF9C27B0)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = if (gpuInfo.loadPercent != null) "${gpuInfo.loadPercent}%" else "Busy",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (gpuInfo.frequencyMhz != null) {
                Text(
                    text = "${gpuInfo.frequencyMhz} MHz",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color(0xFF9C27B0)
                )
            }
        }
        
        if (gpuInfo.loadPercent != null) {
            Spacer(modifier = Modifier.height(12.dp))
            MetricProgressBar(
                progress = gpuInfo.loadPercent / 100f,
                color = Color(0xFF9C27B0)
            )
        }
    }
}

@Composable
fun CoreMiniBar(index: Int, load: Float, isOnline: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "C$index", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp)
            Text(
                text = if (isOnline) "${load.roundToInt()}%" else "OFF",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = if (isOnline) MaterialTheme.colorScheme.onSurfaceVariant else Color.Red
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (isOnline) {
                val animatedLoad by animateFloatAsState(targetValue = load / 100f, label = "core_load")
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedLoad)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
fun MetricProgressBar(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 800),
        label = "progress"
    )
    
    LinearProgressIndicator(
        progress = animatedProgress,
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp)),
        color = color,
        trackColor = color.copy(alpha = 0.12f)
    )
}

fun Float.format(digits: Int) = "%.${digits}f".format(this)
