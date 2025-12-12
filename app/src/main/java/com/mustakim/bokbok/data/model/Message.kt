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
    val mediaUrl: String? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val replyToId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val isDeletedForEveryone: Boolean = false,
    val deletedBy: List<String> = emptyList(), // List of userIds who deleted this message for themselves
    val status: MessageStatus = MessageStatus.SENT, // For sent messages tracking
    val readBy: List<String> = emptyList(), // List of userIds who read the message
    val summonedUserIds: List<String> = emptyList() // List of userIds summoned in this message
)

enum class MessageType {
    TEXT, IMAGE, AUDIO
}

enum class MessageStatus {
    SENDING,    // Message is being sent
    SENT,       // Message sent to server
    DELIVERED,  // Message delivered to recipient
    READ        // Message read by recipient
}
