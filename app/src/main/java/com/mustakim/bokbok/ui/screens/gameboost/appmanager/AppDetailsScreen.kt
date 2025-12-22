package com.mustakim.bokbok.ui.screens.gameboost.appmanager

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mustakim.bokbok.data.bloatware.RemovalSafety
import com.mustakim.bokbok.data.model.AppItem
import com.mustakim.bokbok.data.repository.AppManagerRepository
import com.mustakim.bokbok.utils.AppIconCache
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailsScreen(
    app: AppItem,
    repository: AppManagerRepository,
    onBack: () -> Unit,
    onAppUninstalled: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    var showUninstallDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var showAdbUninstallDialog by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = app.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", app.packageName, null)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "System Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // App Icon and Basic Info
            AppHeaderSection(app, clipboardManager) {
                scope.launch {
                    snackbarHostState.showSnackbar("Package name copied")
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Bloatware Warning Section
            if (app.isBloatware) {
                BloatwareWarningSection(app)
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Quick Actions
            QuickActionsSection(
                app = app,
                isProcessing = isProcessing,
                onLaunch = {
                    context.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
                        context.startActivity(it)
                    }
                },
                onForceStop = {
                    scope.launch {
                        isProcessing = true
                        val result = repository.forceStopApp(app.packageName)
                        isProcessing = false
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar("App force stopped")
                        } else {
                            snackbarHostState.showSnackbar("Failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                },
                onClearCache = {
                    scope.launch {
                        isProcessing = true
                        val result = repository.clearAppCache(app.packageName)
                        isProcessing = false
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar("Cache cleared")
                        } else {
                            snackbarHostState.showSnackbar("Failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                },
                onUninstall = {
                    if (app.isSystemApp) {
                        showAdbUninstallDialog = true
                    } else {
                        showUninstallDialog = true
                    }
                },
                onToggleEnable = {
                    scope.launch {
                        isProcessing = true
                        val result = if (app.isEnabled) {
                            repository.disableApp(app.packageName)
                        } else {
                            repository.enableApp(app.packageName)
                        }
                        isProcessing = false
                        if (result.isSuccess) {
                            val action = if (app.isEnabled) "disabled" else "enabled"
                            snackbarHostState.showSnackbar("App $action. Refreshing list...")
                            onAppUninstalled() // Refresh list
                        } else {
                            snackbarHostState.showSnackbar("Failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                },
                onRestore = {
                    scope.launch {
                        isProcessing = true
                        val result = repository.reinstallApp(app.packageName)
                        isProcessing = false
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar("App restored successfully")
                            onAppUninstalled() // Refresh list
                        } else {
                            snackbarHostState.showSnackbar("Failed: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // App Details Section
            AppDetailsSection(app)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Technical Info Section
            TechnicalInfoSection(app)
            
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    // Standard Uninstall Dialog
    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
            title = { Text("Uninstall ${app.label}?") },
            text = { Text("This will remove the app and all its data from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        showUninstallDialog = false
                        repository.requestUninstall(app.packageName)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Uninstall")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
    
    // ADB Uninstall Dialog for System Apps
    if (showAdbUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showAdbUninstallDialog = false },
            title = { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uninstall System App?")
                }
            },
            text = { 
                Column {
                    Text("This is a system app. Uninstalling it will:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• Remove it for the current user only")
                    Text("• Keep app data (reversible)")
                    Text("• Require Shizuku permission")
                    
                    if (app.removalSafety == RemovalSafety.UNSAFE) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "WARNING: This app is marked as UNSAFE to remove. It may break system functionality!",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    
                    app.bloatwareWarning?.let { warning ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAdbUninstallDialog = false
                        scope.launch {
                            isProcessing = true
                            val result = repository.uninstallViaAdb(app.packageName, keepData = true)
                            isProcessing = false
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar("App uninstalled successfully")
                                onAppUninstalled()
                            } else {
                                snackbarHostState.showSnackbar("Failed: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Uninstall via ADB")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdbUninstallDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AppHeaderSection(
    app: AppItem,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    onCopied: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            val context = LocalContext.current
            var iconBitmap by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
            
            LaunchedEffect(app.packageName) {
                iconBitmap = AppIconCache.getIcon(context, app.packageName)
            }

            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap!!,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // App Name
        Text(
            text = app.label,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        // Package Name with copy button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp)
        ) {
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(app.packageName))
                    onCopied()
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        // Version and badges
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            app.versionName?.let { version ->
                InfoChip(text = "v$version")
            }
            InfoChip(text = "SDK ${app.targetSdk}")
            if (app.isSystemApp) {
                InfoChip(
                    text = "SYSTEM",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun BloatwareWarningSection(app: AppItem) {
    val (bgColor, textColor, icon, label) = when (app.removalSafety) {
        RemovalSafety.SAFE -> Quadruple(
            Color(0xFF4CAF50).copy(alpha = 0.1f),
            Color(0xFF4CAF50),
            Icons.Default.Delete,
            "Safe to Remove"
        )
        RemovalSafety.REPLACEABLE -> Quadruple(
            Color(0xFF2196F3).copy(alpha = 0.1f),
            Color(0xFF2196F3),
            Icons.Default.Refresh,
            "Can be Replaced"
        )
        RemovalSafety.CAUTION -> Quadruple(
            Color(0xFFFFA726).copy(alpha = 0.1f),
            Color(0xFFFFA726),
            Icons.Default.Warning,
            "Remove with Caution"
        )
        RemovalSafety.UNSAFE -> Quadruple(
            Color(0xFFE53935).copy(alpha = 0.1f),
            Color(0xFFE53935),
            Icons.Default.Block,
            "Do NOT Remove"
        )
        RemovalSafety.UNKNOWN -> Quadruple(
            Color(0xFF9E9E9E).copy(alpha = 0.1f),
            Color(0xFF9E9E9E),
            Icons.Default.Info,
            "Unknown"
        )
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    app.bloatwareType?.let { type ->
                        Text(
                            text = "Type: ${type.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            app.bloatwareDescription?.let { description ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = description.replace("\\n", "\n"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            
            app.bloatwareWarning?.let { warning ->
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
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
    onClearCache: () -> Unit,
    onUninstall: () -> Unit,
    onToggleEnable: () -> Unit,
    onRestore: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Quick Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (isProcessing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Launch button (only if app has activities)
                    if (app.hasActivities) {
                        ActionButton(
                            icon = Icons.Default.PlayArrow,
                            label = "Launch",
                            onClick = onLaunch
                        )
                    }
                    
                    ActionButton(
                        icon = Icons.Default.Stop,
                        label = "Force Stop",
                        onClick = onForceStop
                    )
                    
                    ActionButton(
                        icon = Icons.Default.CleaningServices,
                        label = "Clear Data",
                        onClick = onClearCache
                    )
                    
                    // Disable/Enable button for system apps (or all apps if we want)
                    if (app.isInstalled) {
                        ActionButton(
                            icon = if (app.isEnabled) Icons.Default.Block else Icons.Default.PlayArrow,
                            label = if (app.isEnabled) "Disable" else "Enable",
                            onClick = onToggleEnable
                        )
                        
                        ActionButton(
                            icon = if (app.isSystemApp) Icons.Default.DeleteForever else Icons.Default.Delete,
                            label = if (app.isSystemApp) "Uninstall (ADB)" else "Uninstall",
                            onClick = onUninstall,
                            isDangerous = true
                        )
                    } else {
                        // Restore Button
                        ActionButton(
                            icon = Icons.Default.Refresh,
                            label = "Restore (ADB)",
                            onClick = onRestore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    isDangerous: Boolean = false
) {
    FilledTonalButton(
        onClick = onClick,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = if (isDangerous) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            contentColor = if (isDangerous) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 13.sp)
    }
}

@Composable
private fun AppDetailsSection(app: AppItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "App Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            DetailRow("APK Size", formatFileSize(app.apkSize))
            DetailRow("Data Size", formatFileSize(app.dataSize))
            DetailRow("Cache Size", formatFileSize(app.cacheSize))
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            DetailRow("Installed", formatDate(app.firstInstallTime))
            DetailRow("Updated", formatDate(app.lastUpdateTime))
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            DetailRow("Status", if (app.isEnabled) "Enabled" else "Disabled")
            DetailRow("Has Launcher", if (app.hasActivities) "Yes" else "No")
            DetailRow("Debuggable", if (app.isDebuggable) "Yes" else "No")
        }
    }
}

@Composable
private fun TechnicalInfoSection(app: AppItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Technical Info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            DetailRow("Target SDK", app.targetSdk.toString())
            DetailRow("Min SDK", if (app.minSdk > 0) app.minSdk.toString() else "Unknown")
            DetailRow("UID", app.uid.toString())
            DetailRow("Version Code", app.versionCode.toString())
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            if (app.apkPath.isNotEmpty()) {
                DetailRow("APK Path", app.apkPath, isPath = true)
            }
            if (app.dataPath.isNotEmpty()) {
                DetailRow("Data Path", app.dataPath, isPath = true)
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isPath: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
            textAlign = TextAlign.End,
            maxLines = if (isPath) 2 else 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InfoChip(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(
                color.copy(alpha = 0.1f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// Helper class for multiple return values
private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
    return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "Unknown"
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
