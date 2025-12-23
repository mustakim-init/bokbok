package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.GameItem
import com.mustakim.bokbok.data.model.OptimizationProfile
import com.mustakim.bokbok.data.repository.GameRepository
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
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

enum class LaunchState {
    NONE, OPTIMIZING, COMPILING, LAUNCHING
}

@HiltViewModel
class GameSpaceViewModel @Inject constructor(
    private val repository: GameRepository,
    private val application: Application // Inject Application context for services
) : androidx.lifecycle.ViewModel() {

    private val _launchState = MutableStateFlow(LaunchState.NONE)
    val launchState: StateFlow<LaunchState> = _launchState.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedGames = MutableStateFlow<Set<String>>(emptySet())
    val selectedGames: StateFlow<Set<String>> = _selectedGames.asStateFlow()

    private val _isSelectionMode = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _isSelectionMode.asStateFlow()

    init {
        // Recovery Logic: If the app starts and find snapshots but the service isn't running,
        // it means we had a "messy" exit previously. Clean up.
        viewModelScope.launch(Dispatchers.IO) {
            if (repository.hasActiveSnapshots()) {
                repository.revertAllOptimizations()
            }
        }
    }

    val games: StateFlow<List<GameItem>> = repository.getGames()
        .onEach { _isLoading.value = false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredGames: StateFlow<List<GameItem>> = combine(games, _searchQuery) { currentGames, query ->
        if (query.isBlank()) currentGames
        else currentGames.filter { it.label.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPackageName = MutableStateFlow<String?>(null)
    
    val selectedGame: StateFlow<GameItem?> = combine(games, _selectedPackageName) { currentGames, pkgName ->
        if (pkgName == null) null
        else currentGames.find { it.packageName == pkgName }
    }.onEach { if (it == null) _selectedPackageName.value = null } // Clear if game disappears from list
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun selectGame(game: GameItem) {
        if (_isSelectionMode.value) {
            toggleGameSelection(game.packageName)
        } else {
            _selectedPackageName.value = game.packageName
        }
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

    fun clearSelectedGame() {
        _selectedPackageName.value = null
    }

    fun launchGameWithOptimizations(game: GameItem) {
        viewModelScope.launch {
            try {
                _launchState.value = LaunchState.OPTIMIZING
                
                // 1. Get profile and custom JSON
                val profile = game.optimizationProfile
                val customJson = game.customSettingsJson
                val json = try { JSONObject(customJson) } catch (_: Exception) { JSONObject() }

                // 2. Apply Tweaks on IO thread to ensure absolute UI smoothness (Profile Threading)
                withContext(Dispatchers.IO) {
                    // Kill background apps first if needed
                    val shouldKillApps = profile == OptimizationProfile.PERFORMANCE || json.optString("kill_bg_apps") == "true"
                    if (shouldKillApps) {
                        repository.killBackgroundApps()
                    }

                    when (profile) {
                        OptimizationProfile.PERFORMANCE -> {
                            repository.applyOptimization("window_animation_scale", "0.25", game.packageName)
                            repository.applyOptimization("transition_animation_scale", "0.25", game.packageName)
                            repository.applyOptimization("animator_duration_scale", "0.25", game.packageName)
                            repository.applyOptimization("force_gpu_rendering", "true", game.packageName)
                            repository.applyOptimization("native_game_mode", "true", game.packageName)
                            repository.applyOptimization("app_standby_active", "true", game.packageName)
                            
                            _launchState.value = LaunchState.COMPILING
                            repository.compileApp(game.packageName, com.mustakim.bokbok.data.model.CompileMode.SPEED.value)
                        }
                        OptimizationProfile.CUSTOM -> {
                            val keys = json.keys()
                            while(keys.hasNext()) {
                                val key = keys.next()
                                val value = json.getString(key)
                                if (key == "compile_speed" && value == "true") {
                                    _launchState.value = LaunchState.COMPILING
                                    repository.compileApp(game.packageName, com.mustakim.bokbok.data.model.CompileMode.SPEED.value)
                                    _launchState.value = LaunchState.OPTIMIZING
                                } else {
                                    repository.applyOptimization(key, value, game.packageName)
                                }
                            }
                        }
                        else -> {}
                    }
                }
                
                // 3. Final Launch
                _launchState.value = LaunchState.LAUNCHING
                
                // START MONITORING SERVICE BEFORE LAUNCH
                // This ensures the service is ready to catch the PID as soon as the app starts.
                com.mustakim.bokbok.data.service.GameMonitorService.start(application, game.packageName)
                
                repository.launchGame(game.packageName)
                
            } catch (e: Exception) {
                android.util.Log.e("GameSpaceViewModel", "Launch failed, reverting", e)
                // If anything fails during launch, REVERT immediately
                withContext(Dispatchers.IO) {
                    repository.revertAllOptimizations()
                    com.mustakim.bokbok.data.service.GameMonitorService.stop(application)
                }
            } finally {
                // Reset state after a delay to ensure UI reflects launch
                delay(1500)
                _launchState.value = LaunchState.NONE
            }
        }
    }

    fun updateGameProfile(packageName: String, profile: OptimizationProfile) {
        viewModelScope.launch {
            repository.updateGameEntity(packageName) { it.copy(optimizationProfile = profile) }
        }
    }

    fun updateCustomTweak(packageName: String, tweakId: String, value: String) {
        viewModelScope.launch {
            repository.updateGameEntity(packageName) { current ->
                val json = JSONObject(current.customSettingsJson)
                json.put(tweakId, value)
                current.copy(customSettingsJson = json.toString(), optimizationProfile = OptimizationProfile.CUSTOM)
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

    fun addGameManually(packageName: String) {
        viewModelScope.launch {
            repository.addGameManually(packageName)
        }
    }
}
