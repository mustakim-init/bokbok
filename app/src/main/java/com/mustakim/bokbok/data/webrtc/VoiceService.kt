package com.mustakim.bokbok.data.webrtc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mustakim.bokbok.R

class VoiceService : Service() {

    companion object {
        const val ACTION_START = "bokbok.voice.START"
        const val ACTION_STOP = "bokbok.voice.STOP"
        const val ACTION_SET_MUTED = "bokbok.voice.SET_MUTED"
        const val ACTION_CONNECT_TO = "bokbok.voice.CONNECT_TO"

        const val EXTRA_ROOM_ID = "roomId"
        const val EXTRA_SELF_ID = "selfId"
        const val EXTRA_MUTED = "muted"
        const val EXTRA_REMOTE_IDS = "remoteIds"

        const val ACTION_SET_SPEAKER = "bokbok.voice.SET_SPEAKER"
        const val EXTRA_SPEAKER_ON = "speakerOn"

        const val ACTION_DISCONNECT_FROM = "bokbok.voice.DISCONNECT_FROM"



        fun start(context: Context, roomId: String, selfId: String) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_SELF_ID, selfId)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, VoiceService::class.java).apply { action = ACTION_STOP }
            ContextCompat.startForegroundService(context, i)
        }

        fun setSpeaker(context: Context, on: Boolean) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_SET_SPEAKER
                putExtra(EXTRA_SPEAKER_ON, on)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun setMuted(context: Context, muted: Boolean) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_SET_MUTED
                putExtra(EXTRA_MUTED, muted)
            }
            ContextCompat.startForegroundService(context, i)
        }


        fun connectTo(context: Context, ids: List<String>) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_CONNECT_TO
                putStringArrayListExtra(EXTRA_REMOTE_IDS, ArrayList(ids))
            }
            ContextCompat.startForegroundService(context, i)
        }
    }

    private val tag = "VoiceService"
    private val channelId = "voice_call_channel_01"
    private val notificationId = 101

    private var wakeLock: PowerManager.WakeLock? = null
    private var client: WebRTCClient? = null
    private var signaling: SignalingBackend? = null
    private var currentRoomId: String? = null
    private var currentSelfId: String? = null
    private var audioRouter: AudioRouteController? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start foreground first, then attach signaling to avoid background throttling
        startAsForeground()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val roomId = intent.getStringExtra(EXTRA_ROOM_ID)
                val selfId = intent.getStringExtra(EXTRA_SELF_ID)
                if (roomId != null && selfId != null) startCall(roomId, selfId)
            }
            ACTION_STOP -> {
                stopCall()
                stopSelf()
            }
            ACTION_SET_MUTED -> {
                val muted = intent.getBooleanExtra(EXTRA_MUTED, false)
                client?.setAudioEnabled(!muted)
            }
            ACTION_CONNECT_TO -> {
                val ids = intent.getStringArrayListExtra(EXTRA_REMOTE_IDS) ?: arrayListOf()
                ids.forEach { id -> client?.createConnectionTo(id) }
            }
            ACTION_SET_SPEAKER -> {
                val on = intent.getBooleanExtra(EXTRA_SPEAKER_ON, true)
                audioRouter?.setSpeakerEnabled(on)
            }
            // NEW: drop specific peers
            ACTION_DISCONNECT_FROM -> {
                val ids = intent.getStringArrayListExtra(EXTRA_REMOTE_IDS) ?: arrayListOf()
                ids.forEach { id -> client?.disconnectFrom(id) }
            }
        }
        return START_STICKY
    }

    private fun startCall(roomId: String, selfId: String) {
        if (client != null && currentRoomId == roomId && currentSelfId == selfId) return
        stopCall()
        currentRoomId = roomId
        currentSelfId = selfId

        com.mustakim.bokbok.state.ConnectionStateManager.clear()

        audioRouter = audioRouter ?: AudioRouteController(applicationContext)
        audioRouter?.start(defaultToSpeaker = true)

        signaling = RealtimeSignaling(roomId, selfId)
        client = WebRTCClient(
            context = applicationContext,
            signalingBackend = signaling!!,
            selfId = selfId,
            roomId = roomId
        ).also { webrtc ->
            webrtc.onSpeakingStateChanged = { speakingMap ->
                val speakingIds = speakingMap.filterValues { it }.keys.toSet()
                com.mustakim.bokbok.state.SpeakingStateManager.updateSpeakingIds(speakingIds)
            }
            webrtc.onPeerConnectionStateChanged = { remoteId, connected ->
                if (connected) {
                    com.mustakim.bokbok.state.ConnectionStateManager.markConnected(remoteId)
                } else {
                    com.mustakim.bokbok.state.ConnectionStateManager.markDisconnected(remoteId)
                }
            }
            webrtc.connect()
        }

        Log.d(tag, "VoiceService started call room=$roomId self=$selfId (RTDB signaling)")
    }

    private fun stopCall() {
        try {
            client?.disconnect()
        } catch (_: Exception) { }
        client = null
        signaling = null
        currentRoomId = null
        currentSelfId = null

        audioRouter?.stop()
        audioRouter = null

        Log.d(tag, "VoiceService stopped call")
    }

    private fun startAsForeground() {
        val notifIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pi = PendingIntent.getActivity(
            this, 0, notifIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("In a voice room")
            .setContentText("Tap to return to call")
            .setSmallIcon(R.drawable.ic_notifications_24)
            .setOngoing(true)
            .setContentIntent(pi)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                "Voice Call",
                NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(ch)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "bokbok:voice_call_wl")
        if (wakeLock?.isHeld != true) wakeLock?.acquire(60*60*1000L /*10 minutes*/)
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        stopCall()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
