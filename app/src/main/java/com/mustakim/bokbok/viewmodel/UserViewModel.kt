package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.data.repository.PresenceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import androidx.compose.runtime.Stable

@Stable
class UserViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository(application.applicationContext)
    private val presenceRepository = PresenceRepository()

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)


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
                        
                        // ✅ FIX: Set user online when profile loads successfully
                        try {
                            presenceRepository.setUserOnline()
                        } catch (e: Exception) {
                            // Ignore errors, presence is not critical for app functionality
                            android.util.Log.w("UserViewModel", "Failed to set user online: ${e.message}")
                        }

                        // ✅ FIX: Ensure FCM token is up-to-date in Firestore
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                            if (!token.isNullOrBlank() && token != user.fcmToken) {
                                viewModelScope.launch {
                                    repository.updateFcmToken(token)
                                }
                            }
                        }
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
    
    override fun onCleared() {
        super.onCleared()
        // ✅ FIX: Set user offline when ViewModel is cleared
        try {
            presenceRepository.setUserOffline()
        } catch (e: Exception) {
            // Best effort, ignore errors
            android.util.Log.w("UserViewModel", "Failed to set user offline: ${e.message}")
        }
    }
}
