package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.AppUsageInfo
import com.mustakim.bokbok.data.repository.UsageStatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import java.util.Calendar
import javax.inject.Inject

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



@HiltViewModel
class UsageStatsViewModel @Inject constructor(
    private val repository: UsageStatsRepository
) : androidx.lifecycle.ViewModel() {
    
    private val _sortOrder = MutableStateFlow(UsageSortOrder.SCREEN_TIME)
    private val _isLoading = MutableStateFlow(false)
    private val _currentDate = MutableStateFlow(System.currentTimeMillis())
    private val _intervalType = MutableStateFlow(IntervalType.DAILY)
    private val _hasPermission = MutableStateFlow(false)

    val uiState: StateFlow<UsageStatsUiState> = combine(
        repository.observeUsageStats(),
        combine(_sortOrder, _isLoading, _currentDate, _intervalType, _hasPermission, ::UsageSettingsData)
    ) { stats, settings ->
        val sortedList = stats.sortedWith(getComparator(settings.sort))
        val totalTime = stats.sumOf { it.screenTime }
        
        UsageStatsUiState(
            usageList = sortedList,
            totalScreenTime = totalTime,
            isLoading = settings.loading,
            hasPermission = settings.perm,
            currentDate = settings.date,
            intervalType = settings.interval,
            sortOrder = settings.sort
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        UsageStatsUiState(isLoading = true)
    )

    init {
        checkPermission()
    }

    private fun checkPermission() {
        viewModelScope.launch(Dispatchers.IO) {
            val hasPerm = repository.hasUsageStatsPermission()
            _hasPermission.value = hasPerm
        }
    }

    fun loadDataIfNeeded() {
        if (uiState.value.usageList.isEmpty() && uiState.value.hasPermission) {
            loadUsageStats()
        }
    }

    fun onResume() {
        checkPermission()
        if (_hasPermission.value && uiState.value.usageList.isEmpty()) {
            loadUsageStats()
        }
    }
    
    fun requestPermission() {
        repository.requestUsageStatsPermission()
    }

    private fun loadUsageStats() {
        repository.refreshUsageStats()
    }

    fun onIntervalChanged(interval: IntervalType) {
        _intervalType.value = interval
        // Note: Repository/Worker currently only caches Today (Daily).
        // For Weekly, we might want to extend the worker later.
        loadUsageStats()
    }

    fun onSortOrderChanged(order: UsageSortOrder) {
        _sortOrder.value = order
    }

    fun onNextDate() {
        // Simple update for now, trigger scan for specific date
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = _currentDate.value
        
        if (_intervalType.value == IntervalType.DAILY) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        } else {
            calendar.add(Calendar.WEEK_OF_YEAR, 1)
        }
        
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            _currentDate.value = calendar.timeInMillis
            loadUsageStats()
        }
    }

    fun onPreviousDate() {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = _currentDate.value
        
        if (_intervalType.value == IntervalType.DAILY) {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        } else {
            calendar.add(Calendar.WEEK_OF_YEAR, -1)
        }
        
        _currentDate.value = calendar.timeInMillis
        loadUsageStats()
    }

    private fun getComparator(sort: UsageSortOrder) = Comparator<AppUsageInfo> { a, b ->
        when (sort) {
            UsageSortOrder.SCREEN_TIME -> b.screenTime.compareTo(a.screenTime)
            UsageSortOrder.TIMES_OPENED -> b.timesOpened.compareTo(a.timesOpened)
            UsageSortOrder.LAST_USED -> b.lastUsedTime.compareTo(a.lastUsedTime)
            UsageSortOrder.APP_NAME -> a.appLabel.lowercase().compareTo(b.appLabel.lowercase())
            UsageSortOrder.BATTERY_USAGE -> b.batteryUsage.compareTo(a.batteryUsage)
            UsageSortOrder.DATA_USAGE -> (b.mobileDataUsage + b.wifiDataUsage).compareTo(a.mobileDataUsage + a.wifiDataUsage)
        }
    }
}

data class UsageSettingsData(
    val sort: UsageSortOrder,
    val loading: Boolean,
    val date: Long,
    val interval: IntervalType,
    val perm: Boolean
)
