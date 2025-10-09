package com.mustakim.bokbok

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.webrtc.IceCandidate
import org.webrtc.SessionDescription
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Firebase signaling (verbose).
 * - Logs writes and reads
 * - Keeps onDisconnect removal
 * - Exposes getParticipantsNow()
 */
class FirebaseSignaling(private val roomId: String) {
    private val TAG = "FirebaseSignaling"
    private val db: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance("https://bokbok-8b961-default-rtdb.asia-southeast1.firebasedatabase.app/")
    }
    private val roomRef = db.getReference("rooms").child(roomId)
    private val participantsRef = roomRef.child("participants")
    private val messagesRef = roomRef.child("messages")

    private val auth = FirebaseAuth.getInstance()
    @Volatile var localId: String? = null
        private set

    private val authReadyCallbacks = CopyOnWriteArrayList<() -> Unit>()
    private var messagesListener: ChildEventListener? = null
    private var participantsListener: ValueEventListener? = null

    private var messageSequence = 0L
    private val messageProcessing = AtomicBoolean(false)
    private var retryCount = 0
    private val maxRetries = 3
    private var onMessage: ((Type, String, Map<String, Any?>, String) -> Unit)? = null
    private var onParticipantsChanged: ((List<String>) -> Unit)? = null

    init { ensureAuth() }

    private fun ensureAuth() {
        val u = auth.currentUser
        if (u != null) {
            localId = u.uid
            Log.d(TAG, "Auth already present: $localId")
            flushAuthReady()
            return
        }
        auth.signInAnonymously().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                localId = auth.currentUser?.uid
                Log.d(TAG, "Anonymous auth ready: $localId")
            } else {
                Log.e(TAG, "Anonymous auth failed: ${task.exception?.message}")
            }
            flushAuthReady()
        }
    }

    private fun flushAuthReady() {
        for (cb in authReadyCallbacks) {
            try { cb() } catch (e: Exception) { Log.w(TAG, "authReady cb fail: ${e.message}") }
        }
        authReadyCallbacks.clear()
    }

    fun whenAuthReady(cb: () -> Unit) {
        if (localId != null) cb() else authReadyCallbacks.add(cb)
    }

    /**
     * Start listeners (must be called once). onMessage receives (type, fromId, payload, messageKey).
     */
    fun start(onMessageCb: (Type, String, Map<String, Any?>, String) -> Unit) {
        onMessage = onMessageCb

        whenAuthReady {
            try {
                // messages listener
                messagesListener = messagesRef.addChildEventListener(object : ChildEventListener {
                    // In FirebaseSignaling.kt - REPLACE the entire onChildAdded method
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                        val currentLocalId = localId
                        if (currentLocalId == null) {
                            Log.w(TAG, "localId still null when processing message, ignoring")
                            return
                        }

                        val key = snapshot.key ?: return
                        val map = snapshot.value as? Map<*, *> ?: return
                        val typeStr = map["type"] as? String ?: return
                        val from = map["from"] as? String ?: return
                        val to = (map["to"] as? String?)

                        // Check message sequence for ordering
                        val seq = (map["seq"] as? Long) ?: 0L
                        if (seq < messageSequence && (typeStr == "sdp" || typeStr == "ice")) {
                            Log.d(TAG, "Ignoring outdated message $key with seq=$seq (current=$messageSequence)")
                            return
                        }

                        // Filter self-messages differently based on type
                        when (typeStr) {
                            "sdp", "ice" -> {
                                if (from == currentLocalId) {
                                    Log.d(TAG, "Ignoring own signaling message $key from $from")
                                    try { messagesRef.child(key).removeValue() } catch (_: Exception) {}
                                    return
                                }
                            }
                            "join", "leave" -> {
                                // Don't filter join/leave messages, but skip self-peer creation
                                if (from == currentLocalId) {
                                    Log.d(TAG, "Received own $typeStr message, skipping peer processing")
                                    // Don't return here - we still want to process the message for UI updates
                                }
                            }
                        }

                        Log.d(TAG, "Received message key=$key type=$typeStr from=$from to=$to")

                        // If message targeted to someone else, ignore
                        if (to != null && to != currentLocalId) {
                            Log.d(TAG, "Message $key ignored (to=$to != me=$currentLocalId)")
                            return
                        }

                        val payload = mutableMapOf<String, Any?>()
                        for ((k, v) in map) if (k is String) payload[k] = v

                        val type = when (typeStr) {
                            "sdp" -> Type.SDP
                            "ice" -> Type.ICE
                            "join" -> Type.JOIN
                            "leave" -> Type.LEAVE
                            else -> null
                        } ?: return

                        try {
                            onMessageCb(type, from, payload, key)

                            // Auto-delete SDP/ICE messages after processing to prevent replay
                            if (type == Type.SDP || type == Type.ICE) {
                                try {
                                    messagesRef.child(key).removeValue()
                                    Log.d(TAG, "Auto-deleted processed $type message $key")
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to delete message: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "onMessageCb error: ${e.message}")
                        }
                    }
                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onChildRemoved(snapshot: DataSnapshot) {}
                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "messages listener cancelled: ${error.message}")
                    }
                })

                // participants listener with null check
                participantsListener = participantsRef.addValueEventListener(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val currentLocalId = localId
                        if (currentLocalId == null) {
                            Log.w(TAG, "localId null when processing participants, ignoring")
                            return
                        }

                        val ids = mutableListOf<String>()
                        for (ch in snapshot.children) {
                            ch.key?.let { id ->
                                if (id != currentLocalId) ids.add(id)
                            }
                        }
                        Log.d(TAG, "participants changed: ${ids.size} other(s)")
                        onParticipantsChanged?.invoke(ids)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(TAG, "participants listener cancelled: ${error.message}")
                    }
                })

                // enable cleanup and onDisconnect
                enableAutoCleanup()

            } catch (e: Exception) {
                Log.e(TAG, "start() setup failed: ${e.message}", e)
            }
        }
    }

    fun onParticipantsChanged(cb: (List<String>) -> Unit) { onParticipantsChanged = cb }

    fun sendSdp(toId: String, sdp: SessionDescription) {
        val my = localId ?: run {
            if (retryCount < maxRetries) {
                retryCount++
                Log.w(TAG, "sendSdp: no localId - retrying in 1s (attempt $retryCount)")
                Handler(Looper.getMainLooper()).postDelayed({
                    sendSdp(toId, sdp)
                }, 1000)
            } else {
                Log.e(TAG, "sendSdp: max retries reached, giving up")
                retryCount = 0
            }
            return
        }
        retryCount = 0 // Reset on success
        val msg = HashMap<String, Any?>()
        msg["type"] = "sdp"
        msg["from"] = my
        msg["to"] = toId
        msg["sdpType"] = sdp.type.canonicalForm()
        msg["sdp"] = sdp.description
        msg["ts"] = ServerValue.TIMESTAMP
        msg["seq"] = messageSequence

        val push = messagesRef.push()
        push.setValue(msg).addOnCompleteListener { t ->
            if (t.isSuccessful) Log.d(TAG, "Sent SDP to $toId (key=${push.key}, seq=${msg["seq"]})")
            else Log.e(TAG, "Failed to send SDP to $toId: ${t.exception?.message}")
        }

        // Increment after setting to ensure consistency
        messageSequence++
    }

    fun sendIceCandidate(toId: String, c: IceCandidate) {
        val my = localId ?: run {
            messageSequence++ // Still increment to maintain sequence even if failed
            Log.w(TAG, "sendIce: no localId - retrying in 1s")
            Handler(Looper.getMainLooper()).postDelayed({
                sendIceCandidate(toId, c)
            }, 1000)
            return
        }
        val msg = HashMap<String, Any?>()
        msg["type"] = "ice"
        msg["from"] = my
        msg["to"] = toId
        msg["candidate"] = c.sdp
        msg["sdpMid"] = c.sdpMid
        msg["sdpMLineIndex"] = c.sdpMLineIndex
        msg["ts"] = ServerValue.TIMESTAMP
        msg["seq"] = messageSequence
        val push = messagesRef.push()
        push.setValue(msg).addOnCompleteListener { t ->
            if (t.isSuccessful) Log.d(TAG, "Sent ICE to $toId (key=${push.key})")
            else Log.e(TAG, "Failed to send ICE to $toId: ${t.exception?.message}")
        }

        // Increment after setting to ensure consistency
        messageSequence++
    }

    fun shouldInitiateTo(remoteId: String): Boolean {
        val my = localId ?: return false
        return my < remoteId
    }

    fun join(): Boolean {
        val my = localId ?: run {
            Log.w(TAG, "join: no localId")
            return false
        }

        Log.d(TAG, "Joining room $roomId as $my")

        // First write to participants, then broadcast join
        val ref = participantsRef.child(my)
        ref.setValue(ServerValue.TIMESTAMP).addOnCompleteListener { t ->
            if (t.isSuccessful) {
                Log.d(TAG, "participantsRef write ok for $my")

                // Set up disconnect cleanup
                try {
                    participantsRef.child(my).onDisconnect().removeValue()
                } catch (e: Exception) {
                    Log.w(TAG, "onDisconnect fail: ${e.message}")
                }

                // Wait a bit then check participants
                Handler(Looper.getMainLooper()).postDelayed({
                    getParticipantsNow { participants ->
                        Log.d(TAG, "Initial room participants: ${participants.size}")
                        // Notify about participants (excluding self)
                        onParticipantsChanged?.invoke(participants.filter { it != my })
                    }
                }, 3000)

            } else {
                Log.e(TAG, "Failed to write participant $my: ${t.exception?.message}")
            }
        }

        // Broadcast join message after a delay to ensure participant is registered
        Handler(Looper.getMainLooper()).postDelayed({
            val msg = HashMap<String, Any?>()
            msg["type"] = "join"
            msg["from"] = my
            msg["to"] = null
            msg["ts"] = ServerValue.TIMESTAMP
            messagesRef.push().setValue(msg).addOnCompleteListener { t ->
                if (t.isSuccessful) Log.d(TAG, "Broadcasted join for $my")
                else Log.e(TAG, "Failed to broadcast join: ${t.exception?.message}")
            }
        }, 2000)

        return true
    }

    fun leave() {
        val my = localId ?: return
        try { participantsRef.child(my).removeValue() } catch (_: Exception) {}
        val msg = HashMap<String, Any?>()
        msg["type"] = "leave"
        msg["from"] = my
        msg["to"] = null
        msg["ts"] = ServerValue.TIMESTAMP
        messagesRef.push().setValue(msg)
        Log.d(TAG, "Left room: $my")
    }

    fun getParticipantsNow(cb: (List<String>) -> Unit) {
        whenAuthReady {
            participantsRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val ids = mutableListOf<String>()
                    val me = localId
                    for (ch in snapshot.children) ch.key?.let { if (it != me) ids.add(it) }
                    cb(ids)
                }
                override fun onCancelled(error: DatabaseError) { cb(emptyList()) }
            })
        }
    }

    fun deleteMessage(key: String) {
        try { messagesRef.child(key).removeValue().addOnCompleteListener { t -> if (!t.isSuccessful) Log.w(TAG, "deleteMessage failed: ${t.exception?.message}") } }
        catch (e: Exception) { Log.w(TAG, "deleteMessage exc: ${e.message}") }
    }

    fun enableAutoCleanup(maxAgeMs: Long = 60_000L, cleanupIntervalMs: Long = 120_000L) {
        try {
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val runnable = object : Runnable {
                override fun run() {
                    try { cleanupOldMessages(maxAgeMs) } catch (_: Exception) {}
                    handler.postDelayed(this, cleanupIntervalMs)
                }
            }
            handler.postDelayed(runnable, cleanupIntervalMs)
        } catch (e: Exception) { Log.w(TAG, "enableAutoCleanup failed: ${e.message}") }

        whenAuthReady {
            try { localId?.let { participantsRef.child(it).onDisconnect().removeValue() } } catch (e: Exception) { Log.w(TAG, "onDisconnect removeValue failed: ${e.message}") }
        }
    }

    fun cleanupOldMessages(maxAgeMs: Long = 60_000L) {
        try {
            val cutoff = System.currentTimeMillis() - maxAgeMs
            messagesRef.orderByChild("ts").endAt(cutoff.toDouble()).addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    for (child in snapshot.children) {
                        try { child.ref.removeValue() } catch (_: Exception) {}
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        } catch (e: Exception) { Log.w(TAG, "cleanupOldMessages error: ${e.message}") }
    }

    fun close() {
        try { messagesListener?.let { messagesRef.removeEventListener(it) } } catch (_: Exception) {}
        try { participantsListener?.let { participantsRef.removeEventListener(it) } } catch (_: Exception) {}
    }

    enum class Type { SDP, ICE, JOIN, LEAVE }
}