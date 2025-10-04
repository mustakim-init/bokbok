package com.mustakim.bokbok


import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import java.util.Collections
import android.content.*
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.gridlayout.widget.GridLayout
import kotlin.math.max
import kotlin.math.min

class CallActivity : AppCompatActivity() {

    private val TAG = "CallActivity"
    private val REQ_AUDIO = 1001

    private var webRtcClient: WebRTCClient? = null
    private lateinit var roomId: String
    private lateinit var participantAdapter: ParticipantAdapter
    private val participants = mutableListOf<String>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var audioManager: AudioManager? = null
    private var focusRequest: AudioFocusRequest? = null
    private var isCleaningUp = false

    private val prefs by lazy { getSharedPreferences("bokbok_prefs", Context.MODE_PRIVATE) }

    // Track whether we've started SCO (so we can stop it later)
    private var isSCOStarted = false
    @Volatile private var isSCOStarting = false  // Add this

    private val pendingEscalations = Collections.synchronizedSet(mutableSetOf<String>())

    // Receiver for headset/Bluetooth
    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val connected = intent.getIntExtra("state", 0) == 1
                    Log.d(TAG, "Headset ${if (connected) "plugged" else "unplugged"}")
                    Handler(Looper.getMainLooper()).postDelayed({ applyAudioRouting() }, 100)
                }
                BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                    Log.d(TAG, "Bluetooth headset state: $state")
                    Handler(Looper.getMainLooper()).postDelayed({ applyAudioRouting() }, 200)
                }
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                    if (state == BluetoothAdapter.STATE_OFF) {
                        Log.d(TAG, "Bluetooth turned off")
                        Handler(Looper.getMainLooper()).postDelayed({ applyAudioRouting() }, 100)
                    }
                }
            }
        }
    }


    // SCO state receiver: waits until SCO is actually connected before routing audio
    // Replace the entire scoReceiver (around line 69-116) with this:
    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val action = intent?.action ?: return
                if (action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            Log.d(TAG, "SCO_AUDIO_STATE_CONNECTED")
                            isSCOStarting = false  // ADDED
                            isSCOStarted = true
                            try {
                                audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
                                audioManager?.isSpeakerphoneOn = false
                            } catch (e: Exception) {
                                Log.w(TAG, "Error setting mode on SCO connect: ${e.message}")
                            }
                            try {
                                (webRtcClient as? WebRTCClient)?.refreshAudioSession()
                            } catch (e: Exception) {
                                Log.w(TAG, "refreshAudioSession failed on SCO connect: ${e.message}")
                            }
                        }

                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
                        AudioManager.SCO_AUDIO_STATE_ERROR -> {
                            Log.d(TAG, "SCO_AUDIO_STATE_DISCONNECTED or ERROR")
                            isSCOStarting = false  // ADDED
                            isSCOStarted = false

                            if (hasHeadphonesConnected()) {
                                routeToWiredHeadset()
                            } else {
                                setSpeakerMode()
                            }

                            try {
                                (webRtcClient as? WebRTCClient)?.refreshAudioSession()
                            } catch (e: Exception) {
                                Log.w(TAG, "refreshAudioSession failed on SCO disconnect: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "scoReceiver error", e)
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
                    Toast.makeText(
                        this,
                        "Noise suppression changed — rejoin call for full effect",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_call)

        setupAdaptiveLayout()

        roomId = intent.getStringExtra("ROOM_ID") ?: "unknown"
        findViewById<TextView>(R.id.roomIdText).text = "Room: $roomId"

        webRtcClient = WebRTCClient(applicationContext, roomId)

        audioManager = getSystemService(AudioManager::class.java)
//        applyAudioRouting()


        // Start voice foreground service
        val svcIntent = Intent(this, VoiceService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svcIntent) else startService(
                svcIntent
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start VoiceService", e)
        }


        // Register audio device receivers for proper device switching
        val audioFilter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        registerReceiver(audioDeviceReceiver, audioFilter)
        registerReceiver(scoReceiver, IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED))
        Log.d(TAG, "Audio routing: Manual device switching enabled")

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
        val volumeValue = findViewById<TextView>(R.id.volumeValue)

        // prefs listener
        prefs.registerOnSharedPreferenceChangeListener(prefListener)

        // initial UI state
        pttToggle.isChecked = prefs.getBoolean(SettingsActivity.PREF_PTT, false)
        receiveVolume.progress = prefs.getInt(SettingsActivity.PREF_RECEIVE_VOL, 100)
        receiveVolume.max = 200
        updatePttUi()

        // Initialize volume display
        val initialVol = prefs.getInt(SettingsActivity.PREF_RECEIVE_VOL, 100)
        volumeValue.text = "${initialVol}%"

        // audio focus + ducking default
        applyDuckSetting()

        // Simplified audio routing - let WebRTC handle headsets automatically
        setSpeakerMode() // Default to speaker, system will route to headsets automatically

        // Buttons
        muteButton.setOnClickListener {
            val muted = webRtcClient?.toggleMute() ?: false
            muteButton.text = if (muted) "Unmute" else "Mute"
            Toast.makeText(this, if (muted) "Mic muted" else "Mic unmuted", Toast.LENGTH_SHORT)
                .show()
        }

        // mute button also serves as fallback PTT
        muteButton.setOnTouchListener { v, event ->
            val isPtt = prefs.getBoolean(SettingsActivity.PREF_PTT, false)
            if (!isPtt) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    webRtcClient?.setLocalMicEnabled(true)
                    (v as Button).text = "Talking..."
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    webRtcClient?.setLocalMicEnabled(false)
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

        // Floating PTT draggable button
        var initialX = 0f
        var initialY = 0f
        var initialTouchX = 0f
        var initialTouchY = 0f
        pttFloat.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = v.x
                    initialY = v.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                    webRtcClient?.setLocalMicEnabled(true)
                    v.alpha = 1.0f
                    (v as Button).text = "🔊"
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val newX = initialX + dx
                    val newY = initialY + dy
                    val parent = v.parent as ViewGroup
                    val maxX = parent.width - v.width
                    val maxY = parent.height - v.height
                    v.x = newX.coerceIn(0f, maxX.toFloat())
                    v.y = newY.coerceIn(0f, maxY.toFloat())
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    webRtcClient?.setLocalMicEnabled(false)
                    v.alpha = 0.75f
                    (v as Button).text = "🎤"
                    true
                }

                else -> false
            }
        }

        settingsButton.setOnClickListener { SettingsActivity.open(this) }
        leaveButton.setOnClickListener { cleanupAndFinish(svcIntent) }

        // receive volume SeekBar
        receiveVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                var v = progress
                if (v < 10) v = 10
                volumeValue.text = "${v}%"
                prefs.edit().putInt(SettingsActivity.PREF_RECEIVE_VOL, v).apply()
                applyReceiveVolume()
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // create and init WebRTCClient
        try {
            ensurePermissionsAndInit()
        } catch (e: Exception) {
            runOnUiThread {
                findViewById<TextView>(R.id.statusText).text = "Status: Error - Restart app"
                findViewById<ProgressBar>(R.id.statusSpinner).visibility = View.GONE
                Toast.makeText(this, "Failed to initialize call: ${e.message}", Toast.LENGTH_LONG).show()
                Log.e(TAG, "WebRTC initialization failed", e)
            }
        }
    }

    // ========= ADDED TURN SERVER DEBUGGING & MONITORING =========

    private fun setupTurnServerDebugging() {
        // Add a long-press listener on the Settings button
        findViewById<Button>(R.id.settingsButton)?.setOnLongClickListener {
            val turnStatus = webRtcClient?.getTurnServerStatus() ?: "No client"
            val audioStatus = webRtcClient?.verifyAudioConnection() ?: "No WebRTC client"

            Toast.makeText(this, "Debug Info Logged", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "TurnDebug: $turnStatus")
            Log.d(TAG, "AudioVerification: $audioStatus")

            // Also log peer connection states
            webRtcClient?.let { client ->
                Log.d(TAG, "PeerStates: Active participants: ${participants.size}")
                participants.forEach { participantId ->
                    Log.d(TAG, "PeerStates: Participant: $participantId")
                }
            }
            true
        }
    }

    private fun monitorConnectionQuality() {
        val handler = Handler(Looper.getMainLooper())
        val monitorRunnable = object : Runnable {
            override fun run() {
                webRtcClient?.let { client ->
                    val status = client.getTurnServerStatus()
                    Log.d(TAG, "TURN Status: $status")

                    if (participants.isNotEmpty()) {
                        val connectionInfo = findViewById<TextView>(R.id.connectionInfo)
                        val currentText = connectionInfo.text.toString()

                        // Check for stuck states and peer ID
                        val stuckStates = listOf("GATHERING", "CONNECTING", "CHECKING", "NEW")
                        val isStuck = stuckStates.any { currentText.contains(it) }

                        if (isStuck) {
                            // Extract peer ID from connection text
                            val peerIdMatch = Regex("Peer ([^:]+):").find(currentText)
                            val peerId = peerIdMatch?.groupValues?.get(1) ?: "unknown"

                            // Only schedule escalation if not already pending for this peer
                            if (!pendingEscalations.contains(peerId)) {
                                pendingEscalations.add(peerId)

                                handler.postDelayed({
                                    try {
                                        // Check if connection is still stuck
                                        val newText = connectionInfo.text.toString()
                                        if (stuckStates.any { newText.contains(it) } && newText.contains(peerId)) {
                                            Log.w(TAG, "Connection stuck for $peerId, forcing escalation")
                                            client.forceTurnServerEscalation()
                                        }
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Error during escalation check", e)
                                    } finally {
                                        pendingEscalations.remove(peerId)
                                    }
                                }, 20_000L)
                            }
                        }
                    }
                }
                handler.postDelayed(this, 10_000L)
            }
        }
        handler.postDelayed(monitorRunnable, 5_000L)
    }


    // Replace the entire applyAudioRouting() method (around line 330-390) with this:
    private fun applyAudioRouting() {
        val am = audioManager ?: run {
            Log.w(TAG, "AudioManager is null in applyAudioRouting")
            return
        }

        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set audio mode: ${e.message}")
        }

        var hasBtSco = false
        var hasBtA2dp = false
        var hasWired = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                for (d in outs) {
                    when (d.type) {
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> hasBtSco = true
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> hasBtA2dp = true
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_USB_HEADSET,
                        AudioDeviceInfo.TYPE_USB_DEVICE -> hasWired = true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error enumerating audio devices: ${e.message}")
            }
        } else {
            hasWired = hasHeadphonesConnected()
        }

        when {
            hasBtSco -> {
                // Check BLUETOOTH_CONNECT permission on Android S+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "BLUETOOTH_CONNECT not granted, falling back to speaker")
                        setSpeakerMode()
                        return
                    }
                }

                Log.d(TAG, "applyAudioRouting: routing to Bluetooth SCO (requesting start)")
                try { am.isSpeakerphoneOn = false } catch (e: Exception) {}

                // Only start SCO if not already started or starting
                if (!isSCOStarted && !isSCOStarting) {
                    isSCOStarting = true
                    try {
                        am.startBluetoothSco()
                        Log.d(TAG, "Bluetooth SCO start requested")
                    } catch (e: Exception) {
                        Log.w(TAG, "startBluetoothSco() failed: ${e.message}")
                        isSCOStarting = false
                        setSpeakerMode()
                    }
                } else {
                    Log.d(TAG, "SCO already started/starting, skipping")
                }
            }

            hasBtA2dp -> {
                Log.d(TAG, "applyAudioRouting: Bluetooth A2DP present – do not start SCO")
                try { am.isSpeakerphoneOn = false } catch (e: Exception) {}
                stopBluetoothScoSafely()
            }

            hasWired -> {
                Log.d(TAG, "applyAudioRouting: routing to wired headset")
                routeToWiredHeadset()
                stopBluetoothScoSafely()
            }

            else -> {
                Log.d(TAG, "applyAudioRouting: defaulting to speaker")
                setSpeakerMode()
                stopBluetoothScoSafely()
            }
        }

        try {
            (webRtcClient as? WebRTCClient)?.refreshAudioSession()
        } catch (e: Exception) {
            Log.w(TAG, "refreshAudioSession failed: ${e.message}")
            try { toggleMicNudge() } catch (ignored: Exception) {}
        }
    }


    private fun stopBluetoothScoSafely() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                am.stopBluetoothSco()
            } else {
                am.stopBluetoothSco()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopBluetoothSco failed: ${e.message}")
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                if (isSCOStarted || isSCOStarting) {
                    isSCOStarted = false
                    isSCOStarting = false
                    Log.d(TAG, "stopBluetoothScoSafely: forced SCO flags clear after timeout")
                }
            }, 5000)
        }
    }

    private fun isBluetoothAudioConnected(): Boolean {
        return try {
            // Check for Bluetooth permissions first
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
                    return false
                }
            }

            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
                return false
            }

            // Store the result in a variable to avoid the lint warning
            val profileState = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)
            profileState == BluetoothProfile.STATE_CONNECTED
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException checking Bluetooth audio connection: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Bluetooth audio connection: ${e.message}")
            false
        }
    }


    private fun updateConnectionStatusWithTurnInfo(remoteId: String, status: String) {
        runOnUiThread {
            val connectionInfo = findViewById<TextView>(R.id.connectionInfo)
            val turnStatus = webRtcClient?.getTurnServerStatus() ?: ""
            connectionInfo.text = "Peer $remoteId: $status\n$turnStatus"
            Log.d(TAG, "Peer $remoteId: $status, TURN: $turnStatus")
        }
    }

    private fun hasHeadphonesConnected(): Boolean {
        val am = audioManager ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                return outs.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
            } catch (e: Exception) {
                Log.w(TAG, "hasHeadphonesConnected error: ${e.message}")
            }
        } else {
            @Suppress("DEPRECATION")
            return am.isWiredHeadsetOn
        }
        return false
    }


    private fun isBluetoothConnected(): Boolean {
        val am = audioManager ?: return false

        return try {
            // Check permissions for Android 12+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission not granted")
                    return false
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any { device ->
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
            } else {
                @Suppress("DEPRECATION")
                am.isBluetoothA2dpOn || am.isBluetoothScoOn
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException checking Bluetooth connection: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Bluetooth connection: ${e.message}")
            false
        }
    }


    private fun setupAdaptiveLayout() {
        val buttonGrid = findViewById<GridLayout>(R.id.buttonGrid)
        val displayMetrics = resources.displayMetrics
        val screenWidthPx = displayMetrics.widthPixels
        val screenWidthDp = screenWidthPx / displayMetrics.density

        // Adjust layout based on screen width
        if (screenWidthDp < 360) {
            // Very small screens: 2x2 grid
            buttonGrid.columnCount = 2
            buttonGrid.rowCount = 2
        } else if (screenWidthDp < 480) {
            // Small screens: 2x2 grid with slightly larger buttons
            buttonGrid.columnCount = 2
            buttonGrid.rowCount = 2
        } else {
            // Normal and large screens: 4x1 row
            buttonGrid.columnCount = 4
            buttonGrid.rowCount = 1
        }

        // Force layout update
        buttonGrid.requestLayout()
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
            webRtcClient?.setLocalMicEnabled(false)
            Toast.makeText(this, "Push-to-talk enabled", Toast.LENGTH_SHORT).show()
        } else {
            webRtcClient?.setLocalMicEnabled(true)
            Toast.makeText(this, "Push-to-talk disabled", Toast.LENGTH_SHORT).show()
        }
        updatePttUi()
    }

    private fun applyDuckSetting() {
        val duck = prefs.getBoolean(SettingsActivity.PREF_DUCK, true)
        val am = audioManager ?: return

        abandonAudioFocus() // Clean up previous focus

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val focusGain = if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                else AudioManager.AUDIOFOCUS_GAIN

                focusRequest = AudioFocusRequest.Builder(focusGain)
                    .setAudioAttributes(attr)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { change ->
                        when (change) {
                            AudioManager.AUDIOFOCUS_GAIN -> Log.d(TAG, "Focus gained")
                            AudioManager.AUDIOFOCUS_LOSS -> Log.w(TAG, "Focus lost")
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> Log.d(TAG, "Focus lost temporarily")
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Log.d(TAG, "Focus lost, can duck")
                            else -> Log.d(TAG, "Focus change: $change")
                        }
                    }
                    .build()

                val result = am.requestAudioFocus(focusRequest!!)
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(TAG, "Audio focus granted")
                } else {
                    Log.w(TAG, "Audio focus not granted: $result")
                }
            } else {
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    { change ->
                        when (change) {
                            AudioManager.AUDIOFOCUS_GAIN -> Log.d(TAG, "Legacy: Focus gained")
                            AudioManager.AUDIOFOCUS_LOSS -> Log.w(TAG, "Legacy: Focus lost")
                            else -> Log.d(TAG, "Legacy: Focus change: $change")
                        }
                    },
                    AudioManager.STREAM_VOICE_CALL,
                    if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    else AudioManager.AUDIOFOCUS_GAIN
                )
                Log.d(TAG, "Legacy audio focus request result: $result")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio focus request failed", e)
        }
    }

    private fun applyReceiveVolume() {
        val volPercent = prefs.getInt(SettingsActivity.PREF_RECEIVE_VOL, 100)
        val multiplier = volPercent / 100.0f
        webRtcClient?.setReceiveVolumeMultiplier(multiplier)
    }

    // Updated speaker mode with modern API
    private fun setSpeakerMode() {
        val am = audioManager ?: return
        try {
            am.isSpeakerphoneOn = true
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            Log.d(TAG, "setSpeakerMode: speaker on")
        } catch (e: Exception) {
            Log.w(TAG, "setSpeakerMode failed: ${e.message}")
        }
    }


    private fun routeToWiredHeadset() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28+
            try {
                val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val wiredDevice = outputs.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
                if (wiredDevice != null) {
                    try {
                        // set as communication device so WebRTC/system uses it
                        am.setCommunicationDevice(wiredDevice)
                        Log.d(TAG, "routeToWiredHeadset: setCommunicationDevice succeeded")
                        return
                    } catch (e: Exception) {
                        Log.w(TAG, "setCommunicationDevice failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error routing to wired device: ${e.message}")
            }
        }

        // Fallback for older APIs: disable speakerphone so audio goes to headset
        try {
            am.isSpeakerphoneOn = false
            Log.d(TAG, "routeToWiredHeadset: speakerphone off fallback")
        } catch (e: Exception) {
            Log.w(TAG, "routeToWiredHeadset fallback failed: ${e.message}")
        }
    }



    private fun toggleMicNudge() {
        try {
            // Use WebRTCClient getters/setters instead of unknown globals
            val wasEnabled = webRtcClient?.isLocalMicEnabled() ?: true
            try {
                webRtcClient?.setLocalMicEnabled(false)
            } catch (e: Exception) {
                Log.w(TAG, "toggleMicNudge: disabling mic failed: ${e.message}")
            }

            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    webRtcClient?.setLocalMicEnabled(wasEnabled)
                } catch (e: Exception) {
                    Log.w(TAG, "toggleMicNudge: re-enabling mic failed: ${e.message}")
                }
            }, 120L)
        } catch (e: Exception) {
            Log.w(TAG, "toggleMicNudge failed: ${e.message}")
        }
    }


    private fun forceSpeakerAsDefault() {
        val am = audioManager ?: return
        am.mode = AudioManager.MODE_IN_COMMUNICATION

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val speaker = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null) {
                am.setCommunicationDevice(speaker)
                Log.d(TAG, "Default route set to speaker using setCommunicationDevice()")
            }
        } else {
            @Suppress("DEPRECATION")
            am.isSpeakerphoneOn = true
            Log.d(TAG, "Default route set to speaker using legacy API")
        }
    }

    private fun setBluetoothMode() {
        val am = audioManager ?: return
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = false
            Log.d(TAG, "Audio mode set for Bluetooth - letting WebRTC handle routing")
        } catch (e: Exception) {
            Log.e(TAG, "setBluetoothMode failed", e)
            setSpeakerMode()
        }
    }

    private fun cleanupAndFinish(svcIntent: Intent) {
        if (isCleaningUp) return
        isCleaningUp = true

        try {
            // Unregister receivers first
            try {
                unregisterReceiver(audioDeviceReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "audioDeviceReceiver already unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering audioDeviceReceiver", e)
            }

            try {
                unregisterReceiver(scoReceiver)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "scoReceiver already unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering scoReceiver", e)
            }

            // Stop Bluetooth SCO
            stopBluetoothScoSafely()

            // Clean up WebRTC
            try {
                webRtcClient?.endCall()
                webRtcClient = null
            } catch (e: Exception) {
                Log.w(TAG, "Error ending call during cleanup", e)
            }

            // Stop service
            try {
                stopService(svcIntent)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping VoiceService during cleanup", e)
            }

            // Clean up preferences
            try {
                prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering pref listener during cleanup", e)
            }

            abandonAudioFocus()

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }

        finish()
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let {
                try {
                    am.abandonAudioFocusRequest(it)
                } catch (e: Exception) {
                    Log.w(TAG, "abandonAudioFocusRequest failed", e)
                }
            }
        } else {
            @Suppress("DEPRECATION")
            try {
                am.abandonAudioFocus(null)
            } catch (e: Exception) {
                Log.w(TAG, "legacy abandonAudioFocus failed", e)
            }
        }
    }
    private fun ensurePermissionsAndInit() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQ_AUDIO)
            return
        }

        initWebRtc()
    }

    private fun initWebRtc() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val statusSpinner = findViewById<ProgressBar>(R.id.statusSpinner)
        val connectionInfo = findViewById<TextView>(R.id.connectionInfo)
        webRtcClient?.init(onReady = {
            runOnUiThread {
                statusText.text = "Status: Ready"
                statusSpinner.visibility = View.GONE
                Toast.makeText(this, "Call system ready", Toast.LENGTH_SHORT).show()
            }

            webRtcClient?.setOnParticipantsChanged { list ->
                mainHandler.post {
                    participants.clear()
                    participants.addAll(list)
                    participantAdapter.notifyDataSetChanged()
                    val infoText = if (list.isEmpty()) "No other participants"
                    else "${list.size} participant(s) connected"
                    connectionInfo.text = infoText
                }
                Unit
            }

            webRtcClient?.getCurrentParticipants { cur ->
                mainHandler.post {
                    participants.clear()
                    participants.addAll(cur)
                    participantAdapter.notifyDataSetChanged()
                }
                Unit
            }

            applyReceiveVolume()
            applyPttMode()
            setupTurnServerDebugging()
            monitorConnectionQuality()

            // Audio state monitor for debugging
            val audioMonitor = Handler(Looper.getMainLooper())
            val monitorRunnable = object : Runnable {
                override fun run() {
                    audioManager?.let { am ->
                        val audioDebugInfo = """
                        AudioDebug:
                        - Mode: ${am.mode}
                        - Speaker: ${am.isSpeakerphoneOn}
                        - BluetoothSCO: ${am.isBluetoothScoOn}
                        - SCOStarted: $isSCOStarted
                        - Music Volume: ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}
                        - Voice Volume: ${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}
                    """.trimIndent()

                        Log.d("AudioDebug", audioDebugInfo)

                        webRtcClient?.let { client ->
                            val webrtcDebugInfo = """
                            WebRTCDebug:
                            - Local track: ${client.getLocalAudioTrack() != null}
                            - Local enabled: ${client.isLocalMicEnabled()}
                            - Remote tracks: ${participants.size}
                            - Peer Connections: ${participants.size}
                        """.trimIndent()

                            Log.d("WebRTCDebug", webrtcDebugInfo)
                        }
                    }
                    audioMonitor.postDelayed(this, 3000)
                }
            }
            audioMonitor.post(monitorRunnable)
            Unit
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                initWebRtc()
            } else {
                Toast.makeText(this, "Audio permissions required for voice chat", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    override fun onPause() {
        super.onPause()
//        abandonAudioFocus()
    }

    override fun onResume() {
        super.onResume()
        // Re-apply any ducking or user settings you had
        applyDuckSetting()

        // Re-evaluate and apply the correct audio route on resume
        applyAudioRouting()
    }


    override fun onDestroy() {
        super.onDestroy()

        if (!isCleaningUp) {
            try {
                // Unregister receivers
                try {
                    unregisterReceiver(audioDeviceReceiver)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "audioDeviceReceiver already unregistered in onDestroy")
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering audioDeviceReceiver in onDestroy", e)
                }

                try {
                    unregisterReceiver(scoReceiver)
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "scoReceiver already unregistered in onDestroy")
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering scoReceiver in onDestroy", e)
                }

                // Stop services and clean up
                try {
                    val svcIntent = Intent(this, VoiceService::class.java)
                    stopService(svcIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping VoiceService onDestroy", e)
                }

                stopBluetoothScoSafely()

                try {
                    prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering pref listener in onDestroy", e)
                }

                try {
                    webRtcClient?.endCall()
                    webRtcClient = null
                } catch (e: Exception) {
                    Log.w(TAG, "Error ending call in onDestroy", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "onDestroy cleanup error: ${e.message}", e)
            }
        }
    }
}
