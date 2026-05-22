package com.mustakim.bokbok.network
import com.mustakim.bokbok.network.NetworkBannerUiState

sealed interface NetworkBannerUiState {
    data object Hidden : NetworkBannerUiState

    data object Offline : NetworkBannerUiState

    data object BackOnline : NetworkBannerUiState
}
