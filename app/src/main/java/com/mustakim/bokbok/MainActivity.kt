package com.mustakim.bokbok

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"

    private val REQ_AUDIO = 1001
    private lateinit var roomInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Firebase anonymous sign-in
        FirebaseAuth.getInstance().signInAnonymously()
        requestBatteryOptimizationExemption()

        roomInput = findViewById(R.id.roomInput)
        val joinButton = findViewById<Button>(R.id.joinButton)

        joinButton.setOnClickListener {
            val roomId = roomInput.text.toString().trim()
            if (roomId.isNotEmpty()) {
                ensurePermissionsAndStartCall(roomId)
            } else {
                Toast.makeText(this, "Please enter a room ID", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun ensurePermissionsAndStartCall(roomId: String) {
        val needed = mutableListOf(Manifest.permission.RECORD_AUDIO)

        // Add Bluetooth permissions for Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val notGranted = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, notGranted.toTypedArray(), REQ_AUDIO)
        } else {
            startCall(roomId)
        }
    }

    private fun startCall(roomId: String) {
        val intent = Intent(this, CallActivity::class.java)
        intent.putExtra("ROOM_ID", roomId)
        startActivity(intent)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            val uri = Uri.parse("package:" + packageName)
            intent.data = uri
            startActivity(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                val roomId = roomInput.text.toString().trim()
                if (roomId.isNotEmpty()) startCall(roomId)
            } else {
                // Check which permissions were denied
                val deniedPermissions = permissions.mapIndexedNotNull { index, permission ->
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) permission else null
                }

                if (deniedPermissions.contains(Manifest.permission.RECORD_AUDIO)) {
                    Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
                } else {
                    // Only Bluetooth was denied - proceed with limited functionality
                    Log.w(TAG, "Bluetooth permission denied, continuing without BT support")
                    val roomId = roomInput.text.toString().trim()
                    if (roomId.isNotEmpty()) startCall(roomId)
                }
            }
        }
    }
}
