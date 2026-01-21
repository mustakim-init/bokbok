package com.mustakim.bokbok.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_facts")
data class AIFact(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)
