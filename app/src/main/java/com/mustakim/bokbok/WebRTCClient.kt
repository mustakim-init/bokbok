package com.mustakim.bokbok

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebRTCClient(private val context: Context, private val roomId: String) {
    private val TAG = "WebRTCClient"
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

    fun setNoiseSuppression(enabled: Boolean) {
        noiseSuppressionEnabled = enabled
        Log.d(TAG, "Noise suppression set to $enabled (restart call to fully re-create audio source)")
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
                        Log.d(TAG, "PeerConnectionFactory created")
                    }

                    if (audioSource == null && factory != null) {
                        val audioConstraints = MediaConstraints()
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", if (noiseSuppressionEnabled) "true" else "false"))
                        audioConstraints.mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
                        audioSource = factory!!.createAudioSource(audioConstraints)
                        localAudioTrack = factory!!.createAudioTrack("ARDAMSa0", audioSource)
                        localAudioTrack?.setEnabled(true)
                        Log.d(TAG, "Local audio track created (noiseSuppression=$noiseSuppressionEnabled)")
                    }

                    signaling.start { type, fromId, payload, msgKey ->
                        Log.d(TAG, "Signal recv type=$type from=$fromId payloadKeys=${payload.keys}")
                        when (type) {
                            FirebaseSignaling.Type.SDP -> handleRemoteSdp(fromId, payload, msgKey)
                            FirebaseSignaling.Type.ICE -> handleRemoteIce(fromId, payload, msgKey)
                            FirebaseSignaling.Type.JOIN -> {
                                Log.d(TAG, "Join message from $fromId -> createPeerIfNeeded")
                                createPeerIfNeeded(fromId, initiator = signaling.shouldInitiateTo(fromId))
                            }
                            FirebaseSignaling.Type.LEAVE -> {
                                Log.d(TAG, "Leave message from $fromId -> closePeer")
                                closePeer(fromId)
                            }
                        }
                    }

                    // NOTE: using your existing API name for participants callback
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
                    Log.d(TAG, "join() returned $joined")

                    signaling.getParticipantsNow { list ->
                        Log.d(TAG, "getParticipantsNow returned ${list.size}")
                        for (rid in list) {
                            if (rid == signaling.localId) continue
                            if (!peerConnections.containsKey(rid) && peerConnections.size < 5) {
                                createPeerIfNeeded(rid, initiator = signaling.shouldInitiateTo(rid))
                            }
                        }
                    }

                    onReady?.let { android.os.Handler(android.os.Looper.getMainLooper()).post { it() } }
                } catch (e: Exception) {
                    Log.e(TAG, "init error: ${e.message}", e)
                }
            }
        }
    }

    fun setOnParticipantsChanged(cb: (List<String>) -> Unit) { onParticipantsChanged = cb }
    fun getCurrentParticipants(cb: (List<String>) -> Unit) { signaling.getParticipantsNow(cb) }

    private fun createPeerIfNeeded(remoteId: String, initiator: Boolean = false) {
        executor.execute {
            try {
                if (peerConnections.containsKey(remoteId)) return@execute
                Log.d(TAG, "Creating PeerConnection for $remoteId (initiator=$initiator)")
                val pc = createPeerConnection(remoteId) ?: return@execute

                val sendTrack = localAudioTrack
                if (sendTrack != null) {
                    val sender = pc.addTrack(sendTrack, listOf("ARDAMS"))
                    try {
                        val params = sender.parameters
                        if (params.encodings.isNotEmpty()) {
                            params.encodings[0].maxBitrateBps = 48000
                            params.encodings[0].minBitrateBps = 16000
                            sender.parameters = params
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "set bitrate failed: ${e.message}")
                    }
                }

                peerConnections[remoteId] = pc

                if (initiator) {
                    pc.createOffer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            Log.d(TAG, "Offer created for $remoteId")
                            pc.setLocalDescription(SimpleSdpObserver(), desc)
                            signaling.sendSdp(remoteId, desc)
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) { Log.e(TAG, "createOffer failed: $p0") }
                        override fun onSetFailure(p0: String?) {}
                    }, MediaConstraints())
                }
            } catch (e: Exception) {
                Log.e(TAG, "createPeerIfNeeded error: ${e.message}", e)
            }
        }
    }

    private fun createPeerConnection(remoteId: String): PeerConnection? {
        val f = factory ?: return null
        val rtcConfig = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = f.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) { Log.d(TAG, "ICE state for $remoteId: $state") }
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "onIceCandidate -> send to $remoteId")
                signaling.sendIceCandidate(remoteId, candidate)
            }
            override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}
            override fun onAddStream(p0: MediaStream?) {}
            override fun onRemoveStream(p0: MediaStream?) {}
            override fun onDataChannel(p0: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                try {
                    val track = receiver?.track()
                    if (track is AudioTrack) {
                        track.setEnabled(true)
                        remoteAudioTracks[remoteId] = track
                        applyVolumeToAudioTrack(track, receiveVolumeMultiplier)
                        Log.d(TAG, "Remote audio track enabled for $remoteId")
                    } else {
                        Log.d(TAG, "onAddTrack: not AudioTrack for $remoteId")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "onAddTrack error: ${e.message}")
                }
            }
            override fun onRemoveTrack(receiver: RtpReceiver?) {
                remoteAudioTracks.remove(remoteId)
                Log.d(TAG, "onRemoveTrack for $remoteId")
            }
        })
        return pc
    }

    private fun handleRemoteSdp(fromId: String, payload: Map<String, Any?>, msgKey: String) {
        executor.execute {
            try {
                val type = payload["sdpType"] as? String ?: return@execute
                val sdp = payload["sdp"] as? String ?: return@execute
                Log.d(TAG, "handleRemoteSdp from=$fromId type=$type key=$msgKey")
                val descType = if (type.equals("offer", true)) SessionDescription.Type.OFFER else SessionDescription.Type.ANSWER
                val sd = SessionDescription(descType, sdp)

                var pc = peerConnections[fromId]
                if (pc == null) {
                    Log.d(TAG, "No pc for $fromId; creating (as answerer)")
                    createPeerIfNeeded(fromId, initiator = false)
                    pc = peerConnections[fromId]
                }
                pc?.setRemoteDescription(SimpleSdpObserver(), sd)
                if (descType == SessionDescription.Type.OFFER) {
                    pc?.createAnswer(object : SdpObserver {
                        override fun onCreateSuccess(desc: SessionDescription) {
                            Log.d(TAG, "Answer created for $fromId")
                            pc.setLocalDescription(SimpleSdpObserver(), desc)
                            signaling.sendSdp(fromId, desc)
                        }
                        override fun onSetSuccess() {}
                        override fun onCreateFailure(p0: String?) { Log.e(TAG, "createAnswer failed: $p0") }
                        override fun onSetFailure(p0: String?) {}
                    }, MediaConstraints())
                }

                val to = payload["to"] as? String?
                if (to != null && to == signaling.localId) {
                    try { signaling.deleteMessage(msgKey); Log.d(TAG, "Deleted direct message $msgKey") } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleRemoteSdp error: ${e.message}", e)
            }
        }
    }

    private fun handleRemoteIce(fromId: String, payload: Map<String, Any?>, msgKey: String) {
        executor.execute {
            try {
                val candidateStr = payload["candidate"] as? String ?: return@execute
                val sdpMid = payload["sdpMid"] as? String
                val sdpMLineIndex = (payload["sdpMLineIndex"] as? Long)?.toInt() ?: 0
                Log.d(TAG, "handleRemoteIce from=$fromId key=$msgKey")
                val candidate = IceCandidate(sdpMid, sdpMLineIndex, candidateStr)
                peerConnections[fromId]?.addIceCandidate(candidate)

                val to = payload["to"] as? String?
                if (to != null && to == signaling.localId) {
                    try { signaling.deleteMessage(msgKey); Log.d(TAG, "Deleted direct ICE message $msgKey") } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleRemoteIce error: ${e.message}", e)
            }
        }
    }

    private fun closePeer(remoteId: String) {
        executor.execute {
            try {
                peerConnections.remove(remoteId)?.close()
                remoteAudioTracks.remove(remoteId)
                Log.d(TAG, "Closed peer $remoteId")
            } catch (e: Exception) {
                Log.w(TAG, "closePeer: ${e.message}")
            }
        }
    }

    fun toggleMute(): Boolean {
        localAudioTrack?.let {
            val newEnabled = !it.enabled()
            it.setEnabled(newEnabled)
            Log.d(TAG, "toggleMute -> nowEnabled=$newEnabled")
            return !newEnabled
        }
        return false
    }

    fun setLocalMicEnabled(enabled: Boolean) {
        try {
            localAudioTrack?.setEnabled(enabled)
            Log.d(TAG, "setLocalMicEnabled -> $enabled")
        } catch (e: Exception) {
            Log.w(TAG, "setLocalMicEnabled failed: ${e.message}")
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

    private fun applyVolumeToAudioTrack(track: AudioTrack, multiplier: Float) {
        try {
            val method: Method? = try {
                track.javaClass.getMethod("setVolume", Double::class.javaPrimitiveType ?: Double::class.java)
            } catch (rnfe: NoSuchMethodException) {
                try { track.javaClass.getMethod("setVolume", Float::class.javaPrimitiveType ?: Float::class.java) } catch (e: Exception) { null }
            }
            method?.let {
                val paramType = it.parameterTypes[0]
                if (paramType == java.lang.Double.TYPE) it.invoke(track, multiplier.toDouble())
                else it.invoke(track, multiplier)
                Log.d(TAG, "Applied receive multiplier $multiplier to track")
                return
            }
        } catch (e: Exception) {
            Log.w(TAG, "applyVolume reflection failed: ${e.message}")
        }

        Log.d(TAG, "applyVolumeToAudioTrack: no setVolume API - cannot apply multiplier directly")
    }

    fun endCall() {
        try { signaling.leave() } catch (_: Exception) {}
        try { for ((_, pc) in peerConnections) pc.close(); peerConnections.clear(); remoteAudioTracks.clear() } catch (_: Exception) {}
        try { audioSource?.dispose() } catch (_: Exception) {}
        try { localAudioTrack?.dispose() } catch (_: Exception) {}
        try { factory?.dispose() } catch (_: Exception) {}

        try {
            executor.shutdown()
            executor.awaitTermination(2, TimeUnit.SECONDS)
        } catch (e: Exception) {
            executor.shutdownNow()
        }
        try { signaling.close() } catch (_: Exception) {}
        Log.d(TAG, "endCall complete")
    }
}
