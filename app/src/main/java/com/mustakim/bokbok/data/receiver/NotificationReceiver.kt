package com.mustakim.bokbok.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import com.mustakim.bokbok.MainActivity
import com.mustakim.bokbok.data.repository.NotificationRepository
import com.mustakim.bokbok.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NotificationReceiver : BroadcastReceiver() {
 
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var userRepository: UserRepository
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra("notificationId", 0)
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        // Using injected repositories

        // Helper to delete notification
        fun deleteNotification() {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val userId = userRepository.getCurrentUserId()
                if (userId != null) {
                    // We need the Firestore document ID. 
                    // Ideally, we should pass the document ID in the intent extras.
                    // For now, if we don't have the doc ID, we can't delete it easily without querying.
                    // Let's assume we passed 'notificationDocId' in the intent.
                    val docId = intent.getStringExtra("notificationDocId")
                    if (docId != null) {
                        notificationRepository.deleteNotification(userId, docId)
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
