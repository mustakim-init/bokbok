package com.mustakim.bokbok.ui.screens.chats

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.viewmodel.ChatUiModel
import com.mustakim.bokbok.viewmodel.FriendsUiState
import com.mustakim.bokbok.viewmodel.FriendsViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatsScreen(
    navController: NavHostController,
    userViewModel: UserViewModel,
    friendsViewModel: FriendsViewModel,
    onFriendClick: (String) -> Unit
) {
    val viewModel = friendsViewModel

    val chatList by viewModel.chats.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    var searchQuery by remember { mutableStateOf("") }

    var selectedChatForMenu by remember { mutableStateOf<ChatUiModel?>(null) }
    var isMuted by remember { mutableStateOf(false) }
    val isMinimized by RoomStateManager.isMinimized

    // New state: store the anchor position (in window px) for the dropdown menu
    var menuAnchor by remember { mutableStateOf(IntOffset.Zero) }

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

    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary

    MainScaffold(
        navController = navController,
        title = "Chats",
        showBottomBar = true,
        useFlexibleTopBar = false,
        isStatic = true,
        notificationCount = 0,
        userViewModel = userViewModel,
        containerColor = Color.Transparent,
        background = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(color1.copy(alpha = 0.12f), Color.Transparent),
                                    center = Offset(size.width * 0.15f, size.height * 0.1f),
                                    radius = size.width * 0.8f
                                )
                            )
                            drawRect(
                                brush = Brush.radialGradient(
                                    colors = listOf(color2.copy(alpha = 0.1f), Color.Transparent),
                                    center = Offset(size.width * 0.85f, size.height * 0.25f),
                                    radius = size.width * 0.7f
                                )
                            )
                        }
                    }
            )
        }
    ) { paddingValues ->
        // Track root container position so we can compute offsets relative to it
        var rootPosition by remember { mutableStateOf(IntOffset.Zero) }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    rootPosition = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                }
        ) {
            if (chatList.isEmpty()) {
                EmptyFriendsState()
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                        .nestedScroll(nestedScrollConnection),
                    contentPadding = PaddingValues(
                        top = searchBarHeight + 8.dp,
                        bottom = 120.dp
                    )
                ) {
                    if (filteredChats.isEmpty() && searchQuery.isNotBlank()) {
                        item {
                            NoSearchResults()
                        }
                    } else {
                        items(
                            items = filteredChats,
                            key = { chat ->
                                if (chat.isGroup) "group_${"" + chat.groupId}"
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
                                            navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.GroupChat.createRoute(chatItem.groupId))
                                        } else if (chatItem.friend != null) {
                                            onFriendClick(chatItem.friend.user.uid)
                                        }
                                    },
                                    // pass back the item's window position so the menu can be anchored near it
                                    onLongClick = { itemPos ->
                                        selectedChatForMenu = chatItem
                                        menuAnchor = itemPos
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (chatList.isNotEmpty()) {
                PremiumSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset { IntOffset(x = 0, y = searchBarOffsetHeightPx.roundToInt()) }
                )
            }


            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
            )

            // Compute a DpOffset for the dropdown menu from the saved anchor in px,
            // but make it relative to the root container so it appears next to the item
            val dropdownOffset = with(LocalDensity.current) {
                val relX = (menuAnchor.x - rootPosition.x).coerceAtLeast(0)
                val relY = (menuAnchor.y - rootPosition.y).coerceAtLeast(0)
                DpOffset(relX.toDp(), relY.toDp() + 8.dp)
            }

            if (selectedChatForMenu != null) {
                ModalBottomSheet(
                    onDismissRequest = { selectedChatForMenu = null },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    dragHandle = {
                       Box(
                           modifier = Modifier
                               .padding(top = 16.dp, bottom = 8.dp)
                               .width(32.dp)
                               .height(4.dp)
                               .clip(RoundedCornerShape(2.dp))
                               .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
                       )
                    }
                ) {
                   Column(
                       modifier = Modifier
                           .fillMaxWidth()
                           .padding(horizontal = 24.dp)
                           .padding(bottom = 48.dp),
                       horizontalAlignment = Alignment.CenterHorizontally,
                       verticalArrangement = Arrangement.spacedBy(16.dp)
                   ) {
                       // Header with Chat Name
                       val chat = selectedChatForMenu!!
                       val name = if (chat.isGroup) chat.groupName ?: "Group" else chat.friend?.user?.displayName ?: "Unknown"
                       
                       Text(
                           text = name,
                           style = MaterialTheme.typography.titleLarge,
                           fontWeight = FontWeight.Bold,
                           textAlign = TextAlign.Center
                       )
                       
                       HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                       
                       // Expressive Menu Items
                       ExpressiveMenuItem(
                           icon = if (isMuted) Icons.Default.NotificationsOff else Icons.Default.Notifications,
                           label = if (isMuted) "Unmute notifications" else "Mute notifications",
                           onClick = {
                               selectedChatForMenu?.let {
                                   viewModel.muteConversation(it.groupId ?: it.friend?.user?.uid ?: "", !isMuted)
                                   isMuted = !isMuted
                               }
                               selectedChatForMenu = null
                           }
                       )
                       
                       ExpressiveMenuItem(
                           icon = Icons.Default.Delete,
                           label = "Clear chat history",
                           onClick = {
                               selectedChatForMenu?.let {
                                   viewModel.clearChatHistory(it.groupId ?: it.friend?.friendship?.id ?: "", it.isGroup)
                               }
                               selectedChatForMenu = null
                           }
                       )
                       
                       if (chat.isGroup) {
                           ExpressiveMenuItem(
                               icon = Icons.Default.Logout,
                               label = "Leave group",
                               color = MaterialTheme.colorScheme.error,
                               onClick = {
                                   selectedChatForMenu?.groupId?.let {
                                       viewModel.leaveGroup(it)
                                   }
                                   selectedChatForMenu = null
                               }
                           )
                       } else {
                           ExpressiveMenuItem(
                               icon = Icons.Default.PersonRemove,
                               label = "Remove friend",
                               color = MaterialTheme.colorScheme.error,
                               onClick = {
                                   selectedChatForMenu?.friend?.friendship?.id?.let {
                                       viewModel.removeFriend(it)
                                   }
                                   selectedChatForMenu = null
                               }
                           )
                       }
                   }
                }
            }
        }
    }
}

@Composable
fun ExpressiveMenuItem(
    icon: ImageVector,
    label: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.96f else 1f, label = "scale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                     if (color == MaterialTheme.colorScheme.error) 
                         MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
                     else 
                         MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (color == MaterialTheme.colorScheme.error)
                        MaterialTheme.colorScheme.onErrorContainer
                    else
                        MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}


@Composable
fun PremiumSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = {
                    Text(
                        "Search chats...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold)
            )
            if (query.isNotEmpty()) {
                BokBokIconButton(
                    onClick = { onQueryChange("") },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: ChatUiModel,
    onClick: () -> Unit,
    onLongClick: (IntOffset) -> Unit
) {
    val displayName = if (chat.isGroup) {
        chat.groupName ?: "Group"
    } else {
        chat.friend?.user?.displayName ?: "Unknown"
    }

    val avatarText = displayName.take(1).uppercase()
    // For group chats use the group's image if available, otherwise use friend's profile image
    val profileImageUrl = if (chat.isGroup) chat.groupImageUrl ?: "" else chat.friend?.user?.profileImageUrl ?: ""
    val isOnline = chat.friend?.isOnline ?: false
    val currentRoomId = chat.friend?.currentRoomId
    val hasUnread = chat.unreadCount > 0 && chat.lastMessageSender != "You"
    val haptic = LocalHapticFeedback.current

    // Track this item's position in window coordinates so parent can anchor a menu near it
    var itemPosition by remember { mutableStateOf(IntOffset.Zero) }

    // Expressive Animations
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(targetValue = if (isPressed) 0.95f else 1f, label = "scale")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .scale(scale)
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                itemPosition = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick(itemPosition)
                }
            ),
        shape = RoundedCornerShape(28.dp),
        color = if (hasUnread)
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else
            MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = if (hasUnread) androidx.compose.foundation.BorderStroke(
            width = 1.5.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
        ) else androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = if (hasUnread) 4.dp else 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar with online indicator
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .padding(2.dp)
            ) {
                if (profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = profileImageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.secondaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = avatarText,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Online / In Room indicator
                if (isOnline || currentRoomId != null) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 4.dp, y = 4.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape)
                            .padding(2.dp)
                            .background(
                                if (currentRoomId != null)
                                    MaterialTheme.colorScheme.tertiary
                                else
                                    MaterialTheme.colorScheme.primary,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentRoomId != null) {
                            Icon(
                                imageVector = Icons.Rounded.Call,
                                contentDescription = null,
                                modifier = Modifier.size(10.dp),
                                tint = MaterialTheme.colorScheme.onTertiary
                            )
                        }
                    }
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
                        fontWeight = if (hasUnread) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (chat.timestamp > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatChatTime(chat.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (hasUnread)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
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
                            currentRoomId != null -> "🎙️ In a voice room • Tap to join"
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
                            currentRoomId != null -> MaterialTheme.colorScheme.tertiary
                            hasUnread -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (hasUnread) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (chat.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = if (chat.unreadCount > 9) "9+" else chat.unreadCount.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
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
private fun NoSearchResults() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text = "No results found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Try a different search term",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            // Decorative icon with gradient background
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "No Conversations Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Add friends to start chatting.\nTap the + button below to get started!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
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