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

    @Volatile private var messageSequence = 0L
    private val sequenceLock = Any()
    private val messageProcessing = AtomicBoolean(false)
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
                        val to = map["to"] as? String?

                        // CRITICAL: Filter self-messages EARLY for all signaling types
                        if (from == currentLocalId) {
                            when (typeStr) {
                                "sdp", "ice" -> {
                                    Log.d(TAG, "Ignoring own $typeStr message $key")
                                    return
                                }
                                "join" -> {
                                    Log.d(TAG, "Received own join message - ignoring for peer creation")
                                    return
                                }
                                "leave" -> {
                                    Log.d(TAG, "Received own leave message")
                                    return
                                }
                            }
                        }

                        // REMOVE THE BROKEN SEQUENCE CHECK COMPLETELY
                        // This was causing SDP/ICE messages to be rejected incorrectly
                        // Each peer has independent messageSequence, so comparing them is wrong

                        // OPTIONAL: Add timestamp-based staleness check (if needed)
                        val ts = (map["ts"] as? Long)
                        if (ts != null) {
                            val age = System.currentTimeMillis() - ts
                            if (age > 300_000) { // 5 minutes old
                                Log.d(TAG, "Ignoring ancient message $key (age=${age}ms)")
                                return
                            }
                        }

                        Log.d(TAG, "Processing message key=$key type=$typeStr from=$from to=$to")

                        // If message targeted to someone else, ignore
                        if (to != null && to != currentLocalId) {
                            Log.d(TAG, "Message $key ignored (to=$to != me=$currentLocalId)")
                            return
                        }

                        val payload = mutableMapOf<String, Any?>()
                        for ((k, v) in map) {
                            if (k is String) payload[k] = v
                        }

                        val type = when (typeStr) {
                            "sdp" -> Type.SDP
                            "ice" -> Type.ICE
                            "join" -> Type.JOIN
                            "leave" -> Type.LEAVE
                            else -> null
                        } ?: return

                        try {
                            onMessage?.invoke(type, from, payload, key)

                            // Auto-delete processed signaling messages after small delay
                            if (type == Type.SDP || type == Type.ICE) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    try {
                                        messagesRef.child(key).removeValue()
                                        Log.d(TAG, "Auto-deleted $type message $key")
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Failed to delete message: ${e.message}")
                                    }
                                }, 500)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "onMessage callback error: ${e.message}", e)
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
        whenAuthReady {
            val my = localId ?: return@whenAuthReady

            val currentSeq = synchronized(sequenceLock) {
                val seq = messageSequence
                messageSequence++
                seq
            }

            val msg = hashMapOf<String, Any?>(
                "type" to "sdp",
                "from" to my,
                "to" to toId,
                "sdpType" to sdp.type.canonicalForm(),
                "sdp" to sdp.description,
                "ts" to ServerValue.TIMESTAMP,
                "seq" to currentSeq  // Keep for logging, not for filtering
            )

            val push = messagesRef.push()
            push.setValue(msg).addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    Log.d(TAG, "Sent SDP to $toId (key=${push.key}, seq=$currentSeq)")
                } else {
                    Log.e(TAG, "Failed to send SDP to $toId: ${t.exception?.message}")
                }
            }
        }
    }

    fun sendIceCandidate(toId: String, c: IceCandidate) {
        whenAuthReady {
            val my = localId ?: return@whenAuthReady

            val currentSeq = synchronized(sequenceLock) {
                val seq = messageSequence
                messageSequence++
                seq
            }

            val msg = hashMapOf<String, Any?>(
                "type" to "ice",
                "from" to my,
                "to" to toId,
                "candidate" to c.sdp,
                "sdpMid" to c.sdpMid,
                "sdpMLineIndex" to c.sdpMLineIndex,
                "ts" to ServerValue.TIMESTAMP,
                "seq" to currentSeq  // Keep for logging, not for filtering
            )

            val push = messagesRef.push()
            push.setValue(msg).addOnCompleteListener { t ->
                if (t.isSuccessful) {
                    Log.d(TAG, "Sent ICE to $toId (key=${push.key}, seq=$currentSeq)")
                } else {
                    Log.e(TAG, "Failed to send ICE to $toId: ${t.exception?.message}")
                }
            }
        }
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
                }, 1000)

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
        }, 800)

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
            val handler = Handler(Looper.getMainLooper())
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