package com.mustakim.bokbok

import android.util.Log
import org.webrtc.PeerConnection
import java.util.concurrent.CopyOnWriteArrayList

class TurnServerManager {
    private val tag = "TurnServerManager"

    private val turnServerConfigs = listOf(
        TurnConfig(listOf("turn:openrelay.metered.ca:80"), "openrelayproject", "openrelayproject", 1),
        TurnConfig(listOf("turn:freestun.net:3478"), "free", "free", 1),
        TurnConfig(listOf("turn:numb.viagenie.ca"), "webrtc@live.com", "muazkh", 2),
        TurnConfig(listOf("turn:192.158.29.39:3478?transport=udp", "turn:192.158.29.39:3478?transport=tcp"),
            "28224511:1379330808", "JZEOEt2V3Qb0y27GRntt2u2PAYA=", 2),
        TurnConfig(listOf("turn:turn.bistri.com:80"), "homeo", "homeo", 3),
        TurnConfig(listOf("turn:turn.anyfirewall.com:443?transport=tcp"), "webrtc", "webrtc", 3),
        TurnConfig(listOf("turn:bn-turn1.xirsys.com:80?transport=udp", "turn:bn-turn1.xirsys.com:3478?transport=udp",
            "turn:bn-turn1.xirsys.com:80?transport=tcp", "turn:bn-turn1.xirsys.com:3478?transport=tcp"),
            "myKJdsyblh2uDcFwj9goLOQj6AbgZfijgQDOCwzuJTycBLNCpnI8f2RqK7Fnr4kiAAAAAGjUDYNtdXN0YWtpbTI1",
            "c39a9d64-995a-11f0-b83a-0242ac140004", 4),
        TurnConfig(listOf("turns:bn-turn1.xirsys.com:443?transport=tcp", "turns:bn-turn1.xirsys.com:5349?transport=tcp"),
            "myKJdsyblh2uDcFwj9goLOQj6AbgZfijgQDOCwzuJTycBLNCpnI8f2RqK7Fnr4kiAAAAAGjUDYNtdXN0YWtpbTI1",
            "c39a9d64-995a-11f0-b83a-0242ac140004", 4),
        TurnConfig(listOf("turn:turn01.hubl.in?transport=udp", "turn:turn02.hubl.in?transport=tcp"), "", "", 5)
    )

    private val stunServers = listOf(
        "stun:stun.l.google.com:19302",
        "stun:stun1.l.google.com:19302",
        "stun:stun.stunprotocol.org:3478",
        "stun:stun.ekiga.net"
    )

    private val failedServers = CopyOnWriteArrayList<TurnConfig>()
    private var currentTier = 1

    data class TurnConfig(
        val urls: List<String>,
        val username: String,
        val password: String,
        val tier: Int,
        var lastFailTime: Long = 0L,
        var failCount: Int = 0
    )

    /**
     * Returns ICE servers including STUNs and TURNs appropriate for the current tier.
     * Skips recently-failed TURN configs.
     */
    fun getIceServersForCurrentTier(): List<PeerConnection.IceServer> {
        val servers = mutableListOf<PeerConnection.IceServer>()
        stunServers.forEach { servers.add(PeerConnection.IceServer.builder(it).createIceServer()) }

        val availableConfigs = turnServerConfigs.filter { it.tier <= currentTier && !isRecentlyFailed(it) }
        val configs = if (availableConfigs.isEmpty()) turnServerConfigs.filter { !isRecentlyFailed(it) } else availableConfigs
        configs.forEach { servers.addAll(createIceServersFromConfig(it)) }

        Log.d(tag, "Using ${servers.size} ICE servers for tier $currentTier")
        return servers
    }

    private fun createIceServersFromConfig(config: TurnConfig): List<PeerConnection.IceServer> {
        return config.urls.mapNotNull { url ->
            try {
                if (config.username.isNotEmpty())
                    PeerConnection.IceServer.builder(url).setUsername(config.username).setPassword(config.password).createIceServer()
                else PeerConnection.IceServer.builder(url).createIceServer()
            } catch (e: Exception) {
                // If creation fails, mark the config as failed and skip it
                markServerAsFailed(config)
                Log.w(tag, "createIceServersFromConfig failed for $url: ${e.message}")
                null
            }
        }
    }

    private fun isRecentlyFailed(config: TurnConfig): Boolean {
        val cooldown = when (config.failCount) {
            0 -> 0L
            1 -> 30_000L
            2 -> 120_000L
            3 -> 300_000L
            else -> 600_000L
        }
        return (System.currentTimeMillis() - config.lastFailTime) < cooldown
    }

    /**
     * Marks a TURN config as failed (updates fail count and last fail time).
     * If many servers in current tier fail, escalate tier.
     */
    fun markServerAsFailed(config: TurnConfig) {
        config.lastFailTime = System.currentTimeMillis()
        config.failCount++
        if (!failedServers.contains(config)) failedServers.add(config)

        Log.w(tag, "Marked TURN server as failed: ${config.urls.firstOrNull() ?: "<unknown>"} (fail count: ${config.failCount})")

        // Check if we should escalate - be more lenient
        val currentTierConfigs = turnServerConfigs.filter { it.tier == currentTier }
        val recentlyFailedInTier = currentTierConfigs.count { isRecentlyFailed(it) }
        val totalInTier = currentTierConfigs.size

        // Escalate if more than 60% of current tier servers have failed recently
        if (totalInTier > 0 && recentlyFailedInTier >= (totalInTier * 0.6).toInt() && totalInTier > 1) {
            Log.w(tag, "Too many failures in tier $currentTier ($recentlyFailedInTier/$totalInTier), escalating")
            escalateToNextTier()
        }
    }

    fun resetAllFailures() {
        Log.d(tag, "Resetting all TURN server failures for fresh start")
        failedServers.clear()
        turnServerConfigs.forEach { config ->
            config.failCount = 0
            config.lastFailTime = 0L
        }
        currentTier = 1
    }

    /**
     * Called when all TURN servers appear exhausted. Resets failure state and attempts a different tier.
     */
    fun handleCompleteFailure() {
        Log.w(tag, "All TURN servers exhausted, resetting and trying again")
        resetAllFailures()
        // Move to a different tier to avoid immediately reusing problematic servers
        currentTier = (currentTier % 5) + 1
    }

    fun escalateToNextTier() { if (currentTier < 5) currentTier++ }
    fun forceEscalateToNextTier() { escalateToNextTier() }
    fun resetToTier1() { currentTier = 1 }
    fun getCurrentTier(): Int = currentTier
    fun getStatusInfo(): String = "Tier $currentTier / Servers OK: ${turnServerConfigs.size - failedServers.size}"

    /**
     * Emergency STUN servers to append when TURN tiers are failing.
     * This method is used by WebRTCClient to ensure there's at least a basic STUN fallback.
     */
    fun getEmergencyIceServers(): List<PeerConnection.IceServer> {
        return listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )
    }
}
