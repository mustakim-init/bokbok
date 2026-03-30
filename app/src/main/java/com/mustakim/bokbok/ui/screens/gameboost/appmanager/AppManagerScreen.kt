package com.mustakim.bokbok.ui.screens.gameboost.appmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.mustakim.bokbok.ui.screens.gameboost.appmanager.components.AppFilterBottomSheet
import com.mustakim.bokbok.ui.screens.gameboost.appmanager.components.AppSortBottomSheet
import com.mustakim.bokbok.viewmodel.AppFilterType
import com.mustakim.bokbok.viewmodel.AppManagerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    navController: androidx.navigation.NavController,
    viewModel: AppManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val isFilterSheetVisible by viewModel.isFilterSheetVisible.collectAsState()
    val isSortSheetVisible by viewModel.isSortSheetVisible.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    val listState = rememberLazyListState()
    
    // Bottom Sheets
    if (isFilterSheetVisible) {
        AppFilterBottomSheet(
            selectedFilter = filterType,
            onFilterSelected = { 
                viewModel.onFilterChanged(it)
                viewModel.hideFilterSheet()
            },
            onDismiss = { viewModel.hideFilterSheet() },
            bloatwareCount = uiState.bloatwareCount,
            safeToRemoveCount = uiState.safeToRemoveCount
        )
    }

    if (isSortSheetVisible) {
        AppSortBottomSheet(
            selectedOrder = sortOrder,
            onOrderSelected = {
                viewModel.onSortOrderChanged(it)
                viewModel.hideSortSheet()
            },
            onDismiss = { viewModel.hideSortSheet() }
        )
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
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = isSearchActive,
                    label = "SearchTransition"
                ) { active ->
                    if (active) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.onSearchQueryChanged(it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search apps...") },
                            leadingIcon = {
                                IconButton(onClick = { 
                                    isSearchActive = false
                                    viewModel.onSearchQueryChanged("")
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Search")
                                }
                            },
                            trailingIcon = {
                                Row {
                                    IconButton(onClick = { viewModel.showFilterSheet() }) {
                                        Icon(
                                            imageVector = Icons.Outlined.FilterAlt,
                                            contentDescription = "Filter"
                                        )
                                    }
                                    IconButton(onClick = { viewModel.showSortSheet() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "Sort"
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = CircleShape,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                                text = "App Manager",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            
                                Row {
                                    IconButton(onClick = { isSearchActive = true }) {
                                        Icon(Icons.Default.Search, contentDescription = "Search")
                                    }
                                    IconButton(onClick = { viewModel.showFilterSheet() }) {
                                        Icon(
                                            imageVector = Icons.Outlined.FilterAlt,
                                            contentDescription = "Filter"
                                        )
                                    }
                                    IconButton(onClick = { viewModel.showSortSheet() }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = "Sort"
                                        )
                                    }
                                }
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
                PullToRefreshBox(
                    isRefreshing = uiState.isLoading,
                    onRefresh = { viewModel.onRefresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
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
                                    else navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.AppDetails.createRoute(app.packageName))
                                },
                                onLongClick = { viewModel.onAppLongClicked(app) }
                            )
                        }
                    }
                }
            }
        }
    }
}

