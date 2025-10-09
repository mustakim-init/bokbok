package com.mustakim.bokbok

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class VoiceService : Service() {

    private val TAG = "VoiceService"
    private var wakeLock: PowerManager.WakeLock? = null
    private val CHANNEL_ID = "voice_call_channel_01"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startAsForeground()
        acquireWakeLock()
        ensureAndroid15Compatibility()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Voice Calls",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Ongoing voice call"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun ensureAndroid15Compatibility() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // Request necessary permissions for Android 12+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // BLUETOOTH_CONNECT is critical for Android 12+
                    if (ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        Log.w(TAG, "BLUETOOTH_CONNECT permission not granted - Bluetooth audio may not work")
                    }
                }

                // For Android 15+, ensure foreground service compliance
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    if (notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                        createNotificationChannel()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Android 15+ compatibility setup failed", e)
            }
        }
    }
    private fun startAsForeground() {
        val intent = Intent(this, CallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BokBok Voice Chat")
            .setContentText("Call in progress - Mic active")
            .setSmallIcon(android.R.drawable.ic_media_play) // Consider using your own icon
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            // Android 13+ required properties
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // Add these for better Android 15 compatibility
            .setStyle(NotificationCompat.DecoratedCustomViewStyle()) // Or BigTextStyle if you want
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("NewApi")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Fallback for devices that don't support microphone foreground type
            Log.w(TAG, "Failed to start with microphone type, using fallback: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (e2: Exception) {
                Log.e(TAG, "Complete foreground service start failed", e2)
            }
        }
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK or
                            PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "BokBok::VoiceCallWakeLock"
                )
                wakeLock?.setReferenceCounted(false)
                wakeLock?.acquire()
            }
        } catch (e: Exception) {
            Log.e("VoiceService", "WakeLock acquisition failed", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (_: Exception) {}
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        stopForeground(true)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null


}