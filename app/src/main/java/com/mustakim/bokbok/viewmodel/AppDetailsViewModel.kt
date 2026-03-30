package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.AppItem
import com.mustakim.bokbok.data.repository.AppManagerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppDetailsViewModel @Inject constructor(
    private val repository: AppManagerRepository
) : ViewModel() {

    private val _packageName = MutableStateFlow<String?>(null)
    
    val app: StateFlow<AppItem?> = _packageName
        .filterNotNull()
        .flatMapLatest { pkg -> repository.observeApp(pkg) }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing = _isProcessing.asStateFlow()

    private val _selectedComponentType = MutableStateFlow(AppManagerRepository.ComponentType.SERVICE)
    val selectedComponentType = _selectedComponentType.asStateFlow()

    val components: StateFlow<List<com.mustakim.bokbok.data.model.AppComponent>> = combine(
        _packageName.filterNotNull(),
        _selectedComponentType,
        _isProcessing // Refresh when processing finishes
    ) { pkg, type, _ ->
        repository.getAppComponents(pkg, type)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val permissionCount: StateFlow<Int> = _packageName
        .filterNotNull()
        .map { repository.getPermissionCount(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val componentCounts: StateFlow<Triple<Int, Int, Int>> = _packageName
        .filterNotNull()
        .map { repository.getComponentCount(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, Triple(0, 0, 0))

    val permissions: StateFlow<List<com.mustakim.bokbok.data.model.AppPermissionDetail>> = combine(
        _packageName.filterNotNull(),
        _isProcessing
    ) { pkg, _ ->
        repository.getAppPermissions(pkg)
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setPackageName(packageName: String) {
        _packageName.value = packageName
        // Trigger data enrichment
        viewModelScope.launch {
            repository.fetchFullAppDetails(packageName)
        }
    }

    fun setComponentType(type: AppManagerRepository.ComponentType) {
        _selectedComponentType.value = type
    }

    fun toggleComponent(componentName: String, currentEnabled: Boolean) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.toggleComponent(pkg, componentName, !currentEnabled)
            _isProcessing.value = false
        }
    }

    fun togglePermission(permission: String, currentGranted: Boolean) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.togglePermission(pkg, permission, !currentGranted)
            _isProcessing.value = false
        }
    }

    fun setBatteryOptimization(optimize: Boolean) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.setBatteryOptimization(pkg, optimize)
            _isProcessing.value = false
        }
    }

    fun setOverlayPermission(allow: Boolean) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.setOverlayPermission(pkg, allow)
            _isProcessing.value = false
        }
    }

    fun forceStopApp() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.forceStopApp(pkg)
            _isProcessing.value = false
        }
    }

    fun clearAppCache() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.clearAppCache(pkg)
            _isProcessing.value = false
        }
    }

    fun clearAppData() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.clearAppData(pkg)
            _isProcessing.value = false
        }
    }

    fun toggleAppEnable() {
        val currentApp = app.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            if (currentApp.isEnabled) {
                repository.disableApp(currentApp.packageName)
            } else {
                repository.enableApp(currentApp.packageName)
            }
            _isProcessing.value = false
        }
    }

    fun requestUninstall() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            repository.requestUninstall(pkg)
        }
    }

    fun reinstallApp() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.reinstallApp(pkg)
            _isProcessing.value = false
        }
    }

    // Gamer Power Tools placeholder methods
    fun uninstallViaAdb() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isProcessing.value = true
            repository.uninstallViaAdb(pkg)
            _isProcessing.value = false
        }
    }
}
