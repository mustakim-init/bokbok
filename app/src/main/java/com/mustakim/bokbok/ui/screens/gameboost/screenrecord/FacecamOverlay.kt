package com.mustakim.bokbok.ui.screens.gameboost.screenrecord

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A floating Facecam overlay using CameraX.
 * Aligned with Kapture's interaction-driven design (tap to flip).
 */
class FacecamOverlay(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("facecam_overlay", Context.MODE_PRIVATE)
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val container = DraggableCameraContainer(context)
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    // Lens state shared between container (tap detect) and content (preview)
    private val lensFacingState = mutableIntStateOf(CameraSelector.LENS_FACING_FRONT)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun setVisibility(visible: Boolean) {
        container.visibility = if (visible) android.view.View.VISIBLE else android.view.View.GONE
    }

    fun show(config: com.mustakim.bokbok.model.RecordConfig) {
        val savedX = prefs.getInt("pos_x", 100)
        val savedY = prefs.getInt("pos_y", 100)
        val savedSize = prefs.getInt("size", 160.dpToPx(context))

        layoutParams = WindowManager.LayoutParams(
            savedSize,
            savedSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        container.composeView.setContent {
            FacecamContent(config)
        }

        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeViewModelStoreOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        windowManager.addView(container, layoutParams)
        container.initDrag(windowManager, layoutParams)
        
        container.onToggleCamera = {
            lensFacingState.intValue = if (lensFacingState.intValue == CameraSelector.LENS_FACING_FRONT)
                CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT
        }
    }

    private fun saveState() {
        prefs.edit()
            .putInt("pos_x", layoutParams.x)
            .putInt("pos_y", layoutParams.y)
            .putInt("size", layoutParams.width)
            .apply()
    }

    fun hide() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        try {
            windowManager.removeView(container)
        } catch (_: Exception) {}
        cameraExecutor.shutdown()
    }

    @Composable
    private fun FacecamContent(config: com.mustakim.bokbok.model.RecordConfig) {
        val lensFacing by lensFacingState
        
        val shape = if (config.facecamShape == "Circle") CircleShape else RoundedCornerShape(12.dp)
        
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp), // Tiny padding to prevent border clipping at edges
            shape = shape,
            color = Color.Black,
            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
            tonalElevation = 8.dp,
            shadowElevation = 4.dp
        ) {
            Box(modifier = Modifier.fillMaxSize().clip(shape)) {
                CameraPreview(
                    lensFacing = lensFacing,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    @Composable
    private fun CameraPreview(
        lensFacing: Int,
        modifier: Modifier = Modifier
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val previewView = remember { 
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
        }

        LaunchedEffect(lensFacing) {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (exc: Exception) {}
            }, ContextCompat.getMainExecutor(context))
        }

        androidx.compose.ui.viewinterop.AndroidView(
            factory = { previewView },
            modifier = modifier
        )
    }

    private fun Int.dpToPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    inner class DraggableCameraContainer(context: Context) : FrameLayout(context) {
        val composeView = ComposeView(context)
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var touchDownTime = 0L
        private var isDragging = false
        private var touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        
        var onToggleCamera: (() -> Unit)? = null
        
        private var wm: WindowManager? = null
        private var lp: WindowManager.LayoutParams? = null

        private val scaleDetector = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val lp = lp ?: return false
                val wm = wm ?: return false
                val scaleFactor = detector.scaleFactor
                val newSize = (lp.width * scaleFactor).toInt().coerceIn(40.dpToPx(context), 600.dpToPx(context))
                lp.width = newSize
                lp.height = newSize
                wm.updateViewLayout(this@DraggableCameraContainer, lp)
                return true
            }
        })

        init { addView(composeView) }

        fun initDrag(wm: WindowManager, lp: WindowManager.LayoutParams) {
            this.wm = wm
            this.lp = lp
        }

        override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean { return true }

        override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
            scaleDetector.onTouchEvent(ev)
            if (scaleDetector.isInProgress) return true

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
                    if (elapsed <= 200 && !isDragging) {
                        onToggleCamera?.invoke()
                    } else if (isDragging) {
                        saveState()
                    }
                }
            }
            return true
        }
    }
}
