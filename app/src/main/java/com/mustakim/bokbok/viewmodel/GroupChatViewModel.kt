package com.mustakim.bokbok.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.AuthRepository
import com.mustakim.bokbok.data.repository.HybridGroupChatRepository
import com.mustakim.bokbok.data.repository.SendMessageResult
import com.mustakim.bokbok.data.repository.UserRepository
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * GroupChatViewModel - Optimized with lazy initialization
 *
 * Performance optimizations:
 * 1. Uses SharingStarted.Lazily for message flows
 * 2. Heavy operations use Dispatchers.IO
 */
@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val repository: HybridGroupChatRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val groupId: String = savedStateHandle.get<String>("groupId")
        ?: throw IllegalArgumentException("groupId is required")

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
        viewModelScope.launch(Dispatchers.IO) {
            repository.addReaction(groupId, messageId, emoji)
        }
    }

    fun removeReaction(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeReaction(groupId, messageId)
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteMessage(groupId, messageId, forEveryone)
        }
    }

    fun searchMessages(query: String) {
        if (query.isBlank()) {
            _isSearching.value = false
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val results = repository.searchMessages(groupId, query)
            _searchResults.value = results
        }
    }

    fun clearSearch() {
        _isSearching.value = false
        _searchResults.value = emptyList()
    }

    fun clearChatHistory(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearChatHistory(groupId)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    fun leaveGroup(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.leaveGroup(groupId)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                 android.util.Log.e("GroupChatViewModel", "Failed to leave group", e)
            }
        }
    }

    fun addMemberToGroup(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addMember(groupId, userId)
        }
    }

    fun deleteGroup(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deleteGroup(groupId)
                kotlinx.coroutines.withContext(Dispatchers.Main) { onSuccess() }
            } catch (e: Exception) {
                android.util.Log.e("GroupChatViewModel", "Failed to delete group", e)
            }
        }
    }

    fun removeGroupImage() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.removeGroupImage(groupId)
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch(Dispatchers.IO) {
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
        _isUploadingImage.value = true
        _uploadError.value = null
        viewModelScope.launch(Dispatchers.IO) {
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

}
