package com.mustakim.bokbok.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "system_configs")
data class SystemConfigEntity(
    @PrimaryKey
    val key: String,
    val value: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
