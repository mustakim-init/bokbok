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

    @Query("SELECT * FROM apps WHERE packageName = :packageName")
    fun getAppByPackage(packageName: String): Flow<AppEntity?>

    @Query("SELECT * FROM apps")
    suspend fun getAppsOneShot(): List<AppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<AppEntity>)

    @Query("DELETE FROM apps")
    suspend fun deleteAll()

    @Transaction
    suspend fun syncApps(apps: List<AppEntity>) {
        // 1. Get all current packages in DB
        val existingPackages = getAppsOneShot().map { it.packageName }.toSet()
        val newPackages = apps.map { it.packageName }.toSet()

        // 2. Insert/Update new list (REPLACE strategy handles updates)
        insertAll(apps)

        // 3. Remove packages that no longer exist on device
        val toDelete = existingPackages - newPackages
        if (toDelete.isNotEmpty()) {
            deleteByPackages(toDelete.toList())
        }
    }

    @Query("DELETE FROM apps WHERE packageName IN (:packageNames)")
    suspend fun deleteByPackages(packageNames: List<String>)

    @Query("SELECT COUNT(*) FROM apps")
    suspend fun getAppCount(): Int

    @Query("UPDATE apps SET isEnabled = :isEnabled WHERE packageName = :packageName")
    suspend fun updateAppEnabledState(packageName: String, isEnabled: Boolean)

    @Query("UPDATE apps SET apkPath = :apkPath, dataPath = :dataPath, apkSize = :apkSize, dataSize = :dataSize, cacheSize = :cacheSize, hasActivities = :hasLauncher WHERE packageName = :packageName")
    suspend fun updateAppDetails(
        packageName: String, 
        apkPath: String, 
        dataPath: String, 
        apkSize: Long, 
        dataSize: Long, 
        cacheSize: Long, 
        hasLauncher: Boolean
    )
}
