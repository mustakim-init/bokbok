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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Launch
import android.content.pm.PackageManager
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.LineWeight
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.ScreenLockPortrait
import androidx.compose.material.icons.filled.ScreenLockLandscape
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
    private var isDrawingModeEnabled = false

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
            PixelFormat.TRANSPARENT // Use TRANSPARENT for drawing layer
        )

        hudContainer.hudComposeView.setContent {
            FloatingHUD(currentConfig)
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
        // Initially only add the HUD. The drawing layer is added on demand to avoid "weird overlay" issues.
        // windowManager.addView(drawingComposeView, drawingLayoutParams)
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
        isDrawingModeEnabled = enabled
        drawingLayoutParams.flags = if (enabled) {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        
        try {
            if (enabled) {
                // Add view if not added or update if exists
                try {
                    windowManager.addView(drawingComposeView, drawingLayoutParams)
                } catch (e: Exception) {
                    windowManager.updateViewLayout(drawingComposeView, drawingLayoutParams)
                }
            } else {
                // Remove view when not in drawing mode to ensure no interference
                try {
                    windowManager.removeView(drawingComposeView)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private val drawPaths = mutableStateListOf<DrawPath>()
    private val undonePaths = mutableStateListOf<DrawPath>() // Changed to StateList for UI reactivity
    private val recentColors = mutableStateListOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White) // Default recent colors
    
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
        // Update recent colors (move to front if exists, else add)
        if (recentColors.contains(color)) {
            recentColors.remove(color)
        }
        recentColors.add(0, color)
        if (recentColors.size > 5) recentColors.removeLast()
    }
    
    /**
     * Captures just the drawing layer to a bitmap and saves it.
     */
    private fun captureDrawingLayer() {
        // Implement generic View PixelCopy or Bitmap creation
        val bitmap = Bitmap.createBitmap(drawingComposeView.width, drawingComposeView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawingComposeView.draw(canvas)
        
        // Save using existing utility (assuming KFile-like utility or just direct IO)
        // For now, reuse the callback but strictly for drawing? 
        // Or better, trigger the standard screenshot callback but modify logic?
        // Actually, let's create a specific method for this.
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
                            undonePaths.clear() // Clear redo history on new draw
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
        var isExpanded by remember { mutableStateOf(!config.startMinimized) }
        var isPaused by remember { mutableStateOf(false) }
        var isMinimized by remember { mutableStateOf(config.startMinimized) }
        var isDrawingMode by remember { mutableStateOf(false) }
        var showShortcuts by remember { mutableStateOf(false) }
        
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
            if (isMinimized) {
                isExpanded = false
                showShortcuts = false
            }
            hudContainer.animateMinimized(isMinimized)
        }

        val menuAlpha by animateFloatAsState(
            targetValue = if (isMinimized) 0.6f else 1f,
            animationSpec = tween(300), label = ""
        )

        Surface(
            modifier = Modifier
                .wrapContentSize()
                .alpha(menuAlpha),
            shape = RoundedCornerShape(20.dp), // Kapture Parity
            color = Color(0xCC1A1A1A),
            tonalElevation = 12.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.15f))
        ) {
            val isVertical = config.menuStyle == 1
            
            if (isVertical) {
                Column(
                    modifier = Modifier.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp) // Kapture parity
                ) {
                    HUDContent(config, isVertical, isExpanded, isPaused, isMinimized, isDrawingMode, showShortcuts, elapsedSeconds,
                        toggleExpand = { isExpanded = !isExpanded },
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
                    modifier = Modifier.padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp) // Kapture parity
                ) {
                    HUDContent(config, isVertical, isExpanded, isPaused, isMinimized, isDrawingMode, showShortcuts, elapsedSeconds,
                        toggleExpand = { isExpanded = !isExpanded },
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
        isExpanded: Boolean,
        isPaused: Boolean,
        isMinimized: Boolean,
        isDrawingMode: Boolean,
        showShortcuts: Boolean,
        elapsedSeconds: Long,
        toggleExpand: () -> Unit,
        togglePause: () -> Unit,
        toggleMinimize: () -> Unit,
        toggleDrawing: () -> Unit,
        toggleShortcuts: () -> Unit
    ) {
        // Handle/Pill when minimized
        if (isMinimized) {
            Box(
                modifier = Modifier
                    .size(if (isVertical) 36.dp else 5.dp, if (isVertical) 5.dp else 36.dp)
                    .background(Color.White.copy(0.3f), RoundedCornerShape(100.dp))
                    .clickable { toggleMinimize() }
            )
            return
        }

        // Timer and Countdown (if enabled)
        if (config.showTimeOnMenu) {
            val autoStopSeconds = config.autoStopDurationMinutes * 60L
            val remainingSeconds = if (autoStopSeconds > 0) (autoStopSeconds - elapsedSeconds).coerceAtLeast(0) else -1L

            Column(
                modifier = Modifier.padding(horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).background(if (isPaused) Color.Yellow else Color.Red, CircleShape))
                    Text(formatTime(elapsedSeconds), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                
                if (remainingSeconds >= 0) {
                    Text(
                        "Ends in: ${formatTime(remainingSeconds)}", 
                        color = if (remainingSeconds < 60) Color.Red else Color.LightGray, 
                        fontSize = 9.sp
                    )
                }

                // Audio Levels
                if (!isMinimized) {
                    Spacer(modifier = Modifier.height(2.dp))
                    AudioLevelMeters(micRms, internalRms, isVertical)
                }
            }
        }

        // Toggle Expand/Collapse
        IconButton(onClick = toggleExpand, modifier = Modifier.size(36.dp)) {
            Icon(if (isExpanded) Icons.Default.Close else Icons.Default.Menu, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }

        AnimatedVisibility(visible = isExpanded) {
            if (isVertical) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) { ExpandableButtons(config, isPaused, isDrawingMode, showShortcuts, togglePause, toggleDrawing, toggleShortcuts, toggleMinimize) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { ExpandableButtons(config, isPaused, isDrawingMode, showShortcuts, togglePause, toggleDrawing, toggleShortcuts, toggleMinimize) }
            }
        }
        
        // App Shortcuts Section
        if (showShortcuts && config.shortcuts.isNotEmpty()) {
            if (isVertical) {
                Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { ShortcutList(config.shortcuts) }
            } else {
                Row(modifier = Modifier.padding(start = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { ShortcutList(config.shortcuts) }
            }
        }

        // Expanded Drawing Controls
        AnimatedVisibility(
            visible = isExpanded && isDrawingMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 8.dp, start = 8.dp, end = 8.dp)
                    .width(IntrinsicSize.Min),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = Color.White.copy(0.1f))

                // Undo / Redo / Screenshot Draw
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    IconButton(onClick = { undo() }, enabled = drawPaths.isNotEmpty()) {
                        Icon(
                            Icons.Default.Undo, 
                            null, 
                            tint = if (drawPaths.isNotEmpty()) Color.White else Color.White.copy(0.3f)
                        )
                    }
                    
                    IconButton(onClick = { redo() }, enabled = undonePaths.isNotEmpty()) {
                        Icon(
                            Icons.Default.Redo, 
                            null, 
                            tint = if (undonePaths.isNotEmpty()) Color.White else Color.White.copy(0.3f)
                        )
                    }
                    
                    // Screenshot Drawing Only
                    IconButton(onClick = { captureDrawingLayer() }) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color.Cyan)
                    }
                }
                
                HorizontalDivider(color = Color.White.copy(0.1f))
                
                // Color History
                if (recentColors.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        recentColors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(color, CircleShape)
                                    .border(
                                        width = if (currentColor == color) 2.dp else 0.dp,
                                        color = Color.White,
                                        shape = CircleShape
                                    )
                                    .clickable { selectColor(color) }
                            )
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(0.1f))
                }
                
                // Colors (Standard Palette) - Modified to use selectColor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White, Color.Cyan, Color.Magenta).forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(color, CircleShape)
                                .border(
                                    width = if (currentColor == color) 2.dp else 0.dp,
                                    color = Color.White,
                                    shape = CircleShape
                                )
                                .clickable { selectColor(color) }
                        )
                    }
                }

                // Stroke Width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.LineWeight, null, tint = Color.White, modifier = Modifier.size(16.dp))
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
    
    // Helper to save bitmap
    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val filename = "Drawing_${System.currentTimeMillis()}.png"
        var fos: java.io.OutputStream? = null
        try {
             // For Android Q and above used scoped storage via MediaStore
             if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                 val contentValues = android.content.ContentValues().apply {
                     put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                     put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                     put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/BokBok/Drawings")
                 }
                 val imageUri = context.contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                 if (imageUri != null) {
                     fos = context.contentResolver.openOutputStream(imageUri)
                 }
             } else {
                 // For older versions, direct file access
                 val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/BokBok/Drawings"
                 val file = java.io.File(imagesDir)
                 if (!file.exists()) {
                     file.mkdirs()
                 }
                 val image = java.io.File(imagesDir, filename)
                 fos = java.io.FileOutputStream(image)
             }
             
             fos?.use {
                 bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                 android.widget.Toast.makeText(context, "Drawing Saved!", android.widget.Toast.LENGTH_SHORT).show()
             }
        } catch (e: Exception) {
            android.util.Log.e("RecordingOverlay", "Error saving drawing", e)
             android.widget.Toast.makeText(context, "Failed to save drawing", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @Composable
    private fun ExpandableButtons(
        config: RecordConfig, 
        isPaused: Boolean, 
        isDrawingMode: Boolean, 
        showShortcuts: Boolean,
        togglePause: () -> Unit, 
        toggleDrawing: () -> Unit,
        toggleShortcuts: () -> Unit,
        toggleMinimize: () -> Unit
    ) {
        if (config.showPauseResumeOnMenu) {
            SmallHudButton(if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (isPaused) Color.Green else Color.White, togglePause)
        }
        
        if (config.showCameraButtonOnMenu) {
            SmallHudButton(Icons.Default.PhotoCamera, Color.White) { onToggleFacecamCallback?.invoke() }
        }

        if (config.showDrawButtonOnMenu) {
            SmallHudButton(Icons.Default.Edit, if (isDrawingMode) Color.Cyan else Color.White, toggleDrawing)
        }

        if (config.showScreenshotButtonOnMenu) {
            SmallHudButton(Icons.Default.CameraAlt, Color.White) { onTakeScreenshotCallback?.invoke() }
        }

        // Orientation Toggle
        var currentOrientationMode by remember { mutableIntStateOf(0) } // 0: Auto, 1: Portrait, 2: Landscape
        SmallHudButton(
            icon = when(currentOrientationMode) {
                1 -> Icons.Default.ScreenLockPortrait
                2 -> Icons.Default.ScreenLockLandscape
                else -> Icons.Default.ScreenRotation
            },
            tint = if (currentOrientationMode == 0) Color.White else Color.Cyan
        ) {
            currentOrientationMode = (currentOrientationMode + 1) % 3
            // TODO: In a real implementation, this would send an intent to ScreenRecordService
            // to update the virtual display orientation.
            android.widget.Toast.makeText(context, when(currentOrientationMode) {
                1 -> "Locked: Portrait"
                2 -> "Locked: Landscape"
                else -> "Orientation: Auto"
            }, android.widget.Toast.LENGTH_SHORT).show()
        }
        
        if (config.useWatermarkText || config.useWatermarkImage) {
            SmallHudButton(Icons.Default.WaterDrop, Color.White) { onToggleWatermarkCallback?.invoke() }
        }

        if (config.showShortcuts && config.shortcuts.isNotEmpty()) {
            SmallHudButton(Icons.Default.Launch, if (showShortcuts) Color.Yellow else Color.White, toggleShortcuts)
        }

        SmallHudButton(Icons.Default.Close, Color.White, toggleMinimize)

        IconButton(onClick = { hide(); onStopCallback?.invoke() }, modifier = Modifier.size(36.dp).background(Color.Red.copy(0.2f), CircleShape)) {
            Icon(Icons.Default.Stop, null, tint = Color.Red, modifier = Modifier.size(20.dp))
        }
    }

    @Composable
    private fun ShortcutList(shortcuts: List<String>) {
        shortcuts.take(4).forEach { pkg ->
            IconButton(onClick = { launchApp(pkg) }, modifier = Modifier.size(36.dp)) {
                val icon = remember { getAppIcon(pkg) }
                if (icon != null) {
                    androidx.compose.foundation.Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(24.dp).alpha(0.8f))
                } else {
                    Icon(Icons.Default.Launch, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
                }
            }
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
    private fun SmallHudButton(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color = Color.White, onClick: () -> Unit) {
        IconButton(onClick = onClick, modifier = Modifier.size(36.dp)) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
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
        Box(
            modifier = modifier
                .background(Color.White.copy(0.1f), RoundedCornerShape(1.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(level)
                    .background(color, RoundedCornerShape(1.dp))
            )
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
            
            val offset = (viewWidth * 0.7).toInt() // Slightly more hidden - Kapture style
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
    val points: androidx.compose.runtime.snapshots.SnapshotStateList<androidx.compose.ui.geometry.Offset>,
    val color: Color = Color.Red,
    val width: Float = 4f
)
