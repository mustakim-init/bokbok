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
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mustakim.bokbok.MainActivity
import com.mustakim.bokbok.R
import dagger.hilt.android.AndroidEntryPoint
import com.mustakim.bokbok.model.RecordConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class ScreenRecordService : Service() {

    private val binder = LocalBinder()
    private var mediaProjection: MediaProjection? = null
    private val nativeRecorder = NativeRecorder()
    private lateinit var audioCaptureManager: AudioCaptureManager
    
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var recordingOverlay: com.mustakim.bokbok.ui.screens.gameboost.screenrecord.RecordingOverlay? = null
    
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

    inner class LocalBinder : Binder() {
        fun getService(): ScreenRecordService = this@ScreenRecordService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        audioCaptureManager = AudioCaptureManager(this, nativeRecorder)
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            if (mediaProjection == null) {
                handleError("Failed to obtain screen capture permission")
                return
            }

            // Register callback for system-initiated stop
            mediaProjection?.registerCallback(projectionCallback, null)

            // 1. Setup NDK Engine - Save to PUBLIC directory for Gallery visibility
            val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            val bokbokDir = File(moviesDir, "BokBok").apply { mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outputFile = File(bokbokDir, "BokBok_$timestamp.mp4").absolutePath
            _lastRecordingPath.value = outputFile
            
            val setupSuccess = nativeRecorder.setup(
                config.width, config.height, config.bitrate, config.fps, config.useHevc, outputFile
            )

            if (!setupSuccess) {
                handleError("Failed to initialize video encoder")
                return
            }

            // 2. Start Audio Capture (Kotlin -> NDK)
            if (config.includeMic || config.includeInternal) {
                audioCaptureManager.startCapture(config.includeMic, config.includeInternal, mediaProjection)
            }

            // 3. Get Surface and Start VirtualDisplay
            val surface = nativeRecorder.getInputSurface()
            if (surface == null) {
                handleError("Failed to create video surface")
                return
            }

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecorder",
                config.width,
                config.height,
                currentDpi(), 
                android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            if (nativeRecorder.start()) {
                // Update notification with recording state and actions
                updateNotification("Recording Screen...", showPause = true)
                
                // Show Draggable HUD
                recordingOverlay = com.mustakim.bokbok.ui.screens.gameboost.screenrecord.RecordingOverlay(this)
                recordingOverlay?.show(
                    onStop = { stopRecording() },
                    onPause = { pauseRecording() },
                    onResume = { resumeRecording() }
                )
                
                _isRecording.value = true
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
    
    private fun cleanupResources() {
        try {
            audioCaptureManager.stopCapture()
            virtualDisplay?.release()
            virtualDisplay = null
            recordingOverlay?.hide()
            recordingOverlay = null
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
        
        val recordedPath = _lastRecordingPath.value
        
        nativeRecorder.stop()
        cleanupResources()
        
        _isRecording.value = false
        _isPaused.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        
        // Notify MediaScanner so video appears in Gallery
        recordedPath?.let { path ->
            MediaScannerConnection.scanFile(this, arrayOf(path), arrayOf("video/mp4")) { _, uri ->
                Timber.i("MediaScanner completed: $uri")
            }
            // Show success notification with Play/Share actions
            showCapturedNotification(path)
        }
        
        stopSelf()
        Timber.i("Recording stopped. File: $recordedPath")
    }

    fun pauseRecording() {
        if (!_isRecording.value || _isPaused.value) return
        if (nativeRecorder.pause()) {
            _isPaused.value = true
            updateNotification("Recording Paused", showResume = true)
        }
    }

    fun resumeRecording() {
        if (!_isRecording.value || !_isPaused.value) return
        if (nativeRecorder.resume()) {
            _isPaused.value = false
            updateNotification("Recording Screen...", showPause = true)
        }
    }
    
    fun clearError() {
        _errorMessage.value = null
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

    companion object {
        const val CHANNEL_ID = "screen_record_channel"
        const val CHANNEL_CAPTURED = "screen_record_captured"
        const val NOTIFICATION_ID = 1337
        
        const val ACTION_PAUSE = "com.mustakim.bokbok.PAUSE_RECORDING"
        const val ACTION_RESUME = "com.mustakim.bokbok.RESUME_RECORDING"
        const val ACTION_STOP = "com.mustakim.bokbok.STOP_RECORDING"
    }
}
