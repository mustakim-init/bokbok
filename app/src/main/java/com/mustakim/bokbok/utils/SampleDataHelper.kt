package com.mustakim.bokbok.utils

import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.RoomCategory
import com.mustakim.bokbok.data.model.UserStatus
import com.mustakim.bokbok.data.model.VoiceRoom

object SampleDataHelper {

    // ✅ FIX: Create static timestamp ONCE
    private val baseTimestamp = System.currentTimeMillis()

    fun getSampleFriends(): List<FriendStatus> = listOf(
        FriendStatus(
            userId = "friend1",
            username = "alex_music",
            displayName = "Alex",
            profileImageUrl = "",
            status = UserStatus.IN_ROOM,
            currentRoomId = "room1",
            currentRoomCategory = RoomCategory.MUSIC
        ),
        FriendStatus(
            userId = "friend2",
            username = "sarah_gamer",
            displayName = "Sarah",
            profileImageUrl = "",
            status = UserStatus.IN_ROOM,
            currentRoomId = "room2",
            currentRoomCategory = RoomCategory.GAMING
        ),
        FriendStatus(
            userId = "friend3",
            username = "mike_study",
            displayName = "Mike",
            profileImageUrl = "",
            status = UserStatus.IDLE,
            currentRoomId = null,
            currentRoomCategory = null
        ),
        FriendStatus(
            userId = "friend4",
            username = "emma_casual",
            displayName = "Emma",
            profileImageUrl = "",
            status = UserStatus.ONLINE,
            currentRoomId = null,
            currentRoomCategory = null
        ),
        FriendStatus(
            userId = "friend5",
            username = "david_work",
            displayName = "David",
            profileImageUrl = "",
            status = UserStatus.IN_ROOM,
            currentRoomId = "room5",
            currentRoomCategory = RoomCategory.WORK
        ),
        FriendStatus(
            userId = "friend6",
            username = "lisa_music",
            displayName = "Lisa",
            profileImageUrl = "",
            status = UserStatus.ONLINE,
            currentRoomId = null,
            currentRoomCategory = null
        )
    )

    fun getSampleMyRooms(): List<VoiceRoom> = listOf(
        VoiceRoom(
            id = "myroom1",
            name = "My Chill Space",
            hostId = "me",
            hostName = "You",
            hostImageUrl = "",
            imageUrl = "",
            description = "Just hanging out and chatting 🎵",
            participants = listOf("me", "friend1", "friend2"),
            maxParticipants = 10,
            isPublic = true,
            category = RoomCategory.CASUAL,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "myroom2",
            name = "Study Session",
            hostId = "friend3",
            hostName = "Mike",
            hostImageUrl = "",
            imageUrl = "",
            description = "Quiet focus time 📚",
            participants = listOf("me", "friend3", "user7"),
            maxParticipants = 8,
            isPublic = false,
            category = RoomCategory.STUDY,
            createdAt = baseTimestamp - 300000  // ✅ Static timestamp
        )
    )

    fun getSamplePublicRooms(): List<VoiceRoom> = listOf(
        VoiceRoom(
            id = "pub1",
            name = "Late Night Vibes",
            hostId = "host1",
            hostName = "Alex Chen",
            hostImageUrl = "",
            imageUrl = "",
            description = "Chill music and good conversations",
            participants = listOf("host1", "user2", "user3"),
            maxParticipants = 10,
            isPublic = true,
            category = RoomCategory.MUSIC,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub2",
            name = "Gaming Squad",
            hostId = "host2",
            hostName = "Sarah J",
            hostImageUrl = "",
            imageUrl = "",
            description = "Playing Valorant!",
            participants = listOf("host2", "user5"),
            maxParticipants = 5,
            isPublic = true,
            category = RoomCategory.GAMING,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub3",
            name = "Coffee Break",
            hostId = "host3",
            hostName = "Emma W",
            hostImageUrl = "",
            imageUrl = "",
            description = "Random chats over coffee",
            participants = listOf("host3", "user8"),
            maxParticipants = 8,
            isPublic = true,
            category = RoomCategory.CASUAL,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub4",
            name = "Study Together",
            hostId = "host4",
            hostName = "Mike S",
            hostImageUrl = "",
            imageUrl = "",
            description = "Quiet study with background music",
            participants = listOf("host4", "user9", "user10", "user11"),
            maxParticipants = 15,
            isPublic = true,
            category = RoomCategory.STUDY,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub5",
            name = "Workout Motivation",
            hostId = "host5",
            hostName = "Lisa K",
            hostImageUrl = "",
            imageUrl = "",
            description = "Let's get fit together!",
            participants = listOf("host5", "user12", "user13"),
            maxParticipants = 12,
            isPublic = true,
            category = RoomCategory.HANGOUT,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub6",
            name = "Creative Jam",
            hostId = "host6",
            hostName = "David L",
            hostImageUrl = "",
            imageUrl = "",
            description = "Making art together",
            participants = listOf("host6", "user14"),
            maxParticipants = 10,
            isPublic = true,
            category = RoomCategory.WORK,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub7",
            name = "Chill Beats Lounge",
            hostId = "host7",
            hostName = "Ryan M",
            hostImageUrl = "",
            imageUrl = "",
            description = "Lo-fi hip hop radio",
            participants = listOf("host7", "user15", "user16", "user17"),
            maxParticipants = 20,
            isPublic = true,
            category = RoomCategory.MUSIC,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub8",
            name = "Tech Talk",
            hostId = "host8",
            hostName = "Anna P",
            hostImageUrl = "",
            imageUrl = "",
            description = "Discussing latest tech news",
            participants = listOf("host8", "user18", "user19"),
            maxParticipants = 10,
            isPublic = true,
            category = RoomCategory.CASUAL,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub9",
            name = "Minecraft Builders",
            hostId = "host9",
            hostName = "Tom H",
            hostImageUrl = "",
            imageUrl = "",
            description = "Building epic structures",
            participants = listOf("host9", "user20", "user21", "user22"),
            maxParticipants = 8,
            isPublic = true,
            category = RoomCategory.GAMING,
            createdAt = baseTimestamp  // ✅ Static timestamp
        ),
        VoiceRoom(
            id = "pub10",
            name = "Language Exchange",
            hostId = "host10",
            hostName = "Maria S",
            hostImageUrl = "",
            imageUrl = "",
            description = "Practice English & Spanish",
            participants = listOf("host10", "user23", "user24"),
            maxParticipants = 12,
            isPublic = true,
            category = RoomCategory.STUDY,
            createdAt = baseTimestamp  // ✅ Static timestamp
        )
    )
}
