package com.mustakim.bokbok.data.model

import com.google.firebase.Timestamp

data class Message(
    val id: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val isRead: Boolean = false,
    val type: MessageType = MessageType.TEXT,
    val mediaUrl: String? = null
)

enum class MessageType {
    TEXT, IMAGE, AUDIO
}
