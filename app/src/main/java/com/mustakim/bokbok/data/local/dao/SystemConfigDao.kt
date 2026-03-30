package com.mustakim.bokbok.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mustakim.bokbok.data.local.entity.SystemConfigEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SystemConfigDao {
    @Query("SELECT * FROM system_configs WHERE `key` = :key")
    suspend fun getConfig(key: String): SystemConfigEntity?

    @Query("SELECT * FROM system_configs WHERE `key` = :key")
    fun getConfigFlow(key: String): Flow<SystemConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: SystemConfigEntity)

    @Query("DELETE FROM system_configs WHERE `key` = :key")
    suspend fun deleteConfig(key: String)

    @Query("SELECT value FROM system_configs WHERE `key` = :key")
    suspend fun getString(key: String): String?

    suspend fun putString(key: String, value: String) {
        insertConfig(SystemConfigEntity(key, value))
    }
}
