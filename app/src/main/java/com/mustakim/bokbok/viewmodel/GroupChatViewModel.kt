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
    val groupId: String
) : ViewModel() {

    val currentUserId: String = repository.currentUserId

    // Group info from Room (instant) - WhileSubscribed ensures updates propagate
    val groupInfo = repository.observeGroup(groupId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    private val _searchResults = MutableStateFlow<List<Message>>(emptyList())
    val searchResults: StateFlow<List<Message>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // Summon rate limit error message
    private val _summonError = MutableStateFlow<String?>(null)
    val summonError: StateFlow<String?> = _summonError.asStateFlow()

    fun clearSummonError() {
        _summonError.value = null
    }

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

    fun searchMessages(query: String) {
        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            _searchResults.value = repository.searchMessages(groupId, query)
        }
    }

    fun clearSearch() {
        _isSearching.value = false
        _searchResults.value = emptyList()
    }

    fun clearChatHistory(onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.clearChatHistory(groupId)
            onSuccess()
        }
    }

    fun leaveGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.leaveGroup(groupId)
                onSuccess()
            } catch (e: Exception) {
                 android.util.Log.e("GroupChatViewModel", "Failed to leave group", e)
            }
        }
    }

    fun addMemberToGroup(userId: String) {
        viewModelScope.launch {
            repository.addMember(groupId, userId)
        }
    }

    fun deleteGroup(onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                repository.deleteGroup(groupId)
                onSuccess()
            } catch (e: Exception) {
                // Ideally handle error (show toast), but for now we ensure we don't pop if failed, or maybe we do?
                // If we want to ensure deletion happened, we only call onSuccess if successful.
                // However, if repository throws, this block catches it? 
                // Repository rethrows. So we need try-catch here if we want to avoid crash.
                // But previously it would crash? Or just cancel.
                // Let's catch and maybe log, or just let it crash if that helps debugging. 
                // But safer to catch.
                android.util.Log.e("GroupChatViewModel", "Failed to delete group", e)
            }
        }
    }

    fun removeGroupImage() {
        viewModelScope.launch {
            repository.removeGroupImage(groupId)
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            repository.removeMember(groupId, userId)
        }
    }

    private val _isUploadingImage = MutableStateFlow(false)
    val isUploadingImage: StateFlow<Boolean> = _isUploadingImage.asStateFlow()

    private val _uploadError = MutableStateFlow<String?>(null)
    val uploadError: StateFlow<String?> = _uploadError.asStateFlow()

    fun clearUploadError() {
        _uploadError.value = null
    }

    fun updateGroupImage(uri: android.net.Uri) {
        viewModelScope.launch {
            _isUploadingImage.value = true
            _uploadError.value = null
            try {
                repository.updateGroupImage(groupId, uri)
            } catch (e: Exception) {
                _uploadError.value = "Failed to upload image: ${e.message}"
                android.util.Log.e("GroupChatViewModel", "Failed to upload image", e)
            } finally {
                _isUploadingImage.value = false
            }
        }
    }

    fun sendMessage() {
        val text = _messageText.value
        if (text.isBlank()) return

        val replyTo = _replyingTo.value

        viewModelScope.launch {
            val result = repository.sendMessage(groupId, text, replyTo)
            
            when (result) {
                is com.mustakim.bokbok.data.repository.SendMessageResult.Success -> {
                    _messageText.value = ""
                    _replyingTo.value = null
                    _showEmojiPicker.value = false
                }
                is com.mustakim.bokbok.data.repository.SendMessageResult.RateLimited -> {
                    _summonError.value = result.reason
                }
                is com.mustakim.bokbok.data.repository.SendMessageResult.Error -> {
                    _summonError.value = result.message
                }
            }
        }
    }

    class Factory(
        private val context: Context,
        val groupId: String
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repository = HybridGroupChatRepository(context)
            return GroupChatViewModel(repository, groupId) as T
        }
    }
}
