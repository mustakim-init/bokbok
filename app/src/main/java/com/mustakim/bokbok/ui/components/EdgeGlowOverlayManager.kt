package com.mustakim.bokbok.ui.components

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.mustakim.bokbok.data.service.BokBokAgentService
import com.mustakim.bokbok.data.service.BokBokAgentService.VoiceState

/**
 * Manages the Edge Glow overlay that appears on top of all apps
 * when voice mode is active in the background.
 */
class EdgeGlowOverlayManager(private val context: Context) {

    private var micFabView: android.widget.ImageView? = null
    private var isFabShowing = false
    private var overlayView: EdgeGlowView? = null
    private var windowManager: WindowManager? = null
    private var isShowing = false

    fun canShowOverlay(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun showOverlay() {
        if (!canShowOverlay()) return

        // 1. Edge Glow (Not Touchable)
        if (overlayView == null) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            overlayView = EdgeGlowView(context)
            
            val glowParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                     @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.START
            }
            try {
                windowManager?.addView(overlayView, glowParams)
                isShowing = true
            } catch (e: Exception) {
                android.util.Log.e("EdgeGlowOverlay", "Failed to add glow", e)
            }
        }

        // 2. Mic FAB (Touchable)
        if (micFabView == null) {
            micFabView = android.widget.ImageView(context).apply {
                setImageResource(android.R.drawable.ic_btn_speak_now)
                background = android.graphics.drawable.ShapeDrawable(android.graphics.drawable.shapes.OvalShape()).apply {
                    paint.color = android.graphics.Color.parseColor("#444444") // Dark gray idle
                }
                setPadding(32, 32, 32, 32)
                elevation = 10f
                setOnClickListener {
                     val service = com.mustakim.bokbok.data.service.BokBokAgentService.getInstance()
                     val controller = service?.getVoiceController()
                     // Toggle logic handled by service state, but we can trigger intent
                     if (controller != null) {
                         // We need to know current state to toggle. 
                         // But simple click can just be "Action".
                         // Only problem: `startRecording` vs `stopRecording`.
                         // Let's rely on the service to expose toggle or check state here.
                         // Ideally, updateState would store local state.
                     }
                }
            }

            val fabParams = WindowManager.LayoutParams().apply {
                width = 160
                height = 160
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                     @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // Allow touch but pass through outside
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.BOTTOM or Gravity.END
                x = 48
                y = 100 // Bottom margin
            }
            
            try {
                windowManager?.addView(micFabView, fabParams)
                isFabShowing = true
                setupFabClickListener()
                
                var initialX = 0
                var initialY = 0
                var initialTouchX = 0f
                var initialTouchY = 0f
                var isDrag = false

                micFabView?.setOnTouchListener { view, event ->
                    when (event.action) {
                        android.view.MotionEvent.ACTION_DOWN -> {
                            initialX = fabParams.x
                            initialY = fabParams.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            isDrag = false
                            true
                        }
                        android.view.MotionEvent.ACTION_MOVE -> {
                            val dx = (event.rawX - initialTouchX).toInt()
                            val dy = (event.rawY - initialTouchY).toInt()
                            
                            // Adjust for Gravity.BOTTOM | Gravity.END
                            fabParams.x = initialX - dx
                            fabParams.y = initialY - dy
                            
                            windowManager?.updateViewLayout(view, fabParams)
                            if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) isDrag = true
                            true
                        }
                        android.view.MotionEvent.ACTION_UP -> {
                            if (!isDrag) {
                                view.performClick()
                            }
                            true
                        }
                        else -> false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("EdgeGlowOverlay", "Failed to add FAB", e)
            }
        }
    }
    
    private var currentState: VoiceState = VoiceState.Idle

    private fun setupFabClickListener() {
        micFabView?.setOnClickListener {
            val service = BokBokAgentService.getInstance() ?: return@setOnClickListener
            val controller = service.getVoiceController()
            
            when (currentState) {
                is VoiceState.Recording -> {
                    controller.stopRecording()
                }
                else -> {
                    controller.startRecording()
                }
            }
        }
    }

    fun updateState(state: VoiceState) {
        currentState = state
        micFabView?.let { fab ->
            val bg = fab.background as? android.graphics.drawable.ShapeDrawable
            
            when (state) {
                is VoiceState.Idle -> {
                    bg?.paint?.color = android.graphics.Color.parseColor("#444444") // Gray
                    fab.setImageResource(android.R.drawable.ic_btn_speak_now)
                }
                is VoiceState.Recording -> {
                    bg?.paint?.color = android.graphics.Color.parseColor("#FF4444") // Red
                    // fab.setImageResource(android.R.drawable.ic_media_pause) // Or stop icon
                    // Using standard android icon for simplicity or null
                    fab.setImageDrawable(context.getDrawable(android.R.drawable.ic_media_pause))
                }
                is VoiceState.Processing -> {
                    bg?.paint?.color = android.graphics.Color.parseColor("#4444FF") // Blue/Spinner
                    fab.setImageResource(android.R.drawable.stat_notify_sync) 
                }
                is VoiceState.Speaking -> {
                    bg?.paint?.color = android.graphics.Color.parseColor("#44FF44") // Green
                    fab.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                }
            }
            // Force redraw
            fab.invalidate()
        }
    }

    // Removing deprecated showOverlay(flow) signature in favor of updateState + showOverlay()
    
    fun hideOverlay() {
        if (overlayView != null) {
            try { windowManager?.removeView(overlayView) } catch (e: Exception) {}
            overlayView = null
            isShowing = false
        }
        if (micFabView != null) {
            try { windowManager?.removeView(micFabView) } catch (e: Exception) {}
            micFabView = null
            isFabShowing = false
        }
    }

    fun updateAmplitude(amplitude: Float) {
        val normalized = (amplitude / 3000f).coerceIn(0f, 1f) // Increased sensitivity (was 5000)
        overlayView?.setAmplitude(normalized)
    }

    fun isOverlayShowing(): Boolean = isShowing
}
