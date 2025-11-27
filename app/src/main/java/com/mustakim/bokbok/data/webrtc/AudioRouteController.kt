// data/webrtc/AudioRouteController.kt
package com.mustakim.bokbok.data.webrtc

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat

@Suppress("DEPRECATION")
class AudioRouteController(private val context: Context) {

    private val tag = "AudioRouteController"
    private val audioManager: AudioManager =
        context.getSystemService(AudioManager::class.java)

    private var focusRequest: AudioFocusRequest? = null
    private var started = false

    // Current desired state from app (speaker toggle)
    private var speakerOn: Boolean = true

    // Optional: A2DP vs SCO mode (can be wired to a future setting)
    private var useA2dpMode: Boolean = false

    // NEW: whether we want other apps (games) to duck instead of fully losing audio
    private var duckingEnabled: Boolean = true

    // Locks and flags from CallActivity for safe SCO handling
    private val audioStateLock = Any()
    private val scoStateLock = Any()
    private var isSCOStarted = false
    @Volatile
    private var isSCOStarting = false

    private val mainHandler = Handler(Looper.getMainLooper())

    // Receiver for wired headset + Bluetooth connection changes
    private val audioDeviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                AudioManager.ACTION_HEADSET_PLUG -> {
                    val connected = intent.getIntExtra("state", 0) == 1
                    Log.d(tag, "Headset ${if (connected) "plugged" else "unplugged"}")
                    // Small delay to let device settle
                    mainHandler.postDelayed({
                        synchronized(audioStateLock) {
                            applyAudioRouting()
                        }
                    }, 500)
                }

                android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        android.bluetooth.BluetoothHeadset.EXTRA_STATE, -1
                    )
                    Log.d(tag, "Bluetooth headset state: $state")
                    // Bluetooth can take a couple of seconds to be ready
                    mainHandler.postDelayed({
                        synchronized(audioStateLock) {
                            applyAudioRouting()
                        }
                    }, 2000)
                }

                android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(
                        android.bluetooth.BluetoothAdapter.EXTRA_STATE, -1
                    )
                    if (state == android.bluetooth.BluetoothAdapter.STATE_OFF) {
                        Log.d(tag, "Bluetooth turned off")
                        mainHandler.postDelayed({
                            synchronized(audioStateLock) {
                                applyAudioRouting()
                            }
                        }, 500)
                    }
                }
            }
        }
    }

    // Receiver for SCO audio state updates
    private val scoReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                val action = intent?.action ?: return
                if (action == AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED) {
                    val state = intent.getIntExtra(
                        AudioManager.EXTRA_SCO_AUDIO_STATE,
                        AudioManager.SCO_AUDIO_STATE_ERROR
                    )
                    when (state) {
                        AudioManager.SCO_AUDIO_STATE_CONNECTED -> {
                            Log.d(tag, "SCO_AUDIO_STATE_CONNECTED")
                            synchronized(scoStateLock) {
                                isSCOStarting = false
                                isSCOStarted = true
                            }
                            try {
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                                audioManager.isSpeakerphoneOn = false
                            } catch (e: Exception) {
                                Log.w(tag, "Error setting mode on SCO connect: ${e.message}")
                            }
                        }

                        AudioManager.SCO_AUDIO_STATE_DISCONNECTED,
                        AudioManager.SCO_AUDIO_STATE_ERROR -> {
                            Log.d(tag, "SCO_AUDIO_STATE_DISCONNECTED or ERROR")
                            synchronized(scoStateLock) {
                                isSCOStarting = false
                                isSCOStarted = false
                            }
                            // If wired headphones still connected, route there, else speaker
                            if (hasHeadphonesConnected()) {
                                routeToWiredHeadset()
                            } else {
                                routeToSpeakerOnly()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "scoReceiver error", e)
            }
        }
    }

    /**
     * Start audio routing for call.
     */
    fun start(
        defaultToSpeaker: Boolean = true,
        useA2dp: Boolean = false,
        ducking: Boolean = true
    ) {
        if (started) return
        started = true

        speakerOn = defaultToSpeaker
        useA2dpMode = useA2dp
        duckingEnabled = ducking

        requestAudioFocus()

        try {
            if (useA2dpMode) {
                // High-quality media profile, phone mic
                audioManager.mode = AudioManager.MODE_NORMAL
                Log.d(tag, "Audio mode NORMAL (A2DP mode)")
            } else {
                // Classic voice communication mode
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                Log.d(tag, "Audio mode IN_COMMUNICATION (SCO mode)")
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to set audio mode: ${e.message}")
        }

        // REMOVED: forceSpeakerAsDefault()
        // We want to detect the best route immediately.

        // Initial routing based on connected devices
        synchronized(audioStateLock) {
            applyAudioRouting()
        }

        // NEW: RETRY MECHANISM
        // Bluetooth devices often take a moment to appear in availableCommunicationDevices
        // after setting the audio mode. We retry routing after 1s to ensure we catch them.
        mainHandler.postDelayed({
            synchronized(audioStateLock) {
                if (started) applyAudioRouting()
            }
        }, 1000)

        // Register receivers
        val audioFilter = IntentFilter().apply {
            addAction(AudioManager.ACTION_HEADSET_PLUG)
            addAction(android.bluetooth.BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        context.registerReceiver(audioDeviceReceiver, audioFilter)
        context.registerReceiver(
            scoReceiver,
            IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
        )

        Log.d(tag, "Audio routing started, speakerOn=$speakerOn, useA2dp=$useA2dpMode")
    }

    /**
     * Toggle user-facing "speaker" control.
     * When off and a headset is connected, we prefer headset; otherwise earpiece.
     */
    fun setSpeakerEnabled(enabled: Boolean) {
        speakerOn = enabled
        synchronized(audioStateLock) {
            applyAudioRouting()
        }
        Log.d(tag, "setSpeakerEnabled($enabled)")
    }

    /**
     * Optional hook if you later expose a setting to force A2DP mode vs SCO.
     */
    fun setUseA2dpMode(enabled: Boolean) {
        useA2dpMode = enabled
        synchronized(audioStateLock) {
            applyAudioRouting()
        }
        Log.d(tag, "setUseA2dpMode($enabled)")
    }

    // Optionally expose a setter (handy later if I add a setting)
    fun setDuckingEnabled(enabled: Boolean) {
        duckingEnabled = enabled
        // Re-request focus with the new mode if we're already running
        if (started) {
            requestAudioFocus()
        }
    }

    fun isA2dpModeEnabled(): Boolean = useA2dpMode

    /**
     * Stop routing, unregister receivers, stop SCO, reset audio mode and abandon focus.
     */
    fun stop() {
        try {
            context.unregisterReceiver(audioDeviceReceiver)
        } catch (_: Exception) { }

        try {
            context.unregisterReceiver(scoReceiver)
        } catch (_: Exception) { }

        stopBluetoothScoSafely()

        try {
            audioManager.apply {
                mode = AudioManager.MODE_NORMAL
                isSpeakerphoneOn = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    try {
                        clearCommunicationDevice()
                    } catch (e: Exception) {
                        Log.w(tag, "clearCommunicationDevice failed: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Error resetting audio mode: ${e.message}")
        }

        abandonAudioFocus()
        started = false

        Log.d(tag, "Audio routing stopped")
    }

    // --- Core routing logic (ported from CallActivity) ---

    private fun applyAudioRouting() {
        val am = audioManager
        try {
            val isBtConnected = isBluetoothAudioConnected()

            // 1. Dynamic Mode Switching
            // If A2DP is requested but NO Bluetooth is connected, fallback to Communication mode
            // so the user retains standard "Call Volume" control on speaker/earpiece.
            if (useA2dpMode) {
                if (isBtConnected) {
                    if (am.mode != AudioManager.MODE_NORMAL) {
                        am.mode = AudioManager.MODE_NORMAL
                        Log.d(tag, "Switched to MODE_NORMAL (A2DP active)")
                    }
                } else {
                    if (am.mode != AudioManager.MODE_IN_COMMUNICATION) {
                        am.mode = AudioManager.MODE_IN_COMMUNICATION
                        Log.d(tag, "Fallback to MODE_IN_COMMUNICATION (A2DP on but no device)")
                    }
                }
            } else {
                if (am.mode != AudioManager.MODE_IN_COMMUNICATION) {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                }
            }

            // 2. Routing Logic
            when {
                isBtConnected -> {
                    Log.d(tag, "🎧 Routing to Bluetooth")
                    routeToBluetoothOnly()
                }

                hasHeadphonesConnected() -> {
                    Log.d(tag, "🎧 Routing to wired headset")
                    routeToWiredHeadset()
                }

                else -> {
                    Log.d(tag, "🔊 Routing to speaker / earpiece")
                    if (speakerOn) {
                        routeToSpeakerOnly()
                    } else {
                        // Non-speaker, no headset: treat as earpiece
                        try {
                            am.isSpeakerphoneOn = false
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to set earpiece mode: ${e.message}")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "❌ Audio routing failed", e)
        }
    }

    private fun routeToBluetoothOnly() {
        val am = audioManager
        val useA2dp = useA2dpMode

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                if (useA2dp) {
                    // A2DP: music profile, phone mic
                    // 🛑 FIX: A2DP devices do NOT appear in availableCommunicationDevices.
                    // We must clear any forced communication device and let the system route STREAM_MUSIC to A2DP.
                    am.clearCommunicationDevice()
                    am.isSpeakerphoneOn = false
                    Log.d(tag, "✅ Routed to Bluetooth A2DP (Cleared comm device, using system media routing)")
                } else {
                    // SCO: headset profile, headset mic
                    // Fix: Apple AirPods and some other devices might appear as A2DP or BLE
                    // initially. We should accept them and let setCommunicationDevice handle the switch.
                    val btDevice = devices.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || // Sometimes appears here if SCO is supported
                                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }

                    if (btDevice != null) {
                        am.setCommunicationDevice(btDevice)
                        Log.d(tag, "✅ Routed to Bluetooth (SCO/A2DP/BLE) - type: ${btDevice.type}")
                    } else {
                        Log.w(tag, "No Bluetooth device found for SCO")
                        routeToSpeakerOnly()
                    }
                }
            } else {
                // Pre-Android 12
                if (useA2dp) {
                    // 🛑 FIX: isSpeakerphoneOn = true forces internal speaker. We want A2DP.
                    am.isSpeakerphoneOn = false
                    // Ensure SCO is off
                    if (am.isBluetoothScoOn) {
                        am.stopBluetoothSco()
                        am.isBluetoothScoOn = false
                    }
                    Log.d(tag, "✅ A2DP auto-routing enabled (Speakerphone OFF, SCO OFF)")
                } else {
                    // Explicit SCO for headset mic
                    synchronized(scoStateLock) {
                        if (!isSCOStarted && !isSCOStarting) {
                            isSCOStarting = true
                            am.startBluetoothSco()
                            am.isSpeakerphoneOn = false
                            Log.d(tag, "Started Bluetooth SCO (headset mic)")
                            // Timeout
                            mainHandler.postDelayed({
                                synchronized(scoStateLock) {
                                    if (isSCOStarting && !isSCOStarted) {
                                        Log.w(tag, "⚠️ Bluetooth SCO timeout - resetting")
                                        isSCOStarting = false
                                        try {
                                            am.stopBluetoothSco()
                                        } catch (e: Exception) {
                                            Log.w(tag, "Error stopping SCO on timeout", e)
                                        }
                                        routeToSpeakerOnly()
                                    }
                                }
                            }, 3000)
                        }
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(tag, "Bluetooth SecurityException", e)
            synchronized(scoStateLock) {
                isSCOStarting = false
            }
            routeToSpeakerOnly()
        } catch (e: Exception) {
            Log.e(tag, "Bluetooth routing failed", e)
            synchronized(scoStateLock) {
                isSCOStarting = false
            }
            routeToSpeakerOnly()
        }
    }

    private fun routeToWiredHeadset() {
        val am = audioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val wired = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
                if (wired != null) {
                    am.setCommunicationDevice(wired)
                    Log.d(tag, "routeToWiredHeadset: setCommunicationDevice succeeded")
                } else {
                    am.isSpeakerphoneOn = false
                    Log.d(tag, "routeToWiredHeadset: fallback speakerphoneOff")
                }
            } else {
                am.isSpeakerphoneOn = false
                Log.d(tag, "routeToWiredHeadset: legacy speakerphoneOff")
            }
        } catch (e: Exception) {
            Log.w(tag, "routeToWiredHeadset failed: ${e.message}", e)
        }
    }

    private fun routeToSpeakerOnly() {
        val am = audioManager
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = am.availableCommunicationDevices
                val speaker = devices.firstOrNull {
                    it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                }
                if (speaker != null) {
                    am.setCommunicationDevice(speaker)
                    Log.d(tag, "Speaker set as communication device (SDK >= 31)")
                } else {
                    am.isSpeakerphoneOn = true
                }
            } else {
                am.isSpeakerphoneOn = true
            }
            Log.d(tag, "routeToSpeakerOnly: speaker configured")
        } catch (e: Exception) {
            Log.w(tag, "routeToSpeakerOnly failed: ${e.message}", e)
        }
    }

    private fun forceSpeakerAsDefault() {
        val am = audioManager
        try {
            am.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val speaker = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    am.setCommunicationDevice(speaker)
                    Log.d(tag, "Default route set to speaker via setCommunicationDevice()")
                } else {
                    am.isSpeakerphoneOn = true
                    Log.d(tag, "Default route set to speaker (legacy fallback)")
                }
            } else {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
                Log.d(tag, "Default route set to speaker via legacy API")
            }
        } catch (e: Exception) {
            Log.w(tag, "forceSpeakerAsDefault failed: ${e.message}", e)
        }
    }

    private fun stopBluetoothScoSafely() {
        val am = audioManager
        try {
            synchronized(scoStateLock) {
                if (isSCOStarting) {
                    Log.d(tag, "SCO is starting, delay stop")
                    mainHandler.postDelayed({ stopBluetoothScoSafely() }, 500)
                    return
                }
                if (isSCOStarted) {
                    am.stopBluetoothSco()
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "stopBluetoothSco failed: ${e.message}")
        } finally {
            mainHandler.postDelayed({
                synchronized(scoStateLock) {
                    isSCOStarted = false
                    isSCOStarting = false
                    Log.d(tag, "stopBluetoothScoSafely: forced SCO flags clear")
                }
            }, 5000)
        }
    }

    private fun isBluetoothAudioConnected(): Boolean {
        val am = audioManager

        // On Android 12+ we must have BLUETOOTH_CONNECT permission before touching BT APIs
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d(tag, "No BLUETOOTH_CONNECT permission")
                return false
            }
        }

        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter == null) {
                Log.d(tag, "No Bluetooth adapter")
                return false
            }
            if (!adapter.isEnabled) {
                Log.d(tag, "Bluetooth disabled")
                return false
            }

            // Check both HEADSET (SCO) and A2DP (Media) profiles
            val headsetProfile = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.HEADSET)
            val a2dpProfile = adapter.getProfileConnectionState(android.bluetooth.BluetoothProfile.A2DP)

            val isConnected = headsetProfile == android.bluetooth.BluetoothProfile.STATE_CONNECTED ||
                    a2dpProfile == android.bluetooth.BluetoothProfile.STATE_CONNECTED

            isConnected
        } catch (e: SecurityException) {
            Log.w(tag, "SecurityException checking Bluetooth: ${e.message}")
            false
        } catch (e: Exception) {
            Log.w(tag, "Error checking Bluetooth: ${e.message}")
            false
        }
    }

    private fun hasHeadphonesConnected(): Boolean {
        val am = audioManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                outs.any {
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                            it.type == AudioDeviceInfo.TYPE_USB_DEVICE
                }
            } catch (e: Exception) {
                Log.w(tag, "hasHeadphonesConnected error: ${e.message}")
                false
            }
        } else {
            @Suppress("DEPRECATION")
            am.isWiredHeadsetOn
        }
    }

    // --- Audio focus handling (largely unchanged) ---

    private fun requestAudioFocus() {
        try {
            // Always drop any old focus request before changing mode
            abandonAudioFocus()

            // Choose focus gain type based on ducking setting
            val focusGainNew =
                if (duckingEnabled) {
                    // Let games keep playing but lower their volume
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                } else {
                    // Stronger focus – other apps should pause/stop
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                }

            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder()
                    .setUsage(
                        if (useA2dpMode)
                            AudioAttributes.USAGE_MEDIA
                        else
                            AudioAttributes.USAGE_VOICE_COMMUNICATION
                    )
                    .setContentType(
                        if (useA2dpMode)
                            AudioAttributes.CONTENT_TYPE_MUSIC
                        else
                            AudioAttributes.CONTENT_TYPE_SPEECH
                    )
                    .build()

                val afr = AudioFocusRequest.Builder(focusGainNew)
                    .setAudioAttributes(attrs)
                    .setOnAudioFocusChangeListener { /* no-op or future logging */ }
                    .setAcceptsDelayedFocusGain(true)
                    .build()

                focusRequest = afr
                audioManager.requestAudioFocus(afr)
            } else {
                @Suppress("DEPRECATION")
                val streamType = if (useA2dpMode) {
                    AudioManager.STREAM_MUSIC
                } else {
                    AudioManager.STREAM_VOICE_CALL
                }

                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    streamType,
                    if (duckingEnabled)
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                    else
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                )
            }

            Log.d(tag, "requestAudioFocus result=$result (ducking=$duckingEnabled)")
        } catch (e: Exception) {
            Log.w(tag, "Error requesting audio focus: ${e.message}")
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(tag, "Error abandoning audio focus: ${e.message}")
        }
        focusRequest = null
    }
}
