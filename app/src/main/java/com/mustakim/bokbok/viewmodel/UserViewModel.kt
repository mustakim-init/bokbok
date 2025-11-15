package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.Stable

@Stable
class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository(application.applicationContext)

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        loadCurrentUser()
    }

    fun loadCurrentUser() {
        viewModelScope.launch {
            _isLoading.value = true
            val userId = repository.getCurrentUserId()

            if (userId != null) {
                repository.getUserProfile(userId).fold(
                    onSuccess = { user ->
                        _currentUser.value = user
                        _isLoading.value = false
                    },
                    onFailure = {
                        _isLoading.value = false
                    }
                )
            } else {
                _isLoading.value = false
            }
        }
    }

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
    }

    fun refreshUser() {
        loadCurrentUser()
    }
}
