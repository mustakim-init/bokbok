package com.mustakim.bokbok

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mustakim.bokbok.workers.BloatwareSyncWorker

class BokBokApp : Application() {
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
        
        // Schedule background bloatware database sync (non-blocking)
        scheduleBloatwareSync()
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
