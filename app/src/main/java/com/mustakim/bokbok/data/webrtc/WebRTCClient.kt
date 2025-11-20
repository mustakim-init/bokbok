package com.mustakim.bokbok.data.webrtc

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
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

@Suppress("DEPRECATION")
class WebRTCClient(
    context: Context,
    private val signalingBackend: SignalingBackend,
    private val selfId: String,
    private val roomId: String
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

    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
        optional.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
        optional.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
        optional.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        optional.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        optional.add(MediaConstraints.KeyValuePair("googAudioMirroring", "false"))
        optional.add(MediaConstraints.KeyValuePair("googDAEchoCancellation", "true"))
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
        audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager?.let { am ->
            savedAudioMode = am.mode
            savedIsMicrophoneMute = am.isMicrophoneMute
            savedIsSpeakerphoneOn = am.isSpeakerphoneOn

            am.mode = AudioManager.MODE_IN_COMMUNICATION
            am.isSpeakerphoneOn = true
            am.isMicrophoneMute = false
            Log.d(tag, "AudioManager configured: MODE_IN_COMMUNICATION")
        }
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

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(PeerConnectionFactory.Options())
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun initLocalAudio() {
        val factory = peerConnectionFactory ?: return
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource).apply {
            setEnabled(true)
        }
    }

    fun disconnect() {
        Log.d(tag, "disconnect() called")
        isShuttingDown.set(true)
        
        executeTask {
            stopStatsPolling()
            signalingBackend.dispose()
            
            peerConnections.values.forEach { pc ->
                try { pc.close() } catch (e: Exception) { Log.w(tag, "Error closing PC: ${e.message}") }
            }
            peerConnections.clear()
            stableConnections.clear()
            remoteAudioTracks.clear()
            
            try {
                localAudioTrack?.dispose()
                audioSource?.dispose()
                peerConnectionFactory?.dispose()
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
                        pc.setLocalDescription(SimpleSdpObserver(), it)
                        signalingBackend.sendOffer(remoteId, it.description)
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
                    pc.setLocalDescription(SimpleSdpObserver(), it)
                    signalingBackend.sendOffer(remoteId, it.description)
                    Log.d(tag, "Offer sent to $remoteId")
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
                            pc.setLocalDescription(SimpleSdpObserver(), it)
                            signalingBackend.sendAnswer(from, it.description)
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
            executeTask {
                val speakingMap = mutableMapOf<String, Boolean>()
                peerConnections.forEach { (id, pc) ->
                    pc.getStats { report ->
                        val speaking = report.statsMap.values.any { 
                            it.type == "inbound-rtp" && 
                            (it.members["mediaType"] == "audio" || it.members["kind"] == "audio") &&
                            ((it.members["audioLevel"] as? Double) ?: 0.0) > 0.02
                        }
                        speakingMap[id] = speaking
                    }
                }
                onSpeakingStateChanged?.invoke(speakingMap)
            }
            statsHandler.postDelayed(this, 1000L)
        }
    }
}
