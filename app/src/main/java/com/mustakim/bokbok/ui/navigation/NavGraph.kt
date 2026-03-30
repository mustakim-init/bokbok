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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mustakim.bokbok.data.model.PermissionsList
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
) {
    val context = LocalContext.current

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
            val userViewModel: UserViewModel = hiltViewModel()
            LoginScreen(
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.Signup.route) {
            val userViewModel: UserViewModel = hiltViewModel()
            SignupScreen(
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.GoogleSignup.route) {
            val userViewModel: UserViewModel = hiltViewModel()
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
            val loungeViewModel: LoungeViewModel = hiltViewModel()
            val userViewModel: UserViewModel = hiltViewModel()
            val notificationViewModel: NotificationViewModel = hiltViewModel()

            val notificationCount by notificationViewModel.unreadCount.collectAsState()

            LoungeScreen(
                navController = navController,
                userViewModel = userViewModel,
                notificationCount = notificationCount,
                loungeViewModel = loungeViewModel
            )
        }

        composable(NavRoutes.Chats.route) {
            val userViewModel: UserViewModel = hiltViewModel()
            ChatsScreen(
                onFriendClick = { userId ->
                    navController.navigate(NavRoutes.Chat.createRoute(userId))
                },
                navController = navController,
                userViewModel = userViewModel
            )
        }

        composable(NavRoutes.GameBoost.route) {
            val userViewModel: UserViewModel = hiltViewModel()
            GameBoostScreen(navController, userViewModel)
        }

        // ============= SECONDARY SCREENS =============
        composable(NavRoutes.Notifications.route) {
            val notificationViewModel: NotificationViewModel = hiltViewModel()
 
            NotificationsScreen(
                navController = navController,
                viewModel = notificationViewModel
            )
        }

        composable(NavRoutes.Profile.route) {
            ProfileScreen(navController)
        }

        composable(NavRoutes.Settings.route) {
            val adbSetupViewModel: com.mustakim.bokbok.viewmodel.AdbSetupViewModel = hiltViewModel()
            SettingsScreen(navController, themeViewModel, adbSetupViewModel)
        }

        composable(NavRoutes.BatteryOptimization.route) {
            BatteryOptimizationScreen(navController)
        }

        composable(NavRoutes.RecordingsGallery.route) {
            val screenRecordViewModel: com.mustakim.bokbok.viewmodel.ScreenRecordViewModel = hiltViewModel()
            com.mustakim.bokbok.ui.screens.gameboost.screenrecord.RecordingsGalleryScreen(
                navController = navController,
                viewModel = screenRecordViewModel
            )
        }

        composable(
            route = NavRoutes.VideoPlayer.route,
            arguments = listOf(
                navArgument("path") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString("path") ?: ""
            com.mustakim.bokbok.ui.screens.gameboost.screenrecord.InternalVideoPlayerScreen(
                navController = navController,
                videoPath = path
            )
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
        ) {
            val chatViewModel: com.mustakim.bokbok.viewmodel.ChatViewModel = hiltViewModel()
            
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
        ) {
            val groupChatViewModel: com.mustakim.bokbok.viewmodel.GroupChatViewModel = hiltViewModel()
 
            com.mustakim.bokbok.ui.screens.chat.GroupChatScreen(
                navController = navController,
                viewModel = groupChatViewModel
            )
        }

        composable(
            route = NavRoutes.ChatDetails.route,
            arguments = listOf(
                navArgument("chatId") { type = NavType.StringType },
                navArgument("isGroup") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val isGroup = backStackEntry.arguments?.getBoolean("isGroup") ?: false
 
            if (isGroup) {
                val viewModel: com.mustakim.bokbok.viewmodel.GroupChatViewModel = hiltViewModel()
                val groupName by viewModel.groupName.collectAsState()
                val groupMembers by viewModel.groupMembers.collectAsState()
                val groupInfo by viewModel.groupInfo.collectAsState()
                val isUploadingImage by viewModel.isUploadingImage.collectAsState()
                val uploadError by viewModel.uploadError.collectAsState()
                
                val creatorName = remember(groupInfo, groupMembers) {
                    groupMembers.find { it.uid == groupInfo?.createdBy }?.displayName
                }
                
                // State for add member dialog
                var showAddMemberDialog by remember { mutableStateOf(false) }
                
                com.mustakim.bokbok.ui.screens.chat.ChatDetailsScreen(
                    user = null,
                    isGroup = true,
                    groupName = groupName,
                    groupImageUrl = groupInfo?.imageUrl,
                    members = groupMembers,
                    creatorName = creatorName,
                    onUpdateGroupImage = { uri -> viewModel.updateGroupImage(uri) },
                    isUploadingImage = isUploadingImage,
                    uploadError = uploadError,
                    onClearUploadError = { viewModel.clearUploadError() },
                    onBackClick = { navController.popBackStack() },
                    onMuteClick = { /* TODO: Implement mute */ },
                    onClearHistory = { 
                        viewModel.clearChatHistory {
                            navController.popBackStack(NavRoutes.Lounge.route, false)
                        }
                    },
                    onRemoveFriend = { 
                        viewModel.leaveGroup {
                            navController.popBackStack(NavRoutes.Lounge.route, false)
                        } 
                    },
                    onAddMember = { showAddMemberDialog = true },
                    onSeeMembers = {
                        val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                        navController.navigate(NavRoutes.ChatMembers.createRoute(chatId))
                    },
                    onRemoveGroupImage = { viewModel.removeGroupImage() },
                    onDeleteGroup = if (groupInfo?.createdBy == viewModel.currentUserId) {
                        {
                            viewModel.deleteGroup {
                                navController.popBackStack(NavRoutes.Lounge.route, false)
                            }
                        }
                    } else null
                )
                
                // Add Member Dialog
                if (showAddMemberDialog) {
                    com.mustakim.bokbok.ui.screens.chat.AddMemberDialog(
                        currentMembers = groupMembers,
                        onDismiss = { showAddMemberDialog = false },
                        onAddMembers = { userIds ->
                            userIds.forEach { userId ->
                                viewModel.addMemberToGroup(userId)
                            }
                            showAddMemberDialog = false
                        }
                    )
                }
            } else {
                val viewModel: com.mustakim.bokbok.viewmodel.ChatViewModel = hiltViewModel()
                val friendUser by viewModel.friendUser.collectAsState()
                
                com.mustakim.bokbok.ui.screens.chat.ChatDetailsScreen(
                    user = friendUser,
                    isGroup = false,
                    groupName = null,
                    onBackClick = { navController.popBackStack() },
                    onMuteClick = { /* TODO */ },
                    onClearHistory = {
                        viewModel.clearChatHistory {
                            navController.popBackStack(NavRoutes.Lounge.route, false)
                        }
                    },
                    onRemoveFriend = {
                        viewModel.removeFriend {
                            navController.popBackStack(NavRoutes.Lounge.route, false)
                        }
                    },
                    onAddMember = {},
                    onSeeMembers = {}
                )
            }
        }

        composable(
            route = NavRoutes.ChatMembers.route,
            arguments = listOf(
                navArgument("groupId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val groupId = backStackEntry.arguments?.getString("groupId") ?: return@composable
            
            // Reuse GroupChatViewModel or create new one with same groupId
            // Since we need to manage members (remove), reusing is good.
            val viewModel: com.mustakim.bokbok.viewmodel.GroupChatViewModel = hiltViewModel()

            com.mustakim.bokbok.ui.screens.chat.ChatMembersScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }

        composable(route = NavRoutes.AICompanion.route) {
            com.mustakim.bokbok.ui.screens.ai.AICompanionScreen(navController = navController)
        }

        composable(
            route = NavRoutes.AppDetails.route,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            val viewModel: com.mustakim.bokbok.viewmodel.AppDetailsViewModel = hiltViewModel()
            
            // Initialize ViewModel with packageName
            androidx.compose.runtime.LaunchedEffect(packageName) {
                viewModel.setPackageName(packageName)
            }

            com.mustakim.bokbok.ui.screens.gameboost.appmanager.AppDetailsScreen(
                navController = navController,
                viewModel = viewModel
            )
        }

        composable(
            route = NavRoutes.GameDetail.route,
            arguments = listOf(
                navArgument("packageName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
            val viewModel: com.mustakim.bokbok.viewmodel.GameSpaceViewModel = hiltViewModel()

            // State to hold the specific game being viewed
            val games by viewModel.games.collectAsState()
            val game = remember(games, packageName) { games.find { it.packageName == packageName } }

            game?.let {
                com.mustakim.bokbok.ui.screens.gameboost.games.GameDetailScreen(
                    game = it,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
