package com.mustakim.bokbok.data.webrtc

import android.util.Log
import org.webrtc.PeerConnection

class TurnServerManager {
    private val tag = "TurnServerManager"

    // Unified configuration with all available servers
    // WebRTC's ICE agent will prioritize: Host > STUN > TURN (Relay)
    // It will automatically use TURN only if P2P/STUN fails.
    private val turnConfigs = listOf(
        // Google STUN (Free, Reliable for P2P)
        TurnConfig(
            listOf(
                "stun:stun.l.google.com:19302",
                "stun:stun1.l.google.com:19302",
                "stun:stun2.l.google.com:19302",
                "stun:stun3.l.google.com:19302",
                "stun:stun4.l.google.com:19302"
            )
        ),

        // ExpressTURN (Paid, High Cost/Performance)
        TurnConfig(
            listOf(
                "turn:relay1.expressturn.com:3480",              // UDP: Preferred for low latency/audio quality
                "turn:relay1.expressturn.com:3480?transport=tcp" // TCP: Fallback for restrictive firewalls
            ),
            "000000002076939268",
            "4T3FWv6UghoKKJxYyRSNFUlMcFg="
        ),

        // Metered.ca (Backup)
        TurnConfig(
            listOf(
                "turn:a.relay.metered.ca:443",
                "turn:a.relay.metered.ca:443?transport=tcp"
            ),
            "09a476ce02ceb96bbc9651c0",
            "iW5w+oxvVxuxUYvh"
        )
    )

    data class TurnConfig(
        val urls: List<String>,
        val username: String = "",
        val password: String = ""
    )

    fun getIceServers(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        turnConfigs.forEach { config ->
            servers.addAll(createIceServersFromConfig(config))
        }

        val turnCount = servers.count { it.urls.any { url -> url.startsWith("turn:") } }
        val stunCount = servers.count { it.urls.any { url -> url.startsWith("stun:") } }

        Log.d(tag, "Providing ICE Servers: $stunCount STUN + $turnCount TURN")

        return servers
    }

    private fun createIceServersFromConfig(config: TurnConfig): List<PeerConnection.IceServer> {
        return config.urls.mapNotNull { url ->
            try {
                if (config.username.isNotEmpty()) {
                    PeerConnection.IceServer.builder(url)
                        .setUsername(config.username)
                        .setPassword(config.password)
                        .createIceServer()
                } else {
                    PeerConnection.IceServer.builder(url).createIceServer()
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to create server for $url: ${e.message}")
                null
            }
        }
    }

    // Retained for logging/debugging purposes
    fun estimateTurnUsage(durationSeconds: Int, peersCount: Int): Long {
        val bytesPerSecond = 4000L // Approx 32kbps
        return bytesPerSecond * durationSeconds * peersCount
    }

    fun getUsageEstimateMB(durationMinutes: Int, peersCount: Int): Double {
        val bytes = estimateTurnUsage(durationMinutes * 60, peersCount)
        return bytes / 1024.0 / 1024.0
    }

    fun logUsageWarning(durationMinutes: Int, peersCount: Int) {
        val usageMB = getUsageEstimateMB(durationMinutes, peersCount)
        when {
            usageMB > 400 -> Log.e(tag, "High TURN usage: ${usageMB.toInt()} MB")
            usageMB > 250 -> Log.w(tag, "Moderate TURN usage: ${usageMB.toInt()} MB")
            else -> Log.d(tag, "TURN usage: ${usageMB.toInt()} MB")
        }
    }
}
