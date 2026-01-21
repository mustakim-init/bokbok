package com.mustakim.bokbok.util

import android.content.Context
import androidx.work.*
import com.mustakim.bokbok.workers.TTSModelWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val workManager = WorkManager.getInstance(context)

    companion object {
        private val MODEL_URLS = mapOf(
            "en" to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-amy-medium-int8.tar.bz2",
            "bn" to "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-mimic3-bn-multi_low.tar.bz2"
        )
    }

    fun downloadPremiumTTS(langCode: String = "en") {
        val modelUrl = MODEL_URLS[langCode] ?: return
        
        val data = workDataOf(
            "MODEL_URL" to modelUrl,
            "LANG_CODE" to langCode
        )
        
        val request = OneTimeWorkRequestBuilder<TTSModelWorker>()
            .setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build())
            .build()
        
        workManager.enqueueUniqueWork("TTS_MODEL_DOWNLOAD_$langCode", ExistingWorkPolicy.KEEP, request)
    }

    fun isModelDownloaded(langCode: String): Boolean {
        val destDir = File(File(context.filesDir, "tts_models"), langCode)
        return destDir.exists() && destDir.listFiles()?.isNotEmpty() == true
    }

    fun getDownloadStatus(langCode: String): Flow<WorkInfo.State?> {
        return workManager.getWorkInfosForUniqueWorkFlow("TTS_MODEL_DOWNLOAD_$langCode")
            .map { it.firstOrNull()?.state }
    }
}
