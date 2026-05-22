package com.mustakim.bokbok.music.ui.screens
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.music.constants.DarkModeKey
import com.mustakim.bokbok.music.constants.PureBlackKey
import com.mustakim.bokbok.ui.shared.BottomSheet
import com.mustakim.bokbok.ui.shared.BottomSheetMenu
import com.mustakim.bokbok.ui.shared.LocalMenuState
import com.mustakim.bokbok.ui.shared.rememberBottomSheetState
import com.mustakim.bokbok.music.ui.screens.BrowseScreen
import com.mustakim.bokbok.music.ui.screens.artist.ArtistAlbumsScreen
import com.mustakim.bokbok.music.ui.screens.artist.ArtistItemsScreen
import com.mustakim.bokbok.music.ui.screens.artist.ArtistScreen
import com.mustakim.bokbok.music.ui.screens.artist.ArtistSongsScreen
import com.mustakim.bokbok.music.ui.screens.library.LibraryScreen
import com.mustakim.bokbok.music.ui.screens.playlist.AutoPlaylistScreen
import com.mustakim.bokbok.music.ui.screens.playlist.LocalPlaylistScreen
import com.mustakim.bokbok.music.ui.screens.playlist.OnlinePlaylistScreen
import com.mustakim.bokbok.music.ui.screens.playlist.TopPlaylistScreen
import com.mustakim.bokbok.music.ui.screens.playlist.CachePlaylistScreen
import com.mustakim.bokbok.music.ui.screens.search.MusicSearchScreen
import com.mustakim.bokbok.music.ui.screens.search.OnlineSearchResult
import com.mustakim.bokbok.music.ui.screens.settings.*
import com.mustakim.bokbok.music.ui.screens.settings.MusicAboutScreen
import com.mustakim.bokbok.ui.shared.SettingsPage
import com.mustakim.bokbok.music.ui.screens.settings.CustomizeBackground
import com.mustakim.bokbok.music.ui.screens.settings.BackupAndRestore
import com.mustakim.bokbok.music.ui.screens.settings.ChangelogScreen
import com.mustakim.bokbok.music.ui.screens.settings.ContentSettings
import com.mustakim.bokbok.music.ui.screens.settings.DarkMode
import com.mustakim.bokbok.music.ui.screens.settings.DiscordLoginScreen
import com.mustakim.bokbok.music.ui.screens.settings.DiscordSettings
import com.mustakim.bokbok.music.ui.screens.settings.DebugSettings
import com.mustakim.bokbok.music.ui.screens.settings.FolderExplorerScreen
import com.mustakim.bokbok.music.ui.screens.settings.IntegrationScreen
import com.mustakim.bokbok.music.ui.screens.settings.LastFMSettings
import com.mustakim.bokbok.music.ui.MusicShell
import com.mustakim.bokbok.music.constants.PureBlackKey
import com.mustakim.bokbok.data.local.rememberPreference

import com.mustakim.bokbok.music.ui.screens.settings.PalettePickerScreen
import com.mustakim.bokbok.music.ui.screens.settings.PlayerSettings
import com.mustakim.bokbok.music.ui.screens.settings.PoTokenScreen
import com.mustakim.bokbok.music.ui.screens.settings.SettingsScreen

import com.mustakim.bokbok.music.ui.screens.settings.StorageSettings
import com.mustakim.bokbok.music.ui.screens.settings.ThemeCreatorScreen
import com.mustakim.bokbok.music.ui.screens.settings.UpdateScreen
import com.mustakim.bokbok.music.ui.screens.settings.PrivacySettings
import com.mustakim.bokbok.music.ui.screens.musicrecognition.MusicRecognitionRoute
import com.mustakim.bokbok.music.ui.screens.musicrecognition.MusicRecognitionScreen
import com.mustakim.bokbok.music.ui.utils.ShowMediaInfo
import com.mustakim.bokbok.data.local.rememberEnumPreference

import androidx.compose.foundation.isSystemInDarkTheme

@Composable
fun rememberPureBlack(): Boolean {
    val (pureBlackPref) = rememberPreference(PureBlackKey, defaultValue = false)
    return pureBlackPref && isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3Api::class)
fun NavGraphBuilder.navigationBuilder(
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    latestVersionName: String,
) {
    composable(Screens.Home.route) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName) { scrollBehavior ->
            HomeScreen(navController, scrollBehavior)
        }
    }
    composable(Screens.Search.route) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false) {
            MusicSearchScreen(navController, pureBlack)
        }
    }
    composable(
        Screens.Library.route,
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName) { scrollBehavior ->
            LibraryScreen(navController, scrollBehavior)
        }
    }
    composable("history") {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { scrollBehavior ->
            HistoryScreen(navController)
        }
    }
    composable("stats") {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { scrollBehavior ->
            StatsScreen(navController)
        }
    }
    composable("year_in_music") {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { scrollBehavior ->
            YearInMusicScreen(navController)
        }
    }
    composable(MusicRecognitionRoute) {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { scrollBehavior ->
            MusicRecognitionScreen(navController)
        }
    }
    composable(Screens.MoodAndGenres.route) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName) { scrollBehavior ->
            MoodAndGenresScreen(navController, scrollBehavior)
        }
    }
    composable("account") {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { shellScrollBehavior ->
            AccountScreen(navController, shellScrollBehavior)
        }
    }
    composable("new_release") {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { shellScrollBehavior ->
            NewReleaseScreen(navController, shellScrollBehavior)
        }
    }
    composable("charts_screen") {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { scrollBehavior ->
            ChartsScreen(navController)
        }
    }
    composable(
        route = "browse/{browseId}",
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
            showToolbar = false,
        ) { shellScrollBehavior ->
            BrowseScreen(
                navController,
                shellScrollBehavior,
                backStackEntry.arguments?.getString("browseId")
            )
        }
    }
    composable(
        route = "search/{query}",
        arguments =
        listOf(
            navArgument("query") {
                type = NavType.StringType
            },
        ),
        enterTransition = {
            fadeIn(tween(250))
        },
        exitTransition = {
            if (targetState.destination.route?.startsWith("search/") == true) {
                fadeOut(tween(200))
            } else {
                fadeOut(tween(200)) + slideOutHorizontally { -it / 2 }
            }
        },
        popEnterTransition = {
            if (initialState.destination.route?.startsWith("search/") == true) {
                fadeIn(tween(250))
            } else {
                fadeIn(tween(250)) + slideInHorizontally { -it / 2 }
            }
        },
        popExitTransition = {
            fadeOut(tween(200))
        },
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName) { scrollBehavior ->
            OnlineSearchResult(navController, scrollBehavior)
        }
    }
    composable(
        route = "album/{albumId}",
        arguments =
        listOf(
            navArgument("albumId") {
                type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            AlbumScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}",
        arguments =
        listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            ArtistScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/songs",
        arguments =
        listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            ArtistSongsScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/albums",
        arguments = listOf(
            navArgument("artistId") {
                type = NavType.StringType
            }
        )
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            ArtistAlbumsScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "artist/{artistId}/items?browseId={browseId}&params={params}",
        arguments =
        listOf(
            navArgument("artistId") {
                type = NavType.StringType
            },
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            ArtistItemsScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "online_playlist/{playlistId}",
        arguments =
        listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            OnlinePlaylistScreen(navController, shellScrollBehavior)
        }
    }
    composable("local_device_music") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            com.mustakim.bokbok.music.ui.screens.playlist.LocalDeviceMusicScreen(navController)
        }
    }
    composable(
        route = "local_playlist/{playlistId}",
        arguments =
        listOf(
            navArgument("playlistId") {
                type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            LocalPlaylistScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "auto_playlist/{playlist}",
        arguments =
        listOf(
            navArgument("playlist") {
                type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            AutoPlaylistScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "cache_playlist/{playlist}",
        arguments =
            listOf(
                navArgument("playlist") {
                    type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            CachePlaylistScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "top_playlist/{top}",
        arguments =
        listOf(
            navArgument("top") {
                type = NavType.StringType
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            TopPlaylistScreen(navController, shellScrollBehavior)
        }
    }
    composable(
        route = "youtube_browse/{browseId}?params={params}",
        arguments =
        listOf(
            navArgument("browseId") {
                type = NavType.StringType
                nullable = true
            },
            navArgument("params") {
                type = NavType.StringType
                nullable = true
            },
        ),
    ) {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            YouTubeBrowseScreen(navController)
        }
    }
    composable("settings") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            SettingsScreen(navController, shellScrollBehavior, latestVersionName)
        }
    }
    composable("settings/account") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) { shellScrollBehavior ->
            AccountSettings(navController, shellScrollBehavior, latestVersionName)
        }
    }
    composable("settings/appearance") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.appearance)) { scrollBehavior ->
                AppearanceSettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/appearance/palette_picker") {
        PalettePickerScreen(navController)
    }
    composable("settings/appearance/theme_creator") {
        ThemeCreatorScreen(navController)
    }
    composable("settings/content") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.content)) { scrollBehavior ->
                ContentSettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/player") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.player)) { scrollBehavior ->
                PlayerSettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/storage") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.storage)) { scrollBehavior ->
                StorageSettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/storage/folders") {
        FolderExplorerScreen(navController)
    }
    composable("settings/privacy") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.privacy)) { scrollBehavior ->
                PrivacySettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/backup_restore") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.backup_restore)) { scrollBehavior ->
                BackupAndRestore(navController, scrollBehavior)
            }
        }
    }
    composable("settings/discord") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.discord)) { scrollBehavior ->
                DiscordSettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/integration") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.integration)) { scrollBehavior ->
                IntegrationScreen(navController, scrollBehavior)
            }
        }
    }

    composable("settings/lastfm") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.lastfm_integration)) { scrollBehavior ->
                LastFMSettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/discord/experimental") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            com.mustakim.bokbok.music.ui.screens.settings.DiscordExperimental(navController)
        }
    }

    composable("settings/misc") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.experiment_settings)) { scrollBehavior ->
                DebugSettings(navController, scrollBehavior)
            }
        }
    }
    composable("settings/update") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.updates)) { scrollBehavior ->
                UpdateScreen(navController, scrollBehavior)
            }
        }
    }
    composable("settings/changelog") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.changelog)) { scrollBehavior ->
                ChangelogScreen(navController, scrollBehavior)
            }
        }
    }
    composable("settings/discord/login") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.discord_integration)) { scrollBehavior ->
                DiscordLoginScreen(navController)
            }
        }
    }
    composable("settings/about") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.about)) { scrollBehavior ->
                MusicAboutScreen(navController, scrollBehavior)
            }
        }
    }
    composable("settings/po_token") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            SettingsPage(navController, stringResource(CoreR.string.po_token_generation)) { scrollBehavior ->
                PoTokenScreen(navController, scrollBehavior)
            }
        }
    }
    composable("customize_background") {
        val pureBlack = rememberPureBlack()
        MusicShell(navController = navController, scrollBehavior = scrollBehavior, pureBlack = pureBlack, latestVersionName = latestVersionName, showTopBar = false, showToolbar = false) {
            CustomizeBackground(navController)
        }
    }
    composable(
        route = "$LOGIN_ROUTE?$LOGIN_URL_ARGUMENT={$LOGIN_URL_ARGUMENT}",
        arguments = listOf(
            navArgument(LOGIN_URL_ARGUMENT) {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }
        )
    ) { backStackEntry ->
        LoginScreen(
            navController,
            startUrl = backStackEntry.arguments?.getString(LOGIN_URL_ARGUMENT)?.let(Uri::decode)
        )
    }
    composable("music_about") {
        val pureBlack = rememberPureBlack()
        MusicShell(
            navController = navController,
            scrollBehavior = scrollBehavior,
            pureBlack = pureBlack,
            latestVersionName = latestVersionName,
            showTopBar = false,
        ) { shellScrollBehavior ->
            MusicAboutScreen(navController, shellScrollBehavior)
        }
    }
}
