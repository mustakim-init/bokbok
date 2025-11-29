package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.ChatRepository
import com.mustakim.bokbok.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val friendId: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _friendUser = MutableStateFlow<User?>(null)
    val friendUser: StateFlow<User?> = _friendUser.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    init {
        loadFriendDetails()
        loadMessages()
    }

    private fun loadFriendDetails() {
        viewModelScope.launch {
            userRepository.getUserProfile(friendId).onSuccess { user ->
                _friendUser.value = user
            }
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            chatRepository.getMessages(friendId).collect {
                _messages.value = it
            }
        }
    }

    fun onMessageChange(text: String) {
        _messageText.value = text
    }

    fun sendMessage() {
        val text = _messageText.value
        if (text.isBlank()) return

        viewModelScope.launch {
            // Optimistic update
            val newMessage = Message(
                id = java.util.UUID.randomUUID().toString(),
                senderId = "me", // In real app, get current user ID
                receiverId = friendId,
                text = text
            )
            _messages.value = listOf(newMessage) + _messages.value
            _messageText.value = ""

            chatRepository.sendMessage("me", friendId, text)
        }
    }

    class Factory(
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val friendId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(chatRepository, userRepository, friendId) as T
        }
    }
}
