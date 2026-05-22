package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets

import com.mustakim.bokbok.music.constants.ArtistSeparatorsKey
import com.mustakim.bokbok.music.constants.ExternalDownloaderEnabledKey
import com.mustakim.bokbok.music.constants.ExternalDownloaderPackageKey
import com.mustakim.bokbok.music.constants.AudioNormalizationKey
import com.mustakim.bokbok.music.constants.AudioOffload
import com.mustakim.bokbok.music.constants.AudioQuality
import com.mustakim.bokbok.music.constants.AudioQualityKey
import com.mustakim.bokbok.music.constants.NetworkMeteredKey
import com.mustakim.bokbok.music.constants.AutoDownloadOnLikeKey
import com.mustakim.bokbok.music.constants.AutoStartOnBluetoothKey
import com.mustakim.bokbok.music.constants.AutoSkipNextOnErrorKey
import com.mustakim.bokbok.music.constants.PauseOnDeviceMuteKey
import com.mustakim.bokbok.music.constants.PermanentShuffleKey
import com.mustakim.bokbok.music.constants.PersistentQueueKey

import com.mustakim.bokbok.music.constants.SkipSilenceKey
import com.mustakim.bokbok.music.constants.StopMusicOnTaskClearKey
import com.mustakim.bokbok.music.constants.WakelockKey
import com.mustakim.bokbok.music.constants.HistoryDuration
import com.mustakim.bokbok.music.constants.AudioCrossfadeDurationKey
import com.mustakim.bokbok.music.constants.PlayerStreamClient
import com.mustakim.bokbok.music.constants.PlayerStreamClientKey
import com.mustakim.bokbok.music.constants.SeekExtraSeconds
import com.mustakim.bokbok.music.ui.component.ArtistSeparatorsDialog
import com.mustakim.bokbok.music.ui.component.TagsManagementDialog
import com.mustakim.bokbok.ui.shared.TextFieldDialog
import com.mustakim.bokbok.music.ui.component.EnumListPreference
import com.mustakim.bokbok.ui.shared.BokBokIconButton
import com.mustakim.bokbok.ui.shared.ListDialog
import com.mustakim.bokbok.music.ui.component.PreferenceEntry
import com.mustakim.bokbok.music.ui.component.PreferenceGroupTitle
import com.mustakim.bokbok.music.ui.component.SliderPreference
import com.mustakim.bokbok.music.ui.component.CrossfadeSliderPreference
import com.mustakim.bokbok.music.ui.component.SwitchPreference
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.data.local.rememberEnumPreference
import com.mustakim.bokbok.data.local.rememberPreference
import com.mustakim.bokbok.music.LocalDatabase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (audioQuality, onAudioQualityChange) = rememberEnumPreference(
        AudioQualityKey,
        defaultValue = AudioQuality.AUTO
    )
    val (playerStreamClient, onPlayerStreamClientChange) = rememberEnumPreference(
        PlayerStreamClientKey,
        defaultValue = PlayerStreamClient.ANDROID_VR
    )
    val (networkMetered, onNetworkMeteredChange) = rememberPreference(
        NetworkMeteredKey,
        defaultValue = true
    )
    val (persistentQueue, onPersistentQueueChange) = rememberPreference(
        PersistentQueueKey,
        defaultValue = true
    )
    val (permanentShuffle, onPermanentShuffleChange) = rememberPreference(
        PermanentShuffleKey,
        defaultValue = false
    )
    val (skipSilence, onSkipSilenceChange) = rememberPreference(
        SkipSilenceKey,
        defaultValue = false
    )
    val (audioNormalization, onAudioNormalizationChange) = rememberPreference(
        AudioNormalizationKey,
        defaultValue = true
    )
    val (audioOffload, onAudioOffloadChange) = rememberPreference(
        AudioOffload,
        defaultValue = false
    )

    val (seekExtraSeconds, onSeekExtraSeconds) = rememberPreference(
        SeekExtraSeconds,
        defaultValue = false
    )

    val (autoDownloadOnLike, onAutoDownloadOnLikeChange) = rememberPreference(
        AutoDownloadOnLikeKey,
        defaultValue = false
    )
    val (autoSkipNextOnError, onAutoSkipNextOnErrorChange) = rememberPreference(
        AutoSkipNextOnErrorKey,
        defaultValue = false
    )
    val (pauseOnDeviceMute, onPauseOnDeviceMuteChange) = rememberPreference(
        PauseOnDeviceMuteKey,
        defaultValue = false
    )
    val (autoStartOnBluetooth, onAutoStartOnBluetoothChange) = rememberPreference(
        AutoStartOnBluetoothKey,
        defaultValue = false
    )
    val (stopMusicOnTaskClear, onStopMusicOnTaskClearChange) = rememberPreference(
        StopMusicOnTaskClearKey,
        defaultValue = false
    )
    val (historyDuration, onHistoryDurationChange) = rememberPreference(
        HistoryDuration,
        defaultValue = 30f
    )

    val (audioCrossfadeSeconds, onAudioCrossfadeSecondsChange) = rememberPreference(
        AudioCrossfadeDurationKey,
        defaultValue = 0
    )

    val (artistSeparators, onArtistSeparatorsChange) = rememberPreference(
        ArtistSeparatorsKey,
        defaultValue = ",;/&"
    )
    val (externalDownloaderEnabled, onExternalDownloaderEnabledChange) = rememberPreference(
        ExternalDownloaderEnabledKey,
        defaultValue = false
    )
    val (externalDownloaderPackage, onExternalDownloaderPackageChange) = rememberPreference(
        ExternalDownloaderPackageKey,
        defaultValue = ""
    )

    val (wakelockEnabled, onWakelockChange) = rememberPreference(
        WakelockKey,
        defaultValue = false
    )

    var showArtistSeparatorsDialog by remember { mutableStateOf(false) }
    var showTagsManagementDialog by remember { mutableStateOf(false) }
    var showPlayerStreamClientDialog by remember { mutableStateOf(false) }
    var showExternalDownloaderPackageDialog by remember { mutableStateOf(false) }
    val database = LocalDatabase.current

    if (showArtistSeparatorsDialog) {
        ArtistSeparatorsDialog(
            currentSeparators = artistSeparators,
            onDismiss = { showArtistSeparatorsDialog = false },
            onSave = { newSeparators ->
                onArtistSeparatorsChange(newSeparators)
                showArtistSeparatorsDialog = false
            }
        )
    }

    if (showTagsManagementDialog) {
        TagsManagementDialog(
            database = database,
            onDismiss = { showTagsManagementDialog = false }
        )
    }

    if (showExternalDownloaderPackageDialog) {
        TextFieldDialog(
            initialTextFieldValue = androidx.compose.ui.text.input.TextFieldValue(externalDownloaderPackage),
            onDone = { pkg ->
                onExternalDownloaderPackageChange(pkg)
                showExternalDownloaderPackageDialog = false
            },
            onDismiss = { showExternalDownloaderPackageDialog = false },
            singleLine = true,
            maxLines = 1,
        )
    }

    if (showPlayerStreamClientDialog) {
        ListDialog(
            onDismiss = { showPlayerStreamClientDialog = false },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            items(listOf(PlayerStreamClient.ANDROID_VR, PlayerStreamClient.WEB_REMIX)) { value ->
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPlayerStreamClientChange(value)
                            showPlayerStreamClientDialog = false
                        }.padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(
                        selected = value == playerStreamClient,
                        onClick = null,
                    )

                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        Text(
                            text =
                            when (value) {
                                PlayerStreamClient.ANDROID_VR -> stringResource(MusicR.string.player_stream_client_android_vr)
                                else -> stringResource(MusicR.string.player_stream_client_web_remix)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text =
                            when (value) {
                                PlayerStreamClient.ANDROID_VR -> stringResource(MusicR.string.player_stream_client_android_vr_desc)
                                else -> stringResource(MusicR.string.player_stream_client_web_remix_desc)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
    ) {

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.player)
        )

        EnumListPreference(
            title = { Text(stringResource(MusicR.string.audio_quality)) },
            icon = { Icon(painterResource(CoreR.drawable.graphic_eq), null) },
            selectedValue = audioQuality,
            onValueSelected = onAudioQualityChange,
            valueText = {
                when (it) {
                    AudioQuality.HIGHEST -> stringResource(MusicR.string.audio_quality_max)
                    AudioQuality.HIGH -> stringResource(MusicR.string.audio_quality_high)
                    AudioQuality.AUTO -> stringResource(MusicR.string.audio_quality_auto)
                    AudioQuality.LOW -> stringResource(MusicR.string.audio_quality_low)
                }
            }
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.player_stream_client)) },
            description =
            when (playerStreamClient) {
                PlayerStreamClient.ANDROID_VR -> stringResource(MusicR.string.player_stream_client_android_vr)
                else -> stringResource(MusicR.string.player_stream_client_web_remix)
            },
            icon = { Icon(painterResource(CoreR.drawable.integration), null) },
            onClick = { showPlayerStreamClientDialog = true }
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.network_metered_title)) },
            description = stringResource(MusicR.string.network_metered_description),
            icon = { Icon(painterResource(CoreR.drawable.android_cell), null) },
            checked = networkMetered,
            onCheckedChange = onNetworkMeteredChange
        )

        SliderPreference(
            title = { Text(stringResource(MusicR.string.history_duration)) },
            icon = { Icon(painterResource(CoreR.drawable.history), null) },
            value = historyDuration,
            onValueChange = onHistoryDurationChange,
        )

        CrossfadeSliderPreference(
            value = audioCrossfadeSeconds,
            onValueChange = onAudioCrossfadeSecondsChange,
            isEnabled = !audioOffload,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.skip_silence)) },
            icon = { Icon(painterResource(CoreR.drawable.fast_forward), null) },
            checked = skipSilence,
            onCheckedChange = onSkipSilenceChange,
            isEnabled = !audioOffload,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.audio_normalization)) },
            icon = { Icon(painterResource(CoreR.drawable.volume_up), null) },
            checked = audioNormalization,
            onCheckedChange = onAudioNormalizationChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.audio_offload)) },
            description = stringResource(MusicR.string.audio_offload_desc),
            icon = { Icon(painterResource(CoreR.drawable.speed), null) },
            checked = audioOffload,
            onCheckedChange = { enabled ->
                onAudioOffloadChange(enabled)
                if (enabled) {
                    onSkipSilenceChange(false)
                }
            }
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.seek_seconds_addup)) },
            description = stringResource(MusicR.string.seek_seconds_addup_description),
            icon = { Icon(painterResource(CoreR.drawable.arrow_forward), null) },
            checked = seekExtraSeconds,
            onCheckedChange = onSeekExtraSeconds
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.pause_on_device_mute)) },
            description = stringResource(MusicR.string.pause_on_device_mute_desc),
            icon = { Icon(painterResource(CoreR.drawable.volume_off), null) },
            checked = pauseOnDeviceMute,
            onCheckedChange = onPauseOnDeviceMuteChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.auto_start_on_bluetooth)) },
            description = stringResource(MusicR.string.auto_start_on_bluetooth_desc),
            icon = { Icon(painterResource(CoreR.drawable.bluetooth), null) },
            checked = autoStartOnBluetooth,
            onCheckedChange = onAutoStartOnBluetoothChange
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.queue)
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.persistent_queue)) },
            description = stringResource(MusicR.string.persistent_queue_desc),
            icon = { Icon(painterResource(CoreR.drawable.queue_music), null) },
            checked = persistentQueue,
            onCheckedChange = onPersistentQueueChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.permanent_shuffle)) },
            description = stringResource(MusicR.string.permanent_shuffle_desc),
            icon = { Icon(painterResource(CoreR.drawable.shuffle), null) },
            checked = permanentShuffle,
            onCheckedChange = onPermanentShuffleChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.auto_download_on_like)) },
            description = stringResource(MusicR.string.auto_download_on_like_desc),
            icon = { Icon(painterResource(CoreR.drawable.download), null) },
            checked = autoDownloadOnLike,
            onCheckedChange = onAutoDownloadOnLikeChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.auto_skip_next_on_error)) },
            description = stringResource(MusicR.string.auto_skip_next_on_error_desc),
            icon = { Icon(painterResource(CoreR.drawable.skip_next), null) },
            checked = autoSkipNextOnError,
            onCheckedChange = onAutoSkipNextOnErrorChange
        )

        PreferenceGroupTitle(
            title = stringResource(MusicR.string.misc)
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.stop_music_on_task_clear)) },
            icon = { Icon(painterResource(CoreR.drawable.clear_all), null) },
            checked = stopMusicOnTaskClear,
            onCheckedChange = onStopMusicOnTaskClearChange
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.wakelock)) },
            description = stringResource(MusicR.string.wakelock_desc),
            icon = { Icon(painterResource(CoreR.drawable.bolt), null) },
            checked = wakelockEnabled,
            onCheckedChange = onWakelockChange
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.artist_separators)) },
            description = artistSeparators.map { "\"$it\"" }.joinToString("  "),
            icon = { Icon(painterResource(CoreR.drawable.artist), null) },
            onClick = { showArtistSeparatorsDialog = true }
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.manage_playlist_tags)) },
            description = stringResource(MusicR.string.manage_playlist_tags_desc),
            icon = { Icon(painterResource(CoreR.drawable.style), null) },
            onClick = { showTagsManagementDialog = true }
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.external_downloader)) },
            description = stringResource(MusicR.string.external_downloader_desc),
            icon = { Icon(painterResource(CoreR.drawable.download), null) },
            checked = externalDownloaderEnabled,
            onCheckedChange = onExternalDownloaderEnabledChange
        )

        PreferenceEntry(
            title = { Text(stringResource(MusicR.string.external_downloader_package)) },
            description = externalDownloaderPackage.ifEmpty { stringResource(MusicR.string.external_downloader_package_desc) },
            icon = { Icon(painterResource(CoreR.drawable.integration), null) },
            onClick = { showExternalDownloaderPackageDialog = true },
            isEnabled = externalDownloaderEnabled
        )
    }

}
