package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets
import com.mustakim.bokbok.music.viewmodels.FolderExplorerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderExplorerScreen(
    navController: NavController,
    viewModel: FolderExplorerViewModel = hiltViewModel()
) {
    val currentPath by viewModel.currentPath.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val allowedFolders by viewModel.allowedFolders.collectAsState()
    val blockedFolders by viewModel.blockedFolders.collectAsState()

    BackHandler {
        if (!viewModel.navigateUp()) {
            navController.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(MusicR.string.music_folders),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = currentPath.replace("/storage/emulated/0", "Internal Storage"),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                    }
                }
            )
        },
        contentWindowInsets = LocalPlayerAwareWindowInsets.current
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(folders) { folder ->
                FolderItem(
                    folder = folder,
                    isAllowed = allowedFolders.contains(folder.absolutePath),
                    isBlocked = blockedFolders.contains(folder.absolutePath),
                    onFolderClick = { viewModel.loadFolders(folder.absolutePath) },
                    onToggleAllowed = { viewModel.toggleAllowed(folder.absolutePath) },
                    onToggleBlocked = { viewModel.toggleBlocked(folder.absolutePath) }
                )
            }
        }
    }
}

@Composable
fun FolderItem(
    folder: File,
    isAllowed: Boolean,
    isBlocked: Boolean,
    onFolderClick: () -> Unit,
    onToggleAllowed: () -> Unit,
    onToggleBlocked: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = folder.name,
                fontWeight = if (isAllowed || isBlocked) FontWeight.Bold else FontWeight.Normal,
                color = if (isAllowed) MaterialTheme.colorScheme.primary 
                        else if (isBlocked) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
            )
        },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (isAllowed) MaterialTheme.colorScheme.primary 
                       else if (isBlocked) MaterialTheme.colorScheme.error
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onToggleAllowed) {
                    Icon(
                        imageVector = if (isAllowed) Icons.Default.CheckCircle else Icons.Default.AddCircleOutline,
                        contentDescription = "Allow",
                        tint = if (isAllowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggleBlocked) {
                    Icon(
                        imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.RemoveCircleOutline,
                        contentDescription = "Block",
                        tint = if (isBlocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        modifier = Modifier.clickable(onClick = onFolderClick)
    )
}
