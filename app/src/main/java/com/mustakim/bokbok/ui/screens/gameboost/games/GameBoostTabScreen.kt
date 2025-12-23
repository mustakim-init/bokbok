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

    selectedGame?.let { game ->
        GameDetailScreen(
            game = game,
            viewModel = viewModel,
            onBack = { viewModel.clearSelectedGame() }
        )
    } ?: run {
        GameListScreen(viewModel = viewModel)
    }
}
