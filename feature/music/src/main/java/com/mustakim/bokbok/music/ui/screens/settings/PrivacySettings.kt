@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mustakim.bokbok.music.LocalDatabase
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets

import com.mustakim.bokbok.music.constants.DisableScreenshotKey
import com.mustakim.bokbok.music.constants.PauseListenHistoryKey
import com.mustakim.bokbok.music.constants.PauseSearchHistoryKey
import com.mustakim.bokbok.ui.shared.DefaultDialog
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.music.ui.component.PreferenceEntry
import com.mustakim.bokbok.music.ui.component.PreferenceGroupTitle
import com.mustakim.bokbok.music.ui.component.SwitchPreference
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.data.local.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val database = LocalDatabase.current
    val (pauseListenHistory, onPauseListenHistoryChange) = rememberPreference(
        key = PauseListenHistoryKey,
        defaultValue = false
    )
    val (pauseSearchHistory, onPauseSearchHistoryChange) = rememberPreference(
        key = PauseSearchHistoryKey,
        defaultValue = false
    )
    val (disableScreenshot, onDisableScreenshotChange) = rememberPreference(
        key = DisableScreenshotKey,
        defaultValue = false
    )

    var showClearListenHistoryDialog by remember {
        mutableStateOf(false)
    }

    if (showClearListenHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearListenHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(MusicR.string.clear_listen_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearListenHistoryDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearListenHistoryDialog = false
                        database.query {
                            clearListenHistory()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showClearSearchHistoryDialog by remember {
        mutableStateOf(false)
    }

    if (showClearSearchHistoryDialog) {
        DefaultDialog(
            onDismiss = { showClearSearchHistoryDialog = false },
            content = {
                Text(
                    text = stringResource(MusicR.string.clear_search_history_confirm),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showClearSearchHistoryDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showClearSearchHistoryDialog = false
                        database.query {
                            clearSearchHistory()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.listen_history)
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.pause_listen_history)) },
            icon = { Icon(painterResource(CoreR.drawable.history), null) },
            checked = pauseListenHistory,
            onCheckedChange = onPauseListenHistoryChange,
        )
        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.clear_listen_history)) },
            icon = { Icon(painterResource(CoreR.drawable.delete_history), null) },
            onClick = { showClearListenHistoryDialog = true },
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.search_history)
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.pause_search_history)) },
            icon = { Icon(painterResource(CoreR.drawable.search_off), null) },
            checked = pauseSearchHistory,
            onCheckedChange = onPauseSearchHistoryChange,
        )
        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.clear_search_history)) },
            icon = { Icon(painterResource(CoreR.drawable.clear_all), null) },
            onClick = { showClearSearchHistoryDialog = true },
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.misc),
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.disable_screenshot)) },
            description = stringResource(MusicR.string.disable_screenshot_desc),
            icon = { Icon(painterResource(CoreR.drawable.screenshot), null) },
            checked = disableScreenshot,
            onCheckedChange = onDisableScreenshotChange,
        )
    }

}
