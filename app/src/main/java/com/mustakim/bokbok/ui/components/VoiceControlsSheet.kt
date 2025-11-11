package com.mustakim.bokbok.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun VoiceControlsSheet(
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMic: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenVoiceEffects: () -> Unit,
    onShareInvite: () -> Unit,
    onLeaveRoom: () -> Unit
) {
    // ✅ FIX: Better spacing with SpaceAround instead of SpaceEvenly
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),  // ✅ Increased padding
        horizontalArrangement = Arrangement.SpaceBetween,  // ✅ Changed from SpaceEvenly
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mic toggle
        ControlButton(
            icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            label = if (isMuted) "Unmute" else "Mute",
            isActive = !isMuted,
            onClick = onToggleMic
        )

        // Chat
        ControlButton(
            icon = Icons.AutoMirrored.Filled.Chat,
            label = "Chat",
            onClick = onOpenChat
        )

        // Voice effects
        ControlButton(
            icon = Icons.Default.MusicNote,
            label = "Effects",
            onClick = onOpenVoiceEffects
        )

        // Share/Invite
        ControlButton(
            icon = Icons.Default.Share,
            label = "Invite",
            onClick = onShareInvite
        )

        // Leave room
        ControlButton(
            icon = Icons.Default.CallEnd,
            label = "Leave",
            isDestructive = true,
            onClick = onLeaveRoom
        )
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean = false,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),  // ✅ Increased spacing
        modifier = Modifier.width(64.dp)  // ✅ Fixed width for even distribution
    ) {
        // Icon button
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)  // ✅ Slightly smaller for better spacing
                .clip(CircleShape)
                .background(
                    when {
                        isDestructive -> MaterialTheme.colorScheme.error
                        isActive -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = when {
                    isDestructive -> MaterialTheme.colorScheme.onError
                    isActive -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
        }

        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}
