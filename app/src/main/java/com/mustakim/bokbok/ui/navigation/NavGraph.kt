package com.mustakim.bokbok.ui.navigation

import android.content.pm.PackageManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mustakim.bokbok.data.model.PermissionsList
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.screens.auth.GoogleSignupScreen
import com.mustakim.bokbok.ui.screens.auth.LoginScreen
import com.mustakim.bokbok.ui.screens.auth.SignupScreen
import com.mustakim.bokbok.ui.screens.auth.SplashScreen
import com.mustakim.bokbok.ui.screens.chats.ChatsScreen
import com.mustakim.bokbok.ui.screens.gameboost.GameBoostScreen
import com.mustakim.bokbok.ui.screens.lounge.LoungeScreen
import com.mustakim.bokbok.ui.screens.notifications.NotificationsScreen
import com.mustakim.bokbok.ui.screens.permissions.PermissionsScreen
import com.mustakim.bokbok.ui.screens.profile.ProfileScreen
import com.mustakim.bokbok.ui.screens.room.VoiceRoomScreen
import com.mustakim.bokbok.ui.screens.settings.SettingsScreen
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.state.JoinMode


// ✅ Centralized animation specs for consistency
private object NavigationAnimations {

    // Keep global transitions very light and fast
    val defaultEnterTransition = fadeIn(
        animationSpec = tween(
            durationMillis = 150
        )
    )

    val defaultExitTransition = fadeOut(
        animationSpec = tween(
            durationMillis = 150
        )
    )

    // Voice room enter: faster slide + fade, no extra scaling
    val roomEnterTransition = slideInVertically(
        initialOffsetY = { it },
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        )
    ) + fadeIn(
        animationSpec = tween(
            durationMillis = 180
        )
    )

    // Voice room exit: matching speed, slightly eased out
    val roomExitTransition = slideOutVertically(
        targetOffsetY = { it },
        animationSpec = tween(
            durationMillis = 260,
            easing = FastOutSlowInEasing
        )
    ) + fadeOut(
        animationSpec = tween(
            durationMillis = 180
        )
    )
}

@Composable
fun NavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    userViewModel: UserViewModel // ✅ Receive from parent
) {
    val context = LocalContext.current

    // ✅ Memoize repositories at NavGraph level
    val userRepository = remember(context) { UserRepository(context) }
    val friendsRepository = remember(userRepository) { FriendsRepository(userRepository) }

    // ✅ Memoize permission check logic
    val hasAllRequiredPermissions = remember(context) {
        {
            val requiredPermissions = PermissionsList.getRequiredPermissions()
            requiredPermissions.all { permission ->
                ContextCompat.checkSelfPermission(
                    context,
                    permission.permission
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
    }


    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route,
        enterTransition = { NavigationAnimations.defaultEnterTransition },
        exitTransition = { NavigationAnimations.defaultExitTransition }
    ) {
        // ============= AUTH FLOW =============
        composable(NavRoutes.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLounge = {
                    val destination = if (hasAllRequiredPermissions()) {
                        NavRoutes.Lounge.route
                    } else {
                        NavRoutes.Permissions.route
                    }
                    navController.navigate(destination) {
                        popUpTo(NavRoutes.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(NavRoutes.Login.route) {
            LoginScreen(
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.Signup.route) {
            SignupScreen(
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.GoogleSignup.route) {
            GoogleSignupScreen(
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.Permissions.route) {
            PermissionsScreen(navController = navController)
        }

        // ============= MAIN APP FLOW =============
        composable(NavRoutes.Lounge.route) {
            LoungeScreen(navController, userViewModel)
        }

        composable(NavRoutes.Chats.route) {
            // ✅ Use memoized repositories from NavGraph
            ChatsScreen(
                friendsRepository = friendsRepository,
                onFriendClick = { userId ->
                    navController.navigate(NavRoutes.Chat.createRoute(userId))
                },
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.GameBoost.route) {
            GameBoostScreen(navController, userViewModel)
        }

        // ============= SECONDARY SCREENS =============
        composable(NavRoutes.Notifications.route) {
            NotificationsScreen(navController)
        }

        composable(NavRoutes.Profile.route) {
            ProfileScreen(navController)
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(navController, themeViewModel)
        }

        // ============= VOICE ROOM =============
        composable(
            route = NavRoutes.Room.route,
            arguments = listOf(
                navArgument("roomId") {
                    type = NavType.StringType
                    nullable = false
                }
            ),
            enterTransition = { NavigationAnimations.roomEnterTransition },
            exitTransition = { null }, // Disable exit (use popExit)
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { NavigationAnimations.roomExitTransition }
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId")
                ?: return@composable

            val scope = rememberCoroutineScope()
            val roomRepository = remember { RoomRepository() }
            val userRepository = remember{ UserRepository(context.applicationContext) }

            VoiceRoomScreen(
                roomId = roomId,
                onMinimize = { isMuted ->
                    RoomStateManager.minimizeRoom(isMuted)
                    navController.popBackStack()
                },
                onLeaveRoom = {
                    val modeSnapshot = RoomStateManager.joinMode.value
                    val currentRoom = RoomStateManager.currentRoom.value

                    if (currentRoom != null) {
                        scope.launch {
                            // Clear "in call" marker for this user regardless of mode
                            userRepository.setCurrentRoom(null)

                            if (modeSnapshot == JoinMode.SESSION_ONLY) {
                                val roomResult = roomRepository.getRoom(currentRoom.id)
                                val room = roomResult.getOrNull()
                                val currentUserId = userRepository.getCurrentUserId()

                                if (room != null && currentUserId != null && room.hostId != currentUserId) {
                                    roomRepository.leaveRoom(currentRoom.id)
                                }

                                RoomStateManager.leaveRoom()
                            } else {
                                RoomStateManager.leaveRoom()
                                navController.popBackStack()
                            }
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        // ============= CHAT SCREEN (Future) =============
        composable(
            route = NavRoutes.Chat.route,
            arguments = listOf(
                navArgument("userId") {
                    type = NavType.StringType
                    nullable = false
                }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
                ?: return@composable
            // TODO: Implement ChatScreen
            // ChatScreen(userId = userId, navController = navController)
        }
    }
}
