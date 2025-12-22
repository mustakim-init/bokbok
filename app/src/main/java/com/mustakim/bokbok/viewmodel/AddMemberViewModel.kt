package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddMemberViewModel @Inject constructor(
    private val friendsRepository: FriendsRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _globalSearchResults = MutableStateFlow<List<User>>(emptyList())
    val globalSearchResults = _globalSearchResults.asStateFlow()

    val friends = friendsRepository.observeFriends()
        .map { list -> list.map { it.user } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun searchGlobal(query: String, excludeIds: List<String>) {
        if (query.isBlank()) {
            _globalSearchResults.value = emptyList()
            return
        }

        _isSearching.value = true
        viewModelScope.launch {
            userRepository.searchUsers(query).onSuccess { users ->
                val memberIds = excludeIds.toSet()
                _globalSearchResults.value = users.filter { it.uid !in memberIds }
            }
            _isSearching.value = false
        }
    }
}
