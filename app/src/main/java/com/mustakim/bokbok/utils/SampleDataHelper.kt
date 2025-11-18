package com.mustakim.bokbok.utils

import com.mustakim.bokbok.data.model.FriendStatus
import com.mustakim.bokbok.data.model.RoomCategory
import com.mustakim.bokbok.data.model.UserStatus

object SampleDataHelper {



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
}
