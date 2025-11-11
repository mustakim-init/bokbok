package com.mustakim.bokbok.ui.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
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

        composable(NavRoutes.Permissions.route) {
            PermissionsScreen(navController = navController)
        }

        composable(NavRoutes.Lounge.route) {
            LoungeScreen(navController, userViewModel)
        }

        composable(NavRoutes.Chats.route) {
            ChatsScreen(navController, userViewModel)
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
        composable(
            route = "voice_room/{roomId}",
            arguments = listOf(
                navArgument("roomId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val roomId = backStackEntry.arguments?.getString("roomId") ?: return@composable

            VoiceRoomScreen(
                roomId = roomId,
                // ✅ FIX: Save state and navigate back
                onMinimize = { room, isMuted ->
                    RoomStateManager.minimizeRoom(room, isMuted)
                    navController.popBackStack()
                },
                onLeaveRoom = {
                    RoomStateManager.leaveRoom()
                    navController.popBackStack()
                }
            )
        }
    }
}
