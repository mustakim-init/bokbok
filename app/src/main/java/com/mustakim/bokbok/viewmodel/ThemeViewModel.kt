package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.local.PreferencesManager
import com.mustakim.bokbok.ui.theme.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ThemeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    private val _selectedTheme = MutableStateFlow(AppTheme.MATERIAL_CLASSIC)
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    init {
        loadTheme()
    }

    private fun loadTheme() {
        viewModelScope.launch {
            preferencesManager.selectedTheme.collect { theme ->
                _selectedTheme.value = theme
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferencesManager.saveTheme(theme)
            _selectedTheme.value = theme
        }
    }
}
