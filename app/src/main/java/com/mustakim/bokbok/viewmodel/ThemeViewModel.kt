package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.local.PreferencesManager
import com.mustakim.bokbok.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

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
