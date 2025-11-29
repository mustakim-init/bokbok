package com.mustakim.bokbok.data.repository

import com.mustakim.bokbok.data.model.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class ChatRepository {
    // Mock data for UI development
    fun getMessages(friendId: String): Flow<List<Message>> = flow {
        val mockMessages = listOf(
            Message(UUID.randomUUID().toString(), "me", friendId, "Hey! How are you?", isRead = true),
            Message(UUID.randomUUID().toString(), friendId, "me", "I'm good, thanks! Just working on the new design.", isRead = true),
            Message(UUID.randomUUID().toString(), "me", friendId, "That sounds cool. Can I see it?", isRead = true),
            Message(UUID.randomUUID().toString(), friendId, "me", "Sure! Sending it over now.", isRead = true),
            Message(UUID.randomUUID().toString(), friendId, "me", "Check this out! 🎨", isRead = true),
            Message(UUID.randomUUID().toString(), "me", friendId, "Wow, that looks amazing! The colors are so vibrant.", isRead = true),
            Message(UUID.randomUUID().toString(), friendId, "me", "Thanks! I tried to use the new Material 3 Expressive guidelines.", isRead = true),
            Message(UUID.randomUUID().toString(), "me", friendId, "It really shows. The shapes are so organic.", isRead = true)
        ).reversed() // Newest first
        
        emit(mockMessages)
    }

    suspend fun sendMessage(senderId: String, receiverId: String, text: String) {
        delay(300) // Simulate network
        // In real app: Firestore.collection("chats").add(...)
    }
}
