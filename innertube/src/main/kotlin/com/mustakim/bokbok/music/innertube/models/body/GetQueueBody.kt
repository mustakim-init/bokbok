package com.mustakim.bokbok.music.innertube.models.body

import com.mustakim.bokbok.music.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetQueueBody(
    val context: Context,
    val videoIds: List<String>?,
    val playlistId: String?,
)
