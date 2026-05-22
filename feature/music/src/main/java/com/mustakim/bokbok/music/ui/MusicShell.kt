@file:OptIn(ExperimentalMaterial3Api::class)
package com.mustakim.bokbok.music.ui
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.mustakim.bokbok.music.BuildConfig
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.rememberPreference
import com.mustakim.bokbok.music.LocalPlayerConnection
import com.mustakim.bokbok.music.constants.*
import com.mustakim.bokbok.music.ui.player.BottomSheetPlayer

import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import com.mustakim.bokbok.music.ui.screens.Screens
import com.mustakim.bokbok.music.ui.screens.musicrecognition.MusicRecognitionRoute
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.ui.shared.*
import com.mustakim.bokbok.util.Updater

import com.mustakim.bokbok.music.db.entities.Song
import com.mustakim.bokbok.music.db.entities.Album
import com.mustakim.bokbok.music.db.entities.Artist
import com.mustakim.bokbok.music.db.entities.Playlist
import com.mustakim.bokbok.music.LocalDatabase
import com.mustakim.bokbok.music.LocalPlayerConnection
import com.mustakim.bokbok.music.models.toMediaMetadata
import com.mustakim.bokbok.music.innertube.models.SongItem
import com.mustakim.bokbok.music.innertube.models.AlbumItem
import com.mustakim.bokbok.music.innertube.models.ArtistItem
import com.mustakim.bokbok.music.innertube.models.PlaylistItem
import com.mustakim.bokbok.music.playback.queues.LocalAlbumRadio
import com.mustakim.bokbok.music.playback.queues.YouTubeAlbumRadio
import com.mustakim.bokbok.music.playback.queues.YouTubeQueue
import com.mustakim.bokbok.music.viewmodels.HomeViewModel
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mustakim.bokbok.music.ui.component.CreatePlaylistDialog

// Routes that are considered "main" music screens and should show the music nav bar
private val musicMainRoutes = setOf(
    Screens.Home.route,
    Screens.Search.route,
    Screens.Library.route,
    Screens.MoodAndGenres.route,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicShell(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    pureBlack: Boolean,
    latestVersionName: String,
    title: String? = null,
    showTopBar: Boolean = true,
    showToolbar: Boolean = true,
    content: @Composable (TopAppBarScrollBehavior) -> Unit,
) {
    val musicScrollBehavior = scrollBehavior
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val topLevelScreens = remember {
        setOf(
            Screens.Home.route,
            Screens.MoodAndGenres.route,
            Screens.Library.route
        )
    }
    val isTopLevelScreen = currentRoute in topLevelScreens

    val currentScreen = remember(currentRoute) {
        if (currentRoute == null) return@remember null
        try {
            Screens.MainScreens.firstOrNull { it != null && it.route == currentRoute }
        } catch (e: Exception) {
            null
        }
    }

    val displayTitle = when {
        title != null -> title
        currentScreen != null -> stringResource(currentScreen.titleId)
        currentRoute?.startsWith("search/") == true -> stringResource(MusicR.string.search)
        else -> null
    }

    val shouldUseFloatingTopBar = remember(currentRoute) {
        currentRoute == Screens.Home.route ||
                currentRoute == Screens.MoodAndGenres.route ||
                currentRoute == Screens.Library.route
    }

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val isPureBlack = isSystemInDarkTheme && pureBlack

    val homeViewModel: HomeViewModel = hiltViewModel()
    val allLocalItems by homeViewModel.allLocalItems.collectAsState()
    val allYtItems by homeViewModel.allYtItems.collectAsState()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()

    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false }
        )
    }

    val playerBottomSheetState = LocalPlayerBottomSheetState.current

    // Glassmorphic gradient colors from theme
    val surfaceColor = MaterialTheme.colorScheme.surface
    val currentScrollBehavior = musicScrollBehavior

    // Outer Box: Scaffold + BottomSheetPlayer as overlay
    // NOT in bottomBar (which pollutes innerPadding and eats content space)
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(musicScrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
        ) { _ ->
            // We ignore Scaffold's innerPadding here because the underlying music screens
            // (HomeScreen, SearchScreen, etc.) are designed to use LocalPlayerAwareWindowInsets
            // to handle their own padding for system bars, AppBarHeight, and the BottomSheetPlayer.
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                content(musicScrollBehavior)
            }
        }
    }
}

