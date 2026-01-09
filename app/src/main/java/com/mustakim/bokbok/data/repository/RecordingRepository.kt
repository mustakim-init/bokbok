package com.mustakim.bokbok.data.repository

import com.mustakim.bokbok.data.local.dao.RecordingDao
import com.mustakim.bokbok.data.local.entity.RecordingEntity
import com.mustakim.bokbok.data.local.entity.RecordingStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecordingRepository @Inject constructor(
    private val recordingDao: RecordingDao
) {
    fun getPendingRecordings(): Flow<List<RecordingEntity>> = recordingDao.getPendingRecordings()
    
    fun getAllRecordings(): Flow<List<RecordingEntity>> = recordingDao.getAllRecordings()
    
    suspend fun getRecording(id: Long): RecordingEntity? = recordingDao.getRecordingById(id)

    suspend fun addPendingRecording(
        videoPath: String,
        micPath: String,
        internalPath: String,
        durationMs: Long
    ): Long {
        val id = System.currentTimeMillis()
        val recording = RecordingEntity(
            id = id,
            videoPath = videoPath,
            micPath = micPath,
            internalPath = internalPath,
            durationMs = durationMs,
            status = RecordingStatus.PENDING
        )
        recordingDao.insert(recording)
        return id
    }

    suspend fun updateStatus(id: Long, status: RecordingStatus, finalVideoPath: String? = null) {
        val recording = recordingDao.getRecordingById(id) ?: return
        val updated = recording.copy(
            status = status,
            videoPath = finalVideoPath ?: recording.videoPath
        )
        recordingDao.update(updated)
    }

    suspend fun deleteRecording(id: Long) {
        val recording = recordingDao.getRecordingById(id) ?: return
        
        // Delete actual files from storage
        try {
            java.io.File(recording.videoPath).delete()
            java.io.File(recording.micPath).delete()
            java.io.File(recording.internalPath).delete()
            
            // If it was a temp file, also try deleting that
            val tempPath = recording.videoPath.replace(".mp4", "_temp.mp4")
            java.io.File(tempPath).delete()
        } catch (e: Exception) {
            // Log but continue to delete from DB
        }
        
        recordingDao.deleteById(id)
    }
}
