package com.mustakim.bokbok.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.VoiceRoom

@Composable
fun PublicRoomsSection(
    rooms: List<VoiceRoom>,
    totalRooms: Int,
    totalParticipants: Int,
    onRefresh: () -> Unit,
    onRoomClick: (VoiceRoom) -> Unit,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false  // ✅ Add this parameter
) {
    Column(modifier = modifier) {
        // Section Header with rotating refresh button
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

            // ✅ Refresh button with loading animation
            RefreshButton(
                isRefreshing = isRefreshing,
                onClick = onRefresh
            )
        }

        // Stats cards
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
            rooms.chunked(2).forEach { rowRooms ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowRooms.forEach { room ->
                        Box(modifier = Modifier.weight(1f)) {
                            CompactRoomCard(
                                room = room,
                                onClick = { onRoomClick(room) }
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
}

// ✅ NEW: Refresh button with rotation animation
@Composable
private fun RefreshButton(
    isRefreshing: Boolean,
    onClick: () -> Unit
) {
    // Rotation animation
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "refresh_rotation"
    )

    IconButton(
        onClick = onClick,
        enabled = !isRefreshing
    ) {
        Icon(
            imageVector = Icons.Default.Refresh,
            contentDescription = "Refresh rooms",
            tint = if (isRefreshing)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else
                MaterialTheme.colorScheme.primary,
            modifier = Modifier.rotate(if (isRefreshing) rotation else 0f)
        )
    }
}

// Keep your existing StatCard and CompactRoomCard composables...
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

@Composable
fun CompactRoomCard(
    room: VoiceRoom,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
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
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background
            if (hasImage) {
                AsyncImage(
                    model = room.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(modifier = Modifier.fillMaxSize().background(imageGradient))
            } else {
                Box(modifier = Modifier.fillMaxSize().background(colors.fallbackGradient))
            }

            // Content
            CompactRoomCardContent(room = room, colors = colors)

            // Privacy icon
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
                    text = "${room.participantCount}/${room.maxParticipants}",
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
