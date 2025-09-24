package com.mustakim.bokbok

import android.bluetooth.BluetoothHeadset
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CallActivity : AppCompatActivity() {

    private lateinit var webRtcClient: WebRTCClient
    private lateinit var roomId: String
    private lateinit var participantAdapter: ParticipantAdapter
    private val participants = mutableListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    // 🎧 Receiver for headset & Bluetooth changes
    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1) {
                        setEarpieceMode()
                        Toast.makeText(this@CallActivity, "Headset plugged in", Toast.LENGTH_SHORT).show()
                    } else if (state == 0) {
                        setSpeakerMode()
                        Toast.makeText(this@CallActivity, "Headset unplugged", Toast.LENGTH_SHORT).show()
                    }
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                    if (state == BluetoothHeadset.STATE_CONNECTED) {
                        setEarpieceMode()
                        Toast.makeText(this@CallActivity, "Bluetooth connected", Toast.LENGTH_SHORT).show()
                    } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                        setSpeakerMode()
                        Toast.makeText(this@CallActivity, "Bluetooth disconnected", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        roomId = intent.getStringExtra("ROOM_ID") ?: "unknown"
        findViewById<TextView>(R.id.roomIdText).text = "Room: $roomId"

        // Start foreground service for call survival
        val svcIntent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }

        // Setup audio
        audioManager = getSystemService(AudioManager::class.java)
        requestAudioFocus()
        setSpeakerMode() // default to loudspeaker

        // Register for headset/Bluetooth changes
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(audioDeviceReceiver, filter)

        // Participants list UI
        participantAdapter = ParticipantAdapter(this, participants)
        findViewById<ListView>(R.id.participantsList).adapter = participantAdapter

        // Initialize WebRTC
        webRtcClient = WebRTCClient(this, roomId)
        webRtcClient.init(onReady = {
            Toast.makeText(this, "Call system ready", Toast.LENGTH_SHORT).show()

            webRtcClient.setOnParticipantsChanged { list ->
                mainHandler.post {
                    participants.clear()
                    participants.addAll(list)
                    participantAdapter.notifyDataSetChanged()
                }
            }

            webRtcClient.getCurrentParticipants { currentList ->
                mainHandler.post {
                    participants.clear()
                    participants.addAll(currentList)
                    participantAdapter.notifyDataSetChanged()
                }
            }
        })

        // Buttons
        val muteButton = findViewById<Button>(R.id.muteButton)
        val leaveButton = findViewById<Button>(R.id.leaveButton)

        muteButton.setOnClickListener {
            val muted = webRtcClient.toggleMute()
            muteButton.text = if (muted) "Unmute" else "Mute"
            Toast.makeText(this, if (muted) "Muted mic" else "Unmuted mic", Toast.LENGTH_SHORT).show()
        }

        leaveButton.setOnClickListener {
            cleanupAndFinish(svcIntent)
        }
    }

    // 🔊 Request audio focus
    private fun requestAudioFocus(): Boolean {
        if (audioManager == null) return false

        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attr)
                .setAcceptsDelayedFocusGain(false)
                .setOnAudioFocusChangeListener { }
                .build()
            audioManager!!.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            audioManager!!.requestAudioFocus(
                null,
                AudioManager.STREAM_VOICE_CALL,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }

        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    // 🔊 Loudspeaker mode
    private fun setSpeakerMode() {
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVolume, 0)
        }
    }

    // 🔊 Earpiece/headset/Bluetooth mode
    private fun setEarpieceMode() {
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false
        }
    }

    private fun cleanupAndFinish(svcIntent: Intent) {
        try { webRtcClient.endCall() } catch (_: Exception) {}
        try { stopService(svcIntent) } catch (_: Exception) {}
        abandonAudioFocus()
        try { unregisterReceiver(audioDeviceReceiver) } catch (_: Exception) {}
        finish()
    }

    private fun abandonAudioFocus() {
        audioManager?.let { am ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { am.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(null)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(audioDeviceReceiver) } catch (_: Exception) {}
    }
}
