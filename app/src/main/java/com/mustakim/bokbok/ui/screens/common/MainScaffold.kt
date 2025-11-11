package com.mustakim.bokbok.ui.screens.common

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.components.MinimizedRoomBar
import kotlinx.coroutines.launch

@Composable
fun MainScaffold(
    navController: NavHostController,
    title: String,
    showBottomBar: Boolean = true,
    notificationCount: Int = 0,
    userViewModel: UserViewModel,
    content: @Composable (PaddingValues) -> Unit
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // ✅ Watch for minimized room
    val minimizedRoom by RoomStateManager.minimizedRoom
    val isMuted by RoomStateManager.isMuted

    val onMenuClick = remember(scope) {
        {
            scope.launch { drawerState.open() }
            Unit
        }
    }

    val onMenuItemClick = remember(navController, scope) {
        { route: String ->
            scope.launch {
                drawerState.close()
            }
            when (route) {
                "logout" -> {
                    // Handle logout
                }
                else -> navController.navigate(route)
            }
            Unit
        }
    }

    val onProfileClick = remember(navController, scope) {
        {
            scope.launch { drawerState.close() }
            navController.navigate("profile")
            Unit
        }
    }

    val onNotificationsClick = remember(navController) {
        {
            navController.navigate("notifications")
        }
    }

    AppNavigationDrawer(
        drawerState = drawerState,
        currentRoute = currentRoute,
        onMenuItemClick = onMenuItemClick,
        onProfileClick = onProfileClick,
        userViewModel = userViewModel
    ) {
        Scaffold(
            topBar = {
                TopBar(
                    title = title,
                    notificationCount = notificationCount,
                    onMenuClick = onMenuClick,
                    onNotificationsClick = onNotificationsClick,
                    onProfileClick = onProfileClick,
                    userViewModel = userViewModel
                )
            },
            bottomBar = {
                if (showBottomBar) {
                    val onNavigate = remember(navController) {
                        { route: String ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }

                    BottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = onNavigate
                    )
                }
            }
        ) { paddingValues ->
            // ✅ FIX: Wrap content in Box for overlay
            Box(modifier = Modifier.fillMaxSize()) {
                // Main content
                content(paddingValues)

                // ✅ Floating minimized room bar
                minimizedRoom?.let { room ->
                    MinimizedRoomBar(
                        roomName = room.name,
                        roomImageUrl = room.imageUrl,
                        isMuted = isMuted,
                        onExpand = {
                            RoomStateManager.expandRoom()
                            navController.navigate("voice_room/${room.id}")
                        },
                        onToggleMute = {
                            RoomStateManager.toggleMute()
                        },
                        onLeaveRoom = {
                            RoomStateManager.leaveRoom()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 80.dp)  // Above bottom nav
                    )
                }
            }
        }
    }
}
