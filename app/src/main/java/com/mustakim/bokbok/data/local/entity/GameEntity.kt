package com.mustakim.bokbok.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

import com.mustakim.bokbok.data.model.OptimizationProfile

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey val packageName: String,
    val isHiddenFromLauncher: Boolean = false,
    val isUserAdded: Boolean = false,
    val optimizationProfile: OptimizationProfile = OptimizationProfile.BALANCED,
    val customSettingsJson: String = "{}"
)
