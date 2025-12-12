package com.mustakim.bokbok.utils

/**
 * Parses "summon" mentions from message text.
 * 
 * Supported syntax:
 * - `\summon @username` - Summon a specific user by display name
 * - `\summon @everyone` - Summon all members (groups only)
 * 
 * Returns a SummonResult containing parsed user references.
 */
object SummonParser {
    
    private val SUMMON_PATTERN = Regex("""\\summon\s+@(\S+)""", RegexOption.IGNORE_CASE)
    private const val EVERYONE_KEYWORD = "everyone"
    
    /**
     * Parse summon commands from message text.
     * 
     * @param text The message text to parse
     * @param availableUsers Map of display names (lowercase) to user IDs
     * @param allMemberIds All member IDs in the chat (for @everyone)
     * @param senderId The sender's user ID (excluded from @everyone)
     * @return SummonResult containing the list of user IDs to summon
     */
    fun parse(
        text: String,
        availableUsers: Map<String, String>, // displayName.lowercase() -> userId
        allMemberIds: List<String> = emptyList(),
        senderId: String
    ): SummonResult {
        val matches = SUMMON_PATTERN.findAll(text)
        val summonedIds = mutableSetOf<String>()
        var hasEveryone = false
        
        for (match in matches) {
            val targetName = match.groupValues[1].lowercase()
            
            when {
                targetName == EVERYONE_KEYWORD -> {
                    hasEveryone = true
                    // Add all members except sender
                    summonedIds.addAll(allMemberIds.filter { it != senderId })
                }
                availableUsers.containsKey(targetName) -> {
                    val userId = availableUsers[targetName]!!
                    if (userId != senderId) { // Don't summon yourself
                        summonedIds.add(userId)
                    }
                }
            }
        }
        
        return SummonResult(
            summonedUserIds = summonedIds.toList(),
            hasEveryone = hasEveryone,
            originalText = text
        )
    }
    
    /**
     * Check if message contains any summon commands.
     */
    fun containsSummon(text: String): Boolean {
        return SUMMON_PATTERN.containsMatchIn(text)
    }
    
    /**
     * Get the display name references from text (for highlighting).
     */
    fun getSummonedNames(text: String): List<String> {
        return SUMMON_PATTERN.findAll(text).map { it.groupValues[1] }.toList()
    }
}

data class SummonResult(
    val summonedUserIds: List<String>,
    val hasEveryone: Boolean,
    val originalText: String
) {
    val hasSummons: Boolean get() = summonedUserIds.isNotEmpty()
}
