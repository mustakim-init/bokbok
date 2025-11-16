package com.mustakim.bokbok.ui.screens.lounge

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.VoiceRoom
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.components.FriendsStatusSection
import com.mustakim.bokbok.ui.components.PublicRoomsSection
import com.mustakim.bokbok.ui.components.RoundedParallaxCarousel
import com.mustakim.bokbok.ui.components.VoiceRoomCard
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.viewmodel.LoungeUiState
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.state.JoinMode

@Composable
fun LoungeScreen(
    navController: NavHostController,
    userViewModel: UserViewModel,
    loungeViewModel: LoungeViewModel = viewModel()
) {
    val uiState by loungeViewModel.uiState.collectAsState()
    val currentUser by userViewModel.currentUser.collectAsState()
    val currentUserId = currentUser?.uid
    var showCreateRoomDialog by remember { mutableStateOf(false) }

    MainScaffold(
        navController = navController,
        title = "BokBok Lounge",
        notificationCount = 0,
        userViewModel = userViewModel
    ) { paddingValues ->
        LoungeContent(
            paddingValues = paddingValues,
            uiState = uiState,
            currentUserId = currentUserId,
            onCreateRoom = { showCreateRoomDialog = true },
            onFriendClick = remember { { _: FriendStatus -> } },
            // My Rooms tap → join call session only
            onRoomClick = remember {
                { room: VoiceRoom ->
                    // 1) Mark user as currently in this room's call
                    loungeViewModel.enterRoomFromMyRooms(room)
                    // 2) Join as PERMANENT so leave does NOT drop membership
                    RoomStateManager.joinRoom(room, JoinMode.PERMANENT)
                }
            },
            onRefresh = remember(loungeViewModel) { { loungeViewModel.refreshAllData() } },
            onRefreshPublicRooms = remember(loungeViewModel) { { loungeViewModel.refreshPublicRooms() } },
            // Public Rooms: tap / "Join call only"
            onJoinCallOnly = remember {
                { room: VoiceRoom ->
                    loungeViewModel.joinRoomSessionOnly(room)
                    RoomStateManager.joinRoom(room, JoinMode.SESSION_ONLY)
                }
            },
            // Public Rooms: long‑press / "Join permanently"
            onJoinPermanently = remember(loungeViewModel) {
                { room: VoiceRoom ->
                    loungeViewModel.joinRoomPermanently(room)
                    RoomStateManager.joinRoom(room, JoinMode.PERMANENT)
                }
            },
            onDeleteRoom = remember(loungeViewModel) { { room: VoiceRoom -> loungeViewModel.deleteRoomAsHost(room) } },
            onLeaveRoom = remember(loungeViewModel) { { room: VoiceRoom -> loungeViewModel.leaveRoomPermanently(room) } }
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun LoungeContent(
    paddingValues: PaddingValues,
    uiState: LoungeUiState,
    currentUserId: String?,
    onCreateRoom: () -> Unit,
    isMinimized: Boolean = RoomStateManager.isMinimized.value,
    onFriendClick: (FriendStatus) -> Unit,
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

            friendsSection(friends = uiState.friends, onFriendClick = onFriendClick)

            myRoomsSection(
                rooms = uiState.myRooms,
                currentUserId = currentUserId,
                onRoomClick = onRoomClick,
                onDeleteRoom = onDeleteRoom,
                onLeaveRoom = onLeaveRoom
            )

            publicRoomsSection(
                rooms = uiState.publicRooms,
                totalRooms = uiState.totalActiveRooms,
                totalParticipants = uiState.totalOnlineUsers,
                isRefreshing = uiState.isRefreshingPublicRooms,
                onRefresh = onRefreshPublicRooms,
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

        FloatingActionButton(
            onClick = onCreateRoom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 32.dp,
                    bottom = if (isMinimized) 120.dp else 36.dp
                ),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, "Create Room")
        }
    }
}

private fun LazyListScope.friendsSection(
    friends: List<FriendStatus>,
    onFriendClick: (FriendStatus) -> Unit
) {
    if (friends.isEmpty()) return

    item(key = "friends_section", contentType = "friends") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            FriendsStatusSection(
                friends = friends,
                onFriendClick = onFriendClick
            )
        }
    }
}

private fun LazyListScope.myRoomsSection(
    rooms: List<VoiceRoom>,
    currentUserId: String?,
    onRoomClick: (VoiceRoom) -> Unit,
    onDeleteRoom: (VoiceRoom) -> Unit,
    onLeaveRoom: (VoiceRoom) -> Unit
) {
    item(key = "my_rooms_header", contentType = "header") {
        Text(
            text = "My Rooms",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
        )
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
                    EmptyRoomsCard()
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

private fun LazyListScope.publicRoomsSection(
    rooms: List<VoiceRoom>,
    totalRooms: Int,
    totalParticipants: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onJoinCallOnly: (VoiceRoom) -> Unit,
    onJoinPermanently: (VoiceRoom) -> Unit
) {
    if (rooms.isEmpty()) return

    item(key = "public_rooms_section", contentType = "public_rooms") {
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

@Composable
private fun EmptyRoomsCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎤", style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                "You're not in any rooms",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Create or join a room below",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
