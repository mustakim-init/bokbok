package com.mustakim.bokbok.data.webrtc

import android.content.Context
import android.annotation.SuppressLint
import android.util.Log

@SuppressLint("StaticFieldLeak")
object CallController {

    private const val TAG = "CallController"

    private var client: WebRTCClient? = null

    // Track which remote IDs we've already tried to connect to in this session
    private val attemptedIds = mutableSetOf<String>()

    fun startCall(
        context: Context,
        roomId: String,
        selfId: String
    ) {
        if (client != null) return

        attemptedIds.clear() // new session, clear attempts

        val signaling = FirestoreSignaling(roomId, selfId)
        client = WebRTCClient(
            context = context,
            signalingBackend = signaling,
            selfId = selfId,
            roomId = roomId
        ).also { it.connect() }
    }

    fun endCall() {
        client?.disconnect()
        client = null
        attemptedIds.clear()
    }

    fun setMuted(muted: Boolean) {
        client?.setAudioEnabled(!muted)
    }

    fun connectToParticipants(remoteUserIds: List<String>) {
        val currentClient = client ?: return

        // Only attempt for IDs we haven't tried in this session
        val newIds = remoteUserIds.filter { attemptedIds.add(it) }

        if (newIds.isEmpty()) {
            Log.d(TAG, "connectToParticipants: no new IDs to connect")
            return
        }

        newIds.forEach { remoteId ->
            currentClient.createConnectionTo(remoteId)
        }
    }
}
