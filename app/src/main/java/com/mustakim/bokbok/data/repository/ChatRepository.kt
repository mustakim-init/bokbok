package com.mustakim.bokbok.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import java.util.UUID

class ChatRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun getChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) "${userId1}_$userId2" else "${userId2}_$userId1"
    }

    fun getMessages(friendId: String): Flow<List<Message>> {
        val currentUserId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        val chatId = getChatId(currentUserId, friendId)

        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java)
            }
            .catch { emit(emptyList()) }
    }

    fun getLastMessage(friendId: String): Flow<Message?> {
        val currentUserId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(null)
        val chatId = getChatId(currentUserId, friendId)

        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java).firstOrNull()
            }
            .catch { emit(null) }
    }

    suspend fun sendMessage(senderId: String, receiverId: String, text: String, replyTo: Message? = null) {
        val chatId = getChatId(senderId, receiverId)
        val messageId = UUID.randomUUID().toString()
        
        val newMessage = Message(
            id = messageId,
            senderId = senderId,
            receiverId = receiverId,
            text = text,
            timestamp = Timestamp.now(),
            replyToId = replyTo?.id,
            replyToText = replyTo?.text,
            replyToSenderName = if (replyTo?.senderId == senderId) "You" else "Friend",
            status = MessageStatus.SENT,
            readBy = listOf(senderId)
        )

        val chatRef = firestore.collection("chats").document(chatId)
        
        // Use batch to update both message and chat document atomically
        firestore.runBatch { batch ->
            // Update chat document with last message and participants
            batch.set(
                chatRef,
                mapOf(
                    "participants" to listOf(senderId, receiverId),
                    "lastMessage" to mapOf(
                        "text" to text,
                        "senderId" to senderId,
                        "timestamp" to Timestamp.now(),
                        "type" to newMessage.type.name,
                        "isDeleted" to false
                    ),
                    "lastMessageTime" to Timestamp.now(),
                    "unreadCount_$receiverId" to com.google.firebase.firestore.FieldValue.increment(1)
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
            
            // Add message to messages subcollection
            batch.set(chatRef.collection("messages").document(messageId), newMessage)
        }.await()
    }

    suspend fun addReaction(messageId: String, emoji: String, userId: String, friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)
        
        val messageRef = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)

        // Direct update without transaction - Firestore handles the map merge
        messageRef.update("reactions.$userId", emoji).await()
    }

    suspend fun removeReaction(messageId: String, userId: String, friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)

        val messageRef = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)

        // Use FieldValue.delete() for map field deletion
        messageRef.update("reactions.$userId", com.google.firebase.firestore.FieldValue.delete()).await()
    }

    suspend fun deleteMessage(messageId: String, forEveryone: Boolean, userId: String, friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)

        val messageRef = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)

        try {
            if (forEveryone) {
                // Get message timestamp before deleting
                val messageSnapshot = messageRef.get().await()
                val message = messageSnapshot.toObject(Message::class.java)
                
                // Update the message
                messageRef.update(
                    mapOf(
                        "isDeletedForEveryone" to true,
                        "text" to "Message unsent"
                    )
                ).await()
                
                // Update chat's last message if this was the last message
                message?.let {
                    updateChatLastMessageIfNeeded(chatId, it.timestamp)
                }
            } else {
                // Use FieldValue.arrayUnion to append without reading
                messageRef.update(
                    "deletedBy", 
                    com.google.firebase.firestore.FieldValue.arrayUnion(userId)
                ).await()
                
                // Check if all participants deleted, then fully delete
                val snapshot = messageRef.get().await()
                val message = snapshot.toObject(Message::class.java)
                if (message != null) {
                    val chatDoc = firestore.collection("chats").document(chatId).get().await()
                    val participants = chatDoc.get("participants") as? List<*> ?: emptyList<String>()
                    
                    if (message.deletedBy.size >= participants.size) {
                        // All participants deleted, remove message completely
                        messageRef.delete().await()
                        
                        // Update last message in chat
                        updateChatLastMessageIfNeeded(chatId, message.timestamp)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error deleting message", e)
            throw e
        }
    }

    private suspend fun updateChatLastMessageIfNeeded(chatId: String, deletedMessageTime: Timestamp) {
        // Get the new last message after deletion
        val newLastMessage = firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?.toObject(Message::class.java)
        
        if (newLastMessage != null) {
            firestore.collection("chats").document(chatId)
                .update(
                    mapOf(
                        "lastMessage" to mapOf(
                            "text" to newLastMessage.text,
                            "senderId" to newLastMessage.senderId,
                            "timestamp" to newLastMessage.timestamp,
                            "type" to newLastMessage.type.name,
                            "isDeleted" to newLastMessage.isDeletedForEveryone
                        ),
                        "lastMessageTime" to newLastMessage.timestamp
                    )
                ).await()
        } else {
            // No messages left, clear last message
            firestore.collection("chats").document(chatId)
                .update(
                    mapOf(
                        "lastMessage" to null,
                        "lastMessageTime" to Timestamp.now()
                    )
                ).await()
        }
    }

    suspend fun markMessagesAsRead(friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)
        
        try {
            // Get all unread messages (sent by friend, not read by me)
            val unreadMessages = firestore.collection("chats")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("senderId", friendId)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Message::class.java) }
                .filter { !it.readBy.contains(currentUserId) }
            
            // Batch update to mark as read
            if (unreadMessages.isNotEmpty()) {
                firestore.runBatch { batch ->
                    unreadMessages.forEach { message ->
                        val messageRef = firestore.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(message.id)
                        
                        batch.update(
                            messageRef,
                            mapOf(
                                "readBy" to com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId),
                                "status" to MessageStatus.READ.name
                            )
                        )
                    }
                }.await()
            }
            
            // Reset unread count for current user
            firestore.collection("chats").document(chatId)
                .update("unreadCount_$currentUserId", 0)
                .await()
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error marking messages as read", e)
        }
    }


    // Group Chat Implementation
    fun getGroupMessages(groupId: String): Flow<List<Message>> {
        return firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(Message::class.java)
            }
            .catch { emit(emptyList()) }
    }

    fun getGroupMembers(groupId: String): Map<String, com.mustakim.bokbok.data.model.User> {
        // TODO: Fetch real members from Firestore
        return emptyMap()
    }

    suspend fun sendGroupMessage(senderId: String, groupId: String, text: String, replyTo: Message? = null) {
        val messageId = UUID.randomUUID().toString()
        var replyToSenderName = "Unknown"
        
        if (replyTo != null) {
             try {
                 val userDoc = firestore.collection("users").document(replyTo.senderId).get().await()
                 replyToSenderName = userDoc.getString("displayName") ?: "Unknown"
             } catch (e: Exception) {
                 // Ignore
             }
        }

        val newMessage = Message(
            id = messageId,
            senderId = senderId,
            receiverId = groupId,
            text = text,
            timestamp = Timestamp.now(),
            replyToId = replyTo?.id,
            replyToText = replyTo?.text,
            replyToSenderName = replyToSenderName,
            status = MessageStatus.SENT,
            readBy = listOf(senderId)
        )
        
        firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .document(messageId)
            .set(newMessage)
            .await()
    }

    suspend fun addGroupReaction(groupId: String, messageId: String, emoji: String, userId: String) {
        val messageRef = firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .document(messageId)

        // Direct update without transaction
        messageRef.update("reactions.$userId", emoji).await()
    }

    suspend fun removeGroupReaction(groupId: String, messageId: String, userId: String) {
        val messageRef = firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .document(messageId)

        // Use FieldValue.delete() for map field deletion
        messageRef.update("reactions.$userId", com.google.firebase.firestore.FieldValue.delete()).await()
    }

    suspend fun deleteGroupMessage(groupId: String, messageId: String, forEveryone: Boolean, userId: String) {
        val messageRef = firestore.collection("groups")
            .document(groupId)
            .collection("messages")
            .document(messageId)

        try {
            if (forEveryone) {
                messageRef.update(
                    mapOf(
                        "isDeletedForEveryone" to true,
                        "text" to "Message unsent"
                    )
                ).await()
            } else {
                // Use FieldValue.arrayUnion to append without reading
                messageRef.update(
                    "deletedBy",
                    com.google.firebase.firestore.FieldValue.arrayUnion(userId)
                ).await()
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatRepository", "Error deleting group message", e)
            throw e
        }
    }
}
