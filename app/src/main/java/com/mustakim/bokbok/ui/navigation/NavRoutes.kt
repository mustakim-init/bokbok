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
    object Onboarding : NavRoutes("onboarding")
    object SetupUsername : NavRoutes("setup_username")
    object Permissions : NavRoutes("permissions")

    // ============= MAIN APP =============
    object Lounge : NavRoutes("lounge")
    object Chats : NavRoutes("chats")
    object GameBoost : NavRoutes("game_boost")

    // ============= SECONDARY =============
    object Profile : NavRoutes("profile")
    object Settings : NavRoutes("settings")
    object Notifications : NavRoutes("notifications")

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

    companion object {
        /**
         * ✅ Check if a route is part of the main app flow (shows bottom nav)
         */
        fun isMainAppRoute(route: String?): Boolean {
            return route in listOf(
                Lounge.route,
                Chats.route,
                GameBoost.route
            )
        }

        /**
         * ✅ Check if a route is part of auth flow
         */
        fun isAuthRoute(route: String?): Boolean {
            return route in listOf(
                Splash.route,
                Login.route,
                Signup.route,
                GoogleSignup.route,
                Onboarding.route,
                SetupUsername.route,
                Permissions.route
            )
        }

        /**
         * ✅ Get the base route without parameters
         */
        fun getBaseRoute(route: String?): String? {
            return route?.substringBefore("/")
        }
    }
}