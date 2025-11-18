package com.mustakim.bokbok.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ConnectionStateManager {

    private val _disconnectedIds = MutableStateFlow<Set<String>>(emptySet())
    val disconnectedIds: StateFlow<Set<String>> = _disconnectedIds.asStateFlow()

    fun markDisconnected(id: String) {
        _disconnectedIds.value = _disconnectedIds.value + id
    }

    fun markConnected(id: String) {
        _disconnectedIds.value = _disconnectedIds.value - id
    }
}
