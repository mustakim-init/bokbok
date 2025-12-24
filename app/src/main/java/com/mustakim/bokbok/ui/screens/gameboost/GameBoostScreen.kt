package com.mustakim.bokbok.ui.screens.gameboost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.ui.theme.GoogleSansFlex
import com.mustakim.bokbok.viewmodel.GameBoostTab
import com.mustakim.bokbok.viewmodel.GameBoostViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.ui.screens.gameboost.appmanager.AppManagerScreen
import com.mustakim.bokbok.ui.screens.gameboost.usagestats.UsageStatsScreen
import com.mustakim.bokbok.ui.screens.gameboost.devicemonitor.DeviceMonitorScreen
import com.mustakim.bokbok.ui.screens.gameboost.screenrecord.ScreenRecordTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoostScreen(
    navController: NavHostController,
    userViewModel: UserViewModel,
    viewModel: GameBoostViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val tabs = remember { viewModel.tabs }
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    
    // ✅ SCOPE PERSISTENCE: Scope ViewModels to the 'game_boost' route.
    // This ensures they stay alive in the background even when 'disposed' by the pager UI.
    // Crucially, we keep them lazy by instantiating them only when the tab is first visited.
    val viewModelStoreOwner = remember(navController) {
        navController.getBackStackEntry(com.mustakim.bokbok.ui.navigation.NavRoutes.GameBoost.route)
    }

    val deviceMonitorViewModel: com.mustakim.bokbok.viewmodel.DeviceMonitorViewModel = hiltViewModel(viewModelStoreOwner)

    // Sync Pager state with ViewModel state
    LaunchedEffect(pagerState.currentPage) {
        val selectedTabEnum = tabs[pagerState.currentPage]
        viewModel.onTabSelected(pagerState.currentPage)
        
        // Smart Heartbeat: Only monitor when the user is actually looking at the Monitor tab.
        if (selectedTabEnum == GameBoostTab.DEVICE_MONITOR) {
            deviceMonitorViewModel.startMonitoring()
        } else {
            deviceMonitorViewModel.stopMonitoring()
        }
    }

    LaunchedEffect(selectedTab) {
        if (pagerState.currentPage != selectedTab.ordinal) {
            pagerState.animateScrollToPage(selectedTab.ordinal)
        }
    }

    MainScaffold(
        navController = navController,
        title = "Optimizer", // Not used in UI because showTopBar is false
        showBottomBar = true,
        showTopBar = false, // We use custom TopBar
        userViewModel = userViewModel
    ) { innerPadding ->
        
        // Use a standard scaffold (or just a Box) inside MainScaffold to handle the custom top bar structure
        Scaffold(
            modifier = Modifier
                .padding(bottom = innerPadding.calculateBottomPadding()), // Respect BottomBar
            containerColor = MaterialTheme.colorScheme.surface, // Solid Surface color
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = "Optimizer",
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 40.sp,
                            letterSpacing = 1.sp
                        )
                    },
                    actions = {
                        FilledIconButton(
                            modifier = Modifier.padding(end = 14.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                // TODO: Navigate to GameBoost Settings
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            }
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .padding(top = scaffoldPadding.calculateTopPadding())
                    .fillMaxSize()
            ) {
                ScrollableTabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    edgePadding = 12.dp,
                    indicator = { tabPositions ->
                        if (pagerState.currentPage < tabPositions.size) {
                            TabRowDefaults.PrimaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                height = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {}
                ) {
                    tabs.forEachIndexed { index, tab ->
                        TabAnimation(
                            index = index,
                            title = tab.title,
                            selectedIndex = pagerState.currentPage,
                            unselectedColor = MaterialTheme.colorScheme.surfaceContainerHighest, // Tonal elevation
                            onClick = {
                                scope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                        ) {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                val shizukuActive by viewModel.shizukuActive.collectAsState()
                var showShizukuDialog by remember { mutableStateOf(false) }

                LaunchedEffect(shizukuActive) {
                    if (!shizukuActive) {
                        showShizukuDialog = true
                    }
                }

                if (showShizukuDialog && !shizukuActive) {
                    AlertDialog(
                        modifier = Modifier.padding(28.dp),
                        onDismissRequest = { showShizukuDialog = false },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        title = {
                            Text(
                                "Shizuku Not Running",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        text = {
                            Text(
                                "Most optimizations and system monitoring features require Shizuku to be active and authorized. Please ensure Shizuku is running and this app is authorized in Shizuku's settings.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (intent != null) {
                                        context.startActivity(intent)
                                    } else {
                                        // Open Play Store if not installed? Or just show message
                                        val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
                                        context.startActivity(playStoreIntent)
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open Shizuku", Toast.LENGTH_SHORT).show()
                                }
                                showShizukuDialog = false
                            }) {
                                Text("Open Shizuku")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showShizukuDialog = false }) {
                                Text("Dismiss")
                            }
                        },
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 0.dp)
                        .padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 16.dp),
                        beyondViewportPageCount = 1, // Reduced to save memory
                        key = { tabs[it] }
                    ) { page ->
                        val tab = tabs[page]
                        when (tab) {
                            GameBoostTab.GAME_BOOST -> {
                                val gameSpaceViewModel: com.mustakim.bokbok.viewmodel.GameSpaceViewModel =
                                    hiltViewModel(viewModelStoreOwner)
                                com.mustakim.bokbok.ui.screens.gameboost.games.GameBoostTabScreen(
                                    viewModel = gameSpaceViewModel
                                )
                            }

                            GameBoostTab.APP_MANAGER -> {
                                val appManagerViewModel: com.mustakim.bokbok.viewmodel.AppManagerViewModel =
                                    hiltViewModel(viewModelStoreOwner)
                                // 🚀 PERFORMANCE FIX: Wait for navigation to settle before starting heavy app scan
                                val isSettled = !pagerState.isScrollInProgress && pagerState.currentPage == page
                                LaunchedEffect(isSettled) {
                                    if (isSettled) {
                                        appManagerViewModel.loadDataIfNeeded()
                                    }
                                }
                                AppManagerScreen(viewModel = appManagerViewModel)
                            }

                            GameBoostTab.USAGE_STATS -> {
                                val usageStatsViewModel: com.mustakim.bokbok.viewmodel.UsageStatsViewModel =
                                    hiltViewModel(viewModelStoreOwner)
                                // 🚀 PERFORMANCE FIX: Wait for navigation to settle before querying Usage Stats
                                val isSettled = !pagerState.isScrollInProgress && pagerState.currentPage == page
                                LaunchedEffect(isSettled) {
                                    if (isSettled) {
                                        usageStatsViewModel.loadDataIfNeeded()
                                    }
                                }
                                UsageStatsScreen(viewModel = usageStatsViewModel)
                            }

                            GameBoostTab.DEVICE_MONITOR -> {
                                DeviceMonitorScreen(viewModel = deviceMonitorViewModel)
                            }

                            GameBoostTab.SCREEN_RECORD -> {
                                val screenRecordViewModel: com.mustakim.bokbok.viewmodel.ScreenRecordViewModel =
                                    hiltViewModel(viewModelStoreOwner)
                                ScreenRecordTab(viewModel = screenRecordViewModel)
                            }

                            else -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = tab.title,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Feature Coming Soon",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
