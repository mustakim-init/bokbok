package com.mustakim.bokbok.music.innertube.models.body

import com.mustakim.bokbok.music.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
