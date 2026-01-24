package com.mustakim.bokbok.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.mustakim.bokbok.data.remote.VirusTotalApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class SecurityStatus {
    SECURE, WARNING, MALICIOUS, UNKNOWN, SCANNING
}

data class ScanResult(
    val packageName: String,
    val appName: String,
    val status: SecurityStatus,
    val maliciousCount: Int = 0,
    val suspiciousCount: Int = 0,
    val harmlessCount: Int = 0,
    val undetectedCount: Int = 0,
    val message: String? = null,
    val priority: Int = 0, // Higher = more dangerous (used for sorting)
    val engineResults: List<com.mustakim.bokbok.data.remote.EngineResult> = emptyList()
)

data class DnsPreset(
    val name: String,
    val hostname: String,
    val description: String,
    val isFamilyFriendly: Boolean = false
)

@Singleton
class SecurityRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val virusTotalApi: VirusTotalApi,
    private val preferencesManager: com.mustakim.bokbok.data.local.PreferencesManager
) {
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow(0f)
    val scanProgress: StateFlow<Float> = _scanProgress.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    // Using a simple cache to avoid repeated API calls for the same app version
    private val resultCache = mutableMapOf<String, ScanResult>()

    suspend fun scanInstalledApps(apiKey: String) = withContext(Dispatchers.IO) {
        if (_isScanning.value) return@withContext
        
        _isScanning.value = true
        _scanProgress.value = 0f
        val results = mutableListOf<ScanResult>()
        
        try {
            val pm = context.packageManager
            val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0L))
            } else {
                pm.getInstalledPackages(0)
            }
            
            // 1. Get all installed packages
            val appList = installedPackages.map { packageInfo ->
                val appInfo = packageInfo.applicationInfo
                val isSystem = appInfo?.let { (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0 } ?: false
                val installer = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        pm.getInstallSourceInfo(packageInfo.packageName).installingPackageName
                    } else {
                        pm.getInstallerPackageName(packageInfo.packageName)
                    }
                } catch (e: Exception) { null }
                
                packageInfo to (isSystem to installer)
            }
            
            val total = appList.size.toFloat()
            
            appList.forEachIndexed { index, (packageInfo, info) ->
                val (isSystem, installer) = info
                val appInfo = packageInfo.applicationInfo
                val appName = appInfo?.loadLabel(pm)?.toString() ?: packageInfo.packageName
                val packageName = packageInfo.packageName
                
                // Skip our own app
                if (packageName == context.packageName) return@forEachIndexed
                
                _scanProgress.value = (index + 1) / total
                
                // 1. Local Pass: Check Bloatware Database (Robust OEM/Spyware detection)
                val bloatInfo = com.mustakim.bokbok.data.bloatware.BloatwareDatabase.getBloatwareInfo(context, packageName)
                
                val result = when {
                    bloatInfo != null -> {
                        // Flagged in local robust database
                        val type = bloatInfo.type?.uppercase() ?: "UNKNOWN"
                        val removalRating = bloatInfo.removal ?: "caution"
                        ScanResult(
                            packageName, appName, 
                            if (removalRating == "unsafe") SecurityStatus.WARNING else SecurityStatus.MALICIOUS,
                            message = "$type Bloatware: ${bloatInfo.description ?: "Known issues"}",
                            priority = 2 // WARNING/Bloatware level
                        )
                    }
                    isSystem -> {
                        // System app not in bloatware list - Considered Safe by robust local scanner
                        ScanResult(packageName, appName, SecurityStatus.SECURE, message = "System Protected", priority = 0)
                    }
                    installer == "com.android.vending" -> {
                        // Play Store apps are verified by Google Play Protect
                        ScanResult(packageName, appName, SecurityStatus.SECURE, message = "Play Store Verified", priority = 0)
                    }
                    else -> {
                        // 2. Cloud Pass: User-sideloaded app - Match against VirusTotal
                        val sourceDir = packageInfo.applicationInfo?.sourceDir
                        val hash = sourceDir?.let { getFileSha256(it) }
                        if (hash != null) {
                            if (apiKey.isNotBlank()) {
                                val vtResult = checkHashWithVirusTotal(hash, apiKey, packageName, appName)
                                // Add delay for VT rate limits to respect the 4 req/min free tier
                                if (vtResult.status != SecurityStatus.UNKNOWN) {
                                    kotlinx.coroutines.delay(15500L) 
                                }
                                vtResult
                            } else {
                                ScanResult(packageName, appName, SecurityStatus.UNKNOWN, message = "API key required for sideloaded app", priority = 3)
                            }
                        } else {
                            ScanResult(packageName, appName, SecurityStatus.UNKNOWN, message = "Hash failed", priority = 3)
                        }
                    }
                }
                
                if (result != null) {
                    results.add(result)
                    // Custom Hierarchy Sort: MALICIOUS (4) -> UNKNOWN (3) -> WARNING (2) -> SECURE (0)
                    _scanResults.value = results.sortedWith(
                        compareByDescending<ScanResult> { it.priority }
                        .thenBy { it.appName }
                    ).toList()
                }
            }
        } catch (e: Exception) {
            Log.e("SecurityRepository", "Error during scan", e)
        } finally {
            _isScanning.value = false
        }
    }

    private suspend fun checkHashWithVirusTotal(
        hash: String, 
        apiKey: String, 
        packageName: String, 
        appName: String
    ): ScanResult {
        // Return from cache if we already scanned this specific file hash in this session
        resultCache[hash]?.let { return it.copy(packageName = packageName, appName = appName) }

        return try {
            val response = virusTotalApi.getFileReport(apiKey, hash)
            if (response.isSuccessful) {
                val stats = response.body()?.data?.attributes?.lastAnalysisStats
                if (stats != null) {
                    val status = when {
                        stats.malicious > 0 -> SecurityStatus.MALICIOUS
                        stats.suspicious > 0 -> SecurityStatus.WARNING
                        else -> SecurityStatus.SECURE
                    }
                    val result = ScanResult(
                        packageName = packageName,
                        appName = appName,
                        status = status,
                        maliciousCount = stats.malicious,
                        suspiciousCount = stats.suspicious,
                        harmlessCount = stats.harmless,
                        undetectedCount = stats.undetected,
                        priority = if (status == SecurityStatus.MALICIOUS) 4 else 0,
                        engineResults = response.body()?.data?.attributes?.lastAnalysisResults?.values?.toList() ?: emptyList()
                    )
                    resultCache[hash] = result
                    result
                } else {
                    ScanResult(packageName, appName, SecurityStatus.UNKNOWN, message = "No analysis data", priority = 3)
                }
            } else if (response.code() == 404) {
                // Not found in VirusTotal 
                ScanResult(packageName, appName, SecurityStatus.UNKNOWN, message = "Unknown to database", priority = 3)
            } else {
                ScanResult(packageName, appName, SecurityStatus.UNKNOWN, message = "API Error: ${response.code()}", priority = 3)
            }
        } catch (e: Exception) {
            ScanResult(packageName, appName, SecurityStatus.UNKNOWN, message = e.message)
        }
    }

    private fun getFileSha256(filePath: String): String? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    // DNS Security Logic 
    val dnsPresets = listOf(
        DnsPreset("Cloudflare DNS", "1dot1dot1dot1.cloudflare-dns.com", "Fastest overall speed, best for gaming (PUBG)"),
        DnsPreset("Tiar DNS", "dot.tiar.app", "Low ping, gaming-friendly performance"),
        DnsPreset("Google DNS", "dns.google", "Stable, reliable browsing & streaming"),
        DnsPreset("Quad9 DNS", "dns.quad9.net", "High security, blocks malicious websites"),
        DnsPreset("AdGuard DNS", "dns.adguard.com", "Ads block + safe browsing"),
        DnsPreset("Uncensored DNS", "anycast.censurfridns.dk", "No censorship, unrestricted internet"),
        DnsPreset("Cloudflare Family", "family.cloudflare-dns.com", "Blocks adult content + phishing", true),
        DnsPreset("AdGuard Family", "family.adguard-dns.com", "Family-safe browsing + ads block", true)
    )

    private val _currentDns = MutableStateFlow<String?>(null)
    val currentDns: StateFlow<String?> = _currentDns.asStateFlow()

    suspend fun refreshDnsStatus() {
        val mode = com.mustakim.bokbok.util.ShizukuUtils.executeCommand("settings get global private_dns_mode").trim()
        val specifier = com.mustakim.bokbok.util.ShizukuUtils.executeCommand("settings get global private_dns_specifier").trim()
        
        _currentDns.value = if (mode == "hostname") specifier else "opportunistic"
    }

    suspend fun setDns(hostname: String?): Boolean {
        return if (hostname == null || hostname == "opportunistic") {
            com.mustakim.bokbok.util.ShizukuUtils.executeCommand("settings put global private_dns_mode opportunistic")
            true
        } else {
            com.mustakim.bokbok.util.ShizukuUtils.executeCommand("settings put global private_dns_mode hostname")
            com.mustakim.bokbok.util.ShizukuUtils.executeCommand("settings put global private_dns_specifier $hostname")
            true
        }.also { refreshDnsStatus() }
    }
}
