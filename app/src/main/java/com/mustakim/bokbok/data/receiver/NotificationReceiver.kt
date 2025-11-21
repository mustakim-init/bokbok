package com.mustakim.bokbok.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.mustakim.bokbok.MainActivity
import kotlinx.coroutines.launch

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notificationId", 0)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        val repo = com.mustakim.bokbok.data.repository.NotificationRepository()
        val userRepo = com.mustakim.bokbok.data.repository.UserRepository(context)

        // Helper to delete notification
        fun deleteNotification() {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val userId = userRepo.getCurrentUserId()
                if (userId != null) {
                    // We need the Firestore document ID. 
                    // Ideally, we should pass the document ID in the intent extras.
                    // For now, if we don't have the doc ID, we can't delete it easily without querying.
                    // Let's assume we passed 'notificationDocId' in the intent.
                    val docId = intent.getStringExtra("notificationDocId")
                    if (docId != null) {
                        repo.deleteNotification(userId, docId)
                    }
                }
            }
        }

        if (intent.action == "ACTION_ACCEPT_INVITE") {
            deleteNotification()
            val roomId = intent.getStringExtra("roomId")
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("roomId", roomId)
                putExtra("navigate_to_room", true) // Handle this in MainActivity
            }
            context.startActivity(launchIntent)
        } else if (intent.action == "ACTION_REJECT_INVITE") {
            deleteNotification()
        }
    }
}
