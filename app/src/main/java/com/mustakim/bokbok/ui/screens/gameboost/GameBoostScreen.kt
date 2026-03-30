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
import androidx.compose.material.icons.filled.AutoAwesome
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.IconButton
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
import com.mustakim.bokbok.ui.screens.gameboost.security.SecurityScreen
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
    
    // ViewModels are now lazily initialized inside the pager items using hiltViewModel(viewModelStoreOwner)

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
            // TopAppBar with dynamic Back Button support
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = if (selectedTab == GameBoostTab.DASHBOARD) "Optimizer" else selectedTab.title,
                            fontFamily = GoogleSansFlex,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 32.sp, // Adjusted for dynamic title
                            letterSpacing = 1.sp
                        )
                    },
                    navigationIcon = {
                        if (selectedTab != GameBoostTab.DASHBOARD) {
                            IconButton(onClick = { viewModel.onTabSelected(GameBoostTab.DASHBOARD) }) {
                                Icon(
                                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back to Dashboard",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        FilledIconButton(
                            modifier = Modifier.padding(end = 14.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            onClick = {
                                navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.AICompanion.route)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Companion",
                                tint = MaterialTheme.colorScheme.primary
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
            Box(
                modifier = Modifier
                    .padding(top = scaffoldPadding.calculateTopPadding())
                    .fillMaxSize()
            ) {
                // Feature Content Switcher (Lazy initialization of screens)
                when (selectedTab) {
                    GameBoostTab.DASHBOARD -> {
                        OptimizerDashboard(
                            onTabSelected = { tab -> viewModel.onTabSelected(tab) }
                        )
                    }

                    GameBoostTab.GAME_BOOST -> {
                        val gameSpaceViewModel: com.mustakim.bokbok.viewmodel.GameSpaceViewModel = hiltViewModel(viewModelStoreOwner)
                        com.mustakim.bokbok.ui.screens.gameboost.games.GameBoostTabScreen(
                            navController = navController,
                            viewModel = gameSpaceViewModel
                        )
                    }

                    GameBoostTab.APP_MANAGER -> {
                        val appManagerViewModel: com.mustakim.bokbok.viewmodel.AppManagerViewModel = hiltViewModel(viewModelStoreOwner)
                        AppManagerScreen(
                            navController = navController,
                            viewModel = appManagerViewModel
                        )
                    }

                    GameBoostTab.USAGE_STATS -> {
                        val usageStatsViewModel: com.mustakim.bokbok.viewmodel.UsageStatsViewModel = hiltViewModel(viewModelStoreOwner)
                        UsageStatsScreen(viewModel = usageStatsViewModel)
                    }

                    GameBoostTab.DEVICE_MONITOR -> {
                        val deviceMonitorViewModel: com.mustakim.bokbok.viewmodel.DeviceMonitorViewModel = hiltViewModel(viewModelStoreOwner)
                        LaunchedEffect(Unit) {
                            deviceMonitorViewModel.startMonitoring()
                        }
                        DeviceMonitorScreen(viewModel = deviceMonitorViewModel)
                    }

                    GameBoostTab.SCREEN_RECORD -> {
                        val screenRecordViewModel: com.mustakim.bokbok.viewmodel.ScreenRecordViewModel = hiltViewModel(viewModelStoreOwner)
                        ScreenRecordTab(
                            navController = navController,
                            viewModel = screenRecordViewModel
                        )
                    }

                    GameBoostTab.SECURITY -> {
                        val securityViewModel: com.mustakim.bokbok.viewmodel.SecurityViewModel = hiltViewModel(viewModelStoreOwner)
                        SecurityScreen(
                            viewModel = securityViewModel
                        )
                    }
                }

                // Shizuku Alert Dialog (Persistent across sub-screens)
                val showShizukuDialog by viewModel.showShizukuWarning.collectAsState()
                val shizukuActive by viewModel.shizukuActive.collectAsState()

                if (showShizukuDialog && !shizukuActive) {
                    AlertDialog(
                        modifier = Modifier.padding(28.dp),
                        onDismissRequest = { viewModel.dismissShizukuWarning() },
                        icon = { Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("Shizuku Not Running", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) },
                        text = { Text("This feature requires Shizuku to be active and authorized.", style = MaterialTheme.typography.bodyMedium) },
                        confirmButton = {
                            TextButton(onClick = {
                                try {
                                    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                    if (intent != null) context.startActivity(intent)
                                    else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                                } catch (e: Exception) { Toast.makeText(context, "Could not open Shizuku", Toast.LENGTH_SHORT).show() }
                                viewModel.dismissShizukuWarning()
                            }) { Text("Open Shizuku") }
                        },
                        dismissButton = { TextButton(onClick = { viewModel.dismissShizukuWarning() }) { Text("Dismiss") } },
                        shape = RoundedCornerShape(28.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                }
            }
        }
    }
}
