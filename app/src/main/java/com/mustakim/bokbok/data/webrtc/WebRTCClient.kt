package com.mustakim.bokbok.data.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.AudioTrackSink
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SessionDescription
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer

@Suppress("DEPRECATION")
class WebRTCClient(
    context: Context,
    private val signalingBackend: SignalingBackend,
    private val selfId: String,
    private val roomId: String,
    private val isA2dpMode: Boolean = false
) {
    private val tag = "WebRTCClient"
    private val appContext: Context = context.applicationContext

    // Threading
    private val executor = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newScheduledThreadPool(2)

    // State
    private val isInitialized = AtomicBoolean(false)
    private val isShuttingDown = AtomicBoolean(false)
    private val initializationComplete = AtomicBoolean(false)

    // WebRTC Core
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private val eglBase: EglBase = EglBase.create()
    private var localAudioSink: AudioTrackSink? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null // 🎤 CHANGED: Keep reference

    // 🎤 CHANGED: Two PCs for loopback
    private var warmupPC1: PeerConnection? = null
    private var warmupPC2: PeerConnection? = null

    // Maps
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val remoteAudioTracks = ConcurrentHashMap<String, AudioTrack>()
    private val pendingRemoteCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    
    // Robustness & Monitoring
    private val turnServerManager = TurnServerManager()
    private val stableConnections = ConcurrentHashMap<String, Boolean>()
    private val relayConnections = ConcurrentHashMap<String, Boolean>()
    private val connectionStartTimes = ConcurrentHashMap<String, Long>()
    private val connectionRetryCounts = ConcurrentHashMap<String, Int>()
    private val connectedPeersCount = AtomicInteger(0)

    private val currentSpeakingState = ConcurrentHashMap<String, Boolean>()

    // 🎤 NEW: Store local audio level separately
    @Volatile private var localAudioRms: Double = 0.0

    // Audio Management
    private var audioManager: AudioManager? = null
    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedIsMicrophoneMute: Boolean = false
    private var savedIsSpeakerphoneOn: Boolean = false
    @Volatile private var receiveVolumeMultiplier: Float = 1.0f

    // Callbacks
    var onSpeakingStateChanged: ((Map<String, Boolean>) -> Unit)? = null
    var onPeerConnectionStateChanged: ((remoteUserId: String, connected: Boolean) -> Unit)? = null
    var onRemoteAudioTrackAdded: ((String) -> Unit)? = null

    // Stats
    private val statsHandler = Handler(Looper.getMainLooper())
    private var statsPolling = false

    private var isHighQuality: Boolean = true

    private val audioConstraints: MediaConstraints
        get() {
            val constraints = MediaConstraints()
            // Always offer to receive audio
            constraints.mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            constraints.optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))

            // 🛑 FIX: Only disable processing if A2DP is requested AND a headset is connected.
            // If on Speakerphone, we MUST have AEC enabled.
            val effectiveA2dpMode = isA2dpMode && isHeadsetConnected()

            if (effectiveA2dpMode) {
                // 🎵 A2DP MODE: DISABLE ALL PROCESSING for high quality
                constraints.optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "false"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "false"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "false"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "false"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "false"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "false"))
            } else {
                // 📞 CALL MODE: ENABLE PROCESSING for clear voice
                constraints.optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
                constraints.optional.add(MediaConstraints.KeyValuePair("googTypingNoiseDetection", "false"))
            }
            return constraints
        }

    fun connect() {
        Log.d(tag, "connect() called for selfId=$selfId roomId=$roomId")
        
        executeTask {
            setupAudioManager()
            initPeerConnectionFactory()
            initLocalAudio()
            
            isInitialized.set(true)
            initializationComplete.set(true)
            
            startListeningForSignals()
            startStatsPolling()
            startMinimalHealthCheck()
            
            Log.d(tag, "connect() finished setup for selfId=$selfId")
        }
    }

    private fun executeTask(task: Runnable) {
        try {
            if (!executor.isShutdown && !isShuttingDown.get()) {
                executor.execute(task)
            } else {
                Log.w(tag, "Executor unavailable, running task on current thread")
                task.run()
            }
        } catch (e: RejectedExecutionException) {
            Log.w(tag, "Task rejected: ${e.message}")
        }
    }

    private fun setupAudioManager() {
        // 🎤 CHANGED: Defer audio mode management to AudioRouteController.
        // WebRTCClient should not force MODE_IN_COMMUNICATION as it breaks A2DP mode.
        Log.d(tag, "setupAudioManager: Delegating to AudioRouteController")
    }

    private fun initPeerConnectionFactory() {
        if (peerConnectionFactory != null) return
        val options = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-Audio-NetworkAdaptation/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)
        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        // 🛑 FIX: Fallback to Voice Communication if no headset is connected, even if A2DP is preferred.
        val effectiveA2dpMode = isA2dpMode && isHeadsetConnected()

        val audioAttributes = if (effectiveA2dpMode) {
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        } else {
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        }
        Log.d(tag, "Initializing ADM with A2DP Pref=$isA2dpMode, Effective=$effectiveA2dpMode")

        // 🎤 CHANGED: Assign to class property
        audioDeviceModule = JavaAudioDeviceModule.builder(appContext)
            .setAudioAttributes(audioAttributes)
            .setUseHardwareAcousticEchoCanceler(!effectiveA2dpMode) // Enable AEC if NOT in effective A2DP mode
            .setUseHardwareNoiseSuppressor(!effectiveA2dpMode)      // Enable NS if NOT in effective A2DP mode
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordError(errorMessage: String?) {
                    Log.e(tag, "Audio Record Error: $errorMessage")
                }
                override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                    Log.e(tag, "Audio Record Init Error: $errorMessage")
                }
                override fun onWebRtcAudioRecordStartError(
                    errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?,
                    errorMessage: String?
                ) {
                    Log.e(tag, "Audio Record Start Error: $errorMessage")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackError(errorMessage: String?) {
                    Log.e(tag, "Audio Track Error: $errorMessage")
                }
                override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                    Log.e(tag, "Audio Track Init Error: $errorMessage")
                }
                override fun onWebRtcAudioTrackStartError(
                    errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?,
                    errorMessage: String?
                ) {
                    Log.e(tag, "Audio Track Start Error: $errorMessage")
                }
            })
            .createAudioDeviceModule()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setAudioDeviceModule(audioDeviceModule)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
        // 🎤 REMOVED: audioDeviceModule.release() - We keep it alive now!
    }

    private fun initLocalAudio() {
        val factory = peerConnectionFactory ?: return

        val sourceConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(sourceConstraints)

        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource)
        // 🎤 REMOVED: Don't attach sink here. It doesn't work for local tracks.
        // localAudioSink = ...

        localAudioTrack?.setEnabled(true)

        Log.d(tag, "Local Audio initialized. Starting Twin-PC warmup...")
        startWarmupConnection()
    }

    private fun startWarmupConnection() {
        val factory = peerConnectionFactory ?: return
        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        // Observer for PC1 (Sender) - Unchanged
        val observer1 = object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onConnectionChange(p0: PeerConnection.PeerConnectionState?) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { warmupPC2?.addIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(p0: RtpReceiver?, p1: Array<out MediaStream>?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
        }
        // Observer for PC2 (Receiver) - 🎤 UPDATED
        val observer2 = object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(p0: PeerConnection.IceConnectionState?) {}
            override fun onConnectionChange(p0: PeerConnection.PeerConnectionState?) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let { warmupPC1?.addIceCandidate(it) }
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            // 🎤 NEW: Attach Sink to the INCOMING track
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                receiver?.track()?.let { track ->
                    if (track is AudioTrack) {
                        // 1. Keep enabled so data flows
                        track.setEnabled(true)
                        // 2. Mute volume so no echo
                        track.setVolume(0.0)

                        Log.d(tag, "Warmup receiver track found. Attaching Sink...")

                        // 3. Attach Sink HERE
                        localAudioSink = object : AudioTrackSink {
                            private var lastLogTime = 0L
                            override fun onData(data: ByteBuffer, bitsPerSample: Int, sampleRate: Int, channels: Int, frames: Int, timestamp: Long) {
                                if (bitsPerSample == 16) {
                                    val buffer = data.duplicate()
                                    var sum = 0.0
                                    var count = 0
                                    while (buffer.remaining() >= 2) {
                                        val byte1 = buffer.get().toInt()
                                        val byte2 = buffer.get().toInt()
                                        val sample = ((byte2 shl 8) or (byte1 and 0xFF)).toShort()
                                        sum += sample * sample
                                        count++
                                    }
                                    if (count > 0) {
                                        val rms = Math.sqrt(sum / count)
                                        localAudioRms = rms

                                        val now = System.currentTimeMillis()
                                        if (now - lastLogTime > 1000) {
                                            Log.d(tag, "🎤 Loopback RMS: $rms")
                                            lastLogTime = now
                                        }
                                    }
                                }
                            }
                        }
                        track.addSink(localAudioSink)
                    }
                }
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {}
        }
        warmupPC1 = factory.createPeerConnection(rtcConfig, observer1)
        warmupPC2 = factory.createPeerConnection(rtcConfig, observer2)
        localAudioTrack?.let {
            warmupPC1?.addTrack(it, listOf("ARDAMS_WARMUP"))
        }
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }
        warmupPC1?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let { offer ->
                    warmupPC1?.setLocalDescription(SimpleSdpObserver(), offer)
                    warmupPC2?.setRemoteDescription(SimpleSdpObserver(), offer)

                    warmupPC2?.createAnswer(object : SimpleSdpObserver() {
                        override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                            sessionDescription?.let { answer ->
                                warmupPC2?.setLocalDescription(SimpleSdpObserver(), answer)
                                warmupPC1?.setRemoteDescription(SimpleSdpObserver(), answer)
                                Log.d(tag, "Warmup connection established (Twin-PC).")
                            }
                        }
                    }, constraints)
                }
            }
        }, constraints)
    }

    fun disconnect() {
        Log.d(tag, "disconnect() called")
        isShuttingDown.set(true)

        executeTask {
            stopStatsPolling()
            signalingBackend.dispose()

            // Close warmup PCs
            try {
                warmupPC1?.dispose()
                warmupPC1 = null
                warmupPC2?.dispose()
                warmupPC2 = null
            } catch (e: Exception) { Log.w(tag, "Warmup close error: ${e.message}") }
            peerConnections.values.forEach { pc ->
                try { pc.close() } catch (e: Exception) { Log.w(tag, "Error closing PC: ${e.message}") }
            }
            peerConnections.clear()
            stableConnections.clear()
            remoteAudioTracks.clear()

            try {
                localAudioSink?.let { localAudioTrack?.removeSink(it) }
                localAudioSink = null
                localAudioTrack?.dispose()
                audioSource?.dispose()
                peerConnectionFactory?.dispose()

                audioDeviceModule?.release()
                audioDeviceModule = null

            } catch (e: Exception) {
                Log.w(tag, "Error disposing resources: ${e.message}")
            }

            // Restore audio settings
            audioManager?.let { am ->
                am.mode = savedAudioMode
                am.isMicrophoneMute = savedIsMicrophoneMute
                am.isSpeakerphoneOn = savedIsSpeakerphoneOn
            }
        }

        executor.shutdown()
        scheduler.shutdown()
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun setMicrophoneVolume(volume: Double) {
        // WebRTC AudioTrack doesn't have a direct setVolume for local track in standard API.
        // However, we can control it via the AudioSource or by adjusting the input gain if supported.
        // For simplicity and standard WebRTC behavior, we often rely on the OS mixer.
        // BUT, for this requirement, we can try to use the setVolume on the track if it's supported by the specific implementation
        // or we might need to implement a custom AudioProcessor.
        //
        // NOTE: Standard WebRTC AudioTrack.setVolume() applies to playback (remote tracks).
        // For local mic, it's trickier.
        // A common workaround is to just rely on the system mic volume or software gain.
        // Since AudioTrack.setVolume is for the track's output, calling it on a local track *might* not affect what is sent.
        //
        // Let's try setting it on the local track and see if the implementation respects it for the outgoing stream.
        // If not, we might need to look into AudioProcessing.
        localAudioTrack?.setVolume(volume)
    }

    fun setRemoteVolume(remoteUserId: String, volume: Double) {
        remoteAudioTracks[remoteUserId]?.setVolume(volume)
    }

    private fun isHeadsetConnected(): Boolean {
        return try {
            val am = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                devices.any {
                    it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                            it.type == android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                            it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            it.type == android.media.AudioDeviceInfo.TYPE_USB_HEADSET
                }
            } else {
                @Suppress("DEPRECATION")
                am.isWiredHeadsetOn || am.isBluetoothA2dpOn || am.isBluetoothScoOn
            }
        } catch (e: Exception) {
            Log.w(tag, "Error checking headset state: ${e.message}")
            false // Fail safe: assume no headset -> Force AEC ON
        }
    }

    fun disconnectFrom(remoteUserId: String) {
        executeTask {
            peerConnections[remoteUserId]?.close()
            peerConnections.remove(remoteUserId)
            stableConnections.remove(remoteUserId)
            remoteAudioTracks.remove(remoteUserId)
            pendingRemoteCandidates.remove(remoteUserId)
        }
    }

    fun createConnectionTo(remoteUserId: String) {
        executeTask {
            if (!shouldInitiateTo(remoteUserId)) return@executeTask
            
            Log.d(tag, "createConnectionTo($remoteUserId) starting offer")
            val pc = getOrCreatePeerConnection(remoteUserId)

            if (pc == null) {
                Log.e(tag, "Failed to create peer connection for $remoteUserId - factory not initialized")
                return@executeTask
            }
            
            createOfferWithRetry(pc, remoteUserId)
        }
    }

    private fun shouldInitiateTo(remoteUserId: String): Boolean {
        return selfId < remoteUserId
    }

    private fun getOrCreatePeerConnection(remoteUserId: String): PeerConnection? {
        peerConnections[remoteUserId]?.let { return it }

        val factory = peerConnectionFactory ?: return null
        
        val iceServers = turnServerManager.getIceServersForCurrentTier()
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            keyType = PeerConnection.KeyType.ECDSA
        }

        Log.d(tag, "Creating PC for $remoteUserId (Tier ${turnServerManager.getCurrentTier()})")

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(tag, "[$remoteUserId] ICE: $state")
                handleIceConnectionChange(remoteUserId, state)
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    if (it.sdp.contains("typ relay")) {
                        relayConnections[remoteUserId] = true
                        Log.d(tag, "📡 TURN relay candidate for $remoteUserId")
                    }
                    signalingBackend.sendIceCandidate(remoteUserId, it)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}

            override fun onAddStream(stream: MediaStream?) {
                stream?.audioTracks?.firstOrNull()?.let { track ->
                    handleRemoteTrack(remoteUserId, track)
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                val track = receiver?.track()
                if (track is AudioTrack) {
                    handleRemoteTrack(remoteUserId, track)
                }
            }
        }

        val pc = factory.createPeerConnection(rtcConfig, observer) ?: return null
        
        localAudioTrack?.let { track ->
            pc.addTrack(track, listOf("ARDAMS"))
        }
        
        peerConnections[remoteUserId] = pc
        connectionStartTimes[remoteUserId] = System.currentTimeMillis()
        return pc
    }

    private fun handleIceConnectionChange(remoteId: String, state: PeerConnection.IceConnectionState?) {
        when (state) {
            PeerConnection.IceConnectionState.NEW -> {
                val timeoutMs = when (turnServerManager.getCurrentTier()) {
                    1 -> 6000L
                    2 -> 10000L
                    3 -> 8000L
                    else -> 8000L
                }
                scheduleStateCheck(remoteId, PeerConnection.IceConnectionState.NEW, timeoutMs) {
                    Log.w(tag, "NEW timeout for $remoteId")
                    turnServerManager.reportConnectionFailure()
                }
            }
            PeerConnection.IceConnectionState.CHECKING -> {
                val tier = turnServerManager.getCurrentTier()

                // Timeout for all tiers
                val timeoutMs = when (tier) {
                    1 -> 8000L
                    2 -> 10000L  // 10s timeout for tier 2
                    3 -> 10000L
                    else -> 10000L
                }

                scheduleStateCheck(remoteId, PeerConnection.IceConnectionState.CHECKING, timeoutMs) {
                    Log.w(tag, "CHECKING timeout for $remoteId")

                    if (tier == 2) {
                        // Retry same TURN first
                        restartIceConnection(remoteId)
                        // If still failing, escalate to next TURN
                        turnServerManager.reportConnectionFailure()
                    } else if (tier == 1) {
                        turnServerManager.reportConnectionFailure()
                    } else {
                        restartIceConnection(remoteId)
                    }
                }
            }
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
                stableConnections[remoteId] = true
                connectionStartTimes.remove(remoteId)
                connectedPeersCount.incrementAndGet()
                turnServerManager.reportConnectionSuccess()
                connectionRetryCounts.remove(remoteId)
                
                onPeerConnectionStateChanged?.invoke(remoteId, true)
                
                Handler(Looper.getMainLooper()).postDelayed({
                    verifyRemoteTracksImmediately(remoteId)
                }, 1000)
            }
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.DISCONNECTED -> {
                if (!isStableConnection(remoteId)) {
                    connectionStartTimes.remove(remoteId)
                    stableConnections.remove(remoteId)
                    turnServerManager.reportConnectionFailure()
                    retryConnectionWithNewTier(remoteId)
                } else {
                    // If stable, wait a bit before declaring dead
                    if (state == PeerConnection.IceConnectionState.FAILED) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (peerConnections[remoteId]?.iceConnectionState() == PeerConnection.IceConnectionState.FAILED) {
                                Log.w(tag, "Stable connection $remoteId permanently FAILED")
                                stableConnections.remove(remoteId)
                                turnServerManager.reportConnectionFailure()
                                retryConnectionWithNewTier(remoteId)
                            }
                        }, 3000)
                    }
                }
                onPeerConnectionStateChanged?.invoke(remoteId, false)
            }
            else -> {}
        }
    }

    private fun scheduleStateCheck(remoteId: String, expectedState: PeerConnection.IceConnectionState, delayMs: Long, action: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isStableConnection(remoteId)) {
                val currentState = try { peerConnections[remoteId]?.iceConnectionState() } catch (_: Exception) { null }
                if (currentState == expectedState) {
                    action()
                }
            }
        }, delayMs)
    }

    private fun isStableConnection(remoteId: String): Boolean {
        return stableConnections[remoteId] == true && remoteAudioTracks.containsKey(remoteId)
    }

    private fun handleRemoteTrack(remoteId: String, track: AudioTrack) {
        Log.d(tag, "Remote audio track for $remoteId")
        track.setEnabled(true)
        remoteAudioTracks[remoteId] = track
        track.setVolume(receiveVolumeMultiplier.toDouble())
        
        Handler(Looper.getMainLooper()).postDelayed({
            onRemoteAudioTrackAdded?.invoke(remoteId)
        }, 500)
    }

    private fun verifyRemoteTracksImmediately(remoteId: String) {
        val pc = peerConnections[remoteId] ?: return
        try {
            val receivers = pc.receivers
            var audioTrackFound = false
            for (receiver in receivers) {
                val track = receiver.track()
                if (track is AudioTrack) {
                    audioTrackFound = true
                    if (!remoteAudioTracks.containsKey(remoteId)) {
                        Log.w(tag, "Audio track found but not in map - adding now")
                        handleRemoteTrack(remoteId, track)
                    }
                    break
                }
            }
            if (!audioTrackFound) Log.e(tag, "NO AUDIO TRACK FOUND for $remoteId")
        } catch (e: Exception) {
            Log.e(tag, "Track verification error: ${e.message}")
        }
    }

    private fun restartIceConnection(remoteId: String) {
        executeTask {
            val pc = peerConnections[remoteId] ?: return@executeTask
            Log.d(tag, "Restarting ICE for $remoteId")
            
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }
            
            pc.createOffer(object : SimpleSdpObserver() {
                override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                    sessionDescription?.let {
                        // [NEW] Optimize SDP
                        val optimizedSdp = optimizeSdp(it.description)
                        val optimizedDesc = SessionDescription(it.type, optimizedSdp)

                        pc.setLocalDescription(SimpleSdpObserver(), optimizedDesc)
                        signalingBackend.sendOffer(remoteId, optimizedDesc.description)
                    }
                }
            }, constraints)
        }
    }

    private fun retryConnectionWithNewTier(remoteId: String) {
        if (isShuttingDown.get()) return
        
        val retryCount = connectionRetryCounts.getOrDefault(remoteId, 0)
        val maxRetries = if (turnServerManager.getCurrentTier() == 2) 3 else 2
        
        if (retryCount >= maxRetries) {
            Log.w(tag, "Max retries reached for $remoteId")
            return
        }
        
        connectionRetryCounts[remoteId] = retryCount + 1
        val backoffMs = 1000L * (1 shl retryCount)
        
        Log.d(tag, "Scheduling retry for $remoteId in ${backoffMs}ms")
        
        scheduler.schedule({
            executeTask {
                try {
                    peerConnections[remoteId]?.close()
                    peerConnections.remove(remoteId)
                    remoteAudioTracks.remove(remoteId)
                    stableConnections.remove(remoteId)
                    pendingRemoteCandidates.remove(remoteId)
                    
                    createConnectionTo(remoteId)
                } catch (e: Exception) {
                    Log.e(tag, "Retry error: ${e.message}")
                }
            }
        }, backoffMs, TimeUnit.MILLISECONDS)
    }

    private fun createOfferWithRetry(pc: PeerConnection, remoteId: String, attempt: Int = 0) {
        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                sessionDescription?.let {
                    // [NEW] Optimize SDP
                    val optimizedSdp = optimizeSdp(it.description)
                    val optimizedDesc = SessionDescription(it.type, optimizedSdp)

                    pc.setLocalDescription(SimpleSdpObserver(), optimizedDesc)
                    signalingBackend.sendOffer(remoteId, optimizedDesc.description)
                    Log.d(tag, "Offer sent to $remoteId (Optimized: 32kbps + DTX + 20ms)")
                }
            }
            
            override fun onCreateFailure(p0: String?) {
                if (attempt < 2) {
                    scheduler.schedule({
                        executeTask { createOfferWithRetry(pc, remoteId, attempt + 1) }
                    }, 1000, TimeUnit.MILLISECONDS)
                }
            }
        }, audioConstraints)
    }

    private fun startListeningForSignals() {
        signalingBackend.observeSignals { message ->
            executeTask {
                when (message.type) {
                    "offer" -> handleRemoteOffer(message)
                    "answer" -> handleRemoteAnswer(message)
                    "ice" -> handleRemoteIce(message)
                }
            }
        }
    }

    private fun handleRemoteOffer(message: SignalMessage) {
        val from = message.from
        val sdp = message.sdp ?: return
        
        val pc = getOrCreatePeerConnection(from) ?: return
        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)
        
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                flushPendingCandidates(from, pc)
                
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        sessionDescription?.let {
                            // [NEW] Optimize SDP
                            val optimizedSdp = optimizeSdp(it.description)
                            val optimizedDesc = SessionDescription(it.type, optimizedSdp)

                            pc.setLocalDescription(SimpleSdpObserver(), optimizedDesc)
                            signalingBackend.sendAnswer(from, optimizedDesc.description)
                        }
                    }
                }, audioConstraints)
            }
        }, remoteSdp)
    }

    private fun handleRemoteAnswer(message: SignalMessage) {
        val from = message.from
        val sdp = message.sdp ?: return
        
        val pc = peerConnections[from] ?: return
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                flushPendingCandidates(from, pc)
            }
        }, remoteSdp)
    }

    private fun handleRemoteIce(message: SignalMessage) {
        val from = message.from
        val candidate = message.candidate ?: return
        
        val pc = peerConnections[from]
        if (pc == null || pc.remoteDescription == null) {
            pendingRemoteCandidates.getOrPut(from) { mutableListOf() }.add(candidate)
        } else {
            pc.addIceCandidate(candidate)
        }
    }

    private fun flushPendingCandidates(remoteId: String, pc: PeerConnection) {
        pendingRemoteCandidates[remoteId]?.forEach { pc.addIceCandidate(it) }
        pendingRemoteCandidates.remove(remoteId)
    }

    fun setHighQuality(enabled: Boolean) {
        if (isHighQuality == enabled) return
        isHighQuality = enabled
        Log.d(tag, "Setting Quality Mode: ${if (enabled) "HIGH (64kbps Stereo)" else "LOW (32kbps Mono)"}")

        // Trigger renegotiation to apply new SDP params
        peerConnections.keys.forEach { remoteId ->
            restartIceConnection(remoteId) // Re-uses existing logic to send new Offer
        }
    }
    
    private fun startMinimalHealthCheck() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isShuttingDown.get()) return
                
                executeTask {
                    peerConnections.forEach { (remoteId, pc) ->
                        try {
                            val state = pc.iceConnectionState()
                            if (state == PeerConnection.IceConnectionState.CLOSED) {
                                disconnectFrom(remoteId)
                            } else if (state == PeerConnection.IceConnectionState.FAILED) {
                                if (!remoteAudioTracks.containsKey(remoteId)) {
                                    Log.w(tag, "Health check: $remoteId FAILED with no audio, forcing escalation")
                                    turnServerManager.forceEscalateToNextTier()
                                    retryConnectionWithNewTier(remoteId)
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Health check error: ${e.message}")
                        }
                    }
                }
                handler.postDelayed(this, 10000)
            }
        }
        handler.postDelayed(runnable, 5000)
    }

    private fun optimizeSdp(sdp: String): String {
        val lines = sdp.split("\r\n").toMutableList()
        val rtpMapRegex = Regex("^a=rtpmap:(\\d+) opus/48000/2")
        var opusPayloadType: String? = null

        // 1. Find Opus Payload Type
        for (line in lines) {
            val match = rtpMapRegex.find(line)
            if (match != null) {
                opusPayloadType = match.groupValues[1]
                break
            }
        }

        if (opusPayloadType == null) return sdp

        // 2. Find or Create fmtp line
        val fmtpRegex = Regex("^a=fmtp:$opusPayloadType (.*)")
        var fmtpIndex = -1

        for (i in lines.indices) {
            if (fmtpRegex.matches(lines[i])) {
                fmtpIndex = i
                break
            }
        }

        // 3. Inject Parameters
        // REMOVED: usedtx=1 (Causes breaking up if VAD is too aggressive)
        // REMOVED: cbr=0 (Variable bitrate can sometimes cause jitter on unstable nets)
        val params = if (isHighQuality) {
            // 🌟 HIGH QUALITY: 64kbps Stereo
            ";maxaveragebitrate=64000;stereo=1;sprop-stereo=1"
        } else {
            // 📉 LOW QUALITY: 32kbps Mono, robust
            ";maxaveragebitrate=32000;stereo=0"
        }

        if (fmtpIndex != -1) {
            // Append to existing line
            val currentLine = lines[fmtpIndex]
            if (!currentLine.contains("maxaveragebitrate")) {
                lines[fmtpIndex] = currentLine + params
            }
        } else {
            // Insert new line after rtpmap
            // minptime=20 -> Standard 20ms packets
            // useinbandfec=1 -> Forward Error Correction (Helps with packet loss)
            val rtpMapIndex = lines.indexOfFirst { it.startsWith("a=rtpmap:$opusPayloadType") }
            if (rtpMapIndex != -1) {
                lines.add(rtpMapIndex + 1, "a=fmtp:$opusPayloadType minptime=20;useinbandfec=1$params")
            }
        }

        return lines.joinToString("\r\n")
    }

    // Stats
    private fun startStatsPolling() {
        if (statsPolling) return
        statsPolling = true
        statsHandler.post(statsPollRunnable)
    }

    private fun stopStatsPolling() {
        statsPolling = false
        statsHandler.removeCallbacks(statsPollRunnable)
    }

    private val statsPollRunnable = object : Runnable {
        override fun run() {
            if (!statsPolling) return

            // 🎤 NEW: Update self state from the volatile variable
            // Threshold lowered to 50.0 for better sensitivity
            val isSpeaking = localAudioRms > 50.0
            currentSpeakingState[selfId] = isSpeaking

            // 1. Emit the LATEST known state immediately.
            onSpeakingStateChanged?.invoke(currentSpeakingState.toMap())

            executeTask {
                // 2. Trigger stats gathering for all peers
                peerConnections.forEach { (remoteId, pc) ->
                    pc.getStats { report ->
                        // A. Check REMOTE Audio
                        val remoteSpeaking = report.statsMap.values.any {
                            it.type == "inbound-rtp" &&
                                    (it.members["mediaType"] == "audio" || it.members["kind"] == "audio") &&
                                    ((it.members["audioLevel"] as? Double) ?: 0.0) > 0.05
                        }
                        currentSpeakingState[remoteId] = remoteSpeaking

                        // B. Fallback: Check LOCAL Audio from stats (only if Sink is broken)
                        // We merge the result: Sink OR Stats
                        val localStatsSpeaking = report.statsMap.values.any {
                            (it.type == "media-source" || it.type == "track") &&
                                    it.members["kind"] == "audio" &&
                                    it.members["trackIdentifier"] == localAudioTrack?.id() &&
                                    ((it.members["audioLevel"] as? Double) ?: 0.0) > 0.05
                        }

                        // If stats say we are speaking, update it (OR logic)
                        if (localStatsSpeaking) {
                            currentSpeakingState[selfId] = true
                        }
                    }
                }
            }
            // 3. Schedule next run
            statsHandler.postDelayed(this, 200L)
        }
    }
}
