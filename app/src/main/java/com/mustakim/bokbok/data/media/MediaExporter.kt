package com.mustakim.bokbok.data.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Extracts only the audio from a recorded video file.
     * Replicates kapture's "generate extra files" robustness.
     */
    @OptIn(UnstableApi::class)
    suspend fun extractAudio(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val transformer = Transformer.Builder(context)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .build()

            val mediaItem = MediaItem.fromUri(inputPath)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true) // Extract audio only
                .build()

            transformer.start(editedMediaItem, outputPath)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extracts only the video (no audio) from a recorded file.
     */
    @OptIn(UnstableApi::class)
    suspend fun extractVideoOnly(inputPath: String, outputPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val transformer = Transformer.Builder(context)
                .build()

            val mediaItem = MediaItem.fromUri(inputPath)
            val editedMediaItem = EditedMediaItem.Builder(mediaItem)
                .setRemoveAudio(true) // Extract video only
                .build()

            transformer.start(editedMediaItem, outputPath)
            true
        } catch (e: Exception) {
            false
        }
    }
}
