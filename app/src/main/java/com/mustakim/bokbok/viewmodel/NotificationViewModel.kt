package com.mustakim.bokbok.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.model.FriendRequest
import com.mustakim.bokbok.data.model.Notification
import com.mustakim.bokbok.data.repository.FriendsRepository
import com.mustakim.bokbok.data.repository.NotificationRepository
import com.mustakim.bokbok.data.repository.UserRepository
import com.mustakim.bokbok.data.repository.RoomRepository
import com.mustakim.bokbok.state.JoinMode
import com.mustakim.bokbok.state.RoomStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class NotificationUiItem {
    abstract val id: String
    abstract val timestamp: Long

    data class Standard(val notification: Notification) : NotificationUiItem() {
        override val id = notification.id
        override val timestamp = notification.createdAt
    }

    data class Request(val request: FriendRequest) : NotificationUiItem() {
        override val id = request.friendship.id
        override val timestamp = request.friendship.createdAt.toDate().time
    }
}

enum class NotificationFilter {
    ALL, INVITES, REQUESTS
}

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val friendsRepository: FriendsRepository,
    private val userRepository: UserRepository,
    private val roomRepository: RoomRepository
) : ViewModel() {

    private val _filter = MutableStateFlow(NotificationFilter.ALL)
    val filter: StateFlow<NotificationFilter> = _filter

    // Observe current user ID to trigger data loading
    private val currentUserIdFlow = flowOf(userRepository.getCurrentUserId())

    // 1. Real Notifications
    @OptIn(ExperimentalCoroutinesApi::class)
    private val notificationsFlow = currentUserIdFlow.flatMapLatest { userId ->
        if (userId != null) {
            notificationRepository.observeNotifications(userId)
        } else {
            flowOf(emptyList())
        }
    }

    // 2. Real Friend Requests
    private val requestsFlow = friendsRepository.observeIncomingFriendRequests()

    // Combine into UI Items
    val uiItems: StateFlow<List<NotificationUiItem>> = combine(
        notificationsFlow,
        requestsFlow,
        _filter
    ) { notifications, requests, currentFilter ->

        val notifItems = notifications.map { NotificationUiItem.Standard(it) }
        val requestItems = requests.map { NotificationUiItem.Request(it) }

        val allItems = (notifItems + requestItems).sortedByDescending { it.timestamp }

        when (currentFilter) {
            NotificationFilter.ALL -> allItems
            NotificationFilter.INVITES -> allItems.filterIsInstance<NotificationUiItem.Standard>()
            NotificationFilter.REQUESTS -> allItems.filterIsInstance<NotificationUiItem.Request>()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Expose unread count for badges
    val unreadCount: StateFlow<Int> = combine(
        notificationsFlow,
        requestsFlow
    ) { notifications, requests ->
        val unreadNotifs = notifications.count { !it.isRead }
        val pendingRequests = requests.size
        unreadNotifs + pendingRequests
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )

    fun setFilter(newFilter: NotificationFilter) {
        _filter.value = newFilter
    }

    fun acceptFriendRequest(requestId: String) {
        viewModelScope.launch {
            friendsRepository.acceptFriendRequest(requestId)
        }
    }

    fun declineFriendRequest(requestId: String) {
        viewModelScope.launch {
            // declining a request is effectively removing the friendship entry
            friendsRepository.removeFriendship(requestId)
        }
    }

    fun deleteNotification(notificationId: String) {
        val userId = userRepository.getCurrentUserId() ?: return
        viewModelScope.launch {
            notificationRepository.deleteNotification(userId, notificationId)
        }
    }
 
    fun joinRoom(roomId: String, loungeViewModel: LoungeViewModel, onComplete: () -> Unit) {
        viewModelScope.launch {
            roomRepository.getRoom(roomId).onSuccess { room ->
                loungeViewModel.joinRoomSessionOnly(room)
                RoomStateManager.joinRoom(room, JoinMode.SESSION_ONLY)
                deleteNotification(roomId) // Use roomId as notificationId if applicable, or pass both
                onComplete()
            }.onFailure {
                // handle failure
            }
        }
    }
}