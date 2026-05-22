package com.mustakim.bokbok.music.ui.screens.search
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR

import com.mustakim.bokbok.ui.shared.ChipsRow
import com.mustakim.bokbok.ui.shared.EmptyPlaceholder
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.LocalMenuState

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mustakim.bokbok.music.LocalPlayerConnection

import com.mustakim.bokbok.music.constants.CONTENT_TYPE_LIST
import com.mustakim.bokbok.music.db.entities.Album
import com.mustakim.bokbok.music.db.entities.Artist
import com.mustakim.bokbok.music.db.entities.Playlist
import com.mustakim.bokbok.music.db.entities.Song
import com.mustakim.bokbok.music.extensions.toMediaItem
import com.mustakim.bokbok.music.extensions.togglePlayPause
import com.mustakim.bokbok.music.playback.queues.ListQueue
import com.mustakim.bokbok.music.ui.component.*
import com.mustakim.bokbok.music.ui.menu.SongMenu
import com.mustakim.bokbok.music.viewmodels.LocalFilter
import com.mustakim.bokbok.music.viewmodels.LocalSearchViewModel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    pureBlack: Boolean,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        Surface(
            color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
            tonalElevation = if (pureBlack) 0.dp else 0.dp,
            shadowElevation = if (pureBlack) 0.dp else 1.dp,
        ) {
            ChipsRow(
                chips = listOf(
                    LocalFilter.ALL to stringResource(MusicR.string.filter_all),
                    LocalFilter.SONG to stringResource(MusicR.string.filter_songs),
                    LocalFilter.ALBUM to stringResource(MusicR.string.filter_albums),
                    LocalFilter.ARTIST to stringResource(MusicR.string.filter_artists),
                    LocalFilter.PLAYLIST to stringResource(MusicR.string.filter_playlists),
                ),
                currentValue = searchFilter,
                onValueUpdate = { viewModel.filter.value = it },
                icons = mapOf(
                    LocalFilter.ALL to CoreR.drawable.search,
                    LocalFilter.SONG to CoreR.drawable.music_note,
                    LocalFilter.ALBUM to CoreR.drawable.album,
                    LocalFilter.ARTIST to CoreR.drawable.person,
                    LocalFilter.PLAYLIST to CoreR.drawable.queue_music,
                ),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            result.map.forEach { (filter, items) ->
                if (result.filter == LocalFilter.ALL) {
                    item(key = filter) {
                        val filterIcon = when (filter) {
                            LocalFilter.SONG -> CoreR.drawable.music_note
                            LocalFilter.ALBUM -> CoreR.drawable.album
                            LocalFilter.ARTIST -> CoreR.drawable.person
                            LocalFilter.PLAYLIST -> CoreR.drawable.queue_music
                            LocalFilter.ALL -> CoreR.drawable.search
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.filter.value = filter }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = if (pureBlack) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            ) {
                                Icon(
                                    painter = painterResource(filterIcon),
                                    contentDescription = null,
                                    tint = if (pureBlack) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Text(
                                text = stringResource(
                                    when (filter) {
                                        LocalFilter.SONG -> MusicR.string.filter_songs
                                        LocalFilter.ALBUM -> MusicR.string.filter_albums
                                        LocalFilter.ARTIST -> MusicR.string.filter_artists
                                        LocalFilter.PLAYLIST -> MusicR.string.filter_playlists
                                        LocalFilter.ALL -> error("")
                                    }
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pureBlack) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )

                            Icon(
                                painter = painterResource(CoreR.drawable.navigate_next),
                                contentDescription = null,
                                tint = if (pureBlack) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }

                items(
                    items = items.distinctBy { it.id },
                    key = { it.id },
                    contentType = { CONTENT_TYPE_LIST },
                ) { item ->
                    when (item) {
                        is Song -> SongListItem(
                            song = item,
                            showInLibraryIcon = true,
                            isActive = item.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            trailingContent = {
                                BokBokIconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = item,
                                                navController = navController,
                                                onDismiss = {
                                                    onDismiss()
                                                    menuState.dismiss()
                                                },
                                                isFromCache = isFromCache
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
                                .combinedClickable(
                                    onClick = {
                                        if (item.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            val songs = result.map
                                                .getOrDefault(LocalFilter.SONG, emptyList())
                                                .filterIsInstance<Song>()
                                                .map { it.toMediaItem() }
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(MusicR.string.queue_searched_songs),
                                                    items = songs,
                                                    startIndex = songs.indexOfFirst { it.mediaId == item.id },
                                                )
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = item,
                                                navController = navController,
                                                onDismiss = {
                                                    onDismiss()
                                                    menuState.dismiss()
                                                },
                                                isFromCache = isFromCache
                                            )
                                        }
                                    }
                                )
                                .animateItem(),
                        )

                        is Album -> AlbumListItem(
                            album = item,
                            isActive = item.id == mediaMetadata?.album?.id,
                            isPlaying = isPlaying,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("album/${item.id}")
                                }
                                .animateItem(),
                        )

                        is Artist -> ArtistListItem(
                            artist = item,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("artist/${item.id}")
                                }
                                .animateItem(),
                        )

                        is Playlist -> PlaylistListItem(
                            playlist = item,
                            modifier = Modifier
                                .clickable {
                                    onDismiss()
                                    navController.navigate("local_playlist/${item.id}")
                                }
                                .animateItem(),
                        )
                    }
                }
            }

            if (result.query.isNotEmpty() && result.map.isEmpty()) {
                item(key = "no_result") {
                    EmptyPlaceholder(
                        icon = CoreR.drawable.search,
                        text = stringResource(MusicR.string.no_results_found),
                    )
                }
            }
        }
    }
}