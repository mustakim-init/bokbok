package com.mustakim.bokbok.ui.screens.common

import android.content.Intent
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.navigation.NavRoutes
import com.mustakim.bokbok.ui.screens.room.VoiceRoomScreen
import com.mustakim.bokbok.viewmodel.AuthViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.viewmodel.VoiceRoomViewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import com.mustakim.bokbok.ui.shared.FloatingNavigationToolbar
import com.mustakim.bokbok.ui.shared.NavigationItem
import com.mustakim.bokbok.ui.shared.NavIcon
import com.mustakim.bokbok.ui.screens.Screens
import com.mustakim.bokbok.music.constants.FloatingToolbarBottomPadding
import com.mustakim.bokbok.music.constants.FloatingToolbarHeight
import com.mustakim.bokbok.music.constants.FloatingToolbarHorizontalPadding
import com.mustakim.bokbok.music.constants.PureBlackKey
import com.mustakim.bokbok.data.local.rememberPreference
import androidx.compose.foundation.layout.height


@Composable
fun MainScaffold(
    navController: NavHostController,
    title: String,
    showBottomBar: Boolean = true,
    showTopBar: Boolean = true,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.material3.MaterialTheme.colorScheme.background,
    notificationCount: Int = 0,
    userViewModel: UserViewModel,
    useFlexibleTopBar: Boolean = true,
    isStatic: Boolean = false,
    showProfile: Boolean = true,
    showNotifications: Boolean = true,
    background: @Composable () -> Unit = {},
    customTopBar: (@Composable (androidx.compose.material3.TopAppBarScrollBehavior) -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val scrollBehavior = if (isStatic) {
        androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val authViewModel: AuthViewModel = hiltViewModel()
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

    // Just compute it each recomposition, no remember
    val showBars = true


    fun handleLogout() {
        // 1) Sign out (Firebase + presence)
        authViewModel.signOut()

        // 2) Clear cached user in UserViewModel
        //userViewModel.setCurrentUser(null)

        // 3) Navigate to Login and clear the main graph from back stack
        navController.navigate(NavRoutes.Login.route) {
            // Pop everything in the main app flow (Lounge + others)
            popUpTo(NavRoutes.Lounge.route) {
                inclusive = true
            }
            launchSingleTop = true
            restoreState = false
        }
    }

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
                    "about" -> {
                        val githubUrl = "https://github.com/mustakim-init/bokbok.git"
                        val intent = Intent(Intent.ACTION_VIEW, githubUrl.toUri())
                        context.startActivity(intent)
                    }
                    "logout" -> {
                        handleLogout()
                    }
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

    // 🚀 PERFORMANCE: Don't include currentRoute in remember key - we check it inside
    val onNavigate = remember(navController, context) {
        { route: String ->
            val current = navController.currentBackStackEntry?.destination?.route
            if (current != route) {
                if (route == "music") {
                    navController.navigate("home") {
                        popUpTo(NavRoutes.Lounge.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
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
    }

    Box(modifier = Modifier.fillMaxSize()) {
        background()
        // Layer 1: Main app with Scaffold
        AppNavigationDrawer(
            drawerState = drawerState,
            currentRoute = currentRoute,
            onMenuItemClick = onMenuItemClick,
            onProfileClick = onProfileClick,
            userViewModel = userViewModel
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                containerColor = containerColor,
                floatingActionButton = floatingActionButton,
                topBar = {
                    if (customTopBar != null) {
                        customTopBar(scrollBehavior)
                    } else if (showBars && showTopBar) {
                        TopBar(
                            title = title,
                            notificationCount = notificationCount,
                            onMenuClick = { scope.launch { drawerState.open() } },
                            onNotificationsClick = { navController.navigate("notifications") },
                            onProfileClick = { navController.navigate("profile") },
                            userViewModel = userViewModel,
                            scrollBehavior = scrollBehavior,
                            useFlexibleTopBar = useFlexibleTopBar,
                            isStatic = isStatic,
                            showProfile = showProfile,
                            showNotifications = showNotifications
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    content(innerPadding)
                }
            }
        } // Closes AppNavigationDrawer
    }
}