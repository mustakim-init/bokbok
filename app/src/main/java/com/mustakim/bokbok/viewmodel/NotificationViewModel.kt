package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationViewModel : ViewModel() {
    private val repository = NotificationRepository()
    
    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    
    fun observeNotifications(userId: String) {
        viewModelScope.launch {
            repository.observeNotifications(userId).collect { notifications ->
                _unreadCount.value = notifications.count { !it.isRead }
            }
        }
    }
}
