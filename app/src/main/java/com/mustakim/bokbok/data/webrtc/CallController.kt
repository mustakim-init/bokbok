package com.mustakim.bokbok.data.webrtc

import android.content.Context

object CallController {

    // Store app context so we can start/stop the service later
    private var appContext: Context? = null

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pendingDisconnects = mutableMapOf<String, Runnable>()

    fun startCall(context: Context, roomId: String, selfId: String) {
        appContext = context.applicationContext
        VoiceService.start(appContext!!, roomId, selfId)
    }

    fun endCall() {
        appContext?.let { ctx ->
            VoiceService.stop(ctx)
        }
        // Clear any pending disconnects when the call ends
        pendingDisconnects.values.forEach { handler.removeCallbacks(it) }
        pendingDisconnects.clear()
    }

    fun setMuted(muted: Boolean) {
        appContext?.let { ctx ->
            VoiceService.setMuted(ctx, muted)
        }
    }

    fun connectToParticipants(remoteUserIds: List<String>) {
        if (remoteUserIds.isEmpty()) return
        val ctx = appContext ?: return

        // If we are reconnecting to someone who was scheduled for disconnect, cancel it
        remoteUserIds.forEach { id ->
            pendingDisconnects[id]?.let { runnable ->
                handler.removeCallbacks(runnable)
                pendingDisconnects.remove(id)
                android.util.Log.d("CallController", "Cancelled pending disconnect for $id (rejoined)")
            }
        }

        VoiceService.connectTo(ctx, remoteUserIds)
    }

    fun scheduleDisconnect(remoteUserIds: List<String>) {
        if (remoteUserIds.isEmpty()) return
        val ctx = appContext ?: return

        remoteUserIds.forEach { id ->
            // If already pending, don't schedule again (or maybe reschedule? sticking to first schedule is safer for now)
            if (pendingDisconnects.containsKey(id)) return@forEach

            val runnable = Runnable {
                pendingDisconnects.remove(id)
                android.util.Log.d("CallController", "Executing disconnect for $id after delay")
                disconnectFromParticipants(listOf(id))
            }
            pendingDisconnects[id] = runnable
            handler.postDelayed(runnable, 5000) // 5 seconds delay
        }
    }

    private fun disconnectFromParticipants(remoteUserIds: List<String>) {
        if (remoteUserIds.isEmpty()) return
        val ctx = appContext ?: return
        val intent = android.content.Intent(ctx, VoiceService::class.java).apply {
            action = VoiceService.ACTION_DISCONNECT_FROM
            putStringArrayListExtra(VoiceService.EXTRA_REMOTE_IDS, ArrayList(remoteUserIds))
        }
        androidx.core.content.ContextCompat.startForegroundService(ctx, intent)
    }
}
