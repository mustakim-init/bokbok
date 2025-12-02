package com.mustakim.bokbok.ui.screens.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.repository.ChatRepository
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.viewmodel.ChatUiModel
import com.mustakim.bokbok.viewmodel.FriendsUiState
import com.mustakim.bokbok.viewmodel.FriendsViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    friendsRepository: FriendsRepository,
    chatRepository: ChatRepository,
    onFriendClick: (String) -> Unit,
    navController: NavHostController,
    userViewModel: UserViewModel
) {
    // Use the Factory to create the ViewModel
    val viewModel: FriendsViewModel = viewModel(
        factory = FriendsViewModel.Factory(friendsRepository, chatRepository)
    )

    val chatList by viewModel.chats.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showAddFriendDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState) {
        when (uiState) {
            is FriendsUiState.Success -> {
                snackbarHostState.showSnackbar((uiState as FriendsUiState.Success).message)
                viewModel.clearUiState()
            }
            is FriendsUiState.Error -> {
                snackbarHostState.showSnackbar((uiState as FriendsUiState.Error).message)
                viewModel.clearUiState()
            }
            else -> {}
        }
    }

    // Scroll State for hiding Search Bar
    val listState = rememberLazyListState()
    var isSearchBarVisible by remember { mutableStateOf(true) }

    // Nested Scroll Connection to detect scroll direction
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -10) { // Scrolling Down
                    isSearchBarVisible = false
                } else if (available.y > 10) { // Scrolling Up
                    isSearchBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    MainScaffold(
        navController = navController,
        title = "Chats",
        showBottomBar = true,
        notificationCount = 0,
        userViewModel = userViewModel
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
            ) {
                // Dynamic Search Bar
                AnimatedVisibility(
                    visible = isSearchBarVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    SearchBarDummy()
                }

                if (chatList.isEmpty()) {
                    EmptyFriendsState()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp), // Space for FAB
                    ) {
                        items(
                            items = chatList,
                            key = { it.friend.friendship.id }
                        ) { chatItem ->
                            // Expressive Animation
                            Box(
                                modifier = Modifier.animateItem(
                                    placementSpec = spring(
                                        dampingRatio = 0.8f,
                                        stiffness = Spring.StiffnessLow
                                    )
                                )
                            ) {
                                ChatListItem(
                                    chat = chatItem,
                                    onClick = { onFriendClick(chatItem.friend.user.uid) }
                                )
                            }
                        }
                    }
                }
            }

            // Expressive FAB
            FloatingActionButton(
                onClick = { showAddFriendDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp) // Squircle FAB
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("New Chat", fontWeight = FontWeight.Bold)
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )
        }
    }

    if (showAddFriendDialog) {
        AddFriendDialog(
            viewModel = viewModel,
            onDismiss = { showAddFriendDialog = false }
        )
    }
}

@Composable
fun SearchBarDummy() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .height(52.dp),
        shape = CircleShape, // Fully rounded
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { /* TODO: Implement Search */ }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Search chats...",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ChatListItem(
    chat: ChatUiModel,
    onClick: () -> Unit
) {
    val friend = chat.friend
    val user = friend.user

    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(modifier = Modifier.size(56.dp)) {
                if (user.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp)), // Squircle
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                RoundedCornerShape(18.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = user.displayName.take(1).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Online Status Indicator
                if (friend.isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface) // Border effect
                            .padding(2.dp)
                            .background(Color.Green, CircleShape) // Inner dot
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = user.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Timestamp
                    if (chat.timestamp > 0) {
                        Text(
                            text = formatChatTime(chat.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Subtitle / Last Message with Sender
                    Text(
                        text = when {
                            friend.currentRoomId != null -> "In a voice room • Join now"
                            chat.lastMessage.isEmpty() -> if (friend.isOnline) "Active now" else "Start a conversation"
                            else -> {
                                val prefix = when (chat.lastMessageSender) {
                                    "You" -> "You: "
                                    null -> ""
                                    else -> "${chat.lastMessageSender}: "
                                }
                                prefix + chat.lastMessage
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            friend.currentRoomId != null -> MaterialTheme.colorScheme.primary
                            !chat.isLastMessageRead && chat.lastMessageSender != "You" -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (!chat.isLastMessageRead && chat.lastMessageSender != "You")
                            FontWeight.SemiBold
                        else
                            FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    // Unread Count Badge
                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .background(
                                    MaterialTheme.colorScheme.primary,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (chat.unreadCount > 9) "9+" else chat.unreadCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyFriendsState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "No Chats Yet",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Start a new conversation!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun formatChatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val date = Date(timestamp)
    
    return when {
        diff < 24 * 60 * 60 * 1000 && isSameDay(date, Date()) -> {
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
        }
        diff < 48 * 60 * 60 * 1000 && isYesterday(date) -> {
            "Yesterday"
        }
        else -> {
            SimpleDateFormat("MMM d", Locale.getDefault()).format(date)
        }
    }
}

// Helper functions for date formatting (copied from ChatScreen/GroupChatScreen to avoid circular deps or just for simplicity)
fun isSameDay(date1: Date, date2: Date): Boolean {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return fmt.format(date1) == fmt.format(date2)
}

fun isYesterday(date: Date): Boolean {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DATE, -1)
    return isSameDay(date, cal.time)
}