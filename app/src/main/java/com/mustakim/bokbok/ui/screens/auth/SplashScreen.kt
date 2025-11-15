package com.mustakim.bokbok.ui.screens.auth

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mustakim.bokbok.data.model.PermissionsList
import com.mustakim.bokbok.data.repository.AuthRepository
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToLounge: () -> Unit
) {
    val context = LocalContext.current
    val authRepository = remember { AuthRepository(context) }

    LaunchedEffect(Unit) {
        delay(700) // Show splash for 1.5 seconds

        // Check if user is logged in
        if (authRepository.isUserLoggedIn()) {
            // Check if ALL required permissions are already granted
            val requiredPermissions = PermissionsList.getRequiredPermissions()
            val allRequiredGranted = requiredPermissions.all { permission ->
                // Handle Android 13+ notification permission separately
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

            // Navigate based on permission status
            if (allRequiredGranted) {
                onNavigateToLounge()  // Go straight to lounge ✅
            } else {
                // Still need permissions, handled by NavGraph
                onNavigateToLounge()  // Will be intercepted by NavGraph
            }
        } else {
            onNavigateToLogin() // Not logged in, go to login
        }
    }

    // Splash UI
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "BokBok",
            style = MaterialTheme.typography.displayLarge
        )
    }
}
