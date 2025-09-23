package com.mustakim.bokbok

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incoming_call)

        val roomId = intent.getStringExtra("ROOM_ID") ?: ""
        findViewById<TextView>(R.id.incomingCallText).text = "Incoming Call: $roomId"

        val acceptButton = findViewById<Button>(R.id.acceptButton)
        val rejectButton = findViewById<Button>(R.id.rejectButton)

        acceptButton.setOnClickListener {
            val intent = Intent(this, CallActivity::class.java)
            intent.putExtra("ROOM_ID", roomId)
            startActivity(intent)
            finish()
        }

        rejectButton.setOnClickListener {
            finish()
        }
    }
}
