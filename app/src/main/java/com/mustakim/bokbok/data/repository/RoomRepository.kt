package com.mustakim.bokbok.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mustakim.bokbok.data.model.RoomCategory
import com.mustakim.bokbok.data.model.VoiceRoom
import kotlinx.coroutines.tasks.await

class RoomRepository {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val roomsCollection = firestore.collection("rooms")

    suspend fun getActiveRooms(): Result<List<VoiceRoom>> {
        return try {
            val snapshot = roomsCollection
                .whereEqualTo("isPublic", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()

            val rooms = snapshot.documents.mapNotNull { doc ->
                doc.data?.let { VoiceRoom.fromMap(it) }
            }

            Result.success(rooms)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRoom(
        name: String,
        description: String,
        maxParticipants: Int,
        category: RoomCategory,
        isPublic: Boolean
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            // Get user data from Firestore
            val userDoc = firestore.collection("users")
                .document(currentUser.uid)
                .get()
                .await()

            val displayName = userDoc.getString("displayName") ?: "Unknown"
            val profileImageUrl = userDoc.getString("profileImageUrl") ?: ""

            val roomId = roomsCollection.document().id

            val room = VoiceRoom(
                id = roomId,
                name = name,
                hostId = currentUser.uid,
                hostName = displayName,
                hostImageUrl = profileImageUrl,
                description = description,
                participants = listOf(currentUser.uid),
                maxParticipants = maxParticipants,
                isPublic = isPublic,
                category = category,
                createdAt = System.currentTimeMillis()
            )

            roomsCollection.document(roomId)
                .set(room.toMap())
                .await()

            Result.success(roomId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun joinRoom(roomId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            // Get room
            val roomDoc = roomsCollection.document(roomId).get().await()
            val room = roomDoc.data?.let { VoiceRoom.fromMap(it) }
                ?: throw Exception("Room not found")

            // Check if room is full
            if (room.isFull) {
                throw Exception("Room is full")
            }

            // Check if already in room
            if (room.participants.contains(currentUser.uid)) {
                return Result.success(Unit)
            }

            // Add user to room
            val updatedParticipants = room.participants + currentUser.uid

            roomsCollection.document(roomId)
                .update("participants", updatedParticipants)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun leaveRoom(roomId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser ?: throw Exception("User not authenticated")

            // Get room
            val roomDoc = roomsCollection.document(roomId).get().await()
            val room = roomDoc.data?.let { VoiceRoom.fromMap(it) }
                ?: throw Exception("Room not found")

            // Remove user from room
            val updatedParticipants = room.participants.filter { it != currentUser.uid }

            if (updatedParticipants.isEmpty()) {
                // Delete room if no participants left
                roomsCollection.document(roomId).delete().await()
            } else {
                // Update participants
                roomsCollection.document(roomId)
                    .update("participants", updatedParticipants)
                    .await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRoom(roomId: String): Result<VoiceRoom> {
        return try {
            val roomDoc = roomsCollection.document(roomId).get().await()
            val room = roomDoc.data?.let { VoiceRoom.fromMap(it) }
                ?: throw Exception("Room not found")

            Result.success(room)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
