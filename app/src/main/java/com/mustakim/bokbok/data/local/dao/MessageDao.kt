package com.mustakim.bokbok.data.local.dao

import androidx.room.*
import com.mustakim.bokbok.data.local.entity.MessageEntity
import com.mustakim.bokbok.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    // ---- QUERIES ----
    
    @Query("""
        SELECT * FROM messages 
        WHERE chat_id = :chatId 
        AND sync_status != 'FAILED'
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun getMessagesByChatId(chatId: String, limit: Int = 50): Flow<List<MessageEntity>>
    
    @Query("""
        SELECT * FROM messages 
        WHERE chat_id = :chatId 
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun getLastMessage(chatId: String): MessageEntity?
    
    @Query("""
        SELECT * FROM messages 
        WHERE id = :messageId
    """)
    suspend fun getMessageById(messageId: String): MessageEntity?
    
    @Query("""
        SELECT * FROM messages 
        WHERE sync_status = :status 
        ORDER BY timestamp ASC
    """)
    suspend fun getMessagesByStatus(status: SyncStatus): List<MessageEntity>
    
    @Query("""
        SELECT * FROM messages 
        WHERE sync_status = 'PENDING' OR sync_status = 'FAILED'
        ORDER BY timestamp ASC
        LIMIT :limit
    """)
    suspend fun getPendingMessages(limit: Int = 20): List<MessageEntity>
    
    @Query("""
        SELECT * FROM messages 
        WHERE chat_id = :chatId 
        AND sender_id = :friendId
        AND is_read = 0
    """)
    suspend fun getUnreadMessages(chatId: String, friendId: String): List<MessageEntity>
    
    @Query("""
        SELECT COUNT(*) FROM messages 
        WHERE chat_id = :chatId 
        AND sender_id = :friendId
        AND is_read = 0
    """)
    fun getUnreadCount(chatId: String, friendId: String): Flow<Int>
    
    // Search messages
    @Query("""
        SELECT * FROM messages 
        WHERE chat_id = :chatId 
        AND text LIKE '%' || :query || '%'
        ORDER BY timestamp DESC
        LIMIT :limit
    """)
    suspend fun searchMessages(chatId: String, query: String, limit: Int = 50): List<MessageEntity>
    
    // ---- INSERTS ----
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    // ---- UPDATES ----
    
    @Update
    suspend fun update(message: MessageEntity)
    
    @Query("""
        UPDATE messages 
        SET sync_status = :status, 
            retry_count = :retryCount,
            last_sync_attempt = :lastAttempt
        WHERE id = :messageId
    """)
    suspend fun updateSyncStatus(
        messageId: String, 
        status: SyncStatus,
        retryCount: Int = 0,
        lastAttempt: Long = System.currentTimeMillis()
    )
    
    @Query("""
        UPDATE messages 
        SET is_read = 1 
        WHERE id IN (:messageIds)
    """)
    suspend fun markAsRead(messageIds: List<String>)
    
    @Query("""
        UPDATE messages 
        SET is_read = 1 
        WHERE chat_id = :chatId 
        AND sender_id = :friendId
    """)
    suspend fun markAllAsRead(chatId: String, friendId: String)
    
    @Query("""
        UPDATE messages 
        SET reactions = :reactionsJson 
        WHERE id = :messageId
    """)
    suspend fun updateReactions(messageId: String, reactionsJson: String)
    
    @Query("""
        UPDATE messages 
        SET is_deleted_for_everyone = 1, 
            text = 'Message unsent'
        WHERE id = :messageId
    """)
    suspend fun markAsDeletedForEveryone(messageId: String)
    
    @Query("""
        UPDATE messages 
        SET deleted_by = :deletedByJson 
        WHERE id = :messageId
    """)
    suspend fun updateDeletedBy(messageId: String, deletedByJson: String)
    
    // ---- DELETES ----
    
    @Delete
    suspend fun delete(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)
    
    @Query("DELETE FROM messages WHERE chat_id = :chatId")
    suspend fun deleteAllByChatId(chatId: String)
    
    @Query("""
        DELETE FROM messages 
        WHERE sync_status = 'FAILED' 
        AND retry_count >= :maxRetries
    """)
    suspend fun deleteFailedMessages(maxRetries: Int = 5)
    
    // ---- MAINTENANCE ----
    
    @Query("""
        DELETE FROM messages 
        WHERE id IN (
            SELECT id FROM messages 
            WHERE chat_id = :chatId 
            ORDER BY timestamp DESC 
            LIMIT -1 OFFSET :keepCount
        )
    """)
    suspend fun deleteOldMessages(chatId: String, keepCount: Int = 1000)
}
