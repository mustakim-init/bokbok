package com.mustakim.bokbok.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mustakim.bokbok.data.repository.SecurityRepository
import com.mustakim.bokbok.data.repository.ScanResult
import com.mustakim.bokbok.data.repository.SecurityStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SecurityViewModel @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val preferencesManager: com.mustakim.bokbok.data.local.PreferencesManager
) : ViewModel() {

    val isScanning = securityRepository.isScanning
    val scanProgress = securityRepository.scanProgress
    val scanResults = securityRepository.scanResults

    val storedApiKey = preferencesManager.virustotalApiKey

    private val _securityScore = MutableStateFlow(100)
    val securityScore: StateFlow<Int> = _securityScore.asStateFlow()
    
    val currentDns = securityRepository.currentDns
    val dnsPresets = securityRepository.dnsPresets

    init {
        updateSecurityScore()
        refreshDnsStatus()
    }

    fun refreshDnsStatus() {
        viewModelScope.launch {
            securityRepository.refreshDnsStatus()
        }
    }

    fun startScan(apiKey: String) {
        viewModelScope.launch {
            if (apiKey.isNotBlank()) {
                preferencesManager.saveVirusTotalApiKey(apiKey)
            }
            securityRepository.scanInstalledApps(apiKey)
            updateSecurityScore()
        }
    }

    private fun updateSecurityScore() {
        val results = scanResults.value
        if (results.isEmpty()) {
            _securityScore.value = 100
            return
        }

        val maliciousCount = results.count { it.status == SecurityStatus.MALICIOUS && it.priority == 4 }
        val unknownCount = results.count { it.status == SecurityStatus.UNKNOWN }
        val bloatCount = results.count { it.status == SecurityStatus.WARNING || (it.status == SecurityStatus.MALICIOUS && it.priority == 2) }
        
        // Calculate score: Start at 100
        // -25 for verified Malicious (VT)
        // -10 for Unknown to VT database (Risk of new malware)
        // -5 for Bloatware/Spyware (Database match)
        val score = (100 - (maliciousCount * 25) - (unknownCount * 10) - (bloatCount * 5)).coerceIn(0, 100)
        _securityScore.value = score
    }

    fun setDns(hostname: String?) {
        viewModelScope.launch {
            securityRepository.setDns(hostname)
        }
    }
}
