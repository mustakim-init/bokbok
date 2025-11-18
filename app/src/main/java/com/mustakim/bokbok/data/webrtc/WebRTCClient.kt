package com.mustakim.bokbok.data.webrtc

import android.content.Context
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.SessionDescription
import android.util.Log


private enum class PeerState { NEW, OFFER_SENT, ANSWERED, CONNECTED }

class WebRTCClient(
    context: Context,
    private val signalingBackend: SignalingBackend,
    private val selfId: String,
    private val roomId: String
) {

    private val tag = "WebRTCClient"

    private val appContext: Context = context.applicationContext
    private val peerConnections = mutableMapOf<String, PeerConnection>()

    private val peerStates = mutableMapOf<String, PeerState>()

    private var currentTier: IceTier = IceTier.STUN_ONLY
    private var lastTierChangeTimeMs: Long = 0
    private val tierChangeCooldownMs = 30_000L // don’t spam tiers

    // For ICE candidates that arrive before we have a remote SDP
    private val pendingRemoteCandidates =
        mutableMapOf<String, MutableList<IceCandidate>>()



    // Decide which side is allowed to initiate offers for a given pair.
    // Here: only the "smaller" UID starts offers, the other always waits to answer.
    private fun shouldInitiateTo(remoteUserId: String): Boolean {
        // You can flip < to > if you want the opposite side to be the initiator
        return selfId < remoteUserId
    }


    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Single shared EGL context (even though we are audio‑only for now)
    private val eglBase: EglBase = EglBase.create()

    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
    }

    private fun currentIceServers(): List<PeerConnection.IceServer> {
        val servers = TurnServerProvider.buildIceServers(currentTier)
        Log.d(tag, "Using ICE tier=$currentTier with ${servers.size} servers")
        return servers
    }


    fun connect() {
        Log.d(tag, "connect() called for selfId=$selfId roomId=$roomId")

        initPeerConnectionFactory()
        initLocalAudio()
        startListeningForSignals()

        Log.d(tag, "connect() finished setup for selfId=$selfId")
    }

    fun disconnect() {
        Log.d(tag, "disconnect() called for selfId=$selfId roomId=$roomId")

        signalingBackend.dispose()
        Log.d(tag, "signalingBackend.dispose() done")

        peerConnections.values.forEach { pc ->
            try {
                pc.close()
            } catch (e: Exception) {
                Log.w(tag, "Error closing PeerConnection: ${e.message}")
            }
        }
        peerConnections.clear()
        Log.d(tag, "All PeerConnections closed and cleared")

        try {
            localAudioTrack?.dispose()
            audioSource?.dispose()
            peerConnectionFactory?.dispose()
            Log.d(tag, "Audio track/source and factory disposed")
        } catch (e: Exception) {
            Log.w(tag, "Error disposing WebRTC resources: ${e.message}")
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /**
     * Call this when a new remote participant joins and you want
     * to initiate a connection to them (send offer).
     */
    fun createConnectionTo(remoteUserId: String) {
        // Only one side (deterministic) is allowed to initiate an offer
        if (!shouldInitiateTo(remoteUserId)) {
            Log.d(tag, "createConnectionTo($remoteUserId) skipped by design (selfId=$selfId)")
            return
        }

        val state = peerStates[remoteUserId] ?: PeerState.NEW
        if (state != PeerState.NEW) {
            Log.d(tag, "createConnectionTo($remoteUserId) ignored, state=$state")
            return
        }

        Log.d(tag, "createConnectionTo($remoteUserId) starting offer")
        val pc = getOrCreatePeerConnection(remoteUserId)
        peerStates[remoteUserId] = PeerState.OFFER_SENT

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                val sdp = sessionDescription ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                signalingBackend.sendOffer(remoteUserId, sdp.description)
                Log.d(tag, "Offer sent to $remoteUserId")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(tag, "createOffer failed for $remoteUserId: $p0")
                peerStates[remoteUserId] = PeerState.NEW
            }
        }, audioConstraints)
    }

    // ---------------- internal setup ----------------

    private fun initPeerConnectionFactory() {
        if (peerConnectionFactory != null) return

        val options = PeerConnectionFactory.InitializationOptions.builder(appContext)

            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            /* enableIntelVp8Encoder */ true,
            /* enableH264HighProfile */ true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()
    }

    private fun initLocalAudio() {
        val factory = peerConnectionFactory ?: return

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }

        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("AUDIO_TRACK", audioSource).apply {
            setEnabled(true)
        }
    }

    private fun getOrCreatePeerConnection(remoteUserId: String): PeerConnection {
        peerConnections[remoteUserId]?.let {
            Log.d(tag, "Reusing existing PeerConnection for $remoteUserId")
            return it
        }

        val factory = peerConnectionFactory
            ?: throw IllegalStateException("PeerConnectionFactory not initialized")

        val rtcConfig = PeerConnection.RTCConfiguration(currentIceServers()).apply {
            // Later you can tune these if needed
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        Log.d(tag, "Creating new PeerConnection for $remoteUserId")

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                Log.d(tag, "[$remoteUserId] onSignalingChange: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(tag, "[$remoteUserId] onIceConnectionChange: $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED -> {
                        peerStates[remoteUserId] = PeerState.CONNECTED
                        Log.d(tag, "[$remoteUserId] marked as CONNECTED at tier=$currentTier")
                    }
                    PeerConnection.IceConnectionState.FAILED,
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(tag, "[$remoteUserId] ICE state=$newState at tier=$currentTier")
                        maybeEscalateIceTierAndRetry(remoteUserId)
                    }
                    else -> Unit
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(tag, "[$remoteUserId] onConnectionChange: $newState")
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                Log.d(tag, "[$remoteUserId] onIceGatheringChange: $newState")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                Log.d(
                    tag,
                    "[$remoteUserId] onIceCandidate: sdpMid=${candidate.sdpMid}, " +
                            "mLine=${candidate.sdpMLineIndex}"
                )
                signalingBackend.sendIceCandidate(remoteUserId, candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(tag, "[$remoteUserId] onIceCandidatesRemoved: ${candidates?.size ?: 0}")
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {
                Log.d(tag, "[$remoteUserId] onIceConnectionReceivingChange: $p0")
            }

            override fun onAddStream(stream: org.webrtc.MediaStream?) {
                Log.d(tag, "[$remoteUserId] onAddStream")
            }

            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {
                Log.d(tag, "[$remoteUserId] onRemoveStream")
            }

            override fun onDataChannel(dc: org.webrtc.DataChannel?) {
                Log.d(tag, "[$remoteUserId] onDataChannel: ${dc?.label()}")
            }

            override fun onRenegotiationNeeded() {
                Log.d(tag, "[$remoteUserId] onRenegotiationNeeded")
            }

            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?
            ) {
                Log.d(tag, "[$remoteUserId] onAddTrack, streams=${streams?.size ?: 0}")
            }
        }

        val pc = factory.createPeerConnection(rtcConfig, observer)
            ?: throw IllegalStateException("Failed to create PeerConnection")

        localAudioTrack?.let { audioTrack ->
            pc.addTrack(audioTrack, listOf("LOCAL_AUDIO_STREAM"))
            Log.d(tag, "[$remoteUserId] Local audio track added")
        }

        peerConnections[remoteUserId] = pc
        return pc
    }

    private fun startListeningForSignals() {
        signalingBackend.observeSignals { message ->
            when (message.type) {
                "offer" -> handleRemoteOffer(message)
                "answer" -> handleRemoteAnswer(message)
                "ice" -> handleRemoteIce(message)
            }
        }
    }

    private fun handleRemoteOffer(message: SignalMessage) {
        val from = message.from
        val sdp = message.sdp ?: return

        Log.d(tag, "handleRemoteOffer from=$from, sdpLength=${sdp.length}")

        val pc = getOrCreatePeerConnection(from)

        val state = pc.signalingState()
        if (state != PeerConnection.SignalingState.STABLE &&
            state != PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
            Log.d(tag, "Ignoring OFFER from $from in state=$state")
            return
        }

        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)

        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(tag, "Remote OFFER set for $from, creating ANSWER")
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        if (sessionDescription == null) return
                        Log.d(tag, "Answer created for $from, length=${sessionDescription.description.length}")
                        pc.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                        signalingBackend.sendAnswer(from, sessionDescription.description)
                        Log.d(tag, "Answer sent to $from")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(tag, "createAnswer failed for $from: $p0")
                    }
                }, audioConstraints)
            }

            override fun onSetFailure(p0: String?) {
                Log.e(tag, "setRemoteDescription(OFFER) failed for $from: $p0")
            }
        }, remoteSdp)
    }

    private fun handleRemoteAnswer(message: SignalMessage) {
        val from = message.from
        val sdp = message.sdp ?: return

        val pc = getOrCreatePeerConnection(from)
        val state = pc.signalingState()
        if (state != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            Log.d(tag, "Ignoring ANSWER from $from in state=$state")
            return
        }

        Log.d(tag, "handleRemoteAnswer from=$from, sdpLength=${sdp.length}")
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(tag, "Remote ANSWER set for $from")
                peerStates[from] = PeerState.ANSWERED
            }

            override fun onSetFailure(p0: String?) {
                Log.e(tag, "setRemoteDescription(ANSWER) failed for $from: $p0")
            }
        }, remoteSdp)
    }

    private fun handleRemoteIce(message: SignalMessage) {
        val from = message.from
        val candidate = message.candidate ?: return

        Log.d(tag, "handleRemoteIce from=$from, mid=${candidate.sdpMid}, mLine=${candidate.sdpMLineIndex}")

        val pc = getOrCreatePeerConnection(from)

        // If we don't have a remote description yet, buffer this candidate
        if (pc.remoteDescription == null) {
            Log.d(tag, "No remoteDescription for $from yet, buffering ICE")
            val list = pendingRemoteCandidates.getOrPut(from) { mutableListOf() }
            list += candidate
            return
        }

        val result = pc.addIceCandidate(candidate)
        Log.d(tag, "addIceCandidate for $from result=$result")
    }

    private fun maybeEscalateIceTierAndRetry(remoteUserId: String) {
        val now = System.currentTimeMillis()
        if (now - lastTierChangeTimeMs < tierChangeCooldownMs) {
            Log.d(tag, "Cooldown active, not escalating tier for $remoteUserId")
            return
        }

        val nextTier = when (currentTier) {
            IceTier.STUN_ONLY -> IceTier.PRIMARY_TURN
            IceTier.PRIMARY_TURN -> IceTier.FALLBACK_TURN
            IceTier.FALLBACK_TURN -> {
                Log.w(tag, "Already at FALLBACK_TURN, cannot escalate further")
                return
            }
        }

        Log.w(tag, "Escalating ICE tier from $currentTier to $nextTier for $remoteUserId")
        currentTier = nextTier
        lastTierChangeTimeMs = now

        // Tear down the old connection and recreate at the new tier
        peerConnections[remoteUserId]?.close()
        peerConnections.remove(remoteUserId)
        peerStates[remoteUserId] = PeerState.NEW

        // If we are the offerer, try again with a new PeerConnection at the new tier
        if (shouldInitiateTo(remoteUserId)) {
            createConnectionTo(remoteUserId)
        } else {
            Log.d(tag, "Waiting for remote side to re-offer at new tier $currentTier")
        }
    }
}
