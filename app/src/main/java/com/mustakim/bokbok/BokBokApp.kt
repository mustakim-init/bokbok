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
class BokBokApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
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
        val request = OneTimeWorkRequestBuilder<com.mustakim.bokbok.workers.UsageStatsWorker>()
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
