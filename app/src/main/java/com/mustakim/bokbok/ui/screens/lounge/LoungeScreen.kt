package com.mustakim.bokbok.ui.screens.lounge

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.VoiceRoom
import com.mustakim.bokbok.ui.components.FriendsStatusSection
import com.mustakim.bokbok.ui.components.PublicRoomsSection
import com.mustakim.bokbok.ui.components.RoundedParallaxCarousel
import com.mustakim.bokbok.ui.components.VoiceRoomCard
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.viewmodel.LoungeUiState
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel


@Composable
fun LoungeScreen(
    navController: NavHostController,
    userViewModel: UserViewModel,
    loungeViewModel: LoungeViewModel = viewModel()
) {
    val uiState by loungeViewModel.uiState.collectAsState()
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
            onCreateRoom = { showCreateRoomDialog = true },
            onFriendClick = remember { { _: FriendStatus -> } },
            // ✅ FIX: Navigate to room when clicked
            onRoomClick = remember(navController) {
                { room: VoiceRoom ->
                    navController.navigate("voice_room/${room.id}")
                }
            },
            onRefresh = remember(loungeViewModel) { { loungeViewModel.refreshAllData() } },
            onRefreshPublicRooms = remember(loungeViewModel) { { loungeViewModel.refreshPublicRooms() } },
            // ✅ FIX: Navigate to room when joined
            onJoinRoom = remember(navController) {
                { roomId: String ->
                    navController.navigate("voice_room/$roomId")
                }
            }
        )

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
    onCreateRoom: () -> Unit,
    onFriendClick: (FriendStatus) -> Unit,
    onRoomClick: (VoiceRoom) -> Unit,
    onRefresh: () -> Unit,  // ✅ Pull-to-refresh callback
    onRefreshPublicRooms: () -> Unit,
    onJoinRoom: (String) -> Unit
) {
    // ✅ Pull-to-refresh state
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = onRefresh
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .pullRefresh(pullRefreshState)  // ✅ Enable pull-to-refresh
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

            // Friends section
            friendsSection(friends = uiState.friends, onFriendClick = onFriendClick)

            // My Rooms section
            myRoomsSection(rooms = uiState.myRooms, onRoomClick = onRoomClick)

            // Public Rooms section
            publicRoomsSection(
                rooms = uiState.publicRooms,
                totalRooms = uiState.totalActiveRooms,
                totalParticipants = uiState.totalOnlineUsers,
                isRefreshing = uiState.isRefreshingPublicRooms,
                onRefresh = onRefreshPublicRooms,
                onJoinRoom = onJoinRoom
            )
        }

        // ✅ Pull-to-refresh indicator
        PullRefreshIndicator(
            refreshing = uiState.isRefreshing,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            backgroundColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary
        )

        // FAB
        FloatingActionButton(
            onClick = onCreateRoom,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 32.dp, bottom = 36.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, "Create Room")
        }
    }
}

// Keep your existing extension functions...
private fun LazyListScope.friendsSection(
    friends: List<FriendStatus>,
    onFriendClick: (FriendStatus) -> Unit
) {
    if (friends.isEmpty()) return

    item(key = "friends_section", contentType = "friends") {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
        ) {
            FriendsStatusSection(friends = friends, onFriendClick = onFriendClick)
        }
    }

    item(key = "friends_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun LazyListScope.myRoomsSection(
    rooms: List<VoiceRoom>,
    onRoomClick: (VoiceRoom) -> Unit
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
        Box(
            modifier = Modifier
                .height(420.dp)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }
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
                        onImageSelected = null
                    )
                }
            }
        }
    }

    item(key = "my_rooms_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun LazyListScope.publicRoomsSection(
    rooms: List<VoiceRoom>,
    totalRooms: Int,
    totalParticipants: Int,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onJoinRoom: (String) -> Unit
) {
    if (rooms.isEmpty()) return

    item(key = "public_rooms_section", contentType = "public_rooms") {
        PublicRoomsSection(
            rooms = rooms.take(10),
            totalRooms = totalRooms,
            totalParticipants = totalParticipants,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            onRoomClick = { room -> onJoinRoom(room.id) }
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
