package com.mustakim.bokbok.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject

@HiltWorker
class DeepFilterNetWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val client = OkHttpClient()
    private val baseUrl = "https://raw.githubusercontent.com/mustakim-init/bokbok-nrm/master/"
    private val modelFiles = listOf("enc.onnx", "erb_dec.onnx", "df_dec.onnx", "config.ini")

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val destDir = File(applicationContext.filesDir, "deepfilternet")
        if (!destDir.exists()) destDir.mkdirs()

        var totalBytesToDownload = 0L
        var totalBytesDownloaded = 0L

        // Pre-flight check to get total size (optional, but good for total progress)
        // For simplicity, we'll just track progress per file or assume equal weight, 
        // OR we can make HEAD requests. 
        // Let's stick to a simpler approach: 25% progress per file for now, 
        // internal file progress fills that 25% chunk.
        
        try {
            modelFiles.forEachIndexed { index, fileName ->
                val finalFile = File(destDir, fileName)
                val tempFile = File(destDir, "$fileName.part")
                
                // If final file exists, skip (or verify hash if we wanted to be strict)
                if (finalFile.exists()) {
                    setProgress(index + 1, modelFiles.size, 0f) // 100% for this file
                    return@forEachIndexed
                }

                val url = "$baseUrl$fileName"
                downloadFileResumable(url, tempFile, index, modelFiles.size)
                
                if (tempFile.exists()) {
                    tempFile.renameTo(finalFile)
                }
            }
            
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun downloadFileResumable(
        url: String, 
        tempFile: File, 
        fileIndex: Int, 
        totalFiles: Int
    ) {
        var downloadedBytes = 0L
        if (tempFile.exists()) {
            downloadedBytes = tempFile.length()
        }

        val requestBuilder = Request.Builder().url(url)
        if (downloadedBytes > 0) {
            requestBuilder.header("Range", "bytes=$downloadedBytes-")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            // If Range not satisfiable (e.g. file changed or complete), restart
            if (response.code == 416) {
                tempFile.delete()
                downloadFileResumable(url, tempFile, fileIndex, totalFiles)
                return
            }
            throw IOException("Unexpected code $response")
        }

        val body = response.body ?: throw IOException("Body is null")
        val contentLength = body.contentLength() + downloadedBytes
        
        // Input stream from response
        body.source().use { source ->
            // Append if resuming, else create new
            FileOutputStream(tempFile, downloadedBytes > 0).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var bytesSinceUpdate = 0L
                
                // We use source.read(buffer) from Okio, or simple input stream
                val inputStream = body.byteStream()
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    bytesSinceUpdate += bytesRead
                    
                    // Update progress every 100KB or so to avoid spamming binding updates
                    if (bytesSinceUpdate > 100 * 1024) {
                        val fileProgress = if (contentLength > 0) downloadedBytes.toFloat() / contentLength else 0f
                        setProgress(fileIndex, totalFiles, fileProgress)
                        bytesSinceUpdate = 0
                    }
                }
                output.flush()
            }
        }
    }

    private suspend fun setProgress(fileIndex: Int, totalFiles: Int, fileProgress: Float) {
        // Calculate total progress: 
        // (completedOps + currentOpProgress) / totalOps
        val totalProgress = (fileIndex + fileProgress) / totalFiles * 100f
        
        setProgress(
            workDataOf(
                "PROGRESS" to totalProgress,
                "CURRENT_FILE" to (fileIndex + 1),
                "TOTAL_FILES" to totalFiles
            )
        )
    }
}
