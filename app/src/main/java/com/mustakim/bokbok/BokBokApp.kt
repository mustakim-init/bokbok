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
            .components {
                add(com.mustakim.bokbok.utils.AppIconKeyer())
                add(com.mustakim.bokbok.utils.AppIconFetcher.Factory(this@BokBokApp))
            }
            .crossfade(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()

        // Enable Firestore offline persistence (New API)
        val cacheSettings = com.google.firebase.firestore.PersistentCacheSettings.newBuilder()
            .setSizeBytes(com.google.firebase.firestore.FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
            .build()

        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(cacheSettings)
            .build()

        com.google.firebase.firestore.FirebaseFirestore.getInstance().firestoreSettings = settings
        
        // Schedule scans (non-blocking)
        scheduleBloatwareSync()
        triggerAppScan()
        triggerUsageScan()
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
}
