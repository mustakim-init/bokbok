package com.mustakim.bokbok.ui.screens.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mustakim.bokbok.viewmodel.AICompanionViewModel
import com.mustakim.bokbok.viewmodel.CompanionUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AICompanionScreen(
    navController: NavHostController,
    viewModel: AICompanionViewModel = hiltViewModel()
) {
    val sessions by viewModel.sessions.collectAsState()
    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedImage by viewModel.selectedImage.collectAsState()
    val isListening by viewModel.isListening.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.onScreenVisible()
    }

    // Group messages by date
    val groupedMessages = remember(messages) {
        messages?.groupBy {
            val date = java.util.Date(it.timestamp)
            java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()).format(date)
        }
    }

    // Auto scroll to bottom (Index 0 in reverseLayout)
    LaunchedEffect(messages?.size, uiState) {
        if (messages != null && (messages!!.isNotEmpty() || uiState is CompanionUiState.Streaming)) {
            // In reverseLayout, index 0 is at the bottom (newest message)
            listState.animateScrollToItem(0)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.selectImage(uri) }
    )

    // Right-to-Left Drawer trick: Use CompositionLocal to flip the direction for the drawer
    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // Return to Ltr for drawer content so text isn't flipped
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxHeight().width(300.dp),
                        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        AISessionSidebar(
                            sessions = sessions,
                            currentSessionId = currentSessionId,
                            onSessionClick = {
                                viewModel.selectSession(it)
                                scope.launch { drawerState.close() }
                            },
                            onNewChatClick = {
                                viewModel.startNewChat()
                                scope.launch { drawerState.close() }
                            },
                            onDeleteSession = { viewModel.deleteSession(it) }
                        )
                    }
                }
            }
        ) {
            // Main Content - Back to Ltr
            CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
                Scaffold(
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "BokBok AI",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = { navController.navigateUp() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.History, "History")
                                }
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
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
                        // Message List Area
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            if (messages == null) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    reverseLayout = true,
                                    contentPadding = PaddingValues(vertical = 16.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    // 0. Empty State (If no messages)
                                    if (groupedMessages.isNullOrEmpty() && uiState is CompanionUiState.Idle) {
                                        item {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(bottom = 100.dp) // Visual lift from bottom
                                            ) {
                                                EmptyCompanionState(
                                                    onSuggestionClick = { viewModel.onInputChange(it) },
                                                    modifier = Modifier.align(Alignment.Center)
                                                )
                                            }
                                        }
                                    }

                                    // 1. Loading / Streaming Indicators
                                    when (val state = uiState) {
                                        is CompanionUiState.Generating -> {
                                            item(key = "generating_indicator") {
                                                AIMessageBubble(
                                                    message = com.mustakim.bokbok.data.model.AIMessage(
                                                        conversationId = "generating",
                                                        role = com.mustakim.bokbok.data.model.MessageRole.ASSISTANT,
                                                        content = ""
                                                    ),
                                                    isGenerating = true
                                                )
                                            }
                                        }

                                        is CompanionUiState.Streaming -> {
                                            item(key = "streaming_indicator") {
                                                AIMessageBubble(
                                                    message = com.mustakim.bokbok.data.model.AIMessage(
                                                        conversationId = "streaming",
                                                        role = com.mustakim.bokbok.data.model.MessageRole.ASSISTANT,
                                                        content = state.partialResponse
                                                    )
                                                )
                                            }
                                        }

                                        is CompanionUiState.Error -> {
                                            item(key = "error_indicator") {
                                                Text(
                                                    text = "Error: ${state.message}",
                                                    color = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.padding(16.dp)
                                                )
                                            }
                                        }

                                        else -> {}
                                    }

                                    // 2. Messages (Newest first)
                                    val reversedGroups = groupedMessages?.entries?.toList()?.reversed()
                                    
                                    reversedGroups?.forEach { (date, messageList) ->
                                        items(
                                            items = messageList.reversed(),
                                            key = { it.id }
                                        ) { message ->
                                            AIMessageBubble(message = message)
                                        }
                                        
                                        item(key = "header_$date") {
                                            MessageDateHeader(date = date)
                                        }
                                    }
                                }
                            }
                        }

                        // Refined Input Area Component
                        AIInputArea(
                            inputText = inputText,
                            onInputChange = viewModel::onInputChange,
                            onSendMessage = viewModel::sendMessage,
                            selectedImage = selectedImage,
                            onSelectImage = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly
                                    )
                                )
                            },
                            onClearImage = viewModel::clearImage,
                            isListening = isListening,
                            onStartVoice = viewModel::startVoiceInput
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageDateHeader(date: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyCompanionState(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Hello! I'm BokBok AI",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "I can help you optimize your gaming experience.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(32.dp))
        SuggestionChips(onChipClick = onSuggestionClick)
    }
}

@Composable
fun AIInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    selectedImage: Uri?,
    onSelectImage: () -> Unit,
    onClearImage: () -> Unit,
    isListening: Boolean,
    onStartVoice: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .navigationBarsPadding()
            .imePadding() // Localized here for smooth content push
            .padding(16.dp) // Proper visual padding on all sides
    ) {
        // Selected Image Preview
        if (selectedImage != null) {
            Box(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = selectedImage,
                    contentDescription = "Selected Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onClearImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                .padding(horizontal = 4.dp, vertical = 4.dp), // Thinner inner padding for the pill
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSelectImage,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Image",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            ) {
                if (inputText.isEmpty()) {
                    Text(
                        "Ask something...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                BasicTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                )
            }

            if (inputText.isNotEmpty() || selectedImage != null) {
                IconButton(
                    onClick = onSendMessage,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                VoiceInputButton(
                    isListening = isListening,
                    onClick = onStartVoice
                )
            }
        }
    }
}

@Composable
fun AISessionSidebar(
    sessions: List<com.mustakim.bokbok.data.model.AISession>,
    currentSessionId: String?,
    onSessionClick: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onDeleteSession: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Chat History",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Button(
            onClick = onNewChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            contentPadding = PaddingValues(16.dp),
            shape = RoundedCornerShape(16.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.Add, null)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Start New Chat", fontWeight = FontWeight.SemiBold)
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(sessions) { session ->
                val isSelected = session.id == currentSessionId
                Surface(
                    onClick = { onSessionClick(session.id) },
                    shape = RoundedCornerShape(16.dp),
                    color = if (isSelected) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) 
                    else 
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.ChatBubble else Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            val date = remember(session.lastUpdated) {
                                java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                                    .format(java.util.Date(session.lastUpdated))
                            }
                            Text(
                                text = date,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(
                            onClick = { onDeleteSession(session.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.DeleteOutline, 
                                null, 
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceInputButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition()
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.5f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(contentAlignment = Alignment.Center) {
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                    .graphicsLayer(scaleX = pulseScale, scaleY = pulseScale)
            )
        }
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                contentDescription = "Voice Input",
                tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
