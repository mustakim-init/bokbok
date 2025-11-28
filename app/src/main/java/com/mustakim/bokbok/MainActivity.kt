package com.mustakim.bokbok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.mustakim.bokbok.ui.navigation.NavGraph
import com.mustakim.bokbok.ui.theme.BokBokTheme
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import android.content.Intent
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : ComponentActivity() {
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

        // Request battery optimization exemption to prevent background kills(Gonna remove it in the future)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = "package:$packageName".toUri()
                }
                startActivity(intent)
            }
        }

        setContent {
            // ✅ Hoist both ViewModels
            val themeViewModel: ThemeViewModel = viewModel()
            val userViewModel: UserViewModel = viewModel()

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
                    
                    // Handle Notification Navigation
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val roomRepository = androidx.compose.runtime.remember { com.mustakim.bokbok.data.repository.RoomRepository() }
                    val notificationRepository = androidx.compose.runtime.remember { com.mustakim.bokbok.data.repository.NotificationRepository() }
                    val userRepository = androidx.compose.runtime.remember { com.mustakim.bokbok.data.repository.UserRepository(context) }
                    
                    // Observe the intent state
                    val currentIntent by _intentState
                    
                    androidx.compose.runtime.LaunchedEffect(currentIntent) {
                        val intent = currentIntent
                        if (intent?.getBooleanExtra("navigate_to_room", false) == true) {
                            val roomId = intent.getStringExtra("roomId")
                            val notificationDocId = intent.getStringExtra("notificationDocId")
                            val isAcceptAction = intent.getBooleanExtra("action_accept", false)

                            // ✅ FIX: Delete notification if this was an "Accept" action
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
                                    // Clear the extra so we don't rejoin on rotation
                                    intent.removeExtra("navigate_to_room")
                                }
                            }
                        }
                    }

                    NavGraph(
                        navController = navController,
                        themeViewModel = themeViewModel,
                        userViewModel = userViewModel
                    )
                }
            }
        }
    }
}
