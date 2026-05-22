@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets
import com.mustakim.bokbok.music.LocalPlayerConnection

import com.mustakim.bokbok.music.constants.MaxCanvasCacheSizeKey
import com.mustakim.bokbok.music.constants.MaxImageCacheSizeKey
import com.mustakim.bokbok.music.constants.MaxSongCacheSizeKey
import com.mustakim.bokbok.music.constants.SmartTrimmerKey
import com.mustakim.bokbok.music.extensions.directorySizeBytes
import com.mustakim.bokbok.music.extensions.tryOrNull
import com.mustakim.bokbok.ui.shared.ActionPromptDialog
import com.mustakim.bokbok.ui.shared.DefaultDialog
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.music.ui.component.ListPreference
import com.mustakim.bokbok.music.ui.component.PreferenceEntry
import com.mustakim.bokbok.music.ui.component.SwitchPreference
import com.mustakim.bokbok.music.ui.component.PreferenceGroupTitle
import com.mustakim.bokbok.music.ui.player.CanvasArtworkPlaybackCache
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.music.ui.utils.formatFileSize
import com.mustakim.bokbok.data.local.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoilApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StorageSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current
    val imageDiskCache = context.imageLoader.diskCache ?: return
    val playerCache = LocalPlayerConnection.current?.service?.playerCache ?: return
    val downloadCache = LocalPlayerConnection.current?.service?.downloadCache ?: return
    
    val downloadCacheDir = remember { context.filesDir.resolve("download") }
    val playerCacheDir = remember { context.filesDir.resolve("exoplayer") }

    val coroutineScope = rememberCoroutineScope()
    val (smartTrimmer, onSmartTrimmerChange) = rememberPreference(
        key = SmartTrimmerKey,
        defaultValue = false
    )
    val (maxImageCacheSize, onMaxImageCacheSizeChange) = rememberPreference(
        key = MaxImageCacheSizeKey,
        defaultValue = 512
    )
    val (maxSongCacheSize, onMaxSongCacheSizeChange) = rememberPreference(
        key = MaxSongCacheSizeKey,
        defaultValue = 1024
    )
    val (maxCanvasCacheSize, onMaxCanvasCacheSizeChange) = rememberPreference(
        key = MaxCanvasCacheSizeKey,
        defaultValue = 256,
    )
    var clearCacheDialog by remember { mutableStateOf(false) }
    var clearDownloads by remember { mutableStateOf(false) }
    var clearImageCacheDialog by remember { mutableStateOf(false) }
    var clearCanvasCacheDialog by remember { mutableStateOf(false) }

    var imageCacheSize by remember {
        mutableStateOf(imageDiskCache.size)
    }
    var playerCacheSize by remember {
        mutableStateOf(0L)
    }
    var downloadCacheSize by remember {
        mutableStateOf(0L)
    }
    var canvasCacheSize by remember {
        mutableStateOf(CanvasArtworkPlaybackCache.size())
    }
    val imageCacheProgress by animateFloatAsState(
        targetValue = if (imageDiskCache.maxSize > 0) {
            (imageCacheSize.toFloat() / imageDiskCache.maxSize).coerceIn(0f, 1f)
        } else 0f,
        label = "imageCacheProgress",
    )
    val maxSongCacheSizeBytes = if (maxSongCacheSize > 0) maxSongCacheSize * 1024 * 1024L else 0L
    val playerCacheProgress by animateFloatAsState(
        targetValue = if (maxSongCacheSizeBytes > 0) {
            (playerCacheSize.toFloat() / maxSongCacheSizeBytes).coerceIn(0f, 1f)
        } else 0f,
        label = "playerCacheProgress",
    )
    val canvasCacheProgress by animateFloatAsState(
        targetValue = if (maxCanvasCacheSize > 0) {
            (canvasCacheSize.toFloat() / maxCanvasCacheSize).coerceIn(0f, 1f)
        } else 0f,
        label = "canvasCacheProgress",
    )

    val isSmartTrimmerAvailable = maxImageCacheSize != 0 || maxSongCacheSize != 0
    LaunchedEffect(isSmartTrimmerAvailable) {
        if (!isSmartTrimmerAvailable && smartTrimmer) onSmartTrimmerChange(false)
    }

    LaunchedEffect(maxImageCacheSize) {
        if (maxImageCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                imageDiskCache.clear()
                com.mustakim.bokbok.music.utils.ArtworkStorage.clear(context)
            }
        }
    }
    LaunchedEffect(maxSongCacheSize) {
        if (maxSongCacheSize == 0) {
            coroutineScope.launch(Dispatchers.IO) {
                playerCache.keys.forEach { key ->
                    playerCache.removeResource(key)
                }
            }
        }
    }
    LaunchedEffect(maxCanvasCacheSize) {
        CanvasArtworkPlaybackCache.setMaxSize(maxCanvasCacheSize)
        if (maxCanvasCacheSize == 0) {
            CanvasArtworkPlaybackCache.clear()
        }
    }

    LaunchedEffect(imageDiskCache) {
        while (isActive) {
            delay(500)
            imageCacheSize = imageDiskCache.size
        }
    }
    LaunchedEffect(playerCache, playerCacheDir) {
        while (isActive) {
            delay(500)
            playerCacheSize =
                withContext(Dispatchers.IO) {
                    val cacheSpace = tryOrNull { playerCache.cacheSpace } ?: 0L
                    if (cacheSpace == 0L) playerCacheDir.directorySizeBytes() else cacheSpace
                }
        }
    }
    LaunchedEffect(downloadCache, downloadCacheDir) {
        while (isActive) {
            delay(500)
            downloadCacheSize =
                withContext(Dispatchers.IO) {
                    val cacheSpace = tryOrNull { downloadCache.cacheSpace } ?: 0L
                    if (cacheSpace == 0L) downloadCacheDir.directorySizeBytes() else cacheSpace
                }
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500)
            canvasCacheSize = CanvasArtworkPlaybackCache.size()
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)
            )
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.music_folders)) },
            description = stringResource(MusicR.string.select_music_folders_description),
            onClick = { navController.navigate("settings/storage/folders") },
            icon = { Icon(Icons.Default.Folder, contentDescription = null) }
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.smart_trimmer)) },
            description = stringResource(MusicR.string.smart_trimmer_description),
            checked = smartTrimmer && isSmartTrimmerAvailable,
            onCheckedChange = onSmartTrimmerChange,
            isEnabled = isSmartTrimmerAvailable,
        )

        // --- Section: Downloads ---
        CacheCard(
            icon = CoreR.drawable.ic_download,
            title = stringResource(MusicR.string.downloaded_songs),
            description = stringResource(MusicR.string.size_used, formatFileSize(downloadCacheSize)),
            progress = null,
            actions = {
                PreferenceEntry(
                    title = { Text(stringResource(MusicR.string.clear_all_downloads)) },
                    onClick = { clearDownloads = true },
                )
            }
        )

        if (clearDownloads) {
            ActionPromptDialog(
                title = stringResource(MusicR.string.clear_all_downloads),
                onDismiss = { clearDownloads = false },
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        downloadCache.keys.forEach { key ->
                            downloadCache.removeResource(key)
                        }
                    }
                    clearDownloads = false
                },
                onCancel = { clearDownloads = false },
                content = {
                    Text(text = stringResource(MusicR.string.clear_downloads_dialog))
                }
            )
        }

        // --- Section: Song cache ---
        CacheCard(
            icon = CoreR.drawable.ic_music,
            title = stringResource(MusicR.string.song_cache),
            description = if (maxSongCacheSize == -1) {
                stringResource(MusicR.string.size_used, formatFileSize(playerCacheSize))
            } else {
                "${formatFileSize(playerCacheSize)} / ${formatFileSize(maxSongCacheSize * 1024 * 1024L)}"
            },
            progress = if (maxSongCacheSize > 0) playerCacheProgress else null,
            actions = {
                ListPreference(
                    title = { Text(stringResource(MusicR.string.max_cache_size)) },
                    selectedValue = maxSongCacheSize,
                    values = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192, -1),
                    valueText = {
                        when (it) {
                            0 -> stringResource(MusicR.string.disable)
                            -1 -> stringResource(MusicR.string.unlimited)
                            else -> formatFileSize(it * 1024 * 1024L)
                        }
                    },
                    onValueSelected = onMaxSongCacheSizeChange,
                )
                PreferenceEntry(
                    title = { Text(stringResource(MusicR.string.clear_song_cache)) },
                    onClick = { clearCacheDialog = true },
                )
            }
        )

        if (clearCacheDialog) {
            ActionPromptDialog(
                title = stringResource(MusicR.string.clear_song_cache),
                onDismiss = { clearCacheDialog = false },
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        playerCache.keys.forEach { key ->
                            playerCache.removeResource(key)
                        }
                    }
                    clearCacheDialog = false
                },
                onCancel = { clearCacheDialog = false },
                content = {
                    Text(text = stringResource(MusicR.string.clear_song_cache_dialog))
                }
            )
        }

        // --- Section: Image cache ---
        CacheCard(
            icon = CoreR.drawable.image,
            title = stringResource(MusicR.string.image_cache),
            description = if (maxImageCacheSize > 0) {
                "${formatFileSize(imageCacheSize)} / ${formatFileSize(imageDiskCache.maxSize)}"
            } else {
                stringResource(MusicR.string.disable)
            },
            progress = if (maxImageCacheSize > 0) imageCacheProgress else null,
            actions = {
                ListPreference(
                    title = { Text(stringResource(MusicR.string.max_cache_size)) },
                    selectedValue = maxImageCacheSize,
                    values = listOf(0, 128, 256, 512, 1024, 2048, 4096, 8192),
                    valueText = {
                        when (it) {
                            0 -> stringResource(MusicR.string.disable)
                            else -> formatFileSize(it * 1024 * 1024L)
                        }
                    },
                    onValueSelected = onMaxImageCacheSizeChange,
                )
                PreferenceEntry(
                    title = { Text(stringResource(MusicR.string.clear_image_cache)) },
                    onClick = { clearImageCacheDialog = true },
                )
            }
        )

        if (clearImageCacheDialog) {
            ActionPromptDialog(
                title = stringResource(MusicR.string.clear_image_cache),
                onDismiss = { clearImageCacheDialog = false },
                onConfirm = {
                    coroutineScope.launch(Dispatchers.IO) {
                        imageDiskCache.clear()
                        com.mustakim.bokbok.music.utils.ArtworkStorage.clear(context)
                    }
                    clearImageCacheDialog = false
                },
                onCancel = { clearImageCacheDialog = false },
                content = {
                    Text(text = stringResource(MusicR.string.clear_image_cache_dialog))
                }
            )
        }

        // --- Section: Canvas cache ---
        CacheCard(
            icon = CoreR.drawable.motion_photos_on,
            title = stringResource(MusicR.string.canvas_cache),
            description = if (maxCanvasCacheSize > 0) {
                stringResource(
                    MusicR.string.canvas_cache_usage,
                    stringResource(MusicR.string.canvas_cache_items, canvasCacheSize),
                    stringResource(MusicR.string.canvas_cache_items, maxCanvasCacheSize),
                )
            } else {
                stringResource(MusicR.string.disable)
            },
            progress = if (maxCanvasCacheSize > 0) canvasCacheProgress else null,
            actions = {
                ListPreference(
                    title = { Text(stringResource(MusicR.string.max_cache_size)) },
                    selectedValue = maxCanvasCacheSize,
                    values = listOf(0, 64, 128, 256, 512, 1024),
                    valueText = {
                        when (it) {
                            0 -> stringResource(MusicR.string.disable)
                            else -> stringResource(MusicR.string.canvas_cache_items, it)
                        }
                    },
                    onValueSelected = onMaxCanvasCacheSizeChange,
                )
                PreferenceEntry(
                    title = { Text(stringResource(MusicR.string.clear_canvas_cache)) },
                    onClick = { clearCanvasCacheDialog = true },
                )
            }
        )

        if (clearCanvasCacheDialog) {
            ActionPromptDialog(
                title = stringResource(MusicR.string.clear_canvas_cache),
                onDismiss = { clearCanvasCacheDialog = false },
                onConfirm = {
                    CanvasArtworkPlaybackCache.clear()
                    clearCanvasCacheDialog = false
                },
                onCancel = { clearCanvasCacheDialog = false },
                content = {
                    Text(text = stringResource(MusicR.string.clear_canvas_cache_dialog))
                }
            )
        }
    }

    TopAppBar(
        title = { Text(stringResource(MusicR.string.storage)) },
        navigationIcon = {
            BokBokIconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain,
            ) {
                Icon(
                    painterResource(CoreR.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        }
    )
}

@Composable
fun CacheCard(
    icon: Int,
    title: String,
    description: String,
    progress: Float?,
    actions: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Card(
                    modifier = Modifier.padding(end = 12.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                ) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (progress != null) {
                Spacer(Modifier.padding(top = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearWavyProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    // percent label
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.padding(4.dp))
            actions()
        }
    }
}
