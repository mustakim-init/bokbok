package com.mustakim.bokbok.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.music.constants.GridThumbnailHeight
import com.mustakim.bokbok.ui.shared.ChipsRow
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.LocalMenuState
import com.mustakim.bokbok.music.ui.component.YouTubeGridItem
import com.mustakim.bokbok.ui.shared.shimmer.GridItemPlaceHolder
import com.mustakim.bokbok.ui.shared.shimmer.ShimmerHost
import com.mustakim.bokbok.music.ui.menu.YouTubeAlbumMenu
import com.mustakim.bokbok.music.ui.menu.YouTubeArtistMenu
import com.mustakim.bokbok.music.ui.menu.YouTubePlaylistMenu
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.music.viewmodels.AccountViewModel
import com.mustakim.bokbok.music.viewmodels.AccountContentType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AccountViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val coroutineScope = rememberCoroutineScope()

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val selectedContentType by viewModel.selectedContentType.collectAsStateWithLifecycle()

    // Capture M3 Expressive colors from theme
    val color1 = MaterialTheme.colorScheme.primary
    val color2 = MaterialTheme.colorScheme.secondary
    val color3 = MaterialTheme.colorScheme.tertiary
    val color4 = MaterialTheme.colorScheme.primaryContainer
    val color5 = MaterialTheme.colorScheme.secondaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(CoreR.string.account)) },
                navigationIcon = {
                    BokBokIconButton(
                        onClick = navController::navigateUp,
                        onLongClick = navController::backToMain,
                    ) {
                        Icon(
                            painterResource(CoreR.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Mesh gradient background layer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f)
                    .align(Alignment.TopCenter)
                    .drawBehind {
                        val width = size.width
                        val height = size.height

                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(color1.copy(alpha = 0.2f), Color.Transparent),
                                center = Offset(width * 0.2f, height * 0.1f),
                                radius = width * 0.6f
                            )
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(color2.copy(alpha = 0.15f), Color.Transparent),
                                center = Offset(width * 0.8f, height * 0.3f),
                                radius = width * 0.7f
                            )
                        )
                    }
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                        )
                    ),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ChipsRow(
                        chips = listOf(
                            AccountContentType.PLAYLISTS to stringResource(CoreR.string.filter_playlists),
                            AccountContentType.ALBUMS to stringResource(CoreR.string.filter_albums),
                            AccountContentType.ARTISTS to stringResource(CoreR.string.filter_artists),
                        ),
                        currentValue = selectedContentType,
                        onValueUpdate = { viewModel.setSelectedContentType(it) },
                    )
                }

                when (selectedContentType) {
                    AccountContentType.PLAYLISTS -> {
                        items(
                            items = playlists.orEmpty().distinctBy { it.id },
                            key = { it.id },
                            contentType = { "account_playlist" }
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("online_playlist/${item.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubePlaylistMenu(
                                                    playlist = item,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    ),
                            )
                        }

                        if (playlists == null) {
                            items(8) {
                                ShimmerHost {
                                    GridItemPlaceHolder(fillMaxWidth = true)
                                }
                            }
                        }
                    }

                    AccountContentType.ALBUMS -> {
                        items(
                            items = albums.orEmpty().distinctBy { it.id },
                            key = { it.id },
                            contentType = { "account_album" }
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("album/${item.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeAlbumMenu(
                                                    albumItem = item,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                            )
                        }

                        if (albums == null) {
                            items(8) {
                                ShimmerHost {
                                    GridItemPlaceHolder(fillMaxWidth = true)
                                }
                            }
                        }
                    }

                    AccountContentType.ARTISTS -> {
                        items(
                            items = artists.orEmpty().distinctBy { it.id },
                            key = { it.id },
                            contentType = { "account_artist" }
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                fillMaxWidth = true,
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            navController.navigate("artist/${item.id}")
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeArtistMenu(
                                                    artist = item,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                            )
                        }

                        if (artists == null) {
                            items(8) {
                                ShimmerHost {
                                    GridItemPlaceHolder(fillMaxWidth = true)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
