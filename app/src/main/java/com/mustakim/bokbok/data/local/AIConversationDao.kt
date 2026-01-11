package com.mustakim.bokbok.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mustakim.bokbok.data.model.AIMessage
import com.mustakim.bokbok.data.model.AISession
import kotlinx.coroutines.flow.Flow

@Dao
interface AIConversationDao {
    @Query("SELECT * FROM ai_messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<AIMessage>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AIMessage)
    
    @Query("DELETE FROM ai_messages WHERE conversationId = :conversationId")
    suspend fun clearMessagesForSession(conversationId: String)

    // Session Management
    @Query("SELECT * FROM ai_sessions ORDER BY lastUpdated DESC")
    fun getAllSessions(): Flow<List<AISession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AISession)

    @Query("DELETE FROM ai_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    @Query("UPDATE ai_sessions SET lastUpdated = :timestamp WHERE id = :sessionId")
    suspend fun updateSessionTimestamp(sessionId: String, timestamp: Long)

    @Query("UPDATE ai_sessions SET title = :title WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String)

    @Query("SELECT COUNT(*) FROM ai_messages WHERE conversationId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int
}
