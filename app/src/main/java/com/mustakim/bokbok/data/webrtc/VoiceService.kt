package com.mustakim.bokbok.data.webrtc

import android.annotation.SuppressLint
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
import android.net.wifi.WifiManager
import com.google.firebase.database.ValueEventListener
import com.mustakim.bokbok.data.repository.PresenceRepository

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


        // 🔊 NEW: toggle between A2DP (music) and SCO (call) mode
        const val ACTION_SET_A2DP_MODE = "bokbok.voice.SET_A2DP_MODE"
        const val EXTRA_A2DP_ON = "a2dpOn"

        const val ACTION_SET_QUALITY_MODE = "bokbok.voice.SET_QUALITY_MODE"
        const val EXTRA_HIGH_QUALITY = "highQuality"


        fun setQualityMode(context: Context, highQuality: Boolean) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_SET_QUALITY_MODE
                putExtra(EXTRA_HIGH_QUALITY, highQuality)
            }
            ContextCompat.startForegroundService(context, i)
        }

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

        fun setA2dpMode(context: Context, on: Boolean) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_SET_A2DP_MODE
                putExtra(EXTRA_A2DP_ON, on)
            }
            ContextCompat.startForegroundService(context, i)
        }

        const val ACTION_SET_MIC_VOLUME = "bokbok.voice.SET_MIC_VOLUME"
        const val ACTION_SET_REMOTE_VOLUME = "bokbok.voice.SET_REMOTE_VOLUME"
        const val EXTRA_VOLUME = "volume"
        const val EXTRA_REMOTE_ID = "remoteId"

        fun setMicVolume(context: Context, volume: Double) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_SET_MIC_VOLUME
                putExtra(EXTRA_VOLUME, volume)
            }
            ContextCompat.startForegroundService(context, i)
        }

        fun setRemoteVolume(context: Context, userId: String, volume: Double) {
            val i = Intent(context, VoiceService::class.java).apply {
                action = ACTION_SET_REMOTE_VOLUME
                putExtra(EXTRA_REMOTE_ID, userId)
                putExtra(EXTRA_VOLUME, volume)
            }
            ContextCompat.startForegroundService(context, i)
        }
    }

    private val tag = "VoiceService"
    private val channelId = "voice_call_channel_01"
    private val notificationId = 101

    private var wakeLock: PowerManager.WakeLock? = null

    private var wifiLock: WifiManager.WifiLock? = null
    private var client: WebRTCClient? = null
    private var signaling: SignalingBackend? = null
    private var currentRoomId: String? = null
    private var currentSelfId: String? = null
    private var audioRouter: AudioRouteController? = null

    private val remoteVolumes = mutableMapOf<String, Double>()

    // [NEW] Presence Management in Service
    private val presenceRepository = PresenceRepository()
    private var presenceListener: ValueEventListener? = null
    private var previousParticipantIds = emptySet<String>()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Start foreground first, then attach signaling to avoid background throttling
        startAsForeground()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 🛑 FIX: Check if service was restarted with null intent (system restart)
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
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
            ACTION_SET_A2DP_MODE -> {
                val on = intent.getBooleanExtra(EXTRA_A2DP_ON, false)
                audioRouter?.setUseA2dpMode(on)
            }
            // NEW: drop specific peers
            ACTION_DISCONNECT_FROM -> {
                val ids = intent.getStringArrayListExtra(EXTRA_REMOTE_IDS) ?: arrayListOf()
                ids.forEach { id -> client?.disconnectFrom(id) }
            }
            ACTION_SET_MIC_VOLUME -> {
                val volume = intent.getDoubleExtra(EXTRA_VOLUME, 1.0)
                client?.setMicrophoneVolume(volume)
            }
            ACTION_SET_REMOTE_VOLUME -> {
                val userId = intent.getStringExtra(EXTRA_REMOTE_ID)
                val volume = intent.getDoubleExtra(EXTRA_VOLUME, 1.0)
                if (userId != null) {
                    // 🎤 NEW: Cache the volume
                    remoteVolumes[userId] = volume
                    client?.setRemoteVolume(userId, volume)
                }
            }
            ACTION_SET_QUALITY_MODE -> {
                val highQuality = intent.getBooleanExtra(EXTRA_HIGH_QUALITY, true)
                client?.setHighQuality(highQuality)
            }
        }
        // 🛑 FIX: Return START_NOT_STICKY to prevent auto-restart on crash/kill
        return START_NOT_STICKY
    }

    private fun startCall(roomId: String, selfId: String) {
        if (client != null && currentRoomId == roomId && currentSelfId == selfId) return
        stopCall()
        currentRoomId = roomId
        currentSelfId = selfId

        // Clear volume cache on new call
        remoteVolumes.clear()
        com.mustakim.bokbok.state.ConnectionStateManager.clear()
        audioRouter = audioRouter ?: AudioRouteController(applicationContext)
        audioRouter?.start(
            defaultToSpeaker = true,
            useA2dp = true,
            ducking = true
        )
        signaling = RealtimeSignaling(roomId, selfId)
        client = WebRTCClient(
            context = applicationContext,
            signalingBackend = signaling!!,
            selfId = selfId,
            roomId = roomId,
            isA2dpMode = audioRouter?.isA2dpModeEnabled() ?: true // <--- Pass the mode here
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

            // 🎤 NEW: Listen for new tracks and re-apply volume
            webrtc.onRemoteAudioTrackAdded = { remoteId ->
                val cachedVolume = remoteVolumes[remoteId]
                if (cachedVolume != null) {
                    Log.d(tag, "Restoring volume for $remoteId: $cachedVolume")
                    webrtc.setRemoteVolume(remoteId, cachedVolume)
                }
            }

            webrtc.connect()
        }

        // [NEW] Start monitoring presence for auto-connection
        startPresenceObservation(roomId, selfId)

        Log.d(tag, "VoiceService started call room=$roomId self=$selfId (RTDB signaling)")
    }

    private fun stopCall() {
        // [NEW] Stop monitoring presence
        stopPresenceObservation()

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

    // [NEW] Presence Logic
    private fun startPresenceObservation(roomId: String, selfId: String) {
        stopPresenceObservation() // Safety check
        previousParticipantIds = emptySet()

        presenceListener = presenceRepository.observeRoomPresence(
            roomId = roomId,
            onChange = { currentIds ->
                // Calculate diff
                val newIds = currentIds.filter { it != selfId && !previousParticipantIds.contains(it) }
                val removedIds = previousParticipantIds.filter { it != selfId && !currentIds.contains(it) }

                // Auto-Connect
                if (newIds.isNotEmpty()) {
                    Log.d(tag, "Presence: Found new peers: $newIds")
                    newIds.forEach { id -> client?.createConnectionTo(id) }
                }

                // Auto-Disconnect
                if (removedIds.isNotEmpty()) {
                    Log.d(tag, "Presence: Peers removed: $removedIds")
                    removedIds.forEach { id -> client?.disconnectFrom(id) }
                }

                previousParticipantIds = currentIds
            },
            onError = { e ->
                Log.e(tag, "Presence observation failed: ${e.message}")
            }
        )
    }

    private fun stopPresenceObservation() {
        presenceListener?.let {
            currentRoomId?.let { roomId ->
                presenceRepository.removePresenceListener(roomId, it)
            }
        }
        presenceListener = null
        previousParticipantIds = emptySet()
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

    @Suppress("DEPRECATION")
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        // 🛑 CHANGED: Use SCREEN_DIM_WAKE_LOCK to keep screen on (dimmed)
        // This prevents the device from sleeping at all.
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "bokbok:voice_call_wl"
        )

        if (wakeLock?.isHeld != true) wakeLock?.acquire()

        // Acquire WiFi Lock to prevent network drop on sleep
        try {
            val wm = applicationContext.getSystemService(WIFI_SERVICE) as? WifiManager
            wm?.let {
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = it.createWifiLock(mode, "bokbok:voice_wifi_lock")
                wifiLock?.setReferenceCounted(false)
                wifiLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to acquire WifiLock", e)
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) wakeLock?.release()
        wakeLock = null

        if (wifiLock?.isHeld == true) wifiLock?.release()
        wifiLock = null
    }

    override fun onDestroy() {
        stopCall()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}