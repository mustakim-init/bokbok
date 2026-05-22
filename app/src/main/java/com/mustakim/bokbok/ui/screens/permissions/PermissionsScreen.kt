package com.mustakim.bokbok.ui.screens.permissions

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.model.PermissionsList
import com.mustakim.bokbok.ui.components.PermissionIconCollage

@Composable
fun PermissionsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val permissions = remember { PermissionsList.getAllPermissions() }

    var grantedPermissions by remember { mutableStateOf(setOf<String>()) }

    val requiredPermissions = remember {
        val allRequired = PermissionsList.getRequiredPermissions()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            allRequired
        } else {
            // On Android 12 and below, ignore POST_NOTIFICATIONS as a "required" permission
            allRequired.filter { it.permission != android.Manifest.permission.POST_NOTIFICATIONS }
        }
    }


    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        grantedPermissions = results.filter { it.value }.keys

        // Auto-advance to next screen if all required permissions granted
        val requiredGranted = requiredPermissions.all {
            grantedPermissions.contains(it.permission)
        }

        if (requiredGranted) {
            navController.navigate("lounge") {
                popUpTo("permissions") { inclusive = true }
            }
        }
    }

    // Check already granted permissions
    LaunchedEffect(Unit) {
        grantedPermissions = permissions
            .filter { permission ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    permission.permission == android.Manifest.permission.POST_NOTIFICATIONS) {
                    ContextCompat.checkSelfPermission(
                        context,
                        permission.permission
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    ContextCompat.checkSelfPermission(
                        context,
                        permission.permission
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
            }
            .map { it.permission }
            .toSet()
    }

    val allRequiredGranted = PermissionsList.getRequiredPermissions().all {
        grantedPermissions.contains(it.permission)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Programmatic M3E Mesh gradient background layer
        val color1 = MaterialTheme.colorScheme.primary
        val color2 = MaterialTheme.colorScheme.secondary

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    onDrawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(color1.copy(alpha = 0.12f), Color.Transparent),
                                center = Offset(size.width * 0.15f, size.height * 0.1f),
                                radius = size.width * 0.8f
                            )
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(color2.copy(alpha = 0.1f), Color.Transparent),
                                center = Offset(size.width * 0.85f, size.height * 0.25f),
                                radius = size.width * 0.7f
                            )
                        )
                    }
                }
        )

        Scaffold(
            containerColor = Color.Transparent
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))

                // Icon Collage (Pixelplay style!)
                PermissionIconCollage(
                    icons = listOf(
                        R.drawable.ic_mic_24,      // Microphone
                        R.drawable.ic_notifications_24,  // Notifications
                        R.drawable.ic_camera_24,   // Camera
                        R.drawable.ic_contacts_24  // Contacts
                    ),
                    height = 220.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(32.dp))
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Title
                Text(
                    text = "Grant Permissions",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "BokBok needs a few permissions to provide you with the best voice calling experience",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Permissions list
                permissions.forEach { permission ->
                    PermissionItem(
                        permission = permission,
                        isGranted = grantedPermissions.contains(permission.permission)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Action button
                if (allRequiredGranted) {
                    Button(
                        onClick = {
                            navController.navigate("lounge") {
                                popUpTo("permissions") { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Continue to BokBok", fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Button(
                        onClick = {
                            val permissionsToRequest = requiredPermissions
                                .filter { !grantedPermissions.contains(it.permission) }
                                .map { it.permission }
                                .toTypedArray()

                            permissionLauncher.launch(permissionsToRequest)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Text("Grant Required Permissions", fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun PermissionItem(
    permission: com.mustakim.bokbok.data.model.AppPermission,
    isGranted: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.6f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
        ),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isGranted)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = permission.icon,
                        contentDescription = null,
                        tint = if (isGranted)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = permission.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        if (permission.isRequired) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Required",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Text(
                        text = permission.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (isGranted) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Check,
                    contentDescription = "Granted",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
