package com.mustakim.bokbok.ui.screens.gameboost.appmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mustakim.bokbok.viewmodel.AppFilterType
import com.mustakim.bokbok.viewmodel.AppManagerViewModel
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    viewModel: AppManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    
    // Show App Details Screen if an app is selected
    selectedApp?.let { app ->
        AppDetailsScreen(
            app = app,
            repository = viewModel.getRepository(),
            onBack = { viewModel.clearSelectedApp() },
            onAppUninstalled = {
                viewModel.clearSelectedApp()
                viewModel.onRefresh()
            }
        )
        return@AppManagerScreen
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.selectedCount > 0) {
            // Selection Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection")
                    }
                    Text(
                        text = "${uiState.selectedCount} selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row {
                    if (filterType == AppFilterType.UNINSTALLED) {
                        IconButton(onClick = { viewModel.onRestoreSelected() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restore")
                        }
                    } else {
                        IconButton(onClick = { viewModel.onUninstallSelected() }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Uninstall")
                        }
                        IconButton(onClick = { viewModel.onForceStopSelected() }) {
                            Icon(imageVector = Icons.Default.Stop, contentDescription = "Force Stop")
                        }
                        IconButton(onClick = { viewModel.onClearCacheSelected() }) {
                            Icon(imageVector = Icons.Default.CleaningServices, contentDescription = "Clear Cache")
                        }
                    }
                }
            }
        } else {
            // Search and Toolbar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                         Row {
                             IconButton(onClick = { showFilters = !showFilters }) {
                                 Icon(
                                     imageVector = if (showFilters) Icons.Default.FilterList else Icons.AutoMirrored.Filled.Sort,
                                     contentDescription = "Filter"
                                 )
                             }
                             IconButton(onClick = { viewModel.onRefresh() }) {
                                 Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                             }
                         }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                )
                
                if (showFilters) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        item {
                            FilterChip(
                                selected = filterType == AppFilterType.ALL,
                                onClick = { viewModel.onFilterChanged(AppFilterType.ALL) },
                                label = { Text("All") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == AppFilterType.USER,
                                onClick = { viewModel.onFilterChanged(AppFilterType.USER) },
                                label = { Text("User") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == AppFilterType.SYSTEM,
                                onClick = { viewModel.onFilterChanged(AppFilterType.SYSTEM) },
                                label = { Text("System") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == AppFilterType.BLOATWARE,
                                onClick = { viewModel.onFilterChanged(AppFilterType.BLOATWARE) },
                                label = { Text("Bloatware (${uiState.bloatwareCount})") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == AppFilterType.SAFE_TO_REMOVE,
                                onClick = { viewModel.onFilterChanged(AppFilterType.SAFE_TO_REMOVE) },
                                label = { Text("Safe Remove (${uiState.safeToRemoveCount})") }
                            )
                        }
                        item {
                            FilterChip(
                                selected = filterType == AppFilterType.UNINSTALLED,
                                onClick = { viewModel.onFilterChanged(AppFilterType.UNINSTALLED) },
                                label = { Text("Recycle Bin") },
                                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }
                }
            }
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.apps.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No apps found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(
                        items = uiState.apps,
                        key = { it.packageName }
                    ) { app ->
                        AppListItem(
                            app = app,
                            onClick = { 
                                if (uiState.selectedCount > 0) viewModel.onAppLongClicked(app)
                                else viewModel.onAppClicked(app)
                            },
                            onLongClick = { viewModel.onAppLongClicked(app) }
                        )
                    }
                }
            }
        }
    }
}

