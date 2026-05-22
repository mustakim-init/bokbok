@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mustakim.bokbok.music.ui.screens.playlist
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.mustakim.bokbok.music.LocalDatabase
import com.mustakim.bokbok.music.LocalPlayerConnection

import com.mustakim.bokbok.music.extensions.toMediaItem
import com.mustakim.bokbok.music.extensions.togglePlayPause
import com.mustakim.bokbok.music.innertube.models.SongItem
import com.mustakim.bokbok.music.playback.queues.ListQueue
import com.mustakim.bokbok.ui.shared.DefaultDialog
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.NavigationTitle
import com.mustakim.bokbok.music.ui.component.YouTubeListItem
import com.mustakim.bokbok.music.viewmodels.LocalPlaylistViewModel

@Composable
fun PlaylistSuggestionsSection(
    modifier: Modifier = Modifier,
    viewModel: LocalPlaylistViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val isPlaying by playerConnection?.isPlaying?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(false)
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsState() ?: androidx.compose.runtime.mutableStateOf(null)
    
    val playlistSuggestions by viewModel.playlistSuggestions.collectAsState()
    val isLoading by viewModel.isLoadingSuggestions.collectAsState()
    
    // State for duplicate check dialog
    var showDuplicateDialog by remember { mutableStateOf(false) }
    var songToCheck by remember { mutableStateOf<SongItem?>(null) }
    
    val currentSuggestions = playlistSuggestions
    if (currentSuggestions == null && !isLoading) return
    if (currentSuggestions != null && currentSuggestions.items.isEmpty() && !isLoading) return

    // Duplicate Check Dialog
    if (showDuplicateDialog && songToCheck != null) {
        val song = songToCheck!!
        DefaultDialog(
            title = { Text(stringResource(MusicR.string.duplicates)) },
            buttons = {
                TextButton(
                    onClick = {
                        showDuplicateDialog = false
                        songToCheck = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            // Add to current playlist anyway
                            val browseId = viewModel.playlist.value?.playlist?.browseId
                            viewModel.addSongToPlaylist(song, browseId)
                            
                            val playlistName = viewModel.playlist.value?.playlist?.name
                            val message = if (playlistName != null) {
                                context.getString(MusicR.string.added_to_playlist, playlistName)
                            } else {
                                context.getString(MusicR.string.add_to_playlist)
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        }
                        showDuplicateDialog = false
                        songToCheck = null
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(MusicR.string.add_anyway))
                }
            },
            onDismiss = {
                showDuplicateDialog = false
                songToCheck = null
            }
        ) {
            Text(text = stringResource(MusicR.string.duplicates_description_single))
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavigationTitle(
                title = stringResource(MusicR.string.you_might_like),
                subtitle = currentSuggestions?.let { s ->
                    if (s.totalQueries > 1) {
                        "${s.currentQueryIndex + 1} / ${s.totalQueries}"
                    } else null
                },
                modifier = Modifier.weight(1f)
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        currentSuggestions?.let { suggestions ->
            // Suggestions List (Vertical)
            suggestions.items.forEach { item ->
                YouTubeListItem(
                    item = item,
                    isActive = item.id == mediaMetadata?.id,
                    isPlaying = isPlaying == true,
                    trailingContent = {
                        BokBokIconButton(
                            onClick = { 
                                val songItem = item as? SongItem
                                if (songItem != null) {
                                    // Check for duplicates in current playlist first
                                    songToCheck = songItem
                                    coroutineScope.launch {
                                        val isDuplicate = withContext(Dispatchers.IO) {
                                            val duplicates = database.playlistDuplicates(
                                                viewModel.playlistId,
                                                listOf(songItem.id)
                                            )
                                            duplicates.isNotEmpty()
                                        }
                                        
                                        if (isDuplicate) {
                                            showDuplicateDialog = true
                                        } else {
                                            // No duplicate, add directly
                                            val browseId = viewModel.playlist.value?.playlist?.browseId
                                            val success = viewModel.addSongToPlaylist(
                                                song = songItem,
                                                browseId = browseId
                                            )
                                            
                                            if (success) {
                                                val playlistName = viewModel.playlist.value?.playlist?.name
                                                val message = if (playlistName != null) {
                                                    context.getString(MusicR.string.added_to_playlist, playlistName)
                                                } else {
                                                    context.getString(MusicR.string.add_to_playlist)
                                                }
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, MusicR.string.error_unknown, Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, MusicR.string.error_unknown, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onLongClick = {}
                        ) {
                            Icon(
                                painter = painterResource(CoreR.drawable.playlist_add),
                                contentDescription = stringResource(MusicR.string.add_to_playlist)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable {
                            if (playerConnection == null) return@clickable
                            if (item.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                if (item is SongItem) {
                                    val songItems = suggestions.items.filterIsInstance<SongItem>()
                                    val startIndex = songItems.indexOfFirst { it.id == item.id }
                                    if (startIndex != -1) {
                                        playerConnection.playQueue(
                                            ListQueue(
                                                title = context.getString(MusicR.string.you_might_like),
                                                items = songItems.map { it.toMediaItem() },
                                                startIndex = startIndex
                                            )
                                        )
                                    }
                                }
                            }
                        }
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    TextButton(
                        onClick = { viewModel.resetAndLoadPlaylistSuggestions() },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(CoreR.drawable.sync),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(text = stringResource(MusicR.string.refresh_suggestions))
                        }
                    }
                }
            }
        }
    }
}
