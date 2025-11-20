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

class FriendsRepository(
    private val userRepository: UserRepository
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val friendshipsCollection = firestore.collection("friendships")

    private val rtdb: FirebaseDatabase = FirebaseDatabase.getInstance()
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

        // Live status from RTDB
        val roomById = mutableMapOf<String, String?>()
        val onlineById = mutableMapOf<String, Boolean>()


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

        // ✅ RTDB listener: /userStatus
        val statusListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                roomById.clear()
                onlineById.clear()

                for (child in snapshot.children) {
                    val uid = child.key ?: continue
                    val roomId = child.child("currentRoomId").getValue(String::class.java)
                    val online = child.child("online").getValue(Boolean::class.java) ?: false

                    roomById[uid] = roomId
                    onlineById[uid] = online
                }
                emitCombined()
            }

            override fun onCancelled(error: DatabaseError) {
                // On permission / network errors, do NOT crash the app.
                // Option 1: send empty list and just stop listening.
                trySend(emptyList())

                // Optionally close the flow quietly so collectors stop:
                close()
            }
        }
        userStatusRef.addValueEventListener(statusListener)

        // ✅ Firestore listener: friendships + user profiles
        val listener: ListenerRegistration = friendshipsCollection
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

                CoroutineScope(Dispatchers.IO).launch {
                    val newBase = friendships.mapNotNull { friendship ->
                        val friendId = friendship.getOtherUserId(currentUserId)
                        val user = userRepository.getUserProfile(friendId).getOrNull()
                        user?.let {
                            FriendWithUser(
                                friendship = friendship,
                                user = it,
                                isOnline = false,       // status comes from RTDB
                                currentRoomId = null    // filled from RTDB
                            )
                        }
                    }
                    baseFriends = newBase
                    emitCombined()
                }
            }

        awaitClose {
            listener.remove()
            userStatusRef.removeEventListener(statusListener)
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

                // FIXED: Use CoroutineScope
                CoroutineScope(Dispatchers.IO).launch {
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

                // FIXED: Use CoroutineScope
                CoroutineScope(Dispatchers.IO).launch {
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
        return try {
            if (query.length < 2) {
                return Result.success(emptyList())
            }

            val snapshot = firestore.collection("users")
                .whereGreaterThanOrEqualTo("username", query.lowercase())
                .whereLessThanOrEqualTo("username", query.lowercase() + '\uf8ff')
                .limit(20)
                .get()
                .await()

            val users = snapshot.toObjects(User::class.java)
            val currentUserId = auth.currentUser?.uid
            val filtered = users.filter { it.uid != currentUserId }

            Result.success(filtered)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getFriendshipStatus(targetUserId: String): FriendshipStatus? {
        val currentUserId = auth.currentUser?.uid ?: return null
        val friendship = getFriendship(currentUserId, targetUserId)
        return friendship?.status
    }
}
