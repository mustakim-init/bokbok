package com.mustakim.bokbok.data.model

import androidx.compose.runtime.Immutable


@Immutable
data class User(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val displayName: String = "",
    val bio: String = "",
    val profileImageUrl: String = "",
    val phoneNumber: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastSeen: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uid" to uid,
        "username" to username,
        "email" to email,
        "displayName" to displayName,
        "bio" to bio,
        "profileImageUrl" to profileImageUrl,
        "phoneNumber" to phoneNumber,
        "createdAt" to createdAt,
        "lastSeen" to lastSeen
    )

    companion object {
        fun fromMap(map: Map<String, Any>): User = User(
            uid = map["uid"] as? String ?: "",
            username = map["username"] as? String ?: "",
            email = map["email"] as? String ?: "",
            displayName = map["displayName"] as? String ?: "",
            bio = map["bio"] as? String ?: "",
            profileImageUrl = map["profileImageUrl"] as? String ?: "",
            phoneNumber = map["phoneNumber"] as? String ?: "",
            createdAt = map["createdAt"] as? Long ?: 0L,
            lastSeen = map["lastSeen"] as? Long ?: 0L
        )
    }
}
