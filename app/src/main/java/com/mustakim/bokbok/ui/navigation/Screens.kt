package com.mustakim.bokbok.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.mustakim.bokbok.R

@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    @DrawableRes val iconIdInactive: Int,
    @DrawableRes val iconIdActive: Int,
    val route: String,
) {
    object Lounge : Screens(
        titleId = R.string.lounge,
        iconIdInactive = R.drawable.home_outlined, 
        iconIdActive = R.drawable.home_filled,
        route = NavRoutes.Lounge.route
    )

    object Music : Screens(
        titleId = R.string.music,
        iconIdInactive = R.drawable.library_outlined,
        iconIdActive = R.drawable.library_filled,
        route = "music" // This is handled specially in MainScaffold
    )

    object Chats : Screens(
        titleId = R.string.chats,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = NavRoutes.Chats.route
    )

    object GameBoost : Screens(
        titleId = R.string.game_boost,
        iconIdInactive = R.drawable.home_outlined,
        iconIdActive = R.drawable.home_filled,
        route = NavRoutes.GameBoost.route
    )

    companion object {
        val MainScreens = listOf(Lounge, Music, Chats, GameBoost)
    }
}
