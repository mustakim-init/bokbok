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
import com.mustakim.bokbok.data.repository.AuthRepository
import com.mustakim.bokbok.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val friendsRepository: FriendsRepository,
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val chatRepository: com.mustakim.bokbok.data.repository.ChatRepository,
    private val hybridChatRepository: com.mustakim.bokbok.data.repository.HybridChatRepository,
    private val hybridGroupChatRepository: com.mustakim.bokbok.data.repository.HybridGroupChatRepository
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
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            flowOf(emptyList())
        } else {
            // Combine individual chats and group chats
            combine(
                // Individual chats flow
                FirebaseFirestore.getInstance()
                    .collection("chats")
                    .whereArrayContains("participants", currentUserId)
                    .snapshots()
                    .map { snapshot ->
                        val chatDataMap = snapshot.documents.mapNotNull { doc ->
                            val participants = doc.get("participants") as? List<*> ?: return@mapNotNull null
                            val friendId = participants.firstOrNull { it != currentUserId } as? String ?: return@mapNotNull null

                            val lastMsgMap = doc.get("lastMessage") as? Map<*, *>
                            val senderId = lastMsgMap?.get("senderId") as? String
                            val senderName = when {
                                senderId == currentUserId -> "You"
                                else -> null
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

                        friendList.map { friend ->
                            val chatData = chatDataMap[friend.user.uid]
                            val (lastMessageText, timestamp, unreadData) = chatData ?: Triple(null, 0L, 0 to null)
                            val (unreadCount, senderNameFromChat) = unreadData as Pair<Int, String?>

                            val senderName = senderNameFromChat ?: run {
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
                                isLastMessageRead = unreadCount == 0,
                                isGroup = false
                            )
                        }
                    }
                    .catch { emit(emptyList()) },

                // Group chats flow
                FirebaseFirestore.getInstance()
                    .collection("groups")
                    .whereArrayContains("participants", currentUserId)
                    .snapshots()
                    .map { snapshot ->
                        snapshot.documents.mapNotNull { doc ->
                            // Use document id as the canonical group id
                            val groupId = doc.id
                            val groupName = doc.getString("name") ?: return@mapNotNull null
                            // Be tolerant: some records may use imageUrl or image_url
                            val imageUrl = doc.getString("imageUrl") ?: doc.getString("image_url") ?: ""
                            val lastMsgMap = doc.get("lastMessage") as? Map<*, *>

                            val lastMessageText = when {
                                lastMsgMap == null -> "No messages yet"
                                lastMsgMap["isDeleted"] as? Boolean == true -> "Message unsent"
                                lastMsgMap["type"] == "IMAGE" -> "Sent an image"
                                lastMsgMap["type"] == "AUDIO" -> "Sent an audio"
                                else -> lastMsgMap["text"] as? String ?: ""
                            }

                            val senderId = lastMsgMap?.get("senderId") as? String
                            val senderName = when {
                                senderId == currentUserId -> "You"
                                senderId != null -> "Member" // Simplified
                                else -> null
                            }

                            val timestamp = (doc.getTimestamp("lastMessageTime"))?.toDate()?.time ?: 0L

                            ChatUiModel(
                                groupId = groupId,
                                groupName = groupName,
                                groupImageUrl = imageUrl,
                                isGroup = true,
                                lastMessage = lastMessageText,
                                lastMessageSender = senderName,
                                timestamp = timestamp,
                                unreadCount = 0,
                                isLastMessageRead = true
                            )
                        }
                    }
                    .catch { emit(emptyList()) }
            ) { individualChats, groupChats ->
                // Merge and sort by timestamp
                (individualChats + groupChats).sortedByDescending { it.timestamp }
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

    fun createGroupChat(name: String, participantIds: List<String>) {
        viewModelScope.launch {
            _uiState.value = FriendsUiState.Loading
            try {
                chatRepository.createGroupChat(name, participantIds)
                _uiState.value = FriendsUiState.Success("Group chat created!")
            } catch (e: Exception) {
                _uiState.value = FriendsUiState.Error(e.message ?: "Failed to create group")
            }
        }
    }

    fun muteConversation(chatId: String, isMuted: Boolean) {
        // Placeholder for mute functionality
        // Ideally this would save to DataStore or Local DB preference
        _uiState.value = FriendsUiState.Success(if (isMuted) "Chat muted" else "Chat unmuted")
    }

    fun clearChatHistory(id: String, isGroup: Boolean) {
        viewModelScope.launch {
            if (isGroup) {
                hybridGroupChatRepository.clearChatHistory(id)
                _uiState.value = FriendsUiState.Success("Group chat history cleared")
            } else {
                hybridChatRepository.clearChatHistory(id)
                _uiState.value = FriendsUiState.Success("Chat history cleared")
            }
        }
    }

    fun leaveGroup(groupId: String) {
        viewModelScope.launch {
            try {
                hybridGroupChatRepository.leaveGroup(groupId)
                _uiState.value = FriendsUiState.Success("Left group")
            } catch (e: Exception) {
                _uiState.value = FriendsUiState.Error("Failed to leave group")
            }
        }
        _uiState.value = FriendsUiState.Idle
    }

    fun clearUiState() {
        _uiState.value = FriendsUiState.Idle
    }

}

// Data class for UI representation of a chat item (individual or group)
data class ChatUiModel(
    val friend: FriendWithUser? = null, // null for group chats
    val groupId: String? = null, // null for individual chats
    val groupName: String? = null, // null for individual chats
    val groupImageUrl: String? = null, // image url for group avatars
    val isGroup: Boolean = false,
    val lastMessage: String,
    val lastMessageSender: String? = null,
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
