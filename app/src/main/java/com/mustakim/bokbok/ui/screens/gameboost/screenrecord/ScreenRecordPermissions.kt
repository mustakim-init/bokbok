package com.mustakim.bokbok.ui.screens.gameboost.screenrecord

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner

/**
 * Utility object for checking and managing screen recording permissions.
 */
object ScreenRecordPermissions {

    /**
     * Returns a list of permissions that are required but not yet granted.
     */
    fun getMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()
        
        // RECORD_AUDIO is required for mic recording
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.RECORD_AUDIO)
        }
        
        // POST_NOTIFICATIONS is required on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        return missing
    }

    /**
     * Checks if the app has permission to draw overlays (for the floating HUD).
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Opens the system settings to grant overlay permission.
     */
    fun openOverlaySettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Checks if all required permissions are granted.
     */
    fun allPermissionsGranted(context: Context): Boolean {
        return getMissingPermissions(context).isEmpty() && hasOverlayPermission(context)
    }
}



/**
 * A composable that gates content behind permission checks.
 * Shows permission request UI if permissions are missing.
 */
@Composable
fun PermissionGate(
    onAllPermissionsGranted: @Composable () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var missingPermissions by remember { mutableStateOf(ScreenRecordPermissions.getMissingPermissions(context)) }
    var hasOverlay by remember { mutableStateOf(ScreenRecordPermissions.hasOverlayPermission(context)) }

    // Refresh permission state when app resumes (returning from settings)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                missingPermissions = ScreenRecordPermissions.getMissingPermissions(context)
                hasOverlay = ScreenRecordPermissions.hasOverlayPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        // Refresh state after permission request
        missingPermissions = ScreenRecordPermissions.getMissingPermissions(context)
    }

    if (missingPermissions.isEmpty() && hasOverlay) {
        onAllPermissionsGranted()
    } else {
        PermissionRequestUI(
            missingPermissions = missingPermissions,
            hasOverlay = hasOverlay,
            onRequestPermissions = {
                if (missingPermissions.isNotEmpty()) {
                    permissionLauncher.launch(missingPermissions.toTypedArray())
                }
            },
            onRequestOverlay = {
                ScreenRecordPermissions.openOverlaySettings(context)
            },
            onRefresh = {
                missingPermissions = ScreenRecordPermissions.getMissingPermissions(context)
                hasOverlay = ScreenRecordPermissions.hasOverlayPermission(context)
            }
        )
    }
}

@Composable
private fun PermissionRequestUI(
    missingPermissions: List<String>,
    hasOverlay: Boolean,
    onRequestPermissions: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Permissions Required",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Screen recording requires the following permissions to function properly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Microphone Permission
        if (missingPermissions.contains(Manifest.permission.RECORD_AUDIO)) {
            PermissionItem(
                icon = Icons.Default.Mic,
                title = "Microphone",
                description = "Required to record audio from your microphone",
                isGranted = false
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Overlay Permission
        if (!hasOverlay) {
            PermissionItem(
                icon = Icons.Default.ScreenShare,
                title = "Display Over Apps",
                description = "Required for the floating recording controls",
                isGranted = false
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grant Permissions Button
        if (missingPermissions.isNotEmpty()) {
            Button(
                onClick = onRequestPermissions,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Grant Permissions")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Overlay Settings Button
        if (!hasOverlay) {
            OutlinedButton(
                onClick = onRequestOverlay,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Open Overlay Settings")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Refresh Button
        TextButton(onClick = onRefresh) {
            Text("I've granted permissions, refresh")
        }
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    isGranted: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isGranted) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
