package com.mustakim.bokbok
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first
import com.mustakim.bokbok.music.App
import com.mustakim.bokbok.music.utils.toPlaybackAuthState
import com.mustakim.bokbok.music.utils.clearPlaybackAuthSession
import com.mustakim.bokbok.music.constants.VisitorDataKey
import com.mustakim.bokbok.music.constants.InnerTubeCookieKey
import com.mustakim.bokbok.music.utils.SyncUtils
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import timber.log.Timber

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mustakim.bokbok.workers.BloatwareSyncWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.mustakim.bokbok.data.local.PreferenceStore
import com.mustakim.bokbok.music.ui.player.CanvasArtworkPlaybackCache
import com.mustakim.bokbok.music.innertube.YouTube
import com.mustakim.bokbok.music.innertube.models.YouTubeLocale
import com.mustakim.bokbok.music.kugou.KuGou
import com.mustakim.bokbok.music.lastfm.LastFM
import android.util.Log
import java.util.Locale

@HiltAndroidApp
class BokBokApp : Application(), Configuration.Provider, coil.ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var preferencesManager: com.mustakim.bokbok.data.local.PreferencesManager
    @Inject lateinit var daemonManager: com.mustakim.bokbok.data.shell.DaemonManager
    @Inject lateinit var mediaStoreObserver: com.mustakim.bokbok.music.utils.directory.MediaStoreObserver
    @Inject lateinit var syncUtils: SyncUtils

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
            
    override fun newImageLoader(): coil.ImageLoader {
        return coil.ImageLoader.Builder(this)
            .memoryCache {
                coil.memory.MemoryCache.Builder(this@BokBokApp)
                    .maxSizePercent(0.15) // Limit to 15% of available RAM
                    .build()
            }
            .components {
                add(com.mustakim.bokbok.utils.AppIconKeyer())
                add(com.mustakim.bokbok.utils.AppIconFetcher.Factory(this@BokBokApp))
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Plant Timber FIRST so all subsequent Timber.d/e calls are visible in logcat
        Timber.plant(Timber.DebugTree())

        com.mustakim.bokbok.util.BokBokCrashHandler.init(this)

        // [CRITICAL] Essential initializations must be synchronous to prevent race conditions in ViewModels
        PreferenceStore.start(this)
        CanvasArtworkPlaybackCache.init(this)

        // Initialize YouTube locale immediately
        val locale = Locale.getDefault()
        val languageTag = locale.toLanguageTag().replace("-Hant", "")
        YouTube.locale = YouTubeLocale(
            gl = locale.country.takeIf { it in com.mustakim.bokbok.music.constants.CountryCodeToName } ?: "US",
            hl = locale.language.takeIf { it in com.mustakim.bokbok.music.constants.LanguageCodeToName }
                ?: languageTag.takeIf { it in com.mustakim.bokbok.music.constants.LanguageCodeToName }
                ?: "en"
        )
        if (languageTag == "zh-TW") {
            KuGou.useTraditionalChinese = true
        }
        LastFM.initialize(apiKey = "", secret = "")

        // Bypass hidden API restrictions for Conscrypt (required by libadb-android for ADB pairing)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                "Lcom/android/org/conscrypt/",
                "Lcom/google/android/gms/org/conscrypt/"
            )
        }

        // Offload heavy initializations to a background coroutine
        applicationScope.launch(Dispatchers.IO) {
            initializeDeferredAsync()
        }

        initializeGlobalSync()
        YouTube.useLoginForBrowse = true
    }

    private fun initializeGlobalSync() {
        // Collect and propagate auth state changes globally for playback
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it.toPlaybackAuthState() }
                .distinctUntilChanged()
                .collect { authState ->
                    Timber.d("Applying new YouTube auth state: ${authState.cookie != null}")
                    YouTube.authState = authState
                }
        }

        // Collect and propagate cookie changes globally for browse requests
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[InnerTubeCookieKey] }
                .distinctUntilChanged()
                .collect { cookie ->
                    YouTube.cookie = cookie
                }
        }

        // Auto-retrieve visitor data if missing
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[VisitorDataKey] }
                .distinctUntilChanged()
                .collect { visitorData ->
                    if (!visitorData.isNullOrBlank()) return@collect
                    Timber.d("VisitorData missing, fetching from InnerTube...")
                    YouTube.visitorData().onSuccess { newVisitorData ->
                        dataStore.edit { settings ->
                            settings[VisitorDataKey] = newVisitorData
                        }
                        Timber.d("Successfully retrieved and saved new VisitorData")
                    }.onFailure {
                        Timber.e(it, "Failed to retrieve VisitorData")
                    }
                }
        }
    }

    private fun initializeDeferredAsync() {
        // [STAGE 1] Essential but non-blocking settings (REMAINING AFTER MOVING CRITICAL ONES)
        val cacheSettings = com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
            .setSizeBytes(100 * 1024 * 1024) // 100MB limit
            .build()

        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings

        // [STAGE 2] Deferred Tasks (Triggered after UI is stable)
        com.mustakim.bokbok.startup.StartupManager.apply {
            registerDeferredTask {
                Log.d("BokBokApp", "Executing Stage 2 Deferred Initializations...")

                // Background Services (ONLY if opted-in)
                kotlinx.coroutines.MainScope().launch {
                    val watchdogEnabled = preferencesManager.watchdogEnabled.first()
                    val heartbeatEnabled = preferencesManager.heartbeatEnabled.first()
                    
                    if (watchdogEnabled) {
                        com.mustakim.bokbok.data.service.GameWatchdogService.start(this@BokBokApp)
                    }
                    if (heartbeatEnabled) {
                        daemonManager.deployAndStart()
                    }
                }

                // Initial Scans/Syncs
                scheduleBloatwareSync()
                triggerAppScan()
                triggerUsageScan()
                
                // ✅ OPTIMIZED: Pre-warm Music Module components to eliminate lag
                Log.d("BokBokApp", "Warming up Music Module...")
                try {
                    // Start media store observation and trigger background room sync
                    mediaStoreObserver.register()
                    mediaStoreObserver.forceRescan()
                } catch (e: Exception) {
                    Log.e("BokBokApp", "Music pre-warm failed", e)
                }
            }
        }
    }

    // Dummy Companion to map BokBok App.instance 
    companion object {
        lateinit var instance: Application
            internal set
    }

    private fun triggerAppScan() {
        val request = OneTimeWorkRequestBuilder<com.mustakim.bokbok.workers.AppScanWorker>()
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            com.mustakim.bokbok.workers.AppScanWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun triggerUsageScan() {
        // Provide Today's range as default for the startup scan
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis
        
        val endTime = System.currentTimeMillis()

        val inputData = androidx.work.Data.Builder()
            .putLong("start_time", startTime)
            .putLong("end_time", endTime)
            .build()

        val request = OneTimeWorkRequestBuilder<com.mustakim.bokbok.workers.UsageStatsWorker>()
            .setInputData(inputData)
            .build()
            
        WorkManager.getInstance(this).enqueueUniqueWork(
            com.mustakim.bokbok.workers.UsageStatsWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }
    
    private fun scheduleBloatwareSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val syncRequest = OneTimeWorkRequestBuilder<BloatwareSyncWorker>()
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniqueWork(
            BloatwareSyncWorker.WORK_NAME,
            ExistingWorkPolicy.KEEP, // Don't restart if already running
            syncRequest
        )
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Clear Coil memory cache on pressure or when backgrounded
        if (level >= TRIM_MEMORY_UI_HIDDEN) {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        }
    }
}
