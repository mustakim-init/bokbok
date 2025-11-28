package com.mustakim.bokbok

import android.app.Application

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
    }
}
