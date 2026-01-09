package com.mustakim.bokbok.ui.navigation

/**
 * ✅ Centralized navigation routes with type-safe route creation
 * ✅ Added helper functions for parameterized routes
 * ✅ Added route validation
 */
sealed class NavRoutes(val route: String) {
    // ============= AUTH =============
    object Splash : NavRoutes("splash")
    object Login : NavRoutes("login")
    object Signup : NavRoutes("signup")
    object GoogleSignup : NavRoutes("google_signup")
    object Permissions : NavRoutes("permissions")

    // ============= MAIN APP =============
    object Lounge : NavRoutes("lounge")
    object Chats : NavRoutes("chats")
    object GameBoost : NavRoutes("game_boost")

    // ============= SECONDARY =============
    object Profile : NavRoutes("profile")
    object Settings : NavRoutes("settings")
    object Notifications : NavRoutes("notifications")
    object BatteryOptimization : NavRoutes("battery_optimization")
    object RecordingsGallery : NavRoutes("recordings_gallery")
    object VideoPlayer : NavRoutes("video_player?path={path}") {
        fun createRoute(path: String): String {
            val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
            return "video_player?path=$encodedPath"
        }
    }

    // ============= VOICE ROOM =============
    object Room : NavRoutes("room/{roomId}") {
        fun createRoute(roomId: String): String {
            require(roomId.isNotBlank()) { "Room ID cannot be blank" }
            return "room/$roomId"
        }
    }

    // ============= CHAT =============
    object Chat : NavRoutes("chat/{userId}") {
        fun createRoute(userId: String): String {
            require(userId.isNotBlank()) { "User ID cannot be blank" }
            return "chat/$userId"
        }
    }

    object GroupChat : NavRoutes("group_chat/{groupId}") {
        fun createRoute(groupId: String): String {
            require(groupId.isNotBlank()) { "Group ID cannot be blank" }
            return "group_chat/$groupId"
        }
    }

    object ChatDetails : NavRoutes("chat_details/{chatId}?isGroup={isGroup}") {
        fun createRoute(chatId: String, isGroup: Boolean = false): String {
            require(chatId.isNotBlank()) { "Chat ID cannot be blank" }
            return "chat_details/$chatId?isGroup=$isGroup"
        }
    }

    object ChatMembers : NavRoutes("chat_members/{groupId}") {
        fun createRoute(groupId: String): String {
            require(groupId.isNotBlank()) { "Group ID cannot be blank" }
            return "chat_members/$groupId"
        }
    }
}