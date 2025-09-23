package com.mustakim.bokbok

import android.app.Application
import com.google.firebase.FirebaseApp

class BokBokApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Ensure Firebase is initialized early
        FirebaseApp.initializeApp(this)
    }
}
