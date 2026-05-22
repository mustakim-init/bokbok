package com.mustakim.bokbok.music.utils

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.mustakim.bokbok.music.db.entities.Song
import com.mustakim.bokbok.music.db.entities.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * High performance local music scanner inspired by PixelPlayer.
 */
object LocalDeviceMusicScanner {
    suspend fun getLocalSongs(context: Context, directoryRuleResolver: com.mustakim.bokbok.music.utils.directory.DirectoryRuleResolver? = null): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(
                uri, projection, selection, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataColumn)
                    
                    // Apply directory filtering if resolver is provided
                    if (directoryRuleResolver != null) {
                        val lastSlashIndex = path.lastIndexOf('/')
                        val parentPath = if (lastSlashIndex != -1) path.substring(0, lastSlashIndex) else ""
                        if (directoryRuleResolver.isBlocked(parentPath)) {
                            continue
                        }
                    }

                    val id = cursor.getLong(idColumn)
                    val rawTitle = cursor.getString(titleColumn) ?: "Unknown"
                    val rawArtist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val durationMs = cursor.getInt(durationColumn)


                    // PixelPlayer's advanced title parsing to extract featured artists
                    val (displayTitle, titleArtists) = extractArtistsFromTitle(rawTitle)
                    
                    // Split rawArtist by common delimiters like PixelPlayer does
                    val artistNames = rawArtist.split(Regex("(?i)(?: feat\\.? | ft\\.? | x | & |, | \\+ )"))
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toMutableList()

                    titleArtists.forEach { ta ->
                        if (artistNames.none { it.equals(ta, ignoreCase = true) }) {
                            artistNames.add(ta)
                        }
                    }

                    val allArtistsText = artistNames.joinToString(", ").takeIf { it.isNotBlank() } ?: "Unknown Artist"
                    val audioUri = "content://media/external/audio/media/$id"
                    val albumArtUri = "content://media/external/audio/media/$id/albumart"

                    val songEntity = SongEntity(
                        id = "local_device_$id",
                        title = displayTitle,
                        duration = durationMs / 1000,
                        thumbnailUrl = albumArtUri,
                        liked = false,
                        likedDate = null,
                        totalPlayTime = 0L,
                        isLocal = true
                    )
                    
                    val formatEntity = com.mustakim.bokbok.music.db.entities.FormatEntity(
                        id = "local_device_$id",
                        itag = 0,
                        mimeType = "audio/mpeg", 
                        codecs = "",
                        bitrate = 0,
                        sampleRate = null,
                        contentLength = 0,
                        loudnessDb = null,
                        playbackUrl = audioUri
                    )

                    val artistEntities = artistNames.map { name ->
                        com.mustakim.bokbok.music.db.entities.ArtistEntity(
                            id = "local_artist_$name",
                            name = name,
                            isLocal = true
                        )
                    }

                    songs.add(
                        Song(
                            song = songEntity,
                            artists = artistEntities,
                            album = null,
                            format = formatEntity
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        songs
    }

    private fun extractArtistsFromTitle(title: String): Pair<String, List<String>> {
        var cleanTitle = title
        val extractedArtists = mutableListOf<String>()
        val featuredRegex = Regex("(?i)\\(feat\\.? (.*?)\\)|\\[feat\\.? (.*?)\\]|\\(ft\\.? (.*?)\\)|\\[ft\\.? (.*?)\\]")
        val matchResult = featuredRegex.find(title)
        if (matchResult != null) {
            cleanTitle = title.replace(matchResult.value, "").trim()
            val innerContent = matchResult.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
            if (!innerContent.isNullOrBlank()) {
                val split = innerContent.split(Regex("(?i)(?: & |, | x | \\+ | and )"))
                extractedArtists.addAll(split.map { it.trim() }.filter { it.isNotEmpty() })
            }
        }
        return Pair(cleanTitle, extractedArtists)
    }

    private fun formatDuration(seconds: Int): String {
        val minutes = seconds / 60
        val secs = seconds % 60
        return String.format("%d:%02d", minutes, secs)
    }
}
