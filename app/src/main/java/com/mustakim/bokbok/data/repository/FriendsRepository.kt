package com.mustakim.bokbok.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mustakim.bokbok.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.database.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendsRepository @Inject constructor(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val rtdb: FirebaseDatabase
) {
    private val friendshipsCollection = firestore.collection("friendships")
    private val userStatusRef: DatabaseReference = rtdb.getReference("userStatus")

    private fun getFriendshipId(userId1: String, userId2: String): String {
        val sortedIds = listOf(userId1, userId2).sorted()
        return "${sortedIds[0]}_${sortedIds[1]}"
    }

    private fun getOrderedUserIds(userId1: String, userId2: String): Pair<String, String> {
        val sortedIds = listOf(userId1, userId2).sorted()
        return Pair(sortedIds[0], sortedIds[1])
    }

    suspend fun sendFriendRequest(targetUserId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))

            if (currentUserId == targetUserId) {
                return Result.failure(Exception("Cannot add yourself as a friend"))
            }

            val existingFriendship = getFriendship(currentUserId, targetUserId)
            if (existingFriendship != null) {
                return when (existingFriendship.status) {
                    FriendshipStatus.ACCEPTED -> Result.failure(Exception("Already friends"))
                    FriendshipStatus.PENDING -> Result.failure(Exception("Friend request already sent"))
                    FriendshipStatus.BLOCKED -> Result.failure(Exception("Cannot send friend request"))
                }
            }

            val friendshipId = getFriendshipId(currentUserId, targetUserId)
            val (orderedId1, orderedId2) = getOrderedUserIds(currentUserId, targetUserId)

            val friendship = Friendship(
                id = friendshipId,
                userId1 = orderedId1,
                userId2 = orderedId2,
                status = FriendshipStatus.PENDING,
                requestedBy = currentUserId
            )

            friendshipsCollection.document(friendshipId).set(friendship).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun acceptFriendRequest(friendshipId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))

            val friendship = friendshipsCollection.document(friendshipId).get().await()
                .toObject(Friendship::class.java) ?: return Result.failure(Exception("Friend request not found"))

            if (friendship.requestedBy == currentUserId) {
                return Result.failure(Exception("Cannot accept your own friend request"))
            }

            friendshipsCollection.document(friendshipId).update(
                mapOf(
                    "status" to FriendshipStatus.ACCEPTED.name,
                    "acceptedAt" to com.google.firebase.Timestamp.now()
                )
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFriendship(friendshipId: String): Result<Unit> {
        return try {
            friendshipsCollection.document(friendshipId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun removeFriendByUserId(friendId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
            val friendshipId = getFriendshipId(currentUserId, friendId)
            removeFriendship(friendshipId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun blockUser(targetUserId: String): Result<Unit> {
        return try {
            val currentUserId = auth.currentUser?.uid ?: return Result.failure(Exception("Not logged in"))
            val friendshipId = getFriendshipId(currentUserId, targetUserId)

            friendshipsCollection.document(friendshipId).update(
                mapOf("status" to FriendshipStatus.BLOCKED.name)
            ).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getFriendship(userId1: String, userId2: String): Friendship? {
        return try {
            val friendshipId = getFriendshipId(userId1, userId2)
            friendshipsCollection.document(friendshipId)
                .get()
                .await()
                .toObject(Friendship::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun observeFriends(): Flow<List<FriendWithUser>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: run {
            close(Exception("Not logged in"))
            return@callbackFlow
        }

        // Base info from Firestore: friendship + User profile (no live status here)
        var baseFriends: List<FriendWithUser> = emptyList()

        // Live status data
        val roomById = mutableMapOf<String, String?>()
        val onlineById = mutableMapOf<String, Boolean>()
        
        // Track individual status listeners to reconcile them
        val statusListeners = mutableMapOf<String, ValueEventListener>()

        fun emitCombined() {
            if (baseFriends.isEmpty()) {
                trySend(emptyList())
                return
            }

            val combined = baseFriends.map { base ->
                val uid = base.user.uid
                val roomId = roomById[uid]
                val online = onlineById[uid] == true

                base.copy(
                    isOnline = online,
                    currentRoomId = roomId
                )
            }
            trySend(combined)
        }

        // Firestore listener: friendships + user profiles
        val firestoreListener: ListenerRegistration = friendshipsCollection
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("userId1", currentUserId),
                    com.google.firebase.firestore.Filter.equalTo("userId2", currentUserId)
                )
            )
            .whereEqualTo("status", FriendshipStatus.ACCEPTED.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val friendships = snapshot?.toObjects(Friendship::class.java) ?: emptyList()
                val friendIds = friendships.map { it.getOtherUserId(currentUserId) }.toSet()

                // ✅ FIX: Use the Flow's scope to avoid leaks
                launch {
                    // 1. Reconcile RTDB status listeners (per-friend instead of global root)
                    // Remove listeners for users who are no longer friends
                    val idsToRemove = statusListeners.keys.filter { it !in friendIds }
                    idsToRemove.forEach { uid ->
                        statusListeners[uid]?.let { userStatusRef.child(uid).removeEventListener(it) }
                        statusListeners.remove(uid)
                        roomById.remove(uid)
                        onlineById.remove(uid)
                    }

                    // Add listeners for new friends
                    friendIds.forEach { uid ->
                        if (!statusListeners.containsKey(uid)) {
                            val listener = object : ValueEventListener {
                                override fun onDataChange(s: DataSnapshot) {
                                    val roomId = s.child("currentRoomId").getValue(String::class.java)
                                    val online = s.child("online").getValue(Boolean::class.java) ?: false
                                    
                                    roomById[uid] = roomId
                                    onlineById[uid] = online
                                    emitCombined()
                                }
                                override fun onCancelled(e: DatabaseError) {
                                    android.util.Log.e("FriendsRepo", "Status listener cancelled for $uid", e.toException())
                                }
                            }
                            userStatusRef.child(uid).addValueEventListener(listener)
                            statusListeners[uid] = listener
                        }
                    }

                    // 2. Refresh profile data
                    val profiles = userRepository.getUserProfiles(friendIds.toList())
                    val profilesMap = profiles.associateBy { it.uid }

                    val newBase = friendships.mapNotNull { friendship ->
                        val friendId = friendship.getOtherUserId(currentUserId)
                        val user = profilesMap[friendId]
                        user?.let {
                            FriendWithUser(
                                friendship = friendship,
                                user = it,
                                isOnline = false, // Will be overridden in emitCombined
                                currentRoomId = null
                            )
                        }
                    }
                    baseFriends = newBase
                    emitCombined()
                }
            }

        awaitClose {
            firestoreListener.remove()
            // Cleanup all individual status listeners
            statusListeners.forEach { (uid, listener) ->
                userStatusRef.child(uid).removeEventListener(listener)
            }
            statusListeners.clear()
        }
    }

    fun observeIncomingFriendRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: run {
            close(Exception("Not logged in"))
            return@callbackFlow
        }

        val listener: ListenerRegistration = friendshipsCollection
            .where(
                com.google.firebase.firestore.Filter.or(
                    com.google.firebase.firestore.Filter.equalTo("userId1", currentUserId),
                    com.google.firebase.firestore.Filter.equalTo("userId2", currentUserId)
                )
            )
            .whereEqualTo("status", FriendshipStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val friendships = snapshot?.toObjects(Friendship::class.java) ?: emptyList()
                val incomingRequests = friendships.filter { it.requestedBy != currentUserId }

                // FIXED: Use the Flow's launch scope to prevent leaks
                launch {
                    val requestsWithSenders = incomingRequests.mapNotNull { friendship ->
                        val sender = userRepository.getUserProfile(friendship.requestedBy).getOrNull()
                        sender?.let { FriendRequest(friendship, it) }
                    }

                    trySend(requestsWithSenders)
                }
            }

        awaitClose { listener.remove() }
    }

    fun observeOutgoingFriendRequests(): Flow<List<FriendRequest>> = callbackFlow {
        val currentUserId = auth.currentUser?.uid ?: run {
            close(Exception("Not logged in"))
            return@callbackFlow
        }

        val listener: ListenerRegistration = friendshipsCollection
            .whereEqualTo("requestedBy", currentUserId)
            .whereEqualTo("status", FriendshipStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val friendships = snapshot?.toObjects(Friendship::class.java) ?: emptyList()

                // FIXED: Use the Flow's launch scope to prevent leaks
                launch {
                    val requestsWithRecipients = friendships.mapNotNull { friendship ->
                        val recipientId = friendship.getOtherUserId(currentUserId)
                        val recipient = userRepository.getUserProfile(recipientId).getOrNull()
                        recipient?.let { FriendRequest(friendship, it) }
                    }

                    trySend(requestsWithRecipients)
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun searchUsersByUsername(query: String): Result<List<User>> {
        val currentUserId = auth.currentUser?.uid
        // Delegate to UserRepository
        return userRepository.searchUsers(query.lowercase(), excludeUserId = currentUserId)
    }

    suspend fun getFriendshipStatus(targetUserId: String): FriendshipStatus? {
        val currentUserId = auth.currentUser?.uid ?: return null
        val friendship = getFriendship(currentUserId, targetUserId)
        return friendship?.status
    }

    /**
     * Observes a single user's online status from Firebase Realtime Database.
     * This is used by ChatScreen to sync with the friend's real-time status.
     * @return Flow that emits true when user is online, false when offline
     */
    fun observeUserOnlineStatus(userId: String): Flow<Boolean> = callbackFlow {
        val statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val online = snapshot.child("online").getValue(Boolean::class.java) ?: false
                trySend(online)
            }

            override fun onCancelled(error: DatabaseError) {
                // On error, emit offline status
                trySend(false)
            }
        }
        
        userStatusRef.child(userId).addValueEventListener(statusListener)

        awaitClose {
            userStatusRef.child(userId).removeEventListener(statusListener)
        }
    }
}
