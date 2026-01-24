package com.mustakim.bokbok.di

import android.content.Context
import com.mustakim.bokbok.data.local.BokBokDatabase
import com.mustakim.bokbok.data.local.dao.GameDao
import com.mustakim.bokbok.data.local.dao.AppDao
import com.mustakim.bokbok.data.local.dao.UsageStatsDao
import com.mustakim.bokbok.data.repository.AppManagerRepository
import com.mustakim.bokbok.data.repository.UsageStatsRepository
import com.mustakim.bokbok.data.repository.GameRepository
import com.mustakim.bokbok.data.repository.FCMRepository
import com.mustakim.bokbok.data.repository.BackendService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BokBokDatabase {
        return BokBokDatabase.getInstance(context)
    }

    @Provides
    fun provideGameDao(database: BokBokDatabase): GameDao {
        return database.gameDao()
    }

    @Provides
    fun provideAppDao(database: BokBokDatabase): AppDao {
        return database.appDao()
    }

    @Provides
    fun provideUsageStatsDao(database: BokBokDatabase): UsageStatsDao {
        return database.usageStatsDao()
    }

    @Provides
    fun provideRecordingDao(database: BokBokDatabase): com.mustakim.bokbok.data.local.dao.RecordingDao {
        return database.recordingDao()
    }

    @Provides
    fun provideAIConversationDao(database: BokBokDatabase): com.mustakim.bokbok.data.local.AIConversationDao {
        return database.aiConversationDao()
    }

    @Provides
    @Singleton
    fun provideAppManagerRepository(@ApplicationContext context: Context, appDao: AppDao): AppManagerRepository {
        return AppManagerRepository(context, appDao)
    }

    @Provides
    @Singleton
    fun provideUsageStatsRepository(@ApplicationContext context: Context, usageStatsDao: UsageStatsDao): UsageStatsRepository {
        return UsageStatsRepository(context, usageStatsDao)
    }

    @Provides
    @Singleton
    fun provideGameRepository(@ApplicationContext context: Context, gameDao: GameDao, appDao: AppDao): GameRepository {
        return GameRepository(context, gameDao, appDao)
    }

    @Provides
    @Singleton
    fun provideUserRepository(
        @ApplicationContext context: Context,
        auth: com.google.firebase.auth.FirebaseAuth,
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        imgBBApi: com.mustakim.bokbok.data.api.ImgBBApi
    ): com.mustakim.bokbok.data.repository.UserRepository {
        return com.mustakim.bokbok.data.repository.UserRepository(context, auth, firestore, imgBBApi)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        @ApplicationContext context: Context,
        auth: com.google.firebase.auth.FirebaseAuth,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): com.mustakim.bokbok.data.repository.AuthRepository {
        return com.mustakim.bokbok.data.repository.AuthRepository(context, auth, firestore)
    }

    @Provides
    @Singleton
    fun provideFriendsRepository(
        userRepository: com.mustakim.bokbok.data.repository.UserRepository,
        auth: com.google.firebase.auth.FirebaseAuth,
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        rtdb: com.google.firebase.database.FirebaseDatabase
    ): com.mustakim.bokbok.data.repository.FriendsRepository {
        return com.mustakim.bokbok.data.repository.FriendsRepository(userRepository, auth, firestore, rtdb)
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        auth: com.google.firebase.auth.FirebaseAuth,
        firestore: com.google.firebase.firestore.FirebaseFirestore
    ): com.mustakim.bokbok.data.repository.ChatRepository {
        return com.mustakim.bokbok.data.repository.ChatRepository(auth, firestore)
    }

    @Provides
    @Singleton
    fun provideHybridChatRepository(
        @ApplicationContext context: Context,
        auth: com.google.firebase.auth.FirebaseAuth,
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        database: BokBokDatabase,
        fcmRepository: FCMRepository
    ): com.mustakim.bokbok.data.repository.HybridChatRepository {
        return com.mustakim.bokbok.data.repository.HybridChatRepository(context, auth, firestore, database, fcmRepository)
    }

    @Provides
    @Singleton
    fun provideHybridGroupChatRepository(
        @ApplicationContext context: Context,
        auth: com.google.firebase.auth.FirebaseAuth,
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        database: BokBokDatabase,
        imgBBApi: com.mustakim.bokbok.data.api.ImgBBApi,
        fcmRepository: FCMRepository
    ): com.mustakim.bokbok.data.repository.HybridGroupChatRepository {
        return com.mustakim.bokbok.data.repository.HybridGroupChatRepository(context, auth, firestore, database, imgBBApi, fcmRepository)
    }

    @Provides
    @Singleton
    fun provideNotificationRepository(firestore: com.google.firebase.firestore.FirebaseFirestore): com.mustakim.bokbok.data.repository.NotificationRepository {
        return com.mustakim.bokbok.data.repository.NotificationRepository(firestore)
    }

    @Provides
    @Singleton
    fun provideFCMRepository(backendService: BackendService): FCMRepository {
        return FCMRepository(backendService)
    }

    @Provides
    @Singleton
    fun providePresenceRepository(
        auth: com.google.firebase.auth.FirebaseAuth,
        rtdb: com.google.firebase.database.FirebaseDatabase
    ): com.mustakim.bokbok.data.repository.PresenceRepository {
        return com.mustakim.bokbok.data.repository.PresenceRepository(auth, rtdb)
    }

    @Provides
    @Singleton
    fun provideRoomRepository(
        auth: com.google.firebase.auth.FirebaseAuth,
        firestore: com.google.firebase.firestore.FirebaseFirestore,
        imgBBApi: com.mustakim.bokbok.data.api.ImgBBApi
    ): com.mustakim.bokbok.data.repository.RoomRepository {
        return com.mustakim.bokbok.data.repository.RoomRepository(auth, firestore, imgBBApi)
    }
}
