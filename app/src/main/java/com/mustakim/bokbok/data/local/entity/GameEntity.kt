package com.mustakim.bokbok.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val packageName: String,
    val isHiddenFromLauncher: Boolean = false,
    val isUserAdded: Boolean = false,
    val customSettingsJson: String = "{}",
    val isManuallyRemoved: Boolean = false
)
