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
import com.mustakim.bokbok.ui.components.PublicRoomsSection
import com.mustakim.bokbok.ui.components.RoundedParallaxCarousel
import com.mustakim.bokbok.ui.components.VoiceRoomCard
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.viewmodel.LoungeUiState
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    var showCreateRoomDialog by remember { mutableStateOf(false) }

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
                delay(1500) // Reduced from 2000ms
                loungeViewModel.markMinTimeElapsed()
                android.util.Log.d("LoungeScreen", "Skeleton timer completed")
            }
        }
        
        // Load data (runs once in ViewModel)
        loungeViewModel.loadInitialData()
        
        // Watch for data to arrive and hide skeleton
        if (loungeViewModel.shouldShowSkeleton) {
            launch {
                loungeViewModel.uiState.collect { state ->
                    android.util.Log.d("LoungeScreen", "State update: publicRooms=${state.publicRooms.size}, myRooms=${state.myRooms.size}")
                    
                    if (state.publicRooms.isNotEmpty() || state.myRooms.isNotEmpty()) {
                        android.util.Log.d("LoungeScreen", "Data loaded! Hiding skeleton")
                        loungeViewModel.hideSkeleton()
                        
                        // ✅ OPTIMIZED: Trigger Stage 2 initialization
                        // This starts deferred tasks (FCM, presence, etc.) after first frame
                        com.mustakim.bokbok.startup.StartupManager.markDataReady()
                    }
                }
            }
        } else {
            // Already loaded - still mark data ready in case it wasn't
            com.mustakim.bokbok.startup.StartupManager.markDataReady()
        }
    }

    MainScaffold(
        navController = navController,
        title = "BokBok",
        notificationCount = notificationCount,
        userViewModel = userViewModel
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
                onCreateRoom = { showCreateRoomDialog = true },
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

        if (showCreateRoomDialog) {
            CreateRoomDialog(
                onDismiss = { showCreateRoomDialog = false },
                onConfirm = { roomName, description, maxParticipants, category, isPublic, imageUri ->
                    loungeViewModel.createRoom(
                        roomName,
                        description,
                        maxParticipants,
                        category,
                        isPublic,
                        imageUri
                    )
                    showCreateRoomDialog = false
                }
            )
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
                animationDelay = 0
            )

            myRoomsSection(
                rooms = uiState.myRooms,
                currentUserId = currentUserId,
                onRoomClick = onRoomClick,
                onDeleteRoom = onDeleteRoom,
                onLeaveRoom = onLeaveRoom,
                onCreateRoom = onCreateRoom,
                animationDelay = 100
            )

            publicRoomsSection(
                rooms = uiState.publicRooms,
                totalRooms = uiState.totalActiveRooms,
                totalParticipants = uiState.totalOnlineUsers,
                isRefreshing = uiState.isRefreshingPublicRooms,
                onRefresh = onRefreshPublicRooms,
                onJoinCallOnly = onJoinCallOnly,
                onJoinPermanently = onJoinPermanently,
                animationDelay = 300
            )
        }

        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )

        FloatingActionButton(
            onClick = onCreateRoom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 32.dp,
                    bottom = if (isMinimized) 120.dp else 36.dp
                ),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(Icons.Default.Add, "Create Room")
        }
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
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(500, delayMillis = animationDelay)) + slideInHorizontally(
                animationSpec = tween(500, delayMillis = animationDelay),
                initialOffsetX = { it / 2 } // Slide from right
            )
        ) {
            var selectedRoom by remember { mutableStateOf<VoiceRoom?>(null) }
            var showJoinDialog by remember { mutableStateOf(false) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                FriendsStatusSection(
                    friends = friends,
                    onFriendClick = { friend ->
                        // Tap: join their current room as call-only
                        val roomId = friend.currentRoomId ?: return@FriendsStatusSection

                        val room = myRooms.firstOrNull { it.id == roomId }
                            ?: publicRooms.firstOrNull { it.id == roomId }
                            ?: return@FriendsStatusSection

                        onJoinCallOnly(room)
                    },
                    onFriendLongClick = { friend ->
                        // Long press: show dialog with same options as PublicRoomsSection
                        val roomId = friend.currentRoomId ?: return@FriendsStatusSection

                        val room = myRooms.firstOrNull { it.id == roomId }
                            ?: publicRooms.firstOrNull { it.id == roomId }
                            ?: return@FriendsStatusSection

                        selectedRoom = room
                        showJoinDialog = true
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
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(500, delayMillis = animationDelay)) + slideInHorizontally(
                animationSpec = tween(500, delayMillis = animationDelay),
                initialOffsetX = { it / 2 } // Slide from right
            )
        ) {
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
    }

    item(key = "my_rooms_carousel", contentType = "carousel") {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(500, delayMillis = animationDelay + 50)) + slideInHorizontally(
                animationSpec = tween(500, delayMillis = animationDelay + 50),
                initialOffsetX = { it / 2 } // Slide from right
            )
        ) {
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
}

private fun LazyListScope.publicRoomsSection(
    rooms: List<VoiceRoom>,
    totalRooms: Int,
    totalParticipants: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onJoinCallOnly: (VoiceRoom) -> Unit,
    onJoinPermanently: (VoiceRoom) -> Unit,
    animationDelay: Int = 0
) {
    if (rooms.isEmpty()) return

    item(key = "public_rooms_section", contentType = "public_rooms") {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(500, delayMillis = animationDelay)) + slideInVertically(
                animationSpec = tween(500, delayMillis = animationDelay),
                initialOffsetY = { it / 2 } // Slide from bottom
            )
        ) {
            PublicRoomsSection(
                rooms = rooms.take(10),
                totalRooms = totalRooms,
                totalParticipants = totalParticipants,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                onJoinCallOnly = onJoinCallOnly,
                onJoinPermanently = onJoinPermanently
            )
        }
    }
}

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
