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
        monitoringJob = viewModelScope.launch {
            while (isActive) {
                try {
                    // Ticks are every 2 seconds
                    val isProcessTick = tickCount % 2 == 0   // Every 4 seconds
                    val isBatteryTick = tickCount % 5 == 0   // Every 10 seconds
                    val isStorageTick = tickCount % 10 == 0  // Every 20 seconds

                    val cpuDef = async(Dispatchers.IO) { repository.getCpuInfo() }
                    val ramDef = async(Dispatchers.IO) { repository.getRamInfo() }
                    val gpuDef = async(Dispatchers.IO) { repository.getGpuInfo() }
                    
                    val procDef = if (isProcessTick) async(Dispatchers.IO) { repository.getRunningProcesses() } else null
                    val batDef = if (isBatteryTick) async(Dispatchers.IO) { repository.getBatteryInfo() } else null
                    val stoDef = if (isStorageTick) async(Dispatchers.IO) { repository.getStorageInfo() } else null

                    // Await items
                    val cpu = try { cpuDef.await() } catch (_: Exception) { _uiState.value.cpuInfo }
                    val ram = try { ramDef.await() } catch (_: Exception) { _uiState.value.ramInfo }
                    val gpu = try { gpuDef.await() } catch (_: Exception) { _uiState.value.gpuInfo }
                    
                    val processes = if (procDef != null) {
                        try { procDef.await() } catch (_: Exception) { _uiState.value.processList }
                    } else _uiState.value.processList
                    
                    val battery = if (batDef != null) {
                        try { batDef.await() } catch (_: Exception) { _uiState.value.batteryInfo }
                    } else _uiState.value.batteryInfo
                    
                    val storage = if (stoDef != null) {
                        try { stoDef.await() } catch (_: Exception) { _uiState.value.storageInfo }
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
