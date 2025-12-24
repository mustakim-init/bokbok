package com.mustakim.bokbok.viewmodel

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.service.ScreenRecordService
import com.mustakim.bokbok.ui.screens.gameboost.screenrecord.ScreenRecordPermissions
import com.mustakim.bokbok.model.RecordConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScreenRecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private var screenRecordServiceRef: java.lang.ref.WeakReference<ScreenRecordService>? = null
    private var isBound = false

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()
    
    private val _isCountingDown = MutableStateFlow(false)
    val isCountingDown = _isCountingDown.asStateFlow()
    
    private val _countdownValue = MutableStateFlow(0)
    val countdownValue = _countdownValue.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    private val _lastRecordingPath = MutableStateFlow<String?>(null)
    val lastRecordingPath = _lastRecordingPath.asStateFlow()
    
    private val _permissionsGranted = MutableStateFlow(false)
    val permissionsGranted = _permissionsGranted.asStateFlow()

    private val _config = MutableStateFlow(RecordConfig())
    val config = _config.asStateFlow()

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ScreenRecordService.LocalBinder
            val serviceInstance = binder.getService()
            screenRecordServiceRef = java.lang.ref.WeakReference(serviceInstance)
            isBound = true
            
            // Collect service state
            viewModelScope.launch {
                serviceInstance.isRecording.collect { _isRecording.value = it }
            }
            viewModelScope.launch {
                serviceInstance.isPaused.collect { _isPaused.value = it }
            }
            viewModelScope.launch {
                serviceInstance.isCountingDown.collect { _isCountingDown.value = it }
            }
            viewModelScope.launch {
                serviceInstance.countdownValue.collect { _countdownValue.value = it }
            }
            viewModelScope.launch {
                serviceInstance.errorMessage.collect { _errorMessage.value = it }
            }
            viewModelScope.launch {
                serviceInstance.lastRecordingPath.collect { _lastRecordingPath.value = it }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            screenRecordServiceRef = null
            isBound = false
        }
    }

    init {
        refreshPermissions()
        bindToService()
    }
    
    private fun bindToService() {
        val intent = Intent(context, ScreenRecordService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }
    
    fun refreshPermissions() {
        _permissionsGranted.value = ScreenRecordPermissions.allPermissionsGranted(context)
    }

    fun startRecording(resultCode: Int, data: Intent) {
        if (!_permissionsGranted.value) {
            _errorMessage.value = "Please grant all required permissions first"
            return
        }
        
        // Start the service explicitly before calling startRecording
        val intent = Intent(context, ScreenRecordService::class.java)
        context.startForegroundService(intent)
        
        screenRecordServiceRef?.get()?.startRecording(resultCode, data, _config.value)
    }

    fun stopRecording() {
        screenRecordServiceRef?.get()?.stopRecording()
    }

    fun pauseRecording() {
        screenRecordServiceRef?.get()?.pauseRecording()
    }

    fun resumeRecording() {
        screenRecordServiceRef?.get()?.resumeRecording()
    }

    fun updateConfig(newConfig: RecordConfig) {
        _config.value = newConfig
    }
    
    fun clearError() {
        _errorMessage.value = null
        screenRecordServiceRef?.get()?.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            context.unbindService(connection)
            isBound = false
        }
    }
}
