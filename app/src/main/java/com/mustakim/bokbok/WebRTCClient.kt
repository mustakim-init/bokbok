package com.mustakim.bokbok

import android.content.Context
import android.media.AudioManager
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
    private val signaling = FirebaseSignaling(roomId)

    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val remoteAudioTracks = ConcurrentHashMap<String, AudioTrack>()

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
    // -------------------------------------------------------------

    fun setNoiseSuppression(enabled: Boolean) {
        noiseSuppressionEnabled = enabled
        Log.d(tag, "Noise suppression set to $enabled (restart call to fully re-create audio source)")
    }

    fun setOnConnectionStatusChanged(callback: (String, String) -> Unit) {
        onConnectionStatusChanged = callback
    }

    fun init(onReady: (() -> Unit)? = null) {
        signaling.whenAuthReady {
            executor.execute {
                try {
                    if (factory == null) {
                        PeerConnectionFactory.initialize(
                            PeerConnectionFactory.InitializationOptions.builder(context.applicationContext).createInitializationOptions()
                        )
                        val opts = PeerConnectionFactory.Options()
                        factory = PeerConnectionFactory.builder().setOptions(opts).createPeerConnectionFactory()
                        Log.d(tag, "PeerConnectionFactory created")
                    }

                    if (audioSource == null && factory != null) {
                        val audioConstraints = MediaConstraints()
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", if (noiseSuppressionEnabled) "true" else "false"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                        // Gaming optimizations
                        audioConstraints.optional.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
                        audioConstraints.optional.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))

                        audioSource = factory!!.createAudioSource(audioConstraints)
                        localAudioTrack = factory!!.createAudioTrack("ARDAMSa0", audioSource)
                        localAudioTrack?.setEnabled(true)
                        Log.d(tag, "Local audio track created (noiseSuppression=$noiseSuppressionEnabled)")
                    }

                    signaling.start { type, fromId, payload, msgKey ->
                        Log.d(tag, "Signal recv type=$type from=$fromId payloadKeys=${payload.keys}")
                        when (type) {
                            FirebaseSignaling.Type.SDP -> handleRemoteSdp(fromId, payload, msgKey)
                            FirebaseSignaling.Type.ICE -> handleRemoteIce(fromId, payload, msgKey)
                            FirebaseSignaling.Type.JOIN -> {
                                Log.d(tag, "Join message from $fromId -> createPeerIfNeeded")
                                createPeerIfNeeded(fromId, initiator = signaling.shouldInitiateTo(fromId))
                            }
                            FirebaseSignaling.Type.LEAVE -> {
                                Log.d(tag, "Leave message from $fromId -> closePeer")
                                closePeer(fromId)
                            }
                        }
                    }

                    signaling.onParticipantsChanged { list ->
                        onParticipantsChanged?.invoke(list)
                        for (rid in list) {
                            if (rid == signaling.localId) continue
                            if (!peerConnections.containsKey(rid) && peerConnections.size < 5) {
                                createPeerIfNeeded(rid, initiator = signaling.shouldInitiateTo(rid))
                            }
                        }
                    }

                    val joined = signaling.join()
                    Log.d(tag, "join() returned $joined")

                    signaling.getParticipantsNow { list ->
                        Log.d(tag, "getParticipantsNow returned ${list.size}")
                        for (rid in list) {
                            if (rid == signaling.localId) continue
                            if (!peerConnections.containsKey(rid) && peerConnections.size < 5) {
                                createPeerIfNeeded(rid, initiator = signaling.shouldInitiateTo(rid))
                            }
                        }
                    }

                    // Add null check before calling onReady
                    onReady?.let { callback ->
                        Handler(Looper.getMainLooper()).post(object : Runnable {
                            override fun run() {
                                try {
                                    callback()
                                } catch (e: Exception) {
                                    Log.e(tag, "Error in onReady callback", e)
                                }
                            }
                        })
                    }
                    // Removed explicit Unit return here
                } catch (e: Exception) {
                    Log.e(tag, "init error: ${e.message}", e)
                    // Don't call onReady on error to prevent crashes
                }
            }
        }
    }

    fun setOnParticipantsChanged(cb: (List<String>) -> Unit) { onParticipantsChanged = cb }
    fun getCurrentParticipants(cb: (List<String>) -> Unit) { signaling.getParticipantsNow(cb) }

    private fun createPeerIfNeeded(remoteId: String, initiator: Boolean = false) {
        executor.execute {
            synchronized(peerConnectionLock) {
                try {
                    if (peerConnections.size >= 8) {  // Reasonable limit for mobile
                        Log.w(tag, "Max peer connections reached, ignoring $remoteId")
                        return@execute
                    }
                    if (peerConnections.containsKey(remoteId)) return@execute
                    Log.d(tag, "Creating PeerConnection for $remoteId (initiator=$initiator)")
                    val pc = createPeerConnection(remoteId) ?: return@execute

                    val sendTrack = localAudioTrack
                    if (sendTrack != null) {
                        val sender = pc.addTrack(sendTrack, listOf("ARDAMS"))
                        try {
                            val params = sender.parameters
                            if (params.encodings.isNotEmpty()) {
                                // Gaming-optimized bitrates
                                params.encodings[0].maxBitrateBps = 64000  // Higher quality
                                params.encodings[0].minBitrateBps = 16000
                                sender.parameters = params
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "set bitrate failed: ${e.message}")
                        }
                    }

                    peerConnections[remoteId] = pc

                    if (initiator) {
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
                } catch (e: Exception) {
                    Log.e(tag, "createPeerIfNeeded error: ${e.message}", e)
                }
            }
        }
    }

    private fun createPeerConnection(remoteId: String): PeerConnection? {
        val f = factory ?: return null

        // ---------- PATCHED: Use TurnServerManager to provide STUN + TURN servers ----------
        val iceServers = turnServerManager.getIceServersForCurrentTier().toMutableList()
        // Add emergency STUN servers as a fallback if the current tier is high
        try {
            if (turnServerManager.getCurrentTier() > 3) {
                iceServers.addAll(turnServerManager.getEmergencyIceServers())
            }
        } catch (e: Exception) {
            Log.w(tag, "No emergency servers available or failed to add: ${e.message}")
        }
        // ----------------------------------------------------------------------------------

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            // Gaming optimizations
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            keyType = PeerConnection.KeyType.ECDSA

            // Aggressive timeouts for faster failover (we will monitor & retry)
            try {
                // These fields may not exist on all WebRTC builds; set defensively
                val connTimeoutField = this::class.java.getDeclaredField("iceConnectionReceivingTimeout")
                connTimeoutField.isAccessible = true
                connTimeoutField.setInt(this, 5000)
            } catch (_: Throwable) {}

            try {
                val inactiveTimeoutField = this::class.java.getDeclaredField("iceInactiveTimeout")
                inactiveTimeoutField.isAccessible = true
                inactiveTimeoutField.setInt(this, 8000)
            } catch (_: Throwable) {}
        }
        // ---------------------------------------------------------------------------------------

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
                        // Connection successful - reset to tier 1 for future connections
                        connectionTimeouts.remove(remoteId)
                        turnServerManager.resetToTier1()
                        Log.d(tag, "Connection successful for $remoteId, reset to tier 1")
                        // Reset retry count
                        connectionRetryCounts.remove(remoteId)
                    }

                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        // Connection failed - escalate and retry
                        connectionTimeouts.remove(remoteId)
                        turnServerManager.forceEscalateToNextTier()
                        Log.w(tag, "Connection failed for $remoteId, escalated to tier ${turnServerManager.getCurrentTier()}")

                        if (turnServerManager.getCurrentTier() < 5) {
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
        return pc
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
            // CRITICAL FIX: Fallback to STUN-only mode
            fallbackToStunOnlyConnection(remoteId)
            return
        }

        connectionRetryCounts[remoteId] = retryCount + 1
        val backoffMs = 1000L * (1 shl retryCount) // 1s, 2s, 4s

        executor.execute {
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

                Thread.sleep(backoffMs)

                if (isEnding.get() || executor.isShutdown) {
                    Log.w(tag, "Abort retry for $remoteId because client is ending")
                    return@execute
                }

                createPeerIfNeeded(remoteId, initiator = signaling.shouldInitiateTo(remoteId))
            } catch (e: Exception) {
                Log.e(tag, "Error retrying connection for $remoteId: ${e.message}", e)
            }
        }
    }
    private fun fallbackToStunOnlyConnection(remoteId: String) {
        executor.execute {
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
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            keyType = PeerConnection.KeyType.ECDSA
        }

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
                        turnServerManager.forceEscalateToNextTier()
                        Log.w(tag, "Connection failed for $remoteId")
                        if (turnServerManager.getCurrentTier() < 5) {
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

    private fun handleRemoteSdp(fromId: String, payload: Map<String, Any?>, msgKey: String) {
        executor.execute {
            try {
                val type = payload["sdpType"] as? String ?: return@execute
                val sdp = payload["sdp"] as? String ?: return@execute
                Log.d(tag, "handleRemoteSdp from=$fromId type=$type key=$msgKey")
                val descType = if (type.equals("offer", true)) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
                val sd = SessionDescription(descType, sdp)

                var pc = peerConnections[fromId]
                if (pc == null) {
                    Log.d(tag, "No pc for $fromId; creating (as answerer)")
                    createPeerIfNeeded(fromId, initiator = false)
                    pc = peerConnections[fromId]
                }
                pc?.setRemoteDescription(SimpleSdpObserver(), sd)
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
                    try { signaling.deleteMessage(msgKey); Log.d(tag, "Deleted direct message $msgKey") } catch (e: Exception) { Log.w(tag, "deleteMessage failed: ${e.message}") }
                }
            } catch (e: Exception) {
                Log.e(tag, "handleRemoteSdp error: ${e.message}", e)
            }
        }
    }

    private fun handleRemoteIce(fromId: String, payload: Map<String, Any?>, msgKey: String) {
        executor.execute {
            try {
                val candidateStr = payload["candidate"] as? String ?: return@execute
                val sdpMid = payload["sdpMid"] as? String
                val sdpMLineIndex = (payload["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                Log.d(tag, "handleRemoteIce from=$fromId key=$msgKey")
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
                peerConnections[fromId]?.addIceCandidate(candidate)

                val to = payload["to"] as? String?
                if (to != null && to == signaling.localId) {
                    try { signaling.deleteMessage(msgKey); Log.d(tag, "Deleted direct ICE message $msgKey") } catch (e: Exception) { Log.w(tag, "deleteMessage failed: ${e.message}") }
                }
            } catch (e: Exception) {
                Log.e(tag, "handleRemoteIce error: ${e.message}", e)
            }
        }
    }

    private fun closePeer(remoteId: String) {
        executor.execute {
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
        executor.execute {
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

    private fun applyVolumeToAudioTrack(track: AudioTrack, multiplier: Float) {
        try {
            // Try direct method call first (newer WebRTC versions)
            try {
                track.setVolume(multiplier.toDouble())
                Log.d(tag, "Applied volume multiplier $multiplier via direct method")
                return
            } catch (e: NoSuchMethodError) {
                // Fall back to reflection for older versions
                try {
                    val method = track.javaClass.getMethod("setVolume", Double::class.javaPrimitiveType)
                    method.invoke(track, multiplier.toDouble())
                    Log.d(tag, "Applied volume multiplier $multiplier via reflection")
                    return
                } catch (e: Exception) {
                    Log.w(tag, "Reflection volume set failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "All volume setting methods failed: ${e.message}")
        }
    }

    fun close() {
        // For full disposal when you mean to stop and not reuse the client.
        try {
            if (!executor.isShutdown) {
                executor.execute {
                    synchronized(peerConnectionLock) {
                        peerConnections.values.forEach { it.close() }
                        peerConnections.clear()
                        remoteAudioTracks.clear()
                        try { audioSource?.dispose() } catch (e: Exception) { Log.w(tag, "audioSource.dispose failed: ${e.message}") }
                        try { localAudioTrack?.dispose() } catch (e: Exception) { Log.w(tag, "localAudioTrack.dispose failed: ${e.message}") }
                        try { factory?.dispose() } catch (e: Exception) { Log.w(tag, "factory.dispose failed: ${e.message}") }
                        try { signaling.close() } catch (e: Exception) { Log.w(tag, "signaling.close failed: ${e.message}") }
                    }
                }
                // Shut down executor after cleanup
                try {
                    executor.shutdown()
                    if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                        executor.shutdownNow()
                    }
                } catch (e: Exception) {
                    try { executor.shutdownNow() } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "close() error: ${e.message}")
        }
    }

    fun endCall() {
        // Do the standard end-call cleanup but do not forcibly shut down the executor.
        if (isEnding.getAndSet(true)) {
            Log.d(tag, "endCall already in progress; ignoring repeated call")
            return
        }

        try {
            // Check if executor is already shutdown to avoid RejectedExecutionException
            if (executor.isShutdown) {
                Log.d(tag, "Executor already shutdown, skipping endCall")
                return
            }

            val endCallTask = Runnable {
                try {
                    synchronized(peerConnectionLock) {
                        signaling.leave()
                        peerConnections.values.forEach {
                            try { it.close() } catch (e: Exception) { Log.w(tag, "Error closing peer: ${e.message}") }
                        }
                        peerConnections.clear()
                        remoteAudioTracks.clear()
                    }

                    try { audioSource?.dispose() } catch (e: Exception) { Log.w(tag, "audioSource.dispose failed: ${e.message}") }
                    try { localAudioTrack?.dispose() } catch (e: Exception) { Log.w(tag, "localAudioTrack.dispose failed: ${e.message}") }
                    try { /* keep factory alive for potential quick rejoin - don't dispose here */ } catch (_: Exception) {}
                    try { signaling.close() } catch (e: Exception) { Log.w(tag, "signaling.close failed: ${e.message}") }

                } catch (e: Exception) {
                    Log.e(tag, "Error during endCall cleanup", e)
                } finally {
                    Log.d(tag, "endCall cleanup complete (executor still active)")
                }
            }

            try {
                executor.execute(endCallTask)
            } catch (e: RejectedExecutionException) {
                // Executor shutting down, run on current thread as best-effort
                Log.w(tag, "Executor rejected endCall task; running synchronously", e)
                endCallTask.run()
            }
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error in endCall", e)
        }
    }
}
