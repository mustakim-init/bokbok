package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.mustakim.bokbok.music.BuildConfig


@Composable
fun buildQuickActions(
    navController: NavController,
    resetSearch: () -> Unit,
): List<SettingsQuickAction> =
    listOf(
        SettingsQuickAction(
            icon = painterResource(CoreR.drawable.palette),
            label = stringResource(MusicR.string.appearance),
            onClick = { resetSearch(); navController.navigate("settings/appearance") },
            accentColor = MaterialTheme.colorScheme.primary,
        ),
        SettingsQuickAction(
            icon = painterResource(CoreR.drawable.play),
            label = stringResource(MusicR.string.player_and_audio),
            onClick = { resetSearch(); navController.navigate("settings/player") },
            accentColor = MaterialTheme.colorScheme.tertiary,
        ),
        SettingsQuickAction(
            icon = painterResource(CoreR.drawable.storage),
            label = stringResource(MusicR.string.storage),
            onClick = { resetSearch(); navController.navigate("settings/storage") },
            accentColor = MaterialTheme.colorScheme.secondary,
        ),
        SettingsQuickAction(
            icon = painterResource(CoreR.drawable.security),
            label = stringResource(MusicR.string.privacy),
            onClick = { resetSearch(); navController.navigate("settings/privacy") },
            accentColor = MaterialTheme.colorScheme.error,
        ),
    )

@Composable
fun buildIntegrationActions(
    navController: NavController,
    resetSearch: () -> Unit,
): List<SettingsIntegrationAction> =
    listOf(
        SettingsIntegrationAction(
            icon = painterResource(CoreR.drawable.discord),
            label = stringResource(MusicR.string.discord),
            onClick = { resetSearch(); navController.navigate("settings/discord") },
            accentColor = Color(0xFF5865F2),
        ),
        SettingsIntegrationAction(
            icon = painterResource(CoreR.drawable.integration),
            label = stringResource(MusicR.string.integration),
            onClick = { resetSearch(); navController.navigate("settings/integration") },
            accentColor = MaterialTheme.colorScheme.secondary,
        ),

    )

@Composable
fun buildSettingsGroups(
    navController: NavController,
    isAndroid12OrLater: Boolean,
    hasUpdate: Boolean,
    context: Context,
    resetSearch: () -> Unit,
): List<SettingsGroup> =
    buildList {
        add(
            SettingsGroup(
                title = stringResource(MusicR.string.settings_section_ui),
                items = listOf(
                    SettingsItem(
                        icon = painterResource(CoreR.drawable.palette),
                        title = stringResource(MusicR.string.appearance),
                        subtitle = stringResource(MusicR.string.dark_theme),
                        accentColor = MaterialTheme.colorScheme.primary,
                        keywords = listOf("theme", "palette", "material you", "dynamic color", "font", "ui"),
                        onClick = { resetSearch(); navController.navigate("settings/appearance") },
                    ),
                ),
            ),
        )

        add(
            SettingsGroup(
                title = stringResource(MusicR.string.settings_section_player_content),
                items = listOf(
                    SettingsItem(
                        icon = painterResource(CoreR.drawable.play),
                        title = stringResource(MusicR.string.player_and_audio),
                        subtitle = stringResource(MusicR.string.audio_quality),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        keywords = listOf("audio", "playback", "volume", "quality", "equalizer", "crossfade"),
                        onClick = { resetSearch(); navController.navigate("settings/player") },
                    ),
                    SettingsItem(
                        icon = painterResource(CoreR.drawable.language),
                        title = stringResource(MusicR.string.content),
                        subtitle = stringResource(MusicR.string.content_language),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        keywords = listOf("language", "content", "lyrics", "translation", "region"),
                        onClick = { resetSearch(); navController.navigate("settings/content") },
                    ),
                    SettingsItem(
                        icon = painterResource(CoreR.drawable.token),
                        title = stringResource(MusicR.string.po_token_generation),
                        subtitle = stringResource(MusicR.string.po_token_generation_subtitle),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        keywords = listOf("po token", "token", "web client", "visitor data", "gvs", "player"),
                        onClick = { resetSearch(); navController.navigate("settings/po_token") },
                    ),
                ),
            ),
        )

        add(
            SettingsGroup(
                title = stringResource(MusicR.string.settings_section_privacy),
                items = listOf(
                    SettingsItem(
                        icon = painterResource(CoreR.drawable.security),
                        title = stringResource(MusicR.string.privacy),
                        subtitle = stringResource(MusicR.string.pause_listen_history),
                        accentColor = MaterialTheme.colorScheme.error,
                        keywords = listOf("privacy", "history", "tracking", "security", "permissions"),
                        onClick = { resetSearch(); navController.navigate("settings/privacy") },
                    ),
                ),
            ),
        )

        add(
            SettingsGroup(
                title = stringResource(MusicR.string.settings_section_storage),
                items = listOf(
                    SettingsItem(
                        icon = painterResource(CoreR.drawable.storage),
                        title = stringResource(MusicR.string.storage),
                        subtitle = stringResource(MusicR.string.cache),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        keywords = listOf("storage", "cache", "offline", "downloads", "cleanup"),
                        onClick = { resetSearch(); navController.navigate("settings/storage") },
                    ),
                    SettingsItem(
                        icon = painterResource(CoreR.drawable.restore),
                        title = stringResource(MusicR.string.backup_restore),
                        subtitle = stringResource(MusicR.string.action_backup),
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        keywords = listOf("backup", "restore", "import", "export", "migration"),
                        onClick = { resetSearch(); navController.navigate("settings/backup_restore") },
                    ),
                ),
            ),
        )

        add(
            SettingsGroup(
                title = stringResource(MusicR.string.settings_section_system),
                items = buildList {
                    if (isAndroid12OrLater) {
                        add(
                            SettingsItem(
                                icon = painterResource(CoreR.drawable.link),
                                title = stringResource(MusicR.string.default_links),
                                subtitle = stringResource(MusicR.string.open_supported_links),
                                accentColor = MaterialTheme.colorScheme.primary,
                                keywords = listOf("links", "deeplink", "default", "supported links"),
                                onClick = {
                                    resetSearch()
                                    try {
                                        val intent = Intent(
                                            Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        when (e) {
                                            is ActivityNotFoundException,
                                            is SecurityException,
                                            -> {
                                                Toast.makeText(
                                                    context,
                                                    MusicR.string.open_app_settings_error,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                            else -> {
                                                Toast.makeText(
                                                    context,
                                                    MusicR.string.open_app_settings_error,
                                                    Toast.LENGTH_LONG,
                                                ).show()
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                    }
                    add(
                        SettingsItem(
                            icon = painterResource(CoreR.drawable.experiment),
                            title = stringResource(MusicR.string.experiment_settings),
                            subtitle = stringResource(MusicR.string.misc),
                            accentColor = MaterialTheme.colorScheme.tertiary,
                            keywords = listOf("experimental", "debug", "developer", "labs", "internal"),
                            onClick = { resetSearch(); navController.navigate("settings/misc") },
                        ),
                    )
                    add(
                        SettingsItem(
                            icon = painterResource(CoreR.drawable.bolt),
                            title = "Battery Optimization",
                            subtitle = "Manage background restrictions",
                            accentColor = MaterialTheme.colorScheme.secondary,
                            keywords = listOf("battery", "optimization", "background", "restriction"),
                            onClick = { resetSearch(); navController.navigate("battery_optimization") },
                        ),
                    )
                    add(
                        SettingsItem(
                            icon = painterResource(CoreR.drawable.experiment),
                            title = "Experimental Features",
                            subtitle = "Advanced toggles and tweaks",
                            accentColor = MaterialTheme.colorScheme.error,
                            keywords = listOf("experimental", "adb", "resurrection", "developer"),
                            onClick = { resetSearch(); navController.navigate("settings/experimental") },
                        ),
                    )
                    add(
                        SettingsItem(
                            icon = painterResource(CoreR.drawable.update),
                            title = stringResource(MusicR.string.updates),
                            subtitle = if (hasUpdate) {
                                stringResource(MusicR.string.new_version_available)
                            } else {
                                BuildConfig.VERSION_NAME
                            },
                            showUpdateIndicator = hasUpdate,
                            accentColor = if (hasUpdate) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                            keywords = listOf("update", "version", "release", "changelog"),
                            onClick = { resetSearch(); navController.navigate("settings/update") },
                        ),
                    )
                    add(
                        SettingsItem(
                            icon = painterResource(CoreR.drawable.info),
                            title = stringResource(MusicR.string.about),
                            subtitle = "BokBok",
                            accentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            keywords = listOf("about", "app info", "license", "contributors"),
                            onClick = { resetSearch(); navController.navigate("music_about") },
                        ),
                    )
                },
            ),
        )
    }

@Composable
fun buildInternalItems(
    navController: NavController,
    resetSearch: () -> Unit,
): List<SettingsItem> =
    listOf(
        SettingsItem(
            icon = painterResource(CoreR.drawable.palette),
            title = stringResource(MusicR.string.theme_creator_title),
            subtitle = stringResource(MusicR.string.theme_creator_subtitle),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("theme", "creator", "seed", "material", "palette", "import", "export"),
            onClick = { resetSearch(); navController.navigate("settings/appearance/theme_creator") },
        ),
        SettingsItem(
            icon = painterResource(CoreR.drawable.palette),
            title = stringResource(MusicR.string.customize_colors),
            subtitle = stringResource(MusicR.string.appearance),
            accentColor = MaterialTheme.colorScheme.primary,
            keywords = listOf("palette", "color", "accent", "tone", "dynamic color"),
            onClick = { resetSearch(); navController.navigate("settings/appearance/palette_picker") },
        ),
        SettingsItem(
            icon = painterResource(CoreR.drawable.image),
            title = stringResource(MusicR.string.customize_background_title),
            subtitle = stringResource(MusicR.string.appearance),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("background", "wallpaper", "image", "blur", "gradient"),
            onClick = { resetSearch(); navController.navigate("customize_background") },
        ),
        SettingsItem(
            icon = painterResource(CoreR.drawable.discord),
            title = stringResource(MusicR.string.discord_integration),
            subtitle = stringResource(MusicR.string.integration),
            accentColor = Color(0xFF5865F2),
            keywords = listOf("discord", "rpc", "rich presence", "status", "activity"),
            onClick = { resetSearch(); navController.navigate("settings/discord") },
        ),
        SettingsItem(
            icon = painterResource(CoreR.drawable.security),
            title = stringResource(MusicR.string.advanced_login),
            subtitle = stringResource(MusicR.string.discord),
            accentColor = Color(0xFF5865F2),
            keywords = listOf("token", "login", "authentication", "discord login"),
            onClick = { resetSearch(); navController.navigate("settings/discord/login") },
        ),
        SettingsItem(
            icon = painterResource(CoreR.drawable.experiment),
            title = stringResource(MusicR.string.experimental_features),
            subtitle = stringResource(MusicR.string.experimental_features_description),
            accentColor = MaterialTheme.colorScheme.tertiary,
            keywords = listOf("experimental", "labs", "advanced", "discord experimental", "internal"),
            onClick = { resetSearch(); navController.navigate("settings/discord/experimental") },
        ),
        SettingsItem(
            icon = painterResource(CoreR.drawable.integration),
            title = stringResource(MusicR.string.lastfm_integration),
            subtitle = stringResource(MusicR.string.integration),
            accentColor = MaterialTheme.colorScheme.secondary,
            keywords = listOf("lastfm", "last.fm", "scrobble", "listening history"),
            onClick = { resetSearch(); navController.navigate("settings/lastfm") },
        ),

    )
