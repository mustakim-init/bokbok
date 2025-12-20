package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.GameItem
import com.mustakim.bokbok.data.model.OptimizationProfile
import com.mustakim.bokbok.data.repository.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject

enum class LaunchState {
    NONE, OPTIMIZING, COMPILING, LAUNCHING
}

class GameSpaceViewModel(application: Application) : AndroidViewModel(application) {
    val repository = GameRepository(application)

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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
            _launchState.value = LaunchState.OPTIMIZING
            // 1. Get settings and profile
            val entity = repository.getGames().first().find { it.packageName == game.packageName }
            val profile = entity?.optimizationProfile ?: OptimizationProfile.BALANCED
            val customJson = entity?.customSettingsJson ?: "{}"
            val json = try { JSONObject(customJson) } catch (_: Exception) { JSONObject() }

            // 2. Start monitoring service IMMEDIATELY
            com.mustakim.bokbok.data.service.GameMonitorService.start(getApplication(), game.packageName)
            delay(500)

            // 3. Kill background apps
            val shouldKillApps = profile == OptimizationProfile.PERFORMANCE || json.optString("kill_bg_apps") == "true"
            if (shouldKillApps) {
                repository.killBackgroundApps()
            }
            
            // 4. Apply Tweaks
            when (profile) {
                OptimizationProfile.PERFORMANCE -> {
                    repository.applyOptimization("window_animation_scale", "0.5")
                    repository.applyOptimization("transition_animation_scale", "0.5")
                    repository.applyOptimization("animator_duration_scale", "0.5")
                    repository.applyOptimization("force_gpu_rendering", "true")
                    
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
                            repository.applyOptimization(key, value)
                        }
                    }
                }
                else -> {}
            }
            
            // 5. Final Launch
            _launchState.value = LaunchState.LAUNCHING
            repository.launchGame(game.packageName)
            
            // Reset state after a delay to ensure UI reflects launch
            delay(1000)
            _launchState.value = LaunchState.NONE
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
        viewModelScope.launch {
            _selectedGames.value.forEach { pkg ->
                // Restore from launcher if it was hidden
                repository.showInLauncher(pkg)
                repository.removeFromGameList(pkg)
            }
            clearSelection()
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
