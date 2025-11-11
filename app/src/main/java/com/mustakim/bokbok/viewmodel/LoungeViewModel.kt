package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.RoomCategory
import com.mustakim.bokbok.data.model.VoiceRoom
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.utils.SampleDataHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import android.net.Uri

data class LoungeUiState(
    val friends: List<FriendStatus> = emptyList(),
    val myRooms: List<VoiceRoom> = emptyList(),
    val publicRooms: List<VoiceRoom> = emptyList(),
    val totalActiveRooms: Int = 0,
    val totalOnlineUsers: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,  // ✅ Add refresh state
    val isRefreshingPublicRooms: Boolean = false,  // ✅ Add this
    val error: String? = null
)

class LoungeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RoomRepository()

    private val _uiState = MutableStateFlow(
        LoungeUiState(
            // ✅ Load data immediately in the initial state
            friends = SampleDataHelper.getSampleFriends(),
            myRooms = SampleDataHelper.getSampleMyRooms(),
            publicRooms = SampleDataHelper.getSamplePublicRooms(),
            totalActiveRooms = 247,
            totalOnlineUsers = 1829,
            isLoading = false
        )
    )
    val uiState: StateFlow<LoungeUiState> = _uiState.asStateFlow()

    private val _roomImages = MutableStateFlow<Map<String, String>>(emptyMap())
    val roomImages: StateFlow<Map<String, String>> = _roomImages.asStateFlow()

    fun updateRoomImage(roomId: String, imageUrl: String) {
        _roomImages.value = _roomImages.value + (roomId to imageUrl)
    }


    // ✅ Pull-to-refresh function
    fun refreshAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }

            try {
                // Simulate network refresh
                delay(1500)  // Realistic refresh time

                // Reload all data
                val friends = SampleDataHelper.getSampleFriends()
                val myRooms = SampleDataHelper.getSampleMyRooms()
                val publicRooms = SampleDataHelper.getSamplePublicRooms()

                _uiState.update {
                    it.copy(
                        friends = friends,
                        myRooms = myRooms,
                        publicRooms = publicRooms,
                        totalActiveRooms = (200..300).random(),
                        totalOnlineUsers = (1500..2000).random(),
                        isRefreshing = false,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshing = false,
                        error = "Failed to refresh: ${e.message}"
                    )
                }
            }
        }
    }

    fun refreshPublicRooms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingPublicRooms = true) }

            try {
                // Simulate network call
                delay(800)

                val newRooms = SampleDataHelper.getSamplePublicRooms()

                _uiState.update {
                    it.copy(
                        publicRooms = newRooms,
                        totalActiveRooms = (200..300).random(),
                        totalOnlineUsers = (1500..2000).random(),
                        isRefreshingPublicRooms = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isRefreshingPublicRooms = false,
                        error = "Failed to refresh public rooms"
                    )
                }
            }
        }
    }

    fun createRoom(
        name: String,
        description: String,
        maxParticipants: Int,
        category: RoomCategory,
        isPublic: Boolean,
        imageUri: Uri?  // ✅ Add this parameter
    ) {
        viewModelScope.launch {
            try {
                // For now, just log or store locally
                // When you add backend, upload image here

                val imageUrl = if (imageUri != null) {
                    // TODO: Upload to ImgBB or your storage
                    // For now, use local URI
                    imageUri.toString()
                } else {
                    ""
                }

                // Create room with image
                delay(500)

                // Add to my rooms
                val newRoom = VoiceRoom(
                    id = "room_${System.currentTimeMillis()}",
                    name = name,
                    hostId = "me",
                    hostName = "You",
                    hostImageUrl = "",
                    imageUrl = imageUrl,  // ✅ Use uploaded image
                    description = description,
                    participants = listOf("me"),
                    maxParticipants = maxParticipants,
                    isPublic = isPublic,
                    category = category,
                    createdAt = System.currentTimeMillis()
                )

                _uiState.update {
                    it.copy(myRooms = it.myRooms + newRoom)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = "Failed to create room: ${e.message}")
                }
            }
        }
    }


    fun joinRoom(roomId: String) {
        viewModelScope.launch {
            delay(300)
            // Join room logic
        }
    }
}
