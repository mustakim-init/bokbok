package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.AppUsageInfo
import com.mustakim.bokbok.data.repository.UsageStatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

enum class IntervalType { DAILY, WEEKLY }
enum class UsageSortOrder { SCREEN_TIME, TIMES_OPENED, LAST_USED, APP_NAME, BATTERY_USAGE, DATA_USAGE }

data class UsageStatsUiState(
    val usageList: List<AppUsageInfo> = emptyList(),
    val totalScreenTime: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasPermission: Boolean = false,
    val currentDate: Long = System.currentTimeMillis(),
    val intervalType: IntervalType = IntervalType.DAILY,
    val sortOrder: UsageSortOrder = UsageSortOrder.SCREEN_TIME
)

class UsageStatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UsageStatsRepository(application)
    
    private val _uiState = MutableStateFlow(UsageStatsUiState())
    val uiState: StateFlow<UsageStatsUiState> = _uiState.asStateFlow()

    init {
        checkPermissionAndLoad()
    }

    fun onResume() {
        checkPermissionAndLoad()
    }
    
    private fun checkPermissionAndLoad() {
        val hasPermission = repository.hasUsageStatsPermission()
        _uiState.update { it.copy(hasPermission = hasPermission) }
        
        if (hasPermission) {
            loadUsageStats()
        }
    }
    
    fun requestPermission() {
        repository.requestUsageStatsPermission()
    }

    private fun loadUsageStats() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                val (list, totalTime) = repository.getUsageStats(
                    state.intervalType, 
                    state.currentDate,
                    state.sortOrder
                )
                
                _uiState.update { 
                    it.copy(
                        usageList = list,
                        totalScreenTime = totalTime,
                        isLoading = false
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = e.message,
                        isLoading = false
                    ) 
                }
            }
        }
    }

    fun onIntervalChanged(interval: IntervalType) {
        _uiState.update { it.copy(intervalType = interval) }
        loadUsageStats()
    }

    fun onSortOrderChanged(order: UsageSortOrder) {
        _uiState.update { it.copy(sortOrder = order) }
        loadUsageStats()
    }

    fun onNextDate() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = _uiState.value.currentDate
        
        if (_uiState.value.intervalType == IntervalType.DAILY) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        } else {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        
        // Don't go into future (optional constraint, users might want to see 0 stats for future)
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            _uiState.update { it.copy(currentDate = calendar.timeInMillis) }
            loadUsageStats()
        }
    }

    fun onPreviousDate() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = _uiState.value.currentDate
        
        if (_uiState.value.intervalType == IntervalType.DAILY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
        }
        
        _uiState.update { it.copy(currentDate = calendar.timeInMillis) }
        loadUsageStats()
    }
}
