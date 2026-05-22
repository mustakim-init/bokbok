package com.mustakim.bokbok.music
import com.mustakim.bokbok.music.LocalSyncUtils
import com.mustakim.bokbok.music.LocalDownloadUtil
import com.mustakim.bokbok.music.LocalPlayerConnection
import com.mustakim.bokbok.music.LocalDatabase

import androidx.compose.runtime.compositionLocalOf
import com.mustakim.bokbok.music.db.MusicDatabase
import com.mustakim.bokbok.music.playback.DownloadUtil
import com.mustakim.bokbok.music.playback.PlayerConnection
import com.mustakim.bokbok.music.utils.SyncUtils

val LocalDatabase = compositionLocalOf<MusicDatabase> { error("No MusicDatabase provided") }
val LocalPlayerConnection = compositionLocalOf<PlayerConnection?> { null }
val LocalDownloadUtil = compositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }
val LocalSyncUtils = compositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
