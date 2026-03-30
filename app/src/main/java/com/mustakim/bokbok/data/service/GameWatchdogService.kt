package com.mustakim.bokbok.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mustakim.bokbok.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class GameWatchdogService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    @Inject
    lateinit var daemonManager: com.mustakim.bokbok.data.shell.DaemonManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundService()
        
        // 🚀 SYN-STYLE: Start the persistent background monitor daemon with self-healing
        serviceScope.launch {
            while (isActive) {
                val isAlive = try { daemonManager.checkIsAlive() } catch (_: Exception) { false }
                
                if (!isAlive) {
                    android.util.Log.d("WatchdogService", "Sentinel lost or not started. Deploying...")
                    daemonManager.deployAndStart()
                }
                
                delay(30000) // Check health every 30 seconds
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "game_watchdog_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "BokBok Background Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BokBok is Running")
            .setContentText("Monitoring for games in the background")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1337, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1337, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, GameWatchdogService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
