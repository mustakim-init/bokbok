package com.mustakim.bokbok.music.innertube.pages

import com.mustakim.bokbok.music.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
