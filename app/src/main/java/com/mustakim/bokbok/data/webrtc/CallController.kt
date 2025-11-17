package com.mustakim.bokbok.data.webrtc

import android.content.Context

object CallController {

    // Store app context so we can start/stop the service later
    private var appContext: Context? = null

    fun startCall(context: Context, roomId: String, selfId: String) {
        appContext = context.applicationContext
        VoiceService.start(appContext!!, roomId, selfId)
    }

    fun endCall() {
        appContext?.let { ctx ->
            VoiceService.stop(ctx)
        }
    }

    fun setMuted(muted: Boolean) {
        appContext?.let { ctx ->
            VoiceService.setMuted(ctx, muted)
        }
    }

    fun connectToParticipants(remoteUserIds: List<String>) {
        if (remoteUserIds.isEmpty()) return
        val ctx = appContext ?: return
        VoiceService.connectTo(ctx, remoteUserIds)
    }
}
