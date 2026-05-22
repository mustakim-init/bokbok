package com.mustakim.bokbok.music.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.music.BuildConfig
import com.mustakim.bokbok.music.constants.AppBarHeight
import com.mustakim.bokbok.music.ui.screens.Screens
import com.mustakim.bokbok.util.Updater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicTopBar(
    navController: NavController,
    currentRoute: String?,
    scrollBehavior: TopAppBarScrollBehavior,
    pureBlack: Boolean,
    latestVersionName: String
) {
    val topLevelScreens = remember {
        setOf(
            Screens.Home.route,
            Screens.MoodAndGenres.route,
            Screens.Library.route
        )
    }
    val isTopLevelScreen = currentRoute in topLevelScreens

    val currentScreen = remember(currentRoute) {
        if (currentRoute == null) return@remember null
        try {
            Screens.MainScreens.firstOrNull { it != null && it.route == currentRoute }
        } catch (e: Exception) {
            null
        }
    }

    val displayTitle = when {
        currentScreen != null -> stringResource(currentScreen.titleId)
        currentRoute?.startsWith("search/") == true -> stringResource(MusicR.string.search)
        else -> null
    }

    val shouldUseFloatingTopBar = isTopLevelScreen
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = Modifier.offset {
            IntOffset(x = 0, y = scrollBehavior.state.heightOffset.toInt())
        }
    ) {
        if (shouldUseFloatingTopBar) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppBarHeight + with(LocalDensity.current) {
                        WindowInsets.systemBars.getTop(LocalDensity.current).toDp()
                    })
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                surfaceColor.copy(alpha = 0.95f),
                                surfaceColor.copy(alpha = 0.85f),
                                surfaceColor.copy(alpha = 0.6f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        TopAppBar(
            title = {
                if (isTopLevelScreen) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(CoreR.drawable.about_appbar),
                            contentDescription = null,
                            modifier = Modifier
                                .size(35.dp)
                                .padding(end = 3.dp)
                        )
                        Text(
                            text = stringResource(CoreR.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else {
                    Text(
                        text = displayTitle ?: "",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            navigationIcon = {
                if (!isTopLevelScreen) {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            painter = painterResource(CoreR.drawable.arrow_back),
                            contentDescription = null
                        )
                    }
                }
            },
            actions = {
                IconButton(onClick = { navController.navigate("history") }) {
                    Icon(painter = painterResource(CoreR.drawable.history), contentDescription = stringResource(MusicR.string.history))
                }
                IconButton(onClick = { navController.navigate("stats") }) {
                    Icon(painter = painterResource(CoreR.drawable.stats), contentDescription = stringResource(MusicR.string.stats))
                }
                IconButton(onClick = { navController.navigate("new_release") }) {
                    Icon(painter = painterResource(CoreR.drawable.new_release), contentDescription = stringResource(MusicR.string.new_release_albums))
                }
                if (isTopLevelScreen) {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        BadgedBox(badge = {
                            if (!Updater.isSameVersion(latestVersionName, BuildConfig.VERSION_NAME)) {
                                Badge()
                            }
                        }) {
                            Icon(
                                painter = painterResource(CoreR.drawable.settings),
                                contentDescription = stringResource(CoreR.string.settings),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            scrollBehavior = scrollBehavior,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = if (shouldUseFloatingTopBar) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                scrolledContainerColor = if (shouldUseFloatingTopBar) Color.Transparent else if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}
