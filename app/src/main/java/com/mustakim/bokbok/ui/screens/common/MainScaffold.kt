package com.mustakim.bokbok.ui.screens.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.state.JoinMode
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.components.MinimizedRoomBar
import com.mustakim.bokbok.ui.navigation.NavRoutes
import com.mustakim.bokbok.ui.screens.room.VoiceRoomScreen
import com.mustakim.bokbok.viewmodel.UserViewModel
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


    val roomRepository = remember { RoomRepository() }

    val roomState by remember {
        derivedStateOf {
            Triple(
                RoomStateManager.currentRoom.value,
                RoomStateManager.isMinimized.value,
                RoomStateManager.isMuted.value
            )
        }
    }
    val (currentRoom, isMinimized, isMuted) = roomState

    // Just compute it each recomposition, no remember
    val showBars = currentRoom == null || isMinimized


    val onMenuClick: () -> Unit = remember(scope, drawerState) {
        {
            scope.launch { drawerState.open() }
            Unit
        }
    }

    val onMenuItemClick: (String) -> Unit = remember(navController, scope, drawerState) {
        { route: String ->
            scope.launch {
                drawerState.close()
                when (route) {
                    "logout" -> { /* Handle logout */ }
                    else -> navController.navigate(route) {
                        popUpTo(NavRoutes.Lounge.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            Unit
        }
    }

    val onProfileClick: () -> Unit = remember(navController, scope, drawerState) {
        {
            scope.launch {
                drawerState.close()
                navController.navigate("profile") {
                    popUpTo(NavRoutes.Lounge.route) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            Unit
        }
    }

    val onNotificationsClick: () -> Unit = remember(navController) {
        {
            navController.navigate("notifications") {
                popUpTo(NavRoutes.Lounge.route) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val onNavigate = remember(navController, currentRoute) {
        { route: String ->
            if (currentRoute != route) {
                navController.navigate(route) {
                    popUpTo(NavRoutes.Lounge.route) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Layer 1: Main app with Scaffold
        AppNavigationDrawer(
            drawerState = drawerState,
            currentRoute = currentRoute,
            onMenuItemClick = onMenuItemClick,
            onProfileClick = onProfileClick,
            userViewModel = userViewModel
        ) {
            Scaffold(
                topBar = {
                    if (showBars) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200))
                        ) {
                            TopBar(
                                title = title,
                                notificationCount = notificationCount,
                                onMenuClick = onMenuClick,
                                onNotificationsClick = onNotificationsClick,
                                onProfileClick = onProfileClick,
                                userViewModel = userViewModel
                            )
                        }
                    }
                },
                bottomBar = {
                    if (showBottomBar && showBars) {
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(200))
                        ) {
                            BottomNavigationBar(
                                currentRoute = currentRoute,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    content(paddingValues)


                    // Layer 2: Minimized bar anchored using the real bottom inset
                    if (currentRoom != null && isMinimized) {
                        AnimatedVisibility(
                            visible = true,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(
                                    start = 4.dp,
                                    end = 4.dp,
                                    bottom = paddingValues.calculateBottomPadding() + 8.dp
                                ),
                            enter = slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeIn(),
                            exit = slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = tween(300)
                            ) + fadeOut()
                        ) {
                            MinimizedRoomBar(
                                roomName = currentRoom.name,
                                roomImageUrl = currentRoom.imageUrl,
                                isMuted = isMuted,
                                onExpand = { RoomStateManager.expandRoom() },
                                onToggleMute = { RoomStateManager.toggleMute() },
                                onLeaveRoom = {
                                    val modeSnapshot = RoomStateManager.joinMode.value
                                    val roomSnapshot = currentRoom

                                    if (modeSnapshot == JoinMode.SESSION_ONLY) {
                                        scope.launch {
                                            roomRepository.leaveRoom(roomSnapshot.id)
                                        }
                                    }

                                    RoomStateManager.leaveRoom()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // ✅ FIX: Layer 3 - Full room screen OUTSIDE the Scaffold
        // This prevents it from being affected by Scaffold padding
        if (currentRoom != null && !isMinimized) {
            AnimatedVisibility(
                visible = true,
                modifier = Modifier.fillMaxSize(),
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(
                        durationMillis = 260,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeIn(
                    animationSpec = tween(
                        durationMillis = 180
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(
                        durationMillis = 260,
                        easing = FastOutSlowInEasing
                    )
                ) + fadeOut(
                    animationSpec = tween(
                        durationMillis = 180
                    )
                )
            ) {
                VoiceRoomScreen(
                    roomId = currentRoom.id,
                    onMinimize = { isMuted ->
                        RoomStateManager.minimizeRoom(isMuted)
                    },
                    onLeaveRoom = {
                        val roomSnapshot = currentRoom  // capture local copy
                        val modeSnapshot = RoomStateManager.joinMode.value

                        if (modeSnapshot == JoinMode.SESSION_ONLY) {
                            scope.launch {
                                roomRepository.leaveRoom(roomSnapshot.id)
                            }
                        }
                        RoomStateManager.leaveRoom()
                    }
                )
            }
        }
    }
}