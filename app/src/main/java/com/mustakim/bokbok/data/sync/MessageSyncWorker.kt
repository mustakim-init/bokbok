package com.mustakim.bokbok.data.sync

import android.content.Context
import androidx.work.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mustakim.bokbok.data.local.BokBokDatabase
import com.mustakim.bokbok.data.local.entity.SyncStatus
import com.mustakim.bokbok.data.local.entity.toEntity
import com.mustakim.bokbok.data.model.Message
import com.mustakim.bokbok.data.repository.ChatRepository
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that syncs pending messages to Firestore
 */
class MessageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val database = BokBokDatabase.getInstance(context)
    private val messageDao = database.messageDao()
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    override suspend fun doWork(): Result {
        val currentUserId = auth.currentUser?.uid ?: return Result.failure()
        
        try {
            // Get all pending/failed messages
            val pendingMessages = messageDao.getPendingMessages(limit = 20)
            
            if (pendingMessages.isEmpty()) {
                return Result.success()
            }
            
            android.util.Log.d("MessageSyncWorker", "Syncing ${pendingMessages.size} messages")
            
            var successCount = 0
            var failCount = 0
            
            pendingMessages.forEach { messageEntity ->
                try {
                    // Mark as syncing
                    messageDao.updateSyncStatus(
                        messageId = messageEntity.id,
                        status = SyncStatus.SYNCING,
                        retryCount = messageEntity.retryCount
                    )
                    
                    // Upload to Firestore
                    val chatId = messageEntity.chatId
                    val chatRef = firestore.collection("chats").document(chatId)
                    
                    // Use batch to update both message and chat document
                    firestore.runBatch { batch ->
                        // Add message
                        batch.set(
                            chatRef.collection("messages").document(messageEntity.id),
                            mapOf(
                                "id" to messageEntity.id,
                                "senderId" to messageEntity.senderId,
                                "receiverId" to messageEntity.receiverId,
                                "text" to messageEntity.text,
                                "timestamp" to com.google.firebase.Timestamp(
                                    messageEntity.timestamp / 1000,
                                    ((messageEntity.timestamp % 1000) * 1000000).toInt()
                                ),
                                "type" to messageEntity.type,
                                "status" to "SENT",
                                "readBy" to listOf(messageEntity.senderId),
                                "reactions" to emptyMap<String, String>(),
                                "isDeletedForEveryone" to false,
                                "deletedBy" to emptyList<String>()
                            )
                        )
                        
                        // Update chat last message
                        batch.set(
                            chatRef,
                            mapOf(
                                "participants" to listOf(messageEntity.senderId, messageEntity.receiverId),
                                "lastMessage" to mapOf(
                                    "text" to messageEntity.text,
                                    "senderId" to messageEntity.senderId,
                                    "timestamp" to com.google.firebase.Timestamp(
                                        messageEntity.timestamp / 1000,
                                        ((messageEntity.timestamp % 1000) * 1000000).toInt()
                                    ),
                                    "type" to messageEntity.type,
                                    "isDeleted" to false
                                ),
                                "lastMessageTime" to com.google.firebase.Timestamp(
                                    messageEntity.timestamp / 1000,
                                    ((messageEntity.timestamp % 1000) * 1000000).toInt()
                                ),
                                "unreadCount_${messageEntity.receiverId}" to com.google.firebase.firestore.FieldValue.increment(1)
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                    }.await()
                    
                    // Mark as synced
                    messageDao.updateSyncStatus(
                        messageId = messageEntity.id,
                        status = SyncStatus.SYNCED,
                        retryCount = 0
                    )
                    
                    successCount++
                    android.util.Log.d("MessageSyncWorker", "Synced message: ${messageEntity.id}")
                    
                } catch (e: Exception) {
                    android.util.Log.e("MessageSyncWorker", "Failed to sync message: ${messageEntity.id}", e)
                    
                    // Update retry count
                    val newRetryCount = messageEntity.retryCount + 1
                    val status = if (newRetryCount >= 5) SyncStatus.FAILED else SyncStatus.PENDING
                    
                    messageDao.updateSyncStatus(
                        messageId = messageEntity.id,
                        status = status,
                        retryCount = newRetryCount
                    )
                    
                    failCount++
                }
            }
            
            android.util.Log.d("MessageSyncWorker", "Sync complete: $successCount succeeded, $failCount failed")
            
            return if (failCount > 0) Result.retry() else Result.success()
            
        } catch (e: Exception) {
            android.util.Log.e("MessageSyncWorker", "Sync worker failed", e)
            return Result.retry()
        }
    }
    
    companion object {
        const val WORK_NAME = "message_sync_work"
        
        /**
         * Schedule periodic sync (runs every 15 minutes when device is online)
         */
        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = PeriodicWorkRequestBuilder<MessageSyncWorker>(
                15, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
        
        /**
         * Trigger immediate one-time sync
         */
        fun triggerImmediateSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            
            val syncRequest = OneTimeWorkRequestBuilder<MessageSyncWorker>()
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            
            WorkManager.getInstance(context).enqueue(syncRequest)
        }
    }
}
