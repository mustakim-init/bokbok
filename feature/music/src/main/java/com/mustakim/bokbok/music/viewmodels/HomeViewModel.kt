package com.mustakim.bokbok.music.viewmodels
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.music.innertube.YouTube
import com.mustakim.bokbok.music.innertube.models.PlaylistItem
import com.mustakim.bokbok.music.innertube.models.WatchEndpoint
import com.mustakim.bokbok.music.innertube.models.YTItem
import com.mustakim.bokbok.music.innertube.models.filterExplicit
import com.mustakim.bokbok.music.innertube.models.filterVideo
import com.mustakim.bokbok.music.innertube.pages.ExplorePage
import com.mustakim.bokbok.music.innertube.pages.HomePage
import com.mustakim.bokbok.music.innertube.utils.completed
import com.mustakim.bokbok.music.innertube.utils.parseCookieString
import com.mustakim.bokbok.music.constants.HideExplicitKey
import com.mustakim.bokbok.music.constants.HideVideoKey
import com.mustakim.bokbok.music.constants.InnerTubeCookieKey
import com.mustakim.bokbok.music.constants.QuickPicks
import com.mustakim.bokbok.music.constants.QuickPicksKey
import com.mustakim.bokbok.music.constants.SpeedDialSongIdsKey
import com.mustakim.bokbok.music.constants.YtmSyncKey
import com.mustakim.bokbok.music.db.MusicDatabase
import com.mustakim.bokbok.music.db.entities.*
import com.mustakim.bokbok.music.extensions.toEnum
import com.mustakim.bokbok.music.models.MediaMetadata
import com.mustakim.bokbok.music.models.SimilarRecommendation
import com.mustakim.bokbok.music.models.toMediaMetadata
import com.mustakim.bokbok.music.innertube.models.SongItem
import com.mustakim.bokbok.data.local.dataStore
import com.mustakim.bokbok.data.local.get
import com.mustakim.bokbok.data.local.getAsync
import com.mustakim.bokbok.music.utils.SyncUtils
import com.mustakim.bokbok.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import timber.log.Timber
import javax.inject.Inject
import com.mustakim.bokbok.music.utils.LocalDeviceMusicScanner
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext val context: Context,
    val database: MusicDatabase,
    val syncUtils: SyncUtils,
) : ViewModel() {
    val isRefreshing = MutableStateFlow(false)
    val isLoading = MutableStateFlow(false)
    private val isInitialLoadComplete = MutableStateFlow(false)

    private val quickPicksEnum = context.dataStore.data.map {
        it[QuickPicksKey].toEnum(QuickPicks.QUICK_PICKS)
    }.distinctUntilChanged()

    val quickPicks = MutableStateFlow<List<MediaMetadata>?>(null)
    val speedDialSongs = MutableStateFlow<List<Song>>(emptyList())
    val localDeviceSongs = MutableStateFlow<List<Song>>(emptyList())
    private val _fullLocalDeviceSongs = MutableStateFlow<List<Song>>(emptyList())
    val fullLocalDeviceSongs = _fullLocalDeviceSongs.asStateFlow()

    val localSongsPager = Pager(
        config = PagingConfig(pageSize = 50, enablePlaceholders = true),
        pagingSourceFactory = { database.localSongsPagingSource() }
    ).flow.cachedIn(viewModelScope)
    val forgottenFavorites = MutableStateFlow<List<Song>?>(null)
    val keepListening = MutableStateFlow<List<LocalItem>?>(null)
    val similarRecommendations = MutableStateFlow<List<SimilarRecommendation>?>(null)
    val accountPlaylists = MutableStateFlow<List<PlaylistItem>?>(null)
    val homePage = MutableStateFlow<HomePage?>(null)
    val explorePage = MutableStateFlow<ExplorePage?>(null)
    val selectedChip = MutableStateFlow<HomePage.Chip?>(null)
    private val previousHomePage = MutableStateFlow<HomePage?>(null)

    val recentActivity = MutableStateFlow<List<YTItem>?>(null)
    val recentPlaylistsDb = MutableStateFlow<List<Playlist>?>(null)

    val allLocalItems = MutableStateFlow<List<LocalItem>>(emptyList())
    val allYtItems = MutableStateFlow<List<YTItem>>(emptyList())

    // Account display info
    val accountName = MutableStateFlow("")
    val accountImageUrl = MutableStateFlow<String?>(null)
    val isAccountLoading = MutableStateFlow(true)
    val isAccountLoggedIn = MutableStateFlow(false)
    
    // Track last processed cookie to avoid unnecessary updates
    private var lastProcessedCookie: String? = null
    
    // Track if we're currently processing account data
    private var isProcessingAccountData = false
    private var wasLoggedIn = false

    private fun filterHomeChips(chips: List<HomePage.Chip>?): List<HomePage.Chip>? {
        return chips?.filterNot { it.title.contains("podcasts", ignoreCase = true) }
    }

    private suspend fun getQuickPicks(){
        when (quickPicksEnum.first()) {
            QuickPicks.QUICK_PICKS -> {
                // Tier 1: Related-based algorithmic picks
                var picks = database.quickPicks().first().shuffled().take(20)
                
                if (picks.isEmpty()) {
                    // Tier 2: Fallback to most played/recent history
                    picks = database.mostPlayedSongs(fromTimeStamp = 0, limit = 20).first().shuffled()
                }

                if (picks.isEmpty()) {
                    // Tier 3: absolute cold start (local random)
                    Timber.d("HomeViewModel: Quick Picks history empty, falling back to random local songs")
                    quickPicks.value = database.localSongsByCreateDateAsc().first().shuffled().take(20).map { it.toMediaMetadata() }
                } else {
                    quickPicks.value = picks.map { it.toMediaMetadata() }
                }
            }
            QuickPicks.LAST_LISTEN -> songLoad()
            QuickPicks.DONT_SHOW -> quickPicks.value = null
        }
    }

    private suspend fun loadSpeedDialSongs() {
        val speedDialIds = context.dataStore.getAsync(SpeedDialSongIdsKey, "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(24)
        if (speedDialIds.isEmpty()) {
            speedDialSongs.value = emptyList()
            return
        }
        val songsById = database.getSongsByIds(speedDialIds).associateBy { it.id }
        speedDialSongs.value = speedDialIds.mapNotNull { songsById[it] }
    }

    private suspend fun load() {
        if (isLoading.value) return
        isLoading.value = true
        
        try {
            supervisorScope {
                val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                val hideVideo = context.dataStore.get(HideVideoKey, false)
                val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2

                launch { getQuickPicks() }
                launch { loadSpeedDialSongs() }
                launch { forgottenFavorites.value = database.forgottenFavorites().first().shuffled().take(20) }
                launch { 
                    val allSongs = database.localSongsByCreateDateAsc().first()
                    localDeviceSongs.value = allSongs.shuffled().take(30)
                    _fullLocalDeviceSongs.value = allSongs
                }
                
                launch {
                    val keepListeningSongs = database.mostPlayedSongs(fromTimeStamp, limit = 15, offset = 5)
                        .first().shuffled().take(10)
                    val keepListeningAlbums = database.mostPlayedAlbums(fromTimeStamp, limit = 8, offset = 2)
                        .first().filter { it.album.thumbnailUrl != null }.shuffled().take(5)
                    val keepListeningArtists = database.mostPlayedArtists(fromTimeStamp)
                        .first().filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                        .shuffled().take(5)
                    keepListening.value = (keepListeningSongs + keepListeningAlbums + keepListeningArtists).shuffled()
                }

                launch {
                        YouTube.home().onSuccess { page ->
                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                        val hideVideo = context.dataStore.get(HideVideoKey, false)
                        
                        // Promote "Quick picks" section to Hero Carousel
                        val quickPicksSection = page.sections.find { it.title == context.getString(MusicR.string.quick_picks) }
                        if (quickPicksSection != null) {
                            quickPicks.value = quickPicksSection.items
                                .filterIsInstance<SongItem>()
                                .map { it.toMediaMetadata() }
                        }

                        homePage.value = page.copy(
                            chips = filterHomeChips(page.chips),
                            sections = page.sections
                                .filter { it != quickPicksSection } // Avoid duplicate row
                                .map { section ->
                                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                                }
                        )
                    }.onFailure { reportException(it) }
                }

                launch {
                    YouTube.explore().onSuccess { page ->
                        val artists: MutableMap<Int, String> = mutableMapOf()
                        val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                        database.allArtistsByPlayTime().first().let { list ->
                            var favIndex = 0
                            for ((artistsIndex, artist) in list.withIndex()) {
                                artists[artistsIndex] = artist.id
                                if (artist.artist.bookmarkedAt != null) {
                                    favouriteArtists[favIndex] = artist.id
                                    favIndex++
                                }
                            }
                        }
                        explorePage.value = page.copy(
                            newReleaseAlbums = page.newReleaseAlbums
                                .sortedBy { album ->
                                    val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                    val firstArtistKey = artistIds.firstNotNullOfOrNull { artistId ->
                                        if (artistId in favouriteArtists.values) {
                                            favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                        } else {
                                            artists.entries.firstOrNull { it.value == artistId }?.key
                                        }
                                    } ?: Int.MAX_VALUE
                                    firstArtistKey
                                }.filterExplicit(hideExplicit)
                        )
                    }.onFailure { reportException(it) }
                }
                launch {
                    loadSimilarRecommendations()
                }
            }
                    
            isInitialLoadComplete.value = true
        } catch (e: Exception) {
            reportException(e)
        } finally {
            isLoading.value = false
        }
    }

    private suspend fun loadSimilarRecommendations() {
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideo = context.dataStore.get(HideVideoKey, false)
        val fromTimeStamp = System.currentTimeMillis() - 86400000 * 7 * 2
        
        var artistSeeds = database.mostPlayedArtists(fromTimeStamp, limit = 10).first()
            .filter { it.artist.isYouTubeArtist }

        if (artistSeeds.isEmpty()) {
            Timber.d("HomeViewModel: No play history for artists, warm-up from library artists")
            artistSeeds = database.allArtistsByPlayTime().first()
                .filter { it.artist.isYouTubeArtist && it.artist.thumbnailUrl != null }
                .shuffled().take(3)
        }

        val artistRecommendations = artistSeeds
            .shuffled().take(3)
            .mapNotNull {
                val items = mutableListOf<YTItem>()
                YouTube.artist(it.id).onSuccess { page ->
                    items += page.sections.getOrNull(page.sections.size - 2)?.items.orEmpty()
                    items += page.sections.lastOrNull()?.items.orEmpty()
                }
                SimilarRecommendation(
                    title = it,
                    items = items.filterExplicit(hideExplicit).filterVideo(hideVideo).shuffled().ifEmpty { return@mapNotNull null }
                )
            }

        var songSeeds = database.mostPlayedSongs(fromTimeStamp, limit = 10).first()
            .filter { it.album != null }

        if (songSeeds.isEmpty()) {
            Timber.d("HomeViewModel: No play history for songs, warm-up from random local songs")
            songSeeds = database.localSongsByCreateDateAsc().first()
                .filter { it.album != null }
                .shuffled().take(5)
        }

        val songRecommendations = songSeeds
            .shuffled().take(2)
            .mapNotNull { song ->
                val endpoint = YouTube.next(WatchEndpoint(videoId = song.id)).getOrNull()?.relatedEndpoint
                    ?: return@mapNotNull null
                val page = YouTube.related(endpoint).getOrNull() ?: return@mapNotNull null
                SimilarRecommendation(
                    title = song,
                    items = (page.songs.shuffled().take(8) +
                            page.albums.shuffled().take(4) +
                            page.artists.shuffled().take(4) +
                            page.playlists.shuffled().take(4))
                        .filterExplicit(hideExplicit).filterVideo(hideVideo)
                        .shuffled()
                        .ifEmpty { return@mapNotNull null }
                )
            }

        similarRecommendations.value = (artistRecommendations + songRecommendations).shuffled()
    }

    private suspend fun songLoad() {
        val song = database.events().first().firstOrNull()?.song
        if (song != null) {
            if (database.hasRelatedSongs(song.id)) {
                val relatedSongs = database.getRelatedSongs(song.id).first().shuffled().take(20)
                quickPicks.value = relatedSongs.map { it.toMediaMetadata() }
            }
        }
    }

    private fun clearAccountData() {
        accountName.value = ""
        accountImageUrl.value = null
        accountPlaylists.value = null
    }

    private fun prepareYouTubeAccount(cookie: String): Boolean {
        return try {
            YouTube.cookie = cookie
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to set YouTube cookie")
            false
        }
    }

    private suspend fun refreshAccountIdentity() {
        accountName.value = ""
        accountImageUrl.value = null

        try {
            YouTube.accountInfo().onSuccess { info ->
                accountName.value = info.name
                accountImageUrl.value = info.thumbnailUrl
            }.onFailure { error ->
                Timber.w(error, "Failed to fetch account info")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception fetching account info")
        }
    }

    private suspend fun refreshAccountPlaylistsInternal() {
        try {
            YouTube.library("FEmusic_liked_playlists").completed().onSuccess {
                val lists = it.items.filterIsInstance<PlaylistItem>().filterNot { playlist ->
                    playlist.id == "SE"
                }
                accountPlaylists.value = lists
            }.onFailure { error ->
                Timber.w(error, "Failed to fetch account playlists")
            }
        } catch (e: Exception) {
            Timber.e(e, "Exception fetching account playlists")
        }
    }

    private val _isLoadingMore = MutableStateFlow(false)
    fun loadMoreYouTubeItems(continuation: String?) {
        if (continuation == null || _isLoadingMore.value) return
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideo = context.dataStore.get(HideVideoKey, false)

        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingMore.value = true
            val nextSections = YouTube.home(continuation).getOrNull() ?: run {
                _isLoadingMore.value = false
                return@launch
            }

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = (homePage.value?.sections.orEmpty() + nextSections.sections).map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                }
            )
            _isLoadingMore.value = false
        }
    }

    fun toggleChip(chip: HomePage.Chip?) {
        if (chip == null || chip == selectedChip.value && previousHomePage.value != null) {
            homePage.value = previousHomePage.value
            previousHomePage.value = null
            selectedChip.value = null
            return
        }

        if (selectedChip.value == null) {
            previousHomePage.value = homePage.value
        }

        viewModelScope.launch(Dispatchers.IO) {
            val hideExplicit = context.dataStore.get(HideExplicitKey, false)
            val hideVideo = context.dataStore.get(HideVideoKey, false)
            val nextSections = YouTube.home(params = chip?.endpoint?.params).getOrNull() ?: return@launch

            homePage.value = nextSections.copy(
                chips = homePage.value?.chips,
                sections = nextSections.sections.map { section ->
                    section.copy(items = section.items.filterExplicit(hideExplicit).filterVideo(hideVideo))
                }
            )
            selectedChip.value = chip
        }
    }

    fun refresh() {
        if (isRefreshing.value) return
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing.value = true
            load()
            isRefreshing.value = false
        }
    }

    fun refreshAccountData() {
        viewModelScope.launch(Dispatchers.IO) {
            if (isProcessingAccountData) return@launch
            
            isProcessingAccountData = true
            isAccountLoading.value = true
            try {
                val cookie = context.dataStore.get(InnerTubeCookieKey, "")
                val loggedIn = cookie.isNotEmpty() && "SAPISID" in parseCookieString(cookie)
                isAccountLoggedIn.value = loggedIn

                if (loggedIn && prepareYouTubeAccount(cookie)) {
                    refreshAccountIdentity()
                    launch {
                        refreshAccountPlaylistsInternal()
                    }
                } else {
                    clearAccountData()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error refreshing account data")
                clearAccountData()
            } finally {
                isAccountLoading.value = false
                isProcessingAccountData = false
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            load()
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[SpeedDialSongIdsKey].orEmpty() }
                .distinctUntilChanged()
                .collect {
                    loadSpeedDialSongs()
                }
        }

        // Global aggregators for home screen items
        viewModelScope.launch(Dispatchers.IO) {
            combine(quickPicks, forgottenFavorites, keepListening) { q, f, k ->
                (q.orEmpty() + f.orEmpty() + k.orEmpty()).filterIsInstance<LocalItem>()
            }.collect {
                allLocalItems.value = it
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            combine(similarRecommendations, homePage) { similar, home ->
                similar?.flatMap { it.items }.orEmpty() + home?.sections?.flatMap { it.items }.orEmpty()
            }.collect {
                allYtItems.value = it
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(3000)
            syncUtils.cleanupDuplicatePlaylists()
        }

        viewModelScope.launch(Dispatchers.IO) {
            context.dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    if (isProcessingAccountData) return@collect
                    
                    lastProcessedCookie = cookie
                    isProcessingAccountData = true
                    isAccountLoading.value = true
                    
                    try {
                        val isLoggedIn = cookie?.let { "SAPISID" in parseCookieString(it) } ?: false
                        val loginTransition = isLoggedIn && !wasLoggedIn
                        wasLoggedIn = isLoggedIn
                        isAccountLoggedIn.value = isLoggedIn
                        
                        if (isLoggedIn && cookie != null && cookie.isNotEmpty()) {
                            if (!prepareYouTubeAccount(cookie)) {
                                clearAccountData()
                                return@collect
                            }

                            if (loginTransition) {
                                launch {
                                    try {
                                        if (context.dataStore.get(YtmSyncKey, true)) {
                                            syncUtils.performFullSync()
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Error during login-triggered sync")
                                        reportException(e)
                                    }
                                }
                            }
                            
                            kotlinx.coroutines.delay(100)

                            refreshAccountIdentity()

                            launch {
                                refreshAccountPlaylistsInternal()
                            }
                        } else {
                            clearAccountData()
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing cookie change")
                        clearAccountData()
                        isAccountLoggedIn.value = false
                    } finally {
                        isAccountLoading.value = false
                        isProcessingAccountData = false
                    }
                }
        }
    }
}
