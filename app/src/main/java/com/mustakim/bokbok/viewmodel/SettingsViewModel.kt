package com.mustakim.bokbok.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.local.PreferencesManager
import com.mustakim.bokbok.data.shell.DaemonManager
import com.mustakim.bokbok.data.service.GameWatchdogService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val daemonManager: DaemonManager
) : ViewModel() {

    val watchdogEnabled: StateFlow<Boolean> = preferencesManager.watchdogEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val heartbeatEnabled: StateFlow<Boolean> = preferencesManager.heartbeatEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val overlayEnabled: StateFlow<Boolean> = preferencesManager.overlayEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val crashReportEnabled: StateFlow<Boolean> = preferencesManager.crashReportEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleWatchdog(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setWatchdogEnabled(enabled)
            if (enabled) {
                GameWatchdogService.start(context)
            } else {
                context.stopService(Intent(context, GameWatchdogService::class.java))
            }
        }
    }

    fun toggleHeartbeat(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setHeartbeatEnabled(enabled)
            if (enabled) {
                daemonManager.deployAndStart()
            } else {
                daemonManager.stopDaemon()
            }
        }
    }

    fun toggleOverlay(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setOverlayEnabled(enabled)
            // Overlay service start/stop logic if applicable
        }
    }

    fun toggleCrashReport(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setCrashReportEnabled(enabled)
        }
    }
}
