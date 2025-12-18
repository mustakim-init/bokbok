package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.DeviceMonitorUiState
import com.mustakim.bokbok.data.repository.DeviceMonitorRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DeviceMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceMonitorRepository(application)
    private val _uiState = MutableStateFlow(DeviceMonitorUiState())
    val uiState: StateFlow<DeviceMonitorUiState> = _uiState.asStateFlow()

    private var monitoringJob: Job? = null
    private var isRefreshing = false

    fun startMonitoring() {
        if (isRefreshing) return
        isRefreshing = true
        
        monitoringJob?.cancel()
        monitoringJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val cpu = repository.getCpuInfo()
                    val ram = repository.getRamInfo()
                    val battery = repository.getBatteryInfo()
                    val storage = repository.getStorageInfo()
                    val gpu = repository.getGpuInfo()

                    _uiState.update {
                        it.copy(
                            cpuInfo = cpu,
                            ramInfo = ram,
                            batteryInfo = battery,
                            storageInfo = storage,
                            gpuInfo = gpu,
                            isLoading = false,
                            error = null
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { it.copy(error = e.message, isLoading = false) }
                }
                delay(1500) // Poll every 1.5 seconds
            }
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
