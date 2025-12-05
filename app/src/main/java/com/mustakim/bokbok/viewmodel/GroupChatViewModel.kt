package com.mustakim.bokbok.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.HybridGroupChatRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GroupChatViewModel(
    private val repository: HybridGroupChatRepository,
    private val groupId: String
) : ViewModel() {

    val currentUserId: String = repository.currentUserId

    // Group info from Room (instant)
    val groupInfo = repository.observeGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    // Group name derived from groupInfo
    private val _groupName = MutableStateFlow("Group")
    val groupName: StateFlow<String> = _groupName.asStateFlow()

    // Group members from Room (instant)
    val groupMembers = repository.observeGroupMembers(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Messages from Room (instant)
    val messages = repository.getMessages(groupId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _messageText = MutableStateFlow("")
    val messageText: StateFlow<String> = _messageText.asStateFlow()

    private val _replyingTo = MutableStateFlow<Message?>(null)
    val replyingTo: StateFlow<Message?> = _replyingTo.asStateFlow()

    private val _showEmojiPicker = MutableStateFlow(false)
    val showEmojiPicker: StateFlow<Boolean> = _showEmojiPicker.asStateFlow()

    init {
        // Observe group info and update name
        viewModelScope.launch {
            repository.observeGroup(groupId).collect { group ->
                group?.let {
                    _groupName.value = it.name
                }
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
            repository.addReaction(groupId, messageId, emoji)
        }
    }

    fun removeReaction(messageId: String) {
        viewModelScope.launch {
            repository.removeReaction(groupId, messageId)
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch {
            repository.deleteMessage(groupId, messageId, forEveryone)
        }
    }

    fun sendMessage() {
        val text = _messageText.value
        if (text.isBlank()) return

        val replyTo = _replyingTo.value

        viewModelScope.launch {
            repository.sendMessage(groupId, text, replyTo)
            
            _messageText.value = ""
            _replyingTo.value = null
            _showEmojiPicker.value = false
        }
    }

    class Factory(
        private val context: Context,
        private val groupId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = HybridGroupChatRepository(context)
            return GroupChatViewModel(repository, groupId) as T
        }
    }
}
