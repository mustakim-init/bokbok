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

        setContent {
            // ✅ Hoist both ViewModels
            val themeViewModel: ThemeViewModel = viewModel()
            val userViewModel: UserViewModel = viewModel()

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
                    
                    // Observe the intent state
                    val currentIntent by _intentState
                    
                    androidx.compose.runtime.LaunchedEffect(currentIntent) {
                        val intent = currentIntent
                        if (intent?.getBooleanExtra("navigate_to_room", false) == true) {
                            val roomId = intent.getStringExtra("roomId")
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
