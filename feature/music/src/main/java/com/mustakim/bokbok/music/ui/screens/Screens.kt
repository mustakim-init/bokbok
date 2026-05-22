package com.mustakim.bokbok.music.ui.screens

import androidx.compose.runtime.Immutable
import com.mustakim.bokbok.music.R as MusicR
import com.mustakim.bokbok.core.R as CoreR
import com.mustakim.bokbok.ui.shared.NavIcon
import com.mustakim.bokbok.ui.shared.NavigationItem

@Immutable
sealed class Screens(
    val titleId: Int,
    val iconActive: NavIcon,
    val iconInactive: NavIcon,
    val route: String,
) {
    object Home : Screens(
        titleId = MusicR.string.home,
        iconInactive = NavIcon.Resource(CoreR.drawable.home_outlined),
        iconActive = NavIcon.Resource(CoreR.drawable.home_filled),
        route = "home"
    )

    object Search : Screens(
        titleId = MusicR.string.search,
        iconInactive = NavIcon.Resource(CoreR.drawable.search),
        iconActive = NavIcon.Resource(CoreR.drawable.search),
        route = "search"
    )

    object Library : Screens(
        titleId = MusicR.string.filter_library,
        iconInactive = NavIcon.Resource(CoreR.drawable.library_outlined),
        iconActive = NavIcon.Resource(CoreR.drawable.library_filled),
        route = "library"
    )

    object MoodAndGenres : Screens(
        titleId = MusicR.string.mood_and_genres,
        iconInactive = NavIcon.Resource(CoreR.drawable.style),
        iconActive = NavIcon.Resource(CoreR.drawable.style),
        route = "mood_and_genres"
    )

    object Settings : Screens(
        titleId = CoreR.string.settings,
        iconInactive = NavIcon.Resource(CoreR.drawable.settings),
        iconActive = NavIcon.Resource(CoreR.drawable.settings),
        route = "settings"
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
        fun mainScreens(): List<Screens> = listOfNotNull(Home, Search, MoodAndGenres, Library)
        fun sideBarScreens(): List<Screens> = listOfNotNull(Home, Search, MoodAndGenres, Library, Settings)
        
        val MainScreens: List<Screens> get() = mainScreens()
        val SideBarScreens: List<Screens> get() = sideBarScreens()
    }
}
