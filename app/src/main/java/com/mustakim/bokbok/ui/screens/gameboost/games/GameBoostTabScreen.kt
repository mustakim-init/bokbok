package com.mustakim.bokbok.ui.screens.gameboost.games

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mustakim.bokbok.viewmodel.GameSpaceViewModel

@Composable
fun GameBoostTabScreen(
    viewModel: GameSpaceViewModel
) {
    val selectedGame by viewModel.selectedGame.collectAsState()

    if (selectedGame != null) {
        GameDetailScreen(
            game = selectedGame!!,
            viewModel = viewModel,
            onBack = { viewModel.clearSelectedGame() }
        )
    } else {
        GameListScreen(viewModel = viewModel)
    }
}
