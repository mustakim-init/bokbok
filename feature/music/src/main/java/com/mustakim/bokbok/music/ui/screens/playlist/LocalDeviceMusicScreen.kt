package com.mustakim.bokbok.music.ui.screens.playlist
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import androidx.compose.foundation.background

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets
import com.mustakim.bokbok.music.LocalPlayerConnection
import com.mustakim.bokbok.music.db.entities.Song
import com.mustakim.bokbok.music.extensions.toMediaItem
import com.mustakim.bokbok.music.extensions.togglePlayPause
import com.mustakim.bokbok.music.models.MediaMetadata
import com.mustakim.bokbok.music.playback.queues.ListQueue
import com.mustakim.bokbok.music.ui.component.SongListItem
import com.mustakim.bokbok.music.ui.menu.SongMenu
import com.mustakim.bokbok.music.viewmodels.HomeViewModel
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.LocalMenuState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalDeviceMusicScreen(
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val localDeviceSongs by viewModel.fullLocalDeviceSongs.collectAsState()
    val localSongsLazyPagingItems = viewModel.localSongsPager.collectAsLazyPagingItems()

    val listState = rememberLazyListState()

    fun playFromIndex(index: Int) {
        if (localDeviceSongs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = "Local Music",
                items = localDeviceSongs.map { it.toMediaItem() },
                startIndex = index,
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        LazyColumn(
            state = listState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) {
            // Sticky header with play-all action
            stickyHeader {
                TopAppBar(
                    title = {
                        Text(
                            text = "Local Music",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    navigationIcon = {
                        BokBokIconButton(
                            onClick = { navController.navigateUp() },
                            modifier = Modifier.windowInsetsPadding(
                                WindowInsets.systemBars.only(WindowInsetsSides.Start)
                            )
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (localDeviceSongs.isNotEmpty()) {
                            BokBokIconButton(
                                onClick = { playFromIndex(0) },
                                modifier = Modifier.windowInsetsPadding(
                                    WindowInsets.systemBars.only(WindowInsetsSides.End)
                                )
                            ) {
                                Icon(
                                    painter = painterResource(CoreR.drawable.play),
                                    contentDescription = "Play all",
                                )
                            }
                        }
                    },
                    scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }

            if (localDeviceSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        Text(
                            text = "No local music found",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(
                count = localSongsLazyPagingItems.itemCount,
                key = localSongsLazyPagingItems.itemKey { it.id }
            ) { index ->
                val song = localSongsLazyPagingItems[index] ?: return@items
                val isActive = song.id == mediaMetadata?.id
                SongListItem(
                    song = song,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    isSwipeable = false,
                    trailingContent = {
                        BokBokIconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.more_vert),
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (isActive) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playFromIndex(index)
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        )
                        .animateItem(),
                )
            }
        }
    }
}
