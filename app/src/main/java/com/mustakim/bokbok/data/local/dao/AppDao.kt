package com.mustakim.bokbok.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mustakim.bokbok.data.local.entity.AppEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM apps ORDER BY label COLLATE NOCASE ASC")
    fun getAllApps(): Flow<List<AppEntity>>

    @Query("SELECT * FROM apps")
    suspend fun getAppsOneShot(): List<AppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppEntity>)

    @Query("DELETE FROM apps")
    suspend fun deleteAll()

    @Transaction
    suspend fun refreshApps(apps: List<AppEntity>) {
        deleteAll()
        insertAll(apps)
    }

    @Query("SELECT COUNT(*) FROM apps")
    suspend fun getAppCount(): Int
}
