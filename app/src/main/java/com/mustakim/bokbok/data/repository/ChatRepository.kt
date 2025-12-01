package com.mustakim.bokbok.data.repository

import com.mustakim.bokbok.data.model.Message
import com.google.firebase.Timestamp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class ChatRepository {
    private val _messages = kotlinx.coroutines.flow.MutableStateFlow<List<Message>>(emptyList())
    
    init {
        // Initialize with dummy data
        val now = Timestamp.now()
        val yesterday = Timestamp(now.seconds - 86400, 0)
        val twoDaysAgo = Timestamp(now.seconds - 172800, 0)

        val initialMessages = listOf(
            Message(UUID.randomUUID().toString(), "me", "friend", "Hey! How are you?", timestamp = twoDaysAgo, isRead = true),
            Message(UUID.randomUUID().toString(), "friend", "me", "I'm good, thanks! Just working on the new design.", timestamp = twoDaysAgo, isRead = true),
            
            Message(UUID.randomUUID().toString(), "me", "friend", "That sounds cool. Can I see it?", timestamp = yesterday, isRead = true),
            Message(UUID.randomUUID().toString(), "friend", "me", "Sure! Sending it over now.", timestamp = yesterday, isRead = true),
            
            Message(UUID.randomUUID().toString(), "friend", "me", "Check this out! 🎨", timestamp = now, isRead = true, reactions = mapOf("me" to "❤️")),
            Message(UUID.randomUUID().toString(), "me", "friend", "Wow, that looks amazing! The colors are so vibrant.", timestamp = now, isRead = true),
            Message(UUID.randomUUID().toString(), "friend", "me", "Thanks! I tried to use the new Material 3 Expressive guidelines.", timestamp = now, isRead = true),
            Message(
                id = UUID.randomUUID().toString(), 
                senderId = "me", 
                receiverId = "friend", 
                text = "It really shows. The shapes are so organic.", 
                timestamp = now, 
                isRead = true,
                replyToText = "Thanks! I tried to use the new Material 3 Expressive guidelines.",
                replyToSenderName = "Friend"
            )
        ).sortedByDescending { it.timestamp } // Newest first

        _messages.value = initialMessages
    }

    fun getMessages(friendId: String): Flow<List<Message>> = _messages

    suspend fun sendMessage(senderId: String, receiverId: String, text: String, replyTo: Message? = null) {
        // Create message with SENDING status
        val newMessage = Message(
            id = UUID.randomUUID().toString(),
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            timestamp = Timestamp.now(),
            replyToId = replyTo?.id,
            replyToText = replyTo?.text,
            replyToSenderName = if (replyTo?.senderId == "me") "You" else "Friend",
            status = com.mustakim.bokbok.data.model.MessageStatus.SENDING
        )
        _messages.value = listOf(newMessage) + _messages.value
        
        // Simulate network delay and update to SENT
        delay(500)
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == newMessage.id }
        if (index != -1) {
            currentList[index] = currentList[index].copy(status = com.mustakim.bokbok.data.model.MessageStatus.SENT)
            _messages.value = currentList
        }
        
        // Simulate delivery after another delay
        delay(300)
        val currentList2 = _messages.value.toMutableList()
        val index2 = currentList2.indexOfFirst { it.id == newMessage.id }
        if (index2 != -1) {
            currentList2[index2] = currentList2[index2].copy(status = com.mustakim.bokbok.data.model.MessageStatus.DELIVERED)
            _messages.value = currentList2
        }
    }

    suspend fun addReaction(messageId: String, emoji: String, userId: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val msg = currentList[index]
            val newReactions = msg.reactions.toMutableMap().apply { put(userId, emoji) }
            currentList[index] = msg.copy(reactions = newReactions)
            _messages.value = currentList
        }
    }

    suspend fun removeReaction(messageId: String, userId: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val msg = currentList[index]
            val newReactions = msg.reactions.toMutableMap().apply { remove(userId) }
            currentList[index] = msg.copy(reactions = newReactions)
            _messages.value = currentList
        }
    }

    suspend fun deleteMessage(messageId: String, forEveryone: Boolean, userId: String) {
        val currentList = _messages.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == messageId }
        if (index != -1) {
            val msg = currentList[index]
            if (forEveryone) {
                currentList[index] = msg.copy(isDeletedForEveryone = true, text = "Message unsent")
            } else {
                val newDeletedBy = msg.deletedBy + userId
                currentList[index] = msg.copy(deletedBy = newDeletedBy)
            }
            _messages.value = currentList
        }
    }
}
