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
import com.mustakim.bokbok.model.RecordingProfile
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ScreenRecordViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recordingRepository: com.mustakim.bokbok.data.repository.RecordingRepository,
    private val modelRepository: com.mustakim.bokbok.data.repository.ModelRepository,
    private val preferencesManager: com.mustakim.bokbok.data.local.PreferencesManager
) : ViewModel() {

    private val _isServiceBound = MutableStateFlow(false)
    val isServiceBound = _isServiceBound.asStateFlow()

    private var screenRecordServiceRef: java.lang.ref.WeakReference<ScreenRecordService>? = null
    private var isBound = false
        set(value) {
            field = value
            _isServiceBound.value = value
        }

    val pendingRecordings = recordingRepository.getPendingRecordings()
    val recorderSettings = preferencesManager.recorderSettings

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()
    
    private val _installedApps = MutableStateFlow<List<android.content.pm.PackageInfo>>(emptyList())
    val installedApps = _installedApps.asStateFlow()

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

    private val _processingProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val processingProgress = _processingProgress.asStateFlow()

    private val _audioLevels = MutableStateFlow(floatArrayOf(0f, 0f, 0f, 0f))
    val audioLevels = _audioLevels.asStateFlow()

    // Remote Models
    val modelState = modelRepository.modelState
    val modelDownloadProgress = modelRepository.downloadProgress

    private var pendingStartIntent: Intent? = null
    private var pendingResultCode: Int = 0

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
            viewModelScope.launch {
                serviceInstance.processingProgress.collect { _processingProgress.value = it }
            }
            viewModelScope.launch {
                serviceInstance.audioLevels.collect { _audioLevels.value = it }
            }
            
            // Handle pending start request
            if (pendingStartIntent != null) {
                serviceInstance.startRecording(pendingResultCode, pendingStartIntent!!, _config.value)
                pendingStartIntent = null
                pendingResultCode = 0
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
        fetchInstalledApps()
        
        // Load persistent config
        viewModelScope.launch {
            preferencesManager.recordConfig.collect { savedConfig ->
                _config.value = savedConfig
            }
        }
        
        // Load Wi-Fi share settings
        viewModelScope.launch { 
            recorderSettings.collect { settings ->
                isWifiPasswordRequired = settings["wifiShareRequirePassword"] as? Boolean ?: false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopWifiShare()
        if (isBound) {
            try {
                context.unbindService(connection)
            } catch (_: Exception) {}
            isBound = false
        }
    }

    private fun fetchInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val apps = pm.getInstalledPackages(0).filter { 
                    pm.getLaunchIntentForPackage(it.packageName) != null 
                }.sortedBy { it.applicationInfo?.loadLabel(pm).toString() }
                _installedApps.value = apps
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch apps")
            }
        }
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
        
        // Start the service explicitly
        val intent = Intent(context, ScreenRecordService::class.java)
        context.startForegroundService(intent)
        
        if (isBound && screenRecordServiceRef?.get() != null) {
            screenRecordServiceRef?.get()?.startRecording(resultCode, data, _config.value)
        } else {
            // Queue the start command
            pendingResultCode = resultCode
            pendingStartIntent = data
            // Ensure we are bound (idempotent)
            bindToService()
        }
    }

    fun stopRecording() {
        screenRecordServiceRef?.get()?.stopRecording()
    }

    fun processRecording(id: Long) {
        val micRatio = _config.value.micAudioRatio
        val intRatio = _config.value.internalAudioRatio
        screenRecordServiceRef?.get()?.processHistoricalRecording(id, micRatio, intRatio)
    }

    fun pauseRecording() {
        screenRecordServiceRef?.get()?.pauseRecording()
    }

    fun resumeRecording() {
        screenRecordServiceRef?.get()?.resumeRecording()
    }

    fun updateConfig(newConfig: RecordConfig) {
        // If any core setting is changed manually (not via profile), set profile to CUSTOM
        val finalConfig = if (newConfig.profile != _config.value.profile) {
            // Profile changed -> Apply profile settings
            applyProfileToConfig(newConfig, newConfig.profile)
        } else if (isConfigCustomized(newConfig, _config.value)) {
            newConfig.copy(profile = RecordingProfile.CUSTOM)
        } else {
            newConfig
        }

        _config.value = finalConfig
        viewModelScope.launch {
            preferencesManager.saveRecordConfig(finalConfig)
        }
    }

    private fun applyProfileToConfig(config: RecordConfig, profile: RecordingProfile): RecordConfig {
        if (profile == RecordingProfile.CUSTOM) return config
        
        return config.copy(
            profile = profile,
            resolutionName = profile.resolutionName,
            width = profile.width,
            height = profile.height,
            bitrate = profile.bitrate,
            bitrateName = profile.bitrateName,
            fps = profile.fps,
            useHevc = profile.useHevc,
            includeMic = profile.includeMic,
            includeInternal = profile.includeInternal,
            micAudioRatio = profile.micRatio,
            internalAudioRatio = profile.internalRatio
        )
    }

    private fun isConfigCustomized(new: RecordConfig, old: RecordConfig): Boolean {
        return new.width != old.width ||
               new.height != old.height ||
               new.bitrate != old.bitrate ||
               new.fps != old.fps ||
               new.useHevc != old.useHevc ||
               new.includeMic != old.includeMic ||
               new.includeInternal != old.includeInternal ||
               new.micAudioRatio != old.micAudioRatio ||
               new.internalAudioRatio != old.internalAudioRatio
    }
    
    fun updateMixRatio(mic: Float, internal: Float) {
        updateConfig(_config.value.copy(micAudioRatio = mic, internalAudioRatio = internal))
    }
    
    fun clearError() {
        _errorMessage.value = null
        screenRecordServiceRef?.get()?.clearError()
    }

    fun deleteRecording(id: Long) {
        viewModelScope.launch {
            recordingRepository.deleteRecording(id)
        }
    }

    fun updateRecorderSettings(
        autoProcess: Boolean,
        noiseReduction: Boolean,
        bleedReduction: Boolean,
        qualityMode: Int
    ) {
        viewModelScope.launch {
            preferencesManager.saveRecorderSettings(autoProcess, noiseReduction, bleedReduction, qualityMode)
        }
    }
    
    // Wi-Fi Sharing
    private var wifiServer: com.mustakim.bokbok.data.service.WifiShareServer? = null
    
    private val _wifiIpAddress = MutableStateFlow<String?>(null)
    val wifiIpAddress = _wifiIpAddress.asStateFlow()
    
    private val _wifiPin = MutableStateFlow<String?>(null)
    val wifiPin = _wifiPin.asStateFlow()
    
    // Derived from recorderSettings, cached for startWifiShare
    private var isWifiPasswordRequired = false
    


    fun toggleWifiShare() {
        if (wifiServer != null) {
            stopWifiShare()
        } else {
            startWifiShare()
        }
    }

    private fun startWifiShare() {
        try {
            wifiServer = com.mustakim.bokbok.data.service.WifiShareServer(context, isWifiPasswordRequired)
            wifiServer?.start()
            val ip = wifiServer?.getIpAddress()
            _wifiIpAddress.value = "http://$ip:8080"
            _wifiPin.value = wifiServer?.getPin()
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Wi-Fi share")
            _errorMessage.value = "Failed to start Wi-Fi Server: ${e.message}"
            stopWifiShare()
        }
    }

    private fun stopWifiShare() {
        try {
            wifiServer?.stop()
        } catch (e: Exception) {
            Timber.e(e, "Error stopping Wi-Fi share")
        }
        wifiServer = null
        _wifiIpAddress.value = null
        _wifiPin.value = null
    }

    fun downloadModels() {
        viewModelScope.launch {
            modelRepository.downloadModels()
        }
    }
    
    fun deleteModels() {
        modelRepository.deleteModels()
    }

    fun toggleWifiSharePasswordRequired() {
        viewModelScope.launch {
            preferencesManager.saveWifiSharePasswordRequired(!isWifiPasswordRequired)
        }
    }

    val allRecordings = recordingRepository.getAllRecordings()

    // ============= Custom Profiles =============
    val customProfiles = preferencesManager.customProfiles

    fun saveCurrentAsProfile(name: String) {
        viewModelScope.launch {
            val profile = com.mustakim.bokbok.model.CustomRecordingProfile.fromConfig(name, _config.value)
            preferencesManager.saveCustomProfile(profile)
        }
    }

    fun loadCustomProfile(profile: com.mustakim.bokbok.model.CustomRecordingProfile) {
        updateConfig(profile.applyTo(_config.value))
    }

    fun deleteCustomProfile(name: String) {
        viewModelScope.launch {
            preferencesManager.deleteCustomProfile(name)
        }
    }
}

