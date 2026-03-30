package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.bloatware.RemovalSafety
import com.mustakim.bokbok.data.model.AppItem
import com.mustakim.bokbok.data.repository.AppManagerRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppManagerViewModel @Inject constructor(
    private val repository: AppManagerRepository
) : androidx.lifecycle.ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow(AppFilterType.ALL)
    val filterType = _filterType.asStateFlow()

    private val _sortOrder = MutableStateFlow(AppSortOrder.NAME_ASC)
    val sortOrder = _sortOrder.asStateFlow()

    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackages = _selectedPackages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    
    // Selected app for details screen
    private val _selectedApp = MutableStateFlow<AppItem?>(null)
    val selectedApp: StateFlow<AppItem?> = _selectedApp.asStateFlow()

    private val _isFilterSheetVisible = MutableStateFlow(false)
    val isFilterSheetVisible = _isFilterSheetVisible.asStateFlow()

    private val _isSortSheetVisible = MutableStateFlow(false)
    val isSortSheetVisible = _isSortSheetVisible.asStateFlow()

    private val _appsFlow = repository.observeApps()
    
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val debouncedSearchQuery = _searchQuery
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    // Combining flows to produce the final UI list
    val uiState: StateFlow<AppManagerUiState> = combine(
        combine(_appsFlow, _selectedPackages, _isLoading, _error, ::AppStatsData),
        combine(debouncedSearchQuery, _filterType, _sortOrder, ::Triple)
    ) { (apps, selected, isLoading, error), (query, filter, sort) ->
        val filteredApps = apps
            .map { it.copy(isSelected = selected.contains(it.packageName)) }
            .filter { app ->
                // Apply Filter Type
                when (filter) {
                    AppFilterType.ALL -> app.isInstalled // Show only installed by default for ALL
                    AppFilterType.USER -> app.isInstalled && !app.isSystemApp
                    AppFilterType.SYSTEM -> app.isInstalled && app.isSystemApp
                    AppFilterType.BLOATWARE -> app.isInstalled && app.isBloatware
                    AppFilterType.SAFE_TO_REMOVE -> app.isInstalled && app.isBloatware && 
                        (app.removalSafety == RemovalSafety.SAFE || app.removalSafety == RemovalSafety.REPLACEABLE || app.removalSafety == RemovalSafety.CAUTION)
                    AppFilterType.UNINSTALLED -> !app.isInstalled // Show only uninstalled (restore candidates)
                    AppFilterType.DISABLED -> app.isInstalled && !app.isEnabled
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
                    AppSortOrder.BLOATWARE_FIRST -> filteredList.sortedWith(
                        compareByDescending<AppItem> { it.isBloatware }.thenBy { it.label.lowercase() }
                    )
                    AppSortOrder.PACKAGE_NAME -> filteredList.sortedBy { it.packageName }
                    AppSortOrder.TARGET_SDK -> filteredList.sortedByDescending { it.targetSdk }
                }
            }

        // Calculate bloatware stats
        val bloatwareCount = apps.count { it.isInstalled && it.isBloatware }
        val safeToRemoveCount = apps.count { 
            it.isInstalled && it.isBloatware && (it.removalSafety == RemovalSafety.SAFE || it.removalSafety == RemovalSafety.REPLACEABLE || it.removalSafety == RemovalSafety.CAUTION)
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
        SharingStarted.Lazily,
        AppManagerUiState(isLoading = true)
    )

    init {
        viewModelScope.launch(Dispatchers.IO) {
            // Attempt to grant Usage Stats permission via Shizuku
            repository.grantSelfPermissions()
            
            // 🚀 LAZY LOADING: Trigger initial scan only when entering this feature
            // and only if the database hasn't been populated yet.
            if (uiState.value.apps.isEmpty()) {
                repository.refreshApps()
            }
            
            // Check for debloat database updates in the background
            checkForDatabaseUpdates()
        }
    }

    fun loadDataIfNeeded() {
        // Data is loaded automatically via Flow/Room. 
        // No need to trigger a worker refresh just because state is initially empty.
    }

    fun loadApps(forceRefresh: Boolean = false) {
        if (forceRefresh) {
            repository.refreshApps()
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
        // 🚀 SMART SCAN ENRICHMENT: Fetch deep metadata (paths, sizes) on-demand
        viewModelScope.launch {
            repository.fetchFullAppDetails(app.packageName)
        }
        // Open our custom details screen
        _selectedApp.value = app
    }

    fun showFilterSheet() { _isFilterSheetVisible.value = true }
    fun hideFilterSheet() { _isFilterSheetVisible.value = false }
    fun showSortSheet() { _isSortSheetVisible.value = true }
    fun hideSortSheet() { _isSortSheetVisible.value = false }
    
    fun clearSelectedApp() {
        _selectedApp.value = null
    }
    
    fun openSystemAppDetails(packageName: String) {
        repository.openAppDetails(packageName)
    }
    
    fun getRepository(): AppManagerRepository = repository

    fun onAppLongClicked(app: AppItem) {
        val current = _selectedPackages.value
        if (current.contains(app.packageName)) {
            _selectedPackages.value = current - app.packageName
        } else {
            _selectedPackages.value = current + app.packageName
        }
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
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
        viewModelScope.launch {
            _isLoading.value = true
            // Fake animation delay for better UX
            delay(1500)
            loadApps(forceRefresh = true)
            _isLoading.value = false
        }
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

data class AppStatsData(
    val apps: List<AppItem>,
    val selected: Set<String>,
    val isLoading: Boolean,
    val error: String?
)

enum class AppFilterType { ALL, USER, SYSTEM, BLOATWARE, SAFE_TO_REMOVE, UNINSTALLED, DISABLED }
enum class AppSortOrder { NAME_ASC, NAME_DESC, SIZE, INSTALL_DATE, UPDATE_DATE, BLOATWARE_FIRST, PACKAGE_NAME, TARGET_SDK }

