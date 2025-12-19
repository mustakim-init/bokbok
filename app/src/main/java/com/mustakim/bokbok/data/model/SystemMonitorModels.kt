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
    val coreLoads: List<Float> = emptyList(),
    val frequencies: List<Long> = emptyList(),  // Per-core frequencies in kHz
    val onlineStatus: List<Boolean> = emptyList()
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
    val currentMa: Int? = null,  // Current draw in mA (if available)
    val voltageV: Float = 0f,
    val health: String = "Unknown",
    val powerW: Float? = null // Power in Watts (Voltage * Current)
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
    val loadPercent: Int? = null,      // null if unavailable
    val frequencyMhz: Int? = null,     // null if unavailable
    val renderer: String? = null,
    val vendor: String? = null,
    val apiVersion: String? = null,
    val model: String? = null,
    val clockSpeedMhz: Int? = null,
    val temperatureCelsius: Float? = null,
    val available: Boolean = false
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
