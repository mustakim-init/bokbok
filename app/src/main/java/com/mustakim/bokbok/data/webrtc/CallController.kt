package com.mustakim.bokbok.data.webrtc

import android.content.Context
import android.annotation.SuppressLint


@SuppressLint("StaticFieldLeak")
object CallController {

    private var client: WebRTCClient? = null

    fun startCall(
        context: Context,
        roomId: String,
        selfId: String
    ) {
        if (client != null) return

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
    }

    fun setMuted(muted: Boolean) {
        client?.setAudioEnabled(!muted)
    }

    fun connectToParticipants(remoteUserIds: List<String>) {
        val currentClient = client ?: return
        remoteUserIds.forEach { remoteId ->
            currentClient.createConnectionTo(remoteId)
        }
    }
}
