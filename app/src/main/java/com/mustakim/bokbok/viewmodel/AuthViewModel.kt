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

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        _uiState.value = _uiState.value.copy(
            isLoggedIn = repository.isUserLoggedIn()
        )
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
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        isNewGoogleUser = isNewUser,
                        successMessage = if (isNewUser) "Account created!" else "Welcome back!"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to sign in with Google"
                    )
                }
            )
        }
    }

    // Modern Google Sign-In (Credential Manager for Android 9+)
    // Pass Activity from UI!
    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repository.signInWithGoogle(activity).fold(
                onSuccess = { (user, isNewUser) ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        isNewGoogleUser = isNewUser,
                        successMessage = if (isNewUser) "Account created!" else "Welcome back!"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to sign in with Google"
                    )
                }
            )
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
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to set username"
                        )
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
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create account"
                    )
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
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to sign in"
                    )
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
