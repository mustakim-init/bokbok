package com.mustakim.bokbok.ui.screens.room

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.mustakim.bokbok.data.model.VoiceRoom
import com.mustakim.bokbok.viewmodel.VoiceRoomViewModel
import com.mustakim.bokbok.ui.components.ParticipantCard
import com.mustakim.bokbok.data.model.VoiceRoomParticipant
import com.mustakim.bokbok.ui.components.VoiceControlsSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomScreen(
    roomId: String,
    onMinimize: (VoiceRoom, Boolean) -> Unit,
    onLeaveRoom: () -> Unit,
    viewModel: VoiceRoomViewModel = viewModel()
) {
    LaunchedEffect(roomId) {
        viewModel.loadRoom(roomId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()

    // ✅ FIX: Use darker colors ONLY in dark mode
    val gradientColors = if (isDarkTheme) {
        // Dark mode: Use darker container colors
        listOf(
            colorScheme.primaryContainer,
            colorScheme.secondaryContainer,
            colorScheme.tertiaryContainer
        )
    } else {
        // Light mode: Use bright accent colors
        listOf(
            colorScheme.primary,
            colorScheme.secondary,
            colorScheme.tertiary
        )
    }

    // ✅ FIX: Control system bar appearance based on primary color
    val view = LocalView.current

    DisposableEffect(colorScheme.primary) {
        val window = (view.context as? android.app.Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, view)

        // ✅ If primary color is light (like Monochrome white), use dark icons
        val isLightPrimary = colorScheme.primary.luminance() > 0.5f
        insetsController.isAppearanceLightStatusBars = isLightPrimary
        insetsController.isAppearanceLightNavigationBars = isLightPrimary

        onDispose {
            // ✅ Reset to default when leaving screen
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = gradientColors
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            VoiceRoomTopBar(
                roomName = uiState.room?.name ?: "Voice Room",
                isSpeakerOn = uiState.isSpeakerOn,
                onMinimize = {
                    uiState.room?.let { room ->
                        onMinimize(room, uiState.isMuted)
                    }
                },
                onToggleSpeaker = viewModel::toggleSpeaker,
                onInviteFriends = { /* TODO */ }
            )

            Spacer(modifier = Modifier.height(32.dp))

            DynamicParticipantGrid(
                participants = uiState.participants,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(140.dp))
        }

        // Bottom sheet
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                )
                Spacer(modifier = Modifier.height(16.dp))

                VoiceControlsSheet(
                    isMuted = uiState.isMuted,
                    isSpeakerOn = uiState.isSpeakerOn,
                    onToggleMic = viewModel::toggleMic,
                    onToggleSpeaker = viewModel::toggleSpeaker,
                    onOpenChat = { /* TODO */ },
                    onOpenVoiceEffects = { /* TODO */ },
                    onShareInvite = { /* TODO */ },
                    onLeaveRoom = onLeaveRoom
                )
            }
        }
    }
}


// ✅ FIX: Top bar with proper spacing and room name background
@Composable
private fun VoiceRoomTopBar(
    roomName: String,
    isSpeakerOn: Boolean,
    onMinimize: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onInviteFriends: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ✅ Minimize button with background
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

        // ✅ Room name with background (like Discord)
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

        // ✅ Speaker toggle with background
        IconButton(
            onClick = onToggleSpeaker,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(
                imageVector = if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = if (isSpeakerOn) "Mute speaker" else "Unmute speaker",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // ✅ Invite friends with background
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
    val participantCount = participants.size

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        when (participantCount) {
            0 -> EmptyRoomState()
            1 -> SingleParticipantLayout(participants[0])
            2 -> TwoParticipantLayout(participants)
            3 -> ThreeParticipantLayout(participants)
            4 -> FourParticipantLayout(participants)
            else -> FiveParticipantLayout(participants)
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
            .fillMaxWidth(0.8f)
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
        // First participant - larger
        ParticipantCard(
            participant = participants[0],
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
        )

        // Two smaller participants
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
        // Top row
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

        // Bottom row
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
private fun FiveParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        // Top row - 2 participants
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

        // Bottom row - 3 participants
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
