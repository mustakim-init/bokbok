package com.mustakim.bokbok.ui.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.mustakim.bokbok.data.model.PermissionsList
import com.mustakim.bokbok.ui.screens.auth.LoginScreen
import com.mustakim.bokbok.ui.screens.auth.SignupScreen
import com.mustakim.bokbok.ui.screens.auth.SplashScreen
import com.mustakim.bokbok.ui.screens.auth.UsernameSetupScreen
import com.mustakim.bokbok.ui.screens.chats.ChatsScreen
import com.mustakim.bokbok.ui.screens.gameboost.GameBoostScreen
import com.mustakim.bokbok.ui.screens.lounge.LoungeScreen
import com.mustakim.bokbok.ui.screens.notifications.NotificationsScreen
import com.mustakim.bokbok.ui.screens.permissions.PermissionsScreen
import com.mustakim.bokbok.ui.screens.profile.ProfileScreen
import com.mustakim.bokbok.ui.screens.settings.SettingsScreen
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.ui.screens.room.VoiceRoomScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.mustakim.bokbok.state.RoomStateManager
import androidx.compose.runtime.remember
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.data.repository.FriendsRepository
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mustakim.bokbok.ui.screens.auth.GoogleSignupScreen





@Composable
fun NavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel
) {
    val userViewModel: UserViewModel = viewModel()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = NavRoutes.Splash.route
    ) {
        composable(NavRoutes.Splash.route) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(NavRoutes.Login.route) {
                        popUpTo(NavRoutes.Splash.route) { inclusive = true }
                    }
                },
                onNavigateToLounge = {
                    // Check if required permissions are granted
                    val requiredPermissions = PermissionsList.getRequiredPermissions()
                    val allRequiredGranted = requiredPermissions.all { permission ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            permission.permission == android.Manifest.permission.POST_NOTIFICATIONS) {
                            ContextCompat.checkSelfPermission(
                                context,
                                permission.permission
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        } else {
                            ContextCompat.checkSelfPermission(
                                context,
                                permission.permission
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        }
                    }

                    if (allRequiredGranted) {
                        // All permissions granted, go to lounge
                        navController.navigate(NavRoutes.Lounge.route) {
                            popUpTo(NavRoutes.Splash.route) { inclusive = true }
                        }
                    } else {
                        // Need permissions, show permission screen
                        navController.navigate(NavRoutes.Permissions.route) {
                            popUpTo(NavRoutes.Splash.route) { inclusive = true }
                        }
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

        composable(NavRoutes.SetupUsername.route) {
            UsernameSetupScreen(
                navController = navController,
                userViewModel = userViewModel
            )
        }

        // In your NavGraph setup
        composable("google_signup") {
            GoogleSignupScreen(
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.Permissions.route) {
            PermissionsScreen(navController = navController)
        }

        composable(NavRoutes.Lounge.route) {
            LoungeScreen(navController, userViewModel)
        }

        composable(NavRoutes.Chats.route) {
            val context = LocalContext.current
            val userRepository = remember { UserRepository(context) }
            val friendsRepository = remember { FriendsRepository(userRepository) }

            ChatsScreen(
                friendsRepository = friendsRepository,
                onFriendClick = { userId -> },
                navController = navController,  // ✅ Pass navController
                userViewModel = userViewModel   // ✅ Pass userViewModel
            )
        }

        composable(NavRoutes.GameBoost.route) {
            GameBoostScreen(navController, userViewModel)
        }

        composable(NavRoutes.Notifications.route) {
            NotificationsScreen(navController)
        }

        composable(NavRoutes.Profile.route) {
            ProfileScreen(navController)
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(navController, themeViewModel)
        }

        // Voice Room screen
        // Voice Room screen
        composable(
            route = "voice_room/{roomId}",
            arguments = listOf(navArgument("roomId") { type = NavType.StringType }),
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)
                    )
                ) + fadeIn(
                    animationSpec = tween(300)
                )
            },
            exitTransition = null,  // ✅ Disable exit (we use popExit instead)
            popEnterTransition = {
                // Lounge reappears
                fadeIn(animationSpec = tween(200))
            },
            popExitTransition = {
                // ✅ THIS is the minimize animation
                slideOutVertically(
                    targetOffsetY = { it },  // Slide down
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
                    )
                ) + scaleOut(
                    targetScale = 0.85f,
                    animationSpec = tween(
                        durationMillis = 450,
                        easing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
                    )
                ) + fadeOut(
                    animationSpec = tween(300)
                )
            }
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable

            // ✅ NO coroutine scope needed!
            VoiceRoomScreen(
                roomId = roomId,
                onMinimize = { room, isMuted ->
                    RoomStateManager.minimizeRoom(room, isMuted)
                    navController.popBackStack()  // ✅ Animation happens automatically
                },
                onLeaveRoom = {
                    RoomStateManager.leaveRoom()
                    navController.popBackStack()
                }
            )
        }
    }
}
