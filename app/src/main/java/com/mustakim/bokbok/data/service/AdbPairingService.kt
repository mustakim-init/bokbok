package com.mustakim.bokbok.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.adb.AdbKey
import com.mustakim.bokbok.data.adb.AdbKeyStore
import com.mustakim.bokbok.data.adb.AdbMdns
import com.mustakim.bokbok.data.adb.AdbPairingClient
import com.mustakim.bokbok.data.adb.AdbPairingManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

private const val TAG = "AdbPairingService"

@AndroidEntryPoint
@RequiresApi(Build.VERSION_CODES.R)
class AdbPairingService : Service() {

    @Inject lateinit var adbPairingManager: AdbPairingManager
    @Inject lateinit var adbKeyStore: AdbKeyStore

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var mdns: AdbMdns? = null
    private var isSearching = false

    companion object {
        const val ACTION_START = "com.mustakim.bokbok.action.ADB_PAIRING_START"
        const val ACTION_STOP = "com.mustakim.bokbok.action.ADB_PAIRING_STOP"
        const val ACTION_PAIR = "com.mustakim.bokbok.action.ADB_PAIRING_EXECUTE"

        private const val CHANNEL_ID = "adb_pairing_channel"
        private const val NOTIFICATION_ID = 1337

        private const val EXTRA_PORT = "port"
        private const val EXTRA_HOST = "host"
        private const val KEY_TEXT_REPLY = "key_text_reply"

        private const val REQUEST_REPLY = 1
        private const val REQUEST_STOP = 2
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> startSearching()
            ACTION_STOP -> {
                // If we were started via startForegroundService but haven't called
                // startForeground yet, we must do so before stopping to avoid crash.
                ensureForeground()
                stopService()
            }
            ACTION_PAIR -> {
                // Ensure foreground in case this came via getForegroundService
                ensureForeground()
                val port = intent.getIntExtra(EXTRA_PORT, -1)
                val host = intent.getStringExtra(EXTRA_HOST) ?: "127.0.0.1"
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val code = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString()
                Log.d(TAG, "ACTION_PAIR: port=$port, host=$host, code=${code?.let { "****" }}")
                if (port != -1 && !code.isNullOrBlank()) {
                    executePairing(host, port, code)
                } else {
                    Log.e(TAG, "Invalid pairing parameters: port=$port, code is ${if (code.isNullOrBlank()) "blank" else "present"}")
                    // Don't stop; restart search so user can try again
                    startSearching()
                }
            }
        }
        return START_STICKY
    }

    /**
     * Ensures that startForeground() has been called. This is needed because
     * Android requires startForeground() to be called within a few seconds of
     * startForegroundService(). If we receive ACTION_STOP or ACTION_PAIR on a
     * fresh service instance (e.g. the old one was already destroyed), we must
     * call startForeground() before stopSelf().
     */
    private var isForeground = false
    private fun ensureForeground() {
        if (!isForeground) {
            try {
                val notification = buildSearchingNotification()
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                isForeground = true
            } catch (e: Throwable) {
                Log.w(TAG, "ensureForeground failed (may be okay if not a foreground start)", e)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.adb_pairing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            setSound(null, null)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun startSearching() {
        Log.d(TAG, "startSearching: isSearching=$isSearching")

        // 1. Launch Wireless Debugging settings (only on first start)
        if (!isSearching) {
            try {
                val intent = Intent("android.settings.WIFI_ADB_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Could not launch developer settings", e2)
                }
            }
        }

        if (isSearching) return
        isSearching = true

        // 2. Start foreground service with "Searching" notification
        val notification = buildSearchingNotification()
        try {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            isForeground = true
        } catch (e: Throwable) {
            Log.e(TAG, "startForeground failed", e)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
                getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
            }
        }

        // 3. Start mDNS discovery for pairing service
        Log.d(TAG, "Starting mDNS discovery for pairing port...")
        mdns = AdbMdns(this, AdbMdns.TLS_PAIRING) { host, port ->
            serviceScope.launch {
                if (port != -1) {
                    val hostAddr = host?.hostAddress ?: "127.0.0.1"
                    Log.d(TAG, "★ Discovery update: port=$port at host=$hostAddr. Updating notification.")
                    showFoundNotification(hostAddr, port)
                } else {
                    Log.d(TAG, "Pairing service lost, reverting to searching notification")
                    val searchingNotif = buildSearchingNotification()
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, searchingNotif)
                }
            }
        }
        mdns?.start()
    }

    private fun buildSearchingNotification(): Notification {
        // Use getService() for STOP — NOT getForegroundService
        // If the service is already stopped/destroyed and the user taps stop again,
        // getForegroundService would re-create the service via startForegroundService
        // which mandates calling startForeground(), causing a crash if we just stopSelf().
        val stopPendingIntent = PendingIntent.getService(
            this, REQUEST_STOP,
            Intent(this, AdbPairingService::class.java).apply { action = ACTION_STOP },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else 0
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.adb_pairing))
            .setContentText(getString(R.string.adb_pairing_monitoring))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(Notification.Action.Builder(
                null, getString(R.string.adb_pairing_stop), stopPendingIntent
            ).build())
            .build()
    }

    private fun showFoundNotification(host: String, port: Int) {
        Log.d(TAG, "Building input notification for port=$port, host=$host")

        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel(getString(R.string.adb_pairing_code_label))
            .build()

        // Use getForegroundService — critical for Android 12+
        val pairPendingIntent = PendingIntent.getForegroundService(
            this, REQUEST_REPLY,
            Intent(this, AdbPairingService::class.java).apply {
                action = ACTION_PAIR
                putExtra(EXTRA_PORT, port)
                putExtra(EXTRA_HOST, host)
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else
                PendingIntent.FLAG_UPDATE_CURRENT
        )

        val action = Notification.Action.Builder(
            null, getString(R.string.adb_pairing_enter_code), pairPendingIntent
        ).addRemoteInput(remoteInput).build()

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.adb_pairing))
            .setContentText(getString(R.string.adb_pairing_found, port))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(action)
            .build()

        Log.d(TAG, "Posting input notification...")
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun executePairing(host: String, port: Int, code: String) {
        serviceScope.launch {
            updateNotification(getString(R.string.adb_pairing_in_progress))
            
            // Fix: Ensure system security provider is preferred over GmsCore_OpenSSL.
            // libadb-android uses reflection calls on com.android.org.conscrypt.Conscrypt which
            // fails with IllegalArgumentException if the socket is provided by GMS Core.
            val gmsProvider = java.security.Security.getProvider("GmsCore_OpenSSL")
            if (gmsProvider != null) {
                Log.d(TAG, "Temporarily lowering GmsCore_OpenSSL priority for pairing...")
                java.security.Security.removeProvider("GmsCore_OpenSSL")
                java.security.Security.addProvider(gmsProvider) // Re-adds it at the lowest priority
            }

            val adbKey = AdbKey(adbKeyStore, "BokBokResurrector")
            val client = AdbPairingClient(host, port, code, adbKey)
            
            val success = withContext(Dispatchers.IO) {
                try {
                    client.pair()
                } catch (e: Exception) {
                    Log.e(TAG, "Pairing exception", e)
                    false
                }
            }

            if (success) {
                Log.d(TAG, "Pairing succeeded! Running setup flow...")
                updateNotification(getString(R.string.adb_pairing_success))
                adbPairingManager.runSetup() 
                delay(2000)
                stopService()
            } else {
                Log.e(TAG, "Pairing failed on port $port")
                showFailureNotification()
            }
        }
    }

    private fun updateNotification(text: String) {
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.adb_pairing))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun showFailureNotification() {
        val retryPendingIntent = PendingIntent.getForegroundService(
            this, REQUEST_STOP + 1,
            Intent(this, AdbPairingService::class.java).apply { action = ACTION_START },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                PendingIntent.FLAG_IMMUTABLE
            else 0
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.adb_pairing))
            .setContentText(getString(R.string.adb_pairing_failed))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(Notification.Action.Builder(
                null, getString(R.string.adb_pairing_retry), retryPendingIntent
            ).build())
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }

    private fun stopService() {
        Log.d(TAG, "Stopping service...")
        mdns?.stop()
        isSearching = false
        serviceJob.cancel()
        if (isForeground) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        stopSelf()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        mdns?.stop()
        isSearching = false
        serviceJob.cancel()
        super.onDestroy()
    }
}
