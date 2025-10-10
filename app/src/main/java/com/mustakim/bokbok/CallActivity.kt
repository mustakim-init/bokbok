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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    private val scoStateLock = Any()
    private val participantsLock = Any()


    private val prefs by lazy { getSharedPreferences("bokbok_prefs", Context.MODE_PRIVATE) }

    // Track whether we've started SCO (so we can stop it later)
    private var isSCOStarted = false
    @Volatile private var isSCOStarting = false  // Add this

    private val audioModeEnforcer = Handler(Looper.getMainLooper())
    private var shouldMaintainAudioMode = true

    private val networkChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    handleNetworkChange()
                }
            }
        }
    }

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



    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val action = intent?.action ?: return
                if (action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                    val state = intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, AudioManager.SCO_AUDIO_STATE_ERROR)
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            Log.d(TAG, "SCO_AUDIO_STATE_CONNECTED")
                            synchronized(scoStateLock) {
                                isSCOStarting = false
                                isSCOStarted = true
                            }
                            Log.d(TAG, "SCO connected - flags: starting=false, started=true")

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
                            synchronized(scoStateLock) {
                                isSCOStarting = false
                                isSCOStarted = false
                            }
                            Log.d(TAG, "SCO disconnected - flags: starting=false, started=false")

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
        forceSpeakerAsDefault()

        // START AUDIO MODE ENFORCEMENT
        shouldMaintainAudioMode = true
        startAudioModeEnforcement()


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

        val networkFilter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
        registerReceiver(networkChangeReceiver, networkFilter)
        Log.d(TAG, "Network change monitoring enabled")


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
        settingsButton.setOnLongClickListener {
            // Toggle mic as audio refresh
            toggleMicNudge()

            val turnStatus = webRtcClient?.getTurnServerStatus() ?: "No client"
            val audioStatus = webRtcClient?.verifyAudioConnection() ?: "No WebRTC client"

            Toast.makeText(this, "Audio nudge applied + Debug Info Logged", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "TurnDebug: $turnStatus")
            Log.d(TAG, "AudioVerification: $audioStatus")

            true
        }
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

    private fun handleNetworkChange() {
        Log.d(TAG, "Network change detected, checking connection stability")

        // Check if we have active network
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(network)

        if (capabilities != null && (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))) {
            Log.d(TAG, "Network available, ensuring WebRTC connections are stable")
            webRtcClient?.refreshAudioSession()
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

    private fun startAudioModeEnforcement() {
        audioModeEnforcer.removeCallbacksAndMessages(null)

        // Only enforce audio mode if we have active participants
        if (participants.isEmpty()) {
            Log.d(TAG, "Skipping audio mode enforcement - no participants")
            shouldMaintainAudioMode = false
            return
        }

        val enforcerRunnable = object : Runnable {
            private var consecutiveCorrectMode = 0

            override fun run() {
                if (!shouldMaintainAudioMode || participants.isEmpty() || isCleaningUp || isFinishing) {
                    Log.d(TAG, "Stopping audio mode enforcement")
                    return
                }

                val am = audioManager ?: return
                val currentMode = am.mode

                // Only enforce if we're actually in a call with active audio
                if (currentMode != AudioManager.MODE_IN_COMMUNICATION) {
                    consecutiveCorrectMode = 0
                    Log.w(TAG, "Audio mode changed to $currentMode, restoring to MODE_IN_COMMUNICATION")

                    try {
                        am.mode = AudioManager.MODE_IN_COMMUNICATION

                        // Trigger a re-application of audio routing
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isCleaningUp && !isFinishing) {
                                applyAudioRouting()
                            }
                        }, 100)

                        Log.d(TAG, "Audio mode restored successfully")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to restore audio mode: ${e.message}", e)
                    }

                    // Check again sooner after a fix
                    audioModeEnforcer.postDelayed(this, 1000)
                } else {
                    consecutiveCorrectMode++

                    // If mode has been correct for a while, slow down checks
                    val nextCheckDelay = when {
                        consecutiveCorrectMode < 3 -> 3000L  // First checks: every 3s
                        consecutiveCorrectMode < 10 -> 5000L // Stable: every 5s
                        else -> 10000L                        // Very stable: every 10s
                    }

                    audioModeEnforcer.postDelayed(this, nextCheckDelay)
                }
            }
        }

        // Start enforcement immediately
        audioModeEnforcer.post(enforcerRunnable)
        Log.d(TAG, "Started smart audio mode enforcement")
    }

    private fun applyAudioRouting() {
        val am = audioManager ?: return

        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // Android 14+ requires explicit communication device selection
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    val availableDevices = am.availableCommunicationDevices
                    var deviceSelected = false

                    // Try Bluetooth first
                    if (isBluetoothAudioConnected()) {
                        val bluetoothDevice = availableDevices.firstOrNull { device ->
                            device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                        }
                        if (bluetoothDevice != null) {
                            am.setCommunicationDevice(bluetoothDevice)
                            Log.d(TAG, "Android 14+: Set Bluetooth communication device")
                            deviceSelected = true

                            // Start SCO for Bluetooth headsets
                            synchronized(scoStateLock) {
                                if (!isSCOStarted && !isSCOStarting) {
                                    isSCOStarting = true
                                    am.startBluetoothSco()
                                    Log.d(TAG, "Started Bluetooth SCO")
                                }
                            }
                        }
                    }

                    // Try wired headset next
                    if (!deviceSelected && hasHeadphonesConnected()) {
                        val wiredDevice = availableDevices.firstOrNull { device ->
                            device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                        }
                        if (wiredDevice != null) {
                            am.setCommunicationDevice(wiredDevice)
                            Log.d(TAG, "Android 14+: Set wired headset communication device")
                            deviceSelected = true
                        }
                    }

                    // Fallback to speaker
                    if (!deviceSelected) {
                        val speaker = availableDevices.firstOrNull {
                            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                        }
                        speaker?.let {
                            am.setCommunicationDevice(it)
                            Log.d(TAG, "Android 14+: Fallback to built-in speaker")
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "Android 14+ audio routing failed: ${e.message}")
                    // Fall through to legacy routing
                }
            }

            // Legacy audio routing for older Android versions
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {

                when {
                    isBluetoothAudioConnected() -> {
                        Log.d(TAG, "Legacy: Routing to Bluetooth")
                        setBluetoothMode()
                    }
                    hasHeadphonesConnected() -> {
                        Log.d(TAG, "Legacy: Routing to wired headset")
                        routeToWiredHeadset()
                    }
                    else -> {
                        Log.d(TAG, "Legacy: Routing to speaker")
                        setSpeakerMode()
                    }
                }
            }

            // Apply audio focus after routing
            Handler(Looper.getMainLooper()).postDelayed({
                applyDuckSetting()
            }, 100)

            // Refresh WebRTC audio session
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    webRtcClient?.refreshAudioSession()
                    Log.d(TAG, "Audio session refreshed")
                } catch (e: Exception) {
                    Log.w(TAG, "Audio session refresh failed: ${e.message}")
                }
            }, 200)

        } catch (e: Exception) {
            Log.e(TAG, "Audio routing failed", e)
            // Emergency fallback
            try {
                am.mode = AudioManager.MODE_IN_COMMUNICATION
                am.isSpeakerphoneOn = true
                Log.w(TAG, "Emergency fallback: forced speaker mode")
            } catch (e2: Exception) {
                Log.e(TAG, "Emergency audio fallback also failed", e2)
            }
        }
    }


    private fun stopBluetoothScoSafely() {
        val am = audioManager ?: return
        try {
            synchronized(scoStateLock) {
                if (isSCOStarting) {
                    Log.d(TAG, "SCO is starting, waiting a bit before stopping")
                    Handler(Looper.getMainLooper()).postDelayed({
                        stopBluetoothScoSafely()
                    }, 500)
                    return
                }
                am.stopBluetoothSco()
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopBluetoothSco failed: ${e.message}")
        } finally {
            Handler(Looper.getMainLooper()).postDelayed({
                synchronized(scoStateLock) {
                    if (isSCOStarted || isSCOStarting) {
                        isSCOStarted = false
                        isSCOStarting = false
                        Log.d(TAG, "stopBluetoothScoSafely: forced SCO flags clear after timeout")
                    }
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

    // Replace applyDuckSetting method in CallActivity
    private fun applyDuckSetting(retryCount: Int = 0) {
        val duck = prefs.getBoolean(SettingsActivity.PREF_DUCK, true)
        val isPtt = prefs.getBoolean(SettingsActivity.PREF_PTT, false)
        val am = audioManager ?: return

        abandonAudioFocus() // Clean up previous focus

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                val focusGain = if (isPtt && duck) {
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                } else if (duck) {
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                } else {
                    AudioManager.AUDIOFOCUS_GAIN
                }

                focusRequest = AudioFocusRequest.Builder(focusGain)
                    .setAudioAttributes(attr)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener { change ->
                        when (change) {
                            AudioManager.AUDIOFOCUS_GAIN -> Log.d(TAG, "Focus gained")
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                Log.w(TAG, "Focus lost permanently")
                                // Try to regain focus after delay
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!isCleaningUp && !isFinishing) {
                                        applyDuckSetting()
                                    }
                                }, 2000)
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                Log.d(TAG, "Focus lost temporarily")
                                if (isPtt) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (!isCleaningUp && !isFinishing) {
                                            applyDuckSetting()
                                        }
                                    }, 1000)
                                }
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                Log.d(TAG, "Focus lost, can duck")
                                if (isPtt && !duck) {
                                    applyDuckSetting()
                                }
                            }
                            else -> Log.d(TAG, "Focus change: $change")
                        }
                    }
                    .build()

                val result = am.requestAudioFocus(focusRequest!!)
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(TAG, "Audio focus granted")
                } else {
                    Log.w(TAG, "Audio focus not granted: $result")

                    // Retry logic for focus failures
                    if (retryCount < 3) {
                        Log.w(TAG, "Will retry audio focus request (attempt ${retryCount + 1})")
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isCleaningUp && !isFinishing) {
                                applyDuckSetting(retryCount + 1)
                            }
                        }, 500L * (retryCount + 1)) // Exponential backoff
                    } else {
                        Log.e(TAG, "Failed to gain audio focus after 3 attempts")
                        Toast.makeText(this, "Audio focus issue - try restarting call", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // Legacy API with retry logic
                @Suppress("DEPRECATION")
                val result = am.requestAudioFocus(
                    { change ->
                        when (change) {
                            AudioManager.AUDIOFOCUS_GAIN -> Log.d(TAG, "Legacy: Focus gained")
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                Log.w(TAG, "Legacy: Focus lost")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!isCleaningUp && !isFinishing) {
                                        applyDuckSetting()
                                    }
                                }, 2000)
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                Log.d(TAG, "Legacy: Focus lost temporarily")
                                if (isPtt) {
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (!isCleaningUp && !isFinishing) {
                                            applyDuckSetting()
                                        }
                                    }, 1000)
                                }
                            }
                            else -> Log.d(TAG, "Legacy: Focus change: $change")
                        }
                    },
                    AudioManager.STREAM_VOICE_CALL,
                    if (duck) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    else AudioManager.AUDIOFOCUS_GAIN
                )

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.d(TAG, "Legacy audio focus granted")
                } else if (retryCount < 3) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isCleaningUp && !isFinishing) {
                            applyDuckSetting(retryCount + 1)
                        }
                    }, 500L * (retryCount + 1))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Audio focus request failed", e)
            if (retryCount < 3) {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isCleaningUp && !isFinishing) {
                        applyDuckSetting(retryCount + 1)
                    }
                }, 1000)
            }
        }
    }

    private fun debugAudioState() {
        val am = audioManager ?: return

        try {
            val remoteTrackCount = webRtcClient?.let {
                // You'll need to add a method to expose this
                "Call getRemoteTrackCount()"
            } ?: "0"

            val debugInfo = """
            === AUDIO DEBUG ===
            Mode: ${am.mode} (3=IN_COMMUNICATION)
            Speaker: ${am.isSpeakerphoneOn}
            Bluetooth SCO: ${am.isBluetoothScoOn}
            Music Volume: ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}
            Call Volume: ${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}
            Active Participants: ${participants.size}
            WebRTC Status: ${webRtcClient?.verifyAudioConnection() ?: "No client"}
            Cleaning Up: $isCleaningUp
            Is Finishing: $isFinishing
            SCO Started: $isSCOStarted
            SCO Starting: $isSCOStarting
        """.trimIndent()

            Log.d(TAG, debugInfo)

            // Also check available audio devices
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                Log.d(TAG, "Available audio devices: ${devices.size}")
                devices.forEach { device ->
                    Log.d(TAG, "  - Type: ${device.type}, Product: ${device.productName}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in debugAudioState: ${e.message}", e)
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
            am.mode = AudioManager.MODE_IN_COMMUNICATION

            // CRITICAL: Android 14+ requires explicit communication device
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val speaker = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) {
                    am.setCommunicationDevice(speaker)
                    Log.d(TAG, "Android 14+: Explicitly set speaker as communication device")
                } else {
                    // Fallback for older behavior
                    am.isSpeakerphoneOn = true
                }
            } else {
                am.isSpeakerphoneOn = true
            }

            Log.d(TAG, "setSpeakerMode: speaker configured for SDK ${Build.VERSION.SDK_INT}")
        } catch (e: Exception) {
            Log.w(TAG, "setSpeakerMode failed: ${e.message}", e)
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            am.setCommunicationDevice(wiredDevice)
                        }
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

            // Actually start Bluetooth SCO for voice calls on older Android versions
            synchronized(scoStateLock) {
                if (!isSCOStarted && !isSCOStarting) {
                    isSCOStarting = true
                    am.startBluetoothSco()
                    Log.d(TAG, "Started Bluetooth SCO for legacy API")
                }
            }

            Log.d(TAG, "Bluetooth audio mode configured for legacy API")
        } catch (e: Exception) {
            Log.e(TAG, "setBluetoothMode failed", e)
            // Fallback to speaker if Bluetooth fails
            setSpeakerMode()
        }
    }

    private fun cleanupAndFinish(svcIntent: Intent) {
        if (isCleaningUp) return
        isCleaningUp = true
        shouldMaintainAudioMode = false

        Log.d(TAG, "Starting cleanup process...")

        // Stop audio mode enforcement immediately
        audioModeEnforcer.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)

        // Unregister receivers safely
        safeUnregisterReceiver(audioDeviceReceiver)
        safeUnregisterReceiver(scoReceiver)
        safeUnregisterReceiver(networkChangeReceiver)

        // Stop Bluetooth SCO
        stopBluetoothScoSafely()

        // Clean up WebRTC first
        try {
            webRtcClient?.endCall()
            Thread.sleep(200) // Give time for cleanup
            webRtcClient?.close()
            webRtcClient = null
        } catch (e: Exception) {
            Log.w(TAG, "Error ending WebRTC call", e)
        }

        // Reset audio mode BEFORE stopping service
        try {
            audioManager?.apply {
                mode = AudioManager.MODE_NORMAL
                isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    clearCommunicationDevice()
                }
            }
            abandonAudioFocus()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reset audio mode", e)
        }

        // Stop service after audio cleanup
        try {
            stopService(svcIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping VoiceService", e)
        }

        // Clean up preferences
        try {
            prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering pref listener", e)
        }

        // Force finish
        Handler(Looper.getMainLooper()).postDelayed({
            finish()
        }, 300)
    }

    private fun safeUnregisterReceiver(receiver: BroadcastReceiver) {
        try {
            unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "Receiver already unregistered: ${receiver.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering receiver: ${e.message}")
        }
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

    // In ensurePermissionsAndInit(), improve the Bluetooth permission check:
    private fun ensurePermissionsAndInit() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.RECORD_AUDIO)
        }

        // FIX: Better handling for BLUETOOTH_CONNECT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQ_AUDIO)
            return
        }

        initWebRtc()
    }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when (level) {
            TRIM_MEMORY_RUNNING_MODERATE,
            TRIM_MEMORY_RUNNING_LOW,
            TRIM_MEMORY_RUNNING_CRITICAL -> {
                // App is running but system is low on memory
                Log.d(TAG, "Trim memory level (running): $level")
                clearUnusedResources()
            }

            TRIM_MEMORY_MODERATE,
            TRIM_MEMORY_BACKGROUND,
            TRIM_MEMORY_COMPLETE -> {
                // App is in background and system needs memory
                Log.d(TAG, "Trim memory level (background): $level")
                clearUnusedResources()
                // Could be more aggressive here since app is in background
            }

            TRIM_MEMORY_UI_HIDDEN -> {
                // UI is hidden (app went to background)
                Log.d(TAG, "Trim memory: UI hidden")
                // Optional: Clear UI-related caches
            }
        }
    }

    private fun clearUnusedResources() {
        // Clear any bitmap caches, large collections, etc.
        mainHandler.removeCallbacksAndMessages(null)
        Runtime.getRuntime().gc()
    }
    private fun logAudioState(tag: String) {
        val am = audioManager ?: return
        Log.d(TAG, """
        $tag Audio State:
        - Mode: ${am.mode} (should be 3)
        - Speaker: ${am.isSpeakerphoneOn}
        - Bluetooth SCO: ${am.isBluetoothScoOn}
        - Participants: ${participants.size}
    """.trimIndent())
    }
    private fun initWebRtc() {
        val statusText = findViewById<TextView>(R.id.statusText)
        val statusSpinner = findViewById<ProgressBar>(R.id.statusSpinner)
        val connectionInfo = findViewById<TextView>(R.id.connectionInfo)

        // Set up connection status monitoring BEFORE initializing WebRTC
        webRtcClient?.setOnConnectionStatusChanged { remoteId, status ->
            runOnUiThread {

                updateConnectionStatusWithTurnInfo(remoteId, status)
                Log.d(TAG, "Connection status changed: $remoteId -> $status")

                // Update connection info display
                val currentText = connectionInfo.text.toString()
                val newText = "Peer $remoteId: $status\n${webRtcClient?.getTurnServerStatus() ?: ""}"
                connectionInfo.text = newText

                // Auto-retry logic for failed connections
                if (status.contains("FAILED", ignoreCase = true) ||
                    status.contains("DISCONNECTED", ignoreCase = true) ||
                    status.contains("CLOSED", ignoreCase = true)) {

                    Log.w(TAG, "Connection failed for $remoteId, scheduling retry")

                    Handler(Looper.getMainLooper()).postDelayed({
                        webRtcClient?.let { client ->
                            // Check if participant is still in the room
                            client.getCurrentParticipants { currentParticipants ->
                                if (currentParticipants.contains(remoteId)) {
                                    Log.d(TAG, "Attempting to reconnect to $remoteId")

                                    // Force reconnection by recreating peer
                                    runOnUiThread {
                                        Toast.makeText(
                                            this@CallActivity,
                                            "Reconnecting to $remoteId...",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    // This will trigger the signaling to recreate the peer connection
                                    client.forceTurnServerEscalation()

                                    // Additional retry: manually trigger peer creation after delay
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        if (participants.contains(remoteId) && !isCleaningUp) {
                                            Log.d(TAG, "Manual reconnection attempt for $remoteId")
                                            // The participant change listener should handle recreation
                                        }
                                    }, 3000)
                                } else {
                                    Log.d(TAG, "Participant $remoteId no longer in room, skipping reconnection")
                                }
                            }
                        }
                    }, 2000L) // Wait 2 seconds before retry
                }

                // Handle successful connections
                if (status.contains("CONNECTED", ignoreCase = true) ||
                    status.contains("COMPLETED", ignoreCase = true)) {

                    Log.d(TAG, "Connection established with $remoteId")
                    runOnUiThread {
                        Toast.makeText(
                            this@CallActivity,
                            "Connected to $remoteId",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    // Refresh audio session on successful connection
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            webRtcClient?.refreshAudioSession()
                            applyAudioRouting() // Re-apply audio routing
                        } catch (e: Exception) {
                            Log.w(TAG, "Error refreshing audio after connection: ${e.message}")
                        }
                    }, 1000)
                }

                // Handle connection timeouts
                if (status.contains("GATHERING", ignoreCase = true) ||
                    status.contains("CHECKING", ignoreCase = true)) {

                    // Set a timeout for stuck connection states
                    Handler(Looper.getMainLooper()).postDelayed({
                        val currentStatus = connectionInfo.text.toString()
                        if (currentStatus.contains(remoteId) &&
                            (currentStatus.contains("GATHERING") || currentStatus.contains("CHECKING"))) {

                            Log.w(TAG, "Connection timeout for $remoteId, forcing escalation")
                            webRtcClient?.forceTurnServerEscalation()

                            runOnUiThread {
                                Toast.makeText(
                                    this@CallActivity,
                                    "Optimizing connection to $remoteId...",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }, 10000L) // 10 second timeout
                }
            }
        }

        webRtcClient?.setOnRemoteAudioTrackAdded { remoteId ->
            runOnUiThread {
                Log.d(TAG, "Remote audio track added for $remoteId - scheduling audio setup")

                // Stagger the audio setup to ensure track is ready
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isCleaningUp && !isFinishing) {
                        Log.d(TAG, "Step 1: Refreshing WebRTC audio session for $remoteId")

                        // 1. First ensure WebRTC audio session is fresh
                        try {
                            webRtcClient?.refreshAudioSession()
                        } catch (e: Exception) {
                            Log.w(TAG, "Audio session refresh failed: ${e.message}")
                        }

                        // 2. Then apply routing after session refresh completes
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isCleaningUp && !isFinishing) {
                                Log.d(TAG, "Step 2: Applying audio routing for $remoteId")
                                applyAudioRouting()

                                // 3. Final verification after routing
                                Handler(Looper.getMainLooper()).postDelayed({
                                    debugAudioState()
                                }, 500)
                            }
                        }, 300)
                    }
                }, 1000)  // Increased initial delay
            }
        }

        webRtcClient?.init(onReady = {
            runOnUiThread {
                statusText.text = "Status: Ready"
                statusSpinner.visibility = View.GONE
                Toast.makeText(this, "Call system ready", Toast.LENGTH_SHORT).show()
            }

            val noiseSupp = prefs.getBoolean(SettingsActivity.PREF_NOISE_SUPP, true)
            webRtcClient?.setNoiseSuppression(noiseSupp)

            webRtcClient?.setOnParticipantsChanged { list ->
                synchronized(participantsLock) {
                    mainHandler.post {
                        synchronized(participantsLock) {
                            participants.clear()
                            participants.addAll(list)
                            participantAdapter.notifyDataSetChanged()
                        }
                        val infoText = if (list.isEmpty()) "No other participants"
                        else "${list.size} participant(s) connected"
                        connectionInfo.text = infoText

                        // Log participant changes for debugging
                        Log.d(TAG, "Participants updated: ${list.joinToString()}")
                    }
                }
                Unit
            }

            webRtcClient?.getCurrentParticipants { cur ->
                synchronized(participantsLock) {
                    mainHandler.post {
                        synchronized(participantsLock) {
                            participants.clear()
                            participants.addAll(cur)
                            participantAdapter.notifyDataSetChanged()
                        }
                        Log.d(TAG, "Initial participants: ${cur.joinToString()}")
                    }
                }
                Unit
            }

            applyReceiveVolume()
            applyPttMode()
            setupTurnServerDebugging()
            monitorConnectionQuality()

            // Enhanced audio state monitor for debugging
            val audioMonitor = Handler(Looper.getMainLooper())
            val monitorRunnable = object : Runnable {
                override fun run() {
                    if (isCleaningUp || isFinishing) {
                        audioMonitor.removeCallbacksAndMessages(null)
                        return
                    }

                    audioManager?.let { am ->
                        val audioDebugInfo = """
            AudioDebug:
            - Mode: ${am.mode}
            - Speaker: ${am.isSpeakerphoneOn}
            - BluetoothSCO: ${am.isBluetoothScoOn}
            - SCOStarted: $isSCOStarted
            - Music Volume: ${am.getStreamVolume(AudioManager.STREAM_MUSIC)}/${am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}
            - Voice Volume: ${am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}/${am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)}
            - Active Participants: ${participants.size}
        """.trimIndent()

                        Log.d("AudioDebug", audioDebugInfo)

                        webRtcClient?.let { client ->
                            val webrtcDebugInfo = """
                WebRTCDebug:
                - Local track: ${client.getLocalAudioTrack() != null}
                - Local enabled: ${client.isLocalMicEnabled()}
                - Remote tracks: ${participants.size}
                - Peer Connections: ${participants.size}
                - TURN Status: ${client.getTurnServerStatus()}
            """.trimIndent()

                            Log.d("WebRTCDebug", webrtcDebugInfo)
                        }
                    }

                    if (!isCleaningUp && !isFinishing) {
                        audioMonitor.postDelayed(this, 5000)
                    }
                }
            }
            audioMonitor.postDelayed(monitorRunnable, 3000)
            Unit
        })
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                initWebRtc()
            } else {
                // Check which permission was denied
                val deniedPermissions = permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }

                if (Manifest.permission.RECORD_AUDIO in deniedPermissions) {
                    Toast.makeText(
                        this,
                        "Audio permission required for voice chat",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    // Bluetooth denied but audio granted - continue with limited functionality
                    Log.w(TAG, "Bluetooth permission denied - continuing without BT support")
                    initWebRtc()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
//        abandonAudioFocus()
    }

    override fun onResume() {
        super.onResume()
        shouldMaintainAudioMode = true

        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION //new added for testing
        applyDuckSetting()
        applyAudioRouting()

        startAudioModeEnforcement()
        Log.d(TAG, "onResume: Audio mode enforcement restarted")
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy called, isCleaningUp=$isCleaningUp")

        // Stop all enforcement immediately
        shouldMaintainAudioMode = false
        audioModeEnforcer.removeCallbacksAndMessages(null)
        mainHandler.removeCallbacksAndMessages(null)

        super.onDestroy()

        if (!isCleaningUp) {
            isCleaningUp = true

            try {
                // 1. Unregister receivers first to stop external events
                safeUnregisterReceiver(audioDeviceReceiver)
                safeUnregisterReceiver(scoReceiver)
                safeUnregisterReceiver(networkChangeReceiver)

                // 2. Stop Bluetooth SCO
                stopBluetoothScoSafely()

                // 3. Clean up WebRTC connections
                try {
                    webRtcClient?.endCall()
                    // Give it time to send leave messages
                    Thread.sleep(200)
                    webRtcClient?.close()
                    webRtcClient = null
                } catch (e: Exception) {
                    Log.w(TAG, "Error ending call in onDestroy", e)
                }

                // 4. Reset audio system to normal state
                try {
                    audioManager?.apply {
                        mode = AudioManager.MODE_NORMAL
                        isSpeakerphoneOn = false

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            try {
                                clearCommunicationDevice()
                            } catch (e: Exception) {
                                Log.w(TAG, "clearCommunicationDevice failed: ${e.message}")
                            }
                        }
                    }
                    abandonAudioFocus()
                    Log.d(TAG, "Audio system reset to normal")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to reset audio mode in onDestroy", e)
                }

                // 5. Stop service last
                try {
                    val svcIntent = Intent(this, VoiceService::class.java)
                    stopService(svcIntent)
                } catch (e: Exception) {
                    Log.w(TAG, "Error stopping VoiceService onDestroy", e)
                }

                // 6. Unregister preferences listener
                try {
                    prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
                } catch (e: Exception) {
                    Log.w(TAG, "Error unregistering pref listener in onDestroy", e)
                }

            } catch (e: Exception) {
                Log.e(TAG, "onDestroy cleanup error: ${e.message}", e)
            }
        }
    }
}
