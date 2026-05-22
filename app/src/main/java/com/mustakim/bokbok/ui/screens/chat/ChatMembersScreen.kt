package com.mustakim.bokbok.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.viewmodel.GroupChatViewModel
import com.mustakim.bokbok.ui.shared.BokBokIconButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatMembersScreen(
    viewModel: GroupChatViewModel,
    onBack: () -> Unit
) {
    val groupInfo by viewModel.groupInfo.collectAsState()
    val members by viewModel.groupMembers.collectAsState()
    val currentUserId = viewModel.currentUserId

    var showRemoveDialog by remember { mutableStateOf<User?>(null) }

    val isOwner = groupInfo?.createdBy == currentUserId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat Members") },
                navigationIcon = {
                    BokBokIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(members) { user ->
                MemberItem(
                    user = user,
                    isOwner = isOwner,
                    isMe = user.uid == currentUserId,
                    onRemove = { showRemoveDialog = user }
                )
            }
        }
    }

    if (showRemoveDialog != null) {
        val userToRemove = showRemoveDialog!!
        AlertDialog(
            onDismissRequest = { showRemoveDialog = null },
            title = { Text("Remove Member?") },
            text = { Text("Are you sure you want to remove ${userToRemove.displayName} from the group?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeMember(userToRemove.uid)
                        showRemoveDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun MemberItem(
    user: User,
    isOwner: Boolean,
    isMe: Boolean,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        if (user.profileImageUrl.isNotEmpty()) {
            AsyncImage(
                model = user.profileImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isMe) "You" else user.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            // Can add status or username here if needed
        }

        // Owner actions
        if (isOwner && !isMe) {
            BokBokIconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}