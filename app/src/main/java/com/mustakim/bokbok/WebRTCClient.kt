package com.mustakim.bokbok

import android.content.Context
import android.media.AudioDeviceInfo
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
    private val isInitialized = AtomicBoolean(false)

    private val peerConnectionLock = Any()


    // --- NEW: TurnServerManager + connection monitoring fields ---
    private val turnServerManager = TurnServerManager()
    private val connectionTimeouts = ConcurrentHashMap<String, Long>()
    private val maxConnectionTimeoutMs = 15_000L // 15 seconds
    private val connectionRetryCounts = ConcurrentHashMap<String, Int>() // added for backoff logic
    private val isEnding = AtomicBoolean(false) // guard to avoid races during endCall()
    // CGNAT detection
    private var cgnatDetected = false

    private var onRemoteAudioTrackAdded: ((String) -> Unit)? = null
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

    fun setOnConnectionStatusChanged(callback: (String, String) -> Unit) {
        onConnectionStatusChanged = callback
    }

    fun init(onReady: (() -> Unit)? = null) {
        if (isInitialized.get()) {
            Log.d(tag, "WebRTCClient already initialized")
            onReady?.invoke()
            return
        }

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
                        val audioConstraints = MediaConstraints().apply {
                            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", if (noiseSuppressionEnabled) "true" else "false"))
                            optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                            optional.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
                            optional.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
                        }

                        audioSource = factory!!.createAudioSource(audioConstraints)
                        localAudioTrack = factory!!.createAudioTrack("ARDAMSa0", audioSource)
                        localAudioTrack?.setEnabled(true)
                        Log.d(tag, "Local audio track created (noiseSuppression=$noiseSuppressionEnabled)")
                    }

                    // Add small delay before starting signaling
                    Thread.sleep(300)

                    signaling.start { type, fromId, payload, msgKey ->
                        Log.d(tag, "Signal recv type=$type from=$fromId payloadKeys=${payload.keys}")
                        when (type) {
                            FirebaseSignaling.Type.SDP -> handleRemoteSdp(fromId, payload, msgKey)
                            FirebaseSignaling.Type.ICE -> handleRemoteIce(fromId, payload, msgKey)
                            FirebaseSignaling.Type.JOIN -> {
                                Log.d(tag, "Join message from $fromId")

                                // CRITICAL: Completely ignore self-join messages for peer creation
                                if (fromId == signaling.localId) {
                                    Log.d(tag, "Ignoring self-join message completely")
                                    return@start
                                }

                                // For remote joins, create peer with delay
                                Handler(Looper.getMainLooper()).postDelayed({
                                    executeTask {
                                        signaling.getParticipantsNow { participants ->
                                            if (participants.contains(fromId) && fromId != signaling.localId) {
                                                createPeerIfNeeded(fromId, initiator = signaling.shouldInitiateTo(fromId))
                                            } else {
                                                Log.w(tag, "Participant $fromId no longer in room or is self, skipping peer creation")
                                            }
                                        }
                                    }
                                }, 500) // Increased delay
                            }
                            FirebaseSignaling.Type.LEAVE -> {
                                Log.d(tag, "Leave message from $fromId -> closePeer")
                                if (fromId != signaling.localId) {
                                    closePeer(fromId)
                                }
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
                        }, 300)
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
                                }, index * 300L) // Stagger connections
                            }
                        }
                    }, 800)

                    //calls :
                    isInitialized.set(true)
                    initializationComplete.set(true)
                    startParticipantDiscovery()
                    startTrackMonitoring()
                    startConnectionHealthCheck()


                    onReady?.let { callback ->
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                callback()
                                Log.d(tag, "WebRTC initialization completed successfully")
                            } catch (e: Exception) {
                                Log.e(tag, "Error in onReady callback", e)
                            }
                        }, 1000) // Additional delay to ensure everything is stable
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
            // ADD these for better compatibility:
            optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
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
                        PeerConnection.IceConnectionState.CHECKING -> {
                            // If CHECKING persists, first attempt an ICE restart; if still stuck, recreate the peer
                            Handler(Looper.getMainLooper()).postDelayed({
                                val currentState = try { peerConnections[remoteId]?.iceConnectionState() } catch (_: Exception) { null }
                                if (currentState == PeerConnection.IceConnectionState.CHECKING) {
                                    Log.w(tag, "ICE stuck in CHECKING for $remoteId, attempting ICE restart then validate+repair.")
                                    // first try restart
                                    restartIceConnection(remoteId)
                                    handleIceConnectionStuck(remoteId)

                                    // schedule validation to recreate if ICE restart does not help
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        val stateAfterRestart = try { peerConnections[remoteId]?.iceConnectionState() } catch (_: Exception) { null }
                                        if (stateAfterRestart == PeerConnection.IceConnectionState.CHECKING) {
                                            Log.w(tag, "ICE still CHECKING after restart for $remoteId - validate and repair (recreate peer).")
                                            validateAndRepairConnection(remoteId)
                                        }
                                    }, 10000) // give restart up to 8s
                                }
                            }, 2500) // small initial delay so we don't react to short transient CHECKING spikes
                        }

                        PeerConnection.IceConnectionState.CONNECTED,
                        PeerConnection.IceConnectionState.COMPLETED -> {
                            Handler(Looper.getMainLooper()).postDelayed({
                                verifyRemoteTracksImmediately(remoteId)
                            }, 1000)
                            connectionTimeouts.remove(remoteId)
                            turnServerManager.resetToTier1()
                            Log.d(tag, "Connection successful for $remoteId, reset to tier 1")
                            connectionRetryCounts.remove(remoteId)
                            consecutiveFailures.remove(remoteId)
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
                override fun onAddStream(stream: MediaStream?) {
                    Log.w(tag, "onAddStream called for $remoteId. This is a fallback.")
                    stream?.audioTracks?.firstOrNull()?.let { audioTrack ->
                        Log.d(tag, "Found audio track in stream for $remoteId, processing it.")

                        audioTrack.setEnabled(true)
                        remoteAudioTracks[remoteId] = audioTrack
                        applyVolumeToAudioTrack(audioTrack, receiveVolumeMultiplier)

                        Handler(Looper.getMainLooper()).post {
                            refreshAudioSession()
                        }
                    }
                }
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

                            // REMOVED: The AudioManager manipulation that was fighting with CallActivity
                            // The CallActivity will handle audio routing via applyAudioRouting()

                            // Just apply volume and notify
                            applyVolumeToAudioTrack(track, receiveVolumeMultiplier)

                            // Notify CallActivity with a small delay to ensure track is ready
                            Handler(Looper.getMainLooper()).postDelayed({
                                onRemoteAudioTrackAdded?.invoke(remoteId)
                            }, 500)
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

    private fun restartIceConnection(remoteId: String) {
        executeTask {
            val pc = peerConnections[remoteId] ?: return@executeTask

            Log.d(tag, "Restarting ICE for $remoteId")

            // Create new offer with ice restart
            val constraints = MediaConstraints().apply {
                mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
                mandatory.add(MediaConstraints.KeyValuePair("IceRestart", "true"))
            }

            pc.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription) {
                    pc.setLocalDescription(SimpleSdpObserver(), desc)
                    signaling.sendSdp(remoteId, desc)
                    Log.d(tag, "ICE restart offer sent to $remoteId")
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(error: String?) {
                    Log.e(tag, "ICE restart failed: $error")
                }
                override fun onSetFailure(error: String?) {}
            }, constraints)
        }
    }

    // Retry connection with new TURN tier (close existing and create new peer)
    private fun retryConnectionWithNewTier(remoteId: String) {
        if (isShuttingDown.get() || isEnding.get()) {
            Log.w(tag, "Skipping retry - client is shutting down")
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

        try {
            scheduler.schedule({
                if (isShuttingDown.get() || isEnding.get()) {
                    Log.w(tag, "Abort scheduled retry for $remoteId - client is shutting down")
                    return@schedule
                }

                executeTask {
                    try {
                        Log.d(tag, "Retrying connection for $remoteId with TURN tier ${turnServerManager.getCurrentTier()}, attempt ${retryCount + 1}")

                        synchronized(peerConnectionLock) {
                            try {
                                peerConnections[remoteId]?.close()
                            } catch (e: Exception) {
                                Log.w(tag, "Error closing peer before retry: ${e.message}")
                            }
                            peerConnections.remove(remoteId)
                            remoteAudioTracks.remove(remoteId)
                            remoteDescriptionSet.remove(remoteId)
                            pendingIceCandidates.remove(remoteId)
                        }

                        // Small delay before recreating
                        Thread.sleep(500)

                        if (!isEnding.get() && !isShuttingDown.get()) {
                            createPeerIfNeeded(remoteId, initiator = signaling.shouldInitiateTo(remoteId))
                        }
                    } catch (e: Exception) {
                        Log.e(tag, "Error retrying connection for $remoteId: ${e.message}", e)
                    }
                }
            }, backoffMs, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Log.e(tag, "Failed to schedule retry for $remoteId: ${e.message}", e)
        }
    }

    private fun startParticipantDiscovery() {
        // Periodic participant discovery
        val discoveryHandler = Handler(Looper.getMainLooper())
        val discoveryRunnable = object : Runnable {
            override fun run() {
                if (isShuttingDown.get()) return

                signaling.getParticipantsNow { participants ->
                    val remoteParticipants = participants.filter { it != signaling.localId }
                    Log.d(tag, "Periodic participant discovery: ${remoteParticipants.size} remote participants")

                    if (remoteParticipants.isNotEmpty()) {
                        onParticipantsChanged?.invoke(remoteParticipants)

                        // Create peer connections for new participants
                        remoteParticipants.forEach { participantId ->
                            if (!peerConnections.containsKey(participantId) && peerConnections.size < 5) {
                                Handler(Looper.getMainLooper()).postDelayed({
                                    createPeerIfNeeded(participantId, initiator = signaling.shouldInitiateTo(participantId))
                                }, 1000)
                            }
                        }
                    }
                }

                // Run every 10 seconds
                if (!isShuttingDown.get()) {
                    discoveryHandler.postDelayed(this, 10000)
                }
            }
        }

        // Start discovery after initial delay
        discoveryHandler.postDelayed(discoveryRunnable, 5000)
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
                    PeerConnection.IceConnectionState.CHECKING -> {
                        // If CHECKING persists, first attempt an ICE restart; if still stuck, recreate the peer
                        Handler(Looper.getMainLooper()).postDelayed({
                            val currentState = try { peerConnections[remoteId]?.iceConnectionState() } catch (_: Exception) { null }
                            if (currentState == PeerConnection.IceConnectionState.CHECKING) {
                                Log.w(tag, "ICE stuck in CHECKING for $remoteId, attempting ICE restart then validate+repair.")
                                // first try restart
                                restartIceConnection(remoteId)

                                // schedule validation to recreate if ICE restart does not help
                                Handler(Looper.getMainLooper()).postDelayed({
                                    val stateAfterRestart = try { peerConnections[remoteId]?.iceConnectionState() } catch (_: Exception) { null }
                                    if (stateAfterRestart == PeerConnection.IceConnectionState.CHECKING) {
                                        Log.w(tag, "ICE still CHECKING after restart for $remoteId - validate and repair (recreate peer).")
                                        validateAndRepairConnection(remoteId)
                                    }
                                }, 8000) // give restart up to 8s
                            }
                        }, 2500) // small initial delay so we don't react to short transient CHECKING spikes
                    }

                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {

                        Handler(Looper.getMainLooper()).postDelayed({
                            verifyRemoteTracksImmediately(remoteId)
                        }, 1000)
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
            override fun onAddStream(stream: MediaStream?) {
                Log.w(tag, "onAddStream called for $remoteId. This is a fallback.")
                stream?.audioTracks?.firstOrNull()?.let { audioTrack ->
                    Log.d(tag, "Found audio track in stream for $remoteId, processing it.")

                    audioTrack.setEnabled(true)
                    remoteAudioTracks[remoteId] = audioTrack
                    applyVolumeToAudioTrack(audioTrack, receiveVolumeMultiplier)

                    Handler(Looper.getMainLooper()).post {
                        refreshAudioSession()
                    }
                }
            }
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {
                Log.d(tag, "Renegotiation needed for $remoteId")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                try {
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        Log.d(tag, "Remote audio track added for $remoteId (fallback observer)")

                        track.setEnabled(true)
                        remoteAudioTracks[remoteId] = track

                        // REMOVED: AudioManager manipulation

                        applyVolumeToAudioTrack(track, receiveVolumeMultiplier)

                        Handler(Looper.getMainLooper()).postDelayed({
                            onRemoteAudioTrackAdded?.invoke(remoteId)
                        }, 500)
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

    private fun verifyRemoteTracksImmediately(remoteId: String) {
        Log.d(tag, "Immediately verifying remote tracks for $remoteId")

        val pc = peerConnections[remoteId] ?: return

        try {
            val receivers = pc.receivers
            Log.d(tag, "Found ${receivers.size} receivers for $remoteId")

            var audioTrackFound = false
            for (receiver in receivers) {
                val track = receiver.track()
                Log.d(tag, "Receiver track: ${track?.kind()} - enabled: ${track?.enabled()}")

                if (track is AudioTrack) {
                    audioTrackFound = true
                    if (!remoteAudioTracks.containsKey(remoteId)) {
                        Log.w(tag, "Audio track found but not in map - adding now")
                        track.setEnabled(true)
                        remoteAudioTracks[remoteId] = track
                        applyVolumeToAudioTrack(track, receiveVolumeMultiplier)

                        Handler(Looper.getMainLooper()).post {
                            refreshAudioSession()
                        }
                    }
                    break
                }
            }

            if (!audioTrackFound) {
                Log.e(tag, "NO AUDIO TRACK FOUND in receivers for $remoteId!")
            }

        } catch (e: Exception) {
            Log.e(tag, "Track verification error: ${e.message}", e)
        }
    }

    private fun ensureRemoteTrackEnabled(remoteId: String) {
        val track = remoteAudioTracks[remoteId]
        if (track == null) {
            Log.w(tag, "No remote track found for $remoteId")
            return
        }

        if (!track.enabled()) {
            Log.w(tag, "Remote track was disabled - re-enabling for $remoteId")
            track.setEnabled(true)
            applyVolumeToAudioTrack(track, receiveVolumeMultiplier)

            // Force audio session refresh
            Handler(Looper.getMainLooper()).postDelayed({
                refreshAudioSession()
            }, 100)
        } else {
            Log.d(tag, "Remote track for $remoteId is already enabled")
        }
    }

    private fun startConnectionHealthCheck() {
        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                if (isShuttingDown.get()) return
                peerConnections.forEach { (remoteId, _) ->
                    try {
                        val pc = peerConnections[remoteId] ?: return@forEach
                        val ice = try { pc.iceConnectionState() } catch (e: Exception) { null }
                        val sig = try { pc.signalingState() } catch (e: Exception) { null }
                        Log.d(tag, "HealthCheck $remoteId: ICE=$ice SIGNALING=$sig (tier=${turnServerManager.getCurrentTier()})")

                        if (ice == PeerConnection.IceConnectionState.CHECKING || ice == PeerConnection.IceConnectionState.DISCONNECTED) {
                            validateAndRepairConnection(remoteId)
                        }
                    } catch (e: Exception) {
                        Log.w(tag, "HealthCheck error for $remoteId: ${e.message}")
                    }
                }
                handler.postDelayed(this, 10_000L)
            }
        }
        handler.postDelayed(runnable, 7_000L) // start after short delay
    }


    private fun startTrackMonitoring() {
        val handler = Handler(Looper.getMainLooper())
        val monitorRunnable = object : Runnable {
            override fun run() {
                if (isShuttingDown.get()) return

                peerConnections.forEach { (remoteId, pc) ->
                    if ((pc.iceConnectionState() == PeerConnection.IceConnectionState.CONNECTED ||
                                pc.iceConnectionState() == PeerConnection.IceConnectionState.COMPLETED) &&
                        !remoteAudioTracks.containsKey(remoteId)) {

                        Log.w(tag, "Connected peer $remoteId has no remote track - attempting recovery")
                        verifyRemoteTracksImmediately(remoteId)
                    }
                }

                if (!isShuttingDown.get()) {
                    handler.postDelayed(this, 5000)
                }
            }
        }
        handler.postDelayed(monitorRunnable, 3000)
    }

    private fun validateAndRepairConnection(remoteId: String) {
        val pc = peerConnections[remoteId] ?: return

        executeTask {
            try {
                val iceState = try { pc.iceConnectionState() } catch (e: Exception) { null }
                val signalingState = try { pc.signalingState() } catch (e: Exception) { null }
                val startedAt = connectionTimeouts[remoteId] ?: 0L
                val now = System.currentTimeMillis()

                Log.d(tag, "validateAndRepairConnection for $remoteId: ICE=$iceState, SIGNALING=$signalingState, startedAt=$startedAt")

                // If ICE stuck in CHECKING for > 8-12s or signaling closed, recreate
                val stuckTooLong = (iceState == PeerConnection.IceConnectionState.CHECKING && now - startedAt > 12_000L)
                if (stuckTooLong || signalingState == PeerConnection.SignalingState.CLOSED) {
                    Log.w(tag, "Connection validation failed for $remoteId (stuck=${stuckTooLong}), recreating peer.")
                    try {
                        peerConnections[remoteId]?.close()
                    } catch (e: Exception) { Log.w(tag, "close() failed during validate: ${e.message}") }
                    peerConnections.remove(remoteId)
                    remoteAudioTracks.remove(remoteId)
                    remoteDescriptionSet.remove(remoteId)
                    pendingIceCandidates.remove(remoteId)

                    // Slight delay before recreating to allow remote to settle
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isShuttingDown.get() && !isEnding.get()) {
                            createPeerIfNeeded(remoteId, initiator = signaling.shouldInitiateTo(remoteId))
                        }
                    }, 800)
                }
            } catch (e: Exception) {
                Log.w(tag, "validateAndRepairConnection error: ${e.message}")
            }
        }
    }


    private fun handleRemoteIce(fromId: String, payload: Map<String, Any?>, msgKey: String) {
        executeTask {
            try {
                val candidateStr = payload["candidate"] as? String ?: return@executeTask
                val sdpMid = payload["sdpMid"] as? String
                val sdpMLineIndex = (payload["sdpMLineIndex"] as? Long)?.toInt() ?: 0

                val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)

                synchronized(peerConnectionLock) {
                    val pc = peerConnections[fromId]

                    if (pc == null || remoteDescriptionSet[fromId] != true) {
                        // Queue the candidate
                        pendingIceCandidates.getOrPut(fromId) { mutableListOf() }.add(candidate)
                        Log.d(tag, "Queued ICE candidate for $fromId")
                    } else {
                        // Add immediately
                        try {
                            pc.addIceCandidate(candidate)
                            Log.d(tag, "Added ICE candidate for $fromId")
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to add ICE: ${e.message}")
                            // Try to queue for later
                            pendingIceCandidates.getOrPut(fromId) { mutableListOf() }.add(candidate)
                        }
                    }
                }

                // Delete processed message
                val to = payload["to"] as? String
                if (to == signaling.localId) {
                    signaling.deleteMessage(msgKey)
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

                // CRITICAL FIX: Better signaling collision resolution
                var pc = peerConnections[fromId]

                if (pc == null) {
                    Log.d(tag, "No PC for $fromId; creating as answerer")
                    createPeerIfNeeded(fromId, initiator = false)
                    pc = peerConnections[fromId]
                }

                pc?.let { peerConnection ->
                    try {
                        val currentState = try { peerConnection.signalingState() } catch (e: Exception) { null }

                        // IMPROVED COLLISION HANDLING: If we have local offer and receive remote offer,
                        // close current PC and create new one as answerer
                        if (currentState == PeerConnection.SignalingState.HAVE_LOCAL_OFFER &&
                            descType == SessionDescription.Type.OFFER) {

                            Log.w(tag, "Signaling collision for $fromId: HAVE_LOCAL_OFFER + remote OFFER. Resolution: close and recreate as answerer.")

                            // Close current connection immediately
                            closePeer(fromId)

                            // Clear any pending state
                            remoteDescriptionSet.remove(fromId)
                            pendingIceCandidates.remove(fromId)

                            // Wait a bit longer to ensure clean state
                            Handler(Looper.getMainLooper()).postDelayed({
                                executeTask {
                                    if (!isShuttingDown.get() && !isEnding.get()) {
                                        Log.d(tag, "Recreating PC for $fromId as answerer after collision")
                                        createPeerIfNeeded(fromId, initiator = false)

                                        // Process the offer with the new PC after delay
                                        Handler(Looper.getMainLooper()).postDelayed({
                                            executeTask {
                                                val newPc = peerConnections[fromId]
                                                if (newPc != null) {
                                                    Log.d(tag, "Processing deferred offer for $fromId")
                                                    setRemoteDescriptionAndAnswer(newPc, fromId, sd)
                                                }
                                            }
                                        }, 800)
                                    }
                                }
                            }, 1000) // Increased delay for better state cleanup
                            return@let
                        }

                        // Normal flow - no collision
                        if (currentState != PeerConnection.SignalingState.CLOSED) {
                            setRemoteDescriptionAndAnswer(peerConnection, fromId, sd)
                        }

                    } catch (e: Exception) {
                        Log.w(tag, "Error checking signalingState: ${e.message}")
                        // Fallback to normal flow
                        setRemoteDescriptionAndAnswer(peerConnection, fromId, sd)
                    }
                }

                // Delete processed message
                val to = payload["to"] as? String
                if (to == signaling.localId) {
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

    // NEW METHOD: Extract remote description setting and answer creation
    private fun setRemoteDescriptionAndAnswer(pc: PeerConnection, remoteId: String, sd: SessionDescription) {
        pc.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(tag, "Remote description set for $remoteId")
                remoteDescriptionSet[remoteId] = true

                // Process queued ICE candidates
                val queued = pendingIceCandidates.remove(remoteId)
                if (!queued.isNullOrEmpty()) {
                    Log.d(tag, "Processing ${queued.size} queued ICE candidates for $remoteId")
                    executeTask {
                        queued.forEach { candidate ->
                            try {
                                pc.addIceCandidate(candidate)
                            } catch (e: Exception) {
                                Log.w(tag, "Failed to add queued ICE: ${e.message}")
                            }
                        }
                    }
                }

                // Create answer if this was an offer
                if (sd.type == SessionDescription.Type.OFFER) {
                    createAnswerForRemote(pc, remoteId)
                }
            }

            override fun onSetFailure(error: String?) {
                Log.e(tag, "setRemoteDescription failed for $remoteId: $error")
                remoteDescriptionSet[remoteId] = false

                // Schedule retry for transient failures
                if (error?.contains("TRANSIENT") == true || error?.contains("retry") == true) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isShuttingDown.get() && peerConnections.containsKey(remoteId)) {
                            Log.d(tag, "Retrying setRemoteDescription for $remoteId")
                            setRemoteDescriptionAndAnswer(pc, remoteId, sd)
                        }
                    }, 1000)
                }
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, sd)
    }

    // NEW METHOD: Dedicated answer creation
    private fun createAnswerForRemote(pc: PeerConnection, remoteId: String) {
        val answerConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        }

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription) {
                Log.d(tag, "Answer created for $remoteId")
                pc.setLocalDescription(SimpleSdpObserver(), desc)
                signaling.sendSdp(remoteId, desc)

                // Notify connection attempt
                onConnectionStatusChanged?.let { callback ->
                    Handler(Looper.getMainLooper()).post {
                        callback(remoteId, "Answer created and sent")
                    }
                }
            }

            override fun onSetSuccess() {
                Log.d(tag, "Local description set for answer to $remoteId")
            }

            override fun onCreateFailure(error: String?) {
                Log.e(tag, "createAnswer failed for $remoteId: $error")
                onConnectionStatusChanged?.let { callback ->
                    Handler(Looper.getMainLooper()).post {
                        callback(remoteId, "Answer failed: $error")
                    }
                }

                // Retry answer creation
                if (!isShuttingDown.get() && peerConnections.containsKey(remoteId)) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        Log.d(tag, "Retrying answer creation for $remoteId")
                        createAnswerForRemote(pc, remoteId)
                    }, 2000)
                }
            }

            override fun onSetFailure(error: String?) {
                Log.e(tag, "setLocalDescription failed for answer to $remoteId: $error")
            }
        }, answerConstraints)
    }

    private fun handleIceConnectionStuck(remoteId: String) {
        val pc = peerConnections[remoteId] ?: return
        val startTime = connectionTimeouts[remoteId] ?: System.currentTimeMillis()
        val stuckTime = System.currentTimeMillis() - startTime

        if (stuckTime > 15000) { // 15 seconds stuck
            Log.w(tag, "ICE connection stuck for $remoteId for ${stuckTime}ms, forcing recreation")

            executeTask {
                synchronized(peerConnectionLock) {
                    try {
                        pc.close()
                    } catch (e: Exception) {
                        Log.w(tag, "Error closing stuck peer: ${e.message}")
                    }
                    peerConnections.remove(remoteId)
                    remoteAudioTracks.remove(remoteId)
                    remoteDescriptionSet.remove(remoteId)
                    pendingIceCandidates.remove(remoteId)

                    // Escalate TURN tier
                    turnServerManager.forceEscalateToNextTier()

                    // Recreate after delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        if (!isShuttingDown.get() && !isEnding.get()) {
                            Log.d(tag, "Recreating peer for $remoteId after stuck detection")
                            createPeerIfNeeded(remoteId, initiator = signaling.shouldInitiateTo(remoteId))
                        }
                    }, 2000)
                }
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

    fun setOnRemoteAudioTrackAdded(callback: (String) -> Unit) {
        onRemoteAudioTrackAdded = callback
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

    fun getRemoteTrackCount(): Int {
        return remoteAudioTracks.size
    }

    fun getRemoteTrackInfo(): String {
        val info = StringBuilder()
        info.append("Remote Tracks: ${remoteAudioTracks.size}\n")
        remoteAudioTracks.forEach { (id, track) ->
            info.append("  - $id: enabled=${track.enabled()}, kind=${track.kind()}\n")
        }
        return info.toString()
    }

    fun refreshAudioSession() {
        try {
            executeTask {
                try {
                    // Simple disable/enable for local track
                    localAudioTrack?.setEnabled(false)
                    Thread.sleep(50)
                    localAudioTrack?.setEnabled(true)

                    // For remote tracks, just verify they're enabled
                    remoteAudioTracks.values.forEach { track ->
                        if (!track.enabled()) {
                            track.setEnabled(true)
                            Log.d(tag, "Re-enabled remote track")
                        }
                    }

                    Log.d(tag, "Audio session refreshed")
                } catch (e: Exception) {
                    Log.w(tag, "refreshAudioSession failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "refreshAudioSession error: ${e.message}")
        }
    }


    // Replace applyVolumeToAudioTrack method
    private fun applyVolumeToAudioTrack(track: AudioTrack, multiplier: Float) {
        try {
            // Never go below 0.1 to avoid complete silence
            val clampedMultiplier = multiplier.coerceIn(0.1f, 2.0f)
            var volumeApplied = false

            // Android 14+ WebRTC volume control
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Try float API first
                try {
                    val setVolumeMethod = track.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType)
                    setVolumeMethod.invoke(track, clampedMultiplier)
                    Log.d(tag, "Android 14+: Applied volume $clampedMultiplier via float API")
                    volumeApplied = true
                } catch (e: NoSuchMethodException) {
                    Log.d(tag, "Float setVolume API not available, trying double")
                } catch (e: Exception) {
                    Log.w(tag, "Float setVolume API failed: ${e.message}")
                }

                // Try double API as fallback
                if (!volumeApplied) {
                    try {
                        val setVolumeMethod = track.javaClass.getMethod("setVolume", Double::class.javaPrimitiveType)
                        setVolumeMethod.invoke(track, clampedMultiplier.toDouble())
                        Log.d(tag, "Android 14+: Applied volume $clampedMultiplier via double API")
                        volumeApplied = true
                    } catch (e: Exception) {
                        Log.w(tag, "Double setVolume API failed: ${e.message}")
                    }
                }
            } else {
                // Pre-Android 14: Try newer API first
                try {
                    val setVolumeMethod = track.javaClass.getMethod("setVolume", Double::class.javaPrimitiveType)
                    setVolumeMethod.invoke(track, clampedMultiplier.toDouble())
                    Log.d(tag, "Applied volume $clampedMultiplier via new API")
                    volumeApplied = true
                } catch (e: NoSuchMethodException) {
                    Log.d(tag, "New setVolume API not available, trying legacy")
                } catch (e: Exception) {
                    Log.w(tag, "New setVolume API failed: ${e.message}")
                }
            }

            // Legacy reflection fallback
            if (!volumeApplied) {
                try {
                    val setVolumeMethod = track.javaClass.getDeclaredMethod("setVolume", Double::class.javaPrimitiveType)
                    setVolumeMethod.isAccessible = true
                    setVolumeMethod.invoke(track, clampedMultiplier.toDouble())
                    Log.d(tag, "Applied volume $clampedMultiplier via reflection")
                    volumeApplied = true
                } catch (e: Exception) {
                    Log.w(tag, "Legacy reflection failed: ${e.message}")
                }
            }

            // Final fallback - system volume control
            if (!volumeApplied) {
                Log.w(tag, "All WebRTC volume methods failed, using system volume as last resort")
                track.setEnabled(true) // Ensure track stays enabled

                try {
                    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    val targetVol = (maxVol * clampedMultiplier).toInt().coerceIn(1, maxVol)
                    am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, targetVol, 0)
                    Log.d(tag, "Applied system volume: $targetVol/$maxVol")
                } catch (e: Exception) {
                    Log.e(tag, "System volume adjustment also failed: ${e.message}")
                }
            }

        } catch (e: Exception) {
            Log.e(tag, "applyVolumeToAudioTrack failed completely: ${e.message}", e)
            // Emergency fallback - just ensure track is enabled
            try {
                track.setEnabled(true)
            } catch (e2: Exception) {
                Log.e(tag, "Even track.setEnabled failed: ${e2.message}")
            }
        }
    }

    // In WebRTCClient.kt, improve the close() method:
    fun close() {
        if (isShuttingDown.getAndSet(true)) {
            return
        }

        Log.d(tag, "WebRTCClient closing...")

        try {
            // Shutdown executors first to prevent new tasks
            executor.shutdown()
            scheduler.shutdown()

            // Wait for existing tasks to complete with timeout
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            scheduler.shutdownNow()
            Thread.currentThread().interrupt()
        }

        // Then clean up resources
        synchronized(peerConnectionLock) {
            try {
                // Close all peer connections
                peerConnections.values.forEach { pc ->
                    try {
                        pc.close()
                    } catch (e: Exception) {
                        Log.w(tag, "Error closing peer connection: ${e.message}")
                    }
                }
                peerConnections.clear()
                remoteAudioTracks.clear()

                // Dispose of media resources
                try {
                    localAudioTrack?.dispose()
                    audioSource?.dispose()
                    factory?.dispose()
                } catch (e: Exception) {
                    Log.w(tag, "Error disposing media resources: ${e.message}")
                }

                localAudioTrack = null
                audioSource = null
                factory = null

                // Close signaling
                try {
                    signaling.close()
                } catch (e: Exception) {
                    Log.w(tag, "Error closing signaling: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e(tag, "Error during WebRTC cleanup", e)
            }
        }

        Log.d(tag, "WebRTCClient closed completely")
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
