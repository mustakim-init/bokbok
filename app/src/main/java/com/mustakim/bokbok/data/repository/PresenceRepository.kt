package com.mustakim.bokbok.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PresenceRepository {

    private val db = FirebaseDatabase.getInstance()
    private val presenceRoot = db.getReference("presence")

    // ✅ NEW: per-user status node
    private val userStatusRoot = db.getReference("userStatus")
    private val auth = FirebaseAuth.getInstance()

    // Scope for async work inside this repository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun uid(): String =
        auth.currentUser?.uid ?: error("Not logged in")

    fun setUserOnline() {
        val ref = userStatusRoot.child(uid())

        // Mark as online and not currently in a room
        ref.child("online").setValue(true)
        ref.child("currentRoomId").setValue(null)

        // If the app disconnects unexpectedly, mark offline + no room
        ref.onDisconnect().setValue(
            mapOf(
                "online" to false,
                "currentRoomId" to null
            )
        )
    }

    fun setUserOffline() {
        val userId = uid()
        val statusRef = userStatusRoot.child(userId)

        // Best-effort: if userStatus says we are in a room, leave its presence
        statusRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val roomId = snapshot.child("currentRoomId").getValue(String::class.java)
                if (!roomId.isNullOrEmpty()) {
                    // This clears /presence/{roomId}/{uid} and sets currentRoomId = null
                    scope.launch {
                        try {
                            leaveCall(roomId)
                        } catch (_: Exception) {
                            // Ignore – going offline is best-effort
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // Ignore; going offline is best-effort
            }
        })

        // Cancel the previous onDisconnect and explicitly mark offline + no room
        statusRef.onDisconnect().cancel()
        statusRef.setValue(
            mapOf(
                "online" to false,
                "currentRoomId" to null
            )
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun ensureCleanState(targetRoomId: String) {
        val userId = uid()
        // 1. Get the current room ID from server
        val currentRoomId = suspendCancellableCoroutine<String?> { cont ->
            userStatusRoot.child(userId).child("currentRoomId").addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rId = snapshot.getValue(String::class.java)
                    if (cont.isActive) cont.resume(rId) {}
                }
                override fun onCancelled(error: DatabaseError) {
                    if (cont.isActive) cont.resume(null) {}
                }
            })
        }
        // 2. If it exists and is different, leave it
        if (!currentRoomId.isNullOrEmpty() && currentRoomId != targetRoomId) {
            android.util.Log.w("PresenceRepository", "User is still in $currentRoomId, forcing leave before joining $targetRoomId")
            leaveCall(currentRoomId)
        }
    }

    private fun startReconnectionMonitor(roomId: String) {
        val connectedRef = db.getReference(".info/connected")
        connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    // We just reconnected!
                    // Check if we are still listed in the room. If not, re-join.
                    val userId = uid()
                    val roomRef = presenceRoot.child(roomId)

                    roomRef.child(userId).addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(snap: DataSnapshot) {
                            if (!snap.exists()) {
                                // We were removed (likely due to onDisconnect firing during a flicker).
                                // Re-assert our presence!
                                android.util.Log.d("PresenceRepo", "Reconnected and re-asserting presence in $roomId")
                                roomRef.child(userId).setValue(true)

                                // Re-arm the onDisconnect handler
                                setupPresenceAfterJoin(roomId)
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {}
                    })
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Atomic join using Transaction
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun tryJoinRoom(roomId: String, maxParticipants: Int): Result<Boolean> {
        // 🛑 NEW: Ensure we are not in another room according to the server
        ensureCleanState(roomId)
        return suspendCancellableCoroutine { cont ->
            val roomRef = presenceRoot.child(roomId)

            roomRef.runTransaction(object : Transaction.Handler {
                // ... (rest of your existing transaction code is unchanged) ...
                override fun doTransaction(currentData: MutableData): Transaction.Result {
                    val currentCount = currentData.childrenCount
                    if (currentData.hasChild(uid())) {
                        return Transaction.success(currentData)
                    }
                    if (currentCount >= maxParticipants) {
                        return Transaction.abort()
                    }
                    currentData.child(uid()).value = true
                    return Transaction.success(currentData)
                }
                override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                    if (error != null) {
                        cont.resume(Result.failure(error.toException())) {}
                    } else if (!committed) {
                        cont.resume(Result.success(false)) {}
                    } else {
                        setupPresenceAfterJoin(roomId)
                        cont.resume(Result.success(true)) {}
                    }
                }
            })
        }
    }

    private fun setupPresenceAfterJoin(roomId: String) {
        // Re-affirm presence with onDisconnect (Transaction doesn't set onDisconnect)
        val ref = presenceRoot.child(roomId).child(uid())
        ref.onDisconnect().removeValue()
        // Update user status
        val statusRef = userStatusRoot.child(uid())
        statusRef.child("currentRoomId").setValue(roomId)
        // [ADD THIS LINE HERE]
        startReconnectionMonitor(roomId)
    }

    // Deprecated: joinCall is now handled internally by tryJoinRoom
    // keeping it private if needed for edge cases, or removing if unused.
    // For this refactor, we remove the public joinCall and rely on tryJoinRoom.


    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun leaveCall(roomId: String) = suspendCancellableCoroutine<Unit> { cont ->
        // Per-room presence
        val ref = presenceRoot.child(roomId).child(uid())

        // Cancel onDisconnect first
        ref.onDisconnect().cancel()
        // Remove value and WAIT for it to finish
        ref.removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Also clear user status (best effort)
                val statusRef = userStatusRoot.child(uid())
                statusRef.child("currentRoomId").setValue(null)

                if (cont.isActive) cont.resume(Unit) {}
            } else {
                // Even if it fails, we resume so the app doesn't hang.
                if (cont.isActive) cont.resume(Unit) {}
            }
        }
    }

    fun observeRoomPresence(
        roomId: String,
        onChange: (Set<String>) -> Unit,
        onError: (DatabaseError) -> Unit
    ): ValueEventListener {
        val ref = presenceRoot.child(roomId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val ids = snapshot.children.mapNotNull { it.key }.toSet()
                onChange(ids)
            }

            override fun onCancelled(error: DatabaseError) {
                onError(error)
            }
        }
        ref.addValueEventListener(listener)
        return listener
    }

    fun removePresenceListener(roomId: String, listener: ValueEventListener) {
        presenceRoot.child(roomId).removeEventListener(listener)
    }

    // In PresenceRepository
    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun getOnlineCount(roomId: String): Int =
        suspendCancellableCoroutine { cont ->
            val ref = presenceRoot.child(roomId)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val count = snapshot.childrenCount.toInt()
                    cont.resume(count) {}
                }

                override fun onCancelled(error: DatabaseError) {
                    cont.resume(0) {} // treat as 0 on error
                }
            }
            ref.addListenerForSingleValueEvent(listener)
            cont.invokeOnCancellation { ref.removeEventListener(listener) }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun kickUser(roomId: String, userId: String): Result<Unit> = suspendCancellableCoroutine { cont ->
        // To kick a user, we simply remove their presence node.
        // Their app listens to this node (via startReconnectionMonitor logic or similar)
        // and should detect they are no longer in the room.
        // However, the current client logic relies on `userStatusRoot` for "am I in a room".
        // A robust kick would also clear their `userStatusRoot` entry.
        
        val roomRef = presenceRoot.child(roomId).child(userId)
        val statusRef = userStatusRoot.child(userId).child("currentRoomId")
        
        roomRef.removeValue().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Also try to clear their status (best effort, might fail if rules prevent writing others' status)
                // Assuming admin/host has write access or rules allow it.
                statusRef.removeValue()
                if (cont.isActive) cont.resume(Result.success(Unit)) {}
            } else {
                if (cont.isActive) cont.resume(Result.failure(task.exception ?: Exception("Unknown error"))) {}
            }
        }
    }
}
