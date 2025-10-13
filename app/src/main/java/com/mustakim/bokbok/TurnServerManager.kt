package com.mustakim.bokbok

import android.util.Log
import org.webrtc.PeerConnection
import java.util.concurrent.CopyOnWriteArrayList

class TurnServerManager {
    private val tag = "TurnServerManager"

    private var lastEscalationTime = 0L


    private val turnServerConfigs = listOf(
        // Tier 1: Most reliable servers first
        TurnConfig(
            listOf(
                "stun:stun.l.google.com:19302",
                "stun:stun1.l.google.com:19302",
                "stun2.l.google.com:19302",
                "stun3.l.google.com:19302",
                "stun4.l.google.com:19302",
                "stun.ekiga.net",
                "stun.ideasip.com",
                "stun.rixtelecom.se",
                "stun.schlund.de",
                "stun.stunprotocol.org:3478",
                "stun.voiparound.com",
                "stun.voipbuster.com",
                "stun.voipstunt.com",
                "stun.voxgratia.org"
            ),
            "", "", 1
        ),

        // Tier 2: Reliable TURN servers
        TurnConfig(
            listOf(
                "turn:openrelay.metered.ca:80",
                "turn:openrelay.metered.ca:443",
                "turn:openrelay.metered.ca:443?transport=tcp"
            ),
            "openrelayproject", "openrelayproject", 2
        ),

        // Tier 3: Backup TURN servers
        TurnConfig(
            listOf(
                "turn:numb.viagenie.ca:3478",
                "turn:numb.viagenie.ca:3478?transport=tcp"
            ),
            "webrtc@live.com", "muazkh", 3
        ),

        TurnConfig(
            listOf(
                "turn:turn.anyfirewall.com:443?transport=tcp",
                "turn:turn.anyfirewall.com:3478?transport=udp"
            ),
            "webrtc", "webrtc", 4
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

    fun markServerAsFailed(config: TurnConfig, failedUrl: String? = null) {
        val now = System.currentTimeMillis()
        config.lastFailTime = now
        config.failCount++
        if (!failedServers.contains(config)) failedServers.add(config)
        Log.w(tag, "TURN server failed: ${failedUrl ?: config.urls.firstOrNull() ?: "unknown"} (failCount=${config.failCount})")

        // Only auto-escalate if at least two distinct configs failed in the last 30s
        if (now - lastEscalationTime < 30000) {
            val distinctFailures = failedServers.count { now - it.lastFailTime < 30000 }
            if (distinctFailures >= 2 && currentTier < 4) {
                escalateToNextTier()
                Log.w(tag, "Auto-escalating to tier $currentTier due to multiple distinct failures")
                lastEscalationTime = now
            }
        }
    }

    fun markCurrentTierAsFailed() {
        val currentConfigs = turnServerConfigs.filter { it.tier == currentTier }
        currentConfigs.forEach { markServerAsFailed(it) }
    }

    fun enableCGNATMode() {
        Log.i(tag, "Enabling CGNAT mode - forcing TURN relay")
        forceRelayMode = true
        currentTier = 1
        lastEscalationTime = System.currentTimeMillis()
    }

    fun escalateToNextTier() {
        if (currentTier < 4) {
            currentTier++
            lastEscalationTime = System.currentTimeMillis()
        }
    }
    fun canEscalate(): Boolean = System.currentTimeMillis() - lastEscalationTime > 15000 // 15 second cooldown
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