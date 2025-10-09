package com.mustakim.bokbok

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class WebRTCClient(private val context: Context, private val roomId: String) {
    private val tag = "WebRTCClient"
    private val executor = Executors.newSingleThreadExecutor()
    private val scheduler = java.util.concurrent.Executors.newScheduledThreadPool(2)
    private val signaling = FirebaseSignaling(roomId)

    private val pendingIceCandidates = ConcurrentHashMap<String, MutableList<IceCandidate>>()
    private val remoteDescriptionSet = ConcurrentHashMap<String, Boolean>()
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val remoteAudioTracks = ConcurrentHashMap<String, AudioTrack>()

    private val initializationComplete = AtomicBoolean(false)

    @Volatile private var factory: PeerConnectionFactory? = null
    @Volatile private var audioSource: AudioSource? = null
    @Volatile private var localAudioTrack: AudioTrack? = null

    @Volatile private var receiveVolumeMultiplier: Float = 1.0f
    @Volatile private var noiseSuppressionEnabled: Boolean = true

    private var onParticipantsChanged: ((List<String>) -> Unit)? = null
    private var onConnectionStatusChanged: ((String, String) -> Unit)? = null

    private val peerConnectionLock = Any()


    // --- NEW: TurnServerManager + connection monitoring fields ---
    private val turnServerManager = TurnServerManager()
    private val connectionTimeouts = ConcurrentHashMap<String, Long>()
    private val maxConnectionTimeoutMs = 15_000L // 15 seconds
    private val connectionRetryCounts = ConcurrentHashMap<String, Int>() // added for backoff logic
    private val isEnding = AtomicBoolean(false) // guard to avoid races during endCall()
    // CGNAT detection
    private var cgnatDetected = false
    private val consecutiveFailures = ConcurrentHashMap<String, Int>()

    private val isShuttingDown = AtomicBoolean(false)
    // -------------------------------------------------------------

    fun setNoiseSuppression(enabled: Boolean) {
        noiseSuppressionEnabled = enabled
        Log.d(tag, "Noise suppression set to $enabled (restart call to fully re-create audio source)")
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
            Log.w(tag, "Task rejected, running on current thread: ${e.message}")
            task.run()
        }
    }

    private fun detectCGNAT(): Boolean {
        try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as android.telephony.TelephonyManager
            val networkOperator = telephonyManager.networkOperatorName?.lowercase() ?: ""
            Log.d(tag, "Network operator: $networkOperator")

            // Common CGNAT carriers
            val isCGNAT = networkOperator.contains("jio") ||
                    networkOperator.contains("airtel") ||
                    networkOperator.contains("vi") ||
                    networkOperator.contains("idea") ||
                    networkOperator.contains("vodafone")

            if (isCGNAT) {
                Log.w(tag, "CGNAT carrier detected: $networkOperator")
            }
            return isCGNAT
        } catch (e: Exception) {
            Log.w(tag, "CGNAT detection failed: ${e.message}")
            return false
        }
    }

    fun setConnectionStateListener(listener: (String, String) -> Unit) {
        onConnectionStatusChanged = listener
    }

    fun setOnConnectionStatusChanged(callback: (String, String) -> Unit) {
        onConnectionStatusChanged = callback
    }

    fun init(onReady: (() -> Unit)? = null) {
        signaling.whenAuthReady {
            executeTask {
                try {
                    // CRITICAL FIX: Add initialization delay to ensure Firebase is ready
                    Log.d(tag, "Starting WebRTC initialization...")

                    // Initialize PeerConnectionFactory on main thread with proper timing
                    if (factory == null) {
                        Log.d(tag, "Initializing PeerConnectionFactory...")

                        // Use handler to ensure main thread initialization
                        val initComplete = java.util.concurrent.CountDownLatch(1)

                        Handler(Looper.getMainLooper()).post {
                            try {
                                PeerConnectionFactory.initialize(
                                    PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                                        .setEnableInternalTracer(true)
                                        .setFieldTrials("WebRTC-Audio-NetworkAdaptation/Enabled/")
                                        .createInitializationOptions()
                                )
                                initComplete.countDown()
                                Log.d(tag, "PeerConnectionFactory initialized on main thread")
                            } catch (e: Exception) {
                                Log.e(tag, "PeerConnectionFactory initialization failed", e)
                                initComplete.countDown()
                            }
                        }

                        // Wait for initialization to complete (max 5 seconds)
                        if (!initComplete.await(5, TimeUnit.SECONDS)) {
                            Log.w(tag, "PeerConnectionFactory initialization timeout")
                        }

                        // Additional small delay to ensure system stability
                        Thread.sleep(500)

                        val opts = PeerConnectionFactory.Options()
                        factory = PeerConnectionFactory.builder().setOptions(opts).createPeerConnectionFactory()
                        Log.d(tag, "PeerConnectionFactory instance created")
                    }

                    if (audioSource == null && factory != null) {
                        val audioConstraints = MediaConstraints()
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", if (noiseSuppressionEnabled) "true" else "false"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                        audioConstraints.optional.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
                        audioConstraints.optional.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))

                        audioSource = factory!!.createAudioSource(audioConstraints)
                        localAudioTrack = factory!!.createAudioTrack("ARDAMSa0", audioSource)
                        localAudioTrack?.setEnabled(true)
                        Log.d(tag, "Local audio track created (noiseSuppression=$noiseSuppressionEnabled)")
                    }

                    // Add small delay before starting signaling
                    Thread.sleep(1000)

                    signaling.start { type, fromId, payload, msgKey ->
                        Log.d(tag, "Signal recv type=$type from=$fromId payloadKeys=${payload.keys}")
                        when (type) {
                            FirebaseSignaling.Type.SDP -> handleRemoteSdp(fromId, payload, msgKey)
                            FirebaseSignaling.Type.ICE -> handleRemoteIce(fromId, payload, msgKey)
                            FirebaseSignaling.Type.JOIN -> {
                                Log.d(tag, "Join message from $fromId -> createPeerIfNeeded")
                                // Add delay to ensure participant is properly registered in Firebase
                                Handler(Looper.getMainLooper()).postDelayed({
                                    executeTask {
                                        // Verify the participant still exists before creating peer
                                        signaling.getParticipantsNow { participants ->
                                            if (participants.contains(fromId)) {
                                                createPeerIfNeeded(fromId, initiator = signaling.shouldInitiateTo(fromId))
                                            } else {
                                                Log.w(tag, "Participant $fromId no longer in room, skipping peer creation")
                                            }
                                        }
                                    }
                                }, 1000) // 1 second delay
                            }
                            FirebaseSignaling.Type.LEAVE -> {
                                Log.d(tag, "Leave message from $fromId -> closePeer")
                                closePeer(fromId)
                            }
                        }
                    }

                    signaling.onParticipantsChanged { list ->
                        onParticipantsChanged?.invoke(list)
                        // Add delay to ensure stable participant discovery
                        Handler(Looper.getMainLooper()).postDelayed({
                            for (rid in list) {
                                if (rid == signaling.localId) continue
                                if (!peerConnections.containsKey(rid) && peerConnections.size < 5) {
                                    createPeerIfNeeded(rid, initiator = signaling.shouldInitiateTo(rid))
                                }
                            }
                        }, 1000)
                    }

                    val joined = signaling.join()
                    Log.d(tag, "join() returned $joined")

                    // Add delay before fetching participants
                    Handler(Looper.getMainLooper()).postDelayed({
                        signaling.getParticipantsNow { list ->
                            Log.d(tag, "getParticipantsNow returned ${list.size} participants")
                            // Process participants with delays to avoid overwhelming the system
                            list.forEachIndexed { index, rid ->
                                if (rid == signaling.localId) return@forEachIndexed
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!peerConnections.containsKey(rid) && peerConnections.size < 5) {
                                        createPeerIfNeeded(rid, initiator = signaling.shouldInitiateTo(rid))
                                    }
                                }, index * 500L) // Stagger connections
                            }
                        }
                    }, 2000)

                    // Call onReady with proper error handling
                    onReady?.let { callback ->
                        initializationComplete.set(true)
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                callback()
                                Log.d(tag, "WebRTC initialization completed successfully")
                            } catch (e: Exception) {
                                Log.e(tag, "Error in onReady callback", e)
                            }
                        }, 3000) // Additional delay to ensure everything is stable
                    }

                } catch (e: Exception) {
                    Log.e(tag, "init error: ${e.message}", e)
                    // Still call onReady but with error state
                    onReady?.let { callback ->
                        Handler(Looper.getMainLooper()).post {
                            try { callback() } catch (ex: Exception) { Log.e(tag, "Error in error onReady", ex) }
                        }
                    }
                }
            }
        }
    }

    fun setOnParticipantsChanged(cb: (List<String>) -> Unit) { onParticipantsChanged = cb }
    fun getCurrentParticipants(cb: (List<String>) -> Unit) { signaling.getParticipantsNow(cb) }

    private fun createPeerIfNeeded(remoteId: String, initiator: Boolean = false) {
        if (peerConnections.containsKey(remoteId)) {
            Log.d(tag, "Peer connection already exists for $remoteId")
            return
        }

        executeTask {
            synchronized(peerConnectionLock) {
                try {
                    // Double-check after acquiring lock
                    if (peerConnections.containsKey(remoteId) || peerConnections.size >= 8) {
                        Log.w(tag, "Max peer connections reached, ignoring $remoteId")
                        return@executeTask
                    }

                    Log.d(tag, "Creating PeerConnection for $remoteId (initiator=$initiator)")
                    val pc = createPeerConnection(remoteId) ?: return@executeTask

                    // Add retry logic for initial connection failures
                    var connectionAttempts = 0
                    val maxConnectionAttempts = 2

                    while (connectionAttempts < maxConnectionAttempts) {
                        try {
                            val sendTrack = localAudioTrack
                            if (sendTrack != null) {
                                val sender = pc.addTrack(sendTrack, listOf("ARDAMS"))
                                try {
                                    val params = sender.parameters
                                    if (params.encodings.isNotEmpty()) {
                                        params.encodings[0].maxBitrateBps = 64000
                                        params.encodings[0].minBitrateBps = 16000
                                        sender.parameters = params
                                    }
                                } catch (e: Exception) {
                                    Log.w(tag, "set bitrate failed: ${e.message}")
                                }
                            }

                            peerConnections[remoteId] = pc

                            if (initiator) {
                                createOfferWithRetry(pc, remoteId)
                            }

                            Log.d(tag, "Successfully created peer connection for $remoteId (attempt ${connectionAttempts + 1})")
                            break // Success, exit retry loop

                        } catch (e: Exception) {
                            connectionAttempts++
                            Log.w(tag, "Peer connection setup failed for $remoteId (attempt $connectionAttempts): ${e.message}")

                            if (connectionAttempts >= maxConnectionAttempts) {
                                Log.e(tag, "Max connection attempts reached for $remoteId")
                                try { pc.close() } catch (closeEx: Exception) { }
                                peerConnections.remove(remoteId)
                                break
                            }

                            // Wait before retry
                            try { Thread.sleep(1000L) } catch (ie: InterruptedException) { break }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "createPeerIfNeeded error: ${e.message}", e)
                }
            }
        }
    }

    private fun createOfferWithRetry(pc: PeerConnection, remoteId: String, attempt: Int = 0) {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(tag, "Offer created for $remoteId (attempt ${attempt + 1})")
                pc.setLocalDescription(SimpleSdpObserver(), desc)
                signaling.sendSdp(remoteId, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(tag, "createOffer failed for $remoteId (attempt ${attempt + 1}): $error")

                if (attempt < 2) { // Max 3 attempts total (0,1,2)
                    Handler(Looper.getMainLooper()).postDelayed({
                        createOfferWithRetry(pc, remoteId, attempt + 1)
                    }, 1000L * (attempt + 1))
                } else {
                    onConnectionStatusChanged?.let { callback ->
                        android.os.Handler(Looper.getMainLooper()).post {
                            callback(remoteId, "Offer failed after ${attempt + 1} attempts")
                        }
                    }
                }
            }
            override fun onSetFailure(error: String?) {
                Log.e(tag, "setLocalDescription failed for $remoteId: $error")
            }
        }, sdpConstraints)
    }

    private fun createPeerConnection(remoteId: String): PeerConnection? {
        if (!initializationComplete.get()) {
            Log.w(tag, "Attempted to create peer connection before initialization complete")
            return null
        }

        return try {
            val f = factory ?: throw IllegalStateException("Factory not initialized")

            // Smart server selection based on network detection
            if (detectCGNAT() && !cgnatDetected) {
                cgnatDetected = true
                turnServerManager.enableCGNATMode()
                Log.w(tag, "CGNAT detected - enabling relay mode")
            }

            val iceServers = turnServerManager.getIceServersForCurrentTier().toMutableList()
            // Add emergency STUN servers as a fallback if the current tier is high
            try {
                if (turnServerManager.getCurrentTier() > 3) {
                    iceServers.addAll(turnServerManager.getEmergencyIceServers())
                }
            } catch (e: Exception) {
                Log.w(tag, "No emergency servers available or failed to add: ${e.message}")
            }

            val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
                tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
                bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
                rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
                continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
                // Gaming optimizations
                iceTransportsType = PeerConnection.IceTransportsType.ALL
                keyType = PeerConnection.KeyType.ECDSA

                // Aggressive timeouts for faster failover (set defensively)
                try {
                    val connTimeoutField = this::class.java.getDeclaredField("iceConnectionReceivingTimeout")
                    connTimeoutField.isAccessible = true
                    connTimeoutField.setInt(this, 10000)
                } catch (_: Throwable) {}

                try {
                    val inactiveTimeoutField = this::class.java.getDeclaredField("iceInactiveTimeout")
                    inactiveTimeoutField.isAccessible = true
                    inactiveTimeoutField.setInt(this, 15000)
                } catch (_: Throwable) {}
            }

            // Track connection attempt start time for timeout logic
            connectionTimeouts[remoteId] = System.currentTimeMillis()

            val pc = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                    Log.d(tag, "Signaling state for $remoteId: $state")
                }

                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    Log.d(tag, "ICE state for $remoteId: $state (TURN tier: ${turnServerManager.getCurrentTier()})")
                    when (state) {
                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            connectionTimeouts.remove(remoteId)
                            turnServerManager.resetToTier1()
                            Log.d(tag, "Connection successful for $remoteId, reset to tier 1")
                            connectionRetryCounts.remove(remoteId)
                            consecutiveFailures.remove(remoteId) // Reset failures on success
                        }

                        PeerConnection.IceConnectionState.FAILED,
                        PeerConnection.IceConnectionState.DISCONNECTED -> {
                            connectionTimeouts.remove(remoteId)

                            // Track consecutive failures for CGNAT detection
                            val failures = consecutiveFailures.getOrDefault(remoteId, 0) + 1
                            consecutiveFailures[remoteId] = failures

                            // Enable CGNAT mode after multiple failures
                            if (failures >= 2 && !cgnatDetected) {
                                cgnatDetected = true
                                turnServerManager.enableCGNATMode()
                                Log.w(tag, "Multiple failures detected - likely CGNAT, enabling relay mode")
                            }

                            turnServerManager.forceEscalateToNextTier()
                            Log.w(tag, "Connection failed for $remoteId, escalated to tier ${turnServerManager.getCurrentTier()}")

                            // USE THE FALLBACK FUNCTION WHEN TIER 4 FAILS
                            if (turnServerManager.getCurrentTier() >= 4 && failures >= 3) {
                                Log.w(tag, "All TURN tiers failed, attempting STUN fallback for $remoteId")
                                fallbackToStunOnlyConnection(remoteId)
                            } else if (turnServerManager.getCurrentTier() < 4) {
                                retryConnectionWithNewTier(remoteId)
                            }
                        }

                        else -> {
                            // If it remains in transitional states for too long, escalate
                            val startTime = connectionTimeouts[remoteId]
                            if (startTime != null &&
                                System.currentTimeMillis() - startTime > maxConnectionTimeoutMs &&
                                state != PeerConnection.IceConnectionState.CONNECTED &&
                                state != PeerConnection.IceConnectionState.COMPLETED) {

                                Log.w(tag, "Connection timeout for $remoteId after ${System.currentTimeMillis() - startTime}ms, escalating")
                                turnServerManager.forceEscalateToNextTier()
                                retryConnectionWithNewTier(remoteId)
                            }
                        }
                    }

                    onConnectionStatusChanged?.let { callback ->
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            val statusWithTier = "${state?.name ?: "Unknown"} (T${turnServerManager.getCurrentTier()})"
                            callback(remoteId, statusWithTier)
                        }
                    }
                }

                override fun onIceConnectionReceivingChange(receiving: Boolean) {
                    Log.d(tag, "ICE receiving for $remoteId: $receiving")
                }

                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                    Log.d(tag, "ICE gathering for $remoteId: $state")

                    if (state == PeerConnection.IceGatheringState.GATHERING) {
                        val start = connectionTimeouts[remoteId]
                        if (start != null) {
                            // If gathering takes too long, escalate to next tier
                            android.os.Handler(Looper.getMainLooper()).postDelayed({
                                val currentState = try { peerConnections[remoteId]?.iceGatheringState() } catch (_: Exception) { null }
                                if (currentState == PeerConnection.IceGatheringState.GATHERING) {
                                    Log.w(tag, "ICE gathering timeout for $remoteId, forcing escalation")
                                    turnServerManager.forceEscalateToNextTier()
                                }
                            }, 10_000L) // 10s gathering timeout
                        }
                    }
                }

                override fun onIceCandidate(candidate: IceCandidate) {
                    Log.d(tag, "onIceCandidate -> send to $remoteId")
                    signaling.sendIceCandidate(remoteId, candidate)
                }

                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
                override fun onAddStream(stream: MediaStream?) {}
                override fun onRemoveStream(stream: MediaStream?) {}
                override fun onDataChannel(dataChannel: DataChannel?) {}
                override fun onRenegotiationNeeded() {
                    Log.d(tag, "Renegotiation needed for $remoteId")
                }

                override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                    try {
                        val track = receiver?.track()
                        if (track is AudioTrack) {
                            Log.d(tag, "Remote audio track added for $remoteId")

                            // Ensure track is enabled
                            track.setEnabled(true)
                            remoteAudioTracks[remoteId] = track

                            // Apply volume immediately
                            applyVolumeToAudioTrack(track, receiveVolumeMultiplier)

                            // Force audio session refresh
                            try {
                                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                            } catch (e: Exception) {
                                Log.w(tag, "Audio mode refresh failed: ${e.message}")
                            }

                            Log.d(tag, "Remote audio track fully configured for $remoteId")
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "onAddTrack error: ${e.message}", e)
                    }
                }

                override fun onRemoveTrack(receiver: RtpReceiver?) {
                    remoteAudioTracks.remove(remoteId)
                    Log.d(tag, "onRemoveTrack for $remoteId")
                }
            })

            pc
        } catch (e: Exception) {
            Log.e(tag, "Failed to create peer connection for $remoteId", e)
            onConnectionStatusChanged?.invoke(remoteId, "Failed to connect")
            null
        }
    }


    // Retry connection with new TURN tier (close existing and create new peer)
    private fun retryConnectionWithNewTier(remoteId: String) {
        if (executor.isShutdown) {
            Log.w(tag, "Executor is shutdown, skipping retry for $remoteId")
            return
        }

        val retryCount = connectionRetryCounts.getOrDefault(remoteId, 0)
        if (retryCount >= 3) {
            Log.w(tag, "Max retries reached for $remoteId, falling back to STUN-only")
            connectionRetryCounts.remove(remoteId)
            fallbackToStunOnlyConnection(remoteId)
            return
        }

        connectionRetryCounts[remoteId] = retryCount + 1
        val backoffMs = 1000L * (1 shl retryCount) // 1s, 2s, 4s

        // Use scheduler for delay, then execute on main executor
        scheduler.schedule({
            executeTask {
                try {
                    Log.d(tag, "Retrying connection for $remoteId with TURN tier ${turnServerManager.getCurrentTier()}, attempt ${retryCount + 1}")

                    // Close existing connection
                    try {
                        peerConnections[remoteId]?.close()
                    } catch (e: Exception) {
                        Log.w(tag, "Error closing peer before retry: ${e.message}")
                    }
                    peerConnections.remove(remoteId)
                    remoteAudioTracks.remove(remoteId)

                    if (isEnding.get() || executor.isShutdown) {
                        Log.w(tag, "Abort retry for $remoteId because client is ending")
                        return@executeTask
                    }

                    createPeerIfNeeded(remoteId, initiator = signaling.shouldInitiateTo(remoteId))
                } catch (e: Exception) {
                    Log.e(tag, "Error retrying connection for $remoteId: ${e.message}", e)
                }
            }
        }, backoffMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun fallbackToStunOnlyConnection(remoteId: String) {
        executeTask {
            try {
                Log.w(tag, "Creating STUN-only connection for $remoteId as last resort")

                val stunOnlyServers = listOf(
                    PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
                    PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
                )

                val pc = createPeerConnectionWithCustomServers(remoteId, stunOnlyServers)
                if (pc != null) {
                    // Add local audio track
                    val sendTrack = localAudioTrack
                    if (sendTrack != null) {
                        pc.addTrack(sendTrack, listOf("ARDAMS"))
                    }

                    peerConnections[remoteId] = pc

                    if (signaling.shouldInitiateTo(remoteId)) {
                        createOfferForPeer(pc, remoteId)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "STUN fallback failed for $remoteId: ${e.message}", e)
            }
        }
    }

    private fun createPeerConnectionWithCustomServers(remoteId: String, iceServers: List<PeerConnection.IceServer>): PeerConnection? {
        val f = factory ?: return null

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            keyType = PeerConnection.KeyType.ECDSA
        }

        // USE THE UNUSED FUNCTION
        return f.createPeerConnection(rtcConfig, createPeerConnectionObserver(remoteId))
    }
    private fun createOfferForPeer(pc: PeerConnection, remoteId: String) {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(tag, "Offer created for $remoteId")
                pc.setLocalDescription(SimpleSdpObserver(), desc)
                signaling.sendSdp(remoteId, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) {
                Log.e(tag, "createOffer failed: $p0")
                onConnectionStatusChanged?.let { callback ->
                    android.os.Handler(Looper.getMainLooper()).post {
                        callback(remoteId, "Offer failed")
                    }
                }
            }
            override fun onSetFailure(p0: String?) {
                Log.e(tag, "setLocalDescription failed: $p0")
            }
        }, sdpConstraints)
    }
    private fun createPeerConnectionObserver(remoteId: String): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(tag, "Signaling state for $remoteId: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(tag, "ICE state for $remoteId: $state")
                when (state) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        connectionTimeouts.remove(remoteId)
                        turnServerManager.resetToTier1()
                        Log.d(tag, "Connection successful for $remoteId")
                        connectionRetryCounts.remove(remoteId)
                    }

                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        connectionTimeouts.remove(remoteId)

                        // Track failures for CGNAT detection
                        val failures = consecutiveFailures.getOrDefault(remoteId, 0) + 1
                        consecutiveFailures[remoteId] = failures

                        if (failures >= 2 && !cgnatDetected) {
                            cgnatDetected = true
                            turnServerManager.enableCGNATMode()
                            Log.w(tag, "Multiple failures detected - likely CGNAT, enabling relay mode")
                        }

                        turnServerManager.forceEscalateToNextTier()
                        Log.w(tag, "Connection failed for $remoteId, escalated to tier ${turnServerManager.getCurrentTier()}")

                        if (turnServerManager.getCurrentTier() < 4) {
                            retryConnectionWithNewTier(remoteId)
                        }
                    }

                    else -> {
                        val startTime = connectionTimeouts[remoteId]
                        if (startTime != null &&
                            System.currentTimeMillis() - startTime > maxConnectionTimeoutMs &&
                            state != PeerConnection.IceConnectionState.CONNECTED &&
                            state != PeerConnection.IceConnectionState.COMPLETED) {

                            Log.w(tag, "Connection timeout for $remoteId, escalating")
                            turnServerManager.forceEscalateToNextTier()
                            retryConnectionWithNewTier(remoteId)
                        }
                    }
                }

                onConnectionStatusChanged?.let { callback ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        val statusWithTier = "${state?.name ?: "Unknown"}"
                        callback(remoteId, statusWithTier)
                    }
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(tag, "ICE receiving for $remoteId: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(tag, "ICE gathering for $remoteId: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(tag, "onIceCandidate -> send to $remoteId")
                signaling.sendIceCandidate(remoteId, candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {
                Log.d(tag, "Renegotiation needed for $remoteId")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                try {
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        Log.d(tag, "Remote audio track added for $remoteId")
                        track.setEnabled(true)
                        remoteAudioTracks[remoteId] = track
                        applyVolumeToAudioTrack(track, receiveVolumeMultiplier)

                        try {
                            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                        } catch (e: Exception) {
                            Log.w(tag, "Audio mode refresh failed: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "onAddTrack error: ${e.message}", e)
                }
            }

            override fun onRemoveTrack(receiver: RtpReceiver?) {
                remoteAudioTracks.remove(remoteId)
                Log.d(tag, "onRemoveTrack for $remoteId")
            }
        }
    }

    private fun handleRemoteIce(fromId: String, payload: Map<String, Any?>, msgKey: String) {
        executeTask {
            try {
                val candidateStr = payload["candidate"] as? String ?: return@executeTask
                val sdpMid = payload["sdpMid"] as? String
                val sdpMLineIndex = (payload["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                Log.d(tag, "handleRemoteIce from=$fromId key=$msgKey")

                val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
                val pc = peerConnections[fromId]

                if (pc == null) {
                    Log.w(tag, "No peer connection for $fromId, cannot add ICE candidate")
                    return@executeTask
                }

                // CRITICAL: Check if remote description is set
                if (remoteDescriptionSet[fromId] == true) {
                    pc.addIceCandidate(candidate)
                    Log.d(tag, "Added ICE candidate for $fromId")
                } else {
                    // Queue candidates until remote description is set
                    pendingIceCandidates.getOrPut(fromId) { mutableListOf() }.add(candidate)
                    Log.d(tag, "Queued ICE candidate for $fromId (waiting for remote description)")
                }

                val to = payload["to"] as? String?
                if (to != null && to == signaling.localId) {
                    try {
                        signaling.deleteMessage(msgKey)
                        Log.d(tag, "Deleted direct ICE message $msgKey")
                    } catch (e: Exception) {
                        Log.w(tag, "deleteMessage failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "handleRemoteIce error: ${e.message}", e)
            }
        }
    }

    private fun handleRemoteSdp(fromId: String, payload: Map<String, Any?>, msgKey: String) {
        executeTask {
            try {
                val type = payload["sdpType"] as? String ?: return@executeTask
                val sdp = payload["sdp"] as? String ?: return@executeTask
                Log.d(tag, "handleRemoteSdp from=$fromId type=$type key=$msgKey")
                val descType = if (type.equals("offer", true))
                    SessionDescription.Type.OFFER
                else
                    SessionDescription.Type.ANSWER
                val sd = SessionDescription(descType, sdp)

                var pc = peerConnections[fromId]
                if (pc == null) {
                    Log.d(tag, "No pc for $fromId; creating (as answerer)")
                    createPeerIfNeeded(fromId, initiator = false)
                    pc = peerConnections[fromId]
                }

                // CRITICAL: Mark when setRemoteDescription starts
                pc?.setRemoteDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(tag, "Remote description set for $fromId")

                        // Mark as ready and process queued ICE candidates
                        remoteDescriptionSet[fromId] = true

                        val queued = pendingIceCandidates.remove(fromId)
                        if (!queued.isNullOrEmpty()) {
                            Log.d(tag, "Processing ${queued.size} queued ICE candidates for $fromId")
                            executeTask {
                                queued.forEach { candidate ->
                                    try {
                                        peerConnections[fromId]?.addIceCandidate(candidate)
                                    } catch (e: Exception) {
                                        Log.w(tag, "Failed to add queued ICE: ${e.message}")
                                    }
                                }
                            }
                        }
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(tag, "setRemoteDescription failed for $fromId: $error")
                        remoteDescriptionSet[fromId] = false
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sd)

                if (descType == SessionDescription.Type.OFFER) {
                    val answerConstraints = MediaConstraints().apply {
                        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                        optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
                    }

                    pc?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            Log.d(tag, "Answer created for $fromId")
                            pc.setLocalDescription(SimpleSdpObserver(), desc)
                            signaling.sendSdp(fromId, desc)
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) {
                            Log.e(tag, "createAnswer failed: $p0")
                            onConnectionStatusChanged?.let { callback ->
                                android.os.Handler(Looper.getMainLooper()).post {
                                    callback(fromId, "Answer failed")
                                }
                            }
                        }
                        override fun onSetFailure(p0: String?) {
                            Log.e(tag, "setLocalDescription failed: $p0")
                        }
                    }, answerConstraints)
                }

                val to = payload["to"] as? String?
                if (to != null && to == signaling.localId) {
                    try {
                        signaling.deleteMessage(msgKey)
                        Log.d(tag, "Deleted direct message $msgKey")
                    } catch (e: Exception) {
                        Log.w(tag, "deleteMessage failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "handleRemoteSdp error: ${e.message}", e)
            }
        }
    }

    private fun closePeer(remoteId: String) {
        executeTask {
            try {
                peerConnections.remove(remoteId)?.close()
                remoteAudioTracks.remove(remoteId)
                Log.d(tag, "Closed peer $remoteId")
                onConnectionStatusChanged?.let { callback ->
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        callback(remoteId, "Disconnected")
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "closePeer: ${e.message}")
            }
        }
    }


    fun toggleMute(): Boolean {
        localAudioTrack?.let {
            val newEnabled = !it.enabled()
            it.setEnabled(newEnabled)
            Log.d(tag, "toggleMute -> nowEnabled=$newEnabled")
            return !newEnabled
        }
        return false
    }

    fun setLocalMicEnabled(enabled: Boolean) {
        try {
            localAudioTrack?.setEnabled(enabled)
            Log.d(tag, "setLocalMicEnabled -> $enabled")
        } catch (e: Exception) {
            Log.w(tag, "setLocalMicEnabled failed: ${e.message}")
        }
    }

    fun setReceiveVolumeMultiplier(multiplier: Float) {
        receiveVolumeMultiplier = multiplier
        executeTask {
            for ((_, track) in remoteAudioTracks) {
                applyVolumeToAudioTrack(track, multiplier)
            }
        }
    }

    // Expose local audio track state for debugging
    fun isLocalMicEnabled(): Boolean {
        return localAudioTrack?.enabled() ?: false
    }

    // Get local audio track for debugging
    fun getLocalAudioTrack(): AudioTrack? {
        return localAudioTrack
    }

    // === TURN Server Debug Helpers ===
    fun getTurnServerStatus(): String {
        return turnServerManager.getStatusInfo()
    }

    fun forceTurnServerEscalation() {
        turnServerManager.forceEscalateToNextTier()
        Log.d(tag, "Manual TURN escalation requested -> now tier ${turnServerManager.getCurrentTier()}")
    }
    fun verifyAudioConnection(): String {
        return """
        WebRTC Audio Status:
        - Factory: ${factory != null}
        - Local Audio Source: ${audioSource != null}
        - Local Track: ${localAudioTrack != null}
        - Local Track Enabled: ${localAudioTrack?.enabled() ?: false}
        - Remote Tracks: ${remoteAudioTracks.size}
        - Peer Connections: ${peerConnections.size}
        - ICE Connected Peers: ${peerConnections.values.count {
            it.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED ||
                    it.iceConnectionState() == PeerConnection.IceConnectionState.COMPLETED
        }}
    """.trimIndent()
    }


    fun refreshAudioSession() {
        // Offload longer blocking waits to scheduler so executor isn't blocked
        try {
            // Short, synchronous disable/enable for local track on executor
            executeTask {
                try {
                    localAudioTrack?.setEnabled(false)
                } catch (e: Exception) {
                    Log.w(tag, "refreshAudioSession local disable failed: ${e.message}")
                }
            }

            // Re-enable after a short delay using scheduler
            scheduler.schedule({
                executeTask {
                    try {
                        localAudioTrack?.setEnabled(true)
                    } catch (e: Exception) {
                        Log.w(tag, "refreshAudioSession local enable failed: ${e.message}")
                    }
                }
            }, 60, TimeUnit.MILLISECONDS)

            // For remote tracks, toggle each with a small delay so players can refresh
            var delay = 0L
            for (t in remoteAudioTracks.values) {
                val track = t
                scheduler.schedule({
                    executeTask {
                        try {
                            track.setEnabled(false)
                        } catch (e: Exception) {}
                    }
                    // re-enable slightly later
                    scheduler.schedule({
                        executeTask {
                            try { track.setEnabled(true) } catch (e: Exception) {}
                        }
                    }, 40, TimeUnit.MILLISECONDS)
                }, delay, TimeUnit.MILLISECONDS)
                delay += 25L
            }

            Log.d(tag, "Audio session refresh scheduled")
        } catch (e: Exception) {
            Log.w(tag, "refreshAudioSession scheduling error: ${e.message}")
        }
    }


    // Replace applyVolumeToAudioTrack method
    private fun applyVolumeToAudioTrack(track: AudioTrack, multiplier: Float) {
        try {
            val clampedMultiplier = multiplier.coerceIn(0.0f, 2.0f)

            // Android 14+ WebRTC volume control - use track-based volume instead of system volume
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Try the new WebRTC volume API first
                try {
                    val setVolumeMethod = track.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType)
                    setVolumeMethod.invoke(track, clampedMultiplier)
                    Log.d(tag, "Android 14+: Applied volume $clampedMultiplier via new float API")
                    return
                } catch (e: NoSuchMethodException) {
                    Log.d(tag, "Float setVolume API not available, trying double")
                } catch (e: Exception) {
                    Log.w(tag, "Float setVolume API failed: ${e.message}")
                }

                // Try double API as fallback
                try {
                    val setVolumeMethod = track.javaClass.getMethod("setVolume", Double::class.javaPrimitiveType)
                    setVolumeMethod.invoke(track, clampedMultiplier.toDouble())
                    Log.d(tag, "Android 14+: Applied volume $clampedMultiplier via double API")
                    return
                } catch (e: NoSuchMethodException) {
                    Log.d(tag, "Double setVolume API not available, trying reflection")
                } catch (e: Exception) {
                    Log.w(tag, "Double setVolume API failed: ${e.message}")
                }
            } else {
                // Pre-Android 14: Try newer API first (WebRTC M114+)
                try {
                    val setVolumeMethod = track.javaClass.getMethod("setVolume", Double::class.javaPrimitiveType)
                    setVolumeMethod.invoke(track, clampedMultiplier.toDouble())
                    Log.d(tag, "Applied volume $clampedMultiplier via new API")
                    return
                } catch (e: NoSuchMethodException) {
                    Log.d(tag, "New setVolume API not available, trying legacy")
                }
            }

            // Legacy API
            try {
                val setVolumeMethod = track.javaClass.getDeclaredMethod("setVolume", Double::class.javaPrimitiveType)
                setVolumeMethod.isAccessible = true
                setVolumeMethod.invoke(track, clampedMultiplier.toDouble())
                Log.d(tag, "Applied volume $clampedMultiplier via reflection")
                return
            } catch (e: Exception) {
                Log.w(tag, "All volume methods failed: ${e.message}")
            }

            // Final fallback
            track.setEnabled(clampedMultiplier >= 0.1f)
            Log.d(tag, "Volume control unavailable, using enable/disable")

        } catch (e: Exception) {
            Log.e(tag, "applyVolumeToAudioTrack failed completely: ${e.message}", e)
        }
    }

    fun close() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }

        try {
            // First, prevent new tasks
            executor.shutdown()
            scheduler.shutdown()

            // Execute cleanup with proper synchronization
            synchronized(peerConnectionLock) {
                // Create a copy to avoid ConcurrentModificationException
                val peersToClose = peerConnections.values.toList()
                peerConnections.clear()
                peersToClose.forEach { pc ->
                    try { pc.close() } catch (e: Exception) { Log.w(tag, "Error closing peer: ${e.message}") }
                }
                remoteAudioTracks.clear()
                peersToClose.forEach { pc ->
                    try { pc.close() } catch (e: Exception) { Log.w(tag, "Error closing peer: ${e.message}") }
                }
                remoteAudioTracks.clear()

                try { audioSource?.dispose(); audioSource = null } catch (e: Exception) { Log.w(tag, "audioSource.dispose failed: ${e.message}") }
                try { localAudioTrack?.dispose(); localAudioTrack = null } catch (e: Exception) { Log.w(tag, "localAudioTrack.dispose failed: ${e.message}") }
                try { factory?.dispose(); factory = null } catch (e: Exception) { Log.w(tag, "factory.dispose failed: ${e.message}") }
                try { signaling.close() } catch (e: Exception) { Log.w(tag, "signaling.close failed: ${e.message}") }
            }

            // Force shutdown if needed
            try {
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    executor.shutdownNow()
                }
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow()
                }
            } catch (e: Exception) {
                try {
                    executor.shutdownNow()
                    scheduler.shutdownNow()
                } catch (inner: Exception) {
                    Log.w(tag, "Force shutdown failed: ${inner.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "close() error: ${e.message}")
        }
    }

    fun endCall() {
        if (isEnding.getAndSet(true)) {
            initializationComplete.set(false)
            Log.d(tag, "endCall already in progress")
            close() // Ensure cleanup happens
            return
        }

        isShuttingDown.set(true)


        try {
            executeTask {
                try {
                    synchronized(peerConnectionLock) {
                        val peersToClose = peerConnections.values.toList()
                        peerConnections.clear()
                        signaling.leave()
                        peersToClose.forEach { pc ->
                            try { pc.close() } catch (e: Exception) { Log.w(tag, "Error closing peer: ${e.message}") }
                        }
                        remoteAudioTracks.clear()
                        connectionTimeouts.clear()
                        connectionRetryCounts.clear()
                        consecutiveFailures.clear()
                    }

                    try { audioSource?.dispose(); audioSource = null } catch (e: Exception) { Log.w(tag, "audioSource.dispose failed: ${e.message}") }
                    try { localAudioTrack?.dispose(); localAudioTrack = null } catch (e: Exception) { Log.w(tag, "localAudioTrack.dispose failed: ${e.message}") }
                    try { signaling.close() } catch (e: Exception) { Log.w(tag, "signaling.close failed: ${e.message}") }

                    Log.d(tag, "endCall cleanup complete")
                } catch (e: Exception) {
                    Log.e(tag, "Error during endCall cleanup", e)
                }
                close() // Final cleanup
            }
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in endCall", e)
        }
    }
}
