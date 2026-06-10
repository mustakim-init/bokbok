package com.mustakim.bokbok.ui.navigation

import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.mustakim.bokbok.BuildConfig
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.model.PermissionsList
import com.mustakim.bokbok.music.LocalDatabase
import com.mustakim.bokbok.music.LocalDownloadUtil
import com.mustakim.bokbok.music.LocalPlayerConnection
import com.mustakim.bokbok.music.LocalSyncUtils
import com.mustakim.bokbok.music.constants.AppBarHeight
import com.mustakim.bokbok.music.constants.FloatingToolbarBottomPadding
import com.mustakim.bokbok.music.constants.FloatingToolbarHeight
import com.mustakim.bokbok.music.constants.FloatingToolbarHorizontalPadding
import com.mustakim.bokbok.music.constants.MiniPlayerBottomSpacing
import com.mustakim.bokbok.music.constants.MiniPlayerHeight
import com.mustakim.bokbok.music.db.entities.Album
import com.mustakim.bokbok.music.db.entities.Artist
import com.mustakim.bokbok.music.db.entities.LocalItem
import com.mustakim.bokbok.music.db.entities.Playlist
import com.mustakim.bokbok.music.db.entities.Song
import com.mustakim.bokbok.music.innertube.models.AlbumItem
import com.mustakim.bokbok.music.innertube.models.ArtistItem
import com.mustakim.bokbok.music.innertube.models.PlaylistItem
import com.mustakim.bokbok.music.innertube.models.SongItem
import com.mustakim.bokbok.music.innertube.models.YTItem
import com.mustakim.bokbok.music.models.toMediaMetadata
import com.mustakim.bokbok.music.playback.DownloadUtil
import com.mustakim.bokbok.music.playback.PlayerConnection
import com.mustakim.bokbok.music.playback.queues.LocalAlbumRadio
import com.mustakim.bokbok.music.playback.queues.YouTubeAlbumRadio
import com.mustakim.bokbok.music.playback.queues.YouTubeQueue
import com.mustakim.bokbok.music.ui.player.BottomSheetPlayer
import com.mustakim.bokbok.music.ui.screens.Screens as MusicScreens
import com.mustakim.bokbok.music.ui.screens.musicrecognition.MusicRecognitionRoute
import com.mustakim.bokbok.music.ui.screens.navigationBuilder
import com.mustakim.bokbok.music.ui.screens.settings.AccountSettings
import com.mustakim.bokbok.music.ui.screens.settings.AppearanceSettings
import com.mustakim.bokbok.music.ui.screens.settings.BackupAndRestore
import com.mustakim.bokbok.music.ui.screens.settings.ChangelogScreen
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.music.utils.SyncUtils
import com.mustakim.bokbok.music.viewmodels.HomeViewModel
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.ui.navigation.NavRoutes
import com.mustakim.bokbok.ui.screens.Screens as AppScreens
import com.mustakim.bokbok.ui.screens.auth.GoogleSignupScreen
import com.mustakim.bokbok.ui.screens.auth.LoginScreen
import com.mustakim.bokbok.ui.screens.auth.SignupScreen
import com.mustakim.bokbok.ui.screens.auth.SplashScreen
import com.mustakim.bokbok.ui.screens.batteryoptimization.BatteryOptimizationScreen
import com.mustakim.bokbok.ui.screens.chats.ChatsScreen
import com.mustakim.bokbok.ui.screens.common.TopBar
import com.mustakim.bokbok.ui.screens.gameboost.GameBoostScreen
import com.mustakim.bokbok.ui.screens.gameboost.screenrecord.InternalVideoPlayerScreen
import com.mustakim.bokbok.ui.screens.lounge.LoungeScreen
import com.mustakim.bokbok.ui.screens.notifications.NotificationsScreen
import com.mustakim.bokbok.ui.screens.permissions.PermissionsScreen
import com.mustakim.bokbok.ui.screens.profile.ProfileScreen
import com.mustakim.bokbok.ui.screens.settings.AboutScreen
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.BottomSheetMenu
import com.mustakim.bokbok.ui.shared.BottomSheetPage
import com.mustakim.bokbok.ui.shared.BottomSheetPageState
import com.mustakim.bokbok.ui.shared.FloatingNavigationToolbar
import com.mustakim.bokbok.ui.shared.LocalBottomSheetPageState
import com.mustakim.bokbok.ui.shared.LocalMenuState
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets
import com.mustakim.bokbok.ui.shared.LocalPlayerBottomSheetState
import com.mustakim.bokbok.ui.shared.MenuState
import com.mustakim.bokbok.ui.shared.SettingsPage
import com.mustakim.bokbok.ui.shared.rememberBottomSheetState
import com.mustakim.bokbok.ui.shared.shimmer.ShimmerTheme
import com.mustakim.bokbok.util.Updater
import com.mustakim.bokbok.viewmodel.LoungeViewModel
import com.mustakim.bokbok.viewmodel.NotificationViewModel
import com.mustakim.bokbok.viewmodel.ThemeViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.viewmodel.VoiceRoomViewModel
import com.mustakim.bokbok.viewmodel.FriendsViewModel
import com.mustakim.bokbok.ui.screens.chats.AddFriendDialog
import com.mustakim.bokbok.ui.screens.chats.CreateGroupDialog
import com.mustakim.bokbok.ui.screens.lounge.CreateRoomDialog
import com.valentinilk.shimmer.LocalShimmerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

// ✅ Centralized animation specs for consistency
private object NavigationAnimations {
    val defaultEnterTransition = fadeIn(animationSpec = tween(150))
    val defaultExitTransition = fadeOut(animationSpec = tween(150))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    navController: NavHostController,
    themeViewModel: ThemeViewModel,
    database: com.mustakim.bokbok.music.db.MusicDatabase,
    syncUtils: SyncUtils,
    downloadUtil: DownloadUtil,
    playerConnection: PlayerConnection?
) {
    val context = LocalContext.current
    val bottomSheetPageState = remember { BottomSheetPageState() }
    val menuState = remember { MenuState() }
    val adbSetupViewModel: com.mustakim.bokbok.viewmodel.AdbSetupViewModel = hiltViewModel()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val pureBlackPref by themeViewModel.pureBlack.collectAsState()
    val isSystemInDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val pureBlack = pureBlackPref && isSystemInDarkTheme
    
    val homeViewModel: HomeViewModel = hiltViewModel()
    val loungeViewModel: LoungeViewModel = hiltViewModel()
    val friendsViewModel: FriendsViewModel = hiltViewModel()
    val userViewModel: UserViewModel = hiltViewModel()
    val notificationViewModel: NotificationViewModel = hiltViewModel()
    val voiceRoomViewModel: VoiceRoomViewModel = hiltViewModel()
    val themeViewModelInternal: ThemeViewModel = themeViewModel // Use passed or hoisted

    val allLocalItems by homeViewModel.allLocalItems.collectAsState(initial = emptyList<LocalItem>())
    val allYtItems by homeViewModel.allYtItems.collectAsState(initial = emptyList<YTItem>())
    val coroutineScope = rememberCoroutineScope()

    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showAddFriendDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }

    val topInset = androidx.compose.foundation.layout.WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    val bottomInset = androidx.compose.foundation.layout.WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxHeight = maxHeight
        val collapsedBound = bottomInset + 
                FloatingToolbarBottomPadding + FloatingToolbarHeight + MiniPlayerBottomSpacing + MiniPlayerHeight
        
        val playerBottomSheetState = rememberBottomSheetState(
            dismissedBound = 0.dp,
            collapsedBound = collapsedBound,
            expandedBound = maxHeight,
        )

        // Automatically show the mini player when music starts playing
        DisposableEffect(playerConnection, playerBottomSheetState) {
            val player = playerConnection?.player ?: return@DisposableEffect onDispose { }
            val listener = object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                        mediaItem != null &&
                        playerBottomSheetState.isDismissed
                    ) {
                        playerBottomSheetState.collapseSoft()
                    }
                }
            }
            player.addListener(listener)
            
            // Restore state if music is already playing
            if (player.currentMediaItem != null && playerBottomSheetState.isDismissed) {
                playerBottomSheetState.collapseSoft()
            }

            onDispose {
                player.removeListener(listener)
            }
        }

        val systemBars = WindowInsets.systemBars
        val defaultWindowInsets = remember(playerBottomSheetState.isDismissed, systemBars) {
            val bottom = if (!playerBottomSheetState.isDismissed) MiniPlayerHeight else 0.dp
            systemBars
                .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
                .add(WindowInsets(top = AppBarHeight, bottom = bottom))
        }

        CompositionLocalProvider(
            LocalPlayerAwareWindowInsets provides defaultWindowInsets,
            LocalPlayerBottomSheetState provides playerBottomSheetState,
            LocalDatabase provides database,
            LocalSyncUtils provides syncUtils,
            LocalDownloadUtil provides downloadUtil,
            LocalPlayerConnection provides playerConnection,
            LocalShimmerTheme provides ShimmerTheme,
            LocalBottomSheetPageState provides bottomSheetPageState,
            LocalMenuState provides menuState,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = NavRoutes.Splash.route,
                    enterTransition = { NavigationAnimations.defaultEnterTransition },
                    exitTransition = { NavigationAnimations.defaultExitTransition }
                ) {
                    // ============= AUTH FLOW =============
                    composable(NavRoutes.Splash.route) {
                        SplashScreen(
                            onNavigateToLogin = { navController.navigate(NavRoutes.Login.route) { popUpTo(NavRoutes.Splash.route) { inclusive = true } } },
                            onNavigateToLounge = { navController.navigate(NavRoutes.Lounge.route) { popUpTo(NavRoutes.Splash.route) { inclusive = true } } }
                        )
                    }
                    composable(NavRoutes.Login.route) {
                        LoginScreen(navController = navController)
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
                    composable(NavRoutes.Permissions.route) { PermissionsScreen(navController) }

                    // ============= MAIN APP FLOW =============
                    composable(NavRoutes.Lounge.route) {
                        LoungeScreen(
                            navController = navController,
                            userViewModel = userViewModel,
                            loungeViewModel = loungeViewModel
                        )
                    }

                    composable(NavRoutes.Chats.route) {
                        ChatsScreen(
                            navController = navController,
                            userViewModel = userViewModel,
                            friendsViewModel = friendsViewModel,
                            onFriendClick = { userId -> navController.navigate("chat/$userId") }
                        )
                    }

                    composable(NavRoutes.Notifications.route) {
                        NotificationsScreen(
                            navController = navController,
                            viewModel = notificationViewModel
                        )
                    }

                    composable(NavRoutes.Profile.route) {
                        val profileViewModel: com.mustakim.bokbok.viewmodel.ProfileViewModel = hiltViewModel()
                        ProfileScreen(
                            navController = navController,
                            profileViewModel = profileViewModel,
                            userViewModel = userViewModel
                        )
                    }

                    // ============= MUSIC & SETTINGS FLOW =============
                    navigationBuilder(
                        navController = navController,
                        scrollBehavior = scrollBehavior,
                        latestVersionName = BuildConfig.VERSION_NAME,
                    )

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
                        arguments = listOf(navArgument("path") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val path = backStackEntry.arguments?.getString("path") ?: ""
                        InternalVideoPlayerScreen(
                            navController = navController,
                            videoPath = path
                        )
                    }

                    composable(NavRoutes.AICompanion.route) {
                        com.mustakim.bokbok.ui.screens.ai.AICompanionScreen(
                            navController = navController,
                            userViewModel = userViewModel
                        )
                    }

                    composable(NavRoutes.GameBoost.route) {
                        GameBoostScreen(navController, userViewModel)
                    }

                    composable(
                        route = NavRoutes.AppDetails.route,
                        arguments = listOf(navArgument("packageName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
                        val viewModel: com.mustakim.bokbok.viewmodel.AppDetailsViewModel = hiltViewModel()
                        LaunchedEffect(packageName) { viewModel.setPackageName(packageName) }
                        com.mustakim.bokbok.ui.screens.gameboost.appmanager.AppDetailsScreen(
                            navController = navController,
                            viewModel = viewModel
                        )
                    }

                    composable(
                        route = NavRoutes.GameDetail.route,
                        arguments = listOf(navArgument("packageName") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val packageName = backStackEntry.arguments?.getString("packageName") ?: ""
                        val viewModel: com.mustakim.bokbok.viewmodel.GameSpaceViewModel = hiltViewModel()
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
                        val chatViewModel: com.mustakim.bokbok.viewmodel.ChatViewModel = hiltViewModel(backStackEntry)

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
                        val groupChatViewModel: com.mustakim.bokbok.viewmodel.GroupChatViewModel = hiltViewModel(backStackEntry)

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
                        val chatId = backStackEntry.arguments?.getString("chatId") ?: ""

                        if (isGroup) {
                            // 🚀 SCOPE PERSISTENCE: Attempt to share ViewModel with GroupChatScreen
                            val groupChatEntry = remember(backStackEntry) {
                                try {
                                    navController.getBackStackEntry(NavRoutes.GroupChat.createRoute(chatId))
                                } catch (e: Exception) {
                                    backStackEntry
                                }
                            }
                            val viewModel: com.mustakim.bokbok.viewmodel.GroupChatViewModel = hiltViewModel(groupChatEntry)
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
                            val chatId = backStackEntry.arguments?.getString("chatId") ?: ""
                            // 🚀 SCOPE PERSISTENCE: Attempt to share ViewModel with individual ChatScreen
                            val chatEntry = remember(backStackEntry) {
                                try {
                                    navController.getBackStackEntry(NavRoutes.Chat.createRoute(chatId))
                                } catch (e: Exception) {
                                    backStackEntry
                                }
                            }
                            val viewModel: com.mustakim.bokbok.viewmodel.ChatViewModel = hiltViewModel(chatEntry)
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

                        // 🚀 SCOPE PERSISTENCE: Share GroupChatViewModel from the main chat screen
                        val groupChatEntry = remember(backStackEntry) {
                            try {
                                navController.getBackStackEntry(NavRoutes.GroupChat.createRoute(groupId))
                            } catch (e: Exception) {
                                backStackEntry
                            }
                        }
                        val viewModel: com.mustakim.bokbok.viewmodel.GroupChatViewModel = hiltViewModel(groupChatEntry)

                        com.mustakim.bokbok.ui.screens.chat.ChatMembersScreen(
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }

                // Render Global MusicTopBar over the NavHost to prevent flickering during screen transitions
                val hideTopBarRoutes = remember { setOf(com.mustakim.bokbok.music.ui.screens.Screens.Search.route, "history") }
                val isMusicRouteExt = currentRoute != null && (
                    com.mustakim.bokbok.music.ui.screens.Screens.MainScreens.map { it.route }.contains(currentRoute) || 
                    currentRoute.startsWith("music") || 
                    com.mustakim.bokbok.music.ui.screens.Screens.MainScreens.map { it.route }.contains(currentRoute.split("/")[0])
                )
                // Voice Room State
                val currentRoom by RoomStateManager.currentRoom
                val isMinimizedRoom by RoomStateManager.isMinimized
                val isMutedRoom by RoomStateManager.isMuted

                if (isMusicRouteExt && currentRoute !in hideTopBarRoutes && currentRoute?.startsWith("search") == false && (currentRoom == null || isMinimizedRoom)) {
                    com.mustakim.bokbok.music.ui.MusicTopBar(
                        navController = navController,
                        currentRoute = currentRoute,
                        scrollBehavior = scrollBehavior,
                        pureBlack = pureBlack,
                        latestVersionName = com.mustakim.bokbok.music.BuildConfig.VERSION_NAME
                    )
                }

                // Global Overlays (Menu & Page)
                BottomSheetPage(
                    state = bottomSheetPageState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                BottomSheetMenu(
                    state = menuState,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )

                // Hoisted Floating Navigation Toolbar
                val musicMainRoutes = remember {
                    MusicScreens.MainScreens.map { it.route }.toSet()
                }
                val musicPrefixes = remember {
                    setOf("album", "artist", "online_playlist", "local_playlist", "auto_playlist", "cache_playlist", "top_playlist", "youtube_browse", "history", "stats", "year_in_music", "account", "settings", "search")
                }
                val appMainRoutes = remember {
                    AppScreens.MainScreens.map { it.route }.toSet()
                }
                
                val isMusicRoute = currentRoute != null && (
                    musicMainRoutes.contains(currentRoute) || 
                    currentRoute.startsWith("music") || 
                    musicPrefixes.contains(currentRoute.split("/")[0])
                )
                val isAppRoute = currentRoute != null && (
                    appMainRoutes.contains(currentRoute) || 
                    currentRoute == NavRoutes.Lounge.route || 
                    appMainRoutes.contains(currentRoute.split("/")[0])
                )
                
                val showNav = (isMusicRoute || isAppRoute) && (currentRoom == null || isMinimizedRoom)

                // Start VoiceRoomActivity when a room is joined
                val context = androidx.compose.ui.platform.LocalContext.current
                LaunchedEffect(currentRoom, isMinimizedRoom) {
                    if (currentRoom != null && !isMinimizedRoom) {
                        android.util.Log.d("NavGraph", "Launching VoiceRoomActivity for room: ${currentRoom?.id}")
                        val intent = android.content.Intent(context, com.mustakim.bokbok.ui.screens.room.VoiceRoomActivity::class.java).apply {
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        context.startActivity(intent)
                    }
                }

                if (showNav) {
                    val navItems = if (isMusicRoute) {
                        MusicScreens.MainScreens.map { it.toNavigationItem() }
                    } else {
                        AppScreens.MainScreens.map { it.toNavigationItem() }
                    }

                    val isHomeScreen = currentRoute == MusicScreens.Home.route
                    val isLibraryScreen = currentRoute == MusicScreens.Library.route
                    val shouldShowHomeShuffleButton = isMusicRoute && isHomeScreen && (allLocalItems.isNotEmpty() || allYtItems.isNotEmpty())


                    // Render BottomSheetPlayer only for music routes (matches user requirement for modular isolation)
                    if (isMusicRoute) {
                        BottomSheetPlayer(
                            state = playerBottomSheetState,
                            navController = navController,
                            pureBlack = pureBlack
                        )
                    }

                    FloatingNavigationToolbar(
                        items = navItems,
                        pureBlack = pureBlack,
                        playerBottomSheetState = playerBottomSheetState,
                        bottomInset = bottomInset,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        customFab = null,
                        onShuffleClick = if (shouldShowHomeShuffleButton) {
                            {
                                val useLocalSource = when {
                                    allLocalItems.isNotEmpty() && allYtItems.isNotEmpty() -> Random.nextFloat() < 0.5f
                                    allLocalItems.isNotEmpty() -> true
                                    else -> false
                                }

                                coroutineScope.launch {
                                    if (useLocalSource) {
                                        val luckyItem = allLocalItems.random()
                                        if (luckyItem is Song) {
                                            playerConnection?.playQueue(
                                                YouTubeQueue.radio(luckyItem.toMediaMetadata())
                                            )
                                        }
                                    } else {
                                        val luckyItem = allYtItems.random()
                                        if (luckyItem is SongItem) {
                                            playerConnection?.playQueue(
                                                YouTubeQueue.radio(luckyItem.toMediaMetadata())
                                            )
                                        }
                                    }
                                }
                            }
                        } else null,
                        shuffleIconRes = if (shouldShowHomeShuffleButton) com.mustakim.bokbok.core.R.drawable.shuffle else null,
                        onMusicRecognitionClick = if (isMusicRoute) {
                            {
                                navController.navigate(MusicRecognitionRoute) {
                                    launchSingleTop = true
                                }
                            }
                        } else null,
                        onSettingsClick = { navController.navigate("settings") },
                        onAddFriendClick = if (currentRoute == NavRoutes.Chats.route) {
                            { showAddFriendDialog = true }
                        } else null,
                        onCreateGroupClick = if (currentRoute == NavRoutes.Chats.route) {
                            { showCreateGroupDialog = true }
                        } else null,
                        onCreateRoomClick = if (currentRoute == NavRoutes.Lounge.route) {
                            { showCreateRoomDialog = true }
                        } else null,
                        isSelected = { item -> 
                            if (isMusicRoute && item.id == "music") false
                            else currentRoute == item.id 
                        },
                        onItemClick = { item, alreadySelected ->
                            if (!alreadySelected) {
                                val route = if (item.id == "music") MusicScreens.Home.route else item.id
                                navController.navigate(route) {
                                    popUpTo(NavRoutes.Lounge.route) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
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

                    if (showAddFriendDialog) {
                        AddFriendDialog(
                            viewModel = friendsViewModel,
                            onDismiss = { showAddFriendDialog = false }
                        )
                    }

                    if (showCreateGroupDialog) {
                        CreateGroupDialog(
                            viewModel = friendsViewModel,
                            onDismiss = { showCreateGroupDialog = false }
                        )
                    }
                }
            }
        }
    }
}
