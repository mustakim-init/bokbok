package com.mustakim.bokbok.ui.screens.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mustakim.bokbok.ui.theme.GoogleSansFlexSlanted
import com.mustakim.bokbok.viewmodel.UserViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar(
    title: String,
    notificationCount: Int = 0,
    onMenuClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    userViewModel: UserViewModel? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    useFlexibleTopBar: Boolean = true,
    isStatic: Boolean = false,
    showProfile: Boolean = true,
    showNotifications: Boolean = true,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    customTitle: (@Composable () -> Unit)? = null
) {
    val currentUser by if (userViewModel != null) userViewModel.currentUser.collectAsState() else remember { mutableStateOf(null) }

    val finalTitleContent = customTitle ?: @Composable {
        Text(
            text = title,
            style = if (isStatic) MaterialTheme.typography.headlineLarge else if (useFlexibleTopBar) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            fontFamily = GoogleSansFlexSlanted,
            color = MaterialTheme.colorScheme.primary
        )
    }

    val defaultNavigationIcon = @Composable {
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .padding(start = 8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Menu",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    val finalActions = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            actions?.invoke(this@Row)

            if (actions != null && (showNotifications || showProfile)) {
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (showNotifications) {
                IconButton(
                    onClick = onNotificationsClick,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                ) {
                    BadgedBox(
                        badge = {
                            if (notificationCount > 0) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                ) {
                                    Text(
                                        text = if (notificationCount > 99) "99+" else notificationCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (notificationCount > 0) Icons.Outlined.NotificationsActive else Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            if (showNotifications && showProfile) {
                Spacer(modifier = Modifier.width(12.dp))
            }

            if (showProfile) {
                Box(
                    modifier = Modifier
                        .padding(end = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .clickable(onClick = onProfileClick)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentUser?.profileImageUrl?.isNotEmpty() == true) {
                        AsyncImage(
                            model = currentUser?.profileImageUrl,
                            contentDescription = "Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = currentUser?.displayName?.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val outlineVariant = MaterialTheme.colorScheme.outlineVariant

    val glassBrush = remember(surfaceColor) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to surfaceColor.copy(alpha = 0.96f),       // 100% transparent at very top
                0.35f to surfaceColor.copy(alpha = 0.90f),     // mostly transparent
                0.7f to surfaceColor.copy(alpha = 0.80f),      // drastic jump to 75% near the middle
                0.8f to surfaceColor.copy(alpha = 0.45f),      // getting more opaque
                1.0f to surfaceColor.copy(alpha = 0.0f)       // almost fully opaque at the bottom
            )
        )
    }

    if (isStatic) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 1. Frosted glass background layer (matching parent size, dynamically measured)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                12f, 12f, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    }
                    .background(glassBrush)
            )

            // 2. Clear border/highlight layer (crisp lines, not blurred)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Top highlight (white edge)
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Bottom divider
                        drawLine(
                            color = outlineVariant.copy(alpha = 0.18f),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            )

            // 3. TopAppBar (completely transparent background)
            TopAppBar(
                scrollBehavior = null,
                title = finalTitleContent,
                navigationIcon = navigationIcon ?: defaultNavigationIcon,
                actions = { finalActions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxWidth()) {
            // 1. Frosted glass background layer (matching parent size, dynamically measured)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        if (android.os.Build.VERSION.SDK_INT >= 31) {
                            renderEffect = android.graphics.RenderEffect.createBlurEffect(
                                12f, 12f, android.graphics.Shader.TileMode.CLAMP
                            ).asComposeRenderEffect()
                        }
                    }
                    .background(glassBrush)
            )

            // 2. Clear border/highlight layer (crisp lines, not blurred)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .drawBehind {
                        // Top highlight (white edge)
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, 0f),
                            end = Offset(size.width, 0f),
                            strokeWidth = 1.dp.toPx()
                        )
                        // Bottom divider
                        drawLine(
                            color = outlineVariant.copy(alpha = 0.18f),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
            )

            // 3. LargeTopAppBar (completely transparent background)
            androidx.compose.material3.LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Box(modifier = Modifier.padding(start = if (scrollBehavior?.state?.collapsedFraction ?: 0f > 0.5f) 0.dp else 8.dp)) {
                        finalTitleContent()
                    }
                },
                navigationIcon = navigationIcon ?: defaultNavigationIcon,
                actions = { finalActions() },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}
