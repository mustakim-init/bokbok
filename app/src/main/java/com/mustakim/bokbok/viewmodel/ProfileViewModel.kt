package com.mustakim.bokbok.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.AuthRepository
import com.mustakim.bokbok.data.repository.NotificationRepository
import com.mustakim.bokbok.data.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val error: String? = null,
    val isUploadingImage: Boolean = false,
    val successMessage: String? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            kotlinx.coroutines.supervisorScope {
                val userId = userRepository.getCurrentUserId()
                if (userId != null) {
                    userRepository.getUserProfile(userId).fold(
                        onSuccess = { user ->
                            _uiState.update {
                                it.copy(
                                    user = user,
                                    isLoading = false
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    error = error.message ?: "Failed to load profile"
                                )
                            }
                        }
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "User not logged in"
                        )
                    }
                }
            }
        }
    }

    fun toggleEditMode() {
        _uiState.value = _uiState.value.copy(
            isEditing = !_uiState.value.isEditing,
            error = null
        )
    }

    fun updateProfile(
        displayName: String,
        bio: String,
        phoneNumber: String
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val userId = userRepository.getCurrentUserId()
            if (userId != null) {
                val updates = mapOf(
                    "displayName" to displayName,
                    "bio" to bio,
                    "phoneNumber" to phoneNumber
                )

                userRepository.updateUserProfile(userId, updates).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            isEditing = false,
                            successMessage = "Profile updated successfully"
                        )
                        loadUserProfile()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to update profile"
                        )
                    }
                )
            }
        }
    }

    fun uploadProfileImage(imageUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploadingImage = true,
                error = null
            )

            val userId = userRepository.getCurrentUserId()
            if (userId != null) {
                userRepository.uploadProfileImage(userId, imageUri).fold(
                    onSuccess = { imageUrl ->
                        _uiState.value = _uiState.value.copy(
                            isUploadingImage = false,
                            successMessage = "Profile picture updated"
                        )
                        loadUserProfile()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isUploadingImage = false,
                            error = error.message ?: "Failed to upload image"
                        )
                    }
                )
            }
        }
    }

    fun deleteProfileImage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val userId = userRepository.getCurrentUserId()
            if (userId != null) {
                userRepository.deleteProfileImage(userId).fold(
                    onSuccess = {
                        _uiState.value = _uiState.value.copy(
                            successMessage = "Profile picture removed"
                        )
                        loadUserProfile()
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to delete image"
                        )
                    }
                )
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun clearSuccessMessage() {
        _uiState.value = _uiState.value.copy(successMessage = null)
    }
}
