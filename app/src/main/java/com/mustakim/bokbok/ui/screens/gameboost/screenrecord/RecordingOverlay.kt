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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import android.content.Intent
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mustakim.bokbok.model.RecordConfig
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay

/**
 * A floating HUD overlay for recording controls.
 * Replicates Kapture UI and behavior.
 */
class RecordingOverlay(private val context: Context) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs: SharedPreferences = context.getSharedPreferences("recording_overlay", Context.MODE_PRIVATE)
    
    private val hudContainer = DraggableHUDContainer(context)
    private val drawingComposeView = ComposeView(context)
    private var isDrawingModeEnabled = false

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private var startTime = System.currentTimeMillis()
    private lateinit var hudLayoutParams: WindowManager.LayoutParams
    private lateinit var drawingLayoutParams: WindowManager.LayoutParams
    
    private var onStopCallback: (() -> Unit)? = null
    private var onPauseCallback: (() -> Unit)? = null
    private var onResumeCallback: (() -> Unit)? = null
    private var onTakeScreenshotCallback: (() -> Unit)? = null
    private var onToggleFacecamCallback: (() -> Unit)? = null
    private var onToggleWatermarkCallback: (() -> Unit)? = null
    
    private var currentConfig: RecordConfig = RecordConfig()

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    fun show(
        config: RecordConfig,
        onStop: () -> Unit, 
        onPause: () -> Unit, 
        onResume: () -> Unit,
        onTakeScreenshot: () -> Unit,
        onToggleFacecam: () -> Unit,
        onToggleWatermark: () -> Unit
    ) {
        currentConfig = config
        startTime = System.currentTimeMillis()
        onStopCallback = onStop
        onPauseCallback = onPause
        onResumeCallback = onResume
        onTakeScreenshotCallback = onTakeScreenshot
        onToggleFacecamCallback = onToggleFacecam
        onToggleWatermarkCallback = onToggleWatermark
        
        val savedX = prefs.getInt("pos_x", 100)
        val savedY = prefs.getInt("pos_y", 400)
        
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

        drawingLayoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSPARENT
        )

        hudContainer.hudComposeView.setContent {
            FloatingHUD(currentConfig)
        }

        drawingComposeView.setContent {
            DrawingCanvas()
        }

        listOf(hudContainer, drawingComposeView).forEach { view ->
            view.setViewTreeLifecycleOwner(this)
            view.setViewTreeViewModelStoreOwner(this)
            view.setViewTreeSavedStateRegistryOwner(this)
        }

        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        windowManager.addView(hudContainer, hudLayoutParams)

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
        isDrawingModeEnabled = enabled
        drawingLayoutParams.flags = if (enabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        
        try {
            if (enabled) {
                try {
                    windowManager.addView(drawingComposeView, drawingLayoutParams)
                } catch (e: Exception) {
                    windowManager.updateViewLayout(drawingComposeView, drawingLayoutParams)
                }
            } else {
                try {
                    windowManager.removeView(drawingComposeView)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private val drawPaths = mutableStateListOf<DrawPath>()
    private val undonePaths = mutableStateListOf<DrawPath>()
    private val recentColors = mutableStateListOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White)
    
    private var currentColor by mutableStateOf(Color.Red)
    private var currentWidth by mutableFloatStateOf(4f)
    
    private var internalRms by mutableFloatStateOf(0f)
    private var micRms by mutableFloatStateOf(0f)

    fun updateLevels(levels: FloatArray) {
        if (levels.size >= 4) {
            micRms = levels[0]
            internalRms = levels[2]
        }
    }

    private fun undo() {
        if (drawPaths.isNotEmpty()) {
            val last = drawPaths.removeLast()
            undonePaths.add(last)
        }
    }

    private fun redo() {
        if (undonePaths.isNotEmpty()) {
            val last = undonePaths.removeLast()
            drawPaths.add(last)
        }
    }
    
    private fun selectColor(color: Color) {
        currentColor = color
        if (recentColors.contains(color)) {
            recentColors.remove(color)
        }
        recentColors.add(0, color)
        if (recentColors.size > 5) recentColors.removeLast()
    }
    
    private fun captureDrawingLayer() {
        val bitmap = Bitmap.createBitmap(drawingComposeView.width, drawingComposeView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawingComposeView.draw(canvas)
        saveBitmapToGallery(bitmap)
    }

    @Composable
    private fun DrawingCanvas() {
        var currentPath by remember { mutableStateOf<DrawPath?>(null) }
        
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentPath = DrawPath(
                                points = mutableStateListOf(offset),
                                color = currentColor,
                                width = currentWidth
                            )
                            drawPaths.add(currentPath!!)
                            undonePaths.clear()
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
                    color = path.color,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = path.width.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round
                    )
                )
            }
        }
    }

    @Composable
    private fun FloatingHUD(config: RecordConfig) {
        var isPaused by remember { mutableStateOf(false) }
        var isMinimized by remember { mutableStateOf(config.startMinimized) }
        var isDrawingMode by remember { mutableStateOf(false) }
        var showShortcuts by remember { mutableStateOf(false) }
        
        var elapsedSeconds by remember { mutableLongStateOf(0L) }
        LaunchedEffect(isPaused) {
            while (!isPaused) {
                elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000
                delay(1000)
            }
        }

        LaunchedEffect(isMinimized) {
            if (isMinimized) showShortcuts = false
            hudContainer.isMinimizedHUD = isMinimized
            hudContainer.animateMinimized(isMinimized)
        }
        
        hudContainer.onSwipeMaximize = { if (isMinimized) isMinimized = false }

        val menuAlpha by animateFloatAsState(
            targetValue = if (isMinimized) 0.6f else 1f,
            animationSpec = tween(350), label = ""
        )

        Surface(
            modifier = Modifier
                .wrapContentSize()
                .alpha(menuAlpha),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xD2FFFFFF),
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(0.7.dp, Color(0xFF808080))
        ) {
            val isVertical = config.menuStyle == 1
            
            if (isVertical) {
                Column(
                    modifier = Modifier.padding(vertical = 3.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HUDContent(config, isVertical, isPaused, isMinimized, isDrawingMode, showShortcuts, elapsedSeconds,
                        togglePause = { 
                            isPaused = !isPaused
                            if (isPaused) onPauseCallback?.invoke() else onResumeCallback?.invoke()
                        },
                        toggleMinimize = { isMinimized = !isMinimized },
                        toggleDrawing = { 
                            isDrawingMode = !isDrawingMode
                            toggleDrawingMode(isDrawingMode)
                        },
                        toggleShortcuts = { showShortcuts = !showShortcuts }
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HUDContent(config, isVertical, isPaused, isMinimized, isDrawingMode, showShortcuts, elapsedSeconds,
                        togglePause = { 
                            isPaused = !isPaused
                            if (isPaused) onPauseCallback?.invoke() else onResumeCallback?.invoke()
                        },
                        toggleMinimize = { isMinimized = !isMinimized },
                        toggleDrawing = { 
                            isDrawingMode = !isDrawingMode
                            toggleDrawingMode(isDrawingMode)
                        },
                        toggleShortcuts = { showShortcuts = !showShortcuts }
                    )
                }
            }
        }
    }

    @Composable
    private fun HUDContent(
        config: RecordConfig,
        isVertical: Boolean,
        isPaused: Boolean,
        isMinimized: Boolean,
        isDrawingMode: Boolean,
        showShortcuts: Boolean,
        elapsedSeconds: Long,
        togglePause: () -> Unit,
        toggleMinimize: () -> Unit,
        toggleDrawing: () -> Unit,
        toggleShortcuts: () -> Unit
    ) {
        if (isMinimized) {
            Box(
                modifier = Modifier
                    .size(if (isVertical) 36.dp else 5.dp, if (isVertical) 5.dp else 36.dp)
                    .background(Color(0xFF808080).copy(0.5f), RoundedCornerShape(100.dp))
                    .clickable { toggleMinimize() }
            )
            return
        }

        val kaptureIconColor = Color(0xFF2B2B2B)
        val kaptureSecondaryIcon = Color(0xFF808080)
        
        val items = mutableListOf<@Composable () -> Unit>()

        // 1. Stop Button
        items.add {
            IconButton(
                onClick = { hide(); onStopCallback?.invoke() }, 
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFFEB3B2E), RoundedCornerShape(20.dp))
            ) {
                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }

        // 2. Timer
        if (config.showTimeOnMenu) {
            items.add {
                val autoStopSeconds = config.autoStopDurationMinutes * 60L
                val remainingSeconds = if (autoStopSeconds > 0) (autoStopSeconds - elapsedSeconds).coerceAtLeast(0) else -1L

                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isVertical) 0.dp else 12.dp,
                        vertical = if (isVertical) 4.dp else 0.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(if (isPaused) Color.Yellow else Color.Red, CircleShape))
                        Text(formatTime(elapsedSeconds), color = Color(0xFF2B2B2B), fontSize = if (isVertical) 12.sp else 13.sp, fontWeight = FontWeight.Medium)
                    }
                    
                    if (remainingSeconds >= 0) {
                        Text(
                            "Ends in: ${formatTime(remainingSeconds)}", 
                            color = if (remainingSeconds < 60) Color.Red else Color(0xFF5E5E5E),
                            fontSize = 9.sp
                        )
                    }
                    
                    if (!isMinimized) {
                        Spacer(modifier = Modifier.height(2.dp))
                        AudioLevelMeters(micRms, internalRms, isVertical)
                    }
                }
            }
        }

        // 3. Shortcuts
        if (showShortcuts && config.shortcuts.isNotEmpty()) {
            config.shortcuts.forEach { pkg ->
                items.add {
                    IconButton(onClick = { launchApp(pkg) }, modifier = Modifier.size(36.dp)) {
                        val icon = remember { getAppIcon(pkg) }
                        if (icon != null) {
                            androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp).alpha(0.8f))
                        } else {
                            Icon(Icons.Default.Launch, null, tint = Color(0xFF808080), modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }

        // 4. Configurable Tools
        if (config.showPauseResumeOnMenu) {
            items.add { SmallHudButton(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (isPaused) Color(0xFF4CAF50) else kaptureIconColor, togglePause) }
        }
        if (config.showCameraButtonOnMenu) {
            items.add { SmallHudButton(Icons.Default.PhotoCamera, kaptureIconColor) { onToggleFacecamCallback?.invoke() } }
        }
        if (config.showScreenshotButtonOnMenu) {
            items.add { SmallHudButton(Icons.Default.CameraAlt, kaptureIconColor) { onTakeScreenshotCallback?.invoke() } }
        }
        if (config.showDrawButtonOnMenu) {
            items.add { SmallHudButton(Icons.Default.Edit, if (isDrawingMode) Color(0xFF2196F3) else kaptureIconColor, toggleDrawing) }
        }
        
        if (config.useWatermarkText || config.useWatermarkImage) {
            items.add { SmallHudButton(Icons.Default.WaterDrop, kaptureIconColor) { onToggleWatermarkCallback?.invoke() } }
        }
        
        val currentOrientationModeState = remember { mutableIntStateOf(0) }
        items.add {
            SmallHudButton(
                icon = when(currentOrientationModeState.intValue) {
                    1 -> Icons.Default.ScreenLockPortrait
                    2 -> Icons.Default.ScreenLockLandscape
                    else -> Icons.Default.ScreenRotation
                },
                tint = if (currentOrientationModeState.intValue == 0) kaptureIconColor else Color(0xFF2196F3)
            ) {
                currentOrientationModeState.intValue = (currentOrientationModeState.intValue + 1) % 3
                android.widget.Toast.makeText(context, when(currentOrientationModeState.intValue) {
                    1 -> "Locked: Portrait"
                    2 -> "Locked: Landscape"
                    else -> "Orientation: Auto"
                }, android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // 5. System Controls
        if (config.showShortcuts && config.shortcuts.isNotEmpty()) {
            items.add { SmallHudButton(Icons.Default.Launch, if (showShortcuts) Color(0xFFEB3B2E) else kaptureIconColor, toggleShortcuts) }
        }
        items.add { SmallHudButton(Icons.Default.Remove, kaptureSecondaryIcon, toggleMinimize) }
        items.add { SmallHudButton(Icons.Default.Close, kaptureSecondaryIcon, { hide() }) }

        items.forEachIndexed { index, item ->
            if (index > 0) {
                if (isVertical) {
                    Box(Modifier.fillMaxWidth().height(0.7.dp).background(Color(0xFFE8E8E8)))
                } else {
                    Box(Modifier.fillMaxHeight().width(0.7.dp).background(Color(0xFFE8E8E8)))
                }
            }
            item()
        }

        AnimatedVisibility(
            visible = isDrawingMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(8.dp).width(IntrinsicSize.Min),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.fillMaxWidth().height(0.7.dp).background(Color(0xFFE8E8E8)))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    IconButton(onClick = { undo() }, enabled = drawPaths.isNotEmpty()) {
                        Icon(Icons.Default.Undo, null, tint = if (drawPaths.isNotEmpty()) kaptureIconColor else kaptureSecondaryIcon.copy(0.5f))
                    }
                    IconButton(onClick = { redo() }, enabled = undonePaths.isNotEmpty()) {
                        Icon(Icons.Default.Redo, null, tint = if (undonePaths.isNotEmpty()) kaptureIconColor else kaptureSecondaryIcon.copy(0.5f))
                    }
                    IconButton(onClick = { captureDrawingLayer() }) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFFEB3B2E))
                    }
                }
                Box(Modifier.fillMaxWidth().height(0.7.dp).background(Color(0xFFE8E8E8)))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (recentColors + listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Cyan, Color.Magenta)).distinct().take(10).forEach { color ->
                        Box(
                            modifier = Modifier.size(24.dp).background(color, CircleShape)
                                .border(width = if (currentColor == color) 2.dp else 0.dp, color = kaptureIconColor, shape = CircleShape)
                                .clickable { selectColor(color) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LineWeight, null, tint = kaptureIconColor, modifier = Modifier.size(16.dp))
                    androidx.compose.material3.Slider(
                        value = currentWidth,
                        onValueChange = { currentWidth = it },
                        valueRange = 2f..20f,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "Drawing_${System.currentTimeMillis()}.png"
        var fos: java.io.OutputStream? = null
        try {
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                 val contentValues = android.content.ContentValues().apply {
                     put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                     put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                     put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/BokBok/Drawings")
                 }
                 val imageUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                 if (imageUri != null) fos = context.contentResolver.openOutputStream(imageUri)
             } else {
                 val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/BokBok/Drawings"
                 val file = java.io.File(imagesDir)
                 if (!file.exists()) file.mkdirs()
                 val image = java.io.File(imagesDir, filename)
                 fos = java.io.FileOutputStream(image)
             }
             fos?.use {
                 bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                 android.widget.Toast.makeText(context, "Drawing Saved!", android.widget.Toast.LENGTH_SHORT).show()
             }
        } catch (e: Exception) {
             android.widget.Toast.makeText(context, "Failed to save drawing", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchApp(pkg: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    private fun getAppIcon(pkg: String): ImageBitmap? {
        return try {
            val icon = context.packageManager.getApplicationIcon(pkg)
            val bitmap = Bitmap.createBitmap(icon.intrinsicWidth, icon.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            icon.setBounds(0, 0, canvas.width, canvas.height)
            icon.draw(canvas)
            bitmap.asImageBitmap()
        } catch (_: Exception) { null }
    }

    @Composable
    private fun SmallHudButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color(0xFF2B2B2B), onClick: () -> Unit) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        }
    }

    @Composable
    private fun AudioLevelMeters(mic: Float, internal: Float, isVertical: Boolean) {
        val micLevel by animateFloatAsState(targetValue = mic.coerceIn(0f, 1f), animationSpec = tween(100), label = "")
        val intLevel by animateFloatAsState(targetValue = internal.coerceIn(0f, 1f), animationSpec = tween(100), label = "")
        
        if (isVertical) {
            Row(modifier = Modifier.width(32.dp).height(4.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                LevelBar(micLevel, Modifier.weight(1f), Color(0xFF00E676))
                LevelBar(intLevel, Modifier.weight(1f), Color(0xFF2979FF))
            }
        } else {
            Column(modifier = Modifier.width(32.dp).height(4.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                LevelBar(micLevel, Modifier.fillMaxWidth(), Color(0xFF00E676))
                LevelBar(intLevel, Modifier.fillMaxWidth(), Color(0xFF2979FF))
            }
        }
    }

    @Composable
    private fun LevelBar(level: Float, modifier: Modifier, color: Color) {
        Box(modifier = modifier.background(Color.White.copy(0.1f), RoundedCornerShape(1.dp))) {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(level).background(color, RoundedCornerShape(1.dp)))
        }
    }

    private fun formatTime(seconds: Long): String {
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d", mins, secs)
    }

    inner class DraggableHUDContainer(context: Context) : FrameLayout(context) {
        val hudComposeView = ComposeView(context)
        
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var isDragging = false
        private var touchDownTime = 0L
        private var touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
        
        var isMinimizedHUD = false
        var onSwipeMaximize: (() -> Unit)? = null
        
        private var wm: WindowManager? = null
        private var lp: WindowManager.LayoutParams? = null
        private var onDragRelease: (() -> Unit)? = null

        init { addView(hudComposeView) }

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
                    touchDownTime = System.currentTimeMillis()
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(ev.rawX - initialTouchX)
                    val dy = Math.abs(ev.rawY - initialTouchY)
                    
                    if (isMinimizedHUD && dx > 90) {
                        onSwipeMaximize?.invoke()
                        return true
                    }

                    if (dx > touchSlop || dy > touchSlop) {
                        isDragging = true
                        lp.x = (initialX + (ev.rawX - initialTouchX)).toInt()
                        lp.y = (initialY + (ev.rawY - initialTouchY)).toInt()
                        wm.updateViewLayout(this, lp)
                    }
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchDownTime
                    if (elapsed <= 200 && !isDragging) {
                        performClick()
                    } else if (isDragging) {
                        onDragRelease?.invoke()
                        return true
                    }
                }
            }
            return super.dispatchTouchEvent(ev)
        }

        fun animateMinimized(minimized: Boolean) {
            val lp = this.lp ?: return
            val wm = this.wm ?: return
            val screenWidth = context.resources.displayMetrics.widthPixels
            val viewWidth = this.width
            
            val offset = (viewWidth * 0.7).toInt() 
            val targetX = if (lp.x + viewWidth / 2 < screenWidth / 2) {
                if (minimized) -offset else 0
            } else {
                if (minimized) screenWidth - (viewWidth - offset) else screenWidth - viewWidth
            }

            ValueAnimator.ofInt(lp.x, targetX).apply {
                duration = 350
                interpolator = android.view.animation.LinearInterpolator()
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
    val points: androidx.compose.runtime.snapshots.SnapshotStateList<androidx.compose.ui.geometry.Offset>,
    val color: Color = Color.Red,
    val width: Float = 4f
)
