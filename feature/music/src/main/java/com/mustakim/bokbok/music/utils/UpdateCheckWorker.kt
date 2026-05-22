package com.mustakim.bokbok.music.utils

import com.mustakim.bokbok.constants.*
import com.mustakim.bokbok.data.local.*
import kotlinx.coroutines.flow.first
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.map
import com.mustakim.bokbok.music.BuildConfig
import com.mustakim.bokbok.util.Updater

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val dataStore = applicationContext.dataStore

            val isEnabled = dataStore.data.map { it[EnableUpdateNotificationKey] ?: false }.first()
            if (!isEnabled) return Result.success()

            val updateChannel = dataStore.data.map {
                it[UpdateChannelKey]?.let { value ->
                    try { UpdateChannel.valueOf(value) } catch (e: Exception) { UpdateChannel.STABLE }
                } ?: UpdateChannel.STABLE
            }.first()

            if (updateChannel == UpdateChannel.NIGHTLY) return Result.success()

            Updater.getLatestVersionName().onSuccess { latestVersion ->
                if (!Updater.isSameVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                    UpdateNotificationManager.notifyIfNewVersion(applicationContext, latestVersion)
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
