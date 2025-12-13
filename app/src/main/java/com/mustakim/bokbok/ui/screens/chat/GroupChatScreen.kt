package com.mustakim.bokbok.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.ui.components.ScallopShape
import com.mustakim.bokbok.ui.components.SquircleShape
import com.mustakim.bokbok.ui.theme.getMorphingShape
import com.mustakim.bokbok.viewmodel.GroupChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun GroupChatScreen(
    navController: NavHostController,
    viewModel: GroupChatViewModel
) {
    val allMessages by viewModel.messages.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val groupInfo by viewModel.groupInfo.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    val showEmojiPicker by viewModel.showEmojiPicker.collectAsState()
    
    val currentUserId = viewModel.currentUserId
    
    // Filter out messages deleted by current user
    val messages = remember(allMessages, currentUserId) {
        allMessages.filter { !it.deletedBy.contains(currentUserId) }
    }
    
    val listState = rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Determine if we're at the bottom of the chat (newest messages)
    val isAtBottom by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 100
        }
    }

    val isSearching by viewModel.isSearching.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var showSearchBar by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMessageForReactions by remember { mutableStateOf<Message?>(null) }

    // Handle Back Press to close Emoji Picker
    BackHandler(enabled = showEmojiPicker) {
        viewModel.setShowEmojiPicker(false)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Calculate Read Receipts
    val groupMembersMap = remember(groupMembers) {
        groupMembers.associateBy { it.uid }
    }
    
    val readReceiptsMap = remember(messages, groupMembers, currentUserId) {
        val map = mutableMapOf<String, MutableList<User>>()
        groupMembers.filter { it.uid != currentUserId }.forEach { user ->
            val lastReadMsg = messages.firstOrNull { it.readBy.contains(user.uid) }
            if (lastReadMsg != null) {
                map.getOrPut(lastReadMsg.id) { mutableListOf() }.add(user)
            }
        }
        map
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surfaceContainer) // Header color for the curved edges
        ) {
            // ========== SEPARATE HEADER COMPONENT ==========
            // This is completely outside the LazyColumn - NO LAG
            GroupChatHeader(
                groupName = groupName,
                groupImageUrl = groupInfo?.imageUrl,
                members = groupMembers,
                isExpanded = isAtBottom,
                onBackClick = { navController.navigateUp() },
                onSearchClick = { 
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) {
                        searchQuery = ""
                        viewModel.clearSearch()
                    }
                },
                onDetailsClick = {
                     navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.ChatDetails.createRoute(viewModel.groupId, true))
                }
            )
            
            if (showSearchBar) {
                androidx.compose.material3.OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { 
                        searchQuery = it
                        viewModel.searchMessages(it)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    placeholder = { Text("Search in chat") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        { IconButton(onClick = { searchQuery = ""; viewModel.clearSearch() }) { Icon(Icons.Default.Close, null) } }
                    } else null,
                    singleLine = true
                )
            }
            
            // ========== CHAT MESSAGES (Separate from header) ==========
            // Rounded top corners create the curved inward effect
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { 
                                focusManager.clearFocus()
                                viewModel.setShowEmojiPicker(false)
                            }
                        )
                    }
            ) {
                LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val currentMessages = if (isSearching) searchResults else messages
                    itemsIndexed(
                        items = currentMessages,
                        key = { _, message -> message.id }
                    ) { index, message ->
                        val isMe = message.senderId == currentUserId
                        val nextMessage = messages.getOrNull(index - 1)
                        val prevMessage = messages.getOrNull(index + 1)
                        
                        val isLastInSequence = nextMessage?.senderId != message.senderId
                        val isFirstInSequence = prevMessage?.senderId != message.senderId
                        
                        val sender = groupMembersMap[message.senderId]
                        
                        GroupMessageBubble(
                            message = message,
                            isMe = isMe,
                            showAvatar = !isMe && isLastInSequence,
                            senderImageUrl = sender?.profileImageUrl,
                            senderName = sender?.displayName,
                            isFirst = isFirstInSequence,
                            isLast = isLastInSequence,
                            readReceiptUsers = readReceiptsMap[message.id] ?: emptyList(),
                            onReply = { viewModel.setReplyingTo(message) },
                            onReact = { emoji -> viewModel.reactToMessage(message.id, emoji) },
                            onDelete = { forEveryone -> viewModel.deleteMessage(message.id, forEveryone) },
                            onRemoveReaction = { selectedMessageForReactions = message },
                            onReplyClick = { replyToId ->
                                val replyIndex = messages.indexOfFirst { it.id == replyToId }
                                if (replyIndex != -1) {
                                    scope.launch { listState.animateScrollToItem(replyIndex) }
                                }
                            }
                        )
                        
                        if (isFirstInSequence) {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Date Header - show AFTER message (appears ABOVE in reverseLayout)
                        // Show when this is the oldest message of the day (next older message is from a different day)
                        val showDateHeader = prevMessage == null || !isSameDay(message.timestamp.toDate(), prevMessage.timestamp.toDate())
                        if (showDateHeader) {
                            Spacer(modifier = Modifier.height(8.dp))
                            DateHeader(date = message.timestamp.toDate())
                        }
                    }
                }
            }

            // Input Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                // Summon Autocomplete (for group chat - all members + @everyone)
                com.mustakim.bokbok.ui.components.SummonAutocomplete(
                    text = messageText,
                    availableUsers = groupMembers,
                    isGroup = true,
                    onSuggestionSelected = { viewModel.onMessageChange(it) }
                )
                
                ChatInputBar(
                    text = messageText,
                    onTextChange = viewModel::onMessageChange,
                    onSend = viewModel::sendMessage,
                    replyingTo = replyingTo,
                    onCancelReply = { viewModel.setReplyingTo(null) },
                    onEmojiClick = viewModel::toggleEmojiPicker,
                    showEmojiPicker = showEmojiPicker,
                    onFocus = { viewModel.setShowEmojiPicker(false) }
                )

                AnimatedVisibility(
                    visible = showEmojiPicker,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    EmojiPicker(
                        onEmojiSelected = { emoji ->
                            viewModel.onMessageChange(messageText + emoji)
                        }
                    )
                }
            }
        }
    }

    if (selectedMessageForReactions != null) {
        val message = selectedMessageForReactions!!
        ReactionSummarySheet(
            reactions = message.reactions,
            currentUserId = viewModel.currentUserId, // Assuming currentUserId is exposed in GroupChatViewModel
            userMap = mapOf(
                 // Create a map from group members.
                 viewModel.currentUserId to (groupMembersMap[viewModel.currentUserId] ?: com.mustakim.bokbok.data.model.User(uid = viewModel.currentUserId, displayName = "You")),
                 *(groupMembers.map { it.uid to it }.toTypedArray())
            ),
            onDismiss = { selectedMessageForReactions = null },
            onRemoveReaction = { viewModel.removeReaction(message.id) }
        )
    }
}

/**
 * Separate header component - NOT part of scroll, NO LAG
 * Matches reference design with overlapping avatar cloud
 */
@Composable
fun GroupChatHeader(
    groupName: String,
    groupImageUrl: String? = null,
    members: List<User>,
    isExpanded: Boolean,
    onBackClick: () -> Unit,
    onSearchClick: () -> Unit,
    onDetailsClick: () -> Unit
) {
    // Animate header height - collapsed needs enough for avatars + group name
    val headerHeight by animateDpAsState(
        targetValue = if (isExpanded) 200.dp else 110.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "headerHeight"
    )

    // Morph progress: 0f = Puffy (Expanded), 1f = Circle (Collapsed)
    val morphProgress by animateFloatAsState(
        targetValue = if (isExpanded) 0f else 1f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "morphProgress"
    )
    
    val imageShape = remember(morphProgress) { getMorphingShape(morphProgress) }
    
    // Image size: Large (e.g. 120dp) -> Small (40dp for collapsed, but here we use cloud size logic)
    // Cloud uses dynamic sizes. Let's pick standard sizes for the single image.
    val imageSize by animateDpAsState(
        targetValue = if (isExpanded) 120.dp else 48.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "imageSize"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp // Cleaner look like reference
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Back button - aligned with action buttons
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 4.dp, top = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            // Action buttons - Search and Voice call
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                IconButton(onClick = onSearchClick) {
                    Icon(
                        Icons.Default.Search,
                        "Search",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = { }) {
                    Icon(
                        Icons.Default.Call, 
                        "Voice Call",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Center content - Avatar Cloud OR Group Image + Group Name (always visible)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 56.dp) // Padding to avoid overlap with side buttons
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Image or Avatar Cloud
                if (!groupImageUrl.isNullOrEmpty()) {
                    // Show Single Group Image with Morphing Shape
                    Box(
                        modifier = Modifier
                             .size(imageSize)
                             .clip(imageShape)
                             .border(
                                 width = 2.dp,
                                 color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                 shape = imageShape
                             ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = groupImageUrl,
                            contentDescription = groupName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (members.isNotEmpty()) {
                    // Fallback to Avatar Cloud
                    OverlappingAvatarCloud(
                        members = members,
                        isExpanded = isExpanded
                    )
                }

                Spacer(modifier = Modifier.height(if (isExpanded) 8.dp else 2.dp))

                // Group name with dropdown indicator - ALWAYS visible
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onDetailsClick() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = groupName,
                        style = if (isExpanded) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Options",
                        modifier = Modifier.size(if (isExpanded) 18.dp else 16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * Overlapping avatar cloud
 * - Expanded: Random positions, sizes, and rotations (cloud effect)
 * - Collapsed: Messenger-style stacked row
 */
@Composable
fun OverlappingAvatarCloud(
    members: List<User>,
    isExpanded: Boolean
) {
    val memberCount = members.size
    
    // Container size - dynamic based on member count
    val containerWidth = if (isExpanded) {
        when {
            memberCount <= 2 -> 160.dp
            memberCount <= 4 -> 180.dp
            else -> 200.dp
        }
    } else {
        // Collapsed: width based on stacked avatars
        (memberCount.coerceAtMost(4) * 20 + 40).dp
    }
    
    val containerHeight = if (isExpanded) {
        when {
            memberCount <= 2 -> 100.dp
            memberCount <= 4 -> 110.dp
            else -> 120.dp
        }
    } else {
        50.dp
    }
    
    val animatedWidth by animateDpAsState(targetValue = containerWidth, label = "width")
    val animatedHeight by animateDpAsState(targetValue = containerHeight, label = "height")

    Box(
        modifier = Modifier.size(width = animatedWidth, height = animatedHeight),
        contentAlignment = Alignment.Center
    ) {
        val maxToShow = if (isExpanded) 6 else 4  // Reduced to 6 in expanded to avoid clutter
        val displayMembers = members.take(maxToShow)
        val remainingCount = members.size - maxToShow

        // Get theme colors once
        val primaryContainer = MaterialTheme.colorScheme.primaryContainer
        val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
        val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer

        displayMembers.forEachIndexed { index, user ->
            // Generate properties based on expanded/collapsed state
            val avatarProps = remember(user.uid, isExpanded, displayMembers.size, index) {
                if (isExpanded) {
                    generateExpandedAvatarProps(user.uid.hashCode(), index, displayMembers.size)
                } else {
                    generateCollapsedAvatarProps(index, displayMembers.size)
                }
            }
            
            val animatedSize by animateDpAsState(
                targetValue = avatarProps.size,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "size$index"
            )
            val animatedOffsetX by animateFloatAsState(
                targetValue = avatarProps.offsetX,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "offsetX$index"
            )
            val animatedOffsetY by animateFloatAsState(
                targetValue = avatarProps.offsetY,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "offsetY$index"
            )
            val animatedRotation by animateFloatAsState(
                targetValue = avatarProps.rotation,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "rotation$index"
            )

            // Random shape per user (only in expanded)
            val shape = remember(user.uid, isExpanded) {
                if (isExpanded) getRandomShape(user.uid.hashCode()) else CircleShape
            }

            Box(
                modifier = Modifier
                    .offset(x = animatedOffsetX.dp, y = animatedOffsetY.dp)
                    .zIndex(avatarProps.zIndex)
                    .graphicsLayer { rotationZ = animatedRotation }
                    .size(animatedSize)
                    .clip(shape)
                    .background(primaryContainer)
                    .border(2.5.dp, surfaceContainer, shape),
                contentAlignment = Alignment.Center
            ) {
                if (user.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = user.profileImageUrl,
                        contentDescription = user.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Fallback with single letter - same as LoungeScreen
                    Text(
                        text = user.displayName.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.headlineMedium,
                        color = onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = (animatedSize.value * 0.45f).sp
                    )
                }
            }
        }

        // +N indicator for additional members
        if (remainingCount > 0) {
            val indicatorSize = if (isExpanded) 38.dp else 32.dp
            val indicatorProps = remember(remainingCount, isExpanded, displayMembers.size) {
                if (isExpanded) {
                    RandomAvatarProps(
                        size = indicatorSize,
                        offsetX = 70f,
                        offsetY = 15f,
                        rotation = 12f,
                        zIndex = 20f
                    )
                } else {
                    // Stack at the end in collapsed mode
                    RandomAvatarProps(
                        size = indicatorSize,
                        offsetX = (displayMembers.size * 18f) - 36f,
                        offsetY = 0f,
                        rotation = 0f,
                        zIndex = (displayMembers.size + 1).toFloat()
                    )
                }
            }
            
            val animatedIndicatorSize by animateDpAsState(
                targetValue = indicatorSize,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "indicatorSize"
            )
            val animatedIndicatorX by animateFloatAsState(
                targetValue = indicatorProps.offsetX,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "indicatorX"
            )
            val animatedIndicatorY by animateFloatAsState(
                targetValue = indicatorProps.offsetY,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
                label = "indicatorY"
            )
            
            val indicatorShape = if (isExpanded) {
                ScallopShape(lobes = 8, innerRadiusRatio = 0.88f, rotationDegrees = 22f)
            } else {
                CircleShape
            }
            
            Box(
                modifier = Modifier
                    .offset(x = animatedIndicatorX.dp, y = animatedIndicatorY.dp)
                    .zIndex(indicatorProps.zIndex)
                    .graphicsLayer { rotationZ = indicatorProps.rotation }
                    .size(animatedIndicatorSize)
                    .clip(indicatorShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .border(2.5.dp, surfaceContainer, indicatorShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "+$remainingCount",
                    fontSize = if (isExpanded) 14.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

// Random avatar properties
private data class RandomAvatarProps(
    val size: Dp,
    val offsetX: Float,
    val offsetY: Float,
    val rotation: Float,
    val zIndex: Float
)

/**
 * Generate EXPANDED state avatar properties - random cloud layout
 * Includes minimum size constraints based on member count
 * Fewer members = more spread out, More members = tighter clustering
 */
private fun generateExpandedAvatarProps(
    hash: Int,
    index: Int,
    totalCount: Int
): RandomAvatarProps {
    val absHash = kotlin.math.abs(hash)
    
    // Use different bits of the hash for different properties
    val sizeSeed = (absHash shr 0) and 0xFF
    val xSeed = (absHash shr 8) and 0xFF
    val ySeed = (absHash shr 16) and 0xFF
    val rotSeed = (absHash shr 4) and 0xFF  // Different bits for rotation
    
    // Minimum size based on member count - fewer members = bigger avatars
    val minSize = when {
        totalCount <= 2 -> 56f
        totalCount <= 3 -> 52f
        totalCount <= 4 -> 48f
        totalCount <= 5 -> 44f
        else -> 40f
    }
    
    // Size range based on member count
    val sizeRange = when {
        totalCount <= 2 -> 8f   // 56-64dp
        totalCount <= 3 -> 10f  // 52-62dp
        totalCount <= 4 -> 12f  // 48-60dp
        else -> 16f             // 40-56dp
    }
    
    val baseSize = minSize + (sizeSeed / 255f) * sizeRange
    
    // Radius range based on member count - fewer members = MORE spread, more members = tighter
    // minRadius: base distance from center
    // maxRadius: maximum distance from center
    val (minRadius, maxRadius) = when {
        totalCount <= 2 -> Pair(40f, 55f)   // Very spread out for 2 people
        totalCount <= 3 -> Pair(35f, 50f)   // Spread out for 3
        totalCount <= 4 -> Pair(28f, 48f)   // Medium spread for 4
        totalCount <= 5 -> Pair(22f, 45f)   // Tighter for 5
        else -> Pair(18f, 42f)              // Tightest for 6+
    }
    
    // Random position in a cloud-like area
    val angle = (index.toFloat() / totalCount) * 2f * kotlin.math.PI.toFloat() + (xSeed / 255f) * 0.6f
    val radius = minRadius + (ySeed / 255f) * (maxRadius - minRadius)
    
    val offsetX = kotlin.math.cos(angle) * radius
    val offsetY = kotlin.math.sin(angle) * radius * 0.55f // Squish vertically for cloud shape
    
    // Random rotation between -18 and +18 degrees
    val rotation = (rotSeed / 255f) * 36f - 18f
    
    // Z-index: larger avatars in front, with some randomness
    val zIndex = (sizeSeed / 25f) + index.toFloat()
    
    return RandomAvatarProps(
        size = baseSize.dp,
        offsetX = offsetX,
        offsetY = offsetY,
        rotation = rotation,
        zIndex = zIndex
    )
}

/**
 * Generate COLLAPSED state avatar properties - Messenger-style stacked row
 */
private fun generateCollapsedAvatarProps(
    index: Int,
    totalCount: Int
): RandomAvatarProps {
    // Messenger-style: stacked overlapping circles in a row
    // Size based on member count
    val size = when {
        totalCount <= 2 -> 42.dp
        totalCount <= 3 -> 40.dp
        else -> 36.dp
    }
    
    val overlap = when {
        totalCount <= 2 -> 20f
        totalCount <= 3 -> 18f
        else -> 16f
    }
    
    // Position avatars in a row, last avatar on top (highest z-index)
    val offsetX = (index * overlap) - ((totalCount - 1) * overlap / 2f)
    val offsetY = 0f
    val rotation = 0f  // No rotation in collapsed mode
    val zIndex = index.toFloat()  // Later avatars on top
    
    return RandomAvatarProps(
        size = size,
        offsetX = offsetX,
        offsetY = offsetY,
        rotation = rotation,
        zIndex = zIndex
    )
}

// Random shape selection based on user hash
private fun getRandomShape(hash: Int): Shape {
    val absHash = kotlin.math.abs(hash)
    val shapeSeed = absHash % 100
    
    // Random rotation for the shape (0-360 degrees)
    val shapeRotation = ((absHash shr 12) and 0xFF) / 255f * 360f
    
    return when {
        shapeSeed < 30 -> CircleShape
        shapeSeed < 50 -> SquircleShape(cornerRadiusPercent = 30f + (absHash % 15))
        shapeSeed < 70 -> ScallopShape(
            lobes = 6 + (absHash % 4),  // 6-9 lobes
            innerRadiusRatio = 0.86f + (absHash % 8) / 100f,
            rotationDegrees = shapeRotation
        )
        shapeSeed < 85 -> ScallopShape(
            lobes = 4 + (absHash % 2),  // 4-5 lobes (flower-like)
            innerRadiusRatio = 0.82f + (absHash % 10) / 100f,
            rotationDegrees = shapeRotation
        )
        else -> SquircleShape(cornerRadiusPercent = 40f + (absHash % 12))
    }
}

// Avatar colors for users without profile pictures
@Composable
fun getAvatarColor(index: Int): Color {
    val colors = listOf(
        Color(0xFF6366F1), // Indigo
        Color(0xFFF43F5E), // Rose  
        Color(0xFF10B981), // Emerald
        Color(0xFFF59E0B), // Amber
        Color(0xFF8B5CF6), // Violet
        Color(0xFF06B6D4), // Cyan
        Color(0xFFEC4899), // Pink
        Color(0xFF14B8A6), // Teal
    )
    return colors[index % colors.size]
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupMessageBubble(
    message: Message,
    isMe: Boolean,
    showAvatar: Boolean,
    senderImageUrl: String?,
    senderName: String?,
    isFirst: Boolean,
    isLast: Boolean,
    readReceiptUsers: List<User>,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onDelete: (Boolean) -> Unit,
    onRemoveReaction: () -> Unit,
    onReplyClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Swipe to Reply
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 60.dp.toPx() }
    val maxSwipe = with(density) { 120.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }

    val draggableState = rememberDraggableState { delta ->
        val target = offsetX + delta
        if (isMe) {
            if (target <= 0 && target > -maxSwipe) offsetX = target
        } else {
            if (target >= 0 && target < maxSwipe) offsetX = target
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (kotlin.math.abs(offsetX) > swipeThreshold) {
                        onReply()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    androidx.compose.animation.core.animate(
                        initialValue = offsetX,
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) { value, _ -> offsetX = value }
                }
            )
    ) {
        // Swipe Indicator
        if (offsetX != 0f) {
            val iconAlpha = (kotlin.math.abs(offsetX) / swipeThreshold).coerceIn(0f, 1f)
            val iconScale = (kotlin.math.abs(offsetX) / swipeThreshold).coerceIn(0.5f, 1f)
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Reply,
                contentDescription = "Reply",
                modifier = Modifier
                    .align(if (isMe) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 16.dp)
                    .scale(iconScale)
                    .size(24.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) },
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            // Sender Name
            if (!isMe && isFirst && senderName != null) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp, bottom = 4.dp)
                )
            }

            Row(verticalAlignment = Alignment.Bottom) {
                if (!isMe) {
                    if (showAvatar) {
                        if (!senderImageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = senderImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (senderName?.take(1) ?: "?").uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                // Bubble
                Box(
                    modifier = Modifier.padding(bottom = if (message.reactions.isNotEmpty()) 10.dp else 0.dp)
                ) {
                    val cornerRadius = 24.dp
                    val smallCorner = 4.dp
                    val shape = if (isMe) {
                        RoundedCornerShape(
                            topStart = cornerRadius,
                            topEnd = cornerRadius,
                            bottomStart = cornerRadius,
                            bottomEnd = if (isLast) smallCorner else cornerRadius
                        )
                    } else {
                        RoundedCornerShape(
                            topStart = cornerRadius,
                            topEnd = cornerRadius,
                            bottomStart = if (isLast) smallCorner else cornerRadius,
                            bottomEnd = cornerRadius
                        )
                    }

                    val backgroundColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                    val contentColor = if (isMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                    
                    Surface(
                        color = backgroundColor,
                        shape = shape,
                        modifier = Modifier
                            .widthIn(max = 280.dp)
                            .combinedClickable(
                                onClick = { showTime = !showTime },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showMenu = true
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            // Reply Context
                            if (message.replyToText != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 4.dp)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .clickable { message.replyToId?.let { onReplyClick(it) } }
                                        .padding(8.dp)
                                ) {
                                    Column {
                                        Text(
                                            text = message.replyToSenderName ?: "Unknown",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = contentColor.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = message.replyToText,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            color = contentColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }

                            // Message text with summon highlighting
                            val highlightedText = com.mustakim.bokbok.utils.SummonHighlighter.formatForDisplay(
                                text = message.text,
                                highlightColor = MaterialTheme.colorScheme.primary,
                                showCommand = false
                            )
                            Text(
                                text = highlightedText,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                        }
                    }

                    // Reactions
                    if (message.reactions.isNotEmpty()) {
                        val reactionCounts = message.reactions.values.groupingBy { it }.eachCount()
                        val sortedReactions = reactionCounts.entries.sortedByDescending { it.value }

                        Box(
                            modifier = Modifier
                                .align(if (isMe) Alignment.BottomStart else Alignment.BottomEnd)
                                .offset(x = if (isMe) 12.dp else (-12).dp, y = 10.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clip(CircleShape)
                                .clickable { onRemoveReaction() }
                                .padding(4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                sortedReactions.take(3).forEach { (emoji, count) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = emoji, style = MaterialTheme.typography.labelSmall)
                                        if (count > 1) {
                                            Text(
                                                text = " $count",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Context Menu
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .widthIn(min = 280.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                    ) {
                        // Quick Reactions
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            listOf("❤️", "😂", "😮", "😢", "😠", "👍").forEach { emoji ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            onReact(emoji)
                                            showMenu = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = emoji, fontSize = 24.sp)
                                }
                            }
                        }
                        HorizontalDivider()
                        Column {
                            DropdownMenuItem(Icons.AutoMirrored.Filled.Reply, "Reply") { onReply(); showMenu = false }
                            DropdownMenuItem(Icons.Default.ContentCopy, "Copy") { 
                                clipboardManager.setText(AnnotatedString(message.text))
                                showMenu = false 
                            }
                            if (!message.isDeletedForEveryone) {
                                DropdownMenuItem(Icons.Default.Delete, "Delete", MaterialTheme.colorScheme.error) { 
                                    showMenu = false
                                    showDeleteDialog = true 
                                }
                            }
                        }
                    }
                }
            }
            
            // Read Receipts
            if (readReceiptUsers.isNotEmpty() && isMe && !message.isDeletedForEveryone) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    readReceiptUsers.forEachIndexed { index, user ->
                        Box(
                            modifier = Modifier
                                .offset(x = (index * -8).dp)
                                .zIndex(readReceiptUsers.size - index.toFloat())
                        ) {
                            if (user.profileImageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = user.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                        .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.displayName.take(1).uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Timestamp
            AnimatedVisibility(
                visible = showTime,
                enter = androidx.compose.animation.expandVertically() + fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + fadeOut()
            ) {
                Text(
                    text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(message.timestamp.toDate()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp, start = if (isMe) 0.dp else 44.dp),
                    textAlign = if (isMe) TextAlign.End else TextAlign.Start
                )
            }
        }
    }
    
    // Delete Dialog (outside menu)
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Message") },
            text = { Text(if (isMe) "Choose how to delete" else "Delete for you?") },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                    TextButton(onClick = { onDelete(false); showDeleteDialog = false }) { 
                        Text(if (isMe) "Delete for Me" else "Delete") 
                    }
                    if (isMe) {
                        TextButton(onClick = { onDelete(true); showDeleteDialog = false }) { 
                            Text("Delete for Everyone") 
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun DropdownMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = text,
            tint = if (color == Color.Unspecified) LocalContentColor.current else color,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (color == Color.Unspecified) Color.Unspecified else color
        )
    }
}
