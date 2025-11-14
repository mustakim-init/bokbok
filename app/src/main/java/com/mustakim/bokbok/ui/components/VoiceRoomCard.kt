package com.mustakim.bokbok.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun VoiceRoomCard(
    room: VoiceRoom,
    onClick: () -> Unit,
    onImageSelected: ((android.net.Uri) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val imagePickerLauncher = if (onImageSelected != null) {
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let { onImageSelected.invoke(it) }
        }
    } else {
        null
    }

    val hasImage = remember(room.imageUrl) { room.imageUrl.isNotEmpty() }
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val tertiaryContainer = MaterialTheme.colorScheme.tertiaryContainer

    val categoryBgColor = remember(hasImage) {
        if (hasImage) Color.White.copy(alpha = 0.9f) else null
    }

    val categoryTextColor = remember(hasImage) {
        if (hasImage) Color.Black else null
    }

    val titleColor = remember(hasImage) {
        if (hasImage) Color.White else null
    }

    val descriptionColor = remember(hasImage) {
        if (hasImage) Color.White.copy(alpha = 0.9f) else null
    }

    val imageGradient = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.6f),
                Color.Black.copy(alpha = 0.3f),
                Color.Black.copy(alpha = 0.7f)
            )
        )
    }

    val fallbackGradient = Brush.verticalGradient(
        colors = listOf(primaryContainer, tertiaryContainer)
    )

    Card(
        onClick = { onClick()
        },
        modifier = modifier
            .fillMaxWidth()
            .height(400.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (hasImage) {
                AsyncImage(
                    model = room.imageUrl,
                    contentDescription = "Room background",
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
                        .background(fallbackGradient)
                )
            }

            RoomCardContent(
                room = room,
                hasImage = hasImage,
                categoryBgColor = categoryBgColor,
                categoryTextColor = categoryTextColor,
                titleColor = titleColor,
                descriptionColor = descriptionColor
            )
        }
    }
}

@Composable
private fun RoomCardContent(
    room: VoiceRoom,
    hasImage: Boolean,
    categoryBgColor: Color?,
    categoryTextColor: Color?,
    titleColor: Color?,
    descriptionColor: Color?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = categoryBgColor ?: MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = room.category.displayName,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = categoryTextColor ?: MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (!room.isPublic) {
                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    shape = CircleShape
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Private room",
                        modifier = Modifier
                            .padding(8.dp)
                            .size(16.dp),
                        tint = Color.White
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = room.name,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = titleColor ?: MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (room.description.isNotEmpty()) {
            Text(
                text = room.description,
                style = MaterialTheme.typography.bodyLarge,
                color = descriptionColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        HostInfoSection(room = room, hasImage = hasImage)
        Spacer(modifier = Modifier.height(16.dp))
        ParticipantsSection(room = room, hasImage = hasImage)
    }
}

@Composable
private fun HostInfoSection(room: VoiceRoom, hasImage: Boolean) {
    Surface(
        color = if (hasImage)
            Color.White.copy(alpha = 0.15f)
        else
            MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (room.hostImageUrl.isNotEmpty()) {
                AsyncImage(
                    model = room.hostImageUrl,
                    contentDescription = "Host avatar",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (hasImage)
                                Color.White.copy(alpha = 0.3f)
                            else
                                MaterialTheme.colorScheme.primary,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = room.hostName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = room.hostName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (hasImage) Color.White else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Host",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasImage)
                        Color.White.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ParticipantsSection(room: VoiceRoom, hasImage: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = if (hasImage)
                Color.White.copy(alpha = 0.2f)
            else
                MaterialTheme.colorScheme.primaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hasImage)
                        Color.White
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${room.participantCount}/${room.maxParticipants}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (hasImage)
                        Color.White
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        if (room.isFull) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "Full",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
