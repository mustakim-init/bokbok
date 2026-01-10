package com.mustakim.bokbok.ui.screens.gameboost.screenrecord

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Floating Watermark (Text or Image) during recording.
 * Aligned with Kapture's clean overlay design and drag behavior.
 */
class WatermarkOverlay(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("watermark_overlay", Context.MODE_PRIVATE)
    
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val container = DraggableWatermarkContainer(context)
    private lateinit var layoutParams: WindowManager.LayoutParams

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun showText(text: String, color: Color = Color.White, backgroundColor: Color = Color.Black.copy(0.5f)) {
        show(isText = true, content = text, color = color, bgColor = backgroundColor)
    }

    fun showImage(imagePath: String) {
        show(isText = false, content = imagePath)
    }

    private fun show(isText: Boolean, content: String, color: Color = Color.White, bgColor: Color = Color.Transparent) {
        val type = if (isText) "text" else "image"
        val savedX = prefs.getInt("${type}_pos_x", 100)
        val savedY = prefs.getInt("${type}_pos_y", 100)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        container.composeView.setContent {
            WatermarkContent(isText, content, color, bgColor)
        }

        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeViewModelStoreOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        windowManager.addView(container, layoutParams)
        container.initDrag(windowManager, layoutParams) {
            prefs.edit()
                .putInt("${type}_pos_x", layoutParams.x)
                .putInt("${type}_pos_y", layoutParams.y)
                .apply()
        }
    }

    fun hide() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        try {
            windowManager.removeView(container)
        } catch (_: Exception) {}
    }

    @Composable
    private fun WatermarkContent(isText: Boolean, content: String, color: Color, bgColor: Color) {
        Box(
            modifier = Modifier
                .wrapContentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .padding(8.dp)
        ) {
            if (isText) {
                Text(
                    text = content,
                    color = color,
                    fontSize = 16.sp,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                val bitmap = remember(content) {
                    try {
                        BitmapFactory.decodeFile(content)
                    } catch (_: Exception) {
                        null
                    }
                }
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.sizeIn(maxWidth = 150.dp, maxHeight = 150.dp)
                    )
                }
            }
        }
    }

    inner class DraggableWatermarkContainer(context: Context) : FrameLayout(context) {
        val composeView = ComposeView(context)
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var touchDownTime = 0L
        private var isDragging = false
        private var touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        
        private var wm: WindowManager? = null
        private var lp: WindowManager.LayoutParams? = null
        private var onRelease: (() -> Unit)? = null

        init { addView(composeView) }

        fun initDrag(wm: WindowManager, lp: WindowManager.LayoutParams, onRelease: () -> Unit) {
            this.wm = wm
            this.lp = lp
            this.onRelease = onRelease
        }

        override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean { return true }

        override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
            val lp = this.lp ?: return super.onTouchEvent(ev)
            val wm = this.wm ?: return super.onTouchEvent(ev)

            when (ev.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    touchDownTime = System.currentTimeMillis()
                    isDragging = false
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(ev.rawX - initialTouchX)
                    val dy = Math.abs(ev.rawY - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        lp.x = (initialX + (ev.rawX - initialTouchX)).toInt()
                        lp.y = (initialY + (ev.rawY - initialTouchY)).toInt()
                        wm.updateViewLayout(this, lp)
                    }
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchDownTime
                    if (isDragging || elapsed > 200) {
                        onRelease?.invoke()
                    }
                }
            }
            return true
        }
    }
}
