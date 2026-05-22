package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets

import com.mustakim.bokbok.music.ui.component.PreferenceGroupTitle
import com.mustakim.bokbok.music.constants.ListenBrainzEnabledKey
import com.mustakim.bokbok.music.constants.ListenBrainzTokenKey
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.InfoLabel
import com.mustakim.bokbok.music.ui.component.PreferenceEntry
import com.mustakim.bokbok.music.ui.component.SwitchPreference
import com.mustakim.bokbok.ui.shared.TextFieldDialog
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.data.local.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (listenBrainzEnabled, onListenBrainzEnabledChange) = rememberPreference(ListenBrainzEnabledKey, false)
    val (listenBrainzToken, onListenBrainzTokenChange) = rememberPreference(ListenBrainzTokenKey, "")

    var showListenBrainzTokenEditor = remember { mutableStateOf(false) }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        PreferenceGroupTitle(
                title = stringResource(MusicR.string.general),
            )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.discord_integration)) },
            icon = { Icon(painterResource(CoreR.drawable.discord), null) },
            onClick = {
                navController.navigate("settings/discord")
            },
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.scrobbling),
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.lastfm_integration)) },
            icon = { Icon(painterResource(CoreR.drawable.token), null) },
            onClick = {
                navController.navigate("settings/lastfm")
            },
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.listenbrainz_scrobbling)) },
            description = stringResource(MusicR.string.listenbrainz_scrobbling_description),
            icon = { Icon(painterResource(CoreR.drawable.token), null) },
            checked = listenBrainzEnabled,
            onCheckedChange = onListenBrainzEnabledChange,
        )
        PreferenceEntry(
            title = { Text(if (listenBrainzToken.isBlank()) stringResource(MusicR.string.set_listenbrainz_token) else stringResource(MusicR.string.edit_listenbrainz_token)) },
            icon = { Icon(painterResource(CoreR.drawable.token), null) },
            onClick = { showListenBrainzTokenEditor.value = true },
        )
    }


    if (showListenBrainzTokenEditor.value) {
        TextFieldDialog(
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(listenBrainzToken),
            onDone = { data ->
                onListenBrainzTokenChange(data)
                showListenBrainzTokenEditor.value = false
            },
            onDismiss = { showListenBrainzTokenEditor.value = false },
            singleLine = true,
            maxLines = 1,
            isInputValid = {
                it.isNotEmpty()
            },
            extraContent = {
                InfoLabel(text = stringResource(MusicR.string.listenbrainz_scrobbling_description))
            }
        )
    }
}
