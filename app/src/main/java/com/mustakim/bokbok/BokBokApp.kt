package com.mustakim.bokbok

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp

class BokBokApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Set up default uncaught exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("BokBokApp", "Crash in thread: ${thread.name}", throwable)
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        FirebaseApp.initializeApp(this)
    }
}