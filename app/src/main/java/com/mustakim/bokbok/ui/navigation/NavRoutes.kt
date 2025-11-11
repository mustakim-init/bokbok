package com.mustakim.bokbok.ui.navigation

sealed class NavRoutes(val route: String) {
    // Auth
    object Splash : NavRoutes("splash")
    object Login : NavRoutes("login")
    object Onboarding : NavRoutes("onboarding")

    object Signup : NavRoutes("signup")

    object SetupUsername : NavRoutes("setup_username")
    object Permissions : NavRoutes("permissions")

    // Main App
    object Lounge : NavRoutes("lounge")
    object Chats : NavRoutes("chats")
    object GameBoost : NavRoutes("game_boost")

    // Secondary
    object Profile : NavRoutes("profile")
    object Settings : NavRoutes("settings")
    object Notifications : NavRoutes("notifications")

    // Room
    object Room : NavRoutes("room/{roomId}") {
        fun createRoute(roomId: String) = "room/$roomId"
    }

    // Chat
    object Chat : NavRoutes("chat/{userId}") {
        fun createRoute(userId: String) = "chat/$userId"
    }
}
