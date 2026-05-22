package com.mustakim.bokbok.music.models

import com.mustakim.bokbok.music.innertube.models.YTItem
import com.mustakim.bokbok.music.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
