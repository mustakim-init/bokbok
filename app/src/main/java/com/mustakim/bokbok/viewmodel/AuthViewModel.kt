package com.mustakim.bokbok.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Move AuthEvent to top level (outside the AuthViewModel class)
sealed class AuthEvent {
    object NavigateToUsernameSetup : AuthEvent()
    object NavigateToPermissions : AuthEvent()
    object NavigateToLounge : AuthEvent()
    data class ShowError(val message: String) : AuthEvent()
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isNewGoogleUser: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AuthRepository(application.applicationContext)
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    // One-time events channel
    private val _authEvents = MutableStateFlow<AuthEvent?>(null)
    val authEvents: StateFlow<AuthEvent?> = _authEvents.asStateFlow()

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        _uiState.value = _uiState.value.copy(
            isLoggedIn = repository.isUserLoggedIn()
        )
    }

    // Clear one-time event after consumption
    fun clearAuthEvent() {
        _authEvents.value = null
    }

    // Check if device supports modern Credential Manager
    fun supportsModernAuth(): Boolean {
        return repository.supportsCredentialManager()
    }

    // Start legacy Google Sign-In (older devices)
    fun startLegacyGoogleSignIn(onIntentReady: (Intent) -> Unit) {
        val intent = repository.getGoogleSignInIntent()
        onIntentReady(intent)
    }

    // Handle legacy Google Sign-In result
    fun handleLegacyGoogleSignIn(data: Intent?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.handleLegacyGoogleSignInResult(data).fold(
                onSuccess = { (user, isNewUser) ->
                    if (isNewUser) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = false,
                            isNewGoogleUser = true
                        )
                        _authEvents.value = AuthEvent.NavigateToUsernameSetup
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            isNewGoogleUser = false
                        )
                        _authEvents.value = AuthEvent.NavigateToPermissions
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to sign in with Google"
                    )
                    _authEvents.value = AuthEvent.ShowError(error.message ?: "Failed to sign in with Google")
                }
            )
        }
    }

    // Modern Google Sign-In that determines new vs existing user
    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.signInWithGoogle(activity).fold(
                onSuccess = { (user, isNewUser) ->
                    if (isNewUser) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = false,
                            isNewGoogleUser = true
                        )
                        _authEvents.value = AuthEvent.NavigateToUsernameSetup
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            isNewGoogleUser = false
                        )
                        _authEvents.value = AuthEvent.NavigateToPermissions
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to sign in with Google"
                    )
                    _authEvents.value = AuthEvent.ShowError(error.message ?: "Failed to sign in with Google")
                }
            )
        }
    }

    // Complete account creation for new Google users
    fun completeGoogleSignUp(username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val currentUser = repository.getCurrentUser()
            if (currentUser != null) {
                repository.createGoogleUserProfile(currentUser, username).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isLoggedIn = true,
                            isNewGoogleUser = false,
                            successMessage = "Account created successfully!"
                        )
                        _authEvents.value = AuthEvent.NavigateToPermissions
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to create account"
                        )
                        _authEvents.value = AuthEvent.ShowError(error.message ?: "Failed to create account")
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "No user found. Please try signing in again."
                )
                _authEvents.value = AuthEvent.ShowError("No user found. Please try signing in again.")
            }
        }
    }

    fun checkUsernameAvailability(username: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            repository.isUsernameAvailable(username).fold(
                onSuccess = { isAvailable ->
                    onResult(isAvailable)
                },
                onFailure = {
                    onResult(false)
                }
            )
        }
    }

    // Keep for existing users who need to update username
    fun updateUsername(username: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val userId = repository.getCurrentUser()?.uid
            if (userId != null) {
                repository.updateUsername(userId, username).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            isNewGoogleUser = false,
                            successMessage = "Username set successfully!"
                        )
                        _authEvents.value = AuthEvent.NavigateToPermissions
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to set username"
                        )
                        _authEvents.value = AuthEvent.ShowError(error.message ?: "Failed to set username")
                    }
                )
            }
        }
    }

    fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.signUp(email, password, username, displayName).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        successMessage = "Account created successfully!"
                    )
                    _authEvents.value = AuthEvent.NavigateToPermissions
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create account"
                    )
                    _authEvents.value = AuthEvent.ShowError(error.message ?: "Failed to create account")
                }
            )
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.signIn(email, password).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        successMessage = "Welcome back!"
                    )
                    _authEvents.value = AuthEvent.NavigateToPermissions
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to sign in"
                    )
                    _authEvents.value = AuthEvent.ShowError(error.message ?: "Failed to sign in")
                }
            )
        }
    }

    fun signOut() {
        repository.signOut()
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false,
            successMessage = "Signed out successfully"
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}