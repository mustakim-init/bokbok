package com.mustakim.bokbok.ui.screens.room

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.mustakim.bokbok.state.JoinMode
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.theme.BokBokTheme
import com.mustakim.bokbok.ui.theme.DefaultThemeColor
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import com.mustakim.bokbok.R
import com.mustakim.bokbok.viewmodel.VoiceRoomViewModel
import androidx.activity.viewModels

@AndroidEntryPoint
class VoiceRoomActivity : ComponentActivity() {

    private val viewModel: VoiceRoomViewModel by viewModels()

    companion object {
        private const val ACTION_MUTE = "com.mustakim.bokbok.ACTION_MUTE"
        private const val ACTION_SPEAKER = "com.mustakim.bokbok.ACTION_SPEAKER"
        private const val ACTION_LEAVE = "com.mustakim.bokbok.ACTION_LEAVE"
    }

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            android.util.Log.d("VoiceRoomActivity", "PiP Action received: ${intent.action}")
            when (intent.action) {
                ACTION_MUTE -> viewModel.toggleMic()
                ACTION_SPEAKER -> viewModel.toggleSpeaker()
                ACTION_LEAVE -> {
                    viewModel.leaveRoom()
                    finish()
                }
            }
        }
    }

    private var isInPipMode by mutableStateOf(false)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, IntentFilter().apply {
                addAction(ACTION_MUTE)
                addAction(ACTION_SPEAKER)
                addAction(ACTION_LEAVE)
            }, Context.RECEIVER_EXPORTED) // ✅ Must be exported to receive from system
        } else {
            registerReceiver(pipReceiver, IntentFilter().apply {
                addAction(ACTION_MUTE)
                addAction(ACTION_SPEAKER)
                addAction(ACTION_LEAVE)
            })
        }

        setContent {
            val themeViewModel: ThemeViewModel = hiltViewModel()
            val pureBlack by themeViewModel.pureBlack.collectAsState()
            val themeColorInt by themeViewModel.themeColorInt.collectAsState()
            val darkMode by themeViewModel.darkMode.collectAsState()
            val useSystemFont by themeViewModel.useSystemFont.collectAsState()
            
            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (darkMode) {
                com.mustakim.bokbok.ui.theme.DarkMode.ON -> true
                com.mustakim.bokbok.ui.theme.DarkMode.OFF -> false
                com.mustakim.bokbok.ui.theme.DarkMode.AUTO -> isSystemDark
            }

            val themeColor = themeColorInt?.let { Color(it) } ?: DefaultThemeColor

            val uiState by viewModel.uiState.collectAsState()
            
            // ✅ Update PiP actions when state changes (e.g. mute/speaker toggle)
            androidx.compose.runtime.LaunchedEffect(uiState.isMuted, uiState.isSpeakerOn) {
                if (isInPipMode) {
                    updatePipParams()
                }
            }

            BokBokTheme(
                darkTheme = darkTheme,
                pureBlack = pureBlack,
                themeColor = themeColor,
                useSystemFont = useSystemFont
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val currentRoom by RoomStateManager.currentRoom
                    android.util.Log.d("VoiceRoomActivity", "Content set, currentRoom: ${currentRoom?.id}")
                    
                    if (currentRoom != null) {
                        VoiceRoomScreen(
                            roomId = currentRoom!!.id,
                            onMinimize = {
                                enterPipMode()
                            },
                            onLeaveRoom = {
                                finish()
                            },
                            viewModel = viewModel, // ✅ Share activity-level ViewModel
                            isPipMode = isInPipMode
                        )
                    } else {
                        // If room is null, something went wrong, close activity
                        finish()
                    }
                }
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePipParams()
            enterPictureInPictureMode(getPipParams())
        }
    }

    private fun getPipParams(): PictureInPictureParams {
        val rootView = findViewById<android.view.View>(android.R.id.content)
        val rect = android.graphics.Rect()
        rootView.getGlobalVisibleRect(rect)

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .setSourceRectHint(rect)
            
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(true)
            builder.setSeamlessResizeEnabled(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val actions = mutableListOf<RemoteAction>()
            val uiState = viewModel.uiState.value

            // Mute Action
            val muteIntent = PendingIntent.getBroadcast(
                this, 0, Intent(ACTION_MUTE).setPackage(packageName), 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            actions.add(RemoteAction(
                Icon.createWithResource(this, if (uiState.isMuted) R.drawable.ic_mic_off_24 else R.drawable.mic),
                if (uiState.isMuted) "Unmute" else "Mute",
                if (uiState.isMuted) "Unmute" else "Mute",
                muteIntent
            ))

            // Speaker Action
            val speakerIntent = PendingIntent.getBroadcast(
                this, 1, Intent(ACTION_SPEAKER).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            actions.add(RemoteAction(
                Icon.createWithResource(this, if (uiState.isSpeakerOn) R.drawable.volume_up else R.drawable.volume_off),
                if (uiState.isSpeakerOn) "Speaker Off" else "Speaker On",
                if (uiState.isSpeakerOn) "Speaker Off" else "Speaker On",
                speakerIntent
            ))

            // Leave Action
            val leaveIntent = PendingIntent.getBroadcast(
                this, 2, Intent(ACTION_LEAVE).setPackage(packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            actions.add(RemoteAction(
                Icon.createWithResource(this, R.drawable.leave),
                "Leave Room",
                "Leave Room",
                leaveIntent
            ))

            builder.setActions(actions)
        }

        return builder.build()
    }

    private fun updatePipParams() {
        if (isInPipMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setPictureInPictureParams(getPipParams())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(pipReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode = isInPictureInPictureMode
        if (isInPipMode) {
            updatePipParams()
        }
    }
}
