package com.mustakim.bokbok.ui.screens.lounge

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.VoiceRoom
import com.mustakim.bokbok.state.JoinMode
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.components.FriendsStatusSection
import com.mustakim.bokbok.ui.components.LoungeSkeletonLoader
import com.mustakim.bokbok.ui.components.publicRoomsItems
import com.mustakim.bokbok.ui.components.RoundedParallaxCarousel
import com.mustakim.bokbok.ui.components.VoiceRoomCard
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.viewmodel.LoungeUiState
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.datastore.preferences.core.edit
import com.mustakim.bokbok.data.local.dataStore
import com.mustakim.bokbok.music.constants.LaunchCountKey
import com.mustakim.bokbok.music.constants.HasPressedStarKey
import com.mustakim.bokbok.music.constants.RemindAfterKey
import com.mustakim.bokbok.util.Updater
import com.mustakim.bokbok.BuildConfig
import com.mustakim.bokbok.music.ui.component.MarkdownText
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.music.ui.component.StarDialog
import com.mustakim.bokbok.ui.screens.lounge.CreateRoomDialog
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import androidx.compose.ui.graphics.Brush

@Composable
fun LoungeScreen(
    navController: NavHostController,
    userViewModel: UserViewModel,
    notificationCount: Int = 0,
    loungeViewModel: LoungeViewModel // Must be passed from NavGraph for proper scoping
) {
    val uiState by loungeViewModel.uiState.collectAsState()
    val currentUser by userViewModel.currentUser.collectAsState()
    val currentUserId = currentUser?.uid
    var showUpdateBanner by rememberSaveable { mutableStateOf(false) }
    var latestVersionName by rememberSaveable { mutableStateOf("") }
    var releaseNotes by rememberSaveable { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val cachedReleases by produceState(initialValue = emptyList<Updater.ReleaseInfo>()) {
        value = Updater.getCachedReleases(context)
    }
    var showStarDialog by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    // [NEW STATE VARIABLES]
    var showAlreadyInRoomDialog by remember { mutableStateOf(false) }
    var pendingJoinAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    // [NEW HELPER FUNCTION] - Memoized to prevent recreation
    val handleJoinRequest = remember {
        { action: () -> Unit ->
            if (RoomStateManager.currentRoom.value != null) {
                pendingJoinAction = action
                showAlreadyInRoomDialog = true
            } else {
                action()
            }
        }
    }

    // ✅ OPTIMIZED: Reduced skeleton timer and integrated StartupManager
    LaunchedEffect(Unit) {
        android.util.Log.d("LoungeScreen", "LaunchedEffect started, shouldShowSkeleton=${loungeViewModel.shouldShowSkeleton}")
        
        // Start minimum display timer (reduced from 2s to 1.5s for faster perceived startup)
        if (loungeViewModel.shouldShowSkeleton) {
            launch {
                android.util.Log.d("LoungeScreen", "Starting skeleton timer")
                delay(1000) // Reduced from 2000ms
                loungeViewModel.markMinTimeElapsed()
                android.util.Log.d("LoungeScreen", "Skeleton timer completed")
            }
        }
        
        // Load data (runs once in ViewModel)
        loungeViewModel.loadInitialData()

        // Recovery: Check for updates
        Updater.getLatestReleaseInfo().onSuccess { info ->
            val name = Updater.getLatestVersionName().getOrNull() ?: info.tagName
            if (!Updater.isSameVersion(name, BuildConfig.VERSION_NAME)) {
                latestVersionName = name
                releaseNotes = info.body
                showUpdateBanner = true
            }
        }

        // Watch for data to arrive and hide skeleton
        if (loungeViewModel.shouldShowSkeleton) {
            launch {
                loungeViewModel.uiState.collect { state ->
                    if (state.publicRooms.isNotEmpty() || state.myRooms.isNotEmpty()) {
                        loungeViewModel.hideSkeleton()
                        com.mustakim.bokbok.startup.StartupManager.markDataReady()
                    }
                }
            }
        } else {
            com.mustakim.bokbok.startup.StartupManager.markDataReady()
        }
    }

    // Star dialog recovery
    LaunchedEffect(Unit) {
        delay(4000)
        withContext(Dispatchers.IO) {
            val current = context.dataStore.data.first()[LaunchCountKey] ?: 0
            context.dataStore.edit { it[LaunchCountKey] = current + 1 }
        }
        val shouldShow = withContext(Dispatchers.IO) {
            val prefs = context.dataStore.data.first()
            val hasPressed = prefs[HasPressedStarKey] ?: false
            val remindAfter = prefs[RemindAfterKey] ?: 3
            !hasPressed && (prefs[LaunchCountKey] ?: 0) >= remindAfter
        }
        if (shouldShow) showStarDialog = true
    }

    MainScaffold(
        navController = navController,
        title = "BokBok",
        showBottomBar = true,
        useFlexibleTopBar = false,
        isStatic = true,
        notificationCount = notificationCount,
        userViewModel = userViewModel,
        containerColor = Color.Transparent,
        background = {
            // Programmatic M3E Mesh gradient background layer
            val color1 = MaterialTheme.colorScheme.primary
            val color2 = MaterialTheme.colorScheme.secondary

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
        // Show skeleton only on first load until data arrives and min time passes
        val showSkeleton = loungeViewModel.shouldShowSkeleton || !loungeViewModel.minSkeletonTimeElapsed

        // Skeleton with fade-out animation
        AnimatedVisibility(
            visible = showSkeleton,
            exit = fadeOut(animationSpec = tween(300))
        ) {
            LoungeSkeletonLoader(paddingValues = paddingValues)
        }

        // Content with fade-in animation
        AnimatedVisibility(
            visible = !showSkeleton,
            enter = fadeIn(animationSpec = tween(500, delayMillis = 100))
        ) {
            LoungeContent(
                paddingValues = paddingValues,
                uiState = uiState,
                currentUserId = currentUserId,
                onCreateRoom = { /* Handled by NavGraph FAB */ },
                // My Rooms tap → join call session only
                onRoomClick = remember {
                    { room: VoiceRoom ->
                        handleJoinRequest {
                            loungeViewModel.enterRoomFromMyRooms()
                            RoomStateManager.joinRoom(room, JoinMode.PERMANENT)
                        }
                    }
                },
                onRefresh = remember(loungeViewModel) { { loungeViewModel.refreshAllData() } },
                onRefreshPublicRooms = remember(loungeViewModel) { { loungeViewModel.refreshPublicRooms() } },
                // Public Rooms: tap / "Join call only"
                onJoinCallOnly = remember {
                    { room: VoiceRoom ->
                        handleJoinRequest {
                            loungeViewModel.joinRoomSessionOnly(room)
                            RoomStateManager.joinRoom(room, JoinMode.SESSION_ONLY)
                        }
                    }
                },
                // Public Rooms: long‑press / "Join permanently"
                onJoinPermanently = remember(loungeViewModel) {
                    { room: VoiceRoom ->
                        handleJoinRequest {
                            loungeViewModel.joinRoomPermanently(room)
                            RoomStateManager.joinRoom(room, JoinMode.PERMANENT)
                        }
                    }
                },
                onDeleteRoom = remember(loungeViewModel) { { room: VoiceRoom -> loungeViewModel.deleteRoomAsHost(room) } },
                onLeaveRoom = remember(loungeViewModel) { { room: VoiceRoom -> loungeViewModel.leaveRoomPermanently(room) } }
            )
        }

        if (showAlreadyInRoomDialog) {
            AlertDialog(
                onDismissRequest = {
                    showAlreadyInRoomDialog = false
                    pendingJoinAction = null
                },
                title = { Text("Already in a room") },
                text = { Text("You are already in a room. Do you want to leave it and join this one?") },
                confirmButton = {
                    TextButton(onClick = {
                        RoomStateManager.leaveRoom()
                        pendingJoinAction?.invoke()
                        showAlreadyInRoomDialog = false
                        pendingJoinAction = null
                    }) {
                        Text("Join")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showAlreadyInRoomDialog = false
                        pendingJoinAction = null
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showStarDialog) {
            StarDialog(
                onDismissRequest = { showStarDialog = false },
                onStar = {
                    coroutineScope.launch {
                        context.dataStore.edit { prefs ->
                            prefs[HasPressedStarKey] = true
                        }
                    }
                    showStarDialog = false
                },
                onLater = {
                    coroutineScope.launch {
                        val current = withContext(Dispatchers.IO) { 
                            context.dataStore.data.first()[LaunchCountKey] ?: 0 
                        }
                        context.dataStore.edit { prefs ->
                            prefs[RemindAfterKey] = current + 10
                        }
                        showStarDialog = false
                    }
                }
            )
        }

        if (showUpdateBanner && latestVersionName.isNotBlank()) {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showUpdateBanner = false }) {
                ElevatedCard(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.NewReleases,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = "Update available",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.weight(1f))
                            BokBokIconButton(onClick = { showUpdateBanner = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss")
                            }
                        }
                        OutlinedButton(
                            onClick = {},
                            shape = ButtonDefaults.shape,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(latestVersionName, style = MaterialTheme.typography.labelMedium)
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        val notes = releaseNotes
                        if (notes != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f, fill = false)
                                    .heightIn(max = 260.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                MarkdownText(
                                    markdown = notes,
                                    modifier = Modifier.fillMaxWidth().padding(end = 4.dp)
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                        FilledTonalButton(
                            onClick = {
                                try { com.mustakim.bokbok.util.Updater.getLatestDownloadUrl(cachedReleases)?.let { uriHandler.openUri(it) } } catch (_: Exception) {}
                                showUpdateBanner = false
                            },
                            shape = ButtonDefaults.shape,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Download update")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun LoungeContent(
    paddingValues: PaddingValues,
    uiState: LoungeUiState,
    currentUserId: String?,
    onCreateRoom: () -> Unit,
    isMinimized: Boolean = RoomStateManager.isMinimized.value,
    onRoomClick: (VoiceRoom) -> Unit,
    onRefresh: () -> Unit,
    onRefreshPublicRooms: () -> Unit,
    onJoinCallOnly: (VoiceRoom) -> Unit,
    onJoinPermanently: (VoiceRoom) -> Unit,
    onDeleteRoom: (VoiceRoom) -> Unit,
    onLeaveRoom: (VoiceRoom) -> Unit
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = onRefresh
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .pullRefresh(pullRefreshState)
    ) {
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            item(key = "top_spacer", contentType = "spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }

            friendsSection(
                friends = uiState.friends,
                myRooms = uiState.myRooms,
                publicRooms = uiState.publicRooms,
                onJoinCallOnly = onJoinCallOnly,
                onJoinPermanently = onJoinPermanently,
                animationDelay = 0 // Immediate for first section
            )

            myRoomsSection(
                rooms = uiState.myRooms,
                currentUserId = currentUserId,
                onRoomClick = onRoomClick,
                onDeleteRoom = onDeleteRoom,
                onLeaveRoom = onLeaveRoom,
                onCreateRoom = onCreateRoom,
                animationDelay = 50 // Reduced from 100
            )

            publicRoomsItems(
                rooms = uiState.publicRooms.take(20), // LIMIT count for better performance
                totalRooms = uiState.totalActiveRooms,
                totalParticipants = uiState.totalOnlineUsers,
                isRefreshing = uiState.isRefreshingPublicRooms,
                onRefresh = onRefreshPublicRooms,
                onLoadMore = { /* TODO */ },
                onJoinCallOnly = onJoinCallOnly,
                onJoinPermanently = onJoinPermanently
            )
        }

        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )

    }
}

private fun LazyListScope.friendsSection(
    friends: List<FriendStatus>,
    myRooms: List<VoiceRoom>,
    publicRooms: List<VoiceRoom>,
    onJoinCallOnly: (VoiceRoom) -> Unit,
    onJoinPermanently: (VoiceRoom) -> Unit,
    animationDelay: Int = 0
) {
    if (friends.isEmpty()) return

    item(key = "friends_section", contentType = "friends") {
        var selectedRoom by remember { mutableStateOf<VoiceRoom?>(null) }
        var showJoinDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            val currentOnJoinCallOnly by rememberUpdatedState(onJoinCallOnly)
            
            FriendsStatusSection(
                friends = friends,
                onFriendClick = remember(myRooms, publicRooms) {
                    { friend ->
                        // Tap: join their current room as call-only
                        val roomId = friend.currentRoomId ?: return@remember

                        val room = myRooms.firstOrNull { it.id == roomId }
                            ?: publicRooms.firstOrNull { it.id == roomId }
                            ?: return@remember

                        currentOnJoinCallOnly(room)
                    }
                },
                onFriendLongClick = remember(myRooms, publicRooms) {
                    { friend ->
                        // Long press: show dialog with same options as PublicRoomsSection
                        val roomId = friend.currentRoomId ?: return@remember

                        val room = myRooms.firstOrNull { it.id == roomId }
                            ?: publicRooms.firstOrNull { it.id == roomId }
                            ?: return@remember

                        selectedRoom = room
                        showJoinDialog = true
                    }
                }
            )

            if (showJoinDialog && selectedRoom != null) {
                AlertDialog(
                    onDismissRequest = { showJoinDialog = false },
                    title = { Text("Join room") },
                    text = { Text("How do you want to join this room?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                selectedRoom?.let { onJoinPermanently(it) }
                                showJoinDialog = false
                            }
                        ) {
                            Text("Join permanently")
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                selectedRoom?.let { onJoinCallOnly(it) }
                                showJoinDialog = false
                            }
                        ) {
                            Text("Join call only")
                        }
                    }
                )
            }
        }
    }
}

private fun LazyListScope.myRoomsSection(
    rooms: List<VoiceRoom>,
    currentUserId: String?,
    onRoomClick: (VoiceRoom) -> Unit,
    onDeleteRoom: (VoiceRoom) -> Unit,
    onLeaveRoom: (VoiceRoom) -> Unit,
    onCreateRoom: () -> Unit,
    animationDelay: Int = 0
) {
    item(key = "my_rooms_header", contentType = "header") {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "My Rooms",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (rooms.isEmpty()) "Create your first room" else "${rooms.size} active rooms",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    item(key = "my_rooms_carousel", contentType = "carousel") {
        var selectedRoom by remember { mutableStateOf<VoiceRoom?>(null) }
        var showDeleteDialog by remember { mutableStateOf(false) }
        var showLeaveDialog by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
            ) {
                if (rooms.isEmpty()) {
                    EmptyRoomsCard(onCreateRoom = onCreateRoom)
                } else {
                    RoundedParallaxCarousel(
                        items = rooms,
                        modifier = Modifier.fillMaxSize()
                    ) { room, _ ->
                        VoiceRoomCard(
                            room = room,
                            onClick = { onRoomClick(room) },
                            onLongClick = {
                                selectedRoom = room
                                if (currentUserId != null && room.hostId == currentUserId) {
                                    showDeleteDialog = true
                                } else {
                                    showLeaveDialog = true
                                }
                            }
                        )
                    }
                }
            }
        }

        if (showDeleteDialog && selectedRoom != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete room") },
                text = { Text("Are you sure you want to delete this room for everyone?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedRoom?.let { onDeleteRoom(it) }
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showLeaveDialog && selectedRoom != null) {
            AlertDialog(
                onDismissRequest = { showLeaveDialog = false },
                title = { Text("Leave room") },
                text = { Text("Leave this room and remove it from your My Rooms list?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            selectedRoom?.let { onLeaveRoom(it) }
                            showLeaveDialog = false
                        }
                    ) {
                        Text("Leave")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showLeaveDialog = false }
                    ) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// DELETED publicRoomsSection wrapper as we are using publicRoomsItems directly

@Composable
private fun EmptyRoomsCard(onCreateRoom: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .height(200.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape
                    )
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                "Start a Room",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(Modifier.height(4.dp))
            
            Text(
                "Create your own space to hang out",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
