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
            downloadFileResumable(modelUrl, tempFile)
            
            setProgress(androidx.work.workDataOf("STATUS" to "EXTRACTING", "PROGRESS" to 100f))
            android.util.Log.d("TTSModelWorker", "Extracting $tempFile...")
            
            if (extension == "tar.bz2") {
                extractTarBz2(tempFile, destDir)
            } else {
                extractZip(tempFile, destDir)
            }
            
            tempFile.delete()
            
            // Update Preferences
            val preferencesManager = PreferencesManager(applicationContext)
            preferencesManager.addDownloadedLanguage(langCode)
            
            android.util.Log.d("TTSModelWorker", "TTS Model extraction complete for $langCode.")
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("TTSModelWorker", "Download/Extraction failed", e)
            Result.retry()
        }
    }

    private val client = okhttp3.OkHttpClient()

    private suspend fun downloadFileResumable(url: String, dest: File) {
        var downloadedBytes = 0L
        if (dest.exists()) {
            downloadedBytes = dest.length()
        }

        val requestBuilder = okhttp3.Request.Builder().url(url)
        if (downloadedBytes > 0) {
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
             if (response.code == 416) {
                // Range not satisfiable, restart
                dest.delete()
                downloadFileResumable(url, dest)
                return
            }
            throw Exception("Download failed with code ${response.code}")
        }

        val body = response.body ?: throw Exception("Response body is null")
        val contentLength = body.contentLength() + downloadedBytes
        
        body.byteStream().use { input ->
            FileOutputStream(dest, downloadedBytes > 0).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var bytesSinceUpdate = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    bytesSinceUpdate += bytesRead

                    if (bytesSinceUpdate > 1024 * 100) { // Update every 100KB
                        val progress = if (contentLength > 0) downloadedBytes.toFloat() / contentLength else 0f
                        setProgress(
                            androidx.work.workDataOf(
                                "PROGRESS" to progress * 100f,
                                "STATUS" to "DOWNLOADING"
                            )
                        )
                        bytesSinceUpdate = 0
                    }
                }
                output.flush()
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
