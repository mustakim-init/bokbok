package com.mustakim.bokbok.data.model

import androidx.compose.runtime.Immutable


@Immutable
data class VoiceRoomParticipant(
    val id: String,
    val name: String,
    val avatarUrl: String = "",
    val isHost: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeaking: Boolean = false
)
