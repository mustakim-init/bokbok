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

    private val TAG = "WebRTCClient"

    private val appContext: Context = context.applicationContext
    private val peerConnections = mutableMapOf<String, PeerConnection>()

    private val peerStates = mutableMapOf<String, PeerState>()

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

    // Basic ICE server list (STUN + optional TURN)
    private val iceServers: List<PeerConnection.IceServer> = listOf(
        // Free Google STUN – good enough for dev and many real users
        PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer(),
        PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302")
            .createIceServer()

        // When you have a real TURN:
        // PeerConnection.IceServer.builder("turn:your-turn.example.com:3478")
        //     .setUsername("username")
        //     .setPassword("password")
        //     .createIceServer()
    )


    fun connect() {
        Log.d(TAG, "connect() called for selfId=$selfId roomId=$roomId")

        initPeerConnectionFactory()
        initLocalAudio()
        startListeningForSignals()

        Log.d(TAG, "connect() finished setup for selfId=$selfId")
    }

    fun disconnect() {
        Log.d(TAG, "disconnect() called for selfId=$selfId roomId=$roomId")

        signalingBackend.dispose()
        Log.d(TAG, "signalingBackend.dispose() done")

        peerConnections.values.forEach { pc ->
            try {
                pc.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing PeerConnection: ${e.message}")
            }
        }
        peerConnections.clear()
        Log.d(TAG, "All PeerConnections closed and cleared")

        try {
            localAudioTrack?.dispose()
            audioSource?.dispose()
            peerConnectionFactory?.dispose()
            Log.d(TAG, "Audio track/source and factory disposed")
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing WebRTC resources: ${e.message}")
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
            Log.d(TAG, "createConnectionTo($remoteUserId) skipped by design (selfId=$selfId)")
            return
        }

        val state = peerStates[remoteUserId] ?: PeerState.NEW
        if (state != PeerState.NEW) {
            Log.d(TAG, "createConnectionTo($remoteUserId) ignored, state=$state")
            return
        }

        Log.d(TAG, "createConnectionTo($remoteUserId) starting offer")
        val pc = getOrCreatePeerConnection(remoteUserId)
        peerStates[remoteUserId] = PeerState.OFFER_SENT

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                val sdp = sessionDescription ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                signalingBackend.sendOffer(remoteUserId, sdp.description)
                Log.d(TAG, "Offer sent to $remoteUserId")
            }

            override fun onCreateFailure(p0: String?) {
                Log.e(TAG, "createOffer failed for $remoteUserId: $p0")
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
            Log.d(TAG, "Reusing existing PeerConnection for $remoteUserId")
            return it
        }

        val factory = peerConnectionFactory
            ?: throw IllegalStateException("PeerConnectionFactory not initialized")

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            // Later you can tune these if needed
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            iceTransportsType = PeerConnection.IceTransportsType.ALL
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.ENABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
        }

        Log.d(TAG, "Creating new PeerConnection for $remoteUserId")

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {
                Log.d(TAG, "[$remoteUserId] onSignalingChange: $newState")
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "[$remoteUserId] onIceConnectionChange: $newState")
                if (newState == PeerConnection.IceConnectionState.CONNECTED) {
                    peerStates[remoteUserId] = PeerState.CONNECTED
                    Log.d(TAG, "[$remoteUserId] marked as CONNECTED")
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.d(TAG, "[$remoteUserId] onConnectionChange: $newState")
            }

            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "[$remoteUserId] onIceGatheringChange: $newState")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                Log.d(
                    TAG,
                    "[$remoteUserId] onIceCandidate: sdpMid=${candidate.sdpMid}, " +
                            "mLine=${candidate.sdpMLineIndex}"
                )
                signalingBackend.sendIceCandidate(remoteUserId, candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
                Log.d(TAG, "[$remoteUserId] onIceCandidatesRemoved: ${candidates?.size ?: 0}")
            }

            override fun onIceConnectionReceivingChange(p0: Boolean) {
                Log.d(TAG, "[$remoteUserId] onIceConnectionReceivingChange: $p0")
            }

            override fun onAddStream(stream: org.webrtc.MediaStream?) {
                Log.d(TAG, "[$remoteUserId] onAddStream")
            }

            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {
                Log.d(TAG, "[$remoteUserId] onRemoveStream")
            }

            override fun onDataChannel(dc: org.webrtc.DataChannel?) {
                Log.d(TAG, "[$remoteUserId] onDataChannel: ${dc?.label()}")
            }

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "[$remoteUserId] onRenegotiationNeeded")
            }

            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?
            ) {
                Log.d(TAG, "[$remoteUserId] onAddTrack, streams=${streams?.size ?: 0}")
            }
        }

        val pc = factory.createPeerConnection(rtcConfig, observer)
            ?: throw IllegalStateException("Failed to create PeerConnection")

        localAudioTrack?.let { audioTrack ->
            pc.addTrack(audioTrack, listOf("LOCAL_AUDIO_STREAM"))
            Log.d(TAG, "[$remoteUserId] Local audio track added")
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

        Log.d(TAG, "handleRemoteOffer from=$from, sdpLength=${sdp.length}")

        val pc = getOrCreatePeerConnection(from)

        val state = pc.signalingState()
        if (state != PeerConnection.SignalingState.STABLE &&
            state != PeerConnection.SignalingState.HAVE_REMOTE_OFFER) {
            Log.d(TAG, "Ignoring OFFER from $from in state=$state")
            return
        }

        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)

        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote OFFER set for $from, creating ANSWER")
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        if (sessionDescription == null) return
                        Log.d(TAG, "Answer created for $from, length=${sessionDescription.description.length}")
                        pc.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                        signalingBackend.sendAnswer(from, sessionDescription.description)
                        Log.d(TAG, "Answer sent to $from")
                    }

                    override fun onCreateFailure(p0: String?) {
                        Log.e(TAG, "createAnswer failed for $from: $p0")
                    }
                }, audioConstraints)
            }

            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "setRemoteDescription(OFFER) failed for $from: $p0")
            }
        }, remoteSdp)
    }

    private fun handleRemoteAnswer(message: SignalMessage) {
        val from = message.from
        val sdp = message.sdp ?: return

        val pc = getOrCreatePeerConnection(from)
        val state = pc.signalingState()
        if (state != PeerConnection.SignalingState.HAVE_LOCAL_OFFER) {
            Log.d(TAG, "Ignoring ANSWER from $from in state=$state")
            return
        }

        Log.d(TAG, "handleRemoteAnswer from=$from, sdpLength=${sdp.length}")
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                Log.d(TAG, "Remote ANSWER set for $from")
                peerStates[from] = PeerState.ANSWERED
            }

            override fun onSetFailure(p0: String?) {
                Log.e(TAG, "setRemoteDescription(ANSWER) failed for $from: $p0")
            }
        }, remoteSdp)
    }

    private fun handleRemoteIce(message: SignalMessage) {
        val from = message.from
        val candidate = message.candidate ?: return

        Log.d(TAG, "handleRemoteIce from=$from, mid=${candidate.sdpMid}, mLine=${candidate.sdpMLineIndex}")

        val pc = getOrCreatePeerConnection(from)
        val result = pc.addIceCandidate(candidate)
        Log.d(TAG, "addIceCandidate for $from result=$result")
    }
}
