package com.mustakim.bokbok.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Stable
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.ValueEventListener
import com.mustakim.bokbok.data.model.Notification
import com.mustakim.bokbok.data.model.NotificationType
import com.mustakim.bokbok.data.model.User
import com.mustakim.bokbok.data.model.VoiceRoom
import com.mustakim.bokbok.data.model.VoiceRoomParticipant
import com.mustakim.bokbok.data.repository.FCMRepository
import com.mustakim.bokbok.data.repository.NotificationRepository
import com.mustakim.bokbok.data.repository.PresenceRepository
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.data.webrtc.CallController
import com.mustakim.bokbok.data.webrtc.VoiceService
import com.mustakim.bokbok.state.ConnectionStateManager
import com.mustakim.bokbok.state.RoomStateManager
import com.mustakim.bokbok.state.SpeakingStateManager
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


data class VoiceRoomUiState(
    val room: VoiceRoom? = null,
    val participants: List<VoiceRoomParticipant> = emptyList(),
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isA2dpModeOn: Boolean = true,
    val isHighQuality: Boolean = true,
    val isMinimized: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val micVolume: Float = 1f,
    val outputVolume: Float = 1f,
    val participantVolumes: Map<String, Float> = emptyMap(),
    val wasKicked: Boolean = false,
    val members: List<User> = emptyList(), // Permanent members (from VoiceRoom.participants)
    val uploadedCoverUrl: String? = null // Temporary holder for new uploads
)

@Stable
class VoiceRoomViewModel(application: Application) : AndroidViewModel(application) {

    private val roomRepository = RoomRepository()
    private val userRepository = UserRepository(getApplication<Application>().applicationContext)

    val currentUserId: String?
        get() = userRepository.getCurrentUserId()

    private val friendsRepository = com.mustakim.bokbok.data.repository.FriendsRepository(userRepository)
    private val notificationRepository = NotificationRepository()
    private val fcmRepository = FCMRepository()

    val friends = friendsRepository.observeFriends()
        .stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val REQUIRED_PERMISSIONS = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private val preferencesManager = com.mustakim.bokbok.data.local.PreferencesManager(application.applicationContext)

    private val presenceRepository = PresenceRepository()
    private var presenceListener: ValueEventListener? = null
    private var joinEventsListener: ChildEventListener? = null

    // [NEW] Track if server has confirmed our presence at least once
    private var hasConfirmedJoin = false

    private var currentActiveRoomId: String? = null

    private val _uiState = MutableStateFlow(VoiceRoomUiState())
    val uiState: StateFlow<VoiceRoomUiState> = _uiState.asStateFlow()

    // Raw participants become "from presence" instead of "from observeUsersInRoom"
    private val _rawParticipants = MutableStateFlow<List<VoiceRoomParticipant>>(emptyList())


    // New Search State
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    private val _searchResults = MutableStateFlow<List<User>>(emptyList())
    val searchResults = _searchResults.asStateFlow()
    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    fun searchUsers(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val result = userRepository.searchUsers(query)
            _searchResults.value = result.getOrDefault(emptyList())
            _isSearching.value = false
        }
    }


    fun inviteUsers(userIds: List<String>) {
        viewModelScope.launch {
            val room = _uiState.value.room ?: return@launch
            val currentUser = userRepository.getCurrentUserId()?.let { userRepository.getUserProfile(it).getOrNull() } ?: return@launch
            // Fetch user profiles for the given IDs
            val users = userIds.mapNotNull { id ->
                userRepository.getUserProfile(id).getOrNull()
            }
            users.forEach { user ->
                // 1. Save to Firestore (for in-app history)
                val notification = Notification(
                    recipientId = user.uid,
                    senderId = currentUser.uid,
                    senderName = currentUser.displayName.ifBlank { currentUser.username },
                    senderImageUrl = currentUser.profileImageUrl,
                    type = NotificationType.ROOM_INVITE,
                    payload = mapOf("roomId" to room.id),
                    title = "Room Invite",
                    body = "${currentUser.displayName} invited you to join ${room.name}",
                    createdAt = System.currentTimeMillis()
                )

                val notifResult = notificationRepository.sendNotification(notification)

                var notificationDocId = ""
                notifResult.onSuccess { id ->
                    notificationDocId = id
                }
                notifResult.onFailure { error ->
                    android.util.Log.e("VoiceRoomViewModel", "Failed to save notification to Firestore for ${user.uid}", error)
                }

                // 2. Send Push Notification (FCM V1) if token exists
                user.fcmToken?.let { token ->
                    val fcmResult = fcmRepository.sendNotification(
                        toToken = token,
                        title = "Room Invite",
                        body = "${currentUser.displayName} invited you to join ${room.name}",
                        data = mapOf(
                            "roomId" to room.id,
                            "notificationDocId" to notificationDocId
                        )
                    )
                    fcmResult.onFailure { error ->
                        android.util.Log.e("VoiceRoomViewModel", "Failed to send FCM to ${user.uid}", error)
                    }
                } ?: run {
                    android.util.Log.w("VoiceRoomViewModel", "User ${user.username} has no FCM token, skipping push notification")
                }
            }
            // Show success message via UI State or Event
            _uiState.update { it.copy(error = null) } // Clear error
        }
    }

    fun addMembers(userIds: List<String>) {
        viewModelScope.launch {
            val roomId = _uiState.value.room?.id ?: return@launch
            roomRepository.addUsersToRoom(roomId, userIds).onSuccess {
                loadRoom(roomId) // Reload to update members list
                loadRoomMembers()
                _uiState.update { it.copy(error = null) } // Clear any previous error
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Failed to add members: ${e.message}") }
            }
        }
    }

    fun addFriend(userId: String) {
        viewModelScope.launch {
            friendsRepository.sendFriendRequest(userId).fold(
                onSuccess = {
                   // Optional: Show success message via a transient state or event
                   // For now, we rely on the UI not showing error
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = "Failed to send friend request: ${e.message}") }
                }
            )
        }
    }

    fun isFriend(userId: String): Boolean {
        return friends.value.any { it.user.uid == userId }
    }

    fun isMember(userId: String): Boolean {
        return _uiState.value.members.any { it.uid == userId }
    }

    fun uploadRoomImage(uri: android.net.Uri, context: android.content.Context) {
        viewModelScope.launch {
            _isSearching.value = true // Reuse loading state or add specific one
            val url = roomRepository.uploadRoomImage(context, uri)
            if (url != null) {
                // Determine logic: Do we auto-update the room? Or just return the URL?
                // The prompt implies "preview" or "update". Let's assume we update the room directly if it exists,
                // or we could expose it via a state flow. 
                // However, the RoomSettingsSheet seems to hold local state until "Save" is clicked.
                // But the user said "ask me if I want to upload... image preview".
                // Let's expose the uploaded URL via a SharedFlow or State so the UI can grab it.
                // For simplicity in this architecture, let's treat it as a state update that the UI observes,
                // OR better, we can just return it? No, VM functions shouldn't return values to UI directly.
                // We'll update a temporary state `_uploadedCoverUrl`.
                _uiState.update { it.copy(uploadedCoverUrl = url) } 
            } else {
                 _uiState.update { it.copy(error = "Failed to upload image") }
            }
             _isSearching.value = false
        }
    }

    // Clear uploaded url after consumed
    fun consumeUploadedCoverUrl() {
        _uiState.update { it.copy(uploadedCoverUrl = null) }
    }
    /**
     * Load room metadata and then drive participants from RTDB presence.
     * A user is "in this call" if they have an entry under /presence/{roomId} in Realtime Database.
     */
    fun loadRoom(roomId: String) {
        if (_uiState.value.room?.id == roomId) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            // [NEW] Reset confirmation flag
            hasConfirmedJoin = false

            val roomResult = roomRepository.getRoom(roomId)
            roomResult.fold(
                onSuccess = { room ->
                    _uiState.update {
                        it.copy(
                            room = room,
                            isLoading = false,
                            error = null
                        )
                    }
                    // 1. Optimistically add SELF to the list immediately
                    // (KEEP THIS BLOCK - It is safe now with the new flag logic)
                    val selfId = userRepository.getCurrentUserId()
                    if (selfId != null) {
                        val selfProfile = userRepository.getUserProfile(selfId).getOrNull()
                        if (selfProfile != null) {
                            val selfParticipant = VoiceRoomParticipant(
                                id = selfProfile.uid,
                                name = selfProfile.displayName.ifBlank { selfProfile.username },
                                avatarUrl = selfProfile.profileImageUrl,
                                isHost = selfProfile.uid == room.hostId,
                                isMuted = false,
                                isSpeaking = false
                            )
                            _rawParticipants.value = listOf(selfParticipant)
                        }
                    }

                    // Now drive participants purely from RTDB presence
                    startPresenceListener(room)
                    loadRoomMembers()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Failed to load room: ${e.message ?: "unknown error"}"
                        )
                    }
                }
            )
        }
    }

    fun setMicVolume(volume: Float) {
        _uiState.update { it.copy(micVolume = volume) }
        // ✅ GUARD
        if (currentActiveRoomId != null) {
            CallController.setMicVolume(volume.toDouble())
        }
        saveAudioSettings()
    }
    fun setOutputVolume(volume: Float) {
        _uiState.update { it.copy(outputVolume = volume) }
        // For output volume, we control the System Voice Call stream
        val audioManager = getApplication<Application>().getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
        audioManager?.let { am ->
            val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_VOICE_CALL)
            val target = (max * volume).toInt()
            am.setStreamVolume(android.media.AudioManager.STREAM_VOICE_CALL, target, 0)
        }
        saveAudioSettings()
    }

    fun setParticipantVolume(userId: String, volume: Float) {
        _uiState.update { currentState ->
            val newVolumes = currentState.participantVolumes.toMutableMap().apply {
                put(userId, volume)
            }
            currentState.copy(participantVolumes = newVolumes)
        }

        // ✅ GUARD
        if (currentActiveRoomId != null) {
            CallController.setRemoteVolume(userId, volume.toDouble())
        }
    }

    fun kickParticipant(userId: String) {
        viewModelScope.launch {
            val room = _uiState.value.room ?: return@launch
            // Verify we are host
            val selfId = userRepository.getCurrentUserId()
            if (room.hostId != selfId) return@launch
            presenceRepository.kickUser(room.id, userId).onFailure {
                _uiState.update { s -> s.copy(error = "Failed to kick user: ${it.message}") }
            }
        }
    }

    fun updateRoomSettings(updates: Map<String, Any>) {
        viewModelScope.launch {
            val currentRoom = _uiState.value.room ?: return@launch
            val roomId = currentRoom.id

            roomRepository.updateRoom(roomId, updates).onSuccess {
                // 🎤 CHANGED: Update local state manually without reloading from network
                val updatedRoom = currentRoom.copy(
                    name = updates["name"] as? String ?: currentRoom.name,
                    description = updates["description"] as? String ?: currentRoom.description,
                    maxParticipants = (updates["maxParticipants"] as? Int) ?: currentRoom.maxParticipants,
                    imageUrl = updates["imageUrl"] as? String ?: currentRoom.imageUrl,
                    isPublic = updates["isPublic"] as? Boolean ?: currentRoom.isPublic,
                    allowJoinNotifications = updates["allowJoinNotifications"] as? Boolean ?: currentRoom.allowJoinNotifications
                )

                _uiState.update { it.copy(room = updatedRoom) }

            }.onFailure { e ->
                _uiState.update { it.copy(error = "Failed to update settings: ${e.message}") }
            }
        }
    }

    fun removeMember(userId: String) {
        viewModelScope.launch {
            val roomId = _uiState.value.room?.id ?: return@launch
            roomRepository.removeUserFromRoom(roomId, userId).onSuccess {
                loadRoom(roomId) // Reload room to update participants list
                loadRoomMembers() // Reload member profiles
            }.onFailure { e ->
                _uiState.update { it.copy(error = "Failed to remove member: ${e.message}") }
            }
        }
    }

    private fun loadRoomMembers() {
        viewModelScope.launch {
            val room = _uiState.value.room ?: return@launch
            val memberIds = room.participants

            if (memberIds.isEmpty()) {
                _uiState.update { it.copy(members = emptyList()) }
                return@launch
            }

            // ✅ CHANGED: Use batch fetch instead of N async calls
            // This is safer and much more efficient
            val profiles = userRepository.getUserProfiles(memberIds)

            _uiState.update { it.copy(members = profiles) }
        }
    }

    private fun startPresenceListener(room: VoiceRoom) {
        // ... (existing cleanup code) ...
        presenceListener?.let {
            presenceRepository.removePresenceListener(room.id, it)
        }

        val selfId = userRepository.getCurrentUserId()

        presenceListener = presenceRepository.observeRoomPresence(
            roomId = room.id,
            onChange = { idsInRoom ->
                viewModelScope.launch {
                    // Compute new IDs vs previous raw list
                    val previousIds = _rawParticipants.value.map { it.id }.toSet()
                    val currentIds = idsInRoom

                    // [NEW] Update confirmation flag
                    if (selfId != null && selfId in currentIds) {
                        hasConfirmedJoin = true
                    }

                    if (selfId != null && selfId !in currentIds && previousIds.contains(selfId)) {
                        // ... (Self removal/kick logic remains same) ...
                        // We were in the room, but now we're not.
                        if (hasConfirmedJoin) {
                            val wasAlone = previousIds.size == 1 && previousIds.contains(selfId)
                            if (wasAlone) {
                                android.util.Log.w("VoiceRoomViewModel", "Detected self removal (Alone) - Connection lost/Timeout")
                                _uiState.update { it.copy(error = "Call ended due to connection loss", wasKicked = false) }
                            } else {
                                android.util.Log.w("VoiceRoomViewModel", "Detected self removal - Assuming Kick")
                                _uiState.update { it.copy(error = "You have been removed from the room", wasKicked = true) }
                            }
                            leaveRoom()
                            viewModelScope.launch {
                                kotlinx.coroutines.delay(2000)
                                _uiState.update { it.copy(error = null) }
                            }
                            return@launch
                        }
                    }

                    // [REMOVED] Logic to calculate newIds/removedIds for CallController
                    // The Service now handles this!

                    // Fetch profiles for all current IDs using BATCH fetch
                    val profiles = userRepository.getUserProfiles(idsInRoom.toList())

                    val participants = profiles.mapNotNull { user ->
                        if (user == null) return@mapNotNull null
                        val isHost = user.uid == room.hostId

                        val name: String =
                            user.displayName.takeIf { it.isNotBlank() }
                                ?: user.username.takeIf { it.isNotBlank() }
                                ?: if (isHost) room.hostName else user.uid

                        val avatar = user.profileImageUrl
                            .ifBlank { if (isHost) room.hostImageUrl else "" }

                        VoiceRoomParticipant(
                            id = user.uid,
                            name = name,
                            avatarUrl = avatar,
                            isHost = isHost,
                            isMuted = false,
                            isSpeaking = false
                        )
                    }

                    _rawParticipants.value = participants

                    // [REMOVED] CallController.scheduleDisconnect(removedIds)
                    // [REMOVED] CallController.connectToParticipants(newIds)
                }
            },
            onError = { e ->
                _uiState.update { state ->
                    state.copy(error = "Failed to observe presence: ${e.message}")
                }
            }
        )
        // ✅ Start observing join events
        joinEventsListener?.let { presenceRepository.removeJoinEventsListener(room.id, it) }

        // 🎤 CHANGED: Pass current time to filter out old events
        val startTime = System.currentTimeMillis()

        joinEventsListener = presenceRepository.observeJoinEvents(room.id, startTime) { userId, userName ->
            val selfId = userRepository.getCurrentUserId()
            // Only show notification if not self and setting is enabled
            if (userId != selfId && room.allowJoinNotifications) {
                showJoinNotification(userName, room.name)
            }
        }
    }

    /**
     * Start the WebRTC engine for this room.
     * This is called from the composable when we enter VoiceRoomScreen.
     */
    fun startCallEngine(roomId: String) {
        if (currentActiveRoomId == roomId) return

        viewModelScope.launch {
            if (_uiState.value.wasKicked && _uiState.value.room?.id == roomId) {
                android.util.Log.w("VoiceRoomViewModel", "Blocked rejoin attempt - user was kicked from this room")
                return@launch
            }

            val selfId = userRepository.getCurrentUserId() ?: return@launch
            val room = _uiState.value.room ?: return@launch
            // 🛑 FIX: Prevent double presence.
            // If we are seemingly in another room (according to global state), force leave it first.
            val currentGlobalRoom = RoomStateManager.currentRoom.value
            if (currentGlobalRoom != null && currentGlobalRoom.id != roomId) {
                android.util.Log.w("VoiceRoomViewModel", "Detected lingering session in ${currentGlobalRoom.id}, force leaving before joining $roomId")
                // We use NonCancellable to ensure this cleanup happens
                withContext(NonCancellable) {
                    presenceRepository.leaveCall(currentGlobalRoom.id)
                }
                RoomStateManager.leaveRoom()
            }
            // Enforce maxParticipants on RTDB presence for all joins using atomic transaction
            val result = presenceRepository.tryJoinRoom(roomId, room.maxParticipants)

            result.fold(
                onSuccess = { joined ->
                    if (!joined) {
                        _uiState.update { it.copy(error = "Room is full") }
                        return@launch
                    }

                    // ✅ FIX: Sync global state only on success
                    RoomStateManager.joinRoom(room)
                    // Successfully joined, start the call
                    CallController.startCall(
                        context = getApplication(),
                        roomId = roomId,
                        selfId = selfId
                    )
                    currentActiveRoomId = roomId

                    // ✅ NEW: Sync audio settings now that the service is running
                    syncAudioSettingsToService()

                    // ✅ Broadcast join event
                    val selfProfile = userRepository.getUserProfile(selfId).getOrNull()
                    val userName = selfProfile?.displayName?.ifBlank { selfProfile.username } ?: "User"
                    presenceRepository.broadcastJoinEvent(roomId, selfId, userName)
                },
                onFailure = { e ->
                    _uiState.update { it.copy(error = "Failed to join: ${e.message}") }

                    // [NEW] Remove optimistic self on failure
                    val selfId = userRepository.getCurrentUserId()
                    if (selfId != null) {
                        _rawParticipants.value = _rawParticipants.value.filter { it.id != selfId }
                    }
                }
            )
        }
    }

    private fun syncAudioSettingsToService() {
        val context = getApplication<Application>().applicationContext
        val state = _uiState.value

        // Apply all settings to the running service
        VoiceService.setSpeaker(context, state.isSpeakerOn)
        VoiceService.setA2dpMode(context, state.isA2dpModeOn)
        VoiceService.setQualityMode(context, state.isHighQuality)
        CallController.setMicVolume(state.micVolume.toDouble())
        CallController.setMuted(state.isMuted)
    }

    fun hasRequiredCallPermissions(): Boolean {
        val context = getApplication<Application>().applicationContext
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun getRequiredPermissions(): Array<String> {
        return REQUIRED_PERMISSIONS
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(error = "Microphone permission is required for voice calls")
        }
    }



    /**
     * Stop the WebRTC engine.
     * This is called when the room screen leaves composition.
     */
    fun stopCallEngine() {
        CallController.endCall()
        currentActiveRoomId = null
    }


    fun toggleMic() {
        _uiState.update { current ->
            val newMuted = !current.isMuted

            // Update global minimized bar state
            RoomStateManager.setMuted(newMuted)

            // ✅ GUARD
            if (currentActiveRoomId != null) {
                CallController.setMuted(newMuted)
            }

            current.copy(isMuted = newMuted)
        }
    }

    fun setMutedFromGlobal(muted: Boolean) {
        _uiState.update { it.copy(isMuted = muted) }
        // ✅ GUARD
        if (currentActiveRoomId != null) {
            CallController.setMuted(muted)
        }
    }


    // in VoiceRoomViewModel
    fun toggleSpeaker() {
        val appContext = getApplication<Application>().applicationContext

        _uiState.update { current ->
            val newSpeakerOn = !current.isSpeakerOn

            // ✅ GUARD: Only send to service if call is active
            if (currentActiveRoomId != null) {
                VoiceService.setSpeaker(appContext, newSpeakerOn)
            }

            current.copy(isSpeakerOn = newSpeakerOn)
        }
        saveAudioSettings()
    }

    fun toggleA2dpMode() {
        val appContext = getApplication<Application>().applicationContext
        _uiState.update { current ->
            val newMode = !current.isA2dpModeOn
            // ✅ GUARD
            if (currentActiveRoomId != null) {
                VoiceService.setA2dpMode(appContext, newMode)
            }
            current.copy(isA2dpModeOn = newMode)
        }
        saveAudioSettings()
    }

    fun toggleQualityMode() {
        val appContext = getApplication<Application>().applicationContext
        _uiState.update { current ->
            val newMode = !current.isHighQuality
            // ✅ GUARD
            if (currentActiveRoomId != null) {
                VoiceService.setQualityMode(appContext, newMode)
            }
            current.copy(isHighQuality = newMode)
        }
        saveAudioSettings()
    }



    init {
        // 1. Launch Audio Settings Collector in its own coroutine
        viewModelScope.launch {
            preferencesManager.audioSettings.collect { settings ->
                val isSpeakerOn = settings["isSpeakerOn"] as Boolean
                val isA2dpModeOn = settings["isA2dpModeOn"] as Boolean
                val isHighQuality = settings["isHighQuality"] as Boolean
                val micVolume = settings["micVolume"] as Float
                val outputVolume = settings["outputVolume"] as Float

                _uiState.update {
                    it.copy(
                        isSpeakerOn = isSpeakerOn,
                        isA2dpModeOn = isA2dpModeOn,
                        isHighQuality = isHighQuality,
                        micVolume = micVolume,
                        outputVolume = outputVolume
                    )
                }

                // 🛑 REMOVED: Immediate service calls to prevent ghost notification
                // The service will be synced when the call actually starts.
            }
        }

        // 2. Launch Participant Updates in a SEPARATE coroutine
        viewModelScope.launch {
            combine(
                _rawParticipants,
                SpeakingStateManager.speakingIds,
                ConnectionStateManager.disconnectedIds
            ) { raw, speakingIds, disconnectedIds ->
                // Do NOT hide participants purely because WebRTC marked them disconnected.
                // Presence is the source of truth for "in the room".
                raw.map { p ->
                    p.copy(
                        isSpeaking = speakingIds.contains(p.id)
                        // Optionally you could add an `isWeakConnection` flag here based on disconnectedIds.contains(p.id)
                    )
                }
            }.collect { visibleParticipants ->
                _uiState.update { current ->
                    current.copy(participants = visibleParticipants)
                }
            }
        }
    }

    fun leaveRoom() {
        viewModelScope.launch {
            val roomId = _uiState.value.room?.id
            // Clear kicked flag on manual leave
            _uiState.update {
                it.copy(
                    wasKicked = false,
                    room = null,
                    participants = emptyList(),
                    members = emptyList()
                )
            }

            // 🎤 CHANGED: Reset raw participants to force StateFlow emission on next join
            _rawParticipants.value = emptyList()
            // Stop presence listener
            presenceListener?.let { listener ->
                if (roomId != null) {
                    presenceRepository.removePresenceListener(roomId, listener)
                }
            }
            presenceListener = null
            // Clean up join events listener
            joinEventsListener?.let { listener ->
                if (roomId != null) {
                    presenceRepository.removeJoinEventsListener(roomId, listener)
                }
            }
            joinEventsListener = null
            if (roomId != null) {
                withContext(NonCancellable) {
                    presenceRepository.leaveCall(roomId)
                }
            }
            RoomStateManager.leaveRoom()
            stopCallEngine()
        }
    }

    private fun saveAudioSettings() {
        val state = _uiState.value
        viewModelScope.launch {
            preferencesManager.saveAudioSettings(
                isSpeakerOn = state.isSpeakerOn,
                isA2dpModeOn = state.isA2dpModeOn,
                isHighQuality = state.isHighQuality,
                micVolume = state.micVolume,
                outputVolume = state.outputVolume
            )
        }
    }

    private fun showJoinNotification(userName: String, roomName: String) {
        val context = getApplication<Application>().applicationContext
        val notificationManager = context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        // 🎤 CHANGED: New Channel ID to reset settings
        val channelId = "room_activity_v2"
        // Create notification channel (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId,
                "Room Activity",
                android.app.NotificationManager.IMPORTANCE_HIGH // Critical for Heads-Up
            ).apply {
                description = "Notifications for room join/leave events"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }
        // Build notification
        val notificationId = System.currentTimeMillis().toInt()
        val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_notification_overlay) // Ensure this icon exists or use R.drawable.ic_stat_name
            .setContentTitle(roomName)
            .setContentText("$userName Joined The Room")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH) // Critical for pre-Oreo
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL) // Sound + Vibrate
            .setVibrate(longArrayOf(0, 250, 250, 250)) // Explicit vibration
            .setAutoCancel(true)
            .apply {
                // Android 12+ supports timeoutAfter
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setTimeoutAfter(5000) // Dismiss after 5s
                }
            }
            .build()
        notificationManager.notify(notificationId, notification)
        // For Android < 12, manually cancel after 5 seconds
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(5000)
                notificationManager.cancel(notificationId)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        val roomId = _uiState.value.room?.id
        // Remove presence listener
        presenceListener?.let { listener ->
            if (roomId != null) {
                presenceRepository.removePresenceListener(roomId, listener)
            }
            presenceListener = null
        }

        // ✅ Clean up join events listener
        joinEventsListener?.let { listener ->
            if (roomId != null) {
                presenceRepository.removeJoinEventsListener(roomId, listener)
            }
            joinEventsListener = null
        }

        // Also make sure we leave presence and end the call if something
        // destroyed the ViewModel without a clean leave.
        if (roomId != null) {
            // Use a SupervisorJob for cleanup to ensure it completes safely
            val cleanupScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
            cleanupScope.launch {
                try {
                    presenceRepository.leaveCall(roomId)
                } catch (e: Exception) {
                    android.util.Log.e("VoiceRoomViewModel", "Error leaving room", e)
                }
            }
        }
        stopCallEngine()
    }
}