package com.mustakim.bokbok.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SpeakingStateManager {

    // Set of userIds currently considered "speaking" on this device
    private val _speakingIds = MutableStateFlow<Set<String>>(emptySet())
    val speakingIds: StateFlow<Set<String>> = _speakingIds.asStateFlow()

    fun updateSpeakingIds(ids: Set<String>) {
        _speakingIds.value = ids
    }
}
