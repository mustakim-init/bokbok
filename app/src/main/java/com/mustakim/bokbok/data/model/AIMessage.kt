package com.mustakim.bokbok.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ai_messages")
data class AIMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val role: MessageRole, // USER, ASSISTANT, TOOL, SYSTEM
    val content: String,
    val name: String? = null, // Used for TOOL role
    val toolCallId: String? = null, // Used for TOOL results
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class MessageRole { USER, ASSISTANT, TOOL, SYSTEM }
