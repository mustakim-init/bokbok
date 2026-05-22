package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.local.PreferencesManager
import com.mustakim.bokbok.ui.theme.AppTheme
import com.mustakim.bokbok.ui.theme.DarkMode
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

    private val _selectedTheme = MutableStateFlow(
        preferencesManager.getImmediate(PreferencesManager.THEME_KEY)?.let { 
            try { AppTheme.valueOf(it) } catch (e: Exception) { AppTheme.MATERIAL_CLASSIC }
        } ?: AppTheme.MATERIAL_CLASSIC
    )
    val selectedTheme: StateFlow<AppTheme> = _selectedTheme.asStateFlow()

    private val _themeColorInt = MutableStateFlow<Int?>(preferencesManager.getImmediate(PreferencesManager.THEME_COLOR_KEY))
    val themeColorInt: StateFlow<Int?> = _themeColorInt.asStateFlow()

    private val _pureBlack = MutableStateFlow(preferencesManager.getImmediate(PreferencesManager.PURE_BLACK_KEY) ?: false)
    val pureBlack: StateFlow<Boolean> = _pureBlack.asStateFlow()

    private val _themeSeedPalette = MutableStateFlow<String?>(preferencesManager.getImmediate(PreferencesManager.THEME_SEED_PALETTE_KEY))
    val themeSeedPalette: StateFlow<String?> = _themeSeedPalette.asStateFlow()

    private val _darkMode = MutableStateFlow(
        preferencesManager.getImmediate(PreferencesManager.DARK_MODE_KEY)?.let {
            try { DarkMode.valueOf(it) } catch (e: Exception) { DarkMode.AUTO }
        } ?: DarkMode.AUTO
    )
    val darkMode: StateFlow<DarkMode> = _darkMode.asStateFlow()

    private val _useSystemFont = MutableStateFlow(preferencesManager.getImmediate(PreferencesManager.USE_SYSTEM_FONT_KEY) ?: false)
    val useSystemFont: StateFlow<Boolean> = _useSystemFont.asStateFlow()

    private val _disableBlur = MutableStateFlow(preferencesManager.getImmediate(PreferencesManager.DISABLE_BLUR_KEY) ?: false)
    val disableBlur: StateFlow<Boolean> = _disableBlur.asStateFlow()

    private val _blurRadius = MutableStateFlow(preferencesManager.getImmediate(PreferencesManager.BLUR_RADIUS_KEY) ?: 36f)
    val blurRadius: StateFlow<Float> = _blurRadius.asStateFlow()

    private val _dynamicThemeEnabled = MutableStateFlow(preferencesManager.getImmediate(PreferencesManager.DYNAMIC_THEME_KEY) ?: true)
    val dynamicThemeEnabled: StateFlow<Boolean> = _dynamicThemeEnabled.asStateFlow()

    private val _dynamicThemeColor = MutableStateFlow<Int?>(null)
    val dynamicThemeColor: StateFlow<Int?> = _dynamicThemeColor.asStateFlow()

    init {
        loadTheme()
    }

    private fun loadTheme() {
        viewModelScope.launch {
            preferencesManager.selectedTheme.collect { theme ->
                _selectedTheme.value = theme
            }
        }
        viewModelScope.launch {
            preferencesManager.themeColor.collect { color ->
                _themeColorInt.value = color
            }
        }
        viewModelScope.launch {
            preferencesManager.pureBlack.collect { enabled ->
                _pureBlack.value = enabled
            }
        }
        viewModelScope.launch {
            preferencesManager.themeSeedPalette.collect { palette ->
                _themeSeedPalette.value = palette
            }
        }
        viewModelScope.launch {
            preferencesManager.darkMode.collect { mode ->
                _darkMode.value = mode
            }
        }
        viewModelScope.launch {
            preferencesManager.useSystemFont.collect { enabled ->
                _useSystemFont.value = enabled
            }
        }
        viewModelScope.launch {
            preferencesManager.disableBlur.collect { enabled ->
                _disableBlur.value = enabled
            }
        }
        viewModelScope.launch {
            preferencesManager.blurRadius.collect { radius ->
                _blurRadius.value = radius
            }
        }
        viewModelScope.launch {
            preferencesManager.dynamicThemeEnabled.collect { enabled ->
                _dynamicThemeEnabled.value = enabled
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferencesManager.saveTheme(theme)
            _selectedTheme.value = theme
        }
    }

    fun setThemeColor(color: Int) {
        viewModelScope.launch {
            preferencesManager.saveThemeColor(color)
            _themeColorInt.value = color
        }
    }

    fun setPureBlack(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.savePureBlack(enabled)
            _pureBlack.value = enabled
        }
    }

    fun setThemeSeedPalette(paletteJson: String?) {
        viewModelScope.launch {
            preferencesManager.saveThemeSeedPalette(paletteJson)
            _themeSeedPalette.value = paletteJson
        }
    }

    fun setDarkMode(mode: DarkMode) {
        viewModelScope.launch {
            preferencesManager.saveDarkMode(mode)
            _darkMode.value = mode
        }
    }

    fun setUseSystemFont(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveUseSystemFont(enabled)
            _useSystemFont.value = enabled
        }
    }

    fun setDisableBlur(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveDisableBlur(enabled)
            _disableBlur.value = enabled
        }
    }

    fun setBlurRadius(radius: Float) {
        viewModelScope.launch {
            preferencesManager.saveBlurRadius(radius)
            _blurRadius.value = radius
        }
    }

    fun setDynamicThemeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.saveDynamicThemeEnabled(enabled)
            _dynamicThemeEnabled.value = enabled
        }
    }

    fun updateDynamicThemeColor(color: Int?) {
        _dynamicThemeColor.value = color
    }
}
