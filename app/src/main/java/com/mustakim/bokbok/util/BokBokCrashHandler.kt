package com.mustakim.bokbok.util

import android.content.Context
import android.os.Build
import android.util.Log
import com.mustakim.bokbok.BokBokApp
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 🛠️ De-branded System Diagnostic Handler
 * Intercepts fatal crashes and serializes them locally for debugging.
 * Respects user privacy toggles.
 */
object BokBokCrashHandler {
    private const val TAG = "SystemDiagnostics"
    private const val CRASH_DIR = "crash_logs"
    private const val MAX_LOGS = 5
    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = context.applicationContext

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            handleException(appContext, thread, exception)
            defaultHandler?.uncaughtException(thread, exception)
        }
    }

    private fun handleException(context: Context, thread: Thread, exception: Throwable) {
        try {
            // Check for explicit user opt-in via PreferencesManager
            val app = context.applicationContext as? BokBokApp
            val isOptedIn = app?.preferencesManager?.getImmediate(
                androidx.datastore.preferences.core.booleanPreferencesKey("pref_crash_report_enabled")
            ) ?: false

            if (!isOptedIn) {
                Log.d(TAG, "Diagnostic collection skipped: User hasn't opted-in.")
                return
            }

            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }

            // Auto-purge old logs before creating a new one
            purgeOldLogs(crashDir)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val crashFile = File(crashDir, "diagnostic_$timestamp.txt")
            
            FileWriter(crashFile, true).use { writer ->
                val printWriter = PrintWriter(writer)
                printWriter.println("--- System Diagnostic Report ---")
                printWriter.println("Timestamp: $timestamp")
                printWriter.println("Thread: ${thread.name}")
                printWriter.println("OS Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                printWriter.println("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                printWriter.println("Exception Trace:")
                exception.printStackTrace(printWriter)
                printWriter.flush()
            }
            Log.e(TAG, "Diagnostic data saved: ${crashFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Critically failed to write diagnostic log", e)
        }
    }

    private fun purgeOldLogs(dir: File) {
        val files = dir.listFiles { _, name -> name.endsWith(".txt") }?.sortedBy { it.lastModified() }
        if (files != null && files.size >= MAX_LOGS) {
            val toDelete = files.size - MAX_LOGS + 1
            for (i in 0 until toDelete) {
                files[i].delete()
            }
        }
    }

    fun getCrashLogs(context: Context): List<File> {
        val crashDir = File(context.filesDir, CRASH_DIR)
        return if (crashDir.exists()) {
            crashDir.listFiles { _, name -> name.endsWith(".txt") }
                ?.sortedByDescending { it.lastModified() }?.toList() ?: emptyList()
        } else {
            emptyList()
        }
    }

    fun clearCrashLogs(context: Context) {
        getCrashLogs(context).forEach { it.delete() }
    }
}
