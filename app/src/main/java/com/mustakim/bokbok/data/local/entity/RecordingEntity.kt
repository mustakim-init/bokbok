package com.mustakim.bokbok.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "recordings")
@Serializable
data class RecordingEntity(
    @PrimaryKey val id: Long, // Use timestamp as ID
    val videoPath: String,    // Path to temp video (pending) or final video (processed)
    val micPath: String,      // Path to raw mic PCM
    val internalPath: String, // Path to raw int PCM
    val width: Int,
    val height: Int,
    val audioSampleRate: Int,
    val audioBitrate: Int,
    val isMono: Boolean,
    val durationMs: Long,
    val status: RecordingStatus, // PENDING, PROCESSED, FAILED
    val createdAt: Long = System.currentTimeMillis()
)

enum class RecordingStatus {
    PENDING,
    PROCESSING,
    PROCESSED,
    FAILED
}
