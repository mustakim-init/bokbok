package com.mustakim.bokbok.viewmodel

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.FriendWithUser
import com.mustakim.bokbok.data.model.RoomCategory
import com.mustakim.bokbok.data.model.UserStatus
import com.mustakim.bokbok.data.model.VoiceRoom
import com.mustakim.bokbok.data.repository.AuthRepository
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.data.repository.NotificationRepository
import com.mustakim.bokbok.data.repository.PresenceRepository
import com.mustakim.bokbok.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


data class LoungeUiState(
    val friends: List<FriendStatus> = emptyList(),
    val myRooms: List<VoiceRoom> = emptyList(),
    val publicRooms: List<VoiceRoom> = emptyList(),
    val lastDocument: com.google.firebase.firestore.DocumentSnapshot? = null,
    val isLastPage: Boolean = false,
    val isLoadingMore: Boolean = false,
    val totalActiveRooms: Int = 0,
    val totalOnlineUsers: Int = 0,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isRefreshingPublicRooms: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LoungeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val roomRepository: RoomRepository,
    private val presenceRepository: PresenceRepository,
    private val notificationRepository: NotificationRepository,
    private val friendsRepository: FriendsRepository,
    private val application: Application
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        LoungeUiState(
            friends = emptyList(),
            myRooms = emptyList(),
            publicRooms = emptyList(),
            totalActiveRooms = 0,
            totalOnlineUsers = 0,
            isLoading = false
        )
    )

    val uiState: StateFlow<LoungeUiState> = _uiState.asStateFlow()
    
    // Track if initial data has been loaded
    private var hasLoadedInitialData = false
    
    // Track if friends observer is started
    private var isFriendsObserverStarted = false
    
    // Track if we should show skeleton (only on first ever load)
    var shouldShowSkeleton by mutableStateOf(true)
        private set
    
    // Track if minimum skeleton time has elapsed
    var minSkeletonTimeElapsed by mutableStateOf(false)
        private set
    
    fun hideSkeleton() {
        android.util.Log.d("LoungeViewModel", "hideSkeleton() called, shouldShowSkeleton: $shouldShowSkeleton -> false")
        shouldShowSkeleton = false
    }
    
    fun markMinTimeElapsed() {
        android.util.Log.d("LoungeViewModel", "markMinTimeElapsed() called, minSkeletonTimeElapsed: $minSkeletonTimeElapsed -> true")
        minSkeletonTimeElapsed = true
    }

    init {
        // ✅ OPTIMIZED: Do NOT start firebase observers in init
        // They will be started lazily when loadInitialData() is called
        android.util.Log.d("LoungeViewModel", "✅ Created (deferred init): ${System.identityHashCode(this)}")
    }
    
    override fun onCleared() {
        super.onCleared()
        android.util.Log.d("LoungeViewModel", "❌ Destroyed: ${System.identityHashCode(this)}")
    }



    // Load public rooms from Firestore instead of dummy data
    private suspend fun fetchPublicRooms(): Boolean {
        return try {
            val result = roomRepository.getActiveRoomsSnapshot(null)
            result.fold(
                onSuccess = { (rooms, lastDoc) ->
                    val enriched = enrichWithOnlineCounts(rooms)
                    val totalOnline = enriched.sumOf { it.currentOnline }
                    _uiState.update {
                        it.copy(
                            publicRooms = enriched,
                            lastDocument = lastDoc,
                            isLastPage = rooms.size < 20,
                            totalActiveRooms = enriched.size,
                            totalOnlineUsers = totalOnline,
                            error = null
                        )
                    }
                    true
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = "Failed to load public rooms: ${e.message}") }
                    false
                }
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to load public rooms: ${e.message}") }
            false
        }
    }

    /**
     * Load my rooms from Firestore.
     */
    private suspend fun fetchMyRooms(): Boolean {
        return try {
            val result = roomRepository.getMyRooms()
            result.fold(
                onSuccess = { rooms ->
                    val enriched = enrichWithOnlineCounts(rooms)
                    _uiState.update {
                        it.copy(
                            myRooms = enriched,
                            error = null
                        )
                    }
                    true
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = "Failed to load my rooms: ${e.message}") }
                    false
                }
            )
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to load my rooms: ${e.message}") }
            false
        }
    }

    private suspend fun enrichWithOnlineCounts(rooms: List<VoiceRoom>): List<VoiceRoom> {
        if (rooms.isEmpty()) return emptyList()
        val allCounts = presenceRepository.getAllRoomOnlineCounts()
        return rooms.map { room ->
            room.copy(currentOnline = allCounts[room.id] ?: 0)
        }
    }

    // ✅ Pull-to-refresh function
    fun refreshAllData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, lastDocument = null, isLastPage = false, error = null) }
            
            kotlinx.coroutines.supervisorScope {
                val pubDef = async { fetchPublicRooms() }
                val myDef = async { fetchMyRooms() }
                pubDef.await()
                myDef.await()
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    /**
     * Load more public rooms for pagination.
     */
    fun loadMoreRooms() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isLastPage || state.lastDocument == null) return

        _uiState.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            val result = roomRepository.getActiveRoomsSnapshot(state.lastDocument)
            result.fold(
                onSuccess = { (newRooms, lastDoc) ->
                    val enriched = enrichWithOnlineCounts(newRooms)
                    _uiState.update {
                        it.copy(
                            publicRooms = it.publicRooms + enriched,
                            lastDocument = lastDoc,
                            isLastPage = newRooms.size < 20,
                            isLoadingMore = false
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoadingMore = false, error = "Failed to load more rooms: ${e.message}") }
                }
            )
        }
    }


    fun refreshPublicRooms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshingPublicRooms = true) }
            fetchPublicRooms()
            _uiState.update { it.copy(isRefreshingPublicRooms = false) }
        }
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
                    roomRepository.uploadRoomImage(application, imageUri) ?: ""
                } else {
                    ""
                }

                // 2) Create room in Firestore with the imageUrl
                val result = roomRepository.createRoom(
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
                        val roomResult = roomRepository.getRoom(roomId)
                        roomResult.fold(
                            onSuccess = { room ->
                                _uiState.update {
                                    it.copy(
                                        myRooms = it.myRooms + room,
                                        isLoading = false
                                    )
                                }
                                if (room.isPublic) {
                                    fetchPublicRooms()
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

    private fun FriendWithUser.toFriendStatus(
        currentRooms: List<VoiceRoom>
    ): FriendStatus {
        val effectiveRoomId = currentRoomId

        // Try to match this room to one we know about (myRooms + publicRooms)
        val room = effectiveRoomId?.let { id ->
            currentRooms.firstOrNull { it.id == id }
        }

        val status = when {
            effectiveRoomId != null -> UserStatus.IN_ROOM
            isOnline -> UserStatus.ONLINE
            else -> UserStatus.IDLE
        }

        return FriendStatus(
            userId = user.uid,
            username = user.username,
            displayName = if (user.displayName.isNotBlank()) user.displayName else user.username,
            profileImageUrl = user.profileImageUrl,
            status = status,
            currentRoomId = effectiveRoomId,
            currentRoomCategory = room?.category
        )
    }


    private fun observeFriends() {
        viewModelScope.launch {
            friendsRepository.observeFriends().collect { friendsWithUsers ->
                // Combine public + my rooms so we can resolve categories
                val currentRooms = uiState.value.publicRooms + uiState.value.myRooms

                val mapped = friendsWithUsers.map { it.toFriendStatus(currentRooms) }

                val totalOnline = mapped.count {
                    it.status == UserStatus.IN_ROOM || it.status == UserStatus.ONLINE
                }

                _uiState.update { state ->
                    state.copy(
                        friends = mapped,
                        totalOnlineUsers = totalOnline
                    )
                }
            }
        }
    }

    /**
     * Load initial data when screen becomes visible.
     * Called from LoungeScreen's LaunchedEffect.
     * Only runs once - subsequent navigations use cached data.
     */
    fun loadInitialData() {
        // Don't reload if we've already loaded
        if (hasLoadedInitialData) return
        
        hasLoadedInitialData = true
        
        // ✅ OPTIMIZED: Start friends observer lazily (not in init)
        if (!isFriendsObserverStarted) {
            isFriendsObserverStarted = true
            observeFriends()
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            kotlinx.coroutines.supervisorScope {
                val pubDef = async { fetchPublicRooms() }
                val myDef = async { fetchMyRooms() }
                pubDef.await()
                myDef.await()
            }
            _uiState.update { it.copy(isLoading = false) }
        }
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

            // Soft check before navigation (race condition exists, but handled atomically in VoiceRoomViewModel)
            val currentCount = presenceRepository.getOnlineCount(room.id)
            if (currentCount >= room.maxParticipants) {
                _uiState.update { it.copy(error = "Room is full") }
                return@launch
            }

            // Navigate to VoiceRoomScreen; actual atomic join happens there.
            // e.g. navController.navigate("voiceRoom/${room.id}")
        }
    }

    fun joinRoomPermanently(room: VoiceRoom) {
        viewModelScope.launch {
            _uiState.update { it.copy(error = null) }

            val result = roomRepository.joinRoom(room.id) // Firestore: add to members
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
            val result = roomRepository.leaveRoom(room.id)
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

            val result = roomRepository.deleteRoom(room.id)
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
