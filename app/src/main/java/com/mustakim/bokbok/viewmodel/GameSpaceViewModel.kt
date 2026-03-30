package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.GameItem
import com.mustakim.bokbok.data.repository.GameRepository
import com.mustakim.bokbok.data.local.dao.AppDao
import com.mustakim.bokbok.data.local.entity.AppEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import rikka.shizuku.Shizuku
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.content.pm.PackageManager

enum class LaunchState {
    NONE, OPTIMIZING, LAUNCHING
}

@HiltViewModel
class GameSpaceViewModel @Inject constructor(
    private val repository: GameRepository,
    private val application: Application,
    private val appDao: com.mustakim.bokbok.data.local.dao.AppDao
) : androidx.lifecycle.ViewModel() {

    private val _launchState = MutableStateFlow(LaunchState.NONE)
    val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

    private val _isCompiling = MutableStateFlow(false)
    val isCompiling: StateFlow<Boolean> = _isCompiling.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGames = MutableStateFlow<Set<String>>(emptySet())
    val selectedGames: StateFlow<Set<String>> = _selectedGames.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    private val _addableApps = MutableStateFlow<List<AddableApp>>(emptyList())
    val addableApps: StateFlow<List<AddableApp>> = _addableApps.asStateFlow()

    private val _shizukuActive = MutableStateFlow(false)
    val shizukuActive: StateFlow<Boolean> = _shizukuActive.asStateFlow()

    // 🚀 PERFORMANCE: Track if initial data has already been loaded
    private var _hasLoadedInitialData = false

    init {
        verifyShizukuStatus()
        // Recovery Logic: If the app starts and find snapshots but the service isn't running,
        // it means we had a "messy" exit previously. Clean up.
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.hasActiveSnapshots()) {
                repository.revertAllOptimizations()
            }
        }
    }

    // 🚀 PERFORMANCE: Called from UI when tab is settled, prevents redundant loads
    fun loadDataIfNeeded() {
        verifyShizukuStatus()
        if (_hasLoadedInitialData) return
        _hasLoadedInitialData = true
        // Data is loaded via Flow subscription, just mark as loaded
    }

    fun verifyShizukuStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val isActive = try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
            _shizukuActive.value = isActive
        }
    }

    // 🚀 PERFORMANCE: Use SharingStarted.Lazily to defer flow collection until first subscriber
    val games: StateFlow<List<GameItem>> = repository.getGames()
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val filteredGames: StateFlow<List<GameItem>> = combine(games, _searchQuery) { currentGames, query ->
        if (query.isBlank()) currentGames
        else currentGames.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }


    fun toggleGameSelection(packageName: String) {
        val current = _selectedGames.value
        if (current.contains(packageName)) {
            _selectedGames.value = current - packageName
            if (_selectedGames.value.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            _selectedGames.value = current + packageName
            _isSelectionMode.value = true
        }
    }

    fun clearSelection() {
        _selectedGames.value = emptySet()
        _isSelectionMode.value = false
    }

    fun enterSelectionMode(firstSelectedPackage: String) {
        _selectedGames.value = setOf(firstSelectedPackage)
        _isSelectionMode.value = true
    }


    fun launchGameWithOptimizations(game: GameItem) {
        viewModelScope.launch {
            try {
                _launchState.value = LaunchState.OPTIMIZING
                
                // 1. Delegate optimization to repository (handles JSON parsing and Shell commands on IO)
                withContext(Dispatchers.IO) {
                    repository.applyOptimizations(game.packageName)
                }
                
                // 2. Final Launch
                _launchState.value = LaunchState.LAUNCHING
                
                repository.launchGame(game.packageName)
                
            } catch (e: Exception) {
                android.util.Log.e("GameSpaceViewModel", "Launch failed, reverting", e)
                withContext(Dispatchers.IO) {
                    repository.revertAllOptimizations()
                }
            } finally {
                delay(1500)
                _launchState.value = LaunchState.NONE
            }
        }
    }

    fun updateCustomTweak(packageName: String, tweakId: String, value: String) {
        viewModelScope.launch {
            repository.updateGameEntity(packageName) { current ->
                val json = JSONObject(current.customSettingsJson)
                json.put(tweakId, value)
                current.copy(customSettingsJson = json.toString())
            }
        }
    }

    fun removeSelectedGames() {
        viewModelScope.launch(Dispatchers.IO) {
            val toRemove = _selectedGames.value.toList()
            val successfullyRestored = mutableListOf<String>()
            
            // 1. Restore launcher state for all (Shell commands)
            toRemove.forEach { pkg ->
                try {
                    val success = repository.showInLauncher(pkg)
                    if (success) {
                        successfullyRestored.add(pkg)
                    } else {
                        android.util.Log.w("GameSpaceViewModel", "Failed to restore launcher for $pkg, keeping in list for safety.")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("GameSpaceViewModel", "Error unhiding $pkg", e)
                }
            }
            
            // 2. Only mark as removed in DB if unhiding succeeded (or if it wasn't hidden)
            if (successfullyRestored.isNotEmpty()) {
                repository.batchUpdateGames(successfullyRestored) { it.copy(isManuallyRemoved = true) }
            }
            
            withContext(Dispatchers.Main) {
                clearSelection()
            }
        }
    }


    fun launchGame(game: GameItem) {
        launchGameWithOptimizations(game)
    }

    fun toggleLauncherVisibility(game: GameItem) {
        viewModelScope.launch {
            if (game.isHiddenFromLauncher) {
                repository.showInLauncher(game.packageName)
            } else {
                repository.hideFromLauncher(game.packageName)
            }
        }
    }

    fun preOptimizeGame(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (_isCompiling.value) return@launch
            _isCompiling.value = true
            try {
                repository.compileApp(packageName, com.mustakim.bokbok.data.model.CompileMode.SPEED.value)
            } catch (e: Exception) {
                android.util.Log.e("GameSpaceViewModel", "Manual pre-optimization failed", e)
            } finally {
                _isCompiling.value = false
            }
        }
    }

    fun addGameManually(packageName: String) {
        viewModelScope.launch {
            repository.addGameManually(packageName)
        }
    }

    fun fetchAddableApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val apps = appDao.getAppsOneShot()
                .filter { it.isInstalled && (!it.isSystemApp || it.isUserApp) }
                .map { AddableApp(it.packageName, it.label) }
                .sortedBy { it.label.lowercase() }
            _addableApps.value = apps
        }
    }

    data class AddableApp(val packageName: String, val label: String)
}
