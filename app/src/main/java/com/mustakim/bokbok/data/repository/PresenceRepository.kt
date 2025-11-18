package com.mustakim.bokbok.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.suspendCancellableCoroutine

class PresenceRepository {

    private val db = FirebaseDatabase.getInstance()
    private val presenceRoot = db.getReference("presence")
    private val auth = FirebaseAuth.getInstance()

    private fun uid(): String =
        auth.currentUser?.uid ?: error("Not logged in")

    // Session join: only RTDB
    fun joinCall(roomId: String) {
        val ref = presenceRoot.child(roomId).child(uid())
        ref.setValue(true)
        ref.onDisconnect().removeValue()
    }

    fun leaveCall(roomId: String) {
        val ref = presenceRoot.child(roomId).child(uid())
        // This also cancels the onDisconnect handler
        ref.onDisconnect().cancel()
        ref.removeValue()
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
