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

    fun setMicVolume(volume: Double) {
        appContext?.let { ctx ->
            VoiceService.setMicVolume(ctx, volume)
        }
    }

    fun setRemoteVolume(userId: String, volume: Double) {
        appContext?.let { ctx ->
            VoiceService.setRemoteVolume(ctx, userId, volume)
        }
    }
}