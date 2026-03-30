package com.mustakim.bokbok

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
import javax.inject.Inject

@HiltAndroidApp
class BokBokApp : Application(), Configuration.Provider, coil.ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

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

        // Bypass hidden API restrictions for Conscrypt (required by libadb-android for ADB pairing)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                "Lcom/android/org/conscrypt/",
                "Lcom/google/android/gms/org/conscrypt/"
            )
        }

        // Enable Firestore offline persistence (New API)
        val cacheSettings = com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
            .setSizeBytes(100 * 1024 * 1024) // 100MB limit
            .build()

        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
        
        // Workers and Services are now triggered lazily on-demand to optimize startup performance
        
        // Start background watchdog for game detection and Shizuku health
        com.mustakim.bokbok.data.service.GameWatchdogService.start(this)
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
