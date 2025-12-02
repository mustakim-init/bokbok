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

class GroupChatViewModel(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val groupId: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _groupMembers = MutableStateFlow<Map<String, User>>(emptyMap())
    val groupMembers: StateFlow<Map<String, User>> = _groupMembers.asStateFlow()

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _replyingTo = MutableStateFlow<Message?>(null)
    val replyingTo: StateFlow<Message?> = _replyingTo.asStateFlow()

    private val _showEmojiPicker = MutableStateFlow(false)
    val showEmojiPicker: StateFlow<Boolean> = _showEmojiPicker.asStateFlow()

    init {
        loadGroupDetails()
        loadMessages()
    }

    private fun loadGroupDetails() {
        _groupMembers.value = chatRepository.getGroupMembers(groupId)
    }

    private fun loadMessages() {
        viewModelScope.launch {
            chatRepository.getGroupMessages(groupId).collect {
                _messages.value = it
            }
        }
    }

    fun onMessageChange(text: String) {
        _messageText.value = text
    }

    fun setReplyingTo(message: Message?) {
        _replyingTo.value = message
    }

    fun toggleEmojiPicker() {
        _showEmojiPicker.value = !_showEmojiPicker.value
    }

    fun setShowEmojiPicker(show: Boolean) {
        _showEmojiPicker.value = show
    }

    fun reactToMessage(messageId: String, emoji: String) {
        viewModelScope.launch {
            chatRepository.addGroupReaction(groupId, messageId, emoji, "me")
        }
    }

    fun removeReaction(messageId: String) {
        viewModelScope.launch {
            chatRepository.removeGroupReaction(groupId, messageId, "me")
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            chatRepository.deleteGroupMessage(groupId, messageId, forEveryone, "me")
        }
    }

    fun sendMessage() {
        val text = _messageText.value
        if (text.isBlank()) return

        val replyTo = _replyingTo.value

        viewModelScope.launch {
            chatRepository.sendGroupMessage("me", groupId, text, replyTo)
            
            _messageText.value = ""
            _replyingTo.value = null
            _showEmojiPicker.value = false
        }
    }

    class Factory(
        private val chatRepository: ChatRepository,
        private val userRepository: UserRepository,
        private val groupId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return GroupChatViewModel(chatRepository, userRepository, groupId) as T
        }
    }
}
