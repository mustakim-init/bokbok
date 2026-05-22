package com.mustakim.bokbok.constants
import com.mustakim.bokbok.constants.GitHubReleasesJsonKey
import com.mustakim.bokbok.constants.GitHubReleasesEtagKey
import com.mustakim.bokbok.constants.LastNotifiedVersionKey
import com.mustakim.bokbok.constants.LastUpdateCheckKey
import com.mustakim.bokbok.constants.UpdateChannelKey
import com.mustakim.bokbok.constants.EnableUpdateNotificationKey

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

// Update settings
val EnableUpdateNotificationKey = booleanPreferencesKey("enableUpdateNotification")
val UpdateChannelKey = stringPreferencesKey("updateChannel")
val LastUpdateCheckKey = longPreferencesKey("lastUpdateCheck")
val LastNotifiedVersionKey = stringPreferencesKey("lastNotifiedVersion")

val GitHubContributorsEtagKey = stringPreferencesKey("github_contributors_etag")
val GitHubContributorsJsonKey = stringPreferencesKey("github_contributors_json")
val GitHubContributorsLastCheckedAtKey = longPreferencesKey("github_contributors_last_checked_at")

val GitHubReleasesEtagKey = stringPreferencesKey("github_releases_etag")
val GitHubReleasesJsonKey = stringPreferencesKey("github_releases_json")
val GitHubReleasesLastCheckedAtKey = longPreferencesKey("github_releases_last_checked_at")
val GitHubReleasesFingerprintKey = stringPreferencesKey("github_releases_fingerprint")

enum class UpdateChannel {
    STABLE,
    NIGHTLY,
}
