package com.mustakim.bokbok.data.webrtc

import android.content.Context

object CallController {

    fun startCall(context: Context, roomId: String, selfId: String) {
        VoiceService.start(context.applicationContext, roomId, selfId)
    }

    fun endCall(context: Context? = null) {
        // Optional: require context to stop explicitly
        context?.let { VoiceService.stop(it.applicationContext) }
    }

    fun setMuted(muted: Boolean, context: Context? = null) {
        context?.let { VoiceService.setMuted(it.applicationContext, muted) }
    }

    fun connectToParticipants(context: Context, remoteUserIds: List<String>) {
        if (remoteUserIds.isEmpty()) return
        VoiceService.connectTo(context.applicationContext, remoteUserIds)
    }
}
