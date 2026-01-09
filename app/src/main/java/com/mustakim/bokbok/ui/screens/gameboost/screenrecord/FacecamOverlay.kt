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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
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

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show(config: com.mustakim.bokbok.model.RecordConfig) {
        val savedX = prefs.getInt("pos_x", 100)
        val savedY = prefs.getInt("pos_y", 100)
        val savedSize = prefs.getInt("size", 200.dpToPx(context))

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
            FacecamContent(
                onClose = { hide() },
                config = config
            )
        }

        container.setViewTreeLifecycleOwner(this)
        container.setViewTreeViewModelStoreOwner(this)
        container.setViewTreeSavedStateRegistryOwner(this)

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        
        windowManager.addView(container, layoutParams)
        container.initDrag(windowManager, layoutParams)
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
    private fun FacecamContent(
        onClose: () -> Unit,
        config: com.mustakim.bokbok.model.RecordConfig
    ) {
        var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(if (config.facecamShape == "Circle") CircleShape else RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(2.dp, MaterialTheme.colorScheme.primary, if (config.facecamShape == "Circle") CircleShape else RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            CameraPreview(
                lensFacing = lensFacing,
                modifier = Modifier.fillMaxSize()
            )

            // Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                IconButton(
                    onClick = { 
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) 
                                     CameraSelector.LENS_FACING_BACK else CameraSelector.LENS_FACING_FRONT 
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.FlipCameraAndroid, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
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
        val previewView = remember { PreviewView(context) }

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
                } catch (exc: Exception) {
                    // Log error
                }
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
        private var wm: WindowManager? = null
        private var lp: WindowManager.LayoutParams? = null

        private val scaleDetector = android.view.ScaleGestureDetector(context, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                val lp = lp ?: return false
                val wm = wm ?: return false
                
                val scaleFactor = detector.scaleFactor
                val newSize = (lp.width * scaleFactor).toInt().coerceIn(100.dpToPx(context), 500.dpToPx(context))
                
                lp.width = newSize
                lp.height = newSize
                wm.updateViewLayout(this@DraggableCameraContainer, lp)
                return true
            }
        })

        init {
            addView(composeView)
        }

        fun initDrag(wm: WindowManager, lp: WindowManager.LayoutParams) {
            this.wm = wm
            this.lp = lp
        }

        override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
            return true
        }

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
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    lp.x = (initialX + (ev.rawX - initialTouchX)).toInt()
                    lp.y = (initialY + (ev.rawY - initialTouchY)).toInt()
                    wm.updateViewLayout(this, lp)
                }
                android.view.MotionEvent.ACTION_UP -> {
                    saveState()
                }
            }
            return true
        }
    }
}
