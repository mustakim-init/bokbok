package com.mustakim.bokbok.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.MessageStatus
import com.mustakim.bokbok.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavHostController,
    viewModel: ChatViewModel
) {
    val allMessages by viewModel.messages.collectAsState()
    val friendUser by viewModel.friendUser.collectAsState()
    val isFriendOnline by viewModel.isFriendOnline.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    
    // Filter out messages deleted by current user (Optimized with derivedStateOf)
    val messages by remember {
        derivedStateOf {
            allMessages.filter { !it.deletedBy.contains(viewModel.currentUserId) }
        }
    }
    val showEmojiPicker by viewModel.showEmojiPicker.collectAsState()
    
    val listState = rememberLazyListState()
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val scope = rememberCoroutineScope()

    // Handle Back Press to close Emoji Picker
    BackHandler(enabled = showEmojiPicker) {
        viewModel.setShowEmojiPicker(false)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val user = friendUser
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (user != null) {
                            if (user.profileImageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = user.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(50.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(50.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = user.displayName.take(1).uppercase(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = user?.displayName ?: "Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (user != null) {
                                Text(
                                    text = if (isFriendOnline) "Active now" else "Offline",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isFriendOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { }) { Icon(Icons.Default.Call, "Call") }
                    IconButton(onClick = { }) { Icon(Icons.Default.VideoCall, "Video") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { 
                                focusManager.clearFocus()
                                viewModel.setShowEmojiPicker(false)
                            }
                        )
                    }
            ) {
                // Show empty state if no messages
                if (messages.isEmpty()) {
                    EmptyChatState(
                        friendName = friendUser?.displayName ?: "your friend",
                        onSendSuggestion = { suggestion ->
                            viewModel.onMessageChange(suggestion)
                        }
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        reverseLayout = true,
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 16.dp
                        ),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(messages) { index, message ->
                            val isMe = message.senderId == viewModel.currentUserId
                            val nextMessage = messages.getOrNull(index - 1)
                            val prevMessage = messages.getOrNull(index + 1)
                            
                            val isLastInSequence = nextMessage?.senderId != message.senderId
                            val isFirstInSequence = prevMessage?.senderId != message.senderId
                            
                            // Find the last read message from me (for read receipt)
                            val lastReadMessage = messages.firstOrNull { it.senderId == viewModel.currentUserId && it.isRead }
                            val isLastReadMessage = message.id == lastReadMessage?.id

                            // Date Header Logic
                            val showDateHeader = prevMessage == null || !isSameDay(message.timestamp.toDate(), prevMessage.timestamp.toDate())
                            if (showDateHeader) {
                                DateHeader(date = message.timestamp.toDate())
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            MessageBubble(
                                message = message,
                                isMe = isMe,
                                showAvatar = !isMe && isLastInSequence,
                                friendImageUrl = friendUser?.profileImageUrl,
                                friendName = friendUser?.displayName,
                                isFirst = isFirstInSequence,
                                isLast = isLastInSequence,
                                isLastRead = isLastReadMessage,
                                onReply = { viewModel.setReplyingTo(message) },
                                onReact = { emoji -> viewModel.reactToMessage(message.id, emoji) },
                                onDelete = { forEveryone -> viewModel.deleteMessage(message.id, forEveryone) },
                                onRemoveReaction = { viewModel.removeReaction(message.id) },
                                onReplyClick = { replyToId ->
                                    val replyIndex = messages.indexOfFirst { it.id == replyToId }
                                    if (replyIndex != -1) {
                                        scope.launch {
                                            listState.animateScrollToItem(replyIndex)
                                        }
                                    }
                                }
                            )
                            
                            if (isFirstInSequence) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            // Input Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding() // Handle nav bar padding
                    .imePadding() // Handle keyboard padding
            ) {
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

                // Emoji Picker
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    showAvatar: Boolean,
    friendImageUrl: String?,
    friendName: String?,
    isFirst: Boolean,
    isLast: Boolean,
    isLastRead: Boolean,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onDelete: (Boolean) -> Unit,
    onRemoveReaction: () -> Unit,
    onReplyClick: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    var showMenu by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Swipe to Reply Logic
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 60.dp.toPx() }
    val maxSwipe = with(density) { 120.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()

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
                    if (isMe && offsetX < -swipeThreshold) {
                        onReply()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (!isMe && offsetX > swipeThreshold) {
                        onReply()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    
                    androidx.compose.animation.core.animate(
                        initialValue = offsetX,
                        targetValue = 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                    ) { value, _ ->
                        offsetX = value
                    }
                }
            )
    ) {
        // Swipe Indicator Icon
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
                    .then(Modifier.size(24.dp)),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = iconAlpha)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) },
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            // Show Name only for the first message in a group from friend
            if (!isMe && isFirst && friendName != null) {
                Text(
                    text = friendName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 44.dp, bottom = 4.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                if (!isMe) {
                    if (showAvatar) {
                        if (!friendImageUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = friendImageUrl,
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
                                    .background(
                                        MaterialTheme.colorScheme.secondaryContainer,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = (friendName?.take(1) ?: "?").uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(32.dp))
                    }
                    if (!isMe) {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }

                // Bubble Content
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

                    val backgroundColor = if (isMe) Color(0xFFB3C5F6) else Color(0xFFE1D9F5)
                    val contentColor = Color.Black

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
                                        .background(
                                            Color.Black.copy(alpha = 0.05f),
                                            RoundedCornerShape(8.dp)
                                        )
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

                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                        }
                    }

                    // Reactions Badge - adjusted X position only
                    if (message.reactions.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .align(if (isMe) Alignment.BottomStart else Alignment.BottomEnd)
                                .offset(x = if (isMe) 12.dp else (-12).dp, y = 10.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .padding(4.dp)
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                message.reactions.values.distinct().take(3).forEach { emoji ->
                                    Text(text = emoji, style = MaterialTheme.typography.labelSmall)
                                }
                                if (message.reactions.size > 3) {
                                    Text(
                                        text = "+${message.reactions.size - 3}",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    // Reaction Menu (Dropdown styled as dock)
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier
                            .widthIn(min = 280.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainer,
                                RoundedCornerShape(16.dp)
                            )
                    ) {
                        // Reactions Row
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            val emojis = listOf("❤️", "😂", "😮", "😢", "😠", "👍")
                            emojis.forEach { emoji ->
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
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable { /* Open full picker */ },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddCircle,
                                    contentDescription = "More",
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 4.dp),
                            thickness = DividerDefaults.Thickness,
                            color = DividerDefaults.color
                        )

                        // Actions Column
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Reply
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onReply()
                                        showMenu = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Reply,
                                    contentDescription = "Reply",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Reply", style = MaterialTheme.typography.bodyMedium)
                            }

                            // Copy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        clipboardManager.setText(AnnotatedString(message.text))
                                        showMenu = false
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("Copy", style = MaterialTheme.typography.bodyMedium)
                            }

                            // Delete - only if not already unsent
                            if (!message.isDeletedForEveryone) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showMenu = false
                                            showDeleteDialog = true
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Delete",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )

                                }
                            }
                        }
                    }
                }
            }

            // Read receipt avatar - always visible, only for last read message
            if (isMe && isLastRead && !message.isDeletedForEveryone) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = if (isMe) 0.dp else 44.dp)
                ) {
                    if (!friendImageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = friendImageUrl,
                            contentDescription = "Read",
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Fallback: show initial
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    MaterialTheme.colorScheme.secondaryContainer,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (friendName?.take(1) ?: "?").uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Time and status indicator - appear on tap
            AnimatedVisibility(
                visible = showTime,
                enter = androidx.compose.animation.expandVertically() + fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Status indicator for sent messages
                    if (isMe && !message.isDeletedForEveryone) {
                        Row(
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            val statusIcon = when (message.status) {
                                MessageStatus.SENDING -> Icons.Default.Schedule
                                MessageStatus.SENT -> Icons.Default.Done
                                MessageStatus.DELIVERED -> Icons.Default.DoneAll
                                MessageStatus.READ -> Icons.Default.DoneAll
                            }
                            val statusTint = when (message.status) {
                                MessageStatus.SENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.5f
                                )

                                MessageStatus.SENT -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.6f
                                )

                                MessageStatus.DELIVERED -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.6f
                                )

                                MessageStatus.READ -> Color(0xFF4CAF50)
                            }

                            Icon(
                                imageVector = statusIcon,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = statusTint
                            )
                        }
                    }

                    // Timestamp
                    Text(
                        text = SimpleDateFormat(
                            "h:mm a",
                            Locale.getDefault()
                        ).format(message.timestamp.toDate()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .then(
                                if (isMe) Modifier else Modifier.padding(start = 44.dp)
                            ),
                        textAlign = if (isMe) TextAlign.End else TextAlign.Start
                    )
                }
            }
        }
    }

    
    // Delete Dialog - placed outside DropdownMenu to fix UI issues
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    "Delete Message",
                    style = MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                Text(
                    if (isMe) "Choose how you want to delete this message" else "This message will be deleted for you",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                if (isMe) {
                    // For own messages: show both options
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                onDelete(false) // Delete for me
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Delete for Me")
                        }
                        TextButton(
                            onClick = {
                                onDelete(true) // Delete for everyone
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Delete for Everyone")
                        }
                    }
                } else {
                    // For friend's messages: only delete for me
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text("Cancel")
                        }
                        TextButton(
                            onClick = {
                                onDelete(false) // Delete for me only
                                showDeleteDialog = false
                            }
                        ) {
                            Text("Delete")
                        }
                    }
                }
            },
            dismissButton = null
        )
    }
}

@Composable
fun ChatInputBar(
    text: String,
    modifier: Modifier = Modifier,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    replyingTo: Message?,
    onCancelReply: () -> Unit,
    onEmojiClick: () -> Unit,
    showEmojiPicker: Boolean,
    onFocus: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }
    
    // Derive visibility states to prevent unnecessary recompositions
    val showActionIcons = remember(isFocused, text) { !isFocused && text.isEmpty() }
    val showSendButton = remember(isFocused, text) { text.isNotBlank() || isFocused }

    Column(modifier = modifier.fillMaxWidth()) {
        // Reply Banner
        AnimatedVisibility(visible = replyingTo != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Replying to ${if (replyingTo?.senderId == "me") "You" else "Friend"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = replyingTo?.text ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onCancelReply) {
                    Icon(Icons.Default.Close, "Cancel Reply")
                }
            }
        }

        // Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp) 
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFFE1D9F5))
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Action Icons - use derived state
                AnimatedVisibility(
                    visible = showActionIcons,
                    enter = fadeIn() + androidx.compose.animation.expandHorizontally(),
                    exit = fadeOut() + androidx.compose.animation.shrinkHorizontally()
                ) {
                    Row {
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.AddCircle, "Add", tint = Color(0xFF1D1B20), modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.CameraAlt, "Camera", tint = Color(0xFF1D1B20), modifier = Modifier.size(28.dp))
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Mic, "Mic", tint = Color(0xFF1D1B20), modifier = Modifier.size(28.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Text Field Container with Emoji Button Inside
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp, max = 120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White)
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty() && !isFocused) {
                            Text("Message", style = MaterialTheme.typography.bodyLarge, color = Color.Gray)
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                            cursorBrush = SolidColor(Color.Black),
                            maxLines = 5,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { focusState -> 
                                    isFocused = focusState.isFocused 
                                    if (focusState.isFocused) {
                                        onFocus()
                                    }
                                }
                        )
                    }
                    
                    // Emoji Button (Inside Text Field Container)
                    IconButton(
                        onClick = onEmojiClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEmotions,
                            contentDescription = "Emoji",
                            tint = if (showEmojiPicker) MaterialTheme.colorScheme.primary else Color(0xFF6B6B6B),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Send Button - use derived state
                AnimatedVisibility(
                    visible = showSendButton,
                    enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn() + androidx.compose.animation.expandHorizontally(),
                    exit = scaleOut() + fadeOut() + androidx.compose.animation.shrinkHorizontally()
                ) {
                    IconButton(onClick = onSend, modifier = Modifier.padding(start = 4.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun EmojiPicker(
    onEmojiSelected: (String) -> Unit
) {
    AndroidView(
        factory = { context ->
            androidx.emoji2.emojipicker.EmojiPickerView(context).apply {
                setOnEmojiPickedListener { emojiViewItem ->
                    onEmojiSelected(emojiViewItem.emoji)
                }
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .background(MaterialTheme.colorScheme.surface)
    )
}

@Composable
fun DateHeader(date: Date) {
    val formatter = SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault())
    val dateString = when {
        isToday(date) -> "Today"
        isYesterday(date) -> "Yesterday"
        else -> formatter.format(date)
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dateString,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

fun isSameDay(date1: Date, date2: Date): Boolean {
    val fmt = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return fmt.format(date1) == fmt.format(date2)
}

fun isToday(date: Date): Boolean {
    return isSameDay(date, Date())
}

fun isYesterday(date: Date): Boolean {
    val cal = java.util.Calendar.getInstance()
    cal.add(java.util.Calendar.DATE, -1)
    return isSameDay(date, cal.time)
}
