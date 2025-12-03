package com.mustakim.bokbok.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.model.MessageStatus
import com.mustakim.bokbok.data.model.MessageType
import com.google.firebase.Timestamp

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["chat_id", "timestamp"]),
        Index(value = ["sync_status"]),
        Index(value = ["sender_id"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "chat_id")
    val chatId: String,
    
    @ColumnInfo(name = "sender_id")
    val senderId: String,
    
    @ColumnInfo(name = "receiver_id") 
    val receiverId: String,
    
    val text: String,
    
    val timestamp: Long, // Stored as millis for easy sorting
    
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    
    val type: String = MessageType.TEXT.name,
    
    @ColumnInfo(name = "media_url")
    val mediaUrl: String? = null,
    
    // Stored as JSON string
    val reactions: String = "{}",
    
    @ColumnInfo(name = "reply_to_id")
    val replyToId: String? = null,
    
    @ColumnInfo(name = "reply_to_text")
    val replyToText: String? = null,
    
    @ColumnInfo(name = "reply_to_sender_name")
    val replyToSenderName: String? = null,
    
    @ColumnInfo(name = "is_deleted_for_everyone")
    val isDeletedForEveryone: Boolean = false,
    
    // Stored as JSON array string
    @ColumnInfo(name = "deleted_by")
    val deletedBy: String = "[]",
    
    val status: String = MessageStatus.SENT.name,
    
    // Stored as JSON array string
    @ColumnInfo(name = "read_by")
    val readBy: String = "[]",
    
    // Sync status for offline support
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    
    @ColumnInfo(name = "last_sync_attempt")
    val lastSyncAttempt: Long? = null
)

enum class SyncStatus {
    SYNCED,      // Successfully synced with Firestore
    PENDING,     // Waiting to be synced
    SYNCING,     // Currently being synced
    FAILED       // Sync failed
}

// Extension functions for conversion
fun MessageEntity.toDomain(): Message {
    return Message(
        id = id,
        senderId = senderId,
        receiverId = receiverId,
        text = text,
        timestamp = Timestamp(timestamp / 1000, ((timestamp % 1000) * 1000000).toInt()),
        isRead = isRead,
        type = MessageType.valueOf(type),
        mediaUrl = mediaUrl,
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

fun Message.toEntity(chatId: String, syncStatus: SyncStatus = SyncStatus.SYNCED): MessageEntity {
    return MessageEntity(
        id = id,
        chatId = chatId,
        senderId = senderId,
        receiverId = receiverId,
        text = text,
        timestamp = timestamp.toDate().time,
        isRead = isRead,
        type = type.name,
        mediaUrl = mediaUrl,
        reactions = toReactionsJson(reactions),
        replyToId = replyToId,
        replyToText = replyToText,
        replyToSenderName = replyToSenderName,
        isDeletedForEveryone = isDeletedForEveryone,
        deletedBy = toStringListJson(deletedBy),
        status = status.name,
        readBy = toStringListJson(readBy),
        syncStatus = syncStatus
    )
}

// Simple JSON helpers (you can use Gson/Moshi for production)
private fun parseReactionsJson(json: String): Map<String, String> {
    if (json == "{}") return emptyMap()
    // Simple parsing - in production use Gson
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
    return map.entries.joinToString(",", "{", "}") { 
        "\"${it.key}\":\"${it.value}\"" 
    }
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
