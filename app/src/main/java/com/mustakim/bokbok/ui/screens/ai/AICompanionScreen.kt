package com.mustakim.bokbok.ui.screens.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
    val isSpeaking by viewModel.isSpeaking.collectAsState()
    val isVoiceModeEnabled by viewModel.isVoiceModeEnabled.collectAsState()
    val ttsMode by viewModel.ttsMode.collectAsState()
    val amplitude by viewModel.amplitude.collectAsState()
    val showPermissionDialog by viewModel.showPermissionDialog.collectAsState()
    val showOverlayPermissionDialog by viewModel.showOverlayPermissionDialog.collectAsState()
    
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            title = { Text("Accessibility Required") },
            text = { Text("To use Voice Mode in the background, BokBok AI needs Accessibility permission. If Shizuku is not running, please enable it manually in Settings.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissPermissionDialog()
                    try {
                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showOverlayPermissionDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissPermissionDialog() },
            title = { Text("Overlay Permission Required") },
            text = { Text("BokBok AI needs permission to show the edge glow and mic button over other apps. Please enable 'Display over other apps'.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissPermissionDialog()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        try {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                            context.startActivity(intent)
                        }
                    }
                }) {
                    Text("Grant Permission")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPermissionDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

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

    // Auto scroll to bottom
    LaunchedEffect(messages?.size, uiState) {
        if (messages != null && (messages!!.isNotEmpty() || uiState is CompanionUiState.Streaming)) {
            listState.animateScrollToItem(0)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.selectImage(uri) }
    )

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                                        contentPadding = PaddingValues(bottom = 140.dp, top = 16.dp),
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        if (groupedMessages.isNullOrEmpty() && uiState is CompanionUiState.Idle) {
                                            item {
                                                EmptyCompanionState(
                                                    onSuggestionClick = { viewModel.onInputChange(it) },
                                                    modifier = Modifier.fillMaxWidth().padding(top = 100.dp)
                                                )
                                            }
                                        }

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

                            AIInputArea(
                                inputText = inputText,
                                onInputChange = viewModel::onInputChange,
                                onSendMessage = viewModel::sendMessage,
                                selectedImage = selectedImage,
                                onSelectImage = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onClearImage = viewModel::clearImage,
                                isListening = isVoiceModeEnabled,
                                onStartVoice = viewModel::toggleVoiceMode,
                                ttsMode = ttsMode,
                                onTtsModeChange = viewModel::setTtsMode
                            )
                        }

                        // Premium Voice Interaction Overlay
                        if (isVoiceModeEnabled && (isListening || isSpeaking)) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(220.dp)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                                MaterialTheme.colorScheme.surface
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    GeminiVisualizer(
                                        amplitude = amplitude,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(140.dp)
                                    )
                                    Text(
                                        text = if (isListening) "I'm listening..." else "BokBok is speaking...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GeminiVisualizer(
    amplitude: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition()
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        )
    )

    // Smoothly interpolate amplitude for visual stability
    val animatedAmplitude by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 10000f) / 10000f,
        animationSpec = tween(150),
        label = "amplitude"
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        
        val colors = listOf(
            Color(0xFF4285F4), // Blue
            Color(0xFFEA4335), // Red
            Color(0xFFFBBC05), // Yellow
            Color(0xFF34A853)  // Green
        )

        colors.forEachIndexed { index, color ->
            val path = androidx.compose.ui.graphics.Path()
            val speed = 1f + index * 0.3f
            val frequency = 0.008f + index * 0.004f
            val waveHeight = (height * 0.1f) + (animatedAmplitude * height * 0.4f)
            
            path.moveTo(0f, centerY)
            for (x in 0..width.toInt() step 4) {
                val xFloat = x.toFloat()
                // Complex wave formula for "living" feel
                val y = centerY + waveHeight * 
                        kotlin.math.sin(xFloat * frequency + phase * speed + index) *
                        kotlin.math.cos(xFloat * frequency * 0.5f + phase * 0.5f)
                path.lineTo(xFloat, y)
            }
            
            drawPath(
                path = path,
                color = color.copy(alpha = 0.5f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = (4 + index).dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
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
    onStartVoice: () -> Unit,
    ttsMode: com.mustakim.bokbok.viewmodel.AICompanionViewModel.TtsMode,
    onTtsModeChange: (com.mustakim.bokbok.viewmodel.AICompanionViewModel.TtsMode) -> Unit
) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        VoiceSettingsDialog(
            ttsMode = ttsMode,
            onTtsModeChange = onTtsModeChange,
            onDismiss = { showSettings = false }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .navigationBarsPadding()
            .imePadding()
            .padding(16.dp)
    ) {
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
                .padding(horizontal = 4.dp, vertical = 4.dp),
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

            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Voice Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
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
                    modifier = Modifier.fillMaxWidth()
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
@Composable
fun VoiceSettingsDialog(
    ttsMode: AICompanionViewModel.TtsMode,
    onTtsModeChange: (AICompanionViewModel.TtsMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Voice Settings")
        },
        text = {
            Column {
                Text(
                    text = "Text-to-Speech Engine",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTtsModeChange(AICompanionViewModel.TtsMode.LEGACY) }
                        .padding(vertical = 12.dp)
                ) {
                    RadioButton(
                        selected = ttsMode == AICompanionViewModel.TtsMode.LEGACY,
                        onClick = { onTtsModeChange(AICompanionViewModel.TtsMode.LEGACY) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Standard (Legacy)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Fast, uses system engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onTtsModeChange(AICompanionViewModel.TtsMode.QUALITY) }
                        .padding(vertical = 12.dp)
                ) {
                    RadioButton(
                        selected = ttsMode == AICompanionViewModel.TtsMode.QUALITY,
                        onClick = { onTtsModeChange(AICompanionViewModel.TtsMode.QUALITY) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Sherpa-ONNX (Neural)",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "High quality, offline AI model",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Language Management",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LanguageDownloadItem(
                    langName = "English (US)",
                    langCode = "en",
                    viewModel = hiltViewModel()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                LanguageDownloadItem(
                    langName = "Bengali (Bangla)",
                    langCode = "bn",
                    viewModel = hiltViewModel()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
fun LanguageDownloadItem(
    langName: String,
    langCode: String,
    viewModel: AICompanionViewModel
) {
    val downloadedLangs by viewModel.downloadedLanguages.collectAsState(initial = emptyList())
    val isDownloaded = downloadedLangs.contains(langCode)
    val workInfo by viewModel.getDownloadStatus(langCode).collectAsState(initial = null)
    
    val isDownloading = workInfo?.state == androidx.work.WorkInfo.State.RUNNING || 
                        workInfo?.state == androidx.work.WorkInfo.State.ENQUEUED
    val progress = workInfo?.progress?.getFloat("PROGRESS", 0f) ?: 0f
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = langName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                if (isDownloading) {
                    Text(text = "Downloading: ${progress.toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else if (isDownloaded) {
                    Text(text = "Ready", style = MaterialTheme.typography.labelSmall, color = Color(0xFF34A853))
                } else {
                    Text(text = "Not downloaded", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            if (isDownloading) {
                IconButton(onClick = { viewModel.cancelDownload(langCode) }) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.error)
                }
            } else if (!isDownloaded) {
                Button(
                    onClick = { viewModel.downloadLanguage(langCode) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (workInfo?.state == androidx.work.WorkInfo.State.CANCELLED) "Resume" else "Download", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF34A853), modifier = Modifier.size(24.dp))
            }
        }
        
        if (isDownloading) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
    }
}
