package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.GameItem
import com.mustakim.bokbok.data.repository.GameRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GameSpaceViewModel(application: Application) : AndroidViewModel(application) {
    val repository = GameRepository(application)

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

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
        _selectedPackageName.value = game.packageName
    }

    fun clearSelectedGame() {
        _selectedPackageName.value = null
    }

    fun launchGame(game: GameItem) {
        viewModelScope.launch {
            repository.launchGame(game.packageName)
        }
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

    fun removeFromGameList(game: GameItem) {
        viewModelScope.launch {
            repository.removeFromGameList(game.packageName)
        }
    }
}
