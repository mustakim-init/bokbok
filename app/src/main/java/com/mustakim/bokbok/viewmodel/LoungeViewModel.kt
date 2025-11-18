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
import androidx.compose.runtime.Stable
import android.util.Base64
import com.mustakim.bokbok.data.api.ImgBBApi
import com.mustakim.bokbok.BuildConfig
import com.mustakim.bokbok.data.repository.PresenceRepository

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

@Stable
class LoungeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = RoomRepository()

    private val presenceRepository = PresenceRepository()


    // ImgBB
    private val imgbbApi = ImgBBApi.create()
    private val imgbbApiKey = BuildConfig.IMGBB_API_KEY

    private val _uiState = MutableStateFlow(
        LoungeUiState(
            friends = SampleDataHelper.getSampleFriends(),
            myRooms = emptyList(),
            publicRooms = emptyList(),      // start empty, will be filled from Firestore
            totalActiveRooms = 0,
            totalOnlineUsers = 0,
            isLoading = false
        )
    )

    val uiState: StateFlow<LoungeUiState> = _uiState.asStateFlow()


    private suspend fun uploadRoomImage(imageUri: Uri): String? {
        return try {
            val context = getApplication<Application>()
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: return null

            val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)

            val response = imgbbApi.uploadImage(
                apiKey = imgbbApiKey,
                base64Image = base64
            )

            if (response.success && response.data != null) {
                response.data.url // the direct image URL
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    // Load public rooms from Firestore instead of dummy data
    private fun loadPublicRoomsFromFirestore() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    isRefreshingPublicRooms = true,
                    error = null
                )
            }

            val result = repository.getActiveRooms()
            result.fold(
                onSuccess = { rooms ->
                    _uiState.update {
                        it.copy(
                            publicRooms = rooms,
                            totalActiveRooms = rooms.size,
                            // keep totalOnlineUsers as-is for now (still dummy based on friends)
                            isLoading = false,
                            isRefreshingPublicRooms = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshingPublicRooms = false,
                            error = "Failed to load rooms: ${e.message}"
                        )
                    }
                }
            )
        }
    }

    /**
     * Load my rooms from Firestore.
     */
    private fun loadMyRoomsFromFirestore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val result = repository.getMyRooms()
            result.fold(
                onSuccess = { rooms ->
                    _uiState.update {
                        it.copy(
                            myRooms = rooms,
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load my rooms: ${e.message}"
                        )
                    }
                }
            )
        }
    }



    // ✅ Pull-to-refresh function
    fun refreshAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }

            try {
                // Simulate refresh for friends + myRooms (still dummy)
                delay(1500)

                val friends = SampleDataHelper.getSampleFriends()

                _uiState.update {
                    it.copy(
                        friends = friends,
                        totalOnlineUsers = friends.size,
                        isRefreshing = false
                    )
                }

                // Load real rooms from Firestore
                loadMyRoomsFromFirestore()
                loadPublicRoomsFromFirestore()
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
        // Just trigger Firestore reload; flags are handled inside
        loadPublicRoomsFromFirestore()
    }

    fun createRoom(
        name: String,
        description: String,
        maxParticipants: Int,
        category: RoomCategory,
        isPublic: Boolean,
        imageUri: Uri?
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                // 1) Upload image if provided
                val uploadedImageUrl = if (imageUri != null) {
                    uploadRoomImage(imageUri) ?: ""
                } else {
                    ""
                }

                // 2) Create room in Firestore with the imageUrl
                val result = repository.createRoom(
                    name = name,
                    description = description,
                    maxParticipants = maxParticipants,
                    category = category,
                    isPublic = isPublic,
                    imageUrl = uploadedImageUrl
                )

                result.fold(
                    onSuccess = { roomId ->
                        // 3) Load the created room and update state
                        val roomResult = repository.getRoom(roomId)
                        roomResult.fold(
                            onSuccess = { room ->
                                _uiState.update {
                                    it.copy(
                                        myRooms = it.myRooms + room,
                                        isLoading = false
                                    )
                                }
                                if (room.isPublic) {
                                    loadPublicRoomsFromFirestore()
                                }
                            },
                            onFailure = { e ->
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        error = "Room created but failed to load: ${e.message}"
                                    )
                                }
                            }
                        )
                    },
                    onFailure = { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = "Failed to create room: ${e.message}"
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Failed to create room: ${e.message}"
                    )
                }
            }
        }
    }

    init {
        // Replace dummy public rooms with real ones as soon as possible
        loadPublicRoomsFromFirestore()
        loadMyRoomsFromFirestore()
    }

    /**
     * User is already a permanent member (My Rooms).
     * When they tap a My Room card to enter the call session,
     * we only need to mark them as "currently in this room".
     */
    fun enterRoomFromMyRooms() {
        viewModelScope.launch {
            // Just clear errors; navigation + presence are handled in the room screen
            _uiState.update { it.copy(error = null) }
        }
    }



    fun joinRoomSessionOnly(room: VoiceRoom) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }

            val canJoin = presenceRepository.canJoin(room.id, room.maxParticipants)
            if (!canJoin) {
                _uiState.update { it.copy(error = "Room is full") }
                return@launch
            }

            // Do NOT call presenceRepository.joinCall() here.
            // Just navigate to VoiceRoomScreen; presence is joined in startCallEngine().
            // e.g. navController.navigate("voiceRoom/${room.id}")
        }
    }

    fun joinRoomPermanently(room: VoiceRoom) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }

            val result = repository.joinRoom(room.id) // Firestore: add to members
            result.fold(
                onSuccess = {
                    // Add to My Rooms locally
                    _uiState.update { state ->
                        if (state.myRooms.any { it.id == room.id }) state
                        else state.copy(myRooms = state.myRooms + room)
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = "Failed to join room: ${e.message}") }
                }
            )
        }
    }

    fun leaveRoomPermanently(room: VoiceRoom) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }

            // Only remove from permanent members; call-session leave is done in VoiceRoomViewModel
            val result = repository.leaveRoom(room.id)
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(myRooms = state.myRooms.filter { it.id != room.id })
                    }
                    refreshPublicRooms()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = "Failed to leave room: ${e.message}") }
                }
            )
        }
    }

    fun deleteRoomAsHost(room: VoiceRoom) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null, isLoading = true) }

            val result = repository.deleteRoom(room.id)
            result.fold(
                onSuccess = {
                    _uiState.update { state ->
                        state.copy(
                            myRooms = state.myRooms.filter { it.id != room.id },
                            publicRooms = state.publicRooms.filter { it.id != room.id },
                            isLoading = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to delete room: ${e.message}"
                        )
                    }
                }
            )
        }
    }
}
