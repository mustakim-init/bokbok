package com.mustakim.bokbok.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.media.ImageReader
import android.graphics.PixelFormat
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.mustakim.bokbok.MainActivity
import com.mustakim.bokbok.R
import dagger.hilt.android.AndroidEntryPoint
import com.mustakim.bokbok.model.RecordConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import android.hardware.display.DisplayManager
import android.view.Display
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.graphics.Bitmap
import com.mustakim.bokbok.ui.screens.gameboost.screenrecord.FacecamOverlay
import com.mustakim.bokbok.ui.screens.gameboost.screenrecord.WatermarkOverlay

@AndroidEntryPoint
class ScreenRecordService : Service() {

    companion object {
        const val NOTIFICATION_ID = 1337
        const val CHANNEL_ID = "screen_record_service"
        const val CHANNEL_CAPTURED = "screen_record_captured"
        
        const val ACTION_STOP = "com.mustakim.bokbok.STOP"
        const val ACTION_PAUSE = "com.mustakim.bokbok.PAUSE"
        const val ACTION_RESUME = "com.mustakim.bokbok.RESUME"
        
        // Static state for Quick Settings Tile and Widget
        @Volatile var isRecordingActive = false
            private set
        @Volatile var isPausedState = false
            private set

        private const val AUDIO_LEVEL_POLL_MS = 100L
        private const val SHAKE_THRESHOLD_DIVISOR = 2f
    }

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private val nativeRecorder = NativeRecorder()
    private val projectionManager by lazy { getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }
    private lateinit var audioCaptureManager: AudioCaptureManager
    
    @Inject lateinit var notificationRepository: com.mustakim.bokbok.data.repository.NotificationRepository
    @Inject lateinit var recordingRepository: com.mustakim.bokbok.data.repository.RecordingRepository
    @Inject lateinit var modelRepository: com.mustakim.bokbok.data.repository.ModelRepository
    @Inject lateinit var preferencesManager: com.mustakim.bokbok.data.local.PreferencesManager

    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var recordingOverlay: com.mustakim.bokbok.ui.screens.gameboost.screenrecord.RecordingOverlay? = null
    private var facecamOverlay: com.mustakim.bokbok.ui.screens.gameboost.screenrecord.FacecamOverlay? = null
    private var textWatermarkOverlay: WatermarkOverlay? = null
    private var imageWatermarkOverlay: WatermarkOverlay? = null
    private var currentConfig: RecordConfig? = null
    private var displayManager: DisplayManager? = null
    
    // Auto-stop monitoring
    private val autoStopHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var batteryReceiver: android.content.BroadcastReceiver? = null
    private var screenOffReceiver: BroadcastReceiver? = null
    private var sensorManager: SensorManager? = null
    private var shakeListener: SensorEventListener? = null
    
    private var recordStartTime: Long = 0
    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused = _isPaused.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    private val _lastRecordingPath = MutableStateFlow<String?>(null)
    val lastRecordingPath = _lastRecordingPath.asStateFlow()

    // MediaProjection callback to handle system-initiated termination
    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Timber.w("MediaProjection stopped by system")
            stopRecording()
        }
    }

    private val _isCountingDown = MutableStateFlow(false)
    val isCountingDown = _isCountingDown.asStateFlow()
    
    private val _countdownValue = MutableStateFlow(0)
    val countdownValue = _countdownValue.asStateFlow()

    private val _processingProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
    val processingProgress = _processingProgress.asStateFlow()

    // DisplayListener for orientation changes
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == Display.DEFAULT_DISPLAY && _isRecording.value && !_isPaused.value) {
                handleOrientationChange()
            }
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordService = this@ScreenRecordService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioCaptureManager = AudioCaptureManager(this, nativeRecorder)
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    fun startRecording(resultCode: Int, data: Intent, config: RecordConfig) {
        if (_isRecording.value || _isCountingDown.value) return
        _errorMessage.value = null

        if (config.useCountdown) {
            kotlinx.coroutines.MainScope().launch {
                _isCountingDown.value = true
                for (i in 3 downTo 1) {
                    _countdownValue.value = i
                    kotlinx.coroutines.delay(1000)
                }
                _isCountingDown.value = false
                performStartRecording(resultCode, data, config)
            }
        } else {
            performStartRecording(resultCode, data, config)
        }
    }

    private fun performStartRecording(resultCode: Int, data: Intent, config: RecordConfig) {
        try {
            // 0. Start Foreground Service FIRST (Required for API 34+)
            val notification = createNotification("Preparing for recording...", showActions = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+: Must declare both microphone AND mediaProjection
                startForeground(
                    NOTIFICATION_ID, 
                    notification, 
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            // Adjust dimensions based on Orientation Lock
            var finalWidth = config.width
            var finalHeight = config.height
            
            if (config.orientationLock == "Portrait" && finalWidth > finalHeight) {
                finalWidth = config.height
                finalHeight = config.width
            } else if (config.orientationLock == "Landscape" && finalWidth < finalHeight) {
                finalWidth = config.height
                finalHeight = config.width
            } else if (config.orientationLock == "Auto") {
                 val metrics = resources.displayMetrics
                 val isDeviceLandscape = metrics.widthPixels > metrics.heightPixels
                 val isConfigLandscape = finalWidth > finalHeight
                 if (isDeviceLandscape != isConfigLandscape) {
                     finalWidth = config.height
                     finalHeight = config.width
                 }
            }
            
            // Create effective config
            val effectiveConfig = config.copy(width = finalWidth, height = finalHeight)
            currentConfig = effectiveConfig
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                handleError("Failed to obtain screen capture permission")
                return
            }

            // Register callback for system-initiated stop
            mediaProjection?.registerCallback(projectionCallback, null)

            // Store config for orientation handling
            currentConfig = effectiveConfig

            // Register display listener for orientation changes
            displayManager?.registerDisplayListener(displayListener, null)

            // 1. Setup NDK Engine - Save to PUBLIC directory for Gallery visibility
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val bokbokDir = File(moviesDir, "BokBok").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFile = File(bokbokDir, "BokBok_$timestamp.mp4").absolutePath
            _lastRecordingPath.value = outputFile
            
            // Use app-specific external storage for raw files to ensure native write access
            val rawDir = getExternalFilesDir("raw_recordings")?.apply { mkdirs() }
            val micPath = File(rawDir, "BokBok_${timestamp}_mic.pcm").absolutePath
            val intPath = File(rawDir, "BokBok_${timestamp}_int.pcm").absolutePath
            
            val setupSuccess = nativeRecorder.setup(
                effectiveConfig.width, effectiveConfig.height, effectiveConfig.bitrate, effectiveConfig.fps, effectiveConfig.useHevc, 
                effectiveConfig.includeMic || effectiveConfig.includeInternal, 
                outputFile,
                micPath,
                intPath,
                effectiveConfig.audioSampleRate,
                effectiveConfig.audioBitrate
            )

            if (!setupSuccess) {
                handleError("Failed to initialize video encoder")
                return
            }

            // Set the audio mix ratio from user configuration
            nativeRecorder.setMixRatio(config.internalAudioRatio, config.micAudioRatio)

            // 3. Get Surface and Start VirtualDisplay
            val surface = nativeRecorder.getInputSurface()
            if (surface == null) {
                handleError("Failed to create video surface")
                return
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorder",
                effectiveConfig.width,
                effectiveConfig.height,
                currentDpi(), 
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            if (virtualDisplay == null) {
                handleError("Failed to create virtual display")
                return
            }
            
            // 4. Start Audio Level Polling

            if (nativeRecorder.start()) {
                // 5. Start Audio Capture (Kotlin -> NDK) ONLY AFTER native recorder has started
                // This ensures gCtx->isRecording is true before we start writing samples.
                if (config.includeMic || config.includeInternal) {
                    // Determine if we need to force internal audio for AEC reference
                    var forceInternalRef = false
                    try {
                        val settings = runBlocking(Dispatchers.IO) { preferencesManager.recorderSettings.first() }
                        val bleedReduction = settings["bleedReduction"] as? Boolean ?: true
                        // Force internal if bleed reduction is enabled and we are recording from mic
                        if (bleedReduction && config.includeMic && !config.includeInternal) {
                            forceInternalRef = true
                            Timber.i("Forcing internal audio capture for AEC reference (Bleed Reduction ON)")
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to read bleedReduction setting")
                    }

                    audioCaptureManager.startCapture(
                        includeMic = config.includeMic, 
                        includeInternal = config.includeInternal, 
                        forceInternalRef = forceInternalRef,
                        mediaProjection = mediaProjection,
                        isMono = config.isMono
                    )
                }

                // Update notification with recording state and actions
                updateNotification("Recording Screen...", showPause = true)
                
                // Show Draggable HUD
                recordingOverlay = com.mustakim.bokbok.ui.screens.gameboost.screenrecord.RecordingOverlay(this)
                recordingOverlay?.show(
                    config = config,
                    onPause = { pauseRecording() },
                    onResume = { resumeRecording() },
                    onStop = { stopRecording() },
                    onTakeScreenshot = { takeScreenshot() },
                    onToggleFacecam = { toggleFacecam() },
                    onToggleWatermark = { toggleWatermark() }
                )
                
                // Show Facecam if enabled
                if (config.showFacecam) {
                    facecamOverlay = com.mustakim.bokbok.ui.screens.gameboost.screenrecord.FacecamOverlay(this)
                    facecamOverlay?.show(config)
                }

                if (config.useWatermarkText) {
                    textWatermarkOverlay = WatermarkOverlay(this).also {
                        it.showText(config.watermarkText)
                    }
                }

                if (config.useWatermarkImage && config.watermarkImagePath.isNotEmpty()) {
                    imageWatermarkOverlay = WatermarkOverlay(this).also {
                        it.showImage(config.watermarkImagePath)
                    }
                }
                
                recordStartTime = System.currentTimeMillis()
                startAutoStopMonitoring(config)
                
                // Final Polish Features
                handleFinalPolishOnStart(config)
                
                _isRecording.value = true
                isRecordingActive = true
                isPausedState = false
                updateExternalUIs()
                Timber.i("Recording started: $outputFile")
            } else {
                handleError("Failed to start video encoder")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception during startRecording")
            handleError("Recording failed: ${e.message}")
        }
    }
    
    private fun handleError(message: String) {
        Timber.e(message)
        _errorMessage.value = message
        cleanupResources()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun handleFinalPolishOnStart(config: RecordConfig) {
        // 1. Auto-Launch App
        if (config.autoLaunchPackage.isNotEmpty()) {
            try {
                val launchIntent = packageManager.getLaunchIntentForPackage(config.autoLaunchPackage)
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to launch app: ${config.autoLaunchPackage}")
            }
        }

        // 2. Set Volume
        if (config.setVolumeOnStart) {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val targetVol = (maxVol * (config.startVolumeLevel / 100f)).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, 0)
        }

        // 3. Show Touches (Requires WRITE_SETTINGS)
        if (config.showTouches) {
            try {
                if (Settings.System.canWrite(this)) {
                    Settings.System.putInt(contentResolver, "show_touches", 1)
                }
            } catch (e: Exception) {
                Timber.w("Could not set show_touches: ${e.message}")
            }
        }

        // 4. Screen Off Trigger
        if (config.stopOnScreenOff) {
            screenOffReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_SCREEN_OFF) {
                        stopRecording()
                    }
                }
            }
            registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
        }

        // 5. Shake Trigger
        if (config.stopOnShake) {
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            shakeListener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0]
                    val y = event.values[1]
                    val z = event.values[2]
                    val totalForce = Math.sqrt((x * x + y * y + z * z).toDouble()).toFloat()
                    val netForce = kotlin.math.abs(totalForce - SensorManager.GRAVITY_EARTH)
                    
                    // Config sensitivity is roughly m/s^2. 
                    // Default 20f means ~2g total, or ~1g (9.8) net force required.
                    // We use netForce to be robust against orientation.
                    if (netForce > (config.shakeSensitivity / SHAKE_THRESHOLD_DIVISOR)) { 
                        stopRecording()
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
            }
            sensorManager?.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    /**
     * Handles display orientation changes by resizing the VirtualDisplay.
     * Note: Most MediaCodec implementations don't support dynamic resolution changes,
     * so we log the event but don't attempt to resize the encoder mid-recording.
     * For a full solution, one would need to stop/restart the encoder.
     */
    private fun handleOrientationChange() {
        val metrics = resources.displayMetrics
        val newWidth = metrics.widthPixels
        val newHeight = metrics.heightPixels
        val dpi = metrics.densityDpi

        val config = currentConfig ?: return

        // Only resize if dimensions actually changed
        if (newWidth == config.width && newHeight == config.height) return

        Timber.i("Orientation changed: ${config.width}x${config.height} -> ${newWidth}x${newHeight}")

        // Resize VirtualDisplay (this works without restarting the encoder)
        try {
            // DISABLING: MediaCodec does not support dynamic resolution changes.
            // Feeding a different resolution to a fixed-size encoder causes the "cut in half" bug.
            // virtualDisplay?.resize(newWidth, newHeight, dpi)
            Timber.i("VirtualDisplay resize bypassed to prevent encoder corruption")
        } catch (e: Exception) {
            Timber.w(e, "Failed to resize VirtualDisplay")
        }
    }
    
    private fun cleanupFinalPolish() {
        // 1. Restore Show Touches
        try {
            if (Settings.System.canWrite(this)) {
                Settings.System.putInt(contentResolver, "show_touches", 0)
            }
        } catch (_: Exception) {}

        // 2. Unregister Screen Off
        screenOffReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {}
            screenOffReceiver = null
        }

        // 3. Unregister Shake
        sensorManager?.let { sm ->
            shakeListener?.let { sl ->
                sm.unregisterListener(sl)
            }
            sensorManager = null
            shakeListener = null
        }
    }

    private fun cleanupResources() {
        try {
            // Unregister display listener
            displayManager?.unregisterDisplayListener(displayListener)
            currentConfig = null

            audioCaptureManager.stopCapture()
            virtualDisplay?.release()
            virtualDisplay = null
            recordingOverlay?.hide()
            recordingOverlay = null
            facecamOverlay?.hide()
            facecamOverlay = null
            
            textWatermarkOverlay?.hide()
            textWatermarkOverlay = null
            
            imageWatermarkOverlay?.hide()
            imageWatermarkOverlay = null
            
            // Final Polish Cleanup
            cleanupFinalPolish()
            
            stopAutoStopMonitoring()
            mediaProjection?.unregisterCallback(projectionCallback)
            mediaProjection?.stop()
            mediaProjection = null
            nativeRecorder.release()
        } catch (e: Exception) {
            Timber.e(e, "Error during cleanup")
        }
    }

    private fun Context.currentDpi(): Int = resources.displayMetrics.densityDpi

    fun stopRecording() {
        if (!_isRecording.value && mediaProjection == null) return
        
        // Capture config before cleanup
        val recordConfig = currentConfig
        val recordedPath = _lastRecordingPath.value
        val startTime = recordStartTime
        
        nativeRecorder.stop()
        cleanupResources()
        
        _isRecording.value = false
        _isPaused.value = false
        isRecordingActive = false
        isPausedState = false
        updateExternalUIs()
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        // Notify MediaScanner so video appears in Gallery
        recordedPath?.let { path ->
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val settings = preferencesManager.recorderSettings.first()
                    val autoProcess = settings["autoProcess"] as? Boolean ?: true
                    
                    val rawDir = getExternalFilesDir("raw_recordings")
                    val timestamp = path.substringAfter("BokBok_").substringBefore(".mp4")
                    val micPath = File(rawDir, "BokBok_${timestamp}_mic.pcm").absolutePath
                    val intPath = File(rawDir, "BokBok_${timestamp}_int.pcm").absolutePath

                    // 1. Save to Database as PENDING
                    val duration = if (startTime > 0) System.currentTimeMillis() - startTime else 0L
                    val dbId = recordingRepository.addPendingRecording(
                        videoPath = path,
                        micPath = micPath,
                        internalPath = intPath,
                        durationMs = duration
                    )

                    if (autoProcess) {
                        performProcessing(dbId, recordConfig?.micAudioRatio ?: 1.0f, recordConfig?.internalAudioRatio ?: 1.0f)
                    } else {
                        // Manual Mode
                        MediaScannerConnection.scanFile(this@ScreenRecordService, arrayOf(path), arrayOf("video/mp4")) { _, uri ->
                             Timber.i("MediaScanner completed: $uri")
                        }
                        showCapturedNotification(path) 
                        stopSelf()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error during auto-processing")
                } finally {
                    // Manual stop might be needed here or stopSelf() is enough if all work done
                }
            }
        }
        
        Timber.i("Recording stopped. File: $recordedPath")
    }

    /**
     * Triggers processing for a specific recording ID from the database.
     */
    fun processHistoricalRecording(id: Long, micGain: Float = 1.0f, internalGain: Float = 1.0f) {
        if (_isRecording.value) {
            _errorMessage.value = "Cannot process while recording"
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Ensure foreground service is running for processing (not strictly required if we have a notification, but good for priority)
                // Ensure foreground service is running for processing
                updateNotification("Processing background task...", showPause = false)
                performProcessing(id, micGain, internalGain)
            } catch (e: Exception) {
                Timber.e(e, "Manual processing failed")
            } finally {
                stopSelf()
            }
        }
    }

    private suspend fun performProcessing(dbId: Long, micGain: Float = 1.0f, internalGain: Float = 1.0f) {
        val recording = recordingRepository.getRecording(dbId) ?: return
        val path = recording.videoPath
        val micPath = recording.micPath
        val intPath = recording.internalPath

        try {
            val settings = preferencesManager.recorderSettings.first()
            val noiseReduction = settings["noiseReduction"] as? Boolean ?: true
            val bleedReduction = settings["bleedReduction"] as? Boolean ?: true
            val qualityMode = settings["qualityMode"] as? Int ?: 1
            val exportMic = settings["exportMicOnly"] as? Boolean ?: false
            val exportInternal = settings["exportInternalOnly"] as? Boolean ?: false
            val studioMaster = settings["studioMaster"] as? Boolean ?: true

            val currentRecordConfig = preferencesManager.recordConfig.first()
            
            updateNotification("Processing recording...", showPause = false)
            recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSING)
            
            val originalFile = File(path)
            val tempInputVideo = File(path.replace(".mp4", "_temp.mp4"))
            
            delay(200) 
            
            if (originalFile.renameTo(tempInputVideo)) {
                // Prepare separate audio export paths if enabled
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "BokBok/Audio")
                if ((exportMic || exportInternal) && !musicDir.exists()) {
                    musicDir.mkdirs()
                }
                
                val baseFilename = originalFile.nameWithoutExtension
                val micExportPath = if (exportMic) File(musicDir, "${baseFilename}_mic.m4a").absolutePath else ""
                val internalExportPath = if (exportInternal) File(musicDir, "${baseFilename}_internal.m4a").absolutePath else ""

                nativeRecorder.onProgressUpdate = { progress, msg ->
                    val percent = (progress * 100).toInt()
                    updateNotification("Processing: $percent% - $msg", showPause = false)
                    
                    // Update flow for UI
                    _processingProgress.value = _processingProgress.value.toMutableMap().apply {
                        put(dbId, progress)
                    }
                }

                var success: Boolean
                try {
                    // Open file descriptor for Native Layer (bypasses raw path permission issues)
                    val pfd = ParcelFileDescriptor.open(tempInputVideo, ParcelFileDescriptor.MODE_READ_ONLY)
                    val videoFd = pfd.fd
                    
                    success = nativeRecorder.processRecording(
                        videoFd = videoFd,
                        videoWidth = currentRecordConfig.width,
                        videoHeight = currentRecordConfig.height,
                        micPath = micPath,
                        internalPath = intPath,
                        outputPath = path,
                        micExportPath = micExportPath,
                        internalExportPath = internalExportPath,
                        modelPath = modelRepository.getModelDirectory(),
                        enableBleed = bleedReduction, 
                        enableNoise = noiseReduction, 
                        enableStudioMaster = studioMaster,
                        micGain = micGain, 
                        internalGain = internalGain,
                        exportMic = exportMic,
                        exportInternal = exportInternal,
                        isMono = currentRecordConfig.isMono,
                        audioSampleRate = currentRecordConfig.audioSampleRate,
                        audioBitrate = currentRecordConfig.audioBitrate
                    )
                    
                    pfd.close() // Close FD after native processing is done
                } catch (e: Exception) {
                    Timber.e(e, "Failed to open ParcelFileDescriptor or process recording")
                    success = false
                }
                
                nativeRecorder.onProgressUpdate = null
                _processingProgress.value = _processingProgress.value.toMutableMap().apply {
                    remove(dbId)
                }
                
                if (success) {
                    Timber.i("Processing successful: $path")
                    tempInputVideo.delete()
                    File(micPath).delete()
                    File(intPath).delete()
                    recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSED, path)
                    
                    val filesToScan = mutableListOf(path)
                    if (micExportPath.isNotEmpty() && File(micExportPath).exists()) {
                        filesToScan.add(micExportPath)
                    }
                    if (internalExportPath.isNotEmpty() && File(internalExportPath).exists()) {
                        filesToScan.add(internalExportPath)
                    }

                    MediaScannerConnection.scanFile(this@ScreenRecordService, filesToScan.toTypedArray(), null) { p, _ ->
                        Timber.i("MediaScanner completed: $p")
                    }

                    if (micExportPath.isNotEmpty() || internalExportPath.isNotEmpty()) {
                        autoStopHandler.post {
                            Toast.makeText(this@ScreenRecordService, "Audio exports saved to Music/BokBok", Toast.LENGTH_LONG).show()
                        }
                    }

                    showCapturedNotification(path)
                } else {
                    Timber.e("Processing failed! Restoring original video.")
                    tempInputVideo.renameTo(originalFile)
                    recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED)
                    handleError("Processing failed")
                }
            } else {
                 Timber.e("Failed to rename temp file for processing")
                 recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED)
                 handleError("Processing failed: Could not prepare files.")
            }
        } catch (e: Exception) {
            Timber.e(e, "performProcessing failed")
            recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED)
            handleError("Processing failed: ${e.message}")
        } finally {
            _processingProgress.value = _processingProgress.value.toMutableMap().apply {
                remove(dbId)
            }
        }
    }



    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        if (nativeRecorder.pause()) {
            audioCaptureManager.pauseCapture()
            _isPaused.value = true
            isPausedState = true
            updateExternalUIs()
            updateNotification("Recording Paused", showResume = true)
        }
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        if (nativeRecorder.resume()) {
            audioCaptureManager.resumeCapture()
            _isPaused.value = false
            isPausedState = false
            updateExternalUIs()
            updateNotification("Recording Screen...", showPause = true)
        }
    }

    fun takeScreenshot() {
        val vd = virtualDisplay ?: return
        val config = currentConfig ?: return
        val originalSurface = nativeRecorder.getInputSurface() ?: return
        
        // Hide overlays
        recordingOverlay?.setVisibility(false)
        facecamOverlay?.setVisibility(false)
        textWatermarkOverlay?.setVisibility(false)
        imageWatermarkOverlay?.setVisibility(false)
        
        // Small delay to ensure they are gone from the frame
        autoStopHandler.postDelayed({
            val imageReader = ImageReader.newInstance(config.width, config.height, PixelFormat.RGBA_8888, 1)
            val handlerThread = android.os.HandlerThread("ScreenshotThread")
            handlerThread.start()
            val handler = Handler(handlerThread.looper)
            
            var captured = false
            imageReader.setOnImageAvailableListener({ reader ->
                if (captured) return@setOnImageAvailableListener
                val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                captured = true
                
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * config.width
                    
                    val bitmap = Bitmap.createBitmap(
                        config.width + rowPadding / pixelStride,
                        config.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    val finalBitmap = if (rowPadding > 0) {
                        Bitmap.createBitmap(bitmap, 0, 0, config.width, config.height)
                    } else {
                        bitmap
                    }
                    
                    saveScreenshot(finalBitmap)
                } catch (e: Exception) {
                    Timber.e(e, "ImageReader screenshot processing failed")
                } finally {
                    image.close()
                    // Restore original surface immediately
                    vd.setSurface(originalSurface)
                    
                    // Show overlays again on Main Thread
                    autoStopHandler.post {
                        recordingOverlay?.setVisibility(true)
                        facecamOverlay?.setVisibility(true)
                        textWatermarkOverlay?.setVisibility(true)
                        imageWatermarkOverlay?.setVisibility(true)
                    }
                    
                    reader.close()
                    handlerThread.quitSafely()
                }
            }, handler)
            
            // Swap display surface to ImageReader
            vd.setSurface(imageReader.surface)
            
            // Safety timeout
            handler.postDelayed({
                if (!captured) {
                    vd.setSurface(originalSurface)
                    autoStopHandler.post {
                        recordingOverlay?.setVisibility(true)
                        facecamOverlay?.setVisibility(true)
                        textWatermarkOverlay?.setVisibility(true)
                        imageWatermarkOverlay?.setVisibility(true)
                    }
                    
                    imageReader.close()
                    handlerThread.quitSafely()
                    Timber.w("Screenshot timeout")
                }
            }, 1000)
        }, 150) // Wait for overlays to hide
    }

    private fun saveScreenshot(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val bokbokDir = File(picturesDir, "BokBok/Screenshots").apply { mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val file = File(bokbokDir, "BokBok_SS_$timestamp.png")
                
                file.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                
                MediaScannerConnection.scanFile(this@ScreenRecordService, arrayOf(file.absolutePath), arrayOf("image/png")) { _, _ -> }
                Timber.i("Screenshot saved: ${file.absolutePath}")
                
                autoStopHandler.post {
                    android.widget.Toast.makeText(this@ScreenRecordService, "Screenshot saved to Pictures/BokBok", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to save screenshot")
            }
        }
    }

    fun toggleFacecam() {
        val config = currentConfig ?: return
        if (facecamOverlay == null) {
            facecamOverlay = FacecamOverlay(this)
            facecamOverlay?.show(config)
        } else {
            facecamOverlay?.hide()
            facecamOverlay = null
        }
    }

    fun toggleWatermark() {
        val config = currentConfig ?: return
        if (textWatermarkOverlay == null && imageWatermarkOverlay == null) {
            if (config.useWatermarkText) {
                textWatermarkOverlay = WatermarkOverlay(this).also { it.showText(config.watermarkText) }
            }
            if (config.useWatermarkImage && config.watermarkImagePath.isNotEmpty()) {
                imageWatermarkOverlay = WatermarkOverlay(this).also { it.showImage(config.watermarkImagePath) }
            }
        } else {
            textWatermarkOverlay?.hide()
            textWatermarkOverlay = null
            imageWatermarkOverlay?.hide()
            imageWatermarkOverlay = null
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
    }

    private fun updateExternalUIs() {
        com.mustakim.bokbok.data.service.quicktile.RecordWidgetProvider.updateAllWidgets(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            // Recording channel
            val recordingChannel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for the screen recorder"
                setShowBadge(false)
            }
            manager.createNotificationChannel(recordingChannel)
            
            // Captured channel (for success notifications)
            val capturedChannel = NotificationChannel(
                CHANNEL_CAPTURED,
                "Recording Saved",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when recordings are saved"
            }
            manager.createNotificationChannel(capturedChannel)
        }
    }
    
    private fun showCapturedNotification(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) return
        
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        
        val builder = NotificationCompat.Builder(this, CHANNEL_CAPTURED)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Recording Saved")
            .setContentText(file.name)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        
        // Try to add video thumbnail
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            val thumbnail = retriever.getFrameAtTime(1000000) // 1 second in
            retriever.release()
            if (thumbnail != null) {
                builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(thumbnail))
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to get video thumbnail")
        }
        
        // Play action - open video
        val playIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                this@ScreenRecordService,
                "${packageName}.provider",
                file
            ), "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val playPending = PendingIntent.getActivity(
            this, notificationId + 1, playIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.setContentIntent(playPending)
        builder.addAction(0, "Play", playPending)
        
        // Share action
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                this@ScreenRecordService,
                "${packageName}.provider",
                file
            ))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val sharePending = PendingIntent.getActivity(
            this, notificationId + 2,
            Intent.createChooser(shareIntent, "Share Recording"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(0, "Share", sharePending)
        
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, builder.build())
    }
    
    private fun updateNotification(content: String, showPause: Boolean = false, showResume: Boolean = false) {
        val notification = createNotification(content, showActions = true, showPause = showPause, showResume = showResume)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotification(
        content: String, 
        showActions: Boolean = true,
        showPause: Boolean = false,
        showResume: Boolean = false
    ): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        
        if (showActions) {
            // Stop action (always visible when recording)
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ACTION_STOP
            }
            val stopPending = PendingIntent.getService(
                this, 1, stopIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(R.drawable.ic_launcher_foreground, "Stop", stopPending)
            
            // Pause action
            if (showPause) {
                val pauseIntent = Intent(this, ScreenRecordService::class.java).apply {
                    action = ACTION_PAUSE
                }
                val pausePending = PendingIntent.getService(
                    this, 2, pauseIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(R.drawable.ic_launcher_foreground, "Pause", pausePending)
            }
            
            // Resume action
            if (showResume) {
                val resumeIntent = Intent(this, ScreenRecordService::class.java).apply {
                    action = ACTION_RESUME
                }
                val resumePending = PendingIntent.getService(
                    this, 3, resumeIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                builder.addAction(R.drawable.ic_launcher_foreground, "Resume", resumePending)
            }
        }

        return builder.build()
    }


    private fun startAutoStopMonitoring(config: RecordConfig) {
        // 1. Duration Monitoring
        if (config.autoStopDurationMinutes > 0) {
            val delayMs = config.autoStopDurationMinutes * 60 * 1000L
            autoStopHandler.postDelayed({
                Timber.i("Auto-stop: Duration reached (${config.autoStopDurationMinutes} min)")
                stopRecording()
            }, delayMs)
        }

        // 2. Battery Monitoring
        if (config.autoStopBatteryLevel > 0) {
            batteryReceiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                    val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                    val batteryPct = level * 100 / scale.toFloat()
                    
                    if (batteryPct <= config.autoStopBatteryLevel) {
                        Timber.i("Auto-stop: Battery level reached (${batteryPct.toInt()}%)")
                        stopRecording()
                    }
                }
            }
            registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        }
    }

    private fun stopAutoStopMonitoring() {
        autoStopHandler.removeCallbacksAndMessages(null)
        batteryReceiver?.let {
            unregisterReceiver(it)
            batteryReceiver = null
        }
    }
}
