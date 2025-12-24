package com.mustakim.bokbok.ui.screens.gameboost.screenrecord

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay

/**
 * A floating HUD overlay for recording controls.
 * Features: Timer, minimize/expand, edge snapping, pause/resume, drawing mode.
 * Uses WindowManager params for positioning (not Compose offset) to avoid clipping.
 */
class RecordingOverlay(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = context.getSharedPreferences("recording_overlay", Context.MODE_PRIVATE)
    
    // HUD Container with drag logic
    private val hudContainer = DraggableHUDContainer(context)
    
    // Drawing Layer (full screen)
    private val drawingComposeView = ComposeView(context)
    private var isDrawingLayerVisible = false

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var startTime = System.currentTimeMillis()
    private lateinit var hudLayoutParams: WindowManager.LayoutParams
    private lateinit var drawingLayoutParams: WindowManager.LayoutParams
    
    // Callbacks
    private var onStopCallback: (() -> Unit)? = null
    private var onPauseCallback: (() -> Unit)? = null
    private var onResumeCallback: (() -> Unit)? = null

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show(onStop: () -> Unit, onPause: () -> Unit, onResume: () -> Unit) {
        startTime = System.currentTimeMillis()
        onStopCallback = onStop
        onPauseCallback = onPause
        onResumeCallback = onResume
        
        val savedX = prefs.getInt("pos_x", 100)
        val savedY = prefs.getInt("pos_y", 400)
        
        // Setup HUD Layout
        hudLayoutParams = WindowManager.LayoutParams(
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

        // Setup Drawing Layer Layout (Full Screen)
        drawingLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        hudContainer.hudComposeView.setContent {
            FloatingHUD()
        }

        drawingComposeView.setContent {
            DrawingCanvas()
        }

        // Setup views tree
        listOf(hudContainer, drawingComposeView).forEach { view ->
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        windowManager.addView(drawingComposeView, drawingLayoutParams)
        windowManager.addView(hudContainer, hudLayoutParams)

        // Initialize drag handling link
        hudContainer.initDrag(windowManager, hudLayoutParams) { savePosition() }
    }

    private fun savePosition() {
        prefs.edit()
            .putInt("pos_x", hudLayoutParams.x)
            .putInt("pos_y", hudLayoutParams.y)
            .apply()
    }

    fun hide() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        try {
            windowManager.removeView(hudContainer)
            windowManager.removeView(drawingComposeView)
        } catch (_: Exception) {}
    }

    private fun toggleDrawingMode(enabled: Boolean) {
        isDrawingLayerVisible = enabled
        drawingLayoutParams.flags = if (enabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        try {
            windowManager.updateViewLayout(drawingComposeView, drawingLayoutParams)
        } catch (_: Exception) {}
    }

    private val drawPaths = mutableStateListOf<DrawPath>()

    @Composable
    private fun DrawingCanvas() {
        var currentPath by remember { mutableStateOf<DrawPath?>(null) }
        
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = DrawPath(mutableStateListOf(offset))
                            drawPaths.add(currentPath!!)
                        },
                        onDrag = { change, _ ->
                            currentPath?.points?.add(change.position)
                        },
                        onDragEnd = { currentPath = null }
                    )
                }
        ) {
            drawPaths.forEach { path ->
                val p = androidx.compose.ui.graphics.Path().apply {
                    if (path.points.isNotEmpty()) {
                        moveTo(path.points[0].x, path.points[0].y)
                        path.points.forEach { lineTo(it.x, it.y) }
                    }
                }
                drawPath(
                    path = p,
                    color = Color.Red,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
        }
    }

    @Composable
    private fun FloatingHUD() {
        var isExpanded by remember { mutableStateOf(true) }
        var isPaused by remember { mutableStateOf(false) }
        var isMinimized by remember { mutableStateOf(false) }
        var isDrawingMode by remember { mutableStateOf(false) }
        
        // Timer state
        var elapsedSeconds by remember { mutableLongStateOf(0L) }
        LaunchedEffect(isPaused) {
            while (!isPaused) {
                elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }

        // Snap animation observer
        LaunchedEffect(isMinimized) {
            if (isMinimized) isExpanded = false
            hudContainer.animateMinimized(isMinimized)
        }

        Surface(
            modifier = Modifier.wrapContentSize(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xCC1A1A1A),
            tonalElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.15f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Recording indicator & Timer
                Row(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val indicatorAlpha by animateFloatAsState(
                        targetValue = if (isPaused) 0.5f else 1f,
                        animationSpec = tween(500), label = ""
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(indicatorAlpha)
                            .background(if (isPaused) Color.Yellow else Color.Red, androidx.compose.foundation.shape.CircleShape)
                    )
                    Text(
                        text = formatTime(elapsedSeconds),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                // Main Action Button (Minimize/Expand toggle)
                IconButton(
                    onClick = { 
                        if (isMinimized) {
                            isMinimized = false
                            isExpanded = true
                        } else {
                            isExpanded = !isExpanded
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.Close else Icons.Default.Menu,
                        contentDescription = "Toggle",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Pause Button
                        SmallHudButton(
                            icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            tint = if (isPaused) Color.Green else Color.White,
                            onClick = {
                                isPaused = !isPaused
                                if (isPaused) onPauseCallback?.invoke() else onResumeCallback?.invoke()
                            }
                        )

                        // Draw Button
                        SmallHudButton(
                            icon = Icons.Default.Edit,
                            tint = if (isDrawingMode) Color.Cyan else Color.White,
                            onClick = { 
                                isDrawingMode = !isDrawingMode
                                toggleDrawingMode(isDrawingMode)
                            }
                        )

                        // Trash Button (Clear Drawing)
                        if (isDrawingMode) {
                            SmallHudButton(
                                icon = Icons.Default.Delete,
                                onClick = { drawPaths.clear() }
                            )
                        }

                        // Minimize to Edge Button
                        SmallHudButton(
                            icon = Icons.Default.Close,
                            onClick = { 
                                isMinimized = true
                                isExpanded = false
                            }
                        )

                        // Stop Button
                        IconButton(
                            onClick = {
                                hide()
                                onStopCallback?.invoke()
                            },
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color.Red.copy(0.2f), androidx.compose.foundation.shape.CircleShape)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.Red, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun SmallHudButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
        IconButton(onClick = onClick, modifier = Modifier.size(34.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }

    private fun formatTime(seconds: Long): String {
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    /**
     * Custom FrameLayout that contains a ComposeView and handles its own drag logic.
     * This avoids issues where ComposeView might be final in some environments.
     */
    inner class DraggableHUDContainer(context: Context) : FrameLayout(context) {
        val hudComposeView = ComposeView(context)
        
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false
        private var touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        
        private var wm: WindowManager? = null
        private var lp: WindowManager.LayoutParams? = null
        private var onDragRelease: (() -> Unit)? = null

        init {
            addView(hudComposeView)
        }

        fun initDrag(wm: WindowManager, lp: WindowManager.LayoutParams, onDragRelease: () -> Unit) {
            this.wm = wm
            this.lp = lp
            this.onDragRelease = onDragRelease
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            val lp = this.lp ?: return super.dispatchTouchEvent(ev)
            val wm = this.wm ?: return super.dispatchTouchEvent(ev)

            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = ev.rawX
                    initialTouchY = ev.rawY
                    isDragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(ev.rawX - initialTouchX)
                    val dy = Math.abs(ev.rawY - initialTouchY)
                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        lp.x = (initialX + (ev.rawX - initialTouchX)).toInt()
                        lp.y = (initialY + (ev.rawY - initialTouchY)).toInt()
                        wm.updateViewLayout(this, lp)
                        return true 
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        snapToEdge()
                        onDragRelease?.invoke()
                        return true
                    }
                }
            }
            return super.dispatchTouchEvent(ev)
        }

        fun snapToEdge() {
            val lp = this.lp ?: return
            val wm = this.wm ?: return
            val screenWidth = context.resources.displayMetrics.widthPixels
            val viewWidth = this.width
            val centerX = lp.x + viewWidth / 2
            
            val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - viewWidth

            ValueAnimator.ofInt(lp.x, targetX).apply {
                duration = 250
                interpolator = android.view.animation.DecelerateInterpolator()
                addUpdateListener {
                    lp.x = it.animatedValue as Int
                    try { wm.updateViewLayout(this@DraggableHUDContainer, lp) } catch (_: Exception) {}
                }
                start()
            }
        }

        fun animateMinimized(minimized: Boolean) {
            val lp = this.lp ?: return
            val wm = this.wm ?: return
            val screenWidth = context.resources.displayMetrics.widthPixels
            val viewWidth = this.width
            
            val offset = (viewWidth * 0.6).toInt()
            val targetX = if (lp.x + viewWidth / 2 < screenWidth / 2) {
                if (minimized) -offset else 0
            } else {
                if (minimized) screenWidth - (viewWidth - offset) else screenWidth - viewWidth
            }

            ValueAnimator.ofInt(lp.x, targetX).apply {
                duration = 300
                interpolator = android.view.animation.OvershootInterpolator(0.8f)
                addUpdateListener {
                    lp.x = it.animatedValue as Int
                    try { wm.updateViewLayout(this@DraggableHUDContainer, lp) } catch (_: Exception) {}
                }
                start()
            }
        }
    }
}

data class DrawPath(
    val points: androidx.compose.runtime.snapshots.SnapshotStateList<androidx.compose.ui.geometry.Offset>
)
