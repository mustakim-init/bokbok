package com.mustakim.bokbok

import com.mustakim.bokbok.viewmodel.ThemeViewModel
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import com.mustakim.bokbok.music.innertube.YouTube
import com.mustakim.bokbok.music.utils.reportException
import java.net.URLEncoder
import com.mustakim.bokbok.music.ui.screens.buildLoginRoute
import com.mustakim.bokbok.music.ui.screens.LOGIN_URL_ARGUMENT
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import com.mustakim.bokbok.ui.theme.extractThemeColor
import com.mustakim.bokbok.ui.theme.DefaultThemeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import com.mustakim.bokbok.data.repository.NotificationRepository
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.ui.navigation.NavGraph
import com.mustakim.bokbok.ui.theme.BokBokTheme
import com.mustakim.bokbok.viewmodel.UserViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.mustakim.bokbok.music.playback.MusicService
import com.mustakim.bokbok.music.playback.PlayerConnection
import timber.log.Timber
import androidx.compose.ui.graphics.Color
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.LaunchedEffect

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
 
    @Inject lateinit var roomRepository: RoomRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var database: com.mustakim.bokbok.music.db.MusicDatabase
    @Inject lateinit var syncUtils: com.mustakim.bokbok.music.utils.SyncUtils
    @Inject lateinit var downloadUtil: com.mustakim.bokbok.music.playback.DownloadUtil
 
    private var playerConnection by mutableStateOf<PlayerConnection?>(null)
    private var isMusicServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Timber.d("MusicService connected")
            isMusicServiceBound = true
            if (service is MusicService.MusicBinder) {
                playerConnection = PlayerConnection(this@MainActivity, service, database, lifecycleScope)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Timber.d("MusicService disconnected")
            isMusicServiceBound = false
            playerConnection?.dispose()
            playerConnection = null
        }
    }

    override fun onStart() {
        super.onStart()
        isMusicServiceBound = bindService(
            Intent(this, MusicService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun safeUnbindMusicService() {
        if (!isMusicServiceBound) return
        try {
            unbindService(serviceConnection)
        } catch (e: Exception) {
            Timber.e(e, "Error unbinding MusicService")
        } finally {
            isMusicServiceBound = false
        }
    }

    override fun onStop() {
        safeUnbindMusicService()
        playerConnection?.dispose()
        playerConnection = null
        super.onStop()
    }
 
    // Use a MutableState to track the latest intent for navigation
    private val _intentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _intentState.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            com.mustakim.bokbok.startup.StartupManager.currentStage.value == com.mustakim.bokbok.startup.StartupManager.Stage.UNINITIALIZED
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize with starting intent
        _intentState.value = intent

        // Battery optimization request moved to LaunchedEffect (Deferred Stage)
        // to prevent interrupting the critical cold start path.

        setContent {
            // ✅ Hoist both ViewModels using Hilt
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val userViewModel: UserViewModel = hiltViewModel()

            // ✅ FIX: Monitor App Lifecycle to set Online Status
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        // App came to foreground -> Set Online
                        userViewModel.setOnline()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            val selectedTheme by themeViewModel.selectedTheme.collectAsStateWithLifecycle()
            val themeColorInt by themeViewModel.themeColorInt.collectAsStateWithLifecycle()
            val pureBlack by themeViewModel.pureBlack.collectAsStateWithLifecycle()
            val themeSeedPaletteJson by themeViewModel.themeSeedPalette.collectAsStateWithLifecycle()
            val darkMode by themeViewModel.darkMode.collectAsStateWithLifecycle()
            val useSystemFont by themeViewModel.useSystemFont.collectAsStateWithLifecycle()
            val dynamicThemeEnabled by themeViewModel.dynamicThemeEnabled.collectAsStateWithLifecycle()
            val dynamicThemeColor by themeViewModel.dynamicThemeColor.collectAsStateWithLifecycle()

            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (darkMode) {
                com.mustakim.bokbok.ui.theme.DarkMode.ON -> true
                com.mustakim.bokbok.ui.theme.DarkMode.OFF -> false
                com.mustakim.bokbok.ui.theme.DarkMode.AUTO -> isSystemDark
            }

            // Monitor player for dynamic theme
            val playerConnection = playerConnection
            LaunchedEffect(playerConnection, dynamicThemeEnabled) {
                if (!dynamicThemeEnabled || playerConnection == null) {
                    themeViewModel.updateDynamicThemeColor(null)
                    return@LaunchedEffect
                }
                playerConnection.service.currentMediaMetadata.collectLatest { song ->
                    if (song != null && song.thumbnailUrl != null) {
                        withContext(Dispatchers.Default) {
                            try {
                                val result = imageLoader.execute(
                                    ImageRequest.Builder(this@MainActivity)
                                        .data(song.thumbnailUrl)
                                        .allowHardware(false)
                                        .build(),
                                )
                                val extractedColor = (result as? SuccessResult)?.drawable?.let { 
                                    (it as BitmapDrawable).bitmap.extractThemeColor().toArgb()
                                }
                                withContext(Dispatchers.Main) {
                                    themeViewModel.updateDynamicThemeColor(extractedColor)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    themeViewModel.updateDynamicThemeColor(null)
                                }
                            }
                        }
                    } else {
                        themeViewModel.updateDynamicThemeColor(null)
                    }
                }
            }

            val finalThemeColor = remember(dynamicThemeEnabled, dynamicThemeColor, themeColorInt) {
                if (dynamicThemeEnabled && dynamicThemeColor != null) {
                    Color(dynamicThemeColor!!)
                } else {
                    themeColorInt?.let { Color(it) } ?: DefaultThemeColor
                }
            }

            val seedPalette = remember(themeSeedPaletteJson) {
                themeSeedPaletteJson?.let { com.mustakim.bokbok.ui.theme.ThemeSeedPaletteCodec.decodeFromJson(it) }
            }

            BokBokTheme(
                darkTheme = darkTheme,
                pureBlack = pureBlack,
                themeColor = finalThemeColor,
                useSystemFont = useSystemFont,
                seedPalette = seedPalette
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val currentIntent by _intentState
                    val coroutineScope = rememberCoroutineScope()
                    
                    androidx.compose.runtime.LaunchedEffect(currentIntent) {
                        val intent = currentIntent ?: return@LaunchedEffect
                        
                        if (intent.getBooleanExtra("navigate_to_room", false) == true) {
                            val roomId = intent.getStringExtra("roomId")
                            val notificationDocId = intent.getStringExtra("notificationDocId")
                            val isAcceptAction = intent.getBooleanExtra("action_accept", false)

                            if (isAcceptAction && notificationDocId != null) {
                                launch(kotlinx.coroutines.Dispatchers.IO) {
                                    val userId = userRepository.getCurrentUserId()
                                    if (userId != null) {
                                        notificationRepository.deleteNotification(userId, notificationDocId)
                                    }
                                }
                            }

                            if (roomId != null) {
                                val result = roomRepository.getRoom(roomId)
                                result.onSuccess { room ->
                                    navController.navigate("chat/${room.id}")
                                }
                            }
                        }

                        if (intent.action == MusicService.ACTION_MUSIC_PLAYER) {
                            navController.navigate("home") {
                                launchSingleTop = true
                                restoreState = true
                            }
                        }

                        handleDeepLinkIntent(intent, navController, coroutineScope)
                        
                        launch {
                            com.mustakim.bokbok.startup.StartupManager.currentStage.collect { stage ->
                                if (stage == com.mustakim.bokbok.startup.StartupManager.Stage.FULLY_INITIALIZED) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                        val pm = getSystemService(android.os.PowerManager::class.java)
                                        if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                                            val optIntent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                data = "package:$packageName".toUri()
                                            }
                                            startActivity(optIntent)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    NavGraph(
                        navController = navController,
                        themeViewModel = themeViewModel,
                        database = database,
                        syncUtils = syncUtils,
                        downloadUtil = downloadUtil,
                        playerConnection = playerConnection
                    )
                }
            }
        }
    }

    private fun handleDeepLinkIntent(intent: Intent, navController: NavHostController, coroutineScope: CoroutineScope) {
        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        val authority = uri.authority?.lowercase()

        if (uri.scheme.equals("bokbok", ignoreCase = true) && authority == "login") {
            navController.navigate(buildLoginRoute(uri.getQueryParameter(LOGIN_URL_ARGUMENT)))
            return
        }

        when (val path = uri.pathSegments.firstOrNull()) {
            "playlist" -> uri.getQueryParameter("list")?.let { playlistId ->
                if (playlistId.startsWith("OLAK5uy_")) {
                    coroutineScope.launch {
                        YouTube.albumSongs(playlistId).onSuccess { songs ->
                            songs.firstOrNull()?.album?.id?.let { browseId ->
                                navController.navigate("album/$browseId")
                            }
                        }.onFailure { reportException(it) }
                    }
                } else {
                    navController.navigate("online_playlist/$playlistId")
                }
            }

            "browse" -> uri.lastPathSegment?.let { browseId ->
                navController.navigate("album/$browseId")
            }

            "channel", "c" -> uri.lastPathSegment?.let { artistId ->
                navController.navigate("artist/$artistId")
            }

            else -> {
                // Handle potential YouTube video IDs or other paths
                val videoId = when {
                    uri.host == "youtu.be" -> uri.pathSegments.firstOrNull()
                    uri.pathSegments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
                    uri.pathSegments.firstOrNull() == "shorts" -> uri.pathSegments.getOrNull(1)
                    else -> null
                }
                
                if (videoId != null) {
                    // Logic to play the video or navigate to it
                    // For now, just navigate to a potential player or home
                    // navController.navigate("player/$videoId") 
                }
            }
        }
    }
}
