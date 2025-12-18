package com.mustakim.bokbok.ui.screens.gameboost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.mustakim.bokbok.ui.screens.common.MainScaffold
import com.mustakim.bokbok.ui.theme.GoogleSansFlex
import com.mustakim.bokbok.viewmodel.GameBoostTab
import com.mustakim.bokbok.viewmodel.GameBoostViewModel
import com.mustakim.bokbok.viewmodel.UserViewModel
import com.mustakim.bokbok.ui.screens.gameboost.appmanager.AppManagerScreen
import com.mustakim.bokbok.ui.screens.gameboost.usagestats.UsageStatsScreen
import com.mustakim.bokbok.ui.screens.gameboost.devicemonitor.DeviceMonitorScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameBoostScreen(
    navController: NavHostController,
    userViewModel: UserViewModel,
    viewModel: GameBoostViewModel = viewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsState()
    val tabs = remember { viewModel.tabs }
    val pagerState = rememberPagerState(initialPage = selectedTab.ordinal) { tabs.size }
    val scope = rememberCoroutineScope()

    // Sync Pager state with ViewModel state
    LaunchedEffect(pagerState.currentPage) {
        viewModel.onTabSelected(pagerState.currentPage)
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
                    containerColor = MaterialTheme.colorScheme.surface, // Match TopBar
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
                        key = { tabs[it] }
                    ) { page ->
                        val tab = tabs[page]
                        if (tab == GameBoostTab.APP_MANAGER) {
                            AppManagerScreen()
                        } else if (tab == GameBoostTab.USAGE_STATS) {
                            UsageStatsScreen()
                        } else if (tab == GameBoostTab.DEVICE_MONITOR) {
                            DeviceMonitorScreen()
                        } else {
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
