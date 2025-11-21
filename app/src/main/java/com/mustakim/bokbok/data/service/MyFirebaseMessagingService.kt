package com.mustakim.bokbok.data.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.mustakim.bokbok.MainActivity
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.receiver.NotificationReceiver
import kotlinx.coroutines.launch

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        android.util.Log.d("FCM", "Message received from: ${remoteMessage.from}")
        android.util.Log.d("FCM", "Notification: ${remoteMessage.notification}")
        android.util.Log.d("FCM", "Data payload: ${remoteMessage.data}")
        
        val data = remoteMessage.data
        
        // Check if it's a notification payload (system tray) or data payload (custom handling)
        // Prioritize data payload since we are sending data-only messages now
        val title = data["title"] ?: remoteMessage.notification?.title ?: "New Notification"
        val body = data["body"] ?: remoteMessage.notification?.body ?: ""
        val roomId = data["roomId"]
        val notificationDocId = data["notificationDocId"]
        val notificationId = System.currentTimeMillis().toInt()

        sendNotification(title, body, roomId, notificationDocId, notificationId)
    }

    private fun sendNotification(title: String, body: String, roomId: String?, notificationDocId: String?, notificationId: Int) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // If roomId is present, you can handle navigation in MainActivity
            if (roomId != null) putExtra("roomId", roomId)
            if (notificationDocId != null) putExtra("notificationDocId", notificationDocId)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = "room_invites"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // Replace with your icon
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        // Add Actions if it's an invite
        if (roomId != null) {
            val acceptIntent = Intent(this, NotificationReceiver::class.java).apply {
                action = "ACTION_ACCEPT_INVITE"
                putExtra("roomId", roomId)
                putExtra("notificationId", notificationId)
                if (notificationDocId != null) putExtra("notificationDocId", notificationDocId)
            }
            val acceptPendingIntent = PendingIntent.getBroadcast(
                this, 1, acceptIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val rejectIntent = Intent(this, NotificationReceiver::class.java).apply {
                action = "ACTION_REJECT_INVITE"
                putExtra("notificationId", notificationId)
                if (notificationDocId != null) putExtra("notificationDocId", notificationDocId)
            }
            val rejectPendingIntent = PendingIntent.getBroadcast(
                this, 2, rejectIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            notificationBuilder.addAction(0, "Accept", acceptPendingIntent)
            notificationBuilder.addAction(0, "Reject", rejectPendingIntent)
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Room Invites",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Save token to Firestore
        val repo = com.mustakim.bokbok.data.repository.UserRepository(applicationContext)
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            repo.updateFcmToken(token)
        }
    }
}
