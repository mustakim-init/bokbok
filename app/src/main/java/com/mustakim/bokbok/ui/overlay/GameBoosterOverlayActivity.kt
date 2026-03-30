package com.mustakim.bokbok.ui.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.provider.Settings
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.app.Service
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.mustakim.bokbok.data.model.CpuInfo
import com.mustakim.bokbok.data.model.GpuInfo
import com.mustakim.bokbok.data.model.RamInfo
import com.mustakim.bokbok.data.repository.DeviceMonitorRepository
import com.mustakim.bokbok.data.repository.GameRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import com.mustakim.bokbok.ui.theme.BokBokTheme
import android.widget.Toast
import com.mustakim.bokbok.data.fps.FpsDaemonManager

@AndroidEntryPoint
class GameBoosterOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val gamePackage = intent?.getStringExtra("GAME_PACKAGE") ?: "Unknown"

        val serviceIntent = Intent(this, GameBoosterOverlayService::class.java).apply {
            putExtra("GAME_PACKAGE", gamePackage)
        }
        startService(serviceIntent)
        finish()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val action = intent.getStringExtra("ACTION")
        val gamePackage = intent.getStringExtra("GAME_PACKAGE")
        
        val serviceIntent = Intent(this, GameBoosterOverlayService::class.java).apply {
            if (action != null) putExtra("ACTION", action)
            if (gamePackage != null) putExtra("GAME_PACKAGE", gamePackage)
        }
        startService(serviceIntent)
        finish()
    }
}

data class AppShortcut(val name: String, val pkg: String, val bgColor: Color)

@AndroidEntryPoint
class GameBoosterOverlayService : Service() {
    
    @Inject
    lateinit var gameRepository: GameRepository
    
    @Inject
    lateinit var deviceMonitorRepository: DeviceMonitorRepository

    private var manager: GameBoosterOverlayManager? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.getStringExtra("ACTION")
        val gamePackage = intent?.getStringExtra("GAME_PACKAGE") ?: "Unknown"

        android.util.Log.d("BB-OVERLAY", "onStartCommand: action=$action, game=$gamePackage")

        if (action == "CLOSE") {
            android.util.Log.i("BB-OVERLAY", "Received CLOSE action. Dismissing overlay.")
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                gameRepository.revertAllOptimizations()
            }
            manager?.hide()
            manager = null
            stopSelf()
            return START_NOT_STICKY
        }

        if (manager == null) {
            manager = GameBoosterOverlayManager(this, gameRepository, deviceMonitorRepository)
            manager?.show(gamePackage)
        } else {
            manager?.updateGame(gamePackage)
        }

        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        manager?.hide()
        super.onDestroy()
    }
}

class GameBoosterOverlayManager(
    private val context: Context,
    private val gameRepository: GameRepository,
    private val deviceMonitorRepository: DeviceMonitorRepository
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var handleComposeView: ComposeView? = null
    private var panelComposeView: ComposeView? = null

    // Isolated Telemetry State
    private val cpuState = mutableStateOf(CpuInfo())
    private val gpuState = mutableStateOf(GpuInfo())
    private val ramState = mutableStateOf(RamInfo())
    private val gameFpsState = mutableFloatStateOf(0f)
    private var fpsDaemon: com.mustakim.bokbok.data.fps.FpsDaemonManager? = null

    // UI States
    private val isExpanded = mutableStateOf(false)
    private val currentBrightness = mutableFloatStateOf(0f)
    private val isAutoRotate = mutableStateOf(false)
    private val isMistouchEnabled = mutableStateOf(false)
    private val isDndEnabled = mutableStateOf(false)

    private var isHandleVisible = false

    private val handleParams: WindowManager.LayoutParams
    private val panelParams: WindowManager.LayoutParams

    private val supportedRefreshRates = mutableStateOf<List<Int>>(emptyList())
    private val currentRefreshRate = mutableIntStateOf(60)

    private val currentPerformanceMode = mutableStateOf("Balanced")
    private val isPerformanceModeSupported = mutableStateOf(false)

    private val appShortcuts = mutableStateOf<List<AppShortcut>>(emptyList())
    private var currentGamePackage = mutableStateOf("Unknown")

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val settingsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            updateSettingsState()
        }
    }

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        updateSettingsState()
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, settingsObserver)
        context.contentResolver.registerContentObserver(Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION), false, settingsObserver)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor("zen_mode"), false, settingsObserver)

        lifecycleScope.launch(Dispatchers.Main) {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fetchSupportedRefreshRates()
        fetchPerformanceGovernors()
        loadAppShortcuts()

        handleComposeView = ComposeView(context)
        panelComposeView = ComposeView(context)

        handleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        handleComposeView?.setContent { MinimizedHandleUI(onExpand = { expand() }) }

        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        panelComposeView?.setContent {
            BokBokTheme {
                ExpandedPanelUI(
                    gamePkg = currentGamePackage.value,
                    cpuInfo = cpuState.value,
                    gpuInfo = gpuState.value,
                    ramInfo = ramState.value,
                    fps = gameFpsState.floatValue,
                    brightness = currentBrightness.floatValue,
                    onBrightnessChange = { setBrightness(it) },
                    isAutoRotate = isAutoRotate.value,
                    onToggleAutoRotate = { toggleAutoRotate() },
                    supportedRefreshRates = supportedRefreshRates.value,
                    currentRefreshRate = currentRefreshRate.intValue,
                    onRefreshRateChange = { setRefreshRate(it) },
                    isPerformanceModeSupported = isPerformanceModeSupported.value,
                    currentPerformanceMode = currentPerformanceMode.value,
                    onPerformanceModeChange = { setPerformanceMode(it) },
                    onMemoryCleanup = { runMemoryCleanup() },
                    isMistouchEnabled = isMistouchEnabled.value,
                    onToggleMistouch = { toggleMistouchPrevention() },
                    isDndEnabled = isDndEnabled.value,
                    onToggleDnd = { toggleDnd() },
                    onScreenRecord = { runScreenRecord() },
                    onScreenshot = { takeScreenshot() },
                    appShortcuts = appShortcuts.value,
                    onLaunchApp = { launchApp(it) },
                    onCollapse = { collapse() }
                )
            }
        }

        listOf(handleComposeView, panelComposeView).forEach { view ->
            view?.setViewTreeLifecycleOwner(this)
            view?.setViewTreeViewModelStoreOwner(this)
            view?.setViewTreeSavedStateRegistryOwner(this)
        }

        startSmartPolling()
        fpsDaemon = FpsDaemonManager(context).also {
            it.start(
                lifecycleScope,
                { cmd -> gameRepository.executeShizukuCommand(cmd) },
                { cmd -> gameRepository.executeShizukuCommandAndGet(cmd) },
                { cmd -> gameRepository.executeShizukuCommandRaw(cmd) }
            )
        }
    }

    fun show(gamePackage: String) {
        if (currentGamePackage.value != gamePackage) {
            currentGamePackage.value = gamePackage
            lifecycleScope.launch { gameRepository.applyOptimizations(gamePackage) }
            lifecycleScope.launch(Dispatchers.IO) {
                fpsDaemon?.setTargetPackage(gamePackage) { cmd ->
                    gameRepository.executeShizukuCommandAndGet(cmd)
                }
            }
        }
        if (!isHandleVisible && !isExpanded.value) {
            try {
                windowManager.addView(handleComposeView, handleParams)
                isHandleVisible = true
            } catch (_: Exception) {}
        }
    }

    fun updateGame(gamePackage: String) {
        if (currentGamePackage.value == gamePackage) return
        currentGamePackage.value = gamePackage
        lifecycleScope.launch {
            gameRepository.restoreToMasterSnapshot()
            gameRepository.applyOptimizations(gamePackage)
        }
        lifecycleScope.launch(Dispatchers.IO) {
            fpsDaemon?.setTargetPackage(gamePackage) { cmd ->
                gameRepository.executeShizukuCommandAndGet(cmd)
            }
        }
    }

    private fun startSmartPolling() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                if (isExpanded.value) {
                    try {
                        val cpu = try {
                            deviceMonitorRepository.getCpuInfo()
                        } catch (e: Exception) {
                            cpuState.value
                        }
                        
                        val gpu = try {
                            deviceMonitorRepository.getGpuInfo()
                        } catch (e: Exception) {
                            gpuState.value
                        }
                        
                        val ram = try {
                            deviceMonitorRepository.getRamInfo()
                        } catch (e: Exception) {
                            ramState.value
                        }

                        val shellFps = cpu.fps
                        val nativeFps = fpsDaemon?.fps?.value ?: 0f
                        val combinedFps = if (nativeFps > 0) nativeFps else shellFps

                        withContext(Dispatchers.Main) {
                            cpuState.value = cpu
                            gpuState.value = gpu
                            ramState.value = ram
                            gameFpsState.floatValue = combinedFps
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("BB-OVERLAY", "Polling failed", e)
                    }
                    delay(1000)
                } else {
                    delay(5000)
                }
            }
        }
    }

    private fun updateSettingsState() {
        try {
            val b = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            currentBrightness.floatValue = b / 255f
            isAutoRotate.value = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
            isDndEnabled.value = Settings.Global.getInt(context.contentResolver, "zen_mode", 0) != 0
        } catch (_: Exception) {}
    }

    private fun checkWriteSettingsPermission(): Boolean {
        if (!Settings.System.canWrite(context)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            collapse()
            return false
        }
        return true
    }

    private fun setBrightness(value: Float) {
        if (!checkWriteSettingsPermission()) return
        
        // Optimistically update UI
        currentBrightness.floatValue = value
        
        try {
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val intVal = (value * 255).toInt().coerceIn(0, 255)
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, intVal)
        } catch (_: Exception) {}
    }

    private fun fetchSupportedRefreshRates() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val modes = windowManager.defaultDisplay.supportedModes
            val rates = modes.map { it.refreshRate.toInt() }.distinct().sorted()
            supportedRefreshRates.value = rates.filter { it >= 60 }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val current = gameRepository.executeShizukuCommandAndGet("settings get system peak_refresh_rate").trim().toFloatOrNull()?.toInt()
                if (current != null && current > 0) {
                    withContext(Dispatchers.Main) { currentRefreshRate.intValue = current }
                }
            } catch (_: Exception) {}
        }
    }

    private fun setRefreshRate(rate: Int) {
        currentRefreshRate.intValue = rate
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                gameRepository.executeShizukuCommand("settings put system peak_refresh_rate $rate")
                gameRepository.executeShizukuCommand("settings put system min_refresh_rate $rate")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Refresh rate set to ${rate}Hz", Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {}
        }
    }

    private fun fetchPerformanceGovernors() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val available = gameRepository.executeShizukuCommandAndGet("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").trim()
                if (available.contains("performance") || available.contains("schedutil")) {
                    withContext(Dispatchers.Main) { isPerformanceModeSupported.value = true }
                    val current = gameRepository.executeShizukuCommandAndGet("cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor").trim()
                    withContext(Dispatchers.Main) {
                        currentPerformanceMode.value = when {
                            current.contains("powersave") -> "Battery Saver"
                            current.contains("performance") -> "Monster"
                            else -> "Balanced"
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun setPerformanceMode(mode: String) {
        currentPerformanceMode.value = mode
        lifecycleScope.launch(Dispatchers.IO) {
            val governor = when (mode) {
                "Battery Saver" -> "powersave"
                "Monster" -> "performance"
                else -> "schedutil"
            }
            gameRepository.executeShizukuCommand("for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo \$governor > \"\$f\"; done")
            
            // Toggle Android battery saver Mode
            if (mode == "Battery Saver") {
                gameRepository.executeShizukuCommand("cmd power set-mode 1")
            } else {
                gameRepository.executeShizukuCommand("cmd power set-mode 0")
            }
            
            withContext(Dispatchers.Main) { Toast.makeText(context, "$mode Mode Applied", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun loadAppShortcuts() {
        val targetApps = listOf(
            AppShortcut("Browser", "com.android.chrome", Color(0xFF4285F4)),
            AppShortcut("Discord", "com.discord", Color(0xFF5865F2)),
            AppShortcut("Instagram", "com.instagram.android", Color(0xFFE1306C)),
            AppShortcut("WhatsApp", "com.whatsapp", Color(0xFF25D366)),
            AppShortcut("Settings", "com.android.settings", Color(0xFF757575))
        )
        lifecycleScope.launch(Dispatchers.IO) {
            val installed = try {
                context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()
            } catch (_: Exception) { emptySet() }
            withContext(Dispatchers.Main) {
                appShortcuts.value = targetApps.filter { installed.contains(it.pkg) }
            }
        }
    }

    private fun launchApp(pkg: String) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                collapse()
            }
        } catch (_: Exception) {
            Toast.makeText(context, "Failed to launch $pkg", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleAutoRotate() {
        if (!checkWriteSettingsPermission()) return
        try {
            val newState = if (isAutoRotate.value) 0 else 1
            Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, newState)
        } catch (_: Exception) {}
    }

    private fun runMemoryCleanup() {
        collapse()
        Toast.makeText(context, "Cleaning memory...", Toast.LENGTH_SHORT).show()
        val pkg = currentGamePackage.value
        lifecycleScope.launch(Dispatchers.IO) {
            gameRepository.killBackgroundApps(pkg)
            withContext(Dispatchers.Main) { Toast.makeText(context, "Cleared RAM successfully!", Toast.LENGTH_SHORT).show() }
        }
    }

    private fun toggleMistouchPrevention() {
        val newState = !isMistouchEnabled.value
        isMistouchEnabled.value = newState
        lifecycleScope.launch(Dispatchers.IO) {
            val pkg = currentGamePackage.value
            if (pkg == "Unknown") return@launch
            if (newState) {
                gameRepository.executeShizukuCommand("settings put global policy_control immersive.full=$pkg")
                withContext(Dispatchers.Main) { Toast.makeText(context, "Mistouch Prevention ON", Toast.LENGTH_SHORT).show() }
            } else {
                gameRepository.executeShizukuCommand("settings put global policy_control null")
                withContext(Dispatchers.Main) { Toast.makeText(context, "Mistouch Prevention OFF", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun toggleDnd() {
        val newState = if (isDndEnabled.value) 0 else 1
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                gameRepository.executeShizukuCommand("settings put global zen_mode $newState")
                withContext(Dispatchers.Main) { Toast.makeText(context, if (newState != 0) "DND ON" else "DND OFF", Toast.LENGTH_SHORT).show() }
            } catch (_: Exception) {}
        }
    }

    private fun runScreenRecord() {
        collapse()
        Toast.makeText(context, "Starting Screen Record...", Toast.LENGTH_SHORT).show()
        val rIntent = Intent(context, com.mustakim.bokbok.data.service.ScreenRecordService::class.java).apply { action = "START" }
        context.startService(rIntent)
    }

    private fun takeScreenshot() {
        collapse()
        Handler(Looper.getMainLooper()).postDelayed({
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val timestamp = System.currentTimeMillis()
                    val path = "${android.os.Environment.getExternalStorageDirectory().path}/Pictures/Screenshots/Screenshot_${timestamp}.png"
                    gameRepository.executeShizukuCommand("screencap -p $path")
                    gameRepository.executeShizukuCommand("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file://$path")
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Screenshot saved!", Toast.LENGTH_SHORT).show() }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "Screenshot failed", Toast.LENGTH_SHORT).show() }
                }
            }
        }, 300)
    }

    private fun expand() {
        if (isExpanded.value) return
        isExpanded.value = true
        if (isHandleVisible) {
            windowManager.removeView(handleComposeView)
            isHandleVisible = false
        }
        try { windowManager.addView(panelComposeView, panelParams) } catch (_: Exception) {}
        panelComposeView?.visibility = android.view.View.VISIBLE
    }

    private fun collapse() {
        if (!isExpanded.value) return
        isExpanded.value = false
        panelComposeView?.visibility = android.view.View.GONE
        try { windowManager.removeViewImmediate(panelComposeView) } catch (_: Exception) {}
        if (!isHandleVisible) {
            windowManager.addView(handleComposeView, handleParams)
            isHandleVisible = true
        }
    }

    fun hide() {
        fpsDaemon?.stop()
        fpsDaemon = null
        if (isHandleVisible) {
            try { windowManager.removeView(handleComposeView) } catch (_: Exception) {}
            isHandleVisible = false
        }
        if (isExpanded.value) {
            try { windowManager.removeView(panelComposeView) } catch (_: Exception) {}
            isExpanded.value = false
        }
        try { context.contentResolver.unregisterContentObserver(settingsObserver) } catch (_: Exception) {}
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}

// =====================================================
// COMPOSE UI
// =====================================================

@Composable
fun MinimizedHandleUI(onExpand: () -> Unit) {
    Box(
        modifier = Modifier
            .width(16.dp)
            .height(54.dp)
            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
            .background(Color(0x99000000))
            .clickable { onExpand() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(100.dp))
                .background(Color.White.copy(alpha = 0.7f))
        )
    }
}

enum class OverlayTab { PERFORMANCE, TOOLS }

@Composable
fun ExpandedPanelUI(
    gamePkg: String, 
    cpuInfo: CpuInfo,
    gpuInfo: GpuInfo,
    ramInfo: RamInfo,
    fps: Float,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    isAutoRotate: Boolean,
    onToggleAutoRotate: () -> Unit,
    supportedRefreshRates: List<Int>,
    currentRefreshRate: Int,
    onRefreshRateChange: (Int) -> Unit,
    isPerformanceModeSupported: Boolean,
    currentPerformanceMode: String,
    onPerformanceModeChange: (String) -> Unit,
    onMemoryCleanup: () -> Unit,
    isMistouchEnabled: Boolean,
    onToggleMistouch: () -> Unit,
    isDndEnabled: Boolean,
    onToggleDnd: () -> Unit,
    onScreenRecord: () -> Unit,
    onScreenshot: () -> Unit,
    appShortcuts: List<AppShortcut>,
    onLaunchApp: (String) -> Unit,
    onCollapse: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(OverlayTab.TOOLS) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onCollapse() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 32.dp, top = 24.dp, bottom = 24.dp)
        ) {
            Surface(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { },
                shape = RoundedCornerShape(24.dp),
                color = Color(0xF0181A1F),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x11FFFFFF))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    NavTabItem(
                        icon = Icons.Default.Speed,
                        label = "Performance\npanel",
                        isSelected = selectedTab == OverlayTab.PERFORMANCE,
                        onClick = { selectedTab = OverlayTab.PERFORMANCE }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NavTabItem(
                        icon = Icons.Default.Tune,
                        label = "Game tools",
                        isSelected = selectedTab == OverlayTab.TOOLS,
                        onClick = { selectedTab = OverlayTab.TOOLS }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    NavTabItem(
                        icon = Icons.Default.Stars,
                        label = "Game Center",
                        isSelected = false,
                        onClick = { }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(modifier = Modifier.width(24.dp).height(1.dp).background(Color(0x33FFFFFF)))
                    Spacer(modifier = Modifier.height(16.dp))
                    appShortcuts.forEach { app ->
                        AppShortcutIcon(app.bgColor) { onLaunchApp(app.pkg) } 
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { },
                color = Color(0xF0181A1F),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x11FFFFFF))
            ) {
                when (selectedTab) {
                    OverlayTab.PERFORMANCE -> PerformancePanelContent(
                        cpuInfo = cpuInfo,
                        gpuInfo = gpuInfo,
                        ramInfo = ramInfo,
                        fps = fps,
                        brightness = brightness,
                        onBrightnessChange = onBrightnessChange,
                        supportedRefreshRates = supportedRefreshRates,
                        currentRefreshRate = currentRefreshRate,
                        onRefreshRateChange = onRefreshRateChange,
                        isPerformanceModeSupported = isPerformanceModeSupported,
                        currentPerformanceMode = currentPerformanceMode,
                        onPerformanceModeChange = onPerformanceModeChange
                    )
                    OverlayTab.TOOLS -> GameToolsContent(
                        isAutoRotate = isAutoRotate,
                        onToggleAutoRotate = onToggleAutoRotate,
                        onMemoryCleanup = onMemoryCleanup,
                        isMistouchEnabled = isMistouchEnabled,
                        onToggleMistouch = onToggleMistouch,
                        isDndEnabled = isDndEnabled,
                        onToggleDnd = onToggleDnd,
                        onScreenRecord = onScreenRecord,
                        onScreenshot = onScreenshot
                    )
                }
            }
        }
    }
}

@Composable
fun NavTabItem(icon: ImageVector, label: String, isSelected: Boolean, onClick: () -> Unit) {
    val color = if (isSelected) Color(0xFFFFC107) else Color.Gray
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            lineHeight = 11.sp
        )
    }
}

@Composable
fun AppShortcutIcon(bgColor: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(bgColor))
    }
}

@Composable
fun PerformancePanelContent(
    cpuInfo: CpuInfo,
    gpuInfo: GpuInfo,
    ramInfo: RamInfo,
    fps: Float,
    brightness: Float,
    onBrightnessChange: (Float) -> Unit,
    supportedRefreshRates: List<Int>,
    currentRefreshRate: Int,
    onRefreshRateChange: (Int) -> Unit,
    isPerformanceModeSupported: Boolean,
    currentPerformanceMode: String,
    onPerformanceModeChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DialDummy("CPU", "${cpuInfo.loadPercent.toInt()}", "%")
            DialDummy("GPU", "${gpuInfo.loadPercent ?: 0}", "%")
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(text = "RAM", color = Color.Gray, fontSize = 11.sp)
                Text(text = "${ramInfo.usedMb}/${ramInfo.totalMb} MB", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                val fpsDisplay = if (fps > 0) {
                    if (fps % 1f == 0f || fps >= 100f) fps.toInt().toString() else String.format(java.util.Locale.US, "%.1f", fps)
                } else "--"
                Text(text = "Game FPS", color = Color.Gray, fontSize = 11.sp)
                Text(text = fpsDisplay, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF23252A))
                .padding(4.dp)
        ) {
            val modes = listOf(
                Pair("Battery Saver", Color.Gray), 
                Pair("Balanced", Color(0xFF64B5F6)), 
                Pair("Monster", Color(0xFFFF9800))
            )
            modes.forEach { (mode, activeColor) ->
                val isSelected = currentPerformanceMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) activeColor else Color.Transparent)
                        .clickable(enabled = isPerformanceModeSupported) { onPerformanceModeChange(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (mode == "Monster") "MONSTER" else mode,
                        color = if (isSelected) Color.Black else Color.Gray,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontStyle = if (mode == "Monster") androidx.compose.ui.text.font.FontStyle.Italic else androidx.compose.ui.text.font.FontStyle.Normal,
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        if (supportedRefreshRates.size > 1) {
            Text("Refresh Rate", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(Color(0xFF2A2C31), RoundedCornerShape(22.dp)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                supportedRefreshRates.forEach { rate ->
                    val isSelected = currentRefreshRate == rate
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(4.dp)
                            .background(
                                if (isSelected) Color(0xFFFFC107) else Color.Transparent,
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { onRefreshRateChange(rate) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${rate}Hz",
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text("Brightness", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Slider(
                value = brightness,
                onValueChange = onBrightnessChange,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFFFFC107),
                    inactiveTrackColor = Color(0xFF333333)
                )
            )
        }
    }
}

@Composable
fun DialDummy(title: String, value: String, unit: String) {
    Box(
        modifier = Modifier
            .size(110.dp)
            .border(2.dp, Color(0xFF1E88E5).copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.LightGray, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = Color.LightGray, fontSize = 10.sp)
        }
    }
}

@Composable
fun GameToolsContent(
    isAutoRotate: Boolean,
    onToggleAutoRotate: () -> Unit,
    onMemoryCleanup: () -> Unit,
    isMistouchEnabled: Boolean,
    onToggleMistouch: () -> Unit,
    isDndEnabled: Boolean,
    onToggleDnd: () -> Unit,
    onScreenRecord: () -> Unit,
    onScreenshot: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val tools = listOf(
            FeatureItem(Icons.Default.Memory, "Memory\ncleanup", onMemoryCleanup),
            FeatureItem(Icons.Default.Videocam, "Screen record", onScreenRecord),
            FeatureItem(Icons.Default.Screenshot, "Screenshot", onScreenshot),
            FeatureItem(if (isMistouchEnabled) Icons.Default.TouchApp else Icons.Default.DoNotTouch, if (isMistouchEnabled) "Mistouch\nON" else "Mistouch\nOFF", onToggleMistouch),
            FeatureItem(Icons.Default.PhoneCallback, "Background calls", { Toast.makeText(context, "In Development", Toast.LENGTH_SHORT).show() }),
            FeatureItem(if (isDndEnabled) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive, if (isDndEnabled) "DND\nON" else "Block\nnotifications", onToggleDnd),
            FeatureItem(if (isAutoRotate) Icons.Default.ScreenRotation else Icons.Default.ScreenLockRotation, if (isAutoRotate) "Auto rotate" else "Locked", onToggleAutoRotate),
            FeatureItem(Icons.Default.PictureInPicture, "Notification style", { Toast.makeText(context, "In Development", Toast.LENGTH_SHORT).show() }),
            FeatureItem(Icons.Default.PhoneMissed, "Reject calls", { Toast.makeText(context, "In Development", Toast.LENGTH_SHORT).show() })
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tools) { tool ->
                GameToolGridItem(tool)
            }
        }
    }
}

data class FeatureItem(val icon: ImageVector, val label: String, val onClick: () -> Unit)

@Composable
fun GameToolGridItem(feature: FeatureItem) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { feature.onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF323438)), // Lighter gray for tool buttons
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = feature.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = feature.label,
            color = Color.LightGray,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            maxLines = 2
        )
    }
}
