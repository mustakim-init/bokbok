package com.mustakim.bokbok.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

class PresenceRepository {

    private val db = FirebaseDatabase.getInstance()
    private val presenceRoot = db.getReference("presence")

    // ✅ NEW: per-user status node
    private val userStatusRoot = db.getReference("userStatus")
    private val auth = FirebaseAuth.getInstance()

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
        val ref = userStatusRoot.child(uid())
        // Cancel the previous onDisconnect and explicitly mark offline
        ref.onDisconnect().cancel()
        ref.setValue(
            mapOf(
                "online" to false,
                "currentRoomId" to null
            )
        )
    }

    // Session join: only RTDB
    fun joinCall(roomId: String) {
        // Per-room presence
        val ref = presenceRoot.child(roomId).child(uid())
        ref.setValue(true)
        ref.onDisconnect().removeValue()

        // Per-user status: just set currentRoomId, online already handled by setUserOnline()
        val statusRef = userStatusRoot.child(uid())
        statusRef.child("currentRoomId").setValue(roomId)
    }

    fun leaveCall(roomId: String) {
        // Per-room presence
        val ref = presenceRoot.child(roomId).child(uid())
        // This also cancels the onDisconnect handler
        ref.onDisconnect().cancel()
        ref.removeValue()

        // Per-user status: clear currentRoomId, keep online = true
        val statusRef = userStatusRoot.child(uid())
        statusRef.child("currentRoomId").setValue(null)
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
    suspend fun canJoin(roomId: String, maxParticipants: Int): Boolean =
        suspendCancellableCoroutine { cont ->
            presenceRoot.child(roomId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val count = snapshot.childrenCount.toInt()
                        cont.resume(count < maxParticipants) {}
                    }

                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(false) {}
                    }
                })
        }

}
