package com.mustakim.bokbok.data.model

import androidx.compose.runtime.Immutable
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId


@Immutable
data class Friendship(
    @DocumentId
    val id: String = "",
    val userId1: String = "",
    val userId2: String = "",
    val status: FriendshipStatus = FriendshipStatus.PENDING,
    val requestedBy: String = "",
    val createdAt: Timestamp = Timestamp.now(),
    val acceptedAt: Timestamp? = null
) {
    fun getOtherUserId(currentUserId: String): String {
        return if (userId1 == currentUserId) userId2 else userId1
    }
}

enum class FriendshipStatus {
    PENDING,
    ACCEPTED,
    BLOCKED
}

data class FriendWithUser(
    val friendship: Friendship,
    val user: User,
    val isOnline: Boolean = false,
    val currentRoomId: String? = null
)

data class FriendRequest(
    val friendship: Friendship,
    val sender: User
)
