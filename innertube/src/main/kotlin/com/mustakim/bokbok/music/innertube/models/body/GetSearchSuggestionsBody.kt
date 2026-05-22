package com.mustakim.bokbok.music.innertube.models.body

import com.mustakim.bokbok.music.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
