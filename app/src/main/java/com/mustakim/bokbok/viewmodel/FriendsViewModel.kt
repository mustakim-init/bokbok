package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.FriendRequest
import com.mustakim.bokbok.data.model.FriendWithUser
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.FriendsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val friendsRepository: FriendsRepository
) : ViewModel() {

    val friends: StateFlow<List<FriendWithUser>> = friendsRepository.observeFriends()
        .stateIn(
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
        private val friendsRepository: FriendsRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(FriendsViewModel::class.java)) {
                return FriendsViewModel(friendsRepository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

sealed class FriendsUiState {
    data object Idle : FriendsUiState()
    data object Loading : FriendsUiState()
    data class Success(val message: String) : FriendsUiState()
    data class Error(val message: String) : FriendsUiState()
}
