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
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.repository.ChatRepository
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.viewmodel.ChatUiModel
import com.mustakim.bokbok.viewmodel.FriendsUiState
import com.mustakim.bokbok.viewmodel.FriendsViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    friendsRepository: FriendsRepository,
    chatRepository: ChatRepository,
    isMinimized: Boolean = RoomStateManager.isMinimized.value,
    onFriendClick: (String) -> Unit,
    navController: NavHostController,
    userViewModel: UserViewModel
) {
    val viewModel: FriendsViewModel = viewModel(
        factory = FriendsViewModel.Factory(friendsRepository, chatRepository)
    )

    val chatList by viewModel.chats.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var isFabMenuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Local search state
    var searchQuery by remember { mutableStateOf("") }

    // Filtered list based on search query
    val filteredChats = remember(chatList, searchQuery) {
        if (searchQuery.isBlank()) {
            chatList
        } else {
            chatList.filter { chat ->
                val name = if (chat.isGroup) chat.groupName else chat.friend?.user?.displayName
                name?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

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

    val listState = rememberLazyListState()

    // Search Bar Scroll Logic
    val searchBarHeight = 72.dp
    val searchBarHeightPx = with(LocalDensity.current) { searchBarHeight.toPx() }
    var searchBarOffsetHeightPx by remember { mutableFloatStateOf(0f) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val newOffset = searchBarOffsetHeightPx + delta
                searchBarOffsetHeightPx = newOffset.coerceIn(-searchBarHeightPx, 0f)
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
                .nestedScroll(nestedScrollConnection)
        ) {
            if (chatList.isEmpty()) {
                EmptyFriendsState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = searchBarHeight, // Push content down by search bar height
                        bottom = 80.dp
                    ),
                ) {
                    if (filteredChats.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No results found",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        items(
                            items = filteredChats,
                            key = { chat ->
                                if (chat.isGroup) "group_${chat.groupId}"
                                else "friend_${chat.friend?.friendship?.id}"
                            }
                        ) { chatItem ->
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
                                    onClick = {
                                        if (chatItem.isGroup && chatItem.groupId != null) {
                                            // TODO: Navigate to group chat
                                            // For now, just show a message
                                        } else if (chatItem.friend != null) {
                                            onFriendClick(chatItem.friend.user.uid)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Search Bar (Overlay)
            if (chatList.isNotEmpty()) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(x = 0, y = searchBarOffsetHeightPx.roundToInt()) }
                )
            }

            // Scrim overlay when menu is expanded
            if (isFabMenuExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .clickable { isFabMenuExpanded = false }
                )
            }

            // M3-compliant FAB Menu (Extended FABs)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 32.dp,
                        bottom = if (isMinimized) 120.dp else 36.dp
                    ),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Menu items (appear above main FAB)
                AnimatedVisibility(
                    visible = isFabMenuExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Create Group
                        ExtendedFloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = false
                                showCreateGroupDialog = true
                            },
                            icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                            text = { Text("Create Group") },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            expanded = true
                        )

                        // Add Friend
                        ExtendedFloatingActionButton(
                            onClick = {
                                isFabMenuExpanded = false
                                showAddFriendDialog = true
                            },
                            icon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                            text = { Text("Add Friend") },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            expanded = true
                        )
                    }
                }

                // Main FAB
                FloatingActionButton(
                    onClick = { isFabMenuExpanded = !isFabMenuExpanded },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(
                        imageVector = if (isFabMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = if (isFabMenuExpanded) "Close" else "Menu"
                    )
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

    if (showCreateGroupDialog) {
        CreateGroupChatDialog(
            viewModel = viewModel,
            onDismiss = { showCreateGroupDialog = false }
        )
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text("Search chats...") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        shape = CircleShape,
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent
        )
    )
}

@Composable
fun ChatListItem(
    chat: ChatUiModel,
    onClick: () -> Unit
) {

    // Handle both individual and group chats
    val displayName = if (chat.isGroup) {
        chat.groupName ?: "Group"
    } else {
        chat.friend?.user?.displayName ?: "Unknown"
    }

    val avatarText = displayName.take(1).uppercase()
    val profileImageUrl = chat.friend?.user?.profileImageUrl ?: ""
    val isOnline = chat.friend?.isOnline ?: false
    val currentRoomId = chat.friend?.currentRoomId

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
            Box(modifier = Modifier.size(56.dp)) {
                if (profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.primaryContainer,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                if (isOnline) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp)
                            .background(Color.Green, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

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
                    Text(
                        text = when {
                            currentRoomId != null -> "In a voice room • Join now"
                            chat.lastMessage.isEmpty() -> if (isOnline) "Active now" else "Start a conversation"
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
                            currentRoomId != null -> MaterialTheme.colorScheme.primary
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

fun isSameDay(date1: Date, date2: Date): Boolean {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return fmt.format(date1) == fmt.format(date2)
}

fun isYesterday(date: Date): Boolean {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DATE, -1)
    return isSameDay(date, cal.time)
}

@Composable
fun CreateGroupChatDialog(
    viewModel: FriendsViewModel,
    onDismiss: () -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    val friends by viewModel.friends.collectAsState()
    val selectedFriends = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Group Chat") },
        text = {
            Column {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text("Group Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Select Friends:", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    items(friends) { friend ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val uid = friend.user.uid
                                    if (selectedFriends.contains(uid)) {
                                        selectedFriends.remove(uid)
                                    } else {
                                        selectedFriends.add(uid)
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = selectedFriends.contains(friend.user.uid),
                                onCheckedChange = { checked ->
                                    val uid = friend.user.uid
                                    if (checked) {
                                        selectedFriends.add(uid)
                                    } else {
                                        selectedFriends.remove(uid)
                                    }
                                }
                            )
                            Text(
                                text = friend.user.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (groupName.isNotBlank() && selectedFriends.isNotEmpty()) {
                        viewModel.createGroupChat(groupName, selectedFriends.toList())
                        onDismiss()
                    }
                },
                enabled = groupName.isNotBlank() && selectedFriends.isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}