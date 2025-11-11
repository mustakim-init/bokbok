package com.mustakim.bokbok.state

import androidx.compose.runtime.*
import com.mustakim.bokbok.data.model.VoiceRoom

object RoomStateManager {
    private val _minimizedRoom = mutableStateOf<VoiceRoom?>(null)
    val minimizedRoom: State<VoiceRoom?> = _minimizedRoom

    private val _isMuted = mutableStateOf(false)
    val isMuted: State<Boolean> = _isMuted

    fun minimizeRoom(room: VoiceRoom, isMuted: Boolean) {
        _minimizedRoom.value = room
        _isMuted.value = isMuted
    }

    fun expandRoom() {
        // Keep the room data but don't clear it
    }

    fun leaveRoom() {
        _minimizedRoom.value = null
        _isMuted.value = false
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }
}
