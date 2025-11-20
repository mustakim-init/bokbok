package com.mustakim.bokbok.data.webrtc

import android.util.Log
import com.google.firebase.database.*
import org.webrtc.IceCandidate
import java.util.Collections
import java.util.LinkedHashMap

class RealtimeSignaling(
    private val roomId: String,
    private val selfId: String
) : SignalingBackend {

    private val tag = "RealtimeSignaling"

    private val db: FirebaseDatabase = FirebaseDatabase.getInstance()
    // signals/{roomId}/{autoId} = { type, from, to, ... }
    private val signalsRef: DatabaseReference = db
        .getReference("signals")
        .child(roomId)

    private var listener: ChildEventListener? = null

    // LRU Cache to prevent memory leak (max 1000 items)
    private val processedKeys = Collections.synchronizedSet(
        Collections.newSetFromMap(
            object : LinkedHashMap<String, Boolean>(1000, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                    return size > 1000
                }
            }
        )
    )

    override fun sendOffer(to: String?, sdp: String) {
        val data = mapOf(
            "from" to selfId,
            "to" to to, // nullable = broadcast, same as Firestore version
            "type" to "offer",
            "sdp" to sdp,
            "timestamp" to ServerValue.TIMESTAMP
        )
        Log.d(tag, "sendOffer to=$to, sdpLength=${sdp.length}")
        val node = signalsRef.push()
        node.setValue(data)
            .addOnSuccessListener {
                Log.d(tag, "sendOffer succeeded, key=${node.key}")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "sendOffer failed", e)
            }
    }

    override fun sendAnswer(to: String, sdp: String) {
        val data = mapOf(
            "from" to selfId,
            "to" to to,
            "type" to "answer",
            "sdp" to sdp,
            "timestamp" to ServerValue.TIMESTAMP
        )
        Log.d(tag, "sendAnswer to=$to, sdpLength=${sdp.length}")
        val node = signalsRef.push()
        node.setValue(data)
            .addOnSuccessListener {
                Log.d(tag, "sendAnswer succeeded, key=${node.key}")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "sendAnswer failed", e)
            }
    }

    override fun sendIceCandidate(to: String, candidate: IceCandidate) {
        val data = mapOf(
            "from" to selfId,
            "to" to to,
            "type" to "ice",
            "sdpMid" to candidate.sdpMid,
            "sdpMLineIndex" to candidate.sdpMLineIndex,
            "candidate" to candidate.sdp,
            "timestamp" to ServerValue.TIMESTAMP
        )
        Log.d(
            tag,
            "sendIceCandidate to=$to, mid=${candidate.sdpMid}, mLine=${candidate.sdpMLineIndex}"
        )
        val node = signalsRef.push()
        node.setValue(data)
            .addOnSuccessListener {
                Log.d(tag, "sendIceCandidate succeeded, key=${node.key}")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "sendIceCandidate failed", e)
            }
    }

    override fun observeSignals(onSignal: (SignalMessage) -> Unit) {
        Log.d(tag, "observeSignals() for selfId=$selfId, roomId=$roomId")

        if (listener != null) return

        listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                handleSnapshot(snapshot, onSignal)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                // we only care about newly created signals
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                // no-op
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {
                // no-op
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(tag, "signals listener cancelled: ${error.message}")
            }
        }

        signalsRef.addChildEventListener(listener as ChildEventListener)
    }

    private fun handleSnapshot(
        snapshot: DataSnapshot,
        onSignal: (SignalMessage) -> Unit
    ) {
        val key = snapshot.key ?: return
        if (!processedKeys.add(key)) {
            Log.d(tag, "Skip already-processed key=$key")
            return
        }

        val from = snapshot.child("from").getValue(String::class.java) ?: return
        val to = snapshot.child("to").getValue(String::class.java) // nullable
        val type = snapshot.child("type").getValue(String::class.java) ?: return

        if (from == selfId) {
            Log.d(tag, "Skipping own message from=$from type=$type")
            return
        }

        if (to != null && to != selfId) {
            Log.d(tag, "Skipping message not for me: to=$to type=$type")
            return
        }

        val signal = when (type) {
            "offer", "answer" -> {
                val sdp = snapshot.child("sdp").getValue(String::class.java)
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                
                // Ignore stale signals (> 60 seconds old)
                if (System.currentTimeMillis() - timestamp > 60_000) {
                    Log.d(tag, "Ignoring stale $type from=$from (age=${System.currentTimeMillis() - timestamp}ms)")
                    return
                }

                Log.d(
                    tag,
                    "Received $type from=$from, to=$to, sdpLength=${sdp?.length ?: 0}"
                )
                SignalMessage(
                    from = from,
                    to = to,
                    type = type,
                    sdp = sdp
                )
            }

            "ice" -> {
                val sdpMid = snapshot.child("sdpMid").getValue(String::class.java) ?: return
                val mLineIndexLong = snapshot.child("sdpMLineIndex").getValue(Long::class.java)
                val sdpMLineIndex = mLineIndexLong?.toInt() ?: 0
                val cand = snapshot.child("candidate").getValue(String::class.java) ?: return
                val timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L

                // ICE candidates expire faster (30s)
                if (System.currentTimeMillis() - timestamp > 30_000) {
                    return
                }

                Log.d(
                    tag,
                    "Received ICE from=$from, to=$to, mid=$sdpMid, mLine=$sdpMLineIndex"
                )

                val ice = IceCandidate(sdpMid, sdpMLineIndex, cand)
                SignalMessage(
                    from = from,
                    to = to,
                    type = type,
                    candidate = ice
                )
            }

            else -> {
                Log.d(tag, "Unknown signal type=$type from=$from")
                return
            }
        }

        onSignal(signal)
        // REMOVED: Immediate deletion. We rely on dispose() to clean up.
    }

    override fun dispose() {
        listener?.let {
            signalsRef.removeEventListener(it)
        }
        listener = null


        try {
            // Delete signals sent by this user
            signalsRef.orderByChild("from").equalTo(selfId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        snap.children.forEach { it.ref.removeValue() }
                        Log.d(tag, "Cleaned up signals sent by $selfId")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.w(tag, "Cleanup 'from' cancelled: ${error.message}")
                    }
                })

            // Delete signals addressed to this user
            signalsRef.orderByChild("to").equalTo(selfId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snap: DataSnapshot) {
                        snap.children.forEach { it.ref.removeValue() }
                        Log.d(tag, "Cleaned up signals addressed to $selfId")
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Log.w(tag, "Cleanup 'to' cancelled: ${error.message}")
                    }
                })
        } catch (e: Exception) {
            Log.w(tag, "dispose cleanup error: ${e.message}")
        }

        processedKeys.clear()
        Log.d(tag, "RealtimeSignaling disposed for selfId=$selfId roomId=$roomId")
    }
}
