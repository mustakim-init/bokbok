package com.mustakim.bokbok.viewmodel

sealed class CompanionUiState {
    object Idle : CompanionUiState()
    object Generating : CompanionUiState()
    data class Streaming(val partialResponse: String) : CompanionUiState()
    data class Error(val message: String) : CompanionUiState()
}
