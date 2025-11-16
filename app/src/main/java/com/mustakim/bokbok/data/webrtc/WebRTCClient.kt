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

class WebRTCClient(
    context: Context,
    private val signalingBackend: SignalingBackend,
    private val selfId: String,
    private val roomId: String
) {

    private val appContext: Context = context.applicationContext
    private val peerConnections = mutableMapOf<String, PeerConnection>()

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null

    // Single shared EGL context (even though we are audio‑only for now)
    private val eglBase: EglBase = EglBase.create()

    private val audioConstraints = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        optional.add(MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"))
    }

    fun connect() {
        initPeerConnectionFactory()
        initLocalAudio()
        startListeningForSignals()
    }

    fun disconnect() {
        signalingBackend.dispose()

        peerConnections.values.forEach { it.close() }
        peerConnections.clear()

        localAudioTrack?.dispose()
        audioSource?.dispose()
        peerConnectionFactory?.dispose()
    }

    fun setAudioEnabled(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    /**
     * Call this when a new remote participant joins and you want
     * to initiate a connection to them (send offer).
     */
    fun createConnectionTo(remoteUserId: String) {
        val pc = getOrCreatePeerConnection(remoteUserId)

        pc.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                val sdp = sessionDescription ?: return
                pc.setLocalDescription(SimpleSdpObserver(), sdp)
                signalingBackend.sendOffer(remoteUserId, sdp.description)
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
        peerConnections[remoteUserId]?.let { return it }

        val factory = peerConnectionFactory
            ?: throw IllegalStateException("PeerConnectionFactory not initialized")

        val rtcConfig = PeerConnection.RTCConfiguration(emptyList()).apply {
            // Later we will plug in real stun/turn from TurnServerManager
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}

            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate == null) return
                signalingBackend.sendIceCandidate(remoteUserId, candidate)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            // NEW: add this missing override
            override fun onIceConnectionReceivingChange(p0: Boolean) {
                // No‑op for now
            }
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(dc: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: org.webrtc.RtpReceiver?, streams: Array<out org.webrtc.MediaStream>?) {}
        }

        val pc = factory.createPeerConnection(rtcConfig, observer)
            ?: throw IllegalStateException("Failed to create PeerConnection")

        // For now, single audio track to all peers
        localAudioTrack?.let { audioTrack ->
            // Unified Plan: use addTrack instead of addStream
            pc.addTrack(audioTrack, listOf("LOCAL_AUDIO_STREAM"))
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

        val pc = getOrCreatePeerConnection(from)
        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdp)

        pc.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                pc.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(sessionDescription: SessionDescription?) {
                        if (sessionDescription == null) return
                        pc.setLocalDescription(SimpleSdpObserver(), sessionDescription)
                        signalingBackend.sendAnswer(from, sessionDescription.description)
                    }
                }, audioConstraints)
            }
        }, remoteSdp)
    }

    private fun handleRemoteAnswer(message: SignalMessage) {
        val from = message.from
        val sdp = message.sdp ?: return

        val pc = getOrCreatePeerConnection(from)
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        pc.setRemoteDescription(SimpleSdpObserver(), remoteSdp)
    }

    private fun handleRemoteIce(message: SignalMessage) {
        val from = message.from
        val candidate = message.candidate ?: return

        val pc = getOrCreatePeerConnection(from)
        pc.addIceCandidate(candidate)
    }
}
