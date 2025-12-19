package com.mustakim.bokbok.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.mustakim.bokbok.data.bloatware.BloatwareDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background worker to sync the bloatware database.
 * Runs on app startup (once per session) to avoid blocking ViewModel initialization.
 */
class BloatwareSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            BloatwareDatabase.sync(applicationContext)
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "bloatware_sync"
    }
}
