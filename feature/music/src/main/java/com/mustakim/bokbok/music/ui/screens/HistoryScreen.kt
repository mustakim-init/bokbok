package com.mustakim.bokbok.music.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.rememberPreference
import com.mustakim.bokbok.music.LocalDatabase
import com.mustakim.bokbok.music.LocalPlayerConnection
import com.mustakim.bokbok.music.constants.HistorySource
import com.mustakim.bokbok.music.constants.InnerTubeCookieKey
import com.mustakim.bokbok.music.db.entities.EventWithSong
import com.mustakim.bokbok.music.extensions.toMediaItem
import com.mustakim.bokbok.music.models.toMediaMetadata
import com.mustakim.bokbok.music.innertube.models.WatchEndpoint
import com.mustakim.bokbok.music.innertube.utils.parseCookieString
import com.mustakim.bokbok.music.playback.queues.ListQueue
import com.mustakim.bokbok.music.playback.queues.YouTubeQueue
import com.mustakim.bokbok.music.ui.component.HideOnScrollFAB
import com.mustakim.bokbok.music.ui.component.SongListItem
import com.mustakim.bokbok.music.ui.component.YouTubeListItem
import com.mustakim.bokbok.music.ui.menu.SelectionMediaMetadataMenu
import com.mustakim.bokbok.music.ui.menu.SongMenu
import com.mustakim.bokbok.music.ui.menu.YouTubeSongMenu
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.music.viewmodels.DateAgo
import com.mustakim.bokbok.music.viewmodels.HistoryViewModel
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.ChipsRow
import com.mustakim.bokbok.ui.shared.LocalMenuState
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets
import com.mustakim.bokbok.ui.shared.NavigationTitle
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    var selection by remember {
        mutableStateOf(false)
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }
    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    val historySource by viewModel.historySource.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val historyPage by viewModel.historyPage

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn = remember(innerTubeCookie) {
        "SAPISID" in parseCookieString(innerTubeCookie)
    }

    class WrappedHistoryItem(val item: EventWithSong) {
        var isSelected by mutableStateOf(false)
    }

    val filteredEvents = remember(events, query) {
        if (query.text.isEmpty()) {
            events
        } else {
            events.mapValues { (_, songs) ->
                songs.filter { event ->
                    event.song.song.title.contains(query.text, ignoreCase = true) ||
                            event.song.artists.any {
                                it.name.contains(
                                    query.text,
                                    ignoreCase = true
                                )
                            }
                }
            }.filterValues { it.isNotEmpty() }
        }
    }

    val filteredRemoteContent = remember(historyPage, query) {
        if (query.text.isEmpty()) {
            historyPage?.sections
        } else {
            historyPage?.sections?.map { section ->
                section.copy(
                    songs = section.songs.filter { song ->
                        song.title.contains(query.text, ignoreCase = true) ||
                                song.artists.any { it.name.contains(query.text, ignoreCase = true) }
                    }
                )
            }?.filter { it.songs.isNotEmpty() }
        }
    }

    val wrappedItemsMap = remember(filteredEvents) {
        filteredEvents.mapValues { (_, events) ->
            events.map { WrappedHistoryItem(it) }.toMutableStateList()
        }
    }

    val allWrappedItems = remember(wrappedItemsMap) {
        wrappedItemsMap.values.flatten()
    }

    val lazyListState = rememberLazyListState()

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    if (selection) {
                        val count = allWrappedItems.count { it.isSelected }
                        Text(
                            text = pluralStringResource(MusicR.plurals.n_song, count, count),
                            style = MaterialTheme.typography.titleLarge
                        )
                    } else if (isSearching) {
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = {
                                Text(
                                    text = stringResource(MusicR.string.search),
                                    style = MaterialTheme.typography.titleLarge
                                )
                            },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleLarge,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                        )
                    } else {
                        Text(stringResource(MusicR.string.history))
                    }
                },
                navigationIcon = {
                    BokBokIconButton(
                        onClick = {
                            when {
                                isSearching -> {
                                    isSearching = false
                                    query = TextFieldValue()
                                }

                                selection -> {
                                    selection = false
                                }

                                else -> {
                                    navController.navigateUp()
                                }
                            }
                        },
                        onLongClick = {
                            if (!isSearching && !selection) {
                                navController.backToMain()
                            }
                        },
                    ) {
                        Icon(
                            painterResource(
                                if (selection || isSearching) CoreR.drawable.close else CoreR.drawable.arrow_back
                            ),
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (selection) {
                        val count = allWrappedItems.count { it.isSelected }
                        IconButton(
                            onClick = {
                                if (count == allWrappedItems.size) {
                                    allWrappedItems.forEach { it.isSelected = false }
                                } else {
                                    allWrappedItems.forEach { it.isSelected = true }
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (count == allWrappedItems.size) CoreR.drawable.deselect else CoreR.drawable.select_all
                                ),
                                contentDescription = null
                            )
                        }

                        IconButton(
                            onClick = {
                                menuState.show {
                                    val songSelection = allWrappedItems
                                        .filter { it.isSelected }
                                        .map { it.item.song.toMediaMetadata() }
                                    SelectionMediaMetadataMenu(
                                        songSelection = songSelection,
                                        onDismiss = menuState::dismiss,
                                        clearAction = { selection = false },
                                        currentItems = emptyList()
                                    )
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.more_vert),
                                contentDescription = null
                            )
                        }
                    } else if (!isSearching) {
                        IconButton(
                            onClick = { isSearching = true }
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.search),
                                contentDescription = null
                            )
                        }
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    ChipsRow(
                        chips = if (isLoggedIn) listOf(
                            HistorySource.LOCAL to stringResource(CoreR.string.local_history),
                            HistorySource.REMOTE to stringResource(CoreR.string.remote_history),
                        ) else {
                            listOf(HistorySource.LOCAL to stringResource(CoreR.string.local_history))
                        },
                        currentValue = historySource,
                        onValueUpdate = {
                            viewModel.historySource.value = it
                            if (it == HistorySource.REMOTE) {
                                viewModel.fetchRemoteHistory()
                            }
                        }
                    )
                }

                if (historySource == HistorySource.REMOTE && isLoggedIn) {
                    filteredRemoteContent?.forEach { section ->
                        stickyHeader {
                            NavigationTitle(
                                title = section.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }

                        items(
                            items = section.songs,
                            key = { "${section.title}_${it.id}_${section.songs.indexOf(it)}" },
                            contentType = { "song" }
                        ) { song ->
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(CoreR.drawable.more_vert),
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            playerConnection.playQueue(
                                                YouTubeQueue(
                                                    endpoint = WatchEndpoint(videoId = song.id),
                                                    preloadItem = song.toMediaMetadata()
                                                )
                                            )
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                YouTubeSongMenu(
                                                    song = song,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        }
                                    )
                            )
                        }
                    }
                } else {
                    wrappedItemsMap.forEach { (dateAgo, events) ->
                        stickyHeader {
                            NavigationTitle(
                                title = when (dateAgo) {
                                    DateAgo.Today -> stringResource(CoreR.string.today)
                                    DateAgo.Yesterday -> stringResource(CoreR.string.yesterday)
                                    DateAgo.ThisWeek -> stringResource(CoreR.string.this_week)
                                    DateAgo.LastWeek -> stringResource(CoreR.string.last_week)
                                    is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background)
                            )
                        }

                        itemsIndexed(
                            items = events,
                            key = { _, item -> item.item.event.id },
                            contentType = { _, _ -> "song" }
                        ) { index, item ->
                            SongListItem(
                                song = item.item.song,
                                isActive = item.item.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                isSelected = item.isSelected && selection,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            if (!selection) {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = item.item.song,
                                                        event = item.item.event,
                                                        navController = navController,
                                                        onDismiss = menuState::dismiss
                                                    )
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            painter = painterResource(CoreR.drawable.more_vert),
                                            contentDescription = null
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .combinedClickable(
                                        onClick = {
                                            if (selection) {
                                                item.isSelected = !item.isSelected
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = context.getString(MusicR.string.history),
                                                        items = events
                                                            .drop(index)
                                                            .map { it.item.song.toMediaItem() }
                                                    )
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selection = true
                                            item.isSelected = true
                                        }
                                    )
                                    .background(if (item.isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent)
                            )
                        }
                    }
                }
            }

            HideOnScrollFAB(
                visible = !selection && allWrappedItems.isNotEmpty(),
                lazyListState = lazyListState,
                icon = CoreR.drawable.play,
                onClick = {
                    if (isLoggedIn) {
                        playerConnection.playQueue(
                            YouTubeQueue(
                                endpoint = WatchEndpoint(
                                    playlistId = "RDAMVM${allWrappedItems.random().item.song.id}"
                                )
                            )
                        )
                    } else {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(MusicR.string.history),
                                items = allWrappedItems.map { it.item.song.toMediaItem() }.shuffled()
                            )
                        )
                    }
                }
            )
        }
    }
}
