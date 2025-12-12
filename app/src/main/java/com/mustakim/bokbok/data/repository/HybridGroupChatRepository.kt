package com.mustakim.bokbok.data.repository

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mustakim.bokbok.data.local.BokBokDatabase
import com.mustakim.bokbok.data.local.entity.GroupEntity
import com.mustakim.bokbok.data.local.entity.GroupMemberEntity
import com.mustakim.bokbok.data.local.entity.GroupMessageEntity
import com.mustakim.bokbok.data.local.entity.SyncStatus
import com.mustakim.bokbok.data.local.entity.toUser
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.MessageStatus
import com.mustakim.bokbok.data.model.MessageType
import com.mustakim.bokbok.data.model.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Hybrid GroupChatRepository: Room as source of truth, Firestore for sync
 * 
 * Architecture:
 * - All reads come from Room (instant, offline-first)
 * - Writes go to Room first (instant UI update)
 * - Background sync to Firestore
 * - Firestore listeners update Room for incoming data
 */
class HybridGroupChatRepository(private val context: Context) {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val database = BokBokDatabase.getInstance(context)
    private val groupDao = database.groupDao()
    
    private val backgroundScope = CoroutineScope(Dispatchers.IO)
    
    val currentUserId: String
        get() = auth.currentUser?.uid ?: ""

    init {
        // Start Firestore listeners for real-time updates
        startFirestoreListeners()
    }

    // ============= GROUP INFO =============

    /**
     * Get group info from Room (instant)
     */
    fun observeGroup(groupId: String): Flow<GroupEntity?> {
        // Also trigger background fetch to ensure data is fresh
        backgroundScope.launch { fetchGroupFromFirestore(groupId) }
        return groupDao.observeGroup(groupId)
    }

    /**
     * Get group members from Room (instant)
     */
    fun observeGroupMembers(groupId: String): Flow<List<User>> {
        return groupDao.observeMembers(groupId)
            .map { members -> members.map { it.toUser() } }
            .catch { 
                android.util.Log.e("HybridGroupRepo", "Error observing members", it)
                emit(emptyList()) 
            }
    }

    /**
     * Fetch group from Firestore and save to Room
     */
    private suspend fun fetchGroupFromFirestore(groupId: String) {
        try {
            val snapshot = firestore.collection("groups").document(groupId).get().await()
            if (snapshot.exists()) {
                val groupEntity = GroupEntity(
                    id = snapshot.id,
                    name = snapshot.getString("name") ?: "Group",
                    imageUrl = snapshot.getString("imageUrl") ?: "",
                    participants = toStringListJson((snapshot.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()),
                    createdBy = snapshot.getString("createdBy") ?: "",
                    createdAt = snapshot.getTimestamp("createdAt")?.toDate()?.time ?: 0L,
                    lastMessageTime = snapshot.getTimestamp("lastMessageTime")?.toDate()?.time ?: 0L,
                    lastMessageText = (snapshot.get("lastMessage") as? Map<*, *>)?.get("text") as? String
                )
                groupDao.insertGroup(groupEntity)
                
                // Fetch member profiles
                val participantIds = (snapshot.get("participants") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                fetchMembersFromFirestore(groupId, participantIds)
                
                android.util.Log.d("HybridGroupRepo", "Group synced: $groupId")
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error fetching group", e)
        }
    }

    /**
     * Fetch member profiles from Firestore and save to Room
     */
    private suspend fun fetchMembersFromFirestore(groupId: String, participantIds: List<String>) {
        if (participantIds.isEmpty()) return
        
        try {
            // Firestore 'in' queries limited to 30 items
            participantIds.chunked(30).forEach { chunk ->
                val snapshot = firestore.collection("users")
                    .whereIn("uid", chunk)
                    .get()
                    .await()
                
                val members = snapshot.documents.map { doc ->
                    GroupMemberEntity(
                        groupId = groupId,
                        userId = doc.getString("uid") ?: doc.id,
                        displayName = doc.getString("displayName") ?: "User",
                        profileImageUrl = doc.getString("profileImageUrl") ?: "",
                        username = doc.getString("username") ?: ""
                    )
                }
                
                groupDao.insertMembers(members)
            }
            
            // Remove members who are no longer in the group
            groupDao.removeOldMembers(groupId, participantIds)
            
            android.util.Log.d("HybridGroupRepo", "Members synced for group: $groupId")
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error fetching members", e)
        }
    }

    // ============= MESSAGES =============

    /**
     * Get messages from Room (instant, source of truth)
     */
    fun getMessages(groupId: String): Flow<List<Message>> {
        return groupDao.getMessagesByGroupId(groupId, 50)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { 
                android.util.Log.e("HybridGroupRepo", "Error loading messages", it)
                emit(emptyList()) 
            }
    }

    /**
     * Send message (Room first for instant UI)
     * Supports summon feature with @everyone for groups
     */
    suspend fun sendMessage(groupId: String, text: String, replyTo: Message? = null): SendMessageResult {
        val messageId = UUID.randomUUID().toString()
        
        // Get group members for summon parsing
        val members = groupDao.getMembersByGroupId(groupId)
        val memberMap = members.associate { it.displayName.lowercase() to it.userId }
        val allMemberIds = members.map { it.userId }
        
        // Parse summons from text
        val summonResult = com.mustakim.bokbok.utils.SummonParser.parse(
            text = text,
            availableUsers = memberMap,
            allMemberIds = allMemberIds,
            senderId = currentUserId
        )
        
        // Check rate limit if there are summons
        if (summonResult.hasSummons) {
            val rateLimitResult = com.mustakim.bokbok.utils.SummonRateLimiter.checkAndRecord(
                chatId = groupId,
                isEveryone = summonResult.hasEveryone
            )
            if (!rateLimitResult.allowed) {
                return SendMessageResult.RateLimited(rateLimitResult.reason, rateLimitResult.cooldownSeconds)
            }
        }
        
        var replyToSenderName = "Unknown"
        if (replyTo != null) {
            val member = members.find { it.userId == replyTo.senderId }
            replyToSenderName = member?.displayName ?: "Unknown"
        }
        
        // Get sender's display name for notification
        val senderDisplayName = members.find { it.userId == currentUserId }?.displayName ?: "Someone"
        
        val messageEntity = GroupMessageEntity(
            id = messageId,
            groupId = groupId,
            senderId = currentUserId,
            text = text,
            timestamp = System.currentTimeMillis(),
            replyToId = replyTo?.id,
            replyToText = replyTo?.text,
            replyToSenderName = replyToSenderName,
            status = MessageStatus.SENDING.name,
            readBy = "[\"$currentUserId\"]",
            syncStatus = SyncStatus.PENDING,
            summonedUserIds = summonResult.summonedUserIds.joinToString(",")
        )
        
        // 1. Save to Room immediately
        groupDao.insertMessage(messageEntity)
        
        // 2. Update last message
        groupDao.updateLastMessage(groupId, text, System.currentTimeMillis())
        
        android.util.Log.d("HybridGroupRepo", "Message saved to Room: $messageId")
        
        // 3. Sync to Firestore in background
        backgroundScope.launch {
            syncMessageToFirestore(groupId, messageEntity)
        }
        
        // 4. Send summon notifications via FCM (background)
        if (summonResult.hasSummons) {
            backgroundScope.launch {
                sendSummonNotifications(
                    summonedUserIds = summonResult.summonedUserIds,
                    senderDisplayName = senderDisplayName,
                    groupId = groupId
                )
            }
        }
        
        return SendMessageResult.Success(messageId)
    }
    
    /**
     * Send FCM notifications to summoned users
     */
    private suspend fun sendSummonNotifications(
        summonedUserIds: List<String>,
        senderDisplayName: String,
        groupId: String
    ) {
        val fcmRepository = FCMRepository()
        
        for (userId in summonedUserIds) {
            try {
                // Fetch user's FCM token
                val userDoc = firestore.collection("users").document(userId).get().await()
                val fcmToken = userDoc.getString("fcmToken") ?: continue
                
                fcmRepository.sendNotification(
                    toToken = fcmToken,
                    title = "BokBok - Summon",
                    body = "$senderDisplayName summoned you",
                    data = mapOf(
                        "type" to "summon",
                        "chatId" to "",
                        "groupId" to groupId,
                        "isGroup" to "true"
                    )
                )
                android.util.Log.d("HybridGroupRepo", "Summon notification sent to $userId")
            } catch (e: Exception) {
                android.util.Log.e("HybridGroupRepo", "Failed to send summon notification to $userId", e)
            }
        }
    }

    private suspend fun syncMessageToFirestore(groupId: String, entity: GroupMessageEntity) {
        try {
            val messageData = mapOf(
                "id" to entity.id,
                "senderId" to entity.senderId,
                "receiverId" to groupId,
                "text" to entity.text,
                "timestamp" to Timestamp(entity.timestamp / 1000, ((entity.timestamp % 1000) * 1000000).toInt()),
                "replyToId" to entity.replyToId,
                "replyToText" to entity.replyToText,
                "replyToSenderName" to entity.replyToSenderName,
                "status" to MessageStatus.SENT.name,
                "readBy" to listOf(currentUserId),
                "type" to MessageType.TEXT.name
            )
            
            // 1. Add message to subcollection
            firestore.collection("groups")
                .document(groupId)
                .collection("messages")
                .document(entity.id)
                .set(messageData)
                .await()
            
            // 2. Update group document's lastMessage field for chat list display
            val lastMessageUpdate = mapOf(
                "lastMessage" to mapOf(
                    "text" to entity.text,
                    "senderId" to entity.senderId,
                    "type" to "TEXT",
                    "isDeleted" to false
                ),
                "lastMessageTime" to Timestamp(entity.timestamp / 1000, ((entity.timestamp % 1000) * 1000000).toInt())
            )
            
            firestore.collection("groups")
                .document(groupId)
                .update(lastMessageUpdate)
                .await()
            
            // Update sync status
            groupDao.updateSyncStatus(entity.id, SyncStatus.SYNCED)
            
            android.util.Log.d("HybridGroupRepo", "Message synced to Firestore: ${entity.id}")
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error syncing message", e)
            groupDao.updateSyncStatus(entity.id, SyncStatus.FAILED)
        }
    }

    /**
     * Add reaction (Room first)
     */
    suspend fun addReaction(groupId: String, messageId: String, emoji: String) {
        try {
            val message = groupDao.getMessageById(messageId) ?: return
            val reactions = parseReactionsJson(message.reactions).toMutableMap()
            reactions[currentUserId] = emoji
            
            groupDao.updateReactions(messageId, toReactionsJson(reactions))
            
            // Sync to Firestore
            backgroundScope.launch {
                try {
                    firestore.collection("groups")
                        .document(groupId)
                        .collection("messages")
                        .document(messageId)
                        .update("reactions.$currentUserId", emoji)
                        .await()
                } catch (e: Exception) {
                    android.util.Log.e("HybridGroupRepo", "Error syncing reaction", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error adding reaction", e)
        }
    }

    /**
     * Remove reaction (Room first)
     */
    suspend fun removeReaction(groupId: String, messageId: String) {
        try {
            val message = groupDao.getMessageById(messageId) ?: return
            val reactions = parseReactionsJson(message.reactions).toMutableMap()
            reactions.remove(currentUserId)
            
            groupDao.updateReactions(messageId, toReactionsJson(reactions))
            
            // Sync to Firestore
            backgroundScope.launch {
                try {
                    firestore.collection("groups")
                        .document(groupId)
                        .collection("messages")
                        .document(messageId)
                        .update("reactions.$currentUserId", com.google.firebase.firestore.FieldValue.delete())
                        .await()
                } catch (e: Exception) {
                    android.util.Log.e("HybridGroupRepo", "Error removing reaction", e)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error removing reaction", e)
        }
    }

    /**
     * Delete message (Room first)
     */
    suspend fun deleteMessage(groupId: String, messageId: String, forEveryone: Boolean) {
        try {
            if (forEveryone) {
                groupDao.markAsDeletedForEveryone(messageId)
                
                backgroundScope.launch {
                    try {
                        firestore.collection("groups")
                            .document(groupId)
                            .collection("messages")
                            .document(messageId)
                            .update(mapOf(
                                "isDeletedForEveryone" to true,
                                "text" to "Message unsent"
                            ))
                            .await()
                    } catch (e: Exception) {
                        android.util.Log.e("HybridGroupRepo", "Error deleting in Firestore", e)
                    }
                }
            } else {
                groupDao.deleteById(messageId)
                
                backgroundScope.launch {
                    try {
                        firestore.collection("groups")
                            .document(groupId)
                            .collection("messages")
                            .document(messageId)
                            .update("deletedBy", com.google.firebase.firestore.FieldValue.arrayUnion(currentUserId))
                            .await()
                    } catch (e: Exception) {
                        android.util.Log.e("HybridGroupRepo", "Error updating deletedBy", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error deleting message", e)
        }
    }

    /**
     * Clear chat history (Local only)
     */
    suspend fun clearChatHistory(groupId: String) {
        try {
            groupDao.deleteAllByGroupId(groupId)
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error clearing chat history", e)
        }
    }

    /**
     * Search messages locally
     */
    suspend fun searchMessages(groupId: String, query: String): List<Message> {
        return try {
            groupDao.searchMessages(groupId, query).map { it.toDomain() }
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error searching messages", e)
            emptyList()
        }
    }

    /**
     * Leave group
     */
    suspend fun leaveGroup(groupId: String) {
        try {
            // Remove from Firestore participants
            firestore.collection("groups").document(groupId)
                .update("participants", com.google.firebase.firestore.FieldValue.arrayRemove(currentUserId))
                .await()
            
            // Delete local data
            groupDao.deleteAllByGroupId(groupId)
            // Ideally also delete the group entity itself, but keeping it simple for now or maybe user wants to see it?
            // Usually leaving means it disappears.
            // groupDao.deleteGroup(groupId) // Assuming such method exists or we make one.
            // For now, clearing messages is good. The group will stop syncing because listener checks participants.
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error leaving group", e)
        }
    }

    /**
     * Add a member to the group
     */
    suspend fun addMember(groupId: String, userId: String) {
        try {
            // Add to Firestore participants
            firestore.collection("groups").document(groupId)
                .update("participants", com.google.firebase.firestore.FieldValue.arrayUnion(userId))
                .await()
            
            // Fetch the new member's profile and add to local Room for instant UI
            val userDoc = firestore.collection("users").document(userId).get().await()
            if (userDoc.exists()) {
                val newMember = GroupMemberEntity(
                    groupId = groupId,
                    userId = userId,
                    displayName = userDoc.getString("displayName") ?: "User",
                    profileImageUrl = userDoc.getString("profileImageUrl") ?: "",
                    username = userDoc.getString("username") ?: ""
                )
                groupDao.insertMembers(listOf(newMember))
            }
            
            android.util.Log.d("HybridGroupRepo", "Added member $userId to group $groupId")
        } catch (e: Exception) {
            android.util.Log.e("HybridGroupRepo", "Error adding member", e)
        }
    }

    // ============= FIRESTORE LISTENERS =============

    private fun startFirestoreListeners() {
        if (currentUserId.isEmpty()) return
        
        // Listen to groups where user is a participant
        firestore.collection("groups")
            .whereArrayContains("participants", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("HybridGroupRepo", "Error listening to groups", error)
                    return@addSnapshotListener
                }
                
                snapshot?.documents?.forEach { groupDoc ->
                    val groupId = groupDoc.id
                    
                    // Sync group info
                    backgroundScope.launch {
                        fetchGroupFromFirestore(groupId)
                    }
                    
                    // Listen to messages
                    firestore.collection("groups")
                        .document(groupId)
                        .collection("messages")
                        .orderBy("timestamp", Query.Direction.DESCENDING)
                        .limit(50)
                        .addSnapshotListener { messagesSnapshot, messagesError ->
                            if (messagesError != null) {
                                android.util.Log.e("HybridGroupRepo", "Error listening to messages", messagesError)
                                return@addSnapshotListener
                            }
                            
                            messagesSnapshot?.documents?.forEach { doc ->
                                backgroundScope.launch {
                                    try {
                                        val existing = groupDao.getMessageById(doc.id)
                                        if (existing == null || existing.syncStatus == SyncStatus.PENDING) {
                                            val messageEntity = GroupMessageEntity(
                                                id = doc.id,
                                                groupId = groupId,
                                                senderId = doc.getString("senderId") ?: "",
                                                text = doc.getString("text") ?: "",
                                                timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                                                replyToId = doc.getString("replyToId"),
                                                replyToText = doc.getString("replyToText"),
                                                replyToSenderName = doc.getString("replyToSenderName"),
                                                isDeletedForEveryone = doc.getBoolean("isDeletedForEveryone") ?: false,
                                                deletedBy = toStringListJson((doc.get("deletedBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()),
                                                status = doc.getString("status") ?: MessageStatus.SENT.name,
                                                readBy = toStringListJson((doc.get("readBy") as? List<*>)?.filterIsInstance<String>() ?: emptyList()),
                                                reactions = toReactionsJson((doc.get("reactions") as? Map<*, *>)?.entries?.associate { 
                                                    it.key.toString() to it.value.toString() 
                                                } ?: emptyMap()),
                                                syncStatus = SyncStatus.SYNCED
                                            )
                                            groupDao.insertMessage(messageEntity)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("HybridGroupRepo", "Error syncing message", e)
                                    }
                                }
                            }
                        }
                }
            }
    }

    // ============= HELPERS =============

    private fun GroupMessageEntity.toDomain(): Message {
        return Message(
            id = id,
            senderId = senderId,
            receiverId = groupId,
            text = text,
            timestamp = Timestamp(timestamp / 1000, ((timestamp % 1000) * 1000000).toInt()),
            type = MessageType.valueOf(type),
            reactions = parseReactionsJson(reactions),
            replyToId = replyToId,
            replyToText = replyToText,
            replyToSenderName = replyToSenderName,
            isDeletedForEveryone = isDeletedForEveryone,
            deletedBy = parseStringListJson(deletedBy),
            status = MessageStatus.valueOf(status),
            readBy = parseStringListJson(readBy)
        )
    }

    private fun parseReactionsJson(json: String): Map<String, String> {
        if (json == "{}") return emptyMap()
        return json.trim('{', '}')
            .split(",")
            .mapNotNull {
                val parts = it.split(":")
                if (parts.size == 2) {
                    parts[0].trim('"') to parts[1].trim('"')
                } else null
            }
            .toMap()
    }

    private fun toReactionsJson(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        return map.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
    }

    private fun parseStringListJson(json: String): List<String> {
        if (json == "[]") return emptyList()
        return json.trim('[', ']')
            .split(",")
            .map { it.trim('"') }
            .filter { it.isNotEmpty() }
    }

    private fun toStringListJson(list: List<String>): String {
        if (list.isEmpty()) return "[]"
        return list.joinToString(",", "[", "]") { "\"$it\"" }
    }
}
