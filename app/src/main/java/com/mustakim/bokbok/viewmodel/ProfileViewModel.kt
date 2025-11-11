package com.mustakim.bokbok.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val user: User? = null,
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val error: String? = null,
    val isUploadingImage: Boolean = false,
    val successMessage: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        loadUserProfile()
    }

    fun loadUserProfile() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val userId = repository.getCurrentUserId()
            if (userId != null) {
                repository.getUserProfile(userId).fold(
                    onSuccess = { user ->
                        _uiState.value = _uiState.value.copy(
                            user = user,
                            isLoading = false
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load profile"
                        )
                    }
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "User not logged in"
                )
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

            val userId = repository.getCurrentUserId()
            if (userId != null) {
                val updates = mapOf(
                    "displayName" to displayName,
                    "bio" to bio,
                    "phoneNumber" to phoneNumber
                )

                repository.updateUserProfile(userId, updates).fold(
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

            val userId = repository.getCurrentUserId()
            if (userId != null) {
                repository.uploadProfileImage(userId, imageUri).fold(
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

            val userId = repository.getCurrentUserId()
            if (userId != null) {
                repository.deleteProfileImage(userId).fold(
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
