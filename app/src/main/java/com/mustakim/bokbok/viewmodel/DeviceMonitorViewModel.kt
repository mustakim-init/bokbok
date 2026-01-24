package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.DeviceMonitorUiState
import com.mustakim.bokbok.data.repository.DeviceMonitorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DeviceMonitorViewModel @Inject constructor(
    private val repository: DeviceMonitorRepository
) : androidx.lifecycle.ViewModel() {
    private val _uiState = MutableStateFlow(DeviceMonitorUiState())
    val uiState: StateFlow<DeviceMonitorUiState> = _uiState.asStateFlow()

    private var monitoringJob: Job? = null
    private var isRefreshing = false

    private var tickCount = 0

    fun startMonitoring() {
        if (isRefreshing) return
        isRefreshing = true
        
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch(Dispatchers.IO) { // Run on IO to unblock Main thread
            while (isActive) {
                try {
                    // Ticks are every 2 seconds
                    val isBatteryTick = tickCount % 5 == 0   // Every 10 seconds
                    val isStorageTick = tickCount % 15 == 0  // Every 30 seconds
                    val isProcessTick = tickCount % 4 == 0   // Every 8 seconds (top is VERY heavy)

                    // SEQUENCE UPDATES to avoid "Shizuku Storm"
                    val cpu = try { repository.getCpuInfo() } catch (_: Exception) { _uiState.value.cpuInfo }
                    val ram = try { repository.getRamInfo() } catch (_: Exception) { _uiState.value.ramInfo }
                    val gpu = try { repository.getGpuInfo() } catch (_: Exception) { _uiState.value.gpuInfo }
                    
                    val processes = if (isProcessTick) {
                        try { repository.getRunningProcesses() } catch (_: Exception) { _uiState.value.processList }
                    } else _uiState.value.processList
                    
                    val battery = if (isBatteryTick) {
                        try { repository.getBatteryInfo() } catch (_: Exception) { _uiState.value.batteryInfo }
                    } else _uiState.value.batteryInfo
                    
                    val storage = if (isStorageTick) {
                        try { repository.getStorageInfo() } catch (_: Exception) { _uiState.value.storageInfo }
                    } else _uiState.value.storageInfo

                    val hasPermission = repository.hasUsageStatsPermission()

                    _uiState.update {
                        it.copy(
                            cpuInfo = cpu,
                            ramInfo = ram,
                            gpuInfo = gpu,
                            processList = processes,
                            batteryInfo = battery,
                            storageInfo = storage,
                            hasUsagePermission = hasPermission,
                            isLoading = false,
                            error = null
                        )
                    }
                    tickCount++
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                delay(2000) 
            }
        }
    }

    fun refreshProcesses() {
        viewModelScope.launch {
            val processes = repository.getRunningProcesses()
            _uiState.update { it.copy(processList = processes) }
        }
    }

    fun refreshStorageBreakdown() {
        viewModelScope.launch {
            val breakdown = repository.getStorageBreakdown()
            _uiState.update { it.copy(storageBreakdown = breakdown) }
        }
    }

    fun stopMonitoring() {
        isRefreshing = false
        monitoringJob?.cancel()
        monitoringJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopMonitoring()
    }
}
