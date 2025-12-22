package com.mustakim.bokbok.viewmodel

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlinx.coroutines.withContext
import com.google.firebase.auth.FirebaseUser
import com.mustakim.bokbok.data.repository.PresenceRepository


sealed class AuthEvent {
    object NavigateToUsernameSetup : AuthEvent()
    object NavigateToPermissions : AuthEvent()

    data class ShowError(val message: String) : AuthEvent()
}

data class AuthUiState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isNewGoogleUser: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val presenceRepository: PresenceRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

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

    fun clearAuthEvent() {
        _authEvents.value = null
    }



    fun handleLegacyGoogleSignIn(
        data: Intent?,
        onUserLoaded: (User?, FirebaseUser) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            repository.handleLegacyGoogleSignInResult(data).fold(
                onSuccess = { triple ->
                    val (firebaseUser, existingUser, isNewUser) = triple

                    // Optionally cache user in UserViewModel from the caller
                    onUserLoaded(existingUser, firebaseUser)

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
                        presenceRepository.setUserOnline()
                        _authEvents.value = AuthEvent.NavigateToPermissions
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to sign in with Google"
                    )
                    _authEvents.value = AuthEvent.ShowError(
                        error.message ?: "Failed to sign in with Google"
                    )
                }
            )
        }
    }




    fun signInWithGoogleWithFallback(
        activity: Activity,
        onLegacyIntentReady: (Intent) -> Unit,
        onUserLoaded: (User?, FirebaseUser) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val supportsModern = repository.supportsCredentialManager()

            if (supportsModern) {
                // First try Credential Manager
                val modernResult = repository.signInWithGoogle(activity)
                modernResult.fold(
                    onSuccess = { triple ->
                        val (firebaseUser, existingUser, isNewUser) = triple

                        // Give UI/ViewModel the loaded user
                        onUserLoaded(existingUser, firebaseUser)

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
                            presenceRepository.setUserOnline()
                            _authEvents.value = AuthEvent.NavigateToPermissions
                        }
                    },
                    onFailure = { error ->
                        // Modern path failed → fallback to legacy intent
                        try {
                            val intent = repository.getGoogleSignInIntent()
                            // keep isLoading true; legacy result will finish it
                            onLegacyIntentReady(intent)
                        } catch (_: Exception) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to sign in with Google"
                            )
                            _authEvents.value = AuthEvent.ShowError(
                                error.message ?: "Failed to sign in with Google"
                            )
                        }
                    }
                )
            } else {
                // Older Android → go straight to legacy intent
                try {
                    val intent = repository.getGoogleSignInIntent()
                    onLegacyIntentReady(intent)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to sign in with Google"
                    )
                    _authEvents.value = AuthEvent.ShowError(
                        e.message ?: "Failed to sign in with Google"
                    )
                }
            }
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

    // REMOVED: updateUsername method (no longer needed)

    fun signUp(
        email: String,
        password: String,
        username: String,
        displayName: String
    ) {
        viewModelScope.launch {
            // Start loading
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // 1) Check username availability in Firestore
            val isAvailable = repository.isUsernameAvailable(username).getOrElse { false }

            if (!isAvailable) {
                // Username is taken → show error and stop
                val message = "That username is already taken. Please choose another one."
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = message
                )
                _authEvents.value = AuthEvent.ShowError(message)
                return@launch
            }

            // 2) Username is free → proceed with auth + profile creation
            repository.signUp(email, password, username, displayName).fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isLoggedIn = true,
                        successMessage = "Account created successfully!"
                    )
                    presenceRepository.setUserOnline()
                    _authEvents.value = AuthEvent.NavigateToPermissions
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to create account"
                    )
                    _authEvents.value = AuthEvent.ShowError(
                        error.message ?: "Failed to create account"
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
                    presenceRepository.setUserOnline()
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
        // Mark user offline in RTDB using injected repository
        presenceRepository.setUserOffline()
        repository.signOut()
        _uiState.value = _uiState.value.copy(
            isLoggedIn = false,
            successMessage = "Signed out successfully"
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}