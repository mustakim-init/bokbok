package com.mustakim.bokbok.utils

import java.util.concurrent.ConcurrentHashMap

/**
 * Local rate limiter to prevent summon spam.
 * 
 * Rules:
 * - Max 3 summons per chat per minute
 * - Max 1 @everyone per chat per 5 minutes
 * - Resets when the time window expires
 * 
 * This runs entirely locally - no backend costs.
 */
object SummonRateLimiter {
    
    private const val MAX_SUMMONS_PER_MINUTE = 3
    private const val SUMMON_WINDOW_MS = 60_000L // 1 minute
    
    private const val MAX_EVERYONE_PER_WINDOW = 1
    private const val EVERYONE_WINDOW_MS = 300_000L // 5 minutes
    
    // chatId -> list of timestamps
    private val summonTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    private val everyoneTimestamps = ConcurrentHashMap<String, MutableList<Long>>()
    
    /**
     * Check if a summon is allowed and record it if so.
     * 
     * @param chatId The chat ID
     * @param isEveryone Whether this is an @everyone summon
     * @return RateLimitResult indicating if allowed and cooldown info
     */
    fun checkAndRecord(chatId: String, isEveryone: Boolean): RateLimitResult {
        val now = System.currentTimeMillis()
        
        // Clean old timestamps
        cleanOldTimestamps(chatId, now)
        
        // Check @everyone limit
        if (isEveryone) {
            val everyoneList = everyoneTimestamps.getOrPut(chatId) { mutableListOf() }
            val recentEveryone = everyoneList.count { now - it < EVERYONE_WINDOW_MS }
            
            if (recentEveryone >= MAX_EVERYONE_PER_WINDOW) {
                val oldestEveryone = everyoneList.minOrNull() ?: now
                val cooldownMs = EVERYONE_WINDOW_MS - (now - oldestEveryone)
                return RateLimitResult(
                    allowed = false,
                    reason = "@everyone is on cooldown",
                    cooldownSeconds = (cooldownMs / 1000).toInt()
                )
            }
        }
        
        // Check regular summon limit
        val summonList = summonTimestamps.getOrPut(chatId) { mutableListOf() }
        val recentSummons = summonList.count { now - it < SUMMON_WINDOW_MS }
        
        if (recentSummons >= MAX_SUMMONS_PER_MINUTE) {
            val oldestSummon = summonList.minOrNull() ?: now
            val cooldownMs = SUMMON_WINDOW_MS - (now - oldestSummon)
            return RateLimitResult(
                allowed = false,
                reason = "Too many summons. Please wait.",
                cooldownSeconds = (cooldownMs / 1000).toInt()
            )
        }
        
        // Record this summon
        summonList.add(now)
        if (isEveryone) {
            everyoneTimestamps.getOrPut(chatId) { mutableListOf() }.add(now)
        }
        
        return RateLimitResult(allowed = true)
    }
    
    /**
     * Get remaining summons before hitting the limit.
     */
    fun getRemainingCount(chatId: String): Int {
        val now = System.currentTimeMillis()
        cleanOldTimestamps(chatId, now)
        
        val summonList = summonTimestamps[chatId] ?: return MAX_SUMMONS_PER_MINUTE
        val recentSummons = summonList.count { now - it < SUMMON_WINDOW_MS }
        return (MAX_SUMMONS_PER_MINUTE - recentSummons).coerceAtLeast(0)
    }
    
    private fun cleanOldTimestamps(chatId: String, now: Long) {
        summonTimestamps[chatId]?.removeIf { now - it > SUMMON_WINDOW_MS }
        everyoneTimestamps[chatId]?.removeIf { now - it > EVERYONE_WINDOW_MS }
    }
    
    /**
     * Reset rate limits for a chat (useful for testing).
     */
    fun reset(chatId: String) {
        summonTimestamps.remove(chatId)
        everyoneTimestamps.remove(chatId)
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val reason: String = "",
    val cooldownSeconds: Int = 0
)
