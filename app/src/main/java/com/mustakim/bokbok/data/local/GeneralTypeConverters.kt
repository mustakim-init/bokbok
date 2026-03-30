package com.mustakim.bokbok.data.local

import androidx.room.TypeConverter
import com.mustakim.bokbok.data.bloatware.RemovalSafety

class GeneralTypeConverters {
    @TypeConverter
    fun fromRemovalSafety(safety: RemovalSafety): String {
        return safety.name
    }

    @TypeConverter
    fun toRemovalSafety(value: String): RemovalSafety {
        return RemovalSafety.valueOf(value)
    }
}
