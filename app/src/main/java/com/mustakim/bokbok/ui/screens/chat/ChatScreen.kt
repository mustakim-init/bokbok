package com.mustakim.bokbok.ui.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.viewmodel.ChatViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: NavHostController,
    viewModel: ChatViewModel

) {
    val messages by viewModel.messages.collectAsState()
    val friendUser by viewModel.friendUser.collectAsState()
    val messageText by viewModel.messageText.collectAsState()
    
    val listState = rememberLazyListState()

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

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
                        // Avatar in Top Bar
                        if (user != null) {
                            if (user.profileImageUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = user.profileImageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
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
                                    text = "Active now",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { focusManager.clearFocus() }
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
                    bottom = 100.dp // Add space for input bar
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.ime), // Push list up with keyboard
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(messages) { index, message ->
                    val isMe = message.senderId == "me"
                    val nextMessage = messages.getOrNull(index - 1)
                    val prevMessage = messages.getOrNull(index + 1)
                    
                    val isLastInSequence = nextMessage?.senderId != message.senderId
                    val isFirstInSequence = prevMessage?.senderId != message.senderId

                    MessageBubble(
                        message = message,
                        isMe = isMe,
                        showAvatar = !isMe && isLastInSequence,
                        friendImageUrl = friendUser?.profileImageUrl,
                        friendName = friendUser?.displayName,
                        isFirst = isFirstInSequence,
                        isLast = isLastInSequence
                    )
                    
                    if (isFirstInSequence) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            // Place Input Bar here, aligned to bottom
            ChatInputBar(
                text = messageText,
                onTextChange = viewModel::onMessageChange,
                onSend = viewModel::sendMessage,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .imePadding()
            )
        }
    }
}

@Composable
fun MessageBubble(
    message: Message,
    isMe: Boolean,
    showAvatar: Boolean,
    friendImageUrl: String?,
    friendName: String?,
    isFirst: Boolean,
    isLast: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
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
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
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
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Bubble Shape & Color
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

            val backgroundColor = if (isMe) 
                Color(0xFFB3C5F6) // Soft Blue
            else 
                Color(0xFFE1D9F5) // Soft Purple

            val contentColor = Color.Black

            Surface(
                color = backgroundColor,
                shape = shape,
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    // Floating Dock Style
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp) 
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()

                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xFFE1D9F5)) // Light purple container background
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Action Icons - Hide when focused
            AnimatedVisibility(
                visible = !isFocused,
                enter = fadeIn() + androidx.compose.animation.expandHorizontally(),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
            ) {
                Row {
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.AddCircle,
                            contentDescription = "Add",
                            tint = Color(0xFF1D1B20),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Camera",
                            tint = Color(0xFF1D1B20),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    IconButton(onClick = { }) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Mic",
                            tint = Color(0xFF1D1B20),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // White Pill Text Field
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 120.dp) // Allow expansion up to 120.dp
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp), // Add vertical padding
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty() && !isFocused) {
                    Text(
                        text = "Message",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.Black
                    ),
                    cursorBrush = SolidColor(Color.Black),
                    maxLines = 5, // Add this to allow multiple lines
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            isFocused = focusState.isFocused
                        }
                )
            }


            // Send Button
             AnimatedVisibility(
                visible = text.isNotBlank() || isFocused,
                enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut()
            ) {
                 IconButton(
                    onClick = onSend,
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}
