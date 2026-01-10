package com.mustakim.bokbok.ui.screens.gameboost.usagestats

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mustakim.bokbok.viewmodel.IntervalType
import com.mustakim.bokbok.viewmodel.UsageSortOrder
import com.mustakim.bokbok.viewmodel.UsageStatsViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageStatsScreen(
    viewModel: UsageStatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle changes to refresh permission/data when returning from Settings
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // Cleanup not strictly necessary for Composable lifecycle effect, but good practice
    }

    if (!uiState.hasPermission) {
        PermissionRequestContent(onRequestPermission = { viewModel.requestPermission() })
    } else {
        val listState = rememberLazyListState()
        
        // Trigger load only once
        LaunchedEffect(Unit) {
            viewModel.loadDataIfNeeded()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Header Card (Now scrollable)
                item {
                    UsageStatsHeader(
                        totalScreenTime = uiState.totalScreenTime,
                        currentDate = uiState.currentDate,
                        intervalType = uiState.intervalType,
                        onNextDate = { viewModel.onNextDate() },
                        onPrevDate = { viewModel.onPreviousDate() }
                    )
                }

                // Controls Row (Now scrollable)
                item {
                    UsageControls(
                        intervalType = uiState.intervalType,
                        sortOrder = uiState.sortOrder,
                        onIntervalChanged = { viewModel.onIntervalChanged(it) },
                        onSortOrderChanged = { viewModel.onSortOrderChanged(it) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (uiState.isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (uiState.usageList.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No usage data available",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.usageList, key = { it.packageName }) { item ->
                        UsageStatsItem(usageInfo = item)
                    }
                }
            }
        }
    }
}

@Composable
fun UsageStatsHeader(
    totalScreenTime: Long,
    currentDate: Long,
    intervalType: IntervalType,
    onNextDate: () -> Unit,
    onPrevDate: () -> Unit
) {
    val gradient = Brush.horizontalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) // Slightly lighter/transparent
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent), // Transparent to show gradient
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradient)
                .padding(20.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Date Navigation
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = onPrevDate) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    
                    Text(
                        text = formatDate(currentDate, intervalType),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    
                    IconButton(onClick = onNextDate) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Total Time
                Box(contentAlignment = Alignment.Center) {
                    // Decorative circular back
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.1f))
                    )
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formatDurationHeader(totalScreenTime),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            text = "Screen Time",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UsageControls(
    intervalType: IntervalType,
    sortOrder: UsageSortOrder,
    onIntervalChanged: (IntervalType) -> Unit,
    onSortOrderChanged: (UsageSortOrder) -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Interval Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = intervalType == IntervalType.DAILY,
                    onClick = { onIntervalChanged(IntervalType.DAILY) },
                    label = { Text("Daily") }
                )
            }
            item {
                FilterChip(
                    selected = intervalType == IntervalType.WEEKLY,
                    onClick = { onIntervalChanged(IntervalType.WEEKLY) },
                    label = { Text("Weekly") }
                )
            }
        }
        
        // Sort Button
        Box {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                UsageSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(text = formatSortOrder(order)) },
                        onClick = {
                            onSortOrderChanged(order)
                            sortMenuExpanded = false
                        },
                        trailingIcon = if (sortOrder == order) {
                           { Icon(Icons.Default.Check, contentDescription = null) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionRequestContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Usage Access Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "To display your app usage statistics, this app needs permission to access usage data. Please grant this permission in settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text("Grant Permission")
        }
    }
}

private fun formatDurationHeader(millis: Long): String {
    val seconds = millis / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    return "${hours}h ${minutes % 60}m"
}

private fun formatDate(date: Long, interval: IntervalType): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = date
    
    val today = java.util.Calendar.getInstance()
    val isToday = calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                  calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
                  
    val yesterday = java.util.Calendar.getInstance()
    yesterday.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val isYesterday = calendar.get(java.util.Calendar.YEAR) == yesterday.get(java.util.Calendar.YEAR) &&
                      calendar.get(java.util.Calendar.DAY_OF_YEAR) == yesterday.get(java.util.Calendar.DAY_OF_YEAR)

    if (interval == IntervalType.DAILY) {
        return when {
            isToday -> "Today"
            isYesterday -> "Yesterday"
            else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(date))
        }
    } else {
        // Weekly range logic
        val startOfWeek = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(date))
        calendar.add(java.util.Calendar.DAY_OF_YEAR, 6)
        val endOfWeek = SimpleDateFormat("MMM d", Locale.getDefault()).format(calendar.time)
        return "$startOfWeek - $endOfWeek"
    }
}

private fun formatSortOrder(order: UsageSortOrder): String {
    return when (order) {
        UsageSortOrder.SCREEN_TIME -> "Screen Time"
        UsageSortOrder.TIMES_OPENED -> "Times Opened"
        UsageSortOrder.LAST_USED -> "Last Used"
        UsageSortOrder.APP_NAME -> "Name"
        UsageSortOrder.BATTERY_USAGE -> "Battery Usage"
        UsageSortOrder.DATA_USAGE -> "Data Usage"
    }
}
