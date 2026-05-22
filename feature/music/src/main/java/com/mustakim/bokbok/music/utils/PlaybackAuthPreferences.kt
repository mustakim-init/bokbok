package com.mustakim.bokbok.music.utils

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import com.mustakim.bokbok.music.constants.AccountChannelHandleKey
import com.mustakim.bokbok.music.constants.AccountEmailKey
import com.mustakim.bokbok.music.constants.AccountNameKey
import com.mustakim.bokbok.music.constants.DataSyncIdKey
import com.mustakim.bokbok.music.constants.InnerTubeCookieKey
import com.mustakim.bokbok.music.constants.PoTokenGvsKey
import com.mustakim.bokbok.music.constants.PoTokenKey
import com.mustakim.bokbok.music.constants.PoTokenPlayerKey
import com.mustakim.bokbok.music.constants.PoTokenSourceUrlKey
import com.mustakim.bokbok.music.constants.VisitorDataKey
import com.mustakim.bokbok.music.constants.WebClientPoTokenEnabledKey
import com.mustakim.bokbok.music.innertube.PlaybackAuthState

fun Preferences.toPlaybackAuthState(): PlaybackAuthState =
    PlaybackAuthState(
        cookie = this[InnerTubeCookieKey],
        visitorData = this[VisitorDataKey],
        dataSyncId = this[DataSyncIdKey],
        poToken = this[PoTokenKey],
        poTokenGvs = this[PoTokenGvsKey],
        poTokenPlayer = this[PoTokenPlayerKey],
        webClientPoTokenEnabled = this[WebClientPoTokenEnabledKey] ?: false,
    ).normalized()

fun MutablePreferences.clearPlaybackAuthSession(clearAccountIdentity: Boolean = true) {
    remove(InnerTubeCookieKey)
    remove(VisitorDataKey)
    remove(DataSyncIdKey)
    remove(PoTokenKey)
    remove(PoTokenGvsKey)
    remove(PoTokenPlayerKey)
    remove(PoTokenSourceUrlKey)
    if (clearAccountIdentity) {
        remove(AccountNameKey)
        remove(AccountEmailKey)
        remove(AccountChannelHandleKey)
    }
}

fun MutablePreferences.clearPlaybackLoginContext() {
    remove(DataSyncIdKey)
}

fun MutablePreferences.putLegacyPoToken(value: String?) {
    val normalized = value?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
    if (normalized == null) {
        remove(PoTokenKey)
    } else {
        this[PoTokenKey] = normalized
    }
    remove(PoTokenGvsKey)
    remove(PoTokenPlayerKey)
}
