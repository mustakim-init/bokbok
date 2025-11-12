package com.mustakim.bokbok.ui.screens.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.components.MinimizedRoomBar
import com.mustakim.bokbok.ui.screens.room.VoiceRoomScreen
import com.mustakim.bokbok.viewmodel.UserViewModel
import kotlinx.coroutines.launch
import com.mustakim.bokbok.ui.navigation.NavRoutes



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

    // Room state
    val currentRoom by RoomStateManager.currentRoom
    val isMinimized by RoomStateManager.isMinimized
    val isMuted by RoomStateManager.isMuted

    // Callbacks
    val onMenuClick: () -> Unit = remember(scope) {
        { scope.launch { drawerState.open() } }
    }

    val onMenuItemClick: (String) -> Unit = remember(navController, scope) {
        { route ->
            scope.launch { drawerState.close() }
            when (route) {
                "logout" -> { /* Handle logout */ }
                else -> navController.navigate(route)
            }
        }
    }

    val onProfileClick: () -> Unit = remember(navController, scope) {
        {
            scope.launch { drawerState.close() }
            navController.navigate("profile")
        }
    }

    val onNotificationsClick: () -> Unit = remember(navController) {
        { navController.navigate("notifications") }
    }

    // ✅ OUTER BOX - Contains everything
    Box(modifier = Modifier.fillMaxSize()) {
        // ✅ LAYER 1: Main app (drawer + scaffold)
        AppNavigationDrawer(
            drawerState = drawerState,
            currentRoute = currentRoute,
            onMenuItemClick = onMenuItemClick,
            onProfileClick = onProfileClick,
            userViewModel = userViewModel
        ) {
            Scaffold(
                topBar = {
                    // ✅ ALWAYS show TopBar
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
                    // ✅ ALWAYS show BottomBar
                    if (showBottomBar) {
                        val onNavigate = remember(navController) {
                            { route: String ->
                                // Only navigate if we're not already on that route
                                if (currentRoute != route) {
                                    navController.navigate(route) {
                                        // Pop up to the start destination (Lounge)
                                        popUpTo(NavRoutes.Lounge.route) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
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
                Box(modifier = Modifier.fillMaxSize()) {
                    // Main content
                    content(paddingValues)

                    // ✅ LAYER 2: Minimized bar (inside scaffold, above content)
                    AnimatedVisibility(
                        visible = currentRoom != null && isMinimized,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(),
                        enter = slideInVertically(
                            initialOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeIn(),
                        exit = slideOutVertically(
                            targetOffsetY = { it },
                            animationSpec = tween(300)
                        ) + fadeOut()
                    ) {
                        currentRoom?.let { room ->
                            MinimizedRoomBar(
                                roomName = room.name,
                                roomImageUrl = room.imageUrl,
                                isMuted = isMuted,
                                onExpand = {
                                    RoomStateManager.expandRoom()
                                },
                                onToggleMute = {
                                    RoomStateManager.toggleMute()
                                },
                                onLeaveRoom = {
                                    RoomStateManager.leaveRoom()
                                },
                                modifier = Modifier.padding(bottom = if (showBottomBar) 80.dp else 16.dp)
                            )
                        }
                    }
                }
            }
        }

        // ✅ LAYER 3: Full room screen with smooth state transitions
        currentRoom?.let { room ->
            // ✅ KEY FIX: Use opposite initial state from isMinimized
            val roomVisibilityState = remember(room.id) {
                MutableTransitionState(isMinimized)  // Start with TRUE when room joins (isMinimized=false)
            }

            // ✅ Update target when isMinimized changes
            LaunchedEffect(isMinimized) {
                roomVisibilityState.targetState = !isMinimized
            }

            AnimatedVisibility(
                visibleState = roomVisibilityState,
                modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + fadeIn(tween(300)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(450, easing = FastOutSlowInEasing)
                ) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(450)
                ) + fadeOut(tween(300))
            ) {
                VoiceRoomScreen(
                    roomId = room.id,
                    onMinimize = { _, muted ->
                        RoomStateManager.minimizeRoom(room, muted)
                    },
                    onLeaveRoom = {
                        RoomStateManager.leaveRoom()
                    }
                )
            }
        }
    }
}