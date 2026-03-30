package com.mustakim.bokbok.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class DeviceMonitorUiState(
    val cpuInfo: CpuInfo = CpuInfo(),
    val ramInfo: RamInfo = RamInfo(),
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val storageInfo: StorageInfo = StorageInfo(),
    val gpuInfo: GpuInfo = GpuInfo(),
    val processList: List<ProcessInfo> = emptyList(),
    val storageBreakdown: StorageBreakdown = StorageBreakdown(),
    val hasUsagePermission: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

@Immutable
data class CpuInfo(
    val loadPercent: Float = 0f,
    val coreCount: Int = 0,
    val coreLoads: List<Float> = emptyList(),
    val frequencies: List<Long> = emptyList(),  // Per-core frequencies in kHz
    val onlineStatus: List<Boolean> = emptyList(),
    val socName: String? = null,
    val architecture: String? = null,
    val temperatureCelsius: Float? = null,
    val fps: Float = 0f,
    val clusters: List<CpuClusterInfo> = emptyList()
)

@Immutable
data class CpuClusterInfo(
    val id: Int,
    val coreRange: IntRange,
    val governor: String?,
    val currentFreq: Long,
    val minFreq: Long,
    val maxFreq: Long
)

@Immutable
data class CpuCoreInfo(
    val id: Int,
    val loadRatio: Float,
    val currentFreq: Long,
    val isOnline: Boolean,
    val minFreq: Long = 0,
    val maxFreq: Long = 0
)

@Immutable
data class RamInfo(
    val totalMb: Long = 0,
    val usedMb: Long = 0,
    val availableMb: Long = 0,
    val usagePercent: Float = 0f,
    val swapTotalMb: Long = 0,
    val swapUsedMb: Long = 0,
    val swapUsagePercent: Float = 0f,
    val swapCachedMb: Long = 0
)

@Immutable
data class BatteryInfo(
    val level: Int = 0,
    val temperatureCelsius: Float = 0f,
    val isCharging: Boolean = false,
    val currentMa: Int? = null,
    val voltageV: Float = 0f,
    val health: String = "Unknown",
    val healthPercent: Int? = null,
    val powerW: Float? = null,
    val designCapacityMah: Int? = null,
    val maxCapacityMah: Int? = null,
    val deepSleepPercent: Int? = null
)

@Immutable
data class StorageInfo(
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    val availableGb: Float = 0f,
    val usagePercent: Float = 0f
)

@Immutable
data class StorageBreakdown(
    val appsGb: Float = 0f,
    val systemGb: Float = 0f,
    val imagesGb: Float = 0f,
    val videosGb: Float = 0f,
    val audioGb: Float = 0f,
    val documentsGb: Float = 0f,
    val otherGb: Float = 0f
)

@Immutable
data class GpuInfo(
    val loadPercent: Int? = null,
    val frequencyMhz: Int? = null,
    val renderer: String? = null,
    val vendor: String? = null,
    val apiVersion: String? = null,
    val model: String? = null,
    val clockSpeedMhz: Int? = null,
    val temperatureCelsius: Float? = null,
    val powerLevel: Int? = null,
    val maxPowerLevel: Int? = null,
    val available: Boolean = false,
    val gameFps: Float = 0f
)

@Immutable
data class ProcessInfo(
    val pid: Int,
    val name: String,
    val cpuUsage: Float,
    val ramUsageMb: Float,
    val user: String,
    val command: String
)
