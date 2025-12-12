package com.mustakim.bokbok.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailsScreen(
    user: User?,
    isGroup: Boolean,
    groupName: String?,
    members: List<User> = emptyList(),
    onBackClick: () -> Unit,
    onMuteClick: () -> Unit,
    canMute: Boolean = true, // Simplified
    isMuted: Boolean = false,
    onClearHistory: () -> Unit,
    onRemoveFriend: () -> Unit, // Or Leave Group
    onAddMember: () -> Unit
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Section
            val displayName = if (isGroup) groupName ?: "Group" else user?.displayName ?: "Unknown"
            val imageUrl = if (isGroup) "" else user?.profileImageUrl

            Spacer(modifier = Modifier.height(16.dp))

            if (!imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayName.take(1).uppercase(),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            if (!isGroup) {
                Text(
                    text = "BokBok User",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "${members.size} members",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Audio Call
                ActionItem(icon = Icons.Default.Call, label = "Audio") { /* TODO */ }
                
                // Add Member (if Group or creating group)
                if (isGroup) {
                     ActionItem(icon = Icons.Default.PersonAdd, label = "Add") { onAddMember() }
                }

                // Mute
                ActionItem(
                    icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    label = "Mute"
                ) { onMuteClick() }
            }

            Spacer(modifier = Modifier.height(32.dp))

            HorizontalDivider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceContainerHigh)

            // Options List
            Column(modifier = Modifier.fillMaxWidth()) {
                if (isGroup) {
                    OptionItem(
                        icon = Icons.Default.Group,
                        label = "See chat members",
                        onClick = { /* TODO: Navigate to members list */ }
                    )
                }

                OptionItem(
                    icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                    label = "Sleep chat (Mute notifications)",
                    onClick = onMuteClick
                )

                OptionItem(
                    icon = Icons.Default.Delete,
                    label = "Delete chat history",
                    onClick = { showClearHistoryDialog = true },
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (isGroup) {
                    OptionItem(
                        icon = Icons.Default.Block,
                        label = "Leave group",
                        onClick = { showRemoveDialog = true },
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    OptionItem(
                        icon = Icons.Default.PersonRemove,
                        label = "Remove friend",
                        onClick = { showRemoveDialog = true },
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Delete chat history?") },
            text = { Text("This will clear the chat history for YOU only. Other participants will still see the messages.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text(if (isGroup) "Leave Group?" else "Remove Friend?") },
            text = { 
                Text(if (isGroup) 
                    "Are you sure you want to leave this group?" 
                else 
                    "Are you sure you want to remove this friend? This will also clear your chat history with them.") 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveFriend()
                        showRemoveDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(if (isGroup) "Leave" else "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ActionItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun OptionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = color
        )
    }
}
