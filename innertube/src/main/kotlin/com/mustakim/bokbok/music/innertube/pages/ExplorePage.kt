package com.mustakim.bokbok.music.innertube.pages

import com.mustakim.bokbok.music.innertube.models.AlbumItem

data class ExplorePage(
    val newReleaseAlbums: List<AlbumItem>,
    val moodAndGenres: List<MoodAndGenres.Item>,
)
