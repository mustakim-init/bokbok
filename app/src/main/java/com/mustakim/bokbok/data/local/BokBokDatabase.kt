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
        com.mustakim.bokbok.data.local.entity.GameEntity::class,
        com.mustakim.bokbok.data.local.entity.AppEntity::class,
        com.mustakim.bokbok.data.local.entity.UsageStatsEntity::class,
        com.mustakim.bokbok.data.local.entity.RecordingEntity::class,
        com.mustakim.bokbok.data.model.AIMessage::class,
        com.mustakim.bokbok.data.model.AISession::class,
        com.mustakim.bokbok.data.model.AIFact::class
    ],
    version = 11,
    exportSchema = false
)
@androidx.room.TypeConverters(GeneralTypeConverters::class)
abstract class BokBokDatabase : RoomDatabase() {
    
    abstract fun messageDao(): MessageDao
    abstract fun groupDao(): GroupDao
    abstract fun gameDao(): com.mustakim.bokbok.data.local.dao.GameDao
    abstract fun appDao(): com.mustakim.bokbok.data.local.dao.AppDao
    abstract fun usageStatsDao(): com.mustakim.bokbok.data.local.dao.UsageStatsDao
    abstract fun recordingDao(): com.mustakim.bokbok.data.local.dao.RecordingDao
    abstract fun aiConversationDao(): AIConversationDao
    
    companion object {
        @Volatile
        private var INSTANCE: BokBokDatabase? = null
        
        fun getInstance(context: Context): BokBokDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BokBokDatabase::class.java,
                    "bokbok_database"
                ).fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
