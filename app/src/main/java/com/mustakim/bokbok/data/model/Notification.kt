package com.mustakim.bokbok.data.model
data class Notification(
    val id: String = "",
    val recipientId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val senderImageUrl: String = "",
    val type: NotificationType = NotificationType.UNKNOWN,
    val title: String = "",
    val body: String = "",
    val payload: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
enum class NotificationType {
    UNKNOWN,
    ROOM_INVITE,
    FRIEND_REQUEST
}