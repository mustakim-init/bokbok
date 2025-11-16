package com.mustakim.bokbok.data.webrtc

import org.webrtc.IceCandidate

data class SignalMessage(
    val from: String,
    val to: String?,
    val type: String, // "offer", "answer", "ice"
    val sdp: String? = null,
    val candidate: IceCandidate? = null
)

interface SignalingBackend {
    fun sendOffer(to: String?, sdp: String)
    fun sendAnswer(to: String, sdp: String)
    fun sendIceCandidate(to: String, candidate: IceCandidate)
    fun observeSignals(onSignal: (SignalMessage) -> Unit)
    fun dispose()
}
