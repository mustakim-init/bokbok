package com.mustakim.bokbok.ui.screens.gameboost.games

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mustakim.bokbok.viewmodel.GameSpaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    viewModel: GameSpaceViewModel
) {
    val games by viewModel.filteredGames.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    
    val selectedGames by viewModel.selectedGames.collectAsState()
    val isSelectionMode by viewModel.isSelectionMode.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var isSearchActive by remember { mutableStateOf(searchQuery.isNotEmpty()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Contextual Header (Selection or Search)
            AnimatedContent(
                targetState = isSelectionMode,
                label = "HeaderTransition"
            ) { selectionActive ->
                if (selectionActive) {
                    // Selection Mode Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.clearSelection() }) {
                                Icon(Icons.Default.Close, null)
                            }
                            Spacer(Modifier.padding(horizontal = 4.dp))
                            Text(
                                text = "${selectedGames.size} selected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    // Intelligent Search Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedContent(
                            targetState = isSearchActive,
                            modifier = Modifier.weight(1f),
                            label = "SearchFieldTransition"
                        ) { searchActive ->
                            if (searchActive) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Search your games...") },
                                    leadingIcon = {
                                        IconButton(onClick = { 
                                            isSearchActive = false
                                            viewModel.onSearchQueryChanged("")
                                        }) {
                                            Icon(Icons.Default.Close, null)
                                        }
                                    },
                                    singleLine = true,
                                    shape = CircleShape,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    )
                                )
                            } else {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "My Games",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Default.Search, null)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (games.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No games found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Add your favorite games manually",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp) // FAB room
                ) {
                    items(games, key = { it.packageName }) { game ->
                        GameListItem(
                            game = game,
                            isSelected = selectedGames.contains(game.packageName),
                            isSelectionMode = isSelectionMode,
                            onClick = { viewModel.selectGame(game) },
                            onLongClick = { viewModel.enterSelectionMode(game.packageName) },
                            onLaunchClick = { viewModel.launchGame(game) }
                        )
                    }
                }
            }
        }

        // FAB pinned to bottom end of the Box
        if (!isSelectionMode) {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Add Game") }
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove from Game Space?") },
            text = { Text("Selected games will be removed from your Game Space. If they were hidden from the launcher, they will be restored.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeSelectedGames()
                        showDeleteConfirm = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("REMOVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    if (showAddDialog) {
        AddGameDialog(
            onDismiss = { showAddDialog = false },
            onAddGame = { pkg -> 
                viewModel.addGameManually(pkg)
                showAddDialog = false
            }
        )
    }
}
