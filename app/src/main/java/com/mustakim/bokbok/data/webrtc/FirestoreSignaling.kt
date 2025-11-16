package com.mustakim.bokbok.data.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.webrtc.IceCandidate

class FirestoreSignaling(
    private val roomId: String,
    private val selfId: String
) : SignalingBackend {

    private val db = FirebaseFirestore.getInstance()
    private val signalsRef = db.collection("rooms")
        .document(roomId)
        .collection("signals")

    private var listener: ListenerRegistration? = null

    override fun sendOffer(to: String?, sdp: String) {
        val data = mapOf(
            "from" to selfId,
            "to" to to,            // null = broadcast to all
            "type" to "offer",
            "sdp" to sdp,
            "timestamp" to System.currentTimeMillis()
        )
        signalsRef.add(data)
            .addOnFailureListener { e ->
                Log.e("FirestoreSignaling", "sendOffer failed", e)
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
        signalsRef.add(data)
            .addOnFailureListener { e ->
                Log.e("FirestoreSignaling", "sendAnswer failed", e)
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
        signalsRef.add(data)
            .addOnFailureListener { e ->
                Log.e("FirestoreSignaling", "sendIceCandidate failed", e)
            }
    }

    override fun observeSignals(onSignal: (SignalMessage) -> Unit) {
        // Listen only to signals intended for self or broadcast (to == null)
        listener = signalsRef
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("FirestoreSignaling", "listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                for (doc in snapshot.documentChanges) {
                    val data = doc.document.data

                    val from = data["from"] as? String ?: continue
                    val to = data["to"] as? String
                    val type = data["type"] as? String ?: continue

                    // Ignore messages sent by self
                    if (from == selfId) continue

                    // If 'to' is set and not selfId, ignore
                    if (to != null && to != selfId) continue

                    val signal = when (type) {
                        "offer", "answer" -> {
                            val sdp = data["sdp"] as? String
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

                            val ice = IceCandidate(sdpMid, sdpMLineIndex, cand)
                            SignalMessage(
                                from = from,
                                to = to,
                                type = type,
                                candidate = ice
                            )
                        }

                        else -> continue
                    }

                    onSignal(signal)
                }
            }
    }

    override fun dispose() {
        listener?.remove()
        listener = null
    }
}
