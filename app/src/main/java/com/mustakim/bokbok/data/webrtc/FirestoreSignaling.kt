package com.mustakim.bokbok.data.webrtc

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import org.webrtc.IceCandidate
import java.util.Collections


class FirestoreSignaling(
    private val roomId: String,
    private val selfId: String
) : SignalingBackend {

    private val TAG = "FirestoreSignaling"


    private val sessionStart = System.currentTimeMillis()
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
        Log.d(TAG, "sendOffer to=$to, sdpLength=${sdp.length}")
        signalsRef.add(data)
            .addOnSuccessListener {
                Log.d(TAG, "sendOffer succeeded, docId=${it.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "sendOffer failed", e)
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
        Log.d(TAG, "sendAnswer to=$to, sdpLength=${sdp.length}")
        signalsRef.add(data)
            .addOnSuccessListener {
                Log.d(TAG, "sendAnswer succeeded, docId=${it.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "sendAnswer failed", e)
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
            TAG,
            "sendIceCandidate to=$to, mid=${candidate.sdpMid}, mLine=${candidate.sdpMLineIndex}"
        )
        signalsRef.add(data)
            .addOnSuccessListener {
                Log.d(TAG, "sendIceCandidate succeeded, docId=${it.id}")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "sendIceCandidate failed", e)
            }
    }

    override fun observeSignals(onSignal: (SignalMessage) -> Unit) {
        Log.d(TAG, "observeSignals() for selfId=$selfId, roomId=$roomId")

        listener = signalsRef
            .whereGreaterThan("timestamp", sessionStart)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "listen failed", error)
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) return@addSnapshotListener

                Log.d(TAG, "Signals snapshot: changes=${snapshot.documentChanges.size}")

                for (docChange in snapshot.documentChanges) {
                    val doc = docChange.document

                    // Skip messages we've already processed in this session
                    if (!processedIds.add(doc.id)) {
                        Log.d(TAG, "Skip already-processed docId=${doc.id}")
                        continue
                    }

                    val data = doc.data

                    val from = data["from"] as? String ?: continue
                    val to = data["to"] as? String
                    val type = data["type"] as? String ?: continue

                    if (from == selfId) {
                        Log.d(TAG, "Skipping own message from=$from type=$type")
                        continue
                    }

                    if (to != null && to != selfId) {
                        Log.d(TAG, "Skipping message not for me: to=$to type=$type")
                        continue
                    }

                    val signal = when (type) {
                        "offer", "answer" -> {
                            val sdp = data["sdp"] as? String
                            Log.d(TAG, "Received $type from=$from, to=$to, sdpLength=${sdp?.length ?: 0}")
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

                            Log.d(TAG, "Received ICE from=$from, to=$to, mid=$sdpMid, mLine=$sdpMLineIndex")

                            val ice = IceCandidate(sdpMid, sdpMLineIndex, cand)
                            SignalMessage(
                                from = from,
                                to = to,
                                type = type,
                                candidate = ice
                            )
                        }

                        else -> {
                            Log.d(TAG, "Unknown signal type=$type from=$from")
                            continue
                        }
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
