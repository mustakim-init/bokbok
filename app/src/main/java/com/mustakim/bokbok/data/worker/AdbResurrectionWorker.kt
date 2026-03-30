package com.mustakim.bokbok.data.worker

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.mustakim.bokbok.data.adb.*
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

private const val TAG = "AdbResurrectionWorker"
private const val PREFS_NAME = "adb_resurrection"
private const val KEY_PAIRED = "adb_paired"

@HiltWorker
class AdbResurrectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val adbPairingManager: AdbPairingManager
) : CoroutineWorker(context, params) {

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Guard: don't attempt resurrection if the user hasn't done the one-time setup.
        if (!adbPairingManager.isPaired) {
             Log.w(TAG, "Skipping resurrection — one-time ADB setup not completed yet.")
             return@withContext Result.success() // Not paired is not a failure — just nothing to do
        }

        Log.d(TAG, "Starting ADB resurrection worker...")
        
        try {
            val success = adbPairingManager.silentReconnect()
            if (success) {
                Log.d(TAG, "Resurrection successful")
                Result.success()
            } else {
                Log.w(TAG, "Resurrection attempt failed (may retry)")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resurrection worker crashed", e)
            Result.retry()
        }
    }

    companion object {
        fun enqueue(context: Context) {
            android.util.Log.i(TAG, "Enqueuing AdbResurrectionWorker for execution...")
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<AdbResurrectionWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .addTag("adb_resurrection")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "adb_resurrection",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
