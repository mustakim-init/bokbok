package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.AuthRepository // Added AuthRepository import
import com.mustakim.bokbok.data.repository.ChatRepository
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.data.repository.PresenceRepository
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.startup.StartupManager
import dagger.hilt.android.lifecycle.HiltViewModel // Added HiltViewModel import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject // Added Inject import
import androidx.compose.runtime.Stable

/**
 * UserViewModel - Manages user state with deferred initialization
 *
 * Performance optimizations:
 * 1. init{} only loads cached/minimal data
 * 2. FCM token refresh is deferred to Stage 2
 * 3. Presence updates are deferred to Stage 2
 * 4. Network calls run on Dispatchers.IO
 */
@HiltViewModel
class UserViewModel @Inject constructor(
    private val repository: UserRepository,
    private val presenceRepository: PresenceRepository
) : ViewModel() {

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Track if heavy initialization is done
    private var isHeavyInitDone = false

    init {
        // ✅ OPTIMIZED: Only load user profile, defer heavy work
        loadCurrentUserLightweight()
    }

    /**
     * Lightweight user load - only fetches user profile (uses cache)
     * FCM and presence are deferred to Stage 2
     */
    private fun loadCurrentUserLightweight() {
        viewModelScope.launch {
            _isLoading.value = true
            val userId = repository.getCurrentUserId()

            if (userId != null) {
                // Run on IO thread to avoid blocking main thread
                withContext(Dispatchers.IO) {
                    repository.getUserProfile(userId)
                }.fold(
                    onSuccess = { user ->
                        _currentUser.value = user
                        _isLoading.value = false
                        
                        // ✅ OPTIMIZED: Register heavy work for Stage 2
                        if (!isHeavyInitDone) {
                            registerDeferredTasks(user)
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

    /**
     * Register FCM and presence tasks for deferred execution
     */
    private fun registerDeferredTasks(user: User) {
        isHeavyInitDone = true
        
        // Task 1: Set user online (deferred)
        StartupManager.registerDeferredTask {
            try {
                presenceRepository.setUserOnline()
                android.util.Log.d("UserViewModel", "✅ Deferred: User online status set")
            } catch (e: Exception) {
                android.util.Log.w("UserViewModel", "Failed to set user online: ${e.message}")
            }
        }
        
        // Task 2: Update FCM token (deferred)
        StartupManager.registerDeferredTask {
            try {
                // Use the proper .await() extension function from kotlinx.coroutines.tasks
                val token = com.google.firebase.messaging.FirebaseMessaging.getInstance().token.await()
                if (token.isNotBlank() && token != user.fcmToken) {
                    repository.updateFcmToken(token)
                    android.util.Log.d("UserViewModel", "✅ Deferred: FCM token updated")
                }
            } catch (e: Exception) {
                android.util.Log.w("UserViewModel", "Failed to update FCM token: ${e.message}")
            }
        }
    }

    /**
     * Full user reload - used for pull-to-refresh or manual refresh
     */
    fun loadCurrentUser() {
        loadCurrentUserLightweight()
    }

    fun setCurrentUser(user: User?) {
        _currentUser.value = user
    }

    /**
     * Set user online - called from lifecycle observer
     * Only executes if past Stage 2, otherwise ignored (will be handled by deferred task)
     */
    fun setOnline() {
        if (StartupManager.isAlreadyInitialized()) {
            try {
                presenceRepository.setUserOnline()
            } catch (e: Exception) {
                android.util.Log.e("UserViewModel", "Error setting online", e)
            }
        }
        // If not initialized yet, the deferred task will handle this
    }

    override fun onCleared() {
        super.onCleared()
        // Set user offline when ViewModel is cleared
        try {
            presenceRepository.setUserOffline()
        } catch (e: Exception) {
            android.util.Log.w("UserViewModel", "Failed to set user offline: ${e.message}")
        }
    }
}
