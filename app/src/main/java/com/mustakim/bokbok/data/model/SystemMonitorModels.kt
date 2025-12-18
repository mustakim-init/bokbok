package com.mustakim.bokbok.data.model

data class DeviceMonitorUiState(
    val cpuInfo: CpuInfo = CpuInfo(),
    val ramInfo: RamInfo = RamInfo(),
    val batteryInfo: BatteryInfo = BatteryInfo(),
    val storageInfo: StorageInfo = StorageInfo(),
    val gpuInfo: GpuInfo = GpuInfo(),
    val isLoading: Boolean = true,
    val error: String? = null
)

data class CpuInfo(
    val loadPercent: Float = 0f,
    val coreLoads: List<Float> = emptyList(),
    val frequencies: List<Long> = emptyList(),  // Per-core frequencies in kHz
    val onlineStatus: List<Boolean> = emptyList()
)

data class RamInfo(
    val totalMb: Long = 0,
    val usedMb: Long = 0,
    val availableMb: Long = 0,
    val usagePercent: Float = 0f
)

data class BatteryInfo(
    val level: Int = 0,
    val temperatureCelsius: Float = 0f,
    val isCharging: Boolean = false,
    val currentMa: Int? = null,  // Current draw in mA (if available)
    val voltageV: Float = 0f,
    val health: String = "Unknown"
)

data class StorageInfo(
    val totalGb: Float = 0f,
    val usedGb: Float = 0f,
    val availableGb: Float = 0f,
    val usagePercent: Float = 0f
)

data class GpuInfo(
    val loadPercent: Int? = null,      // null if unavailable
    val frequencyMhz: Int? = null,     // null if unavailable
    val available: Boolean = false
)
