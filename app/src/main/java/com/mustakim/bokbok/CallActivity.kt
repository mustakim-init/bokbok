package com.mustakim.bokbok

import android.bluetooth.BluetoothHeadset
import android.content.*
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.max
import kotlin.math.min

class CallActivity : AppCompatActivity() {

    private lateinit var webRtcClient: WebRTCClient
    private lateinit var roomId: String
    private lateinit var participantAdapter: ParticipantAdapter
    private val participants = mutableListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null

    private val prefs by lazy { getSharedPreferences("bokbok_prefs", Context.MODE_PRIVATE) }

    // Receiver for headset/Bluetooth
    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val state = intent.getIntExtra("state", -1)
                    if (state == 1) setEarpieceMode() else setSpeakerMode()
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                    if (state == BluetoothHeadset.STATE_CONNECTED) setEarpieceMode() else setSpeakerMode()
                }
            }
        }
    }

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            SettingsActivity.PREF_PTT -> runOnUiThread { updatePttUi() }
            SettingsActivity.PREF_DUCK -> applyDuckSetting()
            SettingsActivity.PREF_RECEIVE_VOL -> applyReceiveVolume()
            SettingsActivity.PREF_NOISE_SUPP -> {
                runOnUiThread {
                    Toast.makeText(this, "Noise suppression changed — rejoin call for full effect", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        roomId = intent.getStringExtra("ROOM_ID") ?: "unknown"
        findViewById<TextView>(R.id.roomIdText).text = "Room: $roomId"

        audioManager = getSystemService(AudioManager::class.java)

        // Start voice foreground service
        val svcIntent = Intent(this, VoiceService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svcIntent) else startService(svcIntent)

        // register audio device receiver
        val filter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        }
        registerReceiver(audioDeviceReceiver, filter)

        // participants list
        participantAdapter = ParticipantAdapter(this, participants)
        findViewById<ListView>(R.id.participantsList).adapter = participantAdapter

        // UI elements
        val muteButton = findViewById<Button>(R.id.muteButton)
        val pttToggle = findViewById<ToggleButton>(R.id.pttToggle)
        val settingsButton = findViewById<Button>(R.id.settingsButton)
        val leaveButton = findViewById<Button>(R.id.leaveButton)
        val statusText = findViewById<TextView>(R.id.statusText)
        val statusSpinner = findViewById<ProgressBar>(R.id.statusSpinner)
        val receiveVolume = findViewById<SeekBar>(R.id.receiveVolume)
        val pttFloat = findViewById<Button>(R.id.pttFloatButton)
        val connectionInfo = findViewById<TextView>(R.id.connectionInfo)

        // prefs listener
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // initial UI state
        pttToggle.isChecked = prefs.getBoolean(SettingsActivity.PREF_PTT, false)
        receiveVolume.progress = prefs.getInt(SettingsActivity.PREF_RECEIVE_VOL, 100)
        receiveVolume.max = 200
        updatePttUi()

        // audio focus + ducking default
        applyDuckSetting()

        // Check for headphones on start and set audio routing
        val hasHeadphones = audioManager?.isWiredHeadsetOn == true || audioManager?.isBluetoothA2dpOn == true
        if (hasHeadphones) setEarpieceMode() else setSpeakerMode()

        // Buttons
        muteButton.setOnClickListener {
            val muted = webRtcClient.toggleMute()
            muteButton.text = if (muted) "Unmute" else "Mute"
            Toast.makeText(this, if (muted) "Mic muted" else "Mic unmuted", Toast.LENGTH_SHORT).show()
        }

        // mute button also serves as fallback PTT: if PTT enabled and user holds the mute button, talk.
        muteButton.setOnTouchListener { v, event ->
            val isPtt = prefs.getBoolean(SettingsActivity.PREF_PTT, false)
            if (!isPtt) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    webRtcClient.setLocalMicEnabled(true)
                    (v as Button).text = "Talking..."
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    webRtcClient.setLocalMicEnabled(false)
                    (v as Button).text = "Mute"
                }
            }
            true
        }

        // PTT toggle persisted
        pttToggle.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(SettingsActivity.PREF_PTT, isChecked).apply()
            updatePttUi()
        }

        // Floating PTT button behavior: press-to-talk + draggable
        var lastX = 0f
        var lastY = 0f
        var isDragging = false
        pttFloat.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.rawX
                    lastY = event.rawY
                    isDragging = false
                    // start talking immediately on down
                    webRtcClient.setLocalMicEnabled(true)
                    v.alpha = 1.0f
                    (v as Button).text = "🔊"
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastX
                    val dy = event.rawY - lastY
                    if (kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > 6.0) {
                        isDragging = true
                    }
                    if (isDragging) {
                        // Constrain to screen bounds
                        val newX = v.translationX + dx
                        val newY = v.translationY + dy
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        val buttonWidth = v.width
                        val buttonHeight = v.height

                        v.translationX = max(-v.x, min(newX, screenWidth - v.x - buttonWidth))
                        v.translationY = max(-v.y, min(newY, screenHeight - v.y - buttonHeight))

                        lastX = event.rawX
                        lastY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // stop talking
                    webRtcClient.setLocalMicEnabled(false)
                    v.alpha = 0.55f
                    (v as Button).text = "🎤"
                    true
                }
                else -> false
            }
        }

        settingsButton.setOnClickListener {
            SettingsActivity.open(this)
        }

        leaveButton.setOnClickListener {
            cleanupAndFinish(svcIntent)
        }

        // receive volume SeekBar - live update and persist
        receiveVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                var v = progress
                if (v < 10) v = 10
                prefs.edit().putInt(SettingsActivity.PREF_RECEIVE_VOL, v).apply()
                applyReceiveVolume()
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // create and init WebRTCClient
        webRtcClient = WebRTCClient(this, roomId)
        val wantsNoiseSupp = prefs.getBoolean(SettingsActivity.PREF_NOISE_SUPP, true)
        webRtcClient.setNoiseSuppression(wantsNoiseSupp)

        webRtcClient.init(onReady = {
            runOnUiThread {
                statusText.text = "Status: Ready"
                statusSpinner.visibility = View.GONE
                Toast.makeText(this, "Call system ready", Toast.LENGTH_SHORT).show()
            }

            webRtcClient.setOnParticipantsChanged { list ->
                mainHandler.post {
                    participants.clear()
                    participants.addAll(list)
                    participantAdapter.notifyDataSetChanged()

                    // Update connection info
                    val infoText = if (list.isEmpty()) "No other participants"
                    else "${list.size} participant(s) connected"
                    connectionInfo.text = infoText
                }
            }

            webRtcClient.getCurrentParticipants { cur ->
                mainHandler.post {
                    participants.clear()
                    participants.addAll(cur)
                    participantAdapter.notifyDataSetChanged()
                }
            }

            // apply volume after client ready
            applyReceiveVolume()
            applyPttMode()
        })

        // Set connection status callback
        webRtcClient.setOnConnectionStatusChanged { remoteId, status ->
            runOnUiThread {
                connectionInfo.text = "Peer $remoteId: $status"
            }
        }
    }

    private fun updatePttUi() {
        val pttOn = prefs.getBoolean(SettingsActivity.PREF_PTT, false)
        val pttToggle = findViewById<ToggleButton>(R.id.pttToggle)
        val pttFloat = findViewById<Button>(R.id.pttFloatButton)
        val muteButton = findViewById<Button>(R.id.muteButton)

        pttToggle.isChecked = pttOn
        pttFloat.visibility = if (pttOn) View.VISIBLE else View.GONE

        // Update mute button hint text for PTT mode
        if (pttOn) {
            muteButton.hint = "Hold to talk (PTT)"
        } else {
            muteButton.hint = ""
        }
    }

    private fun applyPttMode() {
        val ptt = prefs.getBoolean(SettingsActivity.PREF_PTT, false)
        if (ptt) {
            webRtcClient.setLocalMicEnabled(false)
            Toast.makeText(this, "Push-to-talk enabled", Toast.LENGTH_SHORT).show()
        } else {
            webRtcClient.setLocalMicEnabled(true)
            Toast.makeText(this, "Push-to-talk disabled", Toast.LENGTH_SHORT).show()
        }
        updatePttUi()
    }

    private fun applyDuckSetting() {
        val duck = prefs.getBoolean(SettingsActivity.PREF_DUCK, true)
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val gain = if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN
            focusRequest = AudioFocusRequest.Builder(gain)
                .setAudioAttributes(attr)
                .setOnAudioFocusChangeListener { /* no-op */ }
                .build()
            am.requestAudioFocus(focusRequest!!)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL,
                if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK else AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun applyReceiveVolume() {
        val volPercent = prefs.getInt(SettingsActivity.PREF_RECEIVE_VOL, 100)
        val multiplier = volPercent / 100.0f
        webRtcClient.setReceiveVolumeMultiplier(multiplier)
    }

    private fun setSpeakerMode() {
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
        }
    }

    private fun setEarpieceMode() {
        audioManager?.let { am ->
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false
        }
    }

    private fun cleanupAndFinish(svcIntent: Intent) {
        try { webRtcClient.endCall() } catch (_: Exception) {}
        try { stopService(svcIntent) } catch (_: Exception) {}
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        abandonAudioFocus()
        try { unregisterReceiver(audioDeviceReceiver) } catch (_: Exception) {}
        finish()
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { prefs.unregisterOnSharedPreferenceChangeListener(prefListener) } catch (_: Exception) {}
        try { unregisterReceiver(audioDeviceReceiver) } catch (_: Exception) {}
    }
}