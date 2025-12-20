package com.mustakim.bokbok.data.local

import androidx.room.TypeConverter
import com.mustakim.bokbok.data.model.OptimizationProfile

class GeneralTypeConverters {
    @TypeConverter
    fun fromOptimizationProfile(profile: OptimizationProfile): String {
        return profile.name
    }

    @TypeConverter
    fun toOptimizationProfile(value: String): OptimizationProfile {
        return OptimizationProfile.valueOf(value)
    }
}
