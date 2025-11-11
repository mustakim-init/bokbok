package com.mustakim.bokbok.state

import androidx.compose.runtime.*
import com.mustakim.bokbok.data.model.VoiceRoom

object RoomStateManager {
    private val _currentRoom = mutableStateOf<VoiceRoom?>(null)
    val currentRoom: State<VoiceRoom?> = _currentRoom

    private val _isMinimized = mutableStateOf(false)
    val isMinimized: State<Boolean> = _isMinimized

    private val _isMuted = mutableStateOf(false)
    val isMuted: State<Boolean> = _isMuted

    fun joinRoom(room: VoiceRoom) {
        _currentRoom.value = room
        _isMinimized.value = false  // ✅ Just set it directly - no delay needed!
    }

    fun minimizeRoom(room: VoiceRoom, muted: Boolean) {
        _currentRoom.value = room
        _isMinimized.value = true
        _isMuted.value = muted
    }

    fun expandRoom() {
        _isMinimized.value = false
    }

    fun leaveRoom() {
        _currentRoom.value = null
        _isMinimized.value = false
        _isMuted.value = false
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }
}
