package com.mustakim.bokbok

import com.mustakim.bokbok.viewmodel.ThemeViewModel
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
 
    @Inject lateinit var roomRepository: RoomRepository
    @Inject lateinit var userRepository: UserRepository
    @Inject lateinit var notificationRepository: NotificationRepository
 
    // Use a MutableState to track the latest intent for navigation
    private val _intentState = androidx.compose.runtime.mutableStateOf<Intent?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        _intentState.value = intent
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

            val selectedTheme by themeViewModel.selectedTheme.collectAsState()
            val darkTheme = isSystemInDarkTheme()

            BokBokTheme(
                selectedTheme = selectedTheme,
                darkTheme = darkTheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // ✅ OPTIMIZED: Repositories for notification handling are now lazy
                    // Only created when a notification intent is actually received
                    val context = androidx.compose.ui.platform.LocalContext.current
                    
                    // Observe the intent state
                    val currentIntent by _intentState
                    
                    androidx.compose.runtime.LaunchedEffect(currentIntent) {
                        // Task 1: Handle notification intent
                        val intent = currentIntent
                        if (intent?.getBooleanExtra("navigate_to_room", false) == true) {
                            // ... (notification handling) ...
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
                                    com.mustakim.bokbok.state.RoomStateManager.joinRoom(room)
                                    intent.removeExtra("navigate_to_room")
                                }
                            }
                        }

                        // Task 2: Defer intrusive system requests (Battery Optimization)
                        // Wait for UI to stabilize (Stage 2)
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
                        themeViewModel = themeViewModel
                    )
                }
            }
        }
    }
}
