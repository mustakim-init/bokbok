package com.mustakim.bokbok.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.VoiceRoom
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf


@Composable
fun PublicRoomsSection(
    rooms: List<VoiceRoom>,
    totalRooms: Int,
    totalParticipants: Int,
    onRefresh: () -> Unit,
    onJoinCallOnly: (VoiceRoom) -> Unit,
    onJoinPermanently: (VoiceRoom) -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false
) {
    val roomRows = remember(rooms) { rooms.chunked(2) }
    val currentOnJoinCallOnly by rememberUpdatedState(onJoinCallOnly)
    val currentOnJoinPermanently by rememberUpdatedState(onJoinPermanently)

    var selectedRoom by remember { mutableStateOf<VoiceRoom?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        // Header + refresh
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Public Rooms",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            RefreshButton(
                isRefreshing = isRefreshing,
                onClick = onRefresh
            )
        }

        // Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                title = "Active Rooms",
                value = totalRooms.toString(),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Online Users",
                value = totalParticipants.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Rooms grid
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            roomRows.forEach { rowRooms ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowRooms.forEach { room ->
                        Box(
                            modifier = Modifier.weight(1f)
                        ) {
                            CompactRoomCard(
                                room = room,
                                onClick = {
                                    // Tap → join call only
                                    currentOnJoinCallOnly(room)
                                },
                                onLongClick = {
                                    // Long press → show options dialog
                                    selectedRoom = room
                                    showJoinDialog = true
                                }
                            )
                        }
                    }
                    if (rowRooms.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }

    if (showJoinDialog && selectedRoom != null) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join room") },
            text = { Text("How do you want to join this room?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedRoom?.let { currentOnJoinPermanently(it) }
                        showJoinDialog = false
                    }
                ) {
                    Text("Join permanently")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedRoom?.let { currentOnJoinCallOnly(it) }
                        showJoinDialog = false
                    }
                ) {
                    Text("Join call only")
                }
            }
        )
    }
}

@Composable
private fun RefreshButton(
    isRefreshing: Boolean,
    onClick: () -> Unit
) {
    // ✅ Only run infinite animation while refreshing, otherwise stay at 0°
    val targetRotation = if (isRefreshing) 360f else 0f
    val rotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = if (isRefreshing) {
            infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        } else {
            tween(durationMillis = 0)
        },
        label = "refresh_rotation"
    )

    IconButton(
        onClick = onClick,
        enabled = !isRefreshing
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh rooms",
            tint = if (isRefreshing) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.rotate(rotation)
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Immutable
private data class CompactRoomColors(
    val hasImage: Boolean,
    val categoryBg: Color?,
    val categoryText: Color?,
    val title: Color?,
    val participantIcon: Color?,
    val participantText: Color?,
    val fallbackGradient: Brush
)

@Composable
fun CompactRoomCard(
    room: VoiceRoom,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val hasImage = remember(room.imageUrl) { room.imageUrl.isNotEmpty() }
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer

    val colors = remember(hasImage, primaryContainer, tertiaryContainer) {
        CompactRoomColors(
            hasImage = hasImage,
            categoryBg = if (hasImage) Color.White.copy(alpha = 0.9f) else null,
            categoryText = if (hasImage) Color.Black else null,
            title = if (hasImage) Color.White else null,
            participantIcon = if (hasImage) Color.White else null,
            participantText = if (hasImage) Color.White else null,
            fallbackGradient = Brush.linearGradient(
                colors = listOf(primaryContainer, tertiaryContainer)
            )
        )
    }

    val imageGradient = remember {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasImage) {
                AsyncImage(
                    model = room.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(imageGradient)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(colors.fallbackGradient)
                )
            }

            CompactRoomCardContent(room = room, colors = colors)

            if (!room.isPublic) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Private",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(16.dp),
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CompactRoomCardContent(
    room: VoiceRoom,
    colors: CompactRoomColors
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Surface(
            color = colors.categoryBg ?: MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.small
        ) {
            Text(
                text = room.category.displayName,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.categoryText ?: MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column {
            Text(
                text = room.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = colors.title ?: MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.participantIcon ?: MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "${room.currentOnline}/${room.maxParticipants}",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.participantText ?: MaterialTheme.colorScheme.onSurface
                )

                if (room.isFull) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "Full",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
