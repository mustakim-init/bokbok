package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.bloatware.RemovalSafety
import com.mustakim.bokbok.data.model.AppItem
import com.mustakim.bokbok.data.repository.AppManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppManagerViewModel(
    application: android.app.Application
) : androidx.lifecycle.AndroidViewModel(application) {

    private val repository = AppManagerRepository(application)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow(AppFilterType.ALL)
    val filterType = _filterType.asStateFlow()

    private val _sortOrder = MutableStateFlow(AppSortOrder.NAME_ASC)
    val sortOrder = _sortOrder.asStateFlow()

    private val _rawApps = MutableStateFlow<List<AppItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    
    // Selected app for details screen
    private val _selectedApp = MutableStateFlow<AppItem?>(null)
    val selectedApp: StateFlow<AppItem?> = _selectedApp.asStateFlow()
    
    // Combining flows to produce the final UI list
    val uiState: StateFlow<AppManagerUiState> = combine(
        combine(_rawApps, _isLoading, _error, ::Triple),
        combine(_searchQuery, _filterType, _sortOrder, ::Triple)
    ) { (apps, isLoading, error), (query, filter, sort) ->
        val filteredApps = apps
            .filter { app ->
                // Apply Filter Type
                when (filter) {
                    AppFilterType.ALL -> app.isInstalled // Show only installed by default for ALL
                    AppFilterType.USER -> app.isInstalled && !app.isSystemApp
                    AppFilterType.SYSTEM -> app.isInstalled && app.isSystemApp
                    AppFilterType.BLOATWARE -> app.isInstalled && app.isBloatware
                    AppFilterType.SAFE_TO_REMOVE -> app.isInstalled && app.isBloatware && 
                        (app.removalSafety == RemovalSafety.SAFE || app.removalSafety == RemovalSafety.REPLACEABLE)
                    AppFilterType.UNINSTALLED -> !app.isInstalled // Show only uninstalled (restore candidates)
                }
            }
            .filter { app ->
                // Apply Search Query
                if (query.isBlank()) true
                else app.label.contains(query, ignoreCase = true) || 
                     app.packageName.contains(query, ignoreCase = true)
            }
            .let { filteredList ->
                // Apply Sorting
                when (sort) {
                    AppSortOrder.NAME_ASC -> filteredList.sortedBy { it.label.lowercase() }
                    AppSortOrder.NAME_DESC -> filteredList.sortedByDescending { it.label.lowercase() }
                    AppSortOrder.SIZE -> filteredList.sortedByDescending { it.apkSize }
                    AppSortOrder.INSTALL_DATE -> filteredList.sortedByDescending { it.firstInstallTime }
                    AppSortOrder.UPDATE_DATE -> filteredList.sortedByDescending { it.lastUpdateTime }
                    AppSortOrder.BLOATWARE_FIRST -> filteredList.sortedByDescending { it.isBloatware }
                }
            }

        // Calculate bloatware stats
        val bloatwareCount = apps.count { it.isBloatware }
        val safeToRemoveCount = apps.count { 
            it.isBloatware && (it.removalSafety == RemovalSafety.SAFE || it.removalSafety == RemovalSafety.REPLACEABLE)
        }

        AppManagerUiState(
            apps = filteredApps,
            isLoading = isLoading,
            error = error,
            selectedCount = filteredApps.count { it.isSelected },
            totalApps = apps.size,
            bloatwareCount = bloatwareCount,
            safeToRemoveCount = safeToRemoveCount
        )
    }.flowOn(Dispatchers.Default)
    .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        AppManagerUiState(isLoading = true)
    )

    init {
        viewModelScope.launch {
            // Attempt to grant Usage Stats permission via Shizuku
            repository.grantSelfPermissions()
            
            // Note: Bloatware DB sync moved to BloatwareSyncWorker (WorkManager)
            // for non-blocking app startup

            // Then load apps
            loadApps()
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                repository.getInstalledApps()
                    .collect { apps ->
                        _rawApps.value = apps
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                _error.value = e.message
                _isLoading.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFilterChanged(filter: AppFilterType) {
        _filterType.value = filter
    }

    fun onSortOrderChanged(order: AppSortOrder) {
        _sortOrder.value = order
    }

    fun onAppClicked(app: AppItem) {
        // Open our custom details screen
        _selectedApp.value = app
    }
    
    fun clearSelectedApp() {
        _selectedApp.value = null
    }
    
    fun openSystemAppDetails(packageName: String) {
        repository.openAppDetails(packageName)
    }
    
    fun getRepository(): AppManagerRepository = repository

    fun onAppLongClicked(app: AppItem) {
        // Toggle selection
        val currentApps = _rawApps.value.toMutableList()
        val index = currentApps.indexOfFirst { it.packageName == app.packageName }
        if (index != -1) {
            currentApps[index] = app.copy(isSelected = !app.isSelected)
            _rawApps.value = currentApps
        }
    }

    fun clearSelection() {
        _rawApps.value = _rawApps.value.map { it.copy(isSelected = false) }
    }

    fun onUninstallSelected() {
        viewModelScope.launch {
            val selected = uiState.value.apps.filter { it.isSelected }
            selected.forEach { app ->
                if (app.isSystemApp) {
                    // Use ADB for system apps
                    repository.uninstallViaAdb(app.packageName)
                } else {
                    // Standard uninstall for user apps
                    repository.requestUninstall(app.packageName)
                }
            }
            clearSelection()
            // Refresh to update list
            loadApps()
        }
    }

    fun onForceStopSelected() {
        viewModelScope.launch {
            val selected = uiState.value.apps.filter { it.isSelected }
            selected.forEach { repository.forceStopApp(it.packageName) }
            clearSelection()
        }
    }

    fun onClearCacheSelected() {
        viewModelScope.launch {
            val selected = uiState.value.apps.filter { it.isSelected }
            selected.forEach { repository.clearAppCache(it.packageName) }
            clearSelection()
        }
    }

    fun onRestoreSelected() {
        viewModelScope.launch {
            val selected = uiState.value.apps.filter { it.isSelected }
            selected.forEach { repository.reinstallApp(it.packageName) }
            clearSelection()
            loadApps()
        }
    }

    fun onRefresh() {
        loadApps()
    }

    fun checkForDatabaseUpdates() {
        viewModelScope.launch {
            _isLoading.value = true
            val updated = repository.syncBloatwareDatabase()
            _isLoading.value = false
            if (updated) {
                 // Reload to apply new definitions
                 loadApps()
            }
        }
    }
}

data class AppManagerUiState(
    val apps: List<AppItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedCount: Int = 0,
    val totalApps: Int = 0,
    val bloatwareCount: Int = 0,
    val safeToRemoveCount: Int = 0
)

enum class AppFilterType { ALL, USER, SYSTEM, BLOATWARE, SAFE_TO_REMOVE, UNINSTALLED }
enum class AppSortOrder { NAME_ASC, NAME_DESC, SIZE, INSTALL_DATE, UPDATE_DATE, BLOATWARE_FIRST }

