package com.mustakim.bokbok.util
import com.mustakim.bokbok.constants.GitHubReleasesJsonKey
import com.mustakim.bokbok.constants.GitHubReleasesEtagKey
import com.mustakim.bokbok.util.Updater

import com.mustakim.bokbok.constants.*
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first
import android.os.Build
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey

object Updater {
    private const val GITHUB_API_URL = "https://api.github.com/repos/mustakim-init/bokbok/releases"
    private const val GITHUB_COMMITS_URL = "https://api.github.com/repos/mustakim-init/bokbok/commits"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 15000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 10000
        }
    }

    var lastCheckTime: Long = 0L

    @Serializable
    data class ReleaseInfo(
        val name: String,
        @SerialName("tag_name") val tagName: String,
        val body: String,
        @SerialName("html_url") val htmlUrl: String,
        val draft: Boolean,
        val prerelease: Boolean,
        @SerialName("published_at") val publishedAt: String,
        val assets: List<Asset> = emptyList()
    ) {
        // Compatibility Aliases
        val tag_name get() = tagName
        val html_url get() = htmlUrl
        val published_at get() = publishedAt
    }

    @Serializable
    data class Asset(
        val name: String,
        val size: Long,
        @SerialName("browser_download_url") val browserDownloadUrl: String
    ) {
        val browser_download_url get() = browserDownloadUrl
    }

    @Serializable
    data class GitCommit(
        val sha: String,
        val commit: CommitDetails,
        @SerialName("html_url") val htmlUrl: String
    ) {
        val html_url get() = htmlUrl
    }

    @Serializable
    data class CommitDetails(
        val message: String,
        val author: AuthorDetails
    )

    @Serializable
    data class AuthorDetails(
        val name: String,
        val date: String
    )

    fun isSameVersion(v1: String, v2: String): Boolean {
        val clean1 = v1.lowercase().removePrefix("v").replace("-nightly", "").replace("-beta", "").trim()
        val clean2 = v2.lowercase().removePrefix("v").replace("-nightly", "").replace("-beta", "").trim()
        return clean1 == clean2
    }

    suspend fun getLatestVersionName(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(GITHUB_API_URL)
            val releases: List<ReleaseInfo> = response.body()
            
            val latestStable = releases.firstOrNull { !it.draft && !it.prerelease }
                ?: releases.firstOrNull { !it.draft }
            
            lastCheckTime = System.currentTimeMillis()
            latestStable?.tagName ?: throw Exception("No releases found")
        }
    }

    suspend fun getLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(GITHUB_API_URL)
            val releases: List<ReleaseInfo> = response.body()
            
            lastCheckTime = System.currentTimeMillis()
            releases.firstOrNull { !it.draft } ?: throw Exception("No releases found")
        }
    }

    // Alias for getLatestReleaseInfo
    suspend fun getLatestReleaseInfo(): Result<ReleaseInfo> = getLatestRelease()

    suspend fun getReleaseHistory(): Result<List<ReleaseInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(GITHUB_API_URL)
            val releases: List<ReleaseInfo> = response.body()
            lastCheckTime = System.currentTimeMillis()
            releases.filter { !it.draft }
        }
    }
    
    // Alias for getAllReleases
    suspend fun getAllReleases(): Result<List<ReleaseInfo>> = getReleaseHistory()

    suspend fun getCommitHistory(): Result<List<GitCommit>> = withContext(Dispatchers.IO) {
        runCatching {
            val response = client.get(GITHUB_COMMITS_URL)
            response.body<List<GitCommit>>()
        }
    }

    /**
     * Specialized high-performance version check for StartupManager cold-start.
     */
    suspend fun checkUpdateFast(context: android.content.Context, currentVersion: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val cachedEtag = context.dataStore.getAsync(GitHubReleasesEtagKey) ?: ""
            
            val response = client.get(GITHUB_API_URL) {
                if (cachedEtag.isNotEmpty()) {
                    header("If-None-Match", cachedEtag)
                }
            }
            
            if (response.status.value == 304) {
                return@runCatching false 
            }
            
            val releases: List<ReleaseInfo> = response.body()
            val latest = releases.firstOrNull { !it.draft }
            
            if (latest != null) {
                val etag = response.headers["ETag"] ?: ""
                context.dataStore.saveAsync(GitHubReleasesEtagKey, etag)
                context.dataStore.saveAsync(GitHubReleasesJsonKey, json.encodeToString(ReleaseInfo.serializer(), latest))
                
                lastCheckTime = System.currentTimeMillis()
                !isSameVersion(latest.tagName, currentVersion)
            } else {
                false
            }
        }
    }

    suspend fun getCachedReleases(context: android.content.Context): List<ReleaseInfo> =
        runCatching {
            val cachedJson = context.dataStore.getAsync(GitHubReleasesJsonKey)
            if (cachedJson != null) {
                listOf(json.decodeFromString<ReleaseInfo>(cachedJson))
            } else {
                emptyList()
            }
        }.getOrElse { emptyList() }

    suspend fun getLatestDownloadUrl(context: android.content.Context? = null): String? {
        val releases = getReleaseHistory().getOrNull() ?: context?.let { getCachedReleases(it) } ?: emptyList()
        return getLatestDownloadUrl(releases)
    }

    // Helper to get latest download url
    fun getLatestDownloadUrl(releases: List<ReleaseInfo>): String? {
        return releases.firstOrNull()?.assets?.firstOrNull { it.name.endsWith(".apk") }?.browserDownloadUrl
    }
}
// Extension for convenience if needed
fun List<Updater.ReleaseInfo>.getLatestDownloadUrl(): String? = Updater.getLatestDownloadUrl(this)
