@file:Suppress("DEPRECATION")

package com.mustakim.bokbok.data.webrtc

import com.mustakim.bokbok.BuildConfig
import org.webrtc.PeerConnection

@Deprecated("Using TurnServerManager instead")
enum class IceTier {
    STUN_ONLY,
    PRIMARY_TURN,
    FALLBACK_TURN
}
@Deprecated("Using TurnServerManager instead")
object TurnServerProvider {

    fun buildIceServers(tier: IceTier): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        // Always include STUN for all tiers
        servers += PeerConnection.IceServer.builder("stun:stun.l.google.com:19302")
            .createIceServer()
        servers += PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302")
            .createIceServer()

        // Primary TURN (Tier 2)
        if (tier >= IceTier.PRIMARY_TURN &&
            BuildConfig.TURN_URL.isNotBlank() &&
            BuildConfig.TURN_USERNAME.isNotBlank() &&
            BuildConfig.TURN_PASSWORD.isNotBlank()
        ) {
            servers += PeerConnection.IceServer.builder(BuildConfig.TURN_URL)
                .setUsername(BuildConfig.TURN_USERNAME)
                .setPassword(BuildConfig.TURN_PASSWORD)
                .createIceServer()
        }

        // Fallback TURN (Tier 3) – optional second server
        if (tier >= IceTier.FALLBACK_TURN &&
            BuildConfig.TURN_FALLBACK_URL.isNotBlank() &&
            BuildConfig.TURN_FALLBACK_USERNAME.isNotBlank() &&
            BuildConfig.TURN_FALLBACK_PASSWORD.isNotBlank()
        ) {
            servers += PeerConnection.IceServer.builder(BuildConfig.TURN_FALLBACK_URL)
                .setUsername(BuildConfig.TURN_FALLBACK_USERNAME)
                .setPassword(BuildConfig.TURN_FALLBACK_PASSWORD)
                .createIceServer()
        }

        return servers
    }
}