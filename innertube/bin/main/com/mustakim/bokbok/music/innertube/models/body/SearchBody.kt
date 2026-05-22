package com.mustakim.bokbok.music.innertube.models.body

import com.mustakim.bokbok.music.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SearchBody(
    val context: Context,
    val query: String?,
    val params: String?,
)
