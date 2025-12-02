package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.FriendRequest
import com.mustakim.bokbok.data.model.FriendWithUser
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val friendsRepository: FriendsRepository,
    private val chatRepository: com.mustakim.bokbok.data.repository.ChatRepository
) : ViewModel() {

    val friends: StateFlow<List<FriendWithUser>> = friendsRepository.observeFriends()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Combine friends with their last messages to create chat summaries
    // Optimized: Use single listener on chats collection instead of N listeners (one per friend)
    @OptIn(ExperimentalCoroutinesApi::class)
    val chats: StateFlow<List<ChatUiModel>> = friends.flatMapLatest { friendList ->
        if (friendList.isEmpty()) {
            flowOf(emptyList())
        } else {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
            if (currentUserId == null) {
                flowOf(emptyList())
            } else {
                // Single query for all chat documents where user is a participant
                FirebaseFirestore.getInstance()
                    .collection("chats")
                    .whereArrayContains("participants", currentUserId)
                    .snapshots()
                    .map { snapshot ->
                        // Create a map of friendId to chat data
                        val chatDataMap = snapshot.documents.mapNotNull { doc ->
                            val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                            val friendId = participants.firstOrNull { it != currentUserId } as? String ?: return@mapNotNull null
                            
                            val lastMsgMap = doc.get("lastMessage") as? Map<*, *>
                            val senderId = lastMsgMap?.get("senderId") as? String
                            val senderName = when {
                                senderId == currentUserId -> "You"
                                else -> null // Will be filled with friend name later
                            }
                            
                            val lastMessageText = when {
                                lastMsgMap == null -> null
                                lastMsgMap["isDeleted"] as? Boolean == true -> "Message unsent"
                                lastMsgMap["type"] == "IMAGE" -> "Sent an image"
                                lastMsgMap["type"] == "AUDIO" -> "Sent an audio"
                                else -> lastMsgMap["text"] as? String ?: ""
                            }
                            
                            val timestamp = (lastMsgMap?.get("timestamp") as? Timestamp)?.toDate()?.time ?: 0L
                            val unreadCount = (doc.getLong("unreadCount_$currentUserId"))?.toInt() ?: 0
                            
                            friendId to Triple(lastMessageText, timestamp, unreadCount to senderName)
                        }.toMap()
                        
                        // Now create ChatUiModel for ALL friends, using chat data if available
                        val chats = friendList.map { friend ->
                            val chatData = chatDataMap[friend.user.uid]
                            val (lastMessageText, timestamp, unreadData) = chatData ?: Triple(null, 0L, 0 to null)
                            val (unreadCount, senderNameFromChat) = unreadData as Pair<Int, String?>
                            
                            val senderName = senderNameFromChat ?: run {
                                // Determine sender from friend if not from "You"
                                if (lastMessageText != null && senderNameFromChat == null) {
                                    friend.user.displayName.split(" ").firstOrNull() ?: "Friend"
                                } else null
                            }
                            
                            val displayMessage = when {
                                lastMessageText != null -> lastMessageText
                                friend.isOnline -> "Active now"
                                else -> "Start a conversation"
                            }
                            
                            ChatUiModel(
                                friend = friend,
                                lastMessage = displayMessage,
                                lastMessageSender = senderName,
                                timestamp = timestamp,
                                unreadCount = unreadCount,
                                isLastMessageRead = unreadCount == 0
                            )
                        }
                        
                        // Sort by timestamp, with chats without messages at the bottom
                        chats.sortedByDescending { it.timestamp }
                    }
                    .catch { emit(emptyList()) }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val incomingRequests: StateFlow<List<FriendRequest>> =
        friendsRepository.observeIncomingFriendRequests()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val outgoingRequests: StateFlow<List<FriendRequest>> =
        friendsRepository.observeOutgoingFriendRequests()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults: StateFlow<List<User>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _uiState = MutableStateFlow<FriendsUiState>(FriendsUiState.Idle)
    val uiState: StateFlow<FriendsUiState> = _uiState.asStateFlow()

    fun searchUsers(query: String) {
        _searchQuery.value = query

        if (query.length < 2) {
            _searchResults.value = emptyList()
            return
        }

        viewModelScope.launch {
            _isSearching.value = true

            friendsRepository.searchUsersByUsername(query)
                .onSuccess { users ->
                    _searchResults.value = users
                }
                .onFailure {
                    _uiState.value = FriendsUiState.Error("Search failed: ${it.message}")
                }

            _isSearching.value = false
        }
    }

    fun sendFriendRequest(targetUserId: String) {
        viewModelScope.launch {
            _uiState.value = FriendsUiState.Loading

            friendsRepository.sendFriendRequest(targetUserId)
                .onSuccess {
                    _uiState.value = FriendsUiState.Success("Friend request sent!")
                    _searchQuery.value = ""
                    _searchResults.value = emptyList()
                }
                .onFailure {
                    _uiState.value = FriendsUiState.Error(it.message ?: "Failed to send request")
                }
        }
    }

    fun acceptFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            friendsRepository.acceptFriendRequest(friendshipId)
                .onSuccess {
                    _uiState.value = FriendsUiState.Success("Friend request accepted!")
                }
                .onFailure {
                    _uiState.value = FriendsUiState.Error(it.message ?: "Failed to accept")
                }
        }
    }

    fun declineFriendRequest(friendshipId: String) {
        viewModelScope.launch {
            friendsRepository.removeFriendship(friendshipId)
                .onSuccess {
                    _uiState.value = FriendsUiState.Success("Request declined")
                }
                .onFailure {
                    _uiState.value = FriendsUiState.Error(it.message ?: "Failed to decline")
                }
        }
    }

    fun removeFriend(friendshipId: String) {
        viewModelScope.launch {
            friendsRepository.removeFriendship(friendshipId)
                .onSuccess {
                    _uiState.value = FriendsUiState.Success("Friend removed")
                }
                .onFailure {
                    _uiState.value = FriendsUiState.Error(it.message ?: "Failed to remove")
                }
        }
    }

    fun clearUiState() {
        _uiState.value = FriendsUiState.Idle
    }

    // Factory for creating FriendsViewModel
    class Factory(
        private val friendsRepository: FriendsRepository,
        private val chatRepository: com.mustakim.bokbok.data.repository.ChatRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FriendsViewModel::class.java)) {
                return FriendsViewModel(friendsRepository, chatRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

// Data class for UI representation of a chat item
data class ChatUiModel(
    val friend: FriendWithUser,
    val lastMessage: String,
    val lastMessageSender: String? = null, // "You", friend name, or null
    val timestamp: Long,
    val unreadCount: Int = 0,
    val isLastMessageRead: Boolean = true
)

sealed class FriendsUiState {
    data object Idle : FriendsUiState()
    data object Loading : FriendsUiState()
    data class Success(val message: String) : FriendsUiState()
    data class Error(val message: String) : FriendsUiState()
}
