package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class GameBoostTab(val title: String) {
    APP_MANAGER("App Manager"),
    DEVICE_MONITOR("Device Monitor"),
    GAME_BOOST("Game Boost"),
    USAGE_STATS("Usage Stats"),
    SCREEN_RECORD("Screen Record");
    
    companion object {
        fun getByIndex(index: Int): GameBoostTab = entries.getOrElse(index) { GAME_BOOST }
    }
}

class GameBoostViewModel : ViewModel() {

    private val _selectedTab = MutableStateFlow(GameBoostTab.GAME_BOOST)
    val selectedTab: StateFlow<GameBoostTab> = _selectedTab.asStateFlow()

    val tabs = GameBoostTab.entries

    fun onTabSelected(tab: GameBoostTab) {
        _selectedTab.value = tab
    }
    
    fun onTabSelected(index: Int) {
        _selectedTab.value = GameBoostTab.getByIndex(index)
    }
}
