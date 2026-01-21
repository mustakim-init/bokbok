package com.mustakim.bokbok.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.mustakim.bokbok.data.local.PreferencesManager
import javax.inject.Inject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream

@HiltWorker
class TTSModelWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val modelUrl = inputData.getString("MODEL_URL") ?: return@withContext Result.failure()
        val langCode = inputData.getString("LANG_CODE") ?: "en"
        val destDir = File(File(applicationContext.filesDir, "tts_models"), langCode)
        if (!destDir.exists()) destDir.mkdirs()

        try {
            val extension = if (modelUrl.endsWith(".tar.bz2")) "tar.bz2" else "zip"
            val tempFile = File(destDir, "model_temp.$extension")
            
            android.util.Log.d("TTSModelWorker", "Downloading $modelUrl...")
            downloadFile(modelUrl, tempFile)
            
            android.util.Log.d("TTSModelWorker", "Extracting $tempFile...")
            if (extension == "tar.bz2") {
                extractTarBz2(tempFile, destDir)
            } else {
                extractZip(tempFile, destDir)
            }
            
            tempFile.delete()
            
            // Update PreferencesManager (HiltWorker can't easily inject, we use manual instance for now or EntryPoint)
            val preferencesManager = PreferencesManager(applicationContext)
            preferencesManager.addDownloadedLanguage(langCode)
            
            android.util.Log.d("TTSModelWorker", "TTS Model extraction complete for $langCode.")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TTSModelWorker", "Download/Extraction failed", e)
            Result.failure()
        }
    }

    private fun downloadFile(url: String, dest: File) {
        URL(url).openStream().use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractZip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val file = File(destDir, entry.name)
                if (entry.isDirectory) {
                    file.mkdirs()
                } else {
                    file.parentFile?.mkdirs()
                    FileOutputStream(file).use { output ->
                        zip.copyTo(output)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun extractTarBz2(tarFile: File, destDir: File) {
        try {
            // Using shell 'tar' command which is available via toybox on modern Android
            val process = Runtime.getRuntime().exec(
                arrayOf("sh", "-c", "tar -xjf \"${tarFile.absolutePath}\" -C \"${destDir.absolutePath}\"")
            )
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                throw Exception("Tar extraction failed with code $exitCode: $error")
            }
        } catch (e: Exception) {
            throw e
        }
    }
}
