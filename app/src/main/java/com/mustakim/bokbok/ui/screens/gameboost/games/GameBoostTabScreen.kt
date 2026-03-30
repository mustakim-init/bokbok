package com.mustakim.bokbok.ui.screens.gameboost.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import com.mustakim.bokbok.viewmodel.GameSpaceViewModel

@Composable
fun GameBoostTabScreen(
    navController: NavController,
    viewModel: GameSpaceViewModel
) {
    GameListScreen(
        navController = navController,
        viewModel = viewModel
    )
}
