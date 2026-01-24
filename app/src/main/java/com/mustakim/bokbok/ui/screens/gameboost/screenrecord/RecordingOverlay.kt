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
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.res.painterResource
import com.mustakim.bokbok.R
import coil.compose.AsyncImage
import com.mustakim.bokbok.utils.AppIcon

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

    fun setVisibility(visible: Boolean) {
        hudContainer.visibility = if (visible) View.VISIBLE else View.GONE
        drawingComposeView.visibility = if (visible) View.VISIBLE else View.GONE
    }
    
    private object KaptureDimens {
        val MenuRadius = 20.dp
        val BorderWidth = 0.7.dp
        
        val IconMainSize = 36.dp
        val IconSecondarySize = 25.dp
        val IconPadding = 5.5.dp
        val IconSecondaryPadding = 3.5.dp
        val DividerSpacer = 3.dp
        
        val MinimizedWidth = 5.dp
        val MinimizedHeight = 36.dp
    }
    
    private object KaptureColors {
        val BackgroundDay = Color(0xD2FFFFFF) 
        val BackgroundNight = Color(0xD2000000)
        
        val BorderDay = Color(0xFF808080)
        val BorderNight = Color(0xFF5E5E5E)
        
        val IconDay = Color(0xFF2B2B2B)
        val IconNight = Color(0xFFEDEDED)
        
        val IconSecondaryDay = Color(0xFF808080)
        val IconSecondaryNight = Color(0xFF5E5E5E)
        
        val TextDay = Color(0xFF2B2B2B)
        val TextNight = Color(0xFFEDEDED)
        
        val TextSecondaryDay = Color(0xFF5E5E5E)
        val TextSecondaryNight = Color(0xFF979797)
        
        val StopBackground = Color(0xFFEB3B2E)
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
        
        // Add drawing view FIRST so it is below the HUD
        // Initially not touchable
        drawingLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowManager.addView(drawingComposeView, drawingLayoutParams)
        
        // Add HUD view SECOND so it is on top
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
        
        // Just update flags; the view is already added at the bottom of the stack
        drawingLayoutParams.flags = if (enabled) {
            // Touchable
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        } else {
            // Not touchable
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        }
        
        try {
            windowManager.updateViewLayout(drawingComposeView, drawingLayoutParams)
        } catch (_: Exception) {}
    }

    private val drawPaths = mutableStateListOf<DrawPath>()
    private val undonePaths = mutableStateListOf<DrawPath>()
    private val recentColors = mutableStateListOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.White)
    
    private var currentColor by mutableStateOf(Color.Red)
    private var currentWidth by mutableFloatStateOf(4f)


    private fun undo() {
        if (drawPaths.isNotEmpty()) {
            val last = drawPaths.removeAt(drawPaths.lastIndex)
            undonePaths.add(last)
        }
    }

    private fun redo() {
        if (undonePaths.isNotEmpty()) {
            val last = undonePaths.removeAt(undonePaths.lastIndex)
            drawPaths.add(last)
        }
    }
    
    private fun selectColor(color: Color) {
        currentColor = color
        if (recentColors.contains(color)) {
            recentColors.remove(color)
        }
        recentColors.add(0, color)
        if (recentColors.size > 5) recentColors.removeAt(recentColors.lastIndex)
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
                    HUDItems(config, isVertical, isPaused, isMinimized, isDrawingMode, showShortcuts, elapsedSeconds,
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
                    HUDItems(config, isVertical, isPaused, isMinimized, isDrawingMode, showShortcuts, elapsedSeconds,
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
    private fun HUDItems(
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

        val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
        val iconColor = if (isDarkTheme) KaptureColors.IconNight else KaptureColors.IconDay
        val iconSecondaryColor = if (isDarkTheme) KaptureColors.IconSecondaryNight else KaptureColors.IconSecondaryDay
        
        val items = mutableListOf<@Composable () -> Unit>()

        // 1. Padding + Stop Button
        // Added a 4dp spacer at the start to prevent the button from hugging the edge
        items.add { 
            Spacer(modifier = if (isVertical) Modifier.height(4.dp) else Modifier.width(4.dp))
        }

        items.add {
            Box(
                modifier = Modifier
                    .size(KaptureDimens.IconMainSize) // 36dp circle
                    .background(KaptureColors.StopBackground, CircleShape)
                    .clickable { hide(); onStopCallback?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                // Centered white square
                androidx.compose.foundation.Canvas(modifier = Modifier.size(14.dp)) {
                    drawRoundRect(color = Color.White, cornerRadius = CornerRadius(2.dp.toPx()))
                }
            }
        }

        // Helper for Dividers (Transparent Spacer)
        val Divider = @Composable {
            if (isVertical) {
                Spacer(Modifier.height(KaptureDimens.DividerSpacer))
            } else {
                Spacer(Modifier.width(KaptureDimens.DividerSpacer))
            }
        }

        // 2. Tools
        if (config.showPauseResumeOnMenu) {
            items.add { 
                KaptureIconBtn(
                    icon = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, 
                    tint = if (isPaused) Color(0xFF4CAF50) else iconColor, 
                    size = KaptureDimens.IconMainSize,
                    padding = KaptureDimens.IconPadding,
                    onClick = togglePause
                ) 
            }
        }
        
        if (config.showCameraButtonOnMenu || config.showFacecam) {
            items.add { 
                KaptureIconBtn(
                    icon = Icons.Default.Videocam, 
                    tint = iconColor,
                    size = KaptureDimens.IconMainSize,
                    padding = KaptureDimens.IconPadding,
                    onClick = { onToggleFacecamCallback?.invoke() }
                ) 
            }
        }
        
        if (config.showDrawButtonOnMenu) {
            items.add { 
                KaptureIconBtn(
                    icon = Icons.Default.Edit, 
                    tint = if (isDrawingMode) Color(0xFF2196F3) else iconColor,
                    size = KaptureDimens.IconMainSize,
                    padding = KaptureDimens.IconPadding,
                    onClick = toggleDrawing
                ) 
            }
        }
        
        if (config.showScreenshotButtonOnMenu) {
            items.add { 
                KaptureIconBtn(
                    icon = Icons.Default.Screenshot, 
                    tint = iconColor,
                    size = KaptureDimens.IconMainSize,
                    padding = KaptureDimens.IconPadding,
                    onClick = { onTakeScreenshotCallback?.invoke() }
                ) 
            }
        }

        if (config.showShortcuts && config.shortcuts.isNotEmpty()) {
             items.add {
                KaptureIconBtn(
                    icon = Icons.Default.Launch,
                    tint = if (showShortcuts) Color(0xFFEB3B2E) else iconColor,
                    size = KaptureDimens.IconMainSize,
                    padding = KaptureDimens.IconPadding,
                    onClick = toggleShortcuts
                )
             }
             
             if (showShortcuts) {
                 config.shortcuts.forEach { pkg ->
                     items.add {
                         Box(
                             modifier = Modifier
                                 .size(KaptureDimens.IconMainSize)
                                 .clip(CircleShape)
                                 .background(Color.White.copy(alpha = 0.1f))
                                 .clickable { launchApp(pkg) },
                             contentAlignment = Alignment.Center
                         ) {
                             AsyncImage(
                                 model = AppIcon(pkg),
                                 contentDescription = null,
                                 modifier = Modifier.fillMaxSize().padding(KaptureDimens.IconPadding)
                             )
                         }
                     }
                 }
             }
        }

        // 3. Time
        if (config.showTimeOnMenu) {
            items.add {
                val autoStopSeconds = config.autoStopDurationMinutes * 60L
                val remainingSeconds = if (autoStopSeconds > 0) (autoStopSeconds - elapsedSeconds).coerceAtLeast(0) else -1L

                Column(
                    modifier = Modifier.padding(
                        horizontal = if (isVertical) 0.dp else 4.dp, 
                        vertical = if (isVertical) 4.dp else 0.dp
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = formatTime(elapsedSeconds),
                        color = if (isDarkTheme) KaptureColors.TextNight else KaptureColors.TextDay,
                        fontSize = if (isVertical) 12.sp else 13.sp,
                        fontWeight = FontWeight.Bold 
                    )
                    
                    if (remainingSeconds >= 0) {
                        Text(
                            "/ ${formatTime(remainingSeconds)}", 
                            color = if (isDarkTheme) KaptureColors.TextSecondaryNight else KaptureColors.TextSecondaryDay,
                            fontSize = if (isVertical) 10.sp else 11.sp
                        )
                    }
                }
            }
        }

        // 4. Minimize (Chevron)
        items.add {
            KaptureIconBtn(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowRight, 
                tint = iconSecondaryColor,
                size = KaptureDimens.IconSecondarySize,
                padding = 1.dp, 
                onClick = toggleMinimize
            )
        }
        
        // 5. Close Button (Restored per user request)
        items.add {
            KaptureIconBtn(
                icon = Icons.Default.Close, 
                tint = iconSecondaryColor,
                size = KaptureDimens.IconSecondarySize,
                padding = 3.dp,
                onClick = { hide() }
            )
        }

        // Add items with dividers
        items.forEachIndexed { index, item ->
            item()
            if (index < items.lastIndex) {
                 Divider()
            }
        }
        
        // Drawing Palette Overlay (Keep existing logic)
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
                        Icon(Icons.Default.Undo, null, tint = if (drawPaths.isNotEmpty()) iconColor else iconSecondaryColor.copy(0.5f))
                    }
                    IconButton(onClick = { redo() }, enabled = undonePaths.isNotEmpty()) {
                        Icon(Icons.Default.Redo, null, tint = if (undonePaths.isNotEmpty()) iconColor else iconSecondaryColor.copy(0.5f))
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
                                .border(width = if (currentColor == color) 2.dp else 0.dp, color = iconColor, shape = CircleShape)
                                .clickable { selectColor(color) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.LineWeight, null, tint = iconColor, modifier = Modifier.size(16.dp))
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
        // Redundant with Coil migration, keeping but tagging for removal or safe fallback if needed outside Compose
        return null 
    }

    @Composable
    private fun KaptureIconBtn(
        icon: androidx.compose.ui.graphics.vector.ImageVector, 
        tint: Color, 
        size: Dp,
        padding: Dp,
        onClick: () -> Unit
    ) {
        IconButton(
            onClick = onClick, 
            modifier = Modifier.size(size)
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = tint, 
                modifier = Modifier.fillMaxSize().padding(padding) 
            )
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
