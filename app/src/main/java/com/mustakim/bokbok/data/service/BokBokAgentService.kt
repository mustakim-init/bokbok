package com.mustakim.bokbok.data.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Path
import android.os.Build
import android.util.Base64
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.mustakim.bokbok.data.media.VoiceManager
import com.mustakim.bokbok.data.repository.AIRepository
import com.mustakim.bokbok.ui.components.EdgeGlowOverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.inject.Inject

@AndroidEntryPoint
class BokBokAgentService : AccessibilityService() {

    @Inject lateinit var voiceManager: VoiceManager
    @Inject lateinit var repository: AIRepository
    @Inject lateinit var preferencesManager: com.mustakim.bokbok.data.local.PreferencesManager
    private lateinit var overlayManager: EdgeGlowOverlayManager
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    // Voice Interaction Controller
    inner class VoiceInteractionController {
        private val _voiceState = MutableStateFlow<VoiceState>(VoiceState.Idle)
        val voiceState: StateFlow<VoiceState> = _voiceState
        
        fun startRecording() {
            if (_voiceState.value is VoiceState.Processing || _voiceState.value is VoiceState.Speaking) {
                voiceManager.stopSpeaking() // Interrupt if speaking
            }
            
            _voiceState.value = VoiceState.Recording
            voiceManager.startManualRecording()
        }
        
        fun stopRecording() {
            if (_voiceState.value != VoiceState.Recording) return
            
            _voiceState.value = VoiceState.Processing
            voiceManager.stopManualRecording { transcription ->
                if (transcription.isNotEmpty()) {
                    processVoiceCommand(transcription)
                } else {
                    _voiceState.value = VoiceState.Idle
                }
            }
        }
        
        private fun processVoiceCommand(prompt: String) {
            serviceScope.launch {
                try {
                    // Send to AI
                    val sessionId = "voice_session" // Or manage session ID
                    
                    // Simple flow: Send message -> Get response -> Speak
                    val response = repository.sendVoiceMessage(prompt)
                    
                    _voiceState.value = VoiceState.Speaking
                    
                    val ttsMode = preferencesManager.aiTtsMode.first()
                    voiceManager.speak(response, useQualityTts = ttsMode == "QUALITY")
                    
                    // Wait for speech to end? VoiceManager doesn't expose a "done" callback easily, 
                    // but we can listen to isSpeaking
                    voiceManager.isSpeaking.collect { speaking ->
                        if (!speaking && _voiceState.value == VoiceState.Speaking) {
                            _voiceState.value = VoiceState.Idle
                        }
                    }
                    
                } catch (e: Exception) {
                    android.util.Log.e("BokBokAgentService", "Voice processing failed", e)
                    _voiceState.value = VoiceState.Idle
                    voiceManager.speak("Sorry, I encountered an error.", useQualityTts = true)
                }
            }
        }
    }
    
    private val _voiceController by lazy { VoiceInteractionController() }
    fun getVoiceController() = _voiceController

    sealed class VoiceState {
        object Idle : VoiceState()
        object Recording : VoiceState()
        object Processing : VoiceState()
        object Speaking : VoiceState()
    }

    companion object {
        private val _isServiceEnabled = MutableStateFlow(false)
        val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled

        private val _isInteractiveModeEnabled = MutableStateFlow(false)
        val isInteractiveModeEnabled: StateFlow<Boolean> = _isInteractiveModeEnabled

        private var instance: BokBokAgentService? = null

        fun getInstance(): BokBokAgentService? = instance

        const val SERVICE_ID = "com.mustakim.bokbok/.data.service.BokBokAgentService"
        private const val ACTION_STOP_INTERACTIVE = "com.mustakim.bokbok.ACTION_STOP_INTERACTIVE"
    }

    override fun onServiceConnected() {
        startAssistantForeground()
        super.onServiceConnected()
        instance = this
        _isServiceEnabled.value = true
        overlayManager = EdgeGlowOverlayManager(this)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or 
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        observeVoiceState()
        
        // Listen for interactive mode changes to update notification
        serviceScope.launch {
            _isInteractiveModeEnabled.collect {
                startAssistantForeground()
            }
        }
    }
    
    private fun startAssistantForeground() {
        val channelId = "bokbok_ai_agent"
        val channelName = "AI Assistant"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_LOW)
            chan.lockscreenVisibility = Notification.VISIBILITY_SECRET
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(chan)
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setOngoing(true)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("BokBok AI")
            .setContentText(if (_isInteractiveModeEnabled.value) "Interactive Mode Active" else "AI Assistant Ready")
            .setPriority(NotificationManager.IMPORTANCE_LOW)
            .setCategory(Notification.CATEGORY_SERVICE)

        if (_isInteractiveModeEnabled.value) {
            val stopIntent = android.content.Intent(this, BokBokAgentService::class.java).apply {
                action = ACTION_STOP_INTERACTIVE
            }
            val stopPendingIntent = android.app.PendingIntent.getService(
                this, 0, stopIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            notificationBuilder.addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop Interaction",
                stopPendingIntent
            )
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, notificationBuilder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1001, notificationBuilder.build())
        }
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_INTERACTIVE) {
            exitInteractiveMode()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun observeVoiceState() {
        serviceScope.launch {
            // Ambient Amplitude Stream for Edge Glow
            launch {
                voiceManager.amplitude.collectLatest { amp ->
                   overlayManager.updateAmplitude(amp)
                }
            }
            
            // Voice State for FAB and Overlay Logic
            launch {
                _voiceController.voiceState.collectLatest { state ->
                    overlayManager.updateState(state)
                    if (_isInteractiveModeEnabled.value) {
                        overlayManager.showOverlay()
                    }
                }
            }
        }
    }

    fun enterInteractiveMode() {
        if (_isInteractiveModeEnabled.value) return
        _isInteractiveModeEnabled.value = true
        voiceManager.startAmbientListening()
        overlayManager.showOverlay()
    }

    fun exitInteractiveMode() {
        if (!_isInteractiveModeEnabled.value) return
        _isInteractiveModeEnabled.value = false
        voiceManager.stopAmbientListening()
        overlayManager.hideOverlay()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // AI perception will be triggered here based on window changes
    }

    override fun onInterrupt() {
        // Handle interruption
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceEnabled.value = false
        _isInteractiveModeEnabled.value = false
        overlayManager.hideOverlay()
        voiceManager.stopAmbientListening()
        serviceJob.cancel()
    }

    /**
     * Dumps the current view hierarchy into a compact JSON for AI consumption.
     */
    fun dumpHierarchy(): String {
        val rootNode = rootInActiveWindow ?: return "{}"
        val result = JSONObject()
        result.put("activity", rootNode.packageName ?: "Unknown")
        
        val nodes = JSONArray()
        collectRelevantNodes(rootNode, nodes)
        result.put("nodes", nodes)
        
        return result.toString()
    }

    private fun collectRelevantNodes(node: AccessibilityNodeInfo?, nodes: JSONArray) {
        if (node == null) return

        // Semantic Compression Logic: Only include nodes with text, descriptions, or clickable actions
        val hasText = !node.text.isNullOrEmpty()
        val hasDesc = !node.contentDescription.isNullOrEmpty()
        val isClickable = node.isClickable || node.isCheckable
        val isInput = node.className?.contains("EditText", ignoreCase = true) == true

        if (hasText || hasDesc || isClickable || isInput) {
            val nodeObj = JSONObject()
            nodeObj.put("text", node.text?.toString() ?: "")
            nodeObj.put("desc", node.contentDescription?.toString() ?: "")
            nodeObj.put("id", node.viewIdResourceName ?: "")
            nodeObj.put("class", node.className?.toString()?.substringAfterLast('.') ?: "")
            nodeObj.put("clickable", node.isClickable)
            
            val bounds = android.graphics.Rect()
            node.getBoundsInScreen(bounds)
            nodeObj.put("bounds", "[${bounds.left},${bounds.top},${bounds.right},${bounds.bottom}]")
            
            nodes.put(nodeObj)
        }

        for (i in 0 until node.childCount) {
            collectRelevantNodes(node.getChild(i), nodes)
        }
    }

    /**
     * Performs a click on a node that matches the given text or ID.
     */
    fun performClick(target: String): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        val foundNodes = rootNode.findAccessibilityNodeInfosByText(target)
        if (foundNodes.isNotEmpty()) {
            return foundNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        val idNodes = rootNode.findAccessibilityNodeInfosByViewId(target)
        if (idNodes.isNotEmpty()) {
            return idNodes[0].performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        
        return false
    }

    /**
     * Captures a screenshot and returns it as a Base64 string.
     * Only works on Android 11 (API 30) and above.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun takeScreenshot(callback: (String?) -> Unit) {
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: ScreenshotResult) {
                val bitmap = Bitmap.wrapHardwareBuffer(screenshotResult.hardwareBuffer, screenshotResult.colorSpace)
                if (bitmap != null) {
                    val scaledBitmap = scaleBitmap(bitmap)
                    val base64 = bitmapToBase64(scaledBitmap)
                    callback(base64)
                } else {
                    callback(null)
                }
            }

            override fun onFailure(errorCode: Int) {
                callback(null)
            }
        })
    }

    private fun scaleBitmap(bitmap: Bitmap): Bitmap {
        val maxWidth = 640
        val maxHeight = 640
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        
        val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
        val targetWidth = if (ratio > 1) maxWidth else (maxHeight * ratio).toInt()
        val targetHeight = if (ratio > 1) (maxWidth / ratio).toInt() else maxHeight
        
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * Performs a custom gesture (e.g., swipe).
     */
    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = 300L): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return dispatchGesture(gesture, null, null)
    }
}
