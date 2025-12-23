package com.mustakim.bokbok.data.local

import androidx.room.TypeConverter
import com.mustakim.bokbok.data.bloatware.RemovalSafety
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

    @TypeConverter
    fun fromRemovalSafety(safety: RemovalSafety): String {
        return safety.name
    }

    @TypeConverter
    fun toRemovalSafety(value: String): RemovalSafety {
        return RemovalSafety.valueOf(value)
    }
}
