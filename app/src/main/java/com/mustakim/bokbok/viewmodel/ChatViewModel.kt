package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.data.repository.HybridChatRepository
import com.mustakim.bokbok.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * ChatViewModel - Optimized with lazy initialization
 *
 * Performance optimizations:
 * 1. init{} does NOT start Firebase listeners
 * 2. Call onScreenVisible() when chat screen appears
 * 3. Network calls run on Dispatchers.IO
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: HybridChatRepository,
    private val userRepository: UserRepository,
    private val friendsRepository: FriendsRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    val friendId: String = savedStateHandle.get<String>("userId")
        ?: throw IllegalArgumentException("userId is required")

    private val _messages = MutableStateFlow<List<Message>?>(null)
    val messages: StateFlow<List<Message>?> = _messages.asStateFlow()

    val currentUserId: String = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

    private val _friendUser = MutableStateFlow<User?>(null)
    val friendUser: StateFlow<User?> = _friendUser.asStateFlow()

    private val _isFriendOnline = MutableStateFlow(false)
    val isFriendOnline: StateFlow<Boolean> = _isFriendOnline.asStateFlow()

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

    // Track if we've already started listeners
    private var isInitialized = false

    fun clearSummonError() {
        _summonError.value = null
    }

    init {
        // ✅ OPTIMIZED: Only load friend details in init (lightweight, cached)
        // Firebase listeners start when onScreenVisible() is called
        loadFriendDetails()
    }

    /**
     * Call this when the screen becomes visible.
     * Starts Firebase listeners lazily.
     */
    fun onScreenVisible() {
        if (isInitialized) return
        isInitialized = true

        // Now start the heavier operations
        loadMessages()
        markMessagesAsRead()
        observeFriendOnlineStatus()
    }

    private fun markMessagesAsRead() {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.markMessagesAsRead(friendId)
        }
    }

    private fun loadFriendDetails() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                userRepository.getUserProfile(friendId)
            }.onSuccess { user ->
                _friendUser.value = user
            }
        }
    }

    private fun observeFriendOnlineStatus() {
        viewModelScope.launch {
            friendsRepository.observeUserOnlineStatus(friendId).collect { isOnline ->
                _isFriendOnline.value = isOnline
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
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.addReaction(messageId, emoji, currentUserId, friendId)
        }
    }

    fun removeReaction(messageId: String) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.removeReaction(messageId, currentUserId, friendId)
        }
    }

    fun deleteMessage(messageId: String, forEveryone: Boolean) {
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.deleteMessage(messageId, forEveryone, currentUserId, friendId)
        }
    }

    fun clearChatHistory(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepository.clearChatHistory(friendId)
            withContext(Dispatchers.Main) {
                _messages.value = emptyList()
                onSuccess()
            }
        }
    }

    fun removeFriend(onSuccess: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            // Remove from friends list using ID
            friendsRepository.removeFriendByUserId(friendId)
            // Clear chat history
            chatRepository.clearChatHistory(friendId)
            withContext(Dispatchers.Main) { onSuccess() }
        }
    }

    fun searchMessages(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }
        _isSearching.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val results = chatRepository.searchMessages(friendId, query)
            withContext(Dispatchers.Main) {
                _searchResults.value = results
            }
        }
    }

    fun clearSearch() {
        _isSearching.value = false
        _searchResults.value = emptyList()
    }

    fun sendMessage() {
        val text = _messageText.value
        if (text.isBlank()) return

        val replyTo = _replyingTo.value
        val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val friendDisplayName = _friendUser.value?.displayName

        viewModelScope.launch {
            val result = chatRepository.sendMessage(
                senderId = currentUserId,
                receiverId = friendId,
                text = text,
                replyTo = replyTo,
                friendDisplayName = friendDisplayName
            )
            
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
