package com.mustakim.bokbok.music.innertube.pages

import com.mustakim.bokbok.music.innertube.models.YTItem

data class ArtistItemsContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
