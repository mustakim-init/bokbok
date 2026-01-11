package com.mustakim.bokbok.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ai_sessions")
data class AISession(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
