package com.mustakim.bokbok.music.models

import com.mustakim.bokbok.music.innertube.models.YTItem

data class ItemsPage(
    val items: List<YTItem>,
    val continuation: String?,
)
