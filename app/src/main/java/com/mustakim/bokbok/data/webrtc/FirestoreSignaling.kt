package com.mustakim.bokbok.data.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.webrtc.IceCandidate
import java.util.Collections

@Deprecated("Use RTDB instead")
class FirestoreSignaling(
    private val roomId: String,
    private val selfId: String
) : SignalingBackend {

    private val tag = "FirestoreSignaling"

    // In-memory set to avoid double-processing while this instance is alive
    private val processedIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val db = FirebaseFirestore.getInstance()
    private val signalsRef = db.collection("rooms")
        .document(roomId)
        .collection("signals")

    private var listener: ListenerRegistration? = null

    override fun sendOffer(to: String?, sdp: String) {
        val data = mapOf(
            "from" to selfId,
            "to" to to,
            "type" to "offer",
            "sdp" to sdp,
            "timestamp" to System.currentTimeMillis()
        )
        Log.d(tag, "sendOffer to=$to, sdpLength=${sdp.length}")
        signalsRef.add(data)
            .addOnSuccessListener {
                Log.d(tag, "sendOffer succeeded, docId=${it.id}")
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
            "timestamp" to System.currentTimeMillis()
        )
        Log.d(tag, "sendAnswer to=$to, sdpLength=${sdp.length}")
        signalsRef.add(data)
            .addOnSuccessListener {
                Log.d(tag, "sendAnswer succeeded, docId=${it.id}")
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
            "timestamp" to System.currentTimeMillis()
        )
        Log.d(
            tag,
            "sendIceCandidate to=$to, mid=${candidate.sdpMid}, mLine=${candidate.sdpMLineIndex}"
        )
        signalsRef.add(data)
            .addOnSuccessListener {
                Log.d(tag, "sendIceCandidate succeeded, docId=${it.id}")
            }
            .addOnFailureListener { e ->
                Log.e(tag, "sendIceCandidate failed", e)
            }
    }

    override fun observeSignals(onSignal: (SignalMessage) -> Unit) {
        Log.d(tag, "observeSignals() for selfId=$selfId, roomId=$roomId")

        listener = signalsRef
            // Remove the sessionStart filter
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                Log.d(tag, "Signals snapshot: changes=${snapshot.documentChanges.size}")

                for (docChange in snapshot.documentChanges) {
                    val doc = docChange.document

                    // Skip messages we've already processed in this session
                    if (!processedIds.add(doc.id)) {
                        Log.d(tag, "Skip already-processed docId=${doc.id}")
                        continue
                    }

                    val data = doc.data

                    val from = data["from"] as? String ?: continue
                    val to = data["to"] as? String
                    val type = data["type"] as? String ?: continue

                    if (from == selfId) {
                        Log.d(tag, "Skipping own message from=$from type=$type")
                        continue
                    }

                    if (to != null && to != selfId) {
                        Log.d(tag, "Skipping message not for me: to=$to type=$type")
                        continue
                    }

                    val signal = when (type) {
                        "offer", "answer" -> {
                            val sdp = data["sdp"] as? String
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
                            val sdpMid = data["sdpMid"] as? String ?: continue
                            val sdpMLineIndex = (data["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                            val cand = data["candidate"] as? String ?: continue

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
                            continue
                        }
                    }

                    // Deliver to WebRTC
                    onSignal(signal)

                    // Delete after handling so it never affects future sessions
                    doc.reference.delete()
                        .addOnSuccessListener {
                            Log.d(tag, "Deleted handled signal docId=${doc.id}")
                        }
                        .addOnFailureListener { e ->
                            Log.w(tag, "Failed to delete handled signal ${doc.id}: ${e.message}")
                        }
                }
            }
    }

    override fun dispose() {
        listener?.remove()
        listener = null

        // Best-effort cleanup of any leftover signals involving this user
        try {
            // Delete docs where this user is the sender
            signalsRef
                .whereEqualTo("from", selfId)
                .get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) return@addOnSuccessListener
                    val batch = db.batch()
                    for (doc in snap.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(tag, "Cleaned up signals sent by $selfId")
                        }
                        .addOnFailureListener { e ->
                            Log.w(tag, "Failed to clean up sent signals: ${e.message}")
                        }
                }

            // Delete docs where this user is the receiver
            signalsRef
                .whereEqualTo("to", selfId)
                .get()
                .addOnSuccessListener { snap ->
                    if (snap.isEmpty) return@addOnSuccessListener
                    val batch = db.batch()
                    for (doc in snap.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            Log.d(tag, "Cleaned up signals addressed to $selfId")
                        }
                        .addOnFailureListener { e ->
                            Log.w(tag, "Failed to clean up received signals: ${e.message}")
                        }
                }
        } catch (e: Exception) {
            Log.w(tag, "dispose cleanup error: ${e.message}")
        }

        Log.d(tag, "FirestoreSignaling disposed for selfId=$selfId roomId=$roomId")
    }
}
