package com.mustakim.bokbok.ui.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mustakim.bokbok.data.model.VoiceRoomParticipant
import com.mustakim.bokbok.ui.components.ParticipantCard
import com.mustakim.bokbok.ui.components.VoiceControlsSheet
import com.mustakim.bokbok.viewmodel.VoiceRoomViewModel
import com.mustakim.bokbok.state.RoomStateManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomScreen(
    roomId: String,
    onMinimize: (Boolean) -> Unit,
    onLeaveRoom: () -> Unit,
    viewModel: VoiceRoomViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.all { it.value }

        if (granted) {
            // Only start engine once room is loaded
            uiState.room?.let { room ->
                viewModel.startCallEngine(room.id)
            }
        } else {
            viewModel.onPermissionDenied()
        }
    }

    // 1) Load room metadata and start RTDB presence listener
    LaunchedEffect(roomId) {
        viewModel.loadRoom(roomId)
    }

    // 2) When room is loaded, then deal with permissions + start engine
    LaunchedEffect(uiState.room) {
        val room = uiState.room ?: return@LaunchedEffect

        if (viewModel.hasRequiredCallPermissions()) {
            viewModel.startCallEngine(room.id)
        } else {
            permissionLauncher.launch(viewModel.getRequiredPermissions())
        }
    }


    val globalMuted by RoomStateManager.isMuted

    // When minimized bar toggles mute, sync back into ViewModel
    LaunchedEffect(globalMuted) {
        viewModel.setMutedFromGlobal(globalMuted)
    }


    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()

    val gradientColors = remember(isDarkTheme, colorScheme.primary, colorScheme.secondary) {
        if (isDarkTheme) {
            listOf(
                colorScheme.primaryContainer,
                colorScheme.secondaryContainer,
                colorScheme.tertiaryContainer
            )
        } else {
            listOf(
                colorScheme.primary,
                colorScheme.secondary,
                colorScheme.tertiary
            )
        }
    }

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    val view = LocalView.current
    DisposableEffect(colorScheme.primary, isDarkTheme) {
        val window = (view.context as? android.app.Activity)?.window
            ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, view)
        val isLightPrimary = colorScheme.primary.luminance() > 0.5f
        insetsController.isAppearanceLightStatusBars = isLightPrimary
        insetsController.isAppearanceLightNavigationBars = isLightPrimary
        onDispose {
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    val roomName = remember(uiState.room?.name) {
        uiState.room?.name ?: "Voice Room"
    }

    val onMinimizeCallback: () -> Unit = remember(uiState.isMuted) {
        {
            // Pass only the muted state to the parent
            onMinimize(uiState.isMuted)
        }
    }

    // ✅ BottomSheetScaffold state with partial expansion
    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true // Prevent hiding the sheet
    )

    val scaffoldState = rememberBottomSheetScaffoldState(
        bottomSheetState = bottomSheetState
    )

    // ✅ BottomSheetScaffold allows background interaction
    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = 140.dp, // Collapsed height
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetContentColor = MaterialTheme.colorScheme.onSurface,
        sheetTonalElevation = 8.dp,
        sheetShadowElevation = 16.dp,
        sheetDragHandle = {
            // Custom drag handle
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
            )
        },
        sheetContent = {
            // Give the sheet more height than the peek, so it can actually expand
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 320.dp) // > sheetPeekHeight (140.dp)
            ) {
                val isExpanded = bottomSheetState.currentValue == SheetValue.Expanded

                VoiceControlsSheet(
                    isMuted = uiState.isMuted,
                    isSpeakerOn = uiState.isSpeakerOn,
                    isA2dpModeOn = uiState.isA2dpModeOn,
                    isExpanded = isExpanded,
                    onToggleMic = viewModel::toggleMic,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onOpenChat = { /* TODO */ },
                    onOpenVoiceEffects = { /* TODO */ },
                    onToggleAudioMode = viewModel::toggleA2dpMode,
                    onShareInvite = { /* TODO */ },
                    onLeaveRoom = {
                        // 1) Stop WebRTC + foreground service
                        viewModel.leaveRoom()

                        // 2) Run the parent logic: clear Firestore membership + RoomStateManager
                        onLeaveRoom()
                    }
                )
            }
        }
    ) { paddingValues ->
        // Main content - can be interacted with even when sheet is open
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush = gradientBrush)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                VoiceRoomTopBar(
                    roomName = roomName,
                    isSpeakerOn = uiState.isSpeakerOn,
                    onMinimize = onMinimizeCallback,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onInviteFriends = { /* TODO */ }
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Participants grid
                DynamicParticipantGrid(
                    participants = uiState.participants,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp)
                        .padding(paddingValues) // Respects bottom sheet position
                )
            }
        }
    }
}

@Composable
private fun VoiceRoomTopBar(
    roomName: String,
    isSpeakerOn: Boolean,
    onMinimize: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onInviteFriends: () -> Unit
) {
    val speakerIcon = remember(isSpeakerOn) {
        if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp
        else Icons.AutoMirrored.Filled.VolumeOff
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMinimize,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Minimize",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.3f)
        ) {
            Text(
                text = roomName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = onToggleSpeaker,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = speakerIcon,
                contentDescription = if (isSpeakerOn) "Mute speaker" else "Unmute speaker",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onInviteFriends,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = Icons.Default.PersonAdd,
                contentDescription = "Invite friends",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun DynamicParticipantGrid(
    participants: List<VoiceRoomParticipant>,
    modifier: Modifier = Modifier
) {
    val stableParticipants = remember(participants) { participants }
    val participantCount = stableParticipants.size

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (participantCount) {
            0 -> EmptyRoomState()
            1 -> SingleParticipantLayout(stableParticipants[0])
            2 -> TwoParticipantLayout(stableParticipants)
            3 -> ThreeParticipantLayout(stableParticipants)
            4 -> FourParticipantLayout(stableParticipants)
            else -> FiveOrMoreParticipantLayout(stableParticipants)
        }
    }
}

@Composable
private fun EmptyRoomState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🎤",
            style = MaterialTheme.typography.displayLarge,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Waiting for others to join...",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.8f)
        )
    }
}

@Composable
private fun SingleParticipantLayout(participant: VoiceRoomParticipant) {
    ParticipantCard(
        participant = participant,
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .aspectRatio(0.8f)
    )
}

@Composable
private fun TwoParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        participants.forEach { participant ->
            ParticipantCard(
                participant = participant,
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(0.8f)
            )
        }
    }
}

@Composable
private fun ThreeParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        ParticipantCard(
            participant = participants[0],
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            participants.drop(1).forEach { participant ->
                ParticipantCard(
                    participant = participant,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                )
            }
        }
    }
}

@Composable
private fun FourParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            participants.take(2).forEach { participant ->
                ParticipantCard(
                    participant = participant,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            participants.drop(2).take(2).forEach { participant ->
                ParticipantCard(
                    participant = participant,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                )
            }
        }
    }
}

@Composable
private fun FiveOrMoreParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
        ) {
            participants.take(2).forEach { participant ->
                ParticipantCard(
                    participant = participant,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            participants.drop(2).take(3).forEach { participant ->
                ParticipantCard(
                    participant = participant,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(0.8f)
                )
            }
        }
    }
}
