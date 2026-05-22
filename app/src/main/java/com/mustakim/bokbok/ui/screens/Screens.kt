package com.mustakim.bokbok.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Gamepad
import androidx.compose.material.icons.outlined.Home
import androidx.compose.runtime.Immutable
import com.mustakim.bokbok.R
import com.mustakim.bokbok.ui.shared.NavIcon
import com.mustakim.bokbok.ui.shared.NavigationItem

@Immutable
sealed class Screens(
    val titleId: Int,
    val iconInactive: NavIcon,
    val iconActive: NavIcon,
    val route: String,
) {
    object Lounge : Screens(
        titleId = R.string.lounge,
        iconInactive = NavIcon.Vector(Icons.Outlined.Home),
        iconActive = NavIcon.Vector(Icons.Filled.Home),
        route = "lounge"
    )

    object Music : Screens(
        titleId = R.string.app_name,
        iconInactive = NavIcon.Resource(R.drawable.ic_music),
        iconActive = NavIcon.Resource(R.drawable.ic_music),
        route = "music"
    )

    object Chats : Screens(
        titleId = R.string.chats,
        iconInactive = NavIcon.Vector(Icons.AutoMirrored.Outlined.Chat),
        iconActive = NavIcon.Vector(Icons.AutoMirrored.Filled.Chat),
        route = "chats"
    )

    object GameBoost : Screens(
        titleId = R.string.game_boost,
        iconInactive = NavIcon.Vector(Icons.Outlined.Gamepad),
        iconActive = NavIcon.Vector(Icons.Filled.Gamepad),
        route = "game_boost"
    )

    object Settings : Screens(
        titleId = R.string.settings,
        iconInactive = NavIcon.Resource(R.drawable.settings),
        iconActive = NavIcon.Resource(R.drawable.settings),
        route = "settings"
    )

    object Search : Screens(
        titleId = R.string.search,
        iconInactive = NavIcon.Resource(R.drawable.search),
        iconActive = NavIcon.Resource(R.drawable.search),
        route = "search"
    )

    fun toNavigationItem(): NavigationItem {
        return NavigationItem(
            id = route,
            titleId = titleId,
            iconActive = iconActive,
            iconInactive = iconInactive
        )
    }

    companion object {
        val MainScreens = listOf(Lounge, Music, Chats, GameBoost)
        val SideBarScreens = listOf(Lounge, Music, Chats, GameBoost, Settings)
    }
}
