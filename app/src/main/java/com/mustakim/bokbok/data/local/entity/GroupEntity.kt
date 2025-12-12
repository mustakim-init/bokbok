package com.mustakim.bokbok.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.mustakim.bokbok.data.model.User

/**
 * Entity for caching group information in Room for offline-first experience
 */
@Entity(
    tableName = "chat_groups",
    indices = [Index(value = ["last_message_time"])]
)
data class GroupEntity(
    @PrimaryKey
    val id: String,
    
    val name: String,
    
    @ColumnInfo(name = "image_url")
    val imageUrl: String = "",
    
    // Stored as JSON array string
    val participants: String = "[]",
    
    @ColumnInfo(name = "created_by")
    val createdBy: String,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    
    @ColumnInfo(name = "last_message_time")
    val lastMessageTime: Long = 0,
    
    @ColumnInfo(name = "last_message_text")
    val lastMessageText: String? = null,
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Entity for caching group member profiles for instant UI display
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["group_id", "user_id"],
    indices = [Index(value = ["group_id"])]
)
data class GroupMemberEntity(
    @ColumnInfo(name = "group_id")
    val groupId: String,
    
    @ColumnInfo(name = "user_id")
    val userId: String,
    
    @ColumnInfo(name = "display_name")
    val displayName: String,
    
    @ColumnInfo(name = "profile_image_url")
    val profileImageUrl: String = "",
    
    val username: String = "",
    
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Entity for caching group messages (similar to MessageEntity but with groupId)
 */
@Entity(
    tableName = "group_messages",
    indices = [
        Index(value = ["group_id", "timestamp"]),
        Index(value = ["sync_status"]),
        Index(value = ["sender_id"])
    ]
)
data class GroupMessageEntity(
    @PrimaryKey
    val id: String,
    
    @ColumnInfo(name = "group_id")
    val groupId: String,
    
    @ColumnInfo(name = "sender_id")
    val senderId: String,
    
    val text: String,
    
    val timestamp: Long,
    
    val type: String = "TEXT",
    
    @ColumnInfo(name = "media_url")
    val mediaUrl: String? = null,
    
    val reactions: String = "{}",
    
    @ColumnInfo(name = "reply_to_id")
    val replyToId: String? = null,
    
    @ColumnInfo(name = "reply_to_text")
    val replyToText: String? = null,
    
    @ColumnInfo(name = "reply_to_sender_name")
    val replyToSenderName: String? = null,
    
    @ColumnInfo(name = "is_deleted_for_everyone")
    val isDeletedForEveryone: Boolean = false,
    
    @ColumnInfo(name = "deleted_by")
    val deletedBy: String = "[]",
    
    val status: String = "SENT",
    
    @ColumnInfo(name = "read_by")
    val readBy: String = "[]",
    
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,
    
    @ColumnInfo(name = "summoned_user_ids")
    val summonedUserIds: String = "" // Comma-separated user IDs
)

// Extension functions
fun GroupMemberEntity.toUser(): User {
    return User(
        uid = userId,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        username = username
    )
}

fun User.toGroupMemberEntity(groupId: String): GroupMemberEntity {
    return GroupMemberEntity(
        groupId = groupId,
        userId = uid,
        displayName = displayName,
        profileImageUrl = profileImageUrl,
        username = username
    )
}
