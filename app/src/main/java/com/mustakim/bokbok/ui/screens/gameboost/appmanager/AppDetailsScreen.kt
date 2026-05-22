package com.mustakim.bokbok.ui.screens.gameboost.appmanager

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.mustakim.bokbok.data.bloatware.RemovalSafety
import com.mustakim.bokbok.data.model.AppItem
import com.mustakim.bokbok.utils.AppIcon
import com.mustakim.bokbok.viewmodel.AppDetailsViewModel
import com.mustakim.bokbok.data.repository.AppManagerRepository
import java.text.DecimalFormat
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.screens.common.MainScaffold

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailsScreen(
    navController: NavController,
    viewModel: AppDetailsViewModel
) {
    val app by viewModel.app.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    var currentManager by remember { mutableStateOf<ManagerType?>(null) }

    when (currentManager) {
        ManagerType.COMPONENT -> {
            AppComponentManagerScreen(
                onBack = { currentManager = null },
                viewModel = viewModel
            )
            return
        }
        ManagerType.PERMISSION -> {
            AppPermissionManagerScreen(
                onBack = { currentManager = null },
                viewModel = viewModel
            )
            return
        }
        null -> {}
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // M3E Mesh gradient background layer
        val color1 = MaterialTheme.colorScheme.primary
        val color2 = MaterialTheme.colorScheme.secondary

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawBehind {
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(color1.copy(alpha = 0.12f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.15f, size.height * 0.1f),
                                radius = size.width * 0.8f
                            )
                        )
                        drawRect(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(color2.copy(alpha = 0.1f), Color.Transparent),
                                center = androidx.compose.ui.geometry.Offset(size.width * 0.85f, size.height * 0.25f),
                                radius = size.width * 0.7f
                            )
                        )
                    }
                }
        )
        
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    app?.let { 
                        Text(
                            text = it.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        ) 
                    } 
                },
                navigationIcon = {
                    BokBokIconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    BokBokIconButton(onClick = {
                        app?.let {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", it.packageName, null)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(Icons.Default.Settings, contentDescription = "System Settings")
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { padding ->
        if (app == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val appItem = app!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp), // Standard M3 margin
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Section (Redesigned)
                AppHeroHeader(app = appItem, clipboardManager = clipboardManager)

                // Quick Actions
                QuickActionsSection(
                    app = appItem,
                    isProcessing = isProcessing,
                    onLaunch = {
                        context.packageManager.getLaunchIntentForPackage(appItem.packageName)?.let {
                            context.startActivity(it)
                        }
                    },
                    onForceStop = { viewModel.forceStopApp() },
                    onToggleEnable = { viewModel.toggleAppEnable() },
                    onUninstall = { viewModel.requestUninstall() },
                    onUninstallAdb = { viewModel.uninstallViaAdb() },
                    onRestore = { viewModel.reinstallApp() }
                )

                // Bloatware Alert
                if (appItem.isBloatware) {
                    BloatwareAlert(app = appItem)
                }

                // App Analysis (Replacing Power Tools, PRESERVING style)
                AppAnalysisElevatedCard(
                    viewModel = viewModel,
                    onManagePermissions = { currentManager = ManagerType.PERMISSION },
                    onManageComponents = { type ->
                        viewModel.setComponentType(type)
                        currentManager = ManagerType.COMPONENT
                    }
                )

                SectionHeader("Storage")
                StorageCard(
                    app = appItem,
                    onClearCache = { viewModel.clearAppCache() },
                    onClearData = { viewModel.clearAppData() }
                )
                
                SectionHeader("Technical")
                TechnicalInfoCard(app = appItem)
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
    )
}

@Composable
private fun AppHeroHeader(
    app: AppItem,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Large Icon (120dp)
        Surface(
            modifier = Modifier.size(140.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
            tonalElevation = 6.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = AppIcon(app.packageName),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // App Name (Header)
        Text(
            text = app.label,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Package Name (Monospace Subhead)
        SelectionContainer {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppHeaderStatusChip(
                label = if (app.isSystemApp) "System App" else "User App",
                color = if (app.isSystemApp) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer
            )
            AppHeaderStatusChip(
                label = "v${app.versionName ?: app.versionCode}",
                color = MaterialTheme.colorScheme.secondaryContainer
            )
            if (!app.isEnabled) {
                AppHeaderStatusChip(
                    label = "Disabled",
                    color = MaterialTheme.colorScheme.tertiaryContainer
                )
            }
        }
    }
}

@Composable
private fun AppHeaderStatusChip(label: String, color: Color) {
    Surface(
        color = color,
        shape = CircleShape
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BloatwareAlert(app: AppItem) {
    val color = when(app.removalSafety) {
        RemovalSafety.SAFE -> Color(0xFF4CAF50)
        RemovalSafety.UNSAFE -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    
    OutlinedCard(
        colors = CardDefaults.outlinedCardColors(
            containerColor = color.copy(alpha = 0.1f),
            contentColor = color
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Warning, null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    "Bloatware: ${app.removalSafety.name}",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleSmall
                )
                if (!app.bloatwareDescription.isNullOrEmpty()) {
                    Text(
                        app.bloatwareDescription,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActionsSection(
    app: AppItem,
    isProcessing: Boolean,
    onLaunch: () -> Unit,
    onForceStop: () -> Unit,
    onToggleEnable: () -> Unit,
    onUninstall: () -> Unit,
    onUninstallAdb: () -> Unit,
    onRestore: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Primary Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = onLaunch,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp), // Standard M3 radius
                enabled = !isProcessing && app.isEnabled && app.hasActivities
            ) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Launch")
            }

            FilledTonalButton(
                onClick = onForceStop,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                enabled = !isProcessing && app.isEnabled
            ) {
                Icon(Icons.Default.Stop, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Force Stop")
            }
        }

        // Secondary Chips
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (app.isInstalled) {
                 AssistChip(
                    onClick = { if (app.isSystemApp) onUninstallAdb() else onUninstall() },
                    label = { Text(if (app.isSystemApp) "Uninstall (ADB)" else "Uninstall") },
                    leadingIcon = { Icon(Icons.Default.Delete, null, Modifier.size(16.dp)) }
                )
                AssistChip(
                    onClick = onToggleEnable,
                    label = { Text(if (app.isEnabled) "Disable" else "Enable") },
                    leadingIcon = { Icon(if (app.isEnabled) Icons.Default.Block else Icons.Default.Check, null, Modifier.size(16.dp)) },
                    colors = if (!app.isEnabled) AssistChipDefaults.assistChipColors(
                        labelColor = MaterialTheme.colorScheme.primary
                    ) else AssistChipDefaults.assistChipColors()
                )
            } else {
                 Button(
                    onClick = onRestore,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Restore", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AppAnalysisElevatedCard(
    viewModel: AppDetailsViewModel,
    onManagePermissions: () -> Unit,
    onManageComponents: (AppManagerRepository.ComponentType) -> Unit
) {
    val permissionCount by viewModel.permissionCount.collectAsState()
    val componentCounts by viewModel.componentCounts.collectAsState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            ListItem(
                headlineContent = { Text("Permissions", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("$permissionCount requested by app") },
                leadingContent = { Icon(Icons.Default.Security, null, tint = MaterialTheme.colorScheme.primary) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onManagePermissions() },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ListItem(
                headlineContent = { Text("Components", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("${componentCounts.first} services, ${componentCounts.second} receivers") },
                leadingContent = { Icon(Icons.Default.Extension, null, tint = MaterialTheme.colorScheme.secondary) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onManageComponents(AppManagerRepository.ComponentType.SERVICE) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ListItem(
                headlineContent = { Text("Activities", fontWeight = FontWeight.Bold) },
                supportingContent = { Text("${componentCounts.third} activities found") },
                leadingContent = { Icon(Icons.Default.PlayCircle, null, tint = MaterialTheme.colorScheme.tertiary) },
                trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable { onManageComponents(AppManagerRepository.ComponentType.ACTIVITY) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
        }
    }
}

enum class ManagerType { COMPONENT, PERMISSION }

@Composable
private fun StorageCard(
    app: AppItem,
    onClearCache: () -> Unit,
    onClearData: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("APK Size", style = MaterialTheme.typography.bodyMedium)
                Text(formatFileSize(app.apkSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Data Size", style = MaterialTheme.typography.bodyMedium)
                Text(formatFileSize(app.dataSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Cache Size", style = MaterialTheme.typography.bodyMedium)
                Text(formatFileSize(app.cacheSize), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.ExtraBold)
            }
            
            Spacer(Modifier.height(24.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onClearCache,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Text("Clear Cache", fontWeight = FontWeight.Bold)
                }
                
                Button(
                    onClick = onClearData,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Data", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TechnicalInfoCard(app: AppItem) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.65f),
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TechRow("Package", app.packageName, true)
            TechRow("Category", getCategoryName(app.category))
            TechRow("Target SDK", "Android ${getTargetAndroidName(app.targetSdk)} (${app.targetSdk})")
            TechRow("UID", app.uid.toString())
            TechRow("Path", app.apkPath, true)
            if (app.dataPath.isNotEmpty()) TechRow("Data", app.dataPath, true)
        }
    }
}

@Composable
private fun TechRow(label: String, value: String, isMonospace: Boolean = false) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        SelectionContainer {
             Text(
                value, 
                style = if (isMonospace) MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace) 
                        else MaterialTheme.typography.bodyMedium
            )
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

private fun getCategoryName(category: Int): String = when (category) {
    0 -> "Game"
    1 -> "Audio"
    2 -> "Video"
    3 -> "Image"
    4 -> "Social"
    5 -> "News"
    6 -> "Maps"
    7 -> "Productivity"
    else -> "Generic"
}

private fun getTargetAndroidName(sdk: Int): String = when (sdk) {
    34, 35 -> "14/15"
    33 -> "13"
    31, 32 -> "12"
    30 -> "11"
    29 -> "10"
    28 -> "9.0"
    else -> sdk.toString()
}