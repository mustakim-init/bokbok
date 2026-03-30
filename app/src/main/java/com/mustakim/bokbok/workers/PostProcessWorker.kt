package com.mustakim.bokbok.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.mustakim.bokbok.data.local.PreferencesManager
import com.mustakim.bokbok.data.repository.ModelRepository
import com.mustakim.bokbok.data.repository.RecordingRepository
import com.mustakim.bokbok.data.service.NativeRecorder
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

@HiltWorker
class PostProcessWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val recordingRepository: RecordingRepository,
    private val modelRepository: ModelRepository,
    private val preferencesManager: PreferencesManager
) : CoroutineWorker(context, workerParams) {

    private val nativeRecorder = NativeRecorder()
    private val NOTIFICATION_ID = 2001
    private val CHANNEL_ID = "post_processing_channel"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dbId = inputData.getLong("dbId", -1L)
        if (dbId == -1L) return@withContext Result.failure()

        val micGain = inputData.getFloat("micGain", 1.0f)
        val internalGain = inputData.getFloat("internalGain", 1.0f)

        val recording = recordingRepository.getRecording(dbId) ?: return@withContext Result.failure()
        
        setForeground(createForegroundInfo(0))

        try {
            val path = recording.videoPath
            val micPath = recording.micPath
            val intPath = recording.internalPath

            val settings = preferencesManager.recorderSettings.first()
            val noiseReduction = settings["noiseReduction"] as? Boolean ?: true
            val bleedReduction = settings["bleedReduction"] as? Boolean ?: true
            val exportMic = settings["exportMicOnly"] as? Boolean ?: false
            val exportInternal = settings["exportInternalOnly"] as? Boolean ?: false
            val studioMaster = settings["studioMaster"] as? Boolean ?: true
            
            val audioSampleRate = recording.audioSampleRate
            val audioBitrate = recording.audioBitrate
            val isMono = recording.isMono
            
            recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSING)
            
            val originalFile = File(path)
            val tempInputVideo = File(path.replace(".mp4", "_temp.mp4"))
            
            delay(200) 
            
            if (originalFile.renameTo(tempInputVideo)) {
                val musicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "BokBok/Audio")
                if ((exportMic || exportInternal) && !musicDir.exists()) {
                    musicDir.mkdirs()
                }
                
                val baseFilename = originalFile.nameWithoutExtension
                val micExportPath = if (exportMic) File(musicDir, "${baseFilename}_mic.m4a").absolutePath else ""
                val internalExportPath = if (exportInternal) File(musicDir, "${baseFilename}_internal.m4a").absolutePath else ""

                nativeRecorder.onProgressUpdate = { progress, msg ->
                    val percent = (progress * 100).toInt()
                    // Update WorkManager progress
                    setProgressAsync(workDataOf("progress" to progress, "message" to msg))
                    // Update notification
                    setForegroundAsync(createForegroundInfo(percent))
                }

                var success: Boolean
                try {
                    val pfd = ParcelFileDescriptor.open(tempInputVideo, ParcelFileDescriptor.MODE_READ_ONLY)
                    val videoFd = pfd.fd
                    
                    success = nativeRecorder.processRecording(
                        videoFd = videoFd,
                        videoWidth = recording.width,
                        videoHeight = recording.height,
                        micPath = micPath,
                        internalPath = intPath,
                        outputPath = path,
                        micExportPath = micExportPath,
                        internalExportPath = internalExportPath,
                        modelPath = modelRepository.getModelDirectory(),
                        enableBleed = bleedReduction, 
                        enableNoise = noiseReduction, 
                        enableStudioMaster = studioMaster,
                        micGain = micGain, 
                        internalGain = internalGain,
                        exportMic = exportMic,
                        exportInternal = exportInternal,
                        isMono = isMono,
                        audioSampleRate = audioSampleRate,
                        audioBitrate = audioBitrate
                    )
                    
                    pfd.close()
                } catch (e: Exception) {
                    Timber.e(e, "Worker: Native processing failed")
                    success = false
                }
                
                nativeRecorder.onProgressUpdate = null
                
                if (success) {
                    tempInputVideo.delete()
                    File(micPath).delete()
                    File(intPath).delete()
                    recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.PROCESSED, path)
                    
                    val filesToScan = mutableListOf(path)
                    if (micExportPath.isNotEmpty() && File(micExportPath).exists()) {
                        filesToScan.add(micExportPath)
                    }
                    if (internalExportPath.isNotEmpty() && File(internalExportPath).exists()) {
                        filesToScan.add(internalExportPath)
                    }

                    MediaScannerConnection.scanFile(applicationContext, filesToScan.toTypedArray(), null) { p, _ ->
                        Timber.i("Worker: MediaScanner completed: $p")
                    }

                    Result.success(workDataOf("path" to path))
                } else {
                    tempInputVideo.renameTo(originalFile)
                    recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED)
                    Result.failure()
                }
            } else {
                 recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED)
                 Result.failure()
            }
        } catch (e: Exception) {
            Timber.e(e, "PostProcessWorker failed")
            recordingRepository.updateStatus(dbId, com.mustakim.bokbok.data.local.entity.RecordingStatus.FAILED)
            Result.failure()
        } finally {
            nativeRecorder.onProgressUpdate = null
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Video Processing", NotificationManager.IMPORTANCE_LOW)
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("BokBok: Finalizing Video")
            .setContentText("Processing: $progress%")
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }
}
