package com.mustakim.bokbok

import android.util.Log
import org.webrtc.PeerConnection
import java.util.concurrent.CopyOnWriteArrayList

class TurnServerManager {
    private val tag = "TurnServerManager"

    private val turnServerConfigs = listOf(
        // Tier 1: Port 443 servers (bypass firewalls)
        TurnConfig(
            listOf(
                "turns:openrelay.metered.ca:443?transport=tcp",
                "turn:openrelay.metered.ca:80?transport=tcp"
            ),
            "openrelayproject", "openrelayproject", 1
        ),

        // Tier 2: More port 443 servers
        TurnConfig(
            listOf("turn:numb.viagenie.ca:443?transport=tcp"),
            "webrtc@live.com", "muazkh", 2
        ),

        // Tier 3: Backup servers
        TurnConfig(
            listOf("turn:turn.anyfirewall.com:443?transport=tcp"),
            "webrtc", "webrtc", 3
        ),

        // Tier 4: Last resort
        TurnConfig(
            listOf("turn:freestun.net:53?transport=tcp"),
            "free", "free", 4
        )
    )

    private val stunServers = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun2.l.google.com:19302"
    )

    private val failedServers = CopyOnWriteArrayList<TurnConfig>()
    private var currentTier = 1
    private var forceRelayMode = false

    data class TurnConfig(
        val urls: List<String>,
        val username: String,
        val password: String,
        val tier: Int,
        var lastFailTime: Long = 0L,
        var failCount: Int = 0
    )

    fun getIceServersForCurrentTier(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()

        // Add fewer STUN servers if forcing relay
        if (forceRelayMode) {
            servers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        } else {
            stunServers.forEach { servers.add(PeerConnection.IceServer.builder(it).createIceServer()) }
        }

        val availableConfigs = turnServerConfigs.filter { it.tier <= currentTier && !isRecentlyFailed(it) }
        val configs = if (availableConfigs.isEmpty()) turnServerConfigs.filter { !isRecentlyFailed(it) } else availableConfigs
        configs.forEach { servers.addAll(createIceServersFromConfig(it)) }

        Log.d(tag, "Using ${servers.size} ICE servers for tier $currentTier (relay: $forceRelayMode)")
        return servers
    }

    private fun createIceServersFromConfig(config: TurnConfig): List<PeerConnection.IceServer> {
        return config.urls.mapNotNull { url ->
            try {
                if (config.username.isNotEmpty())
                    PeerConnection.IceServer.builder(url).setUsername(config.username).setPassword(config.password).createIceServer()
                else PeerConnection.IceServer.builder(url).createIceServer()
            } catch (e: Exception) {
                Log.w(tag, "Failed to create server for $url: ${e.message}")
                null
            }
        }
    }

    private fun isRecentlyFailed(config: TurnConfig): Boolean {
        val cooldown = when (config.failCount) {
            0 -> 0L
            1 -> 30_000L
            2 -> 120_000L
            else -> 300_000L
        }
        return (System.currentTimeMillis() - config.lastFailTime) < cooldown
    }

    fun markServerAsFailed(config: TurnConfig) {
        config.lastFailTime = System.currentTimeMillis()
        config.failCount++
        if (!failedServers.contains(config)) failedServers.add(config)
        Log.w(tag, "TURN server failed: ${config.urls.firstOrNull()}")
    }

    fun enableCGNATMode() {
        Log.i(tag, "Enabling CGNAT mode - forcing TURN relay")
        forceRelayMode = true
        currentTier = 1
    }

    fun escalateToNextTier() { if (currentTier < 4) currentTier++ }
    fun forceEscalateToNextTier() { escalateToNextTier() }
    fun resetToTier1() { currentTier = 1 }
    fun getCurrentTier(): Int = currentTier
    fun getStatusInfo(): String = "T$currentTier${if (forceRelayMode) " (RELAY)" else ""}"

    fun getEmergencyIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("turns:openrelay.metered.ca:443?transport=tcp")
                .setUsername("openrelayproject").setPassword("openrelayproject").createIceServer()
        )
    }
}