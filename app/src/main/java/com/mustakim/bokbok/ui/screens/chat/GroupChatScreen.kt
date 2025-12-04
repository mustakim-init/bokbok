package com.mustakim.bokbok.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.viewmodel.GroupChatViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    navController: NavHostController,
    viewModel: GroupChatViewModel
) {
    val allMessages by viewModel.messages.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    val replyingTo by viewModel.replyingTo.collectAsState()
    
    // Filter out messages deleted by current user
    val messages = remember(allMessages) {
        allMessages.filter { !it.deletedBy.contains("me") }
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

    // Calculate Read Receipts: Map<MessageId, List<User>>
    // For each user (except me), find the *latest* message they have read.
    val readReceiptsMap = remember(messages, groupMembers) {
        val map = mutableMapOf<String, MutableList<User>>()
        groupMembers.values.filter { it.uid != "me" }.forEach { user ->
            // Find the latest message (first in list) that contains user.uid in readBy
            val lastReadMsg = messages.firstOrNull { it.readBy.contains(user.uid) }
            if (lastReadMsg != null) {
                map.getOrPut(lastReadMsg.id) { mutableListOf() }.add(user)
            }
        }
        map
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Trip Planning", // Hardcoded for now, or derive from VM
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${groupMembers.size} members",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                    IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, "More") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
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
                        val isMe = message.senderId == "me"
                        val nextMessage = messages.getOrNull(index - 1)
                        val prevMessage = messages.getOrNull(index + 1)
                        
                        val isLastInSequence = nextMessage?.senderId != message.senderId
                        val isFirstInSequence = prevMessage?.senderId != message.senderId
                        
                        // Sender Info
                        val sender = groupMembers[message.senderId]
                        
                        // Date Header Logic
                        val showDateHeader = prevMessage == null || !isSameDay(message.timestamp.toDate(), prevMessage.timestamp.toDate())
                        if (showDateHeader) {
                            DateHeader(date = message.timestamp.toDate())
                            Spacer(modifier = Modifier.height(8.dp))
                        }

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

            // Input Area (Reusing ChatInputBar from ChatScreen.kt if possible, or copy it)
            // Since ChatInputBar is in ChatScreen.kt, I can import it if it's public.
            // It is public.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .imePadding()
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

    // Swipe to Reply Logic
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
            // Sender Name (Only for first message in sequence from others)
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

                            Text(
                                text = message.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = contentColor
                            )
                        }
                    }

                    // Grouped Reactions Badge
                    if (message.reactions.isNotEmpty()) {
                        val reactionCounts = message.reactions.values.groupingBy { it }.eachCount()
                        val sortedReactions = reactionCounts.entries.sortedByDescending { it.value }

                        Box(
                            modifier = Modifier
                                .align(if (isMe) Alignment.BottomStart else Alignment.BottomEnd)
                                .offset(x = if (isMe) 12.dp else (-12).dp, y = 10.dp)
                                .background(MaterialTheme.colorScheme.surface, CircleShape)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
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
                                if (sortedReactions.size > 3) {
                                    Text(text = "+", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Menu (Same as ChatScreen)
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
                        }
                        HorizontalDivider()
                        // Actions
                        Column {
                            DropdownMenuItem(icon = Icons.AutoMirrored.Filled.Reply, text = "Reply") { onReply(); showMenu = false }
                            DropdownMenuItem(icon = Icons.Default.ContentCopy, text = "Copy") { clipboardManager.setText(AnnotatedString(message.text)); showMenu = false }
                            if (!message.isDeletedForEveryone) {
                                DropdownMenuItem(icon = Icons.Default.Delete, text = "Delete", color = MaterialTheme.colorScheme.error) { showMenu = false; showDeleteDialog = true }
                            }
                        }
                        
                        // Delete Dialog (Same logic as ChatScreen)
                        if (showDeleteDialog) {
                             AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Delete Message") },
                                text = { Text(if (isMe) "Choose how to delete" else "Delete for you?") },
                                confirmButton = {
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
                                        TextButton(onClick = { onDelete(false); showDeleteDialog = false }) { Text(if (isMe) "Delete for Me" else "Delete") }
                                        if (isMe) {
                                            TextButton(onClick = { onDelete(true); showDeleteDialog = false }) { Text("Delete for Everyone") }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
            
            // Read Receipts (Multiple Avatars)
            // Show only if this message is the last read message for some users
            if (readReceiptUsers.isNotEmpty() && isMe && !message.isDeletedForEveryone) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    readReceiptUsers.forEachIndexed { index, user ->
                        Box(
                            modifier = Modifier
                                .offset(x = (index * -8).dp) // Overlap effect
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

            // Time Indicator (Same as ChatScreen)
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
}

@Composable
fun DropdownMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color = Color.Unspecified, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = text, tint = if (color == Color.Unspecified) LocalContentColor.current else color, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (color == Color.Unspecified) Color.Unspecified else color)
    }
}


