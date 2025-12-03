package com.mustakim.bokbok.data.repository

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mustakim.bokbok.data.local.BokBokDatabase
import com.mustakim.bokbok.data.local.entity.SyncStatus
import com.mustakim.bokbok.data.local.entity.toDomain
import com.mustakim.bokbok.data.local.entity.toEntity
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.MessageStatus
import com.mustakim.bokbok.data.sync.MessageSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Hybrid ChatRepository: Room as source of truth, Firestore for sync
 * 
 * Architecture:
 * - All reads come from Room (instant, offline-first)
 * - Writes go to Room first (instant UI update)
 * - Background sync to Firestore via WorkManager
 * - Firestore listeners update Room for incoming messages
 */
class HybridChatRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val database = BokBokDatabase.getInstance(context)
    private val messageDao = database.messageDao()

    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    init {
        // Start periodic sync
        MessageSyncWorker.schedulePeriodicSync(context)

        // Start Firestore listeners for incoming messages
        startFirestoreListeners()
    }

    /**
     * Get chat ID for a conversation
     */
    fun getChatId(userId1: String, userId2: String): String {
        return if (userId1 < userId2) {
            "${userId1}_$userId2"
        } else {
            "${userId2}_$userId1"
        }
    }

    /**
     * Get messages from Room (source of truth)
     * Returns instant results from local database
     */
    fun getMessages(friendId: String): Flow<List<Message>> {
        val currentUserId = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.emptyFlow()
        val chatId = getChatId(currentUserId, friendId)

        return messageDao.getMessagesByChatId(chatId, limit = 50)
            .map { entities ->
                entities.map { it.toDomain() }
            }
            .catch { e ->
                android.util.Log.e("HybridChatRepo", "Error loading messages", e)
                emit(emptyList())
            }
    }

    /**
     * Send message (hybrid approach)
     * 1. Save to Room immediately (instant UI)
     * 2. Trigger background sync to Firestore
     */
    suspend fun sendMessage(
        senderId: String,
        receiverId: String,
        text: String,
        replyTo: Message? = null
    ): String {
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
            status = MessageStatus.SENDING,
            readBy = listOf(senderId)
        )

        // 1. Save to Room immediately with PENDING status
        val messageEntity = newMessage.toEntity(chatId, SyncStatus.PENDING)
        messageDao.insert(messageEntity)

        android.util.Log.d("HybridChatRepo", "Message saved to Room: $messageId")

        // 2. Trigger immediate sync
        MessageSyncWorker.triggerImmediateSync(context)

        return messageId
    }

    /**
     * Mark messages as read (local first)
     */
    suspend fun markMessagesAsRead(friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)

        try {
            // 1. Update Room immediately
            messageDao.markAllAsRead(chatId, friendId)

            // 2. Update Firestore in background
            backgroundScope.launch {
                try {
                    val unreadMessages = firestore.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .whereEqualTo("senderId", friendId)
                        .whereIn(
                            "status",
                            listOf(MessageStatus.SENT.name, MessageStatus.DELIVERED.name)
                        )
                        .get()
                        .await()

                    if (!unreadMessages.isEmpty) {
                        firestore.runBatch { batch ->
                            unreadMessages.documents.forEach { doc ->
                                batch.update(
                                    doc.reference,
                                    mapOf(
                                        "readBy" to com.google.firebase.firestore.FieldValue.arrayUnion(
                                            currentUserId
                                        ),
                                        "status" to MessageStatus.READ.name
                                    )
                                )
                            }
                        }.await()
                    }

                    // Reset unread count
                    firestore.collection("chats").document(chatId)
                        .update("unreadCount_$currentUserId", 0)
                        .await()

                    android.util.Log.d("HybridChatRepo", "Marked messages as read in Firestore")
                } catch (e: Exception) {
                    android.util.Log.e("HybridChatRepo", "Error marking as read in Firestore", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridChatRepo", "Error marking messages as read", e)
        }
    }

    /**
     * Add reaction (local first)
     */
    suspend fun addReaction(messageId: String, emoji: String, userId: String, friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)

        try {
            // 1. Update Room
            val message = messageDao.getMessageById(messageId)
            if (message != null) {
                val domainMessage = message.toDomain()
                val newReactions = domainMessage.reactions.toMutableMap()
                newReactions[userId] = emoji

                // Simple JSON conversion
                val reactionsJson = if (newReactions.isEmpty()) "{}" else {
                    newReactions.entries.joinToString(",", "{", "}") {
                        "\"${it.key}\":\"${it.value}\""
                    }
                }

                messageDao.updateReactions(messageId, reactionsJson)

                // 2. Update Firestore in background
                backgroundScope.launch {
                    try {
                        firestore.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(messageId)
                            .update("reactions.$userId", emoji)
                            .await()
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "HybridChatRepo",
                            "Error adding reaction to Firestore",
                            e
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridChatRepo", "Error adding reaction", e)
        }
    }

    /**
     * Remove reaction (local first)
     */
    suspend fun removeReaction(messageId: String, userId: String, friendId: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)

        try {
            // 1. Update Room
            val message = messageDao.getMessageById(messageId)
            if (message != null) {
                val domainMessage = message.toDomain()
                val newReactions = domainMessage.reactions.toMutableMap()
                newReactions.remove(userId)

                // Simple JSON conversion
                val reactionsJson = if (newReactions.isEmpty()) "{}" else {
                    newReactions.entries.joinToString(",", "{", "}") {
                        "\"${it.key}\":\"${it.value}\""
                    }
                }

                messageDao.updateReactions(messageId, reactionsJson)

                // 2. Update Firestore in background
                backgroundScope.launch {
                    try {
                        firestore.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(messageId)
                            .update(
                                "reactions.$userId",
                                com.google.firebase.firestore.FieldValue.delete()
                            )
                            .await()
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "HybridChatRepo",
                            "Error removing reaction from Firestore",
                            e
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridChatRepo", "Error removing reaction", e)
        }
    }

    /**
     * Delete message (local first)
     */
    suspend fun deleteMessage(
        messageId: String,
        forEveryone: Boolean,
        userId: String,
        friendId: String
    ) {
        val currentUserId = auth.currentUser?.uid ?: return
        val chatId = getChatId(currentUserId, friendId)

        try {
            if (forEveryone) {
                // 1. Update Room
                messageDao.markAsDeletedForEveryone(messageId)

                // 2. Update Firestore in background
                backgroundScope.launch {
                    try {
                        firestore.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(messageId)
                            .update(
                                mapOf(
                                    "isDeletedForEveryone" to true,
                                    "text" to "Message unsent"
                                )
                            )
                            .await()
                    } catch (e: Exception) {
                        android.util.Log.e("HybridChatRepo", "Error deleting in Firestore", e)
                    }
                }
            } else {
                // Delete for me only
                messageDao.deleteById(messageId)

                // Update Firestore in background
                backgroundScope.launch {
                    try {
                        firestore.collection("chats")
                            .document(chatId)
                            .collection("messages")
                            .document(messageId)
                            .update(
                                "deletedBy",
                                com.google.firebase.firestore.FieldValue.arrayUnion(userId)
                            )
                            .await()
                    } catch (e: Exception) {
                        android.util.Log.e(
                            "HybridChatRepo",
                            "Error updating deletedBy in Firestore",
                            e
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridChatRepo", "Error deleting message", e)
        }
    }

    /**
     * Start Firestore listeners to sync incoming messages to Room
     */
    fun startFirestoreListeners() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Listen to all chats where user is a participant
        firestore.collection("chats")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("HybridChatRepo", "Error listening to chats", error)
                    return@addSnapshotListener
                }

                snapshot?.documents?.forEach { chatDoc ->
                    val chatId = chatDoc.id

                    // Listen to messages in each chat
                    firestore.collection("chats")
                        .document(chatId)
                        .collection("messages")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(50)
                        .addSnapshotListener { messagesSnapshot, messagesError ->
                            if (messagesError != null) {
                                android.util.Log.e(
                                    "HybridChatRepo",
                                    "Error listening to messages",
                                    messagesError
                                )
                                return@addSnapshotListener
                            }

                            messagesSnapshot?.toObjects(Message::class.java)
                                ?.forEach { message ->
                                    // Save to Room if not already synced
                                    backgroundScope.launch {
                                        try {
                                            val existing = messageDao.getMessageById(message.id)
                                            if (existing == null || existing.syncStatus == SyncStatus.PENDING) {
                                                messageDao.insert(
                                                    message.toEntity(
                                                        chatId,
                                                        SyncStatus.SYNCED
                                                    )
                                                )
                                                android.util.Log.d(
                                                    "HybridChatRepo",
                                                    "Synced incoming message: ${message.id}"
                                                )
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e(
                                                "HybridChatRepo",
                                                "Error saving incoming message",
                                                e
                                            )
                                        }
                                    }
                                }
                        }
                }
            }
    }
}
