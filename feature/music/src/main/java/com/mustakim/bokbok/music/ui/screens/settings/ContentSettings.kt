package com.mustakim.bokbok.music.ui.screens.settings
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import com.mustakim.bokbok.ui.shared.BokBokIconButton

import android.annotation.TargetApi
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.provider.Settings
import android.os.LocaleList
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.toLowerCase
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.navigation.NavController
import com.mustakim.bokbok.music.innertube.YouTube
import com.mustakim.bokbok.ui.shared.LocalPlayerAwareWindowInsets

import com.mustakim.bokbok.music.constants.*
import com.mustakim.bokbok.music.ui.component.*
import com.mustakim.bokbok.music.utils.directory.LocalMusicSyncWorker
import com.mustakim.bokbok.music.ui.utils.backToMain
import com.mustakim.bokbok.data.local.rememberEnumPreference
import com.mustakim.bokbok.data.local.rememberPreference
import com.mustakim.bokbok.music.utils.setAppLocale
import java.net.Proxy
import java.util.Locale
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    // Used only before Android 13
    val (appLanguage, onAppLanguageChange) = rememberPreference(key = AppLanguageKey, defaultValue = SYSTEM_DEFAULT)

    val (contentLanguage, onContentLanguageChange) = rememberPreference(key = ContentLanguageKey, defaultValue = "system")
    val (contentCountry, onContentCountryChange) = rememberPreference(key = ContentCountryKey, defaultValue = "system")
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideo, onHideVideoChange) = rememberPreference(key = HideVideoKey, defaultValue = false)
    val (proxyEnabled, onProxyEnabledChange) = rememberPreference(key = ProxyEnabledKey, defaultValue = false)
    val (proxyType, onProxyTypeChange) = rememberEnumPreference(key = ProxyTypeKey, defaultValue = Proxy.Type.HTTP)
    val (proxyUrl, onProxyUrlChange) = rememberPreference(key = ProxyUrlKey, defaultValue = "host:port")
    val (streamBypassProxy, onStreamBypassProxyChange) = rememberPreference(key = StreamBypassProxyKey, defaultValue = false)
    val (enableKugou, onEnableKugouChange) = rememberPreference(key = EnableKugouKey, defaultValue = true)
    val (enableLrclib, onEnableLrclibChange) = rememberPreference(key = EnableLrcLibKey, defaultValue = true)
    val (enableBetterLyrics, onEnableBetterLyricsChange) = rememberPreference(key = EnableBetterLyricsKey, defaultValue = true)
    val (enableSimpMusicLyrics, onEnableSimpMusicLyricsChange) =
        rememberPreference(key = EnableSimpMusicLyricsKey, defaultValue = true)
    val (preferredProvider, onPreferredProviderChange) =
        rememberEnumPreference(
            key = PreferredLyricsProviderKey,
            defaultValue = PreferredLyricsProvider.LRCLIB,
        )
    val (lyricsRomanizeJapanese, onLyricsRomanizeJapaneseChange) = rememberPreference(LyricsRomanizeJapaneseKey, defaultValue = true)
    val (lyricsRomanizeKorean, onLyricsRomanizeKoreanChange) = rememberPreference(LyricsRomanizeKoreanKey, defaultValue = true)
    val (lyricsRomanizeChinese, onLyricsRomanizeChineseChange) = rememberPreference(LyricsRomanizeChineseKey, defaultValue = true)
    val (lyricsRomanizeHindi, onLyricsRomanizeHindiChange) = rememberPreference(LyricsRomanizeHindiKey, defaultValue = true)
    val (lyricsRomanizeOtherLanguages, onLyricsRomanizeOtherLanguagesChange) = rememberPreference(LyricsRomanizeOtherLanguagesKey, defaultValue = true)
    val (preloadQueueLyricsEnabled, onPreloadQueueLyricsEnabledChange) = rememberPreference(PreloadQueueLyricsEnabledKey, defaultValue = true)
    val (queueLyricsPreloadCount, onQueueLyricsPreloadCountChange) = rememberPreference(QueueLyricsPreloadCountKey, defaultValue = 1)
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")
    val (quickPicks, onQuickPicksChange) = rememberEnumPreference(key = QuickPicksKey, defaultValue = QuickPicks.QUICK_PICKS)
    val (includedDirectories, onIncludedDirectoriesChange) = rememberPreference(key = IncludedDirectoriesKey, defaultValue = "")

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current)
            .verticalScroll(rememberScrollState()),
    ) {
        PreferenceGroupTitle(title = stringResource(MusicR.string.general))
        ListPreference(
            title = { Text(stringResource(MusicR.string.content_language)) },
            icon = { Icon(painterResource(CoreR.drawable.language), null) },
            selectedValue = contentLanguage,
            values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
            valueText = {
                LanguageCodeToName.getOrElse(it) { stringResource(MusicR.string.system_default) }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()
                val languageTag = locale.toLanguageTag().replace("-Hant", "")
 
                YouTube.locale = YouTube.locale.copy(
                    hl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.language.takeIf { it in LanguageCodeToName }
                        ?: languageTag.takeIf { it in LanguageCodeToName }
                        ?: "en"
                )
 
                onContentLanguageChange(newValue)
            }
        )
        ListPreference(
            title = { Text(stringResource(MusicR.string.content_country)) },
            icon = { Icon(painterResource(CoreR.drawable.location_on), null) },
            selectedValue = contentCountry,
            values = listOf(SYSTEM_DEFAULT) + CountryCodeToName.keys.toList(),
            valueText = {
                CountryCodeToName.getOrElse(it) { stringResource(MusicR.string.system_default) }
            },
            onValueSelected = { newValue ->
                val locale = Locale.getDefault()
 
                YouTube.locale = YouTube.locale.copy(
                    gl = newValue.takeIf { it != SYSTEM_DEFAULT }
                        ?: locale.country.takeIf { it in CountryCodeToName }
                        ?: "US"
                )
 
                onContentCountryChange(newValue)
           }
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.hide_explicit)) },
            icon = { Icon(painterResource(CoreR.drawable.explicit), null) },
            checked = hideExplicit,
            onCheckedChange = onHideExplicitChange,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.hide_video)) },
            icon = { Icon(painterResource(CoreR.drawable.slow_motion_video), null) },
            checked = hideVideo,
            onCheckedChange = onHideVideoChange,
        )

        PreferenceGroupTitle(title = stringResource(MusicR.string.app_language))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PreferenceEntry(
                title = { Text(stringResource(MusicR.string.app_language)) },
                icon = { Icon(painterResource(CoreR.drawable.language), null) },
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APP_LOCALE_SETTINGS,
                            "package:${context.packageName}".toUri()
                        )
                    )
                }
            )
        }
        // Support for Android versions before Android 13
        else {
            ListPreference(
                title = { Text(stringResource(MusicR.string.app_language)) },
                icon = { Icon(painterResource(CoreR.drawable.language), null) },
                selectedValue = appLanguage,
                values = listOf(SYSTEM_DEFAULT) + LanguageCodeToName.keys.toList(),
                valueText = {
                    LanguageCodeToName.getOrElse(it) { stringResource(MusicR.string.system_default) }
                },
                onValueSelected = { langTag ->
                    val newLocale = langTag
                        .takeUnless { it == SYSTEM_DEFAULT }
                        ?.let { Locale.forLanguageTag(it) }
                        ?: Locale.getDefault()

                    onAppLanguageChange(langTag)
                    setAppLocale(context, newLocale)

                }
            )
        }

        PreferenceGroupTitle(title = stringResource(MusicR.string.proxy))
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.enable_proxy)) },
            icon = { Icon(painterResource(CoreR.drawable.wifi_proxy), null) },
            checked = proxyEnabled,
            onCheckedChange = onProxyEnabledChange,
        )
        if (proxyEnabled) {
            Column {
                ListPreference(
                    title = { Text(stringResource(MusicR.string.proxy_type)) },
                    selectedValue = proxyType,
                    values = listOf(Proxy.Type.HTTP, Proxy.Type.SOCKS),
                    valueText = { it.name },
                    onValueSelected = onProxyTypeChange,
                )
                EditTextPreference(
                    title = { Text(stringResource(MusicR.string.proxy_url)) },
                    value = proxyUrl,
                    onValueChange = onProxyUrlChange,
                )
                SwitchPreference(
                    title = { Text(stringResource(MusicR.string.stream_bypass_proxy)) },
                    description = stringResource(MusicR.string.stream_bypass_proxy_desc),
                    icon = { Icon(painterResource(CoreR.drawable.wifi_proxy), null) },
                    checked = streamBypassProxy,
                    onCheckedChange = {
                        onStreamBypassProxyChange(it)
                        YouTube.streamBypassProxy = it
                    },
                )
            }
        }

        PreferenceGroupTitle(title = stringResource(MusicR.string.lyrics))
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.enable_lrclib)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = enableLrclib,
            onCheckedChange = onEnableLrclibChange,
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.enable_kugou)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = enableKugou,
            onCheckedChange = onEnableKugouChange,
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.enable_betterlyrics)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = enableBetterLyrics,
            onCheckedChange = onEnableBetterLyricsChange,
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.enable_simpmusic_lyrics)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = enableSimpMusicLyrics,
            onCheckedChange = onEnableSimpMusicLyricsChange,
        )
        ListPreference(
            title = { Text(stringResource(MusicR.string.set_first_lyrics_provider)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            selectedValue = preferredProvider,
            values = listOf(
                PreferredLyricsProvider.LRCLIB,
                PreferredLyricsProvider.KUGOU,
                PreferredLyricsProvider.BETTER_LYRICS,
                PreferredLyricsProvider.SIMPMUSIC,
            ),
            valueText = {
                when (it) {
                    PreferredLyricsProvider.LRCLIB -> "LrcLib"
                    PreferredLyricsProvider.KUGOU -> "KuGou"
                    PreferredLyricsProvider.BETTER_LYRICS -> "BetterLyrics"
                    PreferredLyricsProvider.SIMPMUSIC -> "SimpMusic"
                }
            },
            onValueSelected = onPreferredProviderChange,
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.lyrics_romanize_japanese)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = lyricsRomanizeJapanese,
            onCheckedChange = onLyricsRomanizeJapaneseChange,
        )

        SwitchPreference(
            title = { Text(stringResource(MusicR.string.lyrics_romanize_korean)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = lyricsRomanizeKorean,
            onCheckedChange = onLyricsRomanizeKoreanChange,
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.lyrics_romanize_chinese)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = lyricsRomanizeChinese,
            onCheckedChange = onLyricsRomanizeChineseChange,
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.lyrics_romanize_hindi)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = lyricsRomanizeHindi,
            onCheckedChange = onLyricsRomanizeHindiChange,
        )
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.lyrics_romanize_other_languages)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = lyricsRomanizeOtherLanguages,
            onCheckedChange = onLyricsRomanizeOtherLanguagesChange,
        )
        // Queue lyrics pre-load settings
        SwitchPreference(
            title = { Text(stringResource(MusicR.string.preload_queue_lyrics)) },
            icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
            checked = preloadQueueLyricsEnabled,
            onCheckedChange = onPreloadQueueLyricsEnabledChange,
        )
        if (preloadQueueLyricsEnabled) {
            NumberPickerPreference(
                title = { Text(stringResource(MusicR.string.queue_lyrics_preload_count)) },
                icon = { Icon(painterResource(CoreR.drawable.lyrics), null) },
                value = queueLyricsPreloadCount,
                onValueChange = onQueueLyricsPreloadCountChange,
                minValue = 0,
                maxValue = 10,
                valueText = { if (it == 0) "Off" else it.toString() },
            )
        }

        PreferenceGroupTitle(title = stringResource(MusicR.string.misc))
        EditTextPreference(
            title = { Text(stringResource(MusicR.string.top_length)) },
            icon = { Icon(painterResource(CoreR.drawable.trending_up), null) },
            value = lengthTop,
            isInputValid = { it.toIntOrNull()?.let { num -> num > 0 } == true },
            onValueChange = onLengthTopChange,
        )
        ListPreference(
            title = { Text(stringResource(MusicR.string.set_quick_picks)) },
            icon = { Icon(painterResource(CoreR.drawable.home_outlined), null) },
            selectedValue = quickPicks,
            values = listOf(QuickPicks.QUICK_PICKS, QuickPicks.LAST_LISTEN, QuickPicks.DONT_SHOW),
            valueText = {
                when (it) {
                    QuickPicks.QUICK_PICKS -> stringResource(MusicR.string.quick_picks)
                    QuickPicks.LAST_LISTEN -> stringResource(MusicR.string.last_song_listened)
                    QuickPicks.DONT_SHOW -> stringResource(MusicR.string.dont_show)
                }
            },
            onValueSelected = onQuickPicksChange,
        )

        PreferenceGroupTitle(title = "Local Music")
        EditTextPreference(
            title = { Text("Included Directories") },
            value = includedDirectories,
            onValueChange = onIncludedDirectoriesChange,
        )
        PreferenceEntry(
            title = { Text("Sync Local Music Now") },
            icon = { Icon(painterResource(CoreR.drawable.sync), null) },
            onClick = {
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<LocalMusicSyncWorker>().build()
                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                Toast.makeText(context, "Sync started", Toast.LENGTH_SHORT).show()
            }
        )
    }

    TopAppBar(
        title = { Text(stringResource(MusicR.string.content)) },
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