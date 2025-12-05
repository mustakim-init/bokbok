package com.mustakim.bokbok.ui.navigation

import android.content.pm.PackageManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mustakim.bokbok.data.model.PermissionsList
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.ui.screens.auth.GoogleSignupScreen
import com.mustakim.bokbok.ui.screens.auth.LoginScreen
import com.mustakim.bokbok.ui.screens.auth.SignupScreen
import com.mustakim.bokbok.ui.screens.auth.SplashScreen
import com.mustakim.bokbok.ui.screens.batteryoptimization.BatteryOptimizationScreen
import com.mustakim.bokbok.ui.screens.chats.ChatsScreen
import com.mustakim.bokbok.ui.screens.gameboost.GameBoostScreen
import com.mustakim.bokbok.ui.screens.lounge.LoungeScreen
import com.mustakim.bokbok.ui.screens.notifications.NotificationsScreen
import com.mustakim.bokbok.ui.screens.permissions.PermissionsScreen
import com.mustakim.bokbok.ui.screens.profile.ProfileScreen
import com.mustakim.bokbok.ui.screens.settings.SettingsScreen
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import com.mustakim.bokbok.viewmodel.NotificationViewModel
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel


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
    val application = context.applicationContext as android.app.Application

    // ✅ Memoize repositories at NavGraph level
    val userRepository = remember(context) { UserRepository(context) }
    val friendsRepository = remember(userRepository) { FriendsRepository(userRepository) }
    val notificationRepository = remember { com.mustakim.bokbok.data.repository.NotificationRepository() }
    val chatRepository = remember { com.mustakim.bokbok.data.repository.ChatRepository() }
    val hybridChatRepository = remember(context) { com.mustakim.bokbok.data.repository.HybridChatRepository(context) }

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
            val loungeViewModel = viewModel<LoungeViewModel>()

            // Create NotificationViewModel with Factory
            val notificationViewModel: NotificationViewModel = viewModel(
                factory = NotificationViewModel.Factory(
                    application,
                    notificationRepository,
                    friendsRepository,
                    userRepository
                )
            )

            val notificationCount by notificationViewModel.unreadCount.collectAsState()

            LoungeScreen(
                navController = navController,
                userViewModel = userViewModel,
                notificationCount = notificationCount,
                loungeViewModel = loungeViewModel
            )
        }

        composable(NavRoutes.Chats.route) {
            // ✅ Use memoized repositories from NavGraph
            ChatsScreen(
                friendsRepository = friendsRepository,
                chatRepository = chatRepository,
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
            // Create NotificationViewModel with Factory (shared instance if scoped correctly, but here it's a new screen)
            // Ideally we scope it to a navigation graph, but for now we recreate or get existing if scoped.
            // Since we are in a different composable, `viewModel()` will create a new one unless we scope it.
            // For notifications, a fresh VM is fine, or we can scope to the Activity.

            val notificationViewModel: NotificationViewModel = viewModel(
                factory = NotificationViewModel.Factory(
                    application,
                    notificationRepository,
                    friendsRepository,
                    userRepository
                )
            )

            NotificationsScreen(
                navController = navController,
                viewModel = notificationViewModel
            )
        }

        composable(NavRoutes.Profile.route) {
            ProfileScreen(navController)
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(navController, themeViewModel)
        }

        composable(NavRoutes.BatteryOptimization.route) {
            BatteryOptimizationScreen(navController)
        }



        // ============= VOICE ROOM =============
        // NOTE: VoiceRoomScreen is rendered via RoomStateManager in MainScaffold, not via navigation
        // The NavRoutes.Room route has been removed to avoid duplicate rendering

        // ============= CHAT SCREEN =============
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
            
            val chatViewModel: com.mustakim.bokbok.viewmodel.ChatViewModel = viewModel(
                factory = com.mustakim.bokbok.viewmodel.ChatViewModel.Factory(
                    hybridChatRepository,
                    userRepository,
                    friendsRepository,
                    userId
                )
            )
            
            com.mustakim.bokbok.ui.screens.chat.ChatScreen(
                navController = navController,
                viewModel = chatViewModel
            )
        }

        composable(
            route = NavRoutes.GroupChat.route,
            arguments = listOf(
                navArgument("groupId") {
                    type = NavType.StringType
                    nullable = false
                }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId")
                ?: return@composable

            val groupChatViewModel: com.mustakim.bokbok.viewmodel.GroupChatViewModel = viewModel(
                factory = com.mustakim.bokbok.viewmodel.GroupChatViewModel.Factory(
                    context,
                    groupId
                )
            )

            com.mustakim.bokbok.ui.screens.chat.GroupChatScreen(
                navController = navController,
                viewModel = groupChatViewModel
            )
        }
    }
}
