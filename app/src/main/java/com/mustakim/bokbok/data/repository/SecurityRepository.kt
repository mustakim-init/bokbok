package com.mustakim.bokbok.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.mustakim.bokbok.data.remote.VirusTotalApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await
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
            
            // 1. Parallel Local Scan Pass
            val scanResultsList = supervisorScope {
                appList.map { (packageInfo, info) ->
                    async {
                        val (isSystem, installer) = info
                        val appInfo = packageInfo.applicationInfo
                        val appName = appInfo?.loadLabel(pm)?.toString() ?: packageInfo.packageName
                        val packageName = packageInfo.packageName
                        
                        if (packageName == context.packageName) return@async null

                        // Local Pass: Check Bloatware Database
                        val bloatInfo = com.mustakim.bokbok.data.bloatware.BloatwareDatabase.getBloatwareInfo(context, packageName)
                        
                        when {
                            bloatInfo != null -> {
                                val type = bloatInfo.type?.uppercase() ?: "UNKNOWN"
                                val removalRating = bloatInfo.removal ?: "caution"
                                ScanResult(
                                    packageName, appName, 
                                    if (removalRating == "unsafe") SecurityStatus.WARNING else SecurityStatus.MALICIOUS,
                                    message = "$type Bloatware: ${bloatInfo.description ?: "Known issues"}",
                                    priority = 2
                                )
                            }
                            isSystem -> ScanResult(packageName, appName, SecurityStatus.SECURE, message = "System Protected", priority = 0)
                            installer == "com.android.vending" -> ScanResult(packageName, appName, SecurityStatus.SECURE, message = "Play Store Verified", priority = 0)
                            else -> null // Sideloaded - Needs cloud pass
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            
            // Update initial results
            results.addAll(scanResultsList)
            updateResultsFlow(results)

            // 2. Throttled Cloud Pass for sideloaded apps
            appList.forEachIndexed { index, (packageInfo, info) ->
                val packageName = packageInfo.packageName
                val pmLocal = context.packageManager
                val appName = packageInfo.applicationInfo?.loadLabel(pmLocal)?.toString() ?: packageInfo.packageName

                _scanProgress.value = (index + 1) / total
                
                // If not already in results (meaning it was sideloaded)
                if (results.none { it.packageName == packageName } && packageName != context.packageName) {
                    val sourceDir = packageInfo.applicationInfo?.sourceDir
                    val hash = sourceDir?.let { getFileSha256(it) }
                    if (hash != null) {
                        if (apiKey.isNotBlank()) {
                            val vtResult = checkHashWithVirusTotal(hash, apiKey, packageName, appName)
                            results.add(vtResult)
                            updateResultsFlow(results)
                            
                            // Respect VT rate limits
                            if (vtResult.status != SecurityStatus.UNKNOWN) {
                                delay(15500L) 
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SecurityRepository", "Error during scan", e)
        } finally {
            _isScanning.value = false
        }
    }

    private fun updateResultsFlow(results: List<ScanResult>) {
        _scanResults.value = results.sortedWith(
            compareByDescending<ScanResult> { it.priority }
            .thenBy { it.appName }
        ).toList()
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
