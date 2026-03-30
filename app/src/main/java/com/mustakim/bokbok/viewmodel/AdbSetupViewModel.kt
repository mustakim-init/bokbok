package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.adb.AdbPairingManager
import com.mustakim.bokbok.data.adb.ResurrectionSetupState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdbSetupViewModel @Inject constructor(
    private val adbPairingManager: AdbPairingManager
) : ViewModel() {

    val setupState: StateFlow<ResurrectionSetupState> = adbPairingManager.state

    fun getAdbPublicKey(): String = adbPairingManager.getAdbPublicKey()

    fun runSetup() {
        viewModelScope.launch {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                adbPairingManager.runSetup()
            }
        }
    }

    fun initiatePairing() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            adbPairingManager.startPairingService()
        }
    }

    fun selfAuthorizeViaShizuku() {
        viewModelScope.launch {
            adbPairingManager.selfAuthorizeViaShizuku()
        }
    }

    fun submitPairingCode(port: Int, code: String) {
        viewModelScope.launch {
            adbPairingManager.pair(port, code)
        }
    }

    fun resetSetup() {
        adbPairingManager.resetSetup()
    }
}
