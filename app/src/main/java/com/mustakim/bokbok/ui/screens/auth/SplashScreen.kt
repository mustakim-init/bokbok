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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

    // Splash UI - premium and immersive
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Subtle ambient mesh
        Box(
            modifier = Modifier
                .size(400.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0f)
                        )
                    )
                )
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "B",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "BokBok",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

