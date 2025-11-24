package com.mustakim.bokbok.data.model

import androidx.compose.runtime.Immutable


@Immutable
data class VoiceRoom(
    val id: String = "",
    val name: String = "",
    val hostId: String = "",
    val hostName: String = "",
    val hostImageUrl: String = "",
    val imageUrl: String = "",  // ← ADD THIS
    val description: String = "",
    val participants: List<String> = emptyList(),
    val maxParticipants: Int = 10,
    val isPublic: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val category: RoomCategory = RoomCategory.CASUAL,
    // NEW: how many are currently in the call (RTDB presence)
    val currentOnline: Int = 0,
    val allowJoinNotifications: Boolean = true
) {
    val participantCount: Int get() = participants.size
    val isFull: Boolean get() = participants.size >= maxParticipants

    fun toMap(): Map<String, Any> = mapOf(
        "id" to id,
        "name" to name,
        "hostId" to hostId,
        "hostName" to hostName,
        "hostImageUrl" to hostImageUrl,
        "imageUrl" to imageUrl,  // ← ADD THIS
        "description" to description,
        "participants" to participants,
        "maxParticipants" to maxParticipants,
        "isPublic" to isPublic,
        "createdAt" to createdAt,
        "category" to category.name,
        "allowJoinNotifications" to allowJoinNotifications
    )

    companion object {
        fun fromMap(map: Map<String, Any>): VoiceRoom = VoiceRoom(
            id = map["id"] as? String ?: "",
            name = map["name"] as? String ?: "",
            hostId = map["hostId"] as? String ?: "",
            hostName = map["hostName"] as? String ?: "",
            hostImageUrl = map["hostImageUrl"] as? String ?: "",
            imageUrl = map["imageUrl"] as? String ?: "",  // ← ADD THIS
            description = map["description"] as? String ?: "",
            participants = (map["participants"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
            maxParticipants = (map["maxParticipants"] as? Long)?.toInt() ?: 10,
            isPublic = map["isPublic"] as? Boolean ?: true,
            createdAt = map["createdAt"] as? Long ?: 0L,
            category = RoomCategory.valueOf(map["category"] as? String ?: "CASUAL"),
            allowJoinNotifications = map["allowJoinNotifications"] as? Boolean ?: true
        )
    }
}


enum class RoomCategory(val displayName: String) {
    CASUAL("Casual"),
    GAMING("Gaming"),
    STUDY("Study"),
    MUSIC("Music"),
    WORK("Work"),
    HANGOUT("Hangout")
}
