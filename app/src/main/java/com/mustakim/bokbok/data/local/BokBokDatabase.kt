package com.mustakim.bokbok.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.mustakim.bokbok.data.local.dao.GroupDao
import com.mustakim.bokbok.data.local.dao.MessageDao
import com.mustakim.bokbok.data.local.entity.GroupEntity
import com.mustakim.bokbok.data.local.entity.GroupMemberEntity
import com.mustakim.bokbok.data.local.entity.GroupMessageEntity
import com.mustakim.bokbok.data.local.entity.MessageEntity

@Database(
    entities = [
        MessageEntity::class,
        GroupEntity::class,
        GroupMemberEntity::class,
        GroupMessageEntity::class,
        com.mustakim.bokbok.data.local.entity.GameEntity::class
    ],
    version = 5,
    exportSchema = false
)
@androidx.room.TypeConverters(GeneralTypeConverters::class)
abstract class BokBokDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao
    abstract fun gameDao(): com.mustakim.bokbok.data.local.dao.GameDao
    
    companion object {
        @Volatile
        private var INSTANCE: BokBokDatabase? = null
        
        fun getInstance(context: Context): BokBokDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BokBokDatabase::class.java,
                    "bokbok_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
