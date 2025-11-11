package com.mustakim.bokbok.data.model

data class FriendStatus(
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String = "",
    val status: UserStatus,
    val currentRoomId: String? = null,
    val currentRoomCategory: RoomCategory? = null
)

enum class UserStatus {
    ONLINE,      // Online but not in a room
    IN_ROOM,     // Currently in a voice room
    IDLE,        // Away/idle
    OFFLINE      // Offline
}
