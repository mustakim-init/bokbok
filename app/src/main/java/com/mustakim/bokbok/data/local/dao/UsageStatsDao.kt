package com.mustakim.bokbok.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mustakim.bokbok.data.local.entity.UsageStatsEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageStatsDao {
    @Query("SELECT * FROM usage_stats ORDER BY screenTime DESC")
    fun getUsageStats(): Flow<List<UsageStatsEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stats: List<UsageStatsEntity>)

    @Query("DELETE FROM usage_stats")
    suspend fun deleteAll()

    @Transaction
    suspend fun refreshUsageStats(stats: List<UsageStatsEntity>) {
        deleteAll()
        insertAll(stats)
    }
}
