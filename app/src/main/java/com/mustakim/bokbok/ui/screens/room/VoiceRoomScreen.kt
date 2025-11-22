package com.mustakim.bokbok.ui.screens.room

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mustakim.bokbok.data.model.VoiceRoomParticipant
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.components.ParticipantCard
import com.mustakim.bokbok.ui.components.VoiceControlsSheet
import com.mustakim.bokbok.viewmodel.VoiceRoomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceRoomScreen(
    roomId: String,
    onMinimize: (Boolean) -> Unit,
    onLeaveRoom: () -> Unit,
    viewModel: VoiceRoomViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val friends by viewModel.friends.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            uiState.room?.let { viewModel.startCallEngine(it.id) }
        } else {
            viewModel.onPermissionDenied()
        }
    }

    LaunchedEffect(roomId) { viewModel.loadRoom(roomId) }

    LaunchedEffect(uiState.room) {
        val room = uiState.room ?: return@LaunchedEffect
        if (viewModel.hasRequiredCallPermissions()) {
            viewModel.startCallEngine(room.id)
        } else {
            permissionLauncher.launch(viewModel.getRequiredPermissions())
        }
    }

    val view = LocalView.current
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            android.widget.Toast.makeText(view.context, error, android.widget.Toast.LENGTH_LONG).show()
            if (error.contains("Room is full") || error.contains("Failed to load")) onLeaveRoom()
        }
    }

    val globalMuted by RoomStateManager.isMuted
    LaunchedEffect(globalMuted) { viewModel.setMutedFromGlobal(globalMuted) }

    var showAddUserDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme
    val isDarkTheme = isSystemInDarkTheme()

    val gradientBrush = remember(isDarkTheme, colorScheme.primary) {
        val colors = if (isDarkTheme) {
            listOf(colorScheme.primaryContainer, colorScheme.secondaryContainer, colorScheme.tertiaryContainer)
        } else {
            listOf(colorScheme.primary, colorScheme.secondary, colorScheme.tertiary)
        }
        Brush.verticalGradient(colors = colors)
    }

    DisposableEffect(colorScheme.primary, isDarkTheme) {
        val window = (view.context as? android.app.Activity)?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, view)
        val isLightPrimary = colorScheme.primary.luminance() > 0.5f
        insetsController.isAppearanceLightStatusBars = isLightPrimary
        insetsController.isAppearanceLightNavigationBars = isLightPrimary
        onDispose {
            insetsController.isAppearanceLightStatusBars = !isDarkTheme
            insetsController.isAppearanceLightNavigationBars = !isDarkTheme
        }
    }

    val roomName = remember(uiState.room?.name) { uiState.room?.name ?: "Voice Room" }
    val onMinimizeCallback: () -> Unit = remember(uiState.isMuted) { { onMinimize(uiState.isMuted) } }

    val bottomSheetState = rememberStandardBottomSheetState(
        initialValue = SheetValue.PartiallyExpanded,
        skipHiddenState = true
    )
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = bottomSheetState)
    val density = LocalDensity.current

    // We ensure peek height is enough for the "floating pill" + gap
    val peekHeightDp = 130.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layoutHeight = maxHeight
        val layoutHeightPx = with(density) { maxHeight.toPx() }
        val peekHeightPx = with(density) { peekHeightDp.toPx() }

        val currentOffset by remember(bottomSheetState) {
            derivedStateOf {
                try {
                    bottomSheetState.requireOffset()
                } catch (_: Exception) {
                    if (bottomSheetState.currentValue == SheetValue.Expanded) 0f
                    else layoutHeightPx - peekHeightPx
                }
            }
        }

        val expansionFraction by remember(currentOffset) {
            derivedStateOf {
                val maxOffset = layoutHeightPx - peekHeightPx
                val minOffset = 0f
                val fraction = (maxOffset - currentOffset) / (maxOffset - minOffset)
                fraction.coerceIn(0f, 1f)
            }
        }

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = peekHeightDp,
            sheetShape = RoundedCornerShape(0.dp), // We handle shape inside VoiceControlsSheet
            sheetContainerColor = Color.Transparent, // Essential for floating effect
            sheetContentColor = MaterialTheme.colorScheme.onSurface,
            // ADD THESE TWO LINES to remove the square shadow:
            sheetShadowElevation = 0.dp,
            sheetTonalElevation = 0.dp,
            sheetDragHandle = null, // We added our own custom stick inside
            sheetContent = {
                // Wrapper to give the sheet a max height constraint
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layoutHeight)
                ) {
                    VoiceControlsSheet(
                        isMuted = uiState.isMuted,
                        isSpeakerOn = uiState.isSpeakerOn,
                        isA2dpModeOn = uiState.isA2dpModeOn,
                        expansionFraction = expansionFraction,
                        screenHeight = layoutHeight,
                        onToggleMic = viewModel::toggleMic,
                        onToggleSpeaker = viewModel::toggleSpeaker,
                        onOpenChat = { /* TODO */ },
                        onOpenVoiceEffects = { /* TODO */ },
                        onToggleAudioMode = viewModel::toggleA2dpMode,
                        onShareInvite = { /* TODO */ },
                        onLeaveRoom = {
                            viewModel.leaveRoom()
                            onLeaveRoom()
                        }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush = gradientBrush)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()

                ) {
                    VoiceRoomTopBar(
                        roomName = roomName,
                        isSpeakerOn = uiState.isSpeakerOn,
                        onMinimize = onMinimizeCallback,
                        onToggleSpeaker = viewModel::toggleSpeaker,
                        onInviteFriends = { showAddUserDialog = true }
                    )

                    Spacer(modifier = Modifier.height(32.dp))

                    DynamicParticipantGrid(
                        participants = uiState.participants,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 140.dp) // Ensure grid isn't hidden by floating dock
                    )
                }
            }
        }
    }

    if (showAddUserDialog) {
        AddParticipantsDialog(
            friends = friends,
            onDismiss = { showAddUserDialog = false },
            onConfirm = { userIds ->
                viewModel.inviteUsers(userIds)
                showAddUserDialog = false
            }
        )
    }
}

// ... (Rest of the components: VoiceRoomTopBar, DynamicParticipantGrid, etc. remain unchanged)
@Composable
private fun VoiceRoomTopBar(
    roomName: String,
    isSpeakerOn: Boolean,
    onMinimize: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onInviteFriends: () -> Unit
) {
    val speakerIcon = remember(isSpeakerOn) {
        if (isSpeakerOn) Icons.AutoMirrored.Filled.VolumeUp
        else Icons.AutoMirrored.Filled.VolumeOff
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMinimize,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "Minimize", tint = Color.White, modifier = Modifier.size(24.dp))
        }

        Spacer(modifier = Modifier.width(12.dp))

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.3f)
        ) {
            Text(
                text = roomName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        IconButton(
            onClick = onToggleSpeaker,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(speakerIcon, if (isSpeakerOn) "Mute" else "Unmute", tint = Color.White, modifier = Modifier.size(22.dp))
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(
            onClick = onInviteFriends,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.3f))
        ) {
            Icon(Icons.Default.PersonAdd, "Invite", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DynamicParticipantGrid(
    participants: List<VoiceRoomParticipant>,
    modifier: Modifier = Modifier
) {
    val stableParticipants = remember(participants) { participants }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (stableParticipants.size) {
            0 -> EmptyRoomState()
            1 -> SingleParticipantLayout(stableParticipants[0])
            2 -> TwoParticipantLayout(stableParticipants)
            3 -> ThreeParticipantLayout(stableParticipants)
            4 -> FourParticipantLayout(stableParticipants)
            else -> FiveOrMoreParticipantLayout(stableParticipants)
        }
    }
}

@Composable
private fun EmptyRoomState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("🎤", style = MaterialTheme.typography.displayLarge, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Waiting for others to join...", style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.8f))
    }
}

@Composable
private fun SingleParticipantLayout(participant: VoiceRoomParticipant) {
    ParticipantCard(participant = participant, modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(0.8f))
}

@Composable
private fun TwoParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
        participants.forEach { p -> ParticipantCard(participant = p, modifier = Modifier.weight(1f).aspectRatio(0.8f)) }
    }
}

@Composable
private fun ThreeParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        ParticipantCard(participant = participants[0], modifier = Modifier.fillMaxWidth(0.7f).aspectRatio(1f).align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            participants.drop(1).forEach { p -> ParticipantCard(participant = p, modifier = Modifier.weight(1f).aspectRatio(0.8f)) }
        }
    }
}

@Composable
private fun FourParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            participants.take(2).forEach { p -> ParticipantCard(participant = p, modifier = Modifier.weight(1f).aspectRatio(0.8f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)) {
            participants.drop(2).forEach { p -> ParticipantCard(participant = p, modifier = Modifier.weight(1f).aspectRatio(0.8f)) }
        }
    }
}

@Composable
private fun FiveOrMoreParticipantLayout(participants: List<VoiceRoomParticipant>) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            participants.take(3).forEach { p -> ParticipantCard(participant = p, modifier = Modifier.weight(1f).aspectRatio(0.75f)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            val remaining = participants.drop(3).take(3)
            remaining.forEach { p -> ParticipantCard(participant = p, modifier = Modifier.weight(1f).aspectRatio(0.75f)) }
            repeat((3 - remaining.size).coerceAtLeast(0)) { Spacer(modifier = Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun AddParticipantsDialog(
    friends: List<com.mustakim.bokbok.data.model.FriendWithUser>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selectedTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var manualUserId by remember { androidx.compose.runtime.mutableStateOf("") }
    val selectedFriendIds = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    var searchQuery by remember { androidx.compose.runtime.mutableStateOf("") }

    val filteredFriends = remember(friends, searchQuery) {
        if (searchQuery.isBlank()) friends
        else friends.filter {
            it.user.username.contains(searchQuery, ignoreCase = true) ||
                    (it.user.displayName.contains(searchQuery, ignoreCase = true))
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.CenterHorizontally),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Add Participants",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(16.dp))
                androidx.compose.material3.PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {}
                ) {
                    androidx.compose.material3.Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Friends") }
                    )
                    androidx.compose.material3.Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("By ID") }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    if (selectedTab == 0) {
                        Column {
                            androidx.compose.material3.OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search friends...") },
                                leadingIcon = { Icon(Icons.Default.Search, null) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(8.dp))
                            if (friends.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("No friends found", style = MaterialTheme.typography.bodyMedium)
                                }
                            } else {
                                androidx.compose.foundation.lazy.LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(filteredFriends) { friend ->
                                        val isSelected = selectedFriendIds.contains(friend.user.uid)
                                        androidx.compose.material3.ListItem(
                                            headlineContent = { Text(friend.user.displayName.ifBlank { friend.user.username }) },
                                            supportingContent = { Text("@${friend.user.username}") },
                                            leadingContent = {
                                                val imageModifier = Modifier.size(40.dp).clip(CircleShape)
                                                if (friend.user.profileImageUrl.isNotBlank()) {
                                                    coil.compose.AsyncImage(
                                                        model = friend.user.profileImageUrl,
                                                        contentDescription = null,
                                                        modifier = imageModifier,
                                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                    )
                                                } else {
                                                    Box(
                                                        modifier = imageModifier.background(MaterialTheme.colorScheme.primaryContainer),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(friend.user.username.take(1).uppercase())
                                                    }
                                                }
                                            },
                                            trailingContent = {
                                                androidx.compose.material3.Checkbox(
                                                    checked = isSelected,
                                                    onCheckedChange = { checked ->
                                                        if (checked) selectedFriendIds.add(friend.user.uid)
                                                        else selectedFriendIds.remove(friend.user.uid)
                                                    }
                                                )
                                            },
                                            colors = androidx.compose.material3.ListItemDefaults.colors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent
                                            ),
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    if (isSelected) selectedFriendIds.remove(friend.user.uid)
                                                    else selectedFriendIds.add(friend.user.uid)
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.Center) {
                            Text(
                                text = "Enter the User ID to add them to this room.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.OutlinedTextField(
                                value = manualUserId,
                                onValueChange = { manualUserId = it },
                                label = { Text("User ID") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            if (selectedTab == 0) {
                                onConfirm(selectedFriendIds.toList())
                            } else {
                                if (manualUserId.isNotBlank()) {
                                    onConfirm(listOf(manualUserId))
                                }
                            }
                        },
                        enabled = (selectedTab == 0 && selectedFriendIds.isNotEmpty()) || (selectedTab == 1 && manualUserId.isNotBlank())
                    ) {
                        Text(if (selectedTab == 0) "Add Selected (${selectedFriendIds.size})" else "Add User")
                    }
                }
            }
        }
    }
}