package com.mustakim.bokbok.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import com.mustakim.bokbok.ui.shared.BokBokIconButton

/**
 * 🚀 PERFORMANCE OPTIMIZED: Flattened version of PublicRoomsSection.
 * Instead of composing the whole grid in one item, we split it into multiple items.
 */
fun LazyListScope.publicRoomsItems(
    rooms: List<VoiceRoom>,
    totalRooms: Int,
    totalParticipants: Int,
    isRefreshing: Boolean,
    isLoadingMore: Boolean = false,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onJoinCallOnly: (VoiceRoom) -> Unit,
    onJoinPermanently: (VoiceRoom) -> Unit
) {
    if (rooms.isEmpty()) return

    // 1. Header Item
    item(key = "public_rooms_header", contentType = "header") {
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
    }

    // 2. Stats Item
    item(key = "public_rooms_stats", contentType = "stats") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
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
    }

    item(key = "public_rooms_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 3. Grid Row Items (The heavy part)
    val rows = rooms.chunked(2)
    itemsIndexed(
        items = rows,
        key = { index, row -> "public_room_row_${row.firstOrNull()?.id ?: index}" },
        contentType = { _, _ -> "room_row" }
    ) { index, rowRooms ->
        // Trigger load more when reaching near the end
        if (index >= rows.size - 2 && !isLoadingMore) {
            onLoadMore()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowRooms.forEach { room ->
                Box(modifier = Modifier.weight(1f)) {
                    var showJoinDialog by remember { mutableStateOf(false) }

                    CompactRoomCard(
                        room = room,
                        onClick = { onJoinCallOnly(room) },
                        onLongClick = { showJoinDialog = true }
                    )

                    if (showJoinDialog) {
                        RoomJoinDialog(
                            room = room,
                            onDismiss = { showJoinDialog = false },
                            onJoinPermanently = { 
                                onJoinPermanently(room)
                                showJoinDialog = false
                            },
                            onJoinCallOnly = {
                                onJoinCallOnly(room)
                                showJoinDialog = false
                            }
                        )
                    }
                }
            }
            if (rowRooms.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }

    // 4. Loading More Indicator
    if (isLoadingMore) {
        item(key = "public_rooms_loading_more", contentType = "loader") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun RoomJoinDialog(
    room: VoiceRoom,
    onDismiss: () -> Unit,
    onJoinPermanently: () -> Unit,
    onJoinCallOnly: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Join room",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "How do you want to join ${room.name}?",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                androidx.compose.material3.Button(
                    onClick = onJoinPermanently,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Join permanently", fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = onJoinCallOnly,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Join call only", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

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
    // Keep legacy version just in case, but LoungeScreen will use flattened version
    val roomRows = remember(rooms) { rooms.chunked(2) }
    val currentOnJoinCallOnly by rememberUpdatedState(onJoinCallOnly)
    val currentOnJoinPermanently by rememberUpdatedState(onJoinPermanently)

    var selectedRoom by remember { mutableStateOf<VoiceRoom?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
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
                                onClick = { currentOnJoinCallOnly(room) },
                                onLongClick = {
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
        RoomJoinDialog(
            room = selectedRoom!!,
            onDismiss = { showJoinDialog = false },
            onJoinPermanently = { 
                selectedRoom?.let { currentOnJoinPermanently(it) }
                showJoinDialog = false
            },
            onJoinCallOnly = {
                selectedRoom?.let { currentOnJoinCallOnly(it) }
                showJoinDialog = false
            }
        )
    }
}

@Composable
private fun RefreshButton(
    isRefreshing: Boolean,
    onClick: () -> Unit
) {
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

    BokBokIconButton(
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
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = 2.dp
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
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onLongClick?.invoke() }
            ),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = 2.dp
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
            color = colors.categoryBg ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = if (colors.hasImage) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Text(
                text = room.category.displayName,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                color = colors.categoryText ?: MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.ExtraBold
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