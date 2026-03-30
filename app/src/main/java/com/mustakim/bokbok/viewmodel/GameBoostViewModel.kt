package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import rikka.shizuku.Shizuku
import javax.inject.Inject

enum class GameBoostTab(val title: String) {
    DASHBOARD("Optimizer"),
    GAME_BOOST("Game Boost"),
    APP_MANAGER("App Manager"),
    DEVICE_MONITOR("Device Monitor"),
    USAGE_STATS("Usage Stats"),
    SCREEN_RECORD("Screen Record"),
    SECURITY("Security");
    
    companion object {
        fun getByIndex(index: Int): GameBoostTab = entries.getOrElse(index) { DASHBOARD }
    }
}



@HiltViewModel
class GameBoostViewModel @Inject constructor() : ViewModel() {

    private val _selectedTab = MutableStateFlow(GameBoostTab.DASHBOARD)
    val selectedTab: StateFlow<GameBoostTab> = _selectedTab.asStateFlow()

    val tabs = GameBoostTab.entries

    private val _shizukuActive = MutableStateFlow(false)
    val shizukuActive: StateFlow<Boolean> = _shizukuActive.asStateFlow()

    private val _showShizukuWarning = MutableStateFlow(false)
    val showShizukuWarning: StateFlow<Boolean> = _showShizukuWarning.asStateFlow()

    companion object {
        private var hasShownSessionWarning = false
    }

    init {
        verifyShizukuStatus()
    }

    fun dismissShizukuWarning() {
        _showShizukuWarning.value = false
        hasShownSessionWarning = true
    }

    fun onTabSelected(tab: GameBoostTab) {
        _selectedTab.value = tab
        verifyShizukuStatus()
    }
    
    fun onTabSelected(index: Int) {
        _selectedTab.value = GameBoostTab.getByIndex(index)
        verifyShizukuStatus()
    }

    fun verifyShizukuStatus() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val isActive = try {
                Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
            _shizukuActive.value = isActive
            
            // Automatically show warning if not active and haven't shown it yet in this session
            if (!isActive && !hasShownSessionWarning) {
                _showShizukuWarning.value = true
            }
        }
    }
}
