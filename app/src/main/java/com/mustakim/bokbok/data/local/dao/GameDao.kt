package com.mustakim.bokbok.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.mustakim.bokbok.data.local.entity.GameEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM games")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE packageName = :packageName LIMIT 1")
    suspend fun getGameByPackage(packageName: String): GameEntity?

    @Upsert
    suspend fun upsertGame(game: GameEntity)

    @Upsert
    suspend fun upsertGames(games: List<GameEntity>)

    @Query("DELETE FROM games WHERE packageName = :packageName")
    suspend fun removeGame(packageName: String)
}
