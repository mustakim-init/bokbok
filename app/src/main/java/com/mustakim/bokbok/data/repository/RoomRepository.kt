package com.mustakim.bokbok.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.mustakim.bokbok.data.model.RoomCategory
import com.mustakim.bokbok.data.model.VoiceRoom
import kotlinx.coroutines.tasks.await
import com.mustakim.bokbok.data.model.User

class RoomRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val roomsCollection = firestore.collection("rooms")
    // ✅ NEW: Simple in-memory cache
    private val roomCache = java.util.concurrent.ConcurrentHashMap<String, Pair<VoiceRoom, Long>>()
    private val CACHE_TTL = 2 * 60 * 1000L // 2 minutes

    private val imgBBApi = com.mustakim.bokbok.data.api.ImgBBApi.create()

    /**
     * Load active rooms from Firestore.
     * Filtered by isPublic = true to hide private rooms.
     */
    suspend fun getActiveRooms(): Result<List<VoiceRoom>> {
        // 1. Try Disk Cache
        try {
            val cachedSnapshot = roomsCollection
                .whereEqualTo("isPublic", true)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20)
                .get(Source.CACHE)
                .await()
            
            if (!cachedSnapshot.isEmpty) {
                 val rooms = cachedSnapshot.documents.mapNotNull { doc ->
                    doc.data?.let { VoiceRoom.fromMap(it) }
                }
                // Return cache immediately. 
                // Note: We might want to trigger a background refresh, but that depends on the ViewModel strategy.
                // For "offline mode", this is sufficient.
                // Assuming ViewModel handles refresh via SwipeToRefresh or similar if new data is needed.
                if (rooms.isNotEmpty()) return Result.success(rooms)
            }
        } catch (e: Exception) {
            // Cache miss
        }

        // 2. Network Fetch
        return try {
            val snapshot = roomsCollection
                .whereEqualTo("isPublic", true) // ✅ Added filter
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(20) // Limit matches cache query
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

    /**
     * Get all rooms that the current user is a participant of.
     */
    suspend fun getMyRooms(): Result<List<VoiceRoom>> {
        val currentUser = auth.currentUser ?: return Result.failure(Exception("User not logged in"))

        // 1. Try Disk Cache
        try {
            val cachedSnapshot = roomsCollection
                .whereArrayContains("participants", currentUser.uid)
                .get(Source.CACHE)
                .await()

            if (!cachedSnapshot.isEmpty) {
                val rooms = cachedSnapshot.documents.mapNotNull { doc ->
                    doc.data?.let { VoiceRoom.fromMap(it) }
                }
                if (rooms.isNotEmpty()) return Result.success(rooms)
            }
        } catch (e: Exception) {
            // Cache miss
        }

        // 2. Network Fetch
        return try {
            val snapshot = roomsCollection
                .whereArrayContains("participants", currentUser.uid)
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

    /**
     * Create a new room document in Firestore and return its id.
     */
    suspend fun createRoom(
        name: String,
        description: String,
        maxParticipants: Int,
        category: RoomCategory,
        isPublic: Boolean,
        imageUrl: String
    ): Result<String> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not logged in"))

            val uid = currentUser.uid
            // Load app profile from Firestore to get correct profileImageUrl
            val userDoc = firestore.collection("users").document(uid).get().await()
            val user = if (userDoc.exists()) {
                User.fromMap(userDoc.data ?: emptyMap())
            } else {
                User(uid = uid) // fallback without image
            }

            val roomId = roomsCollection.document().id

            val room = VoiceRoom(
                id = roomId,
                name = name,
                hostId = currentUser.uid,
                hostName = currentUser.displayName ?: "",
                hostImageUrl = user.profileImageUrl,
                imageUrl = imageUrl,
                description = description,
                participants = listOf(currentUser.uid),
                maxParticipants = maxParticipants,
                isPublic = isPublic,
                category = category,
                createdAt = System.currentTimeMillis()
            )

            roomsCollection.document(roomId).set(room.toMap()).await()

            Result.success(room.id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadRoomImage(context: android.content.Context, imageUri: android.net.Uri): String? {
        return try {
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() }
                ?: return null

            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT)

            val response = imgBBApi.uploadImage(
                apiKey = com.mustakim.bokbok.BuildConfig.IMGBB_API_KEY,
                base64Image = base64
            )

            if (response.success && response.data != null) {
                response.data.url
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Load a single room by id (With Caching)
     */
    suspend fun getRoom(roomId: String): Result<VoiceRoom> {
        // 1. Check in-memory cache
        val cached = roomCache[roomId]
        if (cached != null && (System.currentTimeMillis() - cached.second) < CACHE_TTL) {
            return Result.success(cached.first)
        }

        // 2. Check Disk Persistence (Source.CACHE)
        try {
            val cachedDoc = roomsCollection.document(roomId).get(Source.CACHE).await()
            if (cachedDoc.exists()) {
                val data = cachedDoc.data
                if (data != null) {
                    val room = VoiceRoom.fromMap(data)
                    roomCache[roomId] = room to System.currentTimeMillis()
                    return Result.success(room)
                }
            }
        } catch (e: Exception) {
            // Cache miss
        }

        // 3. Fetch from network
        return try {
            val doc = roomsCollection.document(roomId).get().await()
            val data = doc.data ?: return Result.failure(Exception("Room not found"))
            val room = VoiceRoom.fromMap(data)

            // 4. Update cache
            roomCache[roomId] = room to System.currentTimeMillis()

            Result.success(room)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add multiple users to the room by their IDs.
     */
    suspend fun addUsersToRoom(roomId: String, userIds: List<String>): Result<Unit> {
        return try {
            if (userIds.isEmpty()) return Result.success(Unit)

            roomsCollection.document(roomId)
                .update("participants", FieldValue.arrayUnion(*userIds.toTypedArray()))
                .await()
            // ✅ NEW: Invalidate cache
            roomCache.remove(roomId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Join a room by adding the current user uid to participants.
     */
    suspend fun joinRoom(roomId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not logged in"))

            roomsCollection.document(roomId)
                .update("participants", FieldValue.arrayUnion(currentUser.uid))
                .await()

            // ✅ NEW: Invalidate cache
            roomCache.remove(roomId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Leave a room by removing the current user uid from participants.
     */
    suspend fun leaveRoom(roomId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not logged in"))

            roomsCollection.document(roomId)
                .update("participants", FieldValue.arrayRemove(currentUser.uid))
                .await()

            // ✅ NEW: Invalidate cache
            roomCache.remove(roomId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Delete a room by removing it from Firestore(only host is allowed to do this).
     */
    suspend fun deleteRoom(roomId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val doc = roomsCollection.document(roomId).get().await()
            val room = doc.data?.let { VoiceRoom.fromMap(it) }
                ?: return Result.failure(Exception("Room not found"))

            if (room.hostId != currentUser.uid) {
                return Result.failure(Exception("Only host can delete this room"))
            }

            roomsCollection.document(roomId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    /**
     * Update specific fields of a room.
     */
    suspend fun updateRoom(roomId: String, updates: Map<String, Any>): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            // Verify host (optional but good practice, though Firestore rules should enforce this too)
            val doc = roomsCollection.document(roomId).get().await()
            val room = doc.data?.let { VoiceRoom.fromMap(it) }
                ?: return Result.failure(Exception("Room not found"))

            if (room.hostId != currentUser.uid) {
                return Result.failure(Exception("Only host can update room settings"))
            }

            roomsCollection.document(roomId).update(updates).await()

            // ✅ Invalidate cache so next fetch gets fresh data
            roomCache.remove(roomId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a user from the room (kick from permanent members list).
     */
    suspend fun removeUserFromRoom(roomId: String, userId: String): Result<Unit> {
        return try {
            val currentUser = auth.currentUser
                ?: return Result.failure(Exception("User not authenticated"))

            val doc = roomsCollection.document(roomId).get().await()
            val room = doc.data?.let { VoiceRoom.fromMap(it) }
                ?: return Result.failure(Exception("Room not found"))

            if (room.hostId != currentUser.uid) {
                return Result.failure(Exception("Only host can remove members"))
            }

            roomsCollection.document(roomId)
                .update("participants", FieldValue.arrayRemove(userId))
                .await()

            // ✅ NEW: Invalidate cache
            roomCache.remove(roomId)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
