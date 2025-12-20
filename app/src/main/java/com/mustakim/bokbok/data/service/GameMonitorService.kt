package com.mustakim.bokbok.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.mustakim.bokbok.MainActivity
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.repository.GameRepository
import kotlinx.coroutines.*
import androidx.core.content.edit

class GameMonitorService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var repository: GameRepository
    
    companion object {
        private const val CHANNEL_ID = "game_monitor_channel"
        private const val NOTIFICATION_ID = 202
        private const val EXTRA_PACKAGE_NAME = "package_name"

        fun start(context: Context, packageName: String) {
            val intent = Intent(context, GameMonitorService::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, GameMonitorService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = GameRepository(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sharedPrefs = getSharedPreferences("game_monitor_prefs", MODE_PRIVATE)
        val packageName = intent?.getStringExtra(EXTRA_PACKAGE_NAME) ?: sharedPrefs.getString("last_pkg", null)
        
        if (packageName == null) {
            // Check if we have snapshots left over - if so, revert them even if we don't know the package
            serviceScope.launch {
                if (repository.hasActiveSnapshots()) {
                    repository.revertAllOptimizations()
                }
                stopSelf()
            }
            return START_NOT_STICKY
        }

        // Save for restart resilience
        sharedPrefs.edit { putString("last_pkg", packageName) }

        showForegroundNotification(packageName)
        startMonitoring(packageName)
        
        return START_STICKY
    }

    private fun showForegroundNotification(packageName: String) {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Game Boost Active")
            .setContentText("Monitoring $packageName")
            .setSmallIcon(R.drawable.ic_notifications_24) // Use existing icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun startMonitoring(packageName: String) {
        serviceScope.launch {
            while (isActive) {
                delay(3000) // Check every 3 seconds for snappier reversion
                try {
                    val isRunning = isAppRunning(packageName)
                    if (!isRunning) {
                        Log.d("GameMonitor", "App $packageName stopped. Reverting optimizations.")
                        repository.revertAllOptimizations()
                        stopSelf()
                        break
                    }
                } catch (e: Exception) {
                    Log.e("GameMonitor", "Error monitoring app", e)
                }
            }
        }
    }

    private suspend fun isAppRunning(packageName: String): Boolean {
        // We use repository's existing logic or similar
        // Since we can't easily access private methods, we'll re-implement the check
        // Or we could have exposed a public method in repository
        val output = repository.executeShizukuCommandAndGet("pidof $packageName")
        return output.trim().isNotEmpty()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Game Monitor Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }
}
