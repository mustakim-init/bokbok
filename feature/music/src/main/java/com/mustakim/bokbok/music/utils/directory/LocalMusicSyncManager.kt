package com.mustakim.bokbok.music.utils.directory

import android.content.Context
import android.util.Log
import com.mustakim.bokbok.data.local.dataStore
import com.mustakim.bokbok.data.local.getAsync
import com.mustakim.bokbok.music.constants.LocalMusicAllowedDirsKey
import com.mustakim.bokbok.music.constants.LocalMusicBlockedDirsKey
import com.mustakim.bokbok.music.db.MusicDatabase
import com.mustakim.bokbok.music.db.entities.FormatEntity
import com.mustakim.bokbok.music.db.entities.SongArtistMap
import com.mustakim.bokbok.music.utils.LocalDeviceMusicScanner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMusicSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {
    suspend fun syncLocalMusic() = withContext(Dispatchers.IO) {
        try {
            Log.d("LocalMusicSync", "Starting local music sync to Room database")

            val allowedDirsRaw = context.dataStore.getAsync(LocalMusicAllowedDirsKey) ?: ""
            val blockedDirsRaw = context.dataStore.getAsync(LocalMusicBlockedDirsKey) ?: ""

            val allowedDirsSet = allowedDirsRaw.split(",").filter { it.isNotBlank() }.toSet()
            val blockedDirsSet = blockedDirsRaw.split(",").filter { it.isNotBlank() }.toSet()

            val directoryRuleResolver = DirectoryRuleResolver(allowedDirsSet, blockedDirsSet)
            val songs = LocalDeviceMusicScanner.getLocalSongs(context, directoryRuleResolver)

            var addedCount = 0
            for (songItem in songs) {
                songItem.format?.let { format ->
                    database.insert(format as FormatEntity)
                }

                if (database.insert(songItem.song).toInt() != -1) {
                    addedCount++
                }

                songItem.artists.forEachIndexed { index, artist ->
                    database.insert(artist)
                    database.insert(
                        SongArtistMap(
                            songId = songItem.song.id,
                            artistId = artist.id,
                            position = index
                        )
                    )
                }
            }

            Log.d("LocalMusicSync", "Sync complete. Added $addedCount new songs.")
        } catch (e: Exception) {
            Log.e("LocalMusicSync", "Error syncing local music", e)
        }
    }
}
