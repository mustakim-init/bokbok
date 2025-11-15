package com.mustakim.bokbok.state

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.State
import com.mustakim.bokbok.data.model.VoiceRoom

object RoomStateManager {

    private val _currentRoom: MutableState<VoiceRoom?> = mutableStateOf(null)
    val currentRoom: State<VoiceRoom?> = _currentRoom

    private val _isMinimized: MutableState<Boolean> = mutableStateOf(false)
    val isMinimized: State<Boolean> = _isMinimized

    private val _isMuted: MutableState<Boolean> = mutableStateOf(false)
    val isMuted: State<Boolean> = _isMuted

    fun joinRoom(room: VoiceRoom) {
        _currentRoom.value = room
        _isMinimized.value = false
        _isMuted.value = false
    }

    fun minimizeRoom(muted: Boolean) {
        if (_currentRoom.value != null) {
            _isMinimized.value = true
            setMuted(muted)
        }
    }

    fun setMuted(muted: Boolean) {
        if (_currentRoom.value != null) {
            _isMuted.value = muted
        }
    }

    fun expandRoom() {
        if (_currentRoom.value != null) {
            _isMinimized.value = false
        }
    }

    fun leaveRoom() {
        _currentRoom.value = null
        _isMinimized.value = false
        _isMuted.value = false
    }

    fun toggleMute() {
        if (_currentRoom.value != null) {
            setMuted(!_isMuted.value)
        }
    }
}
