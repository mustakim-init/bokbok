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
import com.mustakim.bokbok.viewmodel.AuthViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.mustakim.bokbok.startup.StartupManager
import kotlinx.coroutines.delay

/**
 * SplashScreen - Optimized for fast startup
 *
 * Performance optimizations:
 * 1. NO network calls - only cached auth check
 * 2. NO Firebase listeners started here
 * 3. Presence update is deferred via StartupManager
 * 4. Minimal delay before navigation
 */
@Composable
fun SplashScreen(
    onNavigateToLogin: () -> Unit,
    onNavigateToLounge: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // ✅ OPTIMIZED: Mark UI as ready for StartupManager
        StartupManager.markUiReady()
        
        // Minimal delay - just enough to show splash
        delay(100)

        // ✅ OPTIMIZED: Only check cached auth status
        if (viewModel.uiState.value.isLoggedIn) {
            // ✅ REMOVED: presenceRepository.setUserOnline()
            // This is now handled by StartupManager deferred tasks

            // Check permissions (synchronous, in-memory check)
            val requiredPermissions = PermissionsList.getRequiredPermissions()
            val allRequiredGranted = requiredPermissions.all { permission ->
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
                onNavigateToLounge()
            } else {
                onNavigateToLounge() // Will be intercepted by NavGraph for permissions
            }
        } else {
            onNavigateToLogin()
        }
    }

    // Splash UI - simple and fast
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

