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
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.SearchOff
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
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.TopSearch
import androidx.compose.ui.text.input.TextFieldValue

import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppManagerScreen(
    navController: androidx.navigation.NavController,
    userViewModel: UserViewModel,
    viewModel: AppManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val isFilterSheetVisible by viewModel.isFilterSheetVisible.collectAsState()
    val isSortSheetVisible by viewModel.isSortSheetVisible.collectAsState()
    val sortOrder by viewModel.sortOrder.collectAsState()
    
    var isSearchActive by remember { mutableStateOf(searchQuery.isNotEmpty()) }
    var searchTextFieldValue by remember { mutableStateOf(TextFieldValue(searchQuery)) }
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

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (uiState.selectedCount > 0) {
            // Selection Toolbar - Inline
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, "Clear", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            text = "${uiState.selectedCount} selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    
                    Row {
                        if (filterType == AppFilterType.UNINSTALLED) {
                            IconButton(onClick = { viewModel.onRestoreSelected() }) {
                                Icon(Icons.Default.Refresh, "Restore", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        } else {
                            IconButton(onClick = { viewModel.onUninstallSelected() }) {
                                Icon(Icons.Default.Delete, "Uninstall", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = { viewModel.onForceStopSelected() }) {
                                Icon(Icons.Default.Stop, "Force Stop", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            IconButton(onClick = { viewModel.onClearCacheSelected() }) {
                                Icon(Icons.Default.CleaningServices, "Clear Cache", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
            }
        } else {
            // Inline Search Bar
            androidx.compose.animation.AnimatedContent(
                targetState = isSearchActive,
                label = "SearchTransition"
            ) { active ->
                if (active) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChanged(it) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                                     Icon(Icons.Outlined.FilterAlt, "Filter")
                                 }
                                 IconButton(onClick = { viewModel.showSortSheet() }) {
                                     Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
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
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(56.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, "Search")
                        }
                        IconButton(onClick = { viewModel.showFilterSheet() }) {
                            Icon(Icons.Outlined.FilterAlt, "Filter")
                        }
                        IconButton(onClick = { viewModel.showSortSheet() }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, "Sort")
                        }
                        IconButton(onClick = { viewModel.onRefresh() }) {
                            Icon(Icons.Default.Refresh, "Refresh")
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier.weight(1f)
        ) {
            if (uiState.isLoading && uiState.apps.isEmpty()) {
                com.mustakim.bokbok.ui.shared.shimmer.ShimmerHost(modifier = Modifier.fillMaxSize()) {
                    repeat(10) {
                        com.mustakim.bokbok.ui.components.ListItemPlaceholder()
                    }
                }
            } else if (uiState.apps.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val icon = if (searchQuery.isNotEmpty()) Icons.Default.SearchOff else Icons.Default.Inbox
                    val title = if (searchQuery.isNotEmpty()) "No results found" else "List is empty"
                    val description = if (searchQuery.isNotEmpty()) 
                        "We couldn't find any app matching \"$searchQuery\". Try a different name."
                        else "No apps found for the selected filter: ${filterType.name.lowercase().replaceFirstChar { it.uppercase() }}"
                    
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    
                    if (searchQuery.isNotEmpty() || filterType != AppFilterType.ALL) {
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                viewModel.onSearchQueryChanged("")
                                viewModel.onFilterChanged(AppFilterType.ALL)
                            },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("Clear Filters & Search")
                        }
                    }
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
