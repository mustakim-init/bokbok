package com.mustakim.bokbok.data.repository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mustakim.bokbok.data.model.Notification
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Singleton

@Singleton
class NotificationRepository {
    private val firestore = FirebaseFirestore.getInstance()
    fun observeNotifications(userId: String): Flow<List<Notification>> = callbackFlow {
        val listener = firestore.collection("users").document(userId)
            .collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val notifications = snapshot?.toObjects(Notification::class.java) ?: emptyList()
                trySend(notifications)
            }
        awaitClose { listener.remove() }
    }
    suspend fun sendNotification(notification: Notification): Result<String> {
        return try {
            val docRef = firestore.collection("users").document(notification.recipientId)
                .collection("notifications").document()

            val notificationWithId = notification.copy(id = docRef.id)
            docRef.set(notificationWithId).await()
            Result.success(docRef.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun deleteNotification(userId: String, notificationId: String) {
        try {
            firestore.collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .delete()
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun markAsRead(userId: String, notificationId: String) {
        try {
            firestore.collection("users").document(userId)
                .collection("notifications").document(notificationId)
                .update("isRead", true)
                .await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}