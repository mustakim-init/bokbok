package com.mustakim.bokbok.ui.screens.gameboost

import com.mustakim.bokbok.ui.screens.common.TopBar
import com.mustakim.bokbok.ui.shared.*

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material.icons.filled.Menu
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
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import com.mustakim.bokbok.R
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack

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

    androidx.activity.compose.BackHandler(enabled = selectedTab != GameBoostTab.DASHBOARD) {
        viewModel.onTabSelected(GameBoostTab.DASHBOARD)
    }

    MainScaffold(
        navController = navController,
        title = "Optimizer",
        showBottomBar = true,
        showTopBar = true,
        useFlexibleTopBar = false,
        isStatic = true,
        showProfile = false,
        showNotifications = false,
        userViewModel = userViewModel,
        customTopBar = { passedScrollBehavior ->
            TopBar(
                title = if (selectedTab == GameBoostTab.DASHBOARD) "Optimizer" else selectedTab.title,
                userViewModel = userViewModel,
                scrollBehavior = passedScrollBehavior,
                useFlexibleTopBar = false,
                isStatic = true,
                showProfile = false,
                showNotifications = false,
                navigationIcon = {
                    if (selectedTab != GameBoostTab.DASHBOARD) {
                        BokBokIconButton(onClick = { viewModel.onTabSelected(GameBoostTab.DASHBOARD) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    } else {
                        BokBokIconButton(onClick = { /* Menu */ }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu")
                        }
                    }
                },
                actions = {
                    BokBokIconButton(onClick = {
                        navController.navigate(com.mustakim.bokbok.ui.navigation.NavRoutes.AICompanion.route)
                    }) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI Companion")
                    }
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        // Capture M3 Expressive colors from theme
        val color1 = MaterialTheme.colorScheme.primary
        val color2 = MaterialTheme.colorScheme.secondary
        val color3 = MaterialTheme.colorScheme.tertiary
        val color4 = MaterialTheme.colorScheme.primaryContainer
        val color5 = MaterialTheme.colorScheme.secondaryContainer
        val surfaceColor = MaterialTheme.colorScheme.surface

        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            // M3E Mesh gradient background layer at the top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxSize(0.7f) // Cover top 70% of screen
                    .align(Alignment.TopCenter)
                    .zIndex(-1f) // Place behind all content
                    .drawWithCache {
                        val width = this.size.width
                        val height = this.size.height

                        val brush1 = Brush.radialGradient(
                            colors = listOf(
                                color1.copy(alpha = 0.38f),
                                color1.copy(alpha = 0.24f),
                                color1.copy(alpha = 0.14f),
                                color1.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.15f, height * 0.1f),
                            radius = width * 0.55f
                        )

                        val brush2 = Brush.radialGradient(
                            colors = listOf(
                                color2.copy(alpha = 0.34f),
                                color2.copy(alpha = 0.2f),
                                color2.copy(alpha = 0.11f),
                                color2.copy(alpha = 0.05f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.85f, height * 0.2f),
                            radius = width * 0.65f
                        )

                        val brush3 = Brush.radialGradient(
                            colors = listOf(
                                color3.copy(alpha = 0.3f),
                                color3.copy(alpha = 0.17f),
                                color3.copy(alpha = 0.09f),
                                color3.copy(alpha = 0.04f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.3f, height * 0.45f),
                            radius = width * 0.6f
                        )

                        val brush4 = Brush.radialGradient(
                            colors = listOf(
                                color4.copy(alpha = 0.26f),
                                color4.copy(alpha = 0.14f),
                                color4.copy(alpha = 0.08f),
                                color4.copy(alpha = 0.03f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.7f, height * 0.5f),
                            radius = width * 0.7f
                        )

                        val brush5 = Brush.radialGradient(
                            colors = listOf(
                                color5.copy(alpha = 0.22f),
                                color5.copy(alpha = 0.12f),
                                color5.copy(alpha = 0.06f),
                                color5.copy(alpha = 0.02f),
                                Color.Transparent
                            ),
                            center = Offset(width * 0.5f, height * 0.75f),
                            radius = width * 0.8f
                        )

                        val overlayBrush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                surfaceColor.copy(alpha = 0.22f),
                                surfaceColor.copy(alpha = 0.55f),
                                surfaceColor
                            ),
                            startY = height * 0.4f,
                            endY = height
                        )

                        onDrawBehind {
                            drawRect(brush = brush1)
                            drawRect(brush = brush2)
                            drawRect(brush = brush3)
                            drawRect(brush = brush4)
                            drawRect(brush = brush5)
                            drawRect(brush = overlayBrush)
                        }
                    }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
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
                            userViewModel = userViewModel,
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