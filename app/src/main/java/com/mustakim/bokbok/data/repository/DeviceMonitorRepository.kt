package com.mustakim.bokbok.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.StatFs
import com.mustakim.bokbok.data.model.BatteryInfo
import com.mustakim.bokbok.data.model.CpuInfo
import com.mustakim.bokbok.data.model.GpuInfo
import com.mustakim.bokbok.data.model.ProcessInfo
import com.mustakim.bokbok.data.model.RamInfo
import com.mustakim.bokbok.data.model.StorageBreakdown
import com.mustakim.bokbok.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.os.Build
import android.os.Process
import android.os.storage.StorageManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader

class DeviceMonitorRepository(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var lastCpuTotal: Long = 0
    private var lastCpuIdle: Long = 0
    private var lastCoreTotals: MutableList<Long> = mutableListOf()
    private var lastCoreIdles: MutableList<Long> = mutableListOf()

    // Cache working paths to avoid expensive iterations
    private var workingGpuLoadPath: String? = null
    private var workingGpuFreqPath: String? = null
    private var workingGpuTempPath: String? = null
    private val workingCoreOnlinePaths = mutableMapOf<Int, String>()
    private val workingCoreFreqPaths = mutableMapOf<Int, String>()
    private var isProcStatRestricted: Boolean? = null
    
    // Path blocking to avoid repeating failed attempts
    private val blockedDirectPaths = mutableSetOf<String>()
    private val blockedShellPaths = mutableSetOf<String>()
    
    // Pre-compiled Regex patterns
    private val whitespaceRegex = Regex("\\s+")
    private val gpuBusyRegex = Regex("^(\\d+)\\s+(\\d+)")
    private val sizeRegex = Regex("""([\d.]+)\s*([GgMmKkBb]*)""")
    
    // Static info cache
    private data class GpuStaticInfo(
        val renderer: String,
        val model: String?,
        val vendor: String?,
        val apiVersion: String?
    )
    private var cachedGpuStaticInfo: GpuStaticInfo? = null

    // Reflection cache for Shizuku
    private object ShizukuReflection {
        private var methodsInitialized = false
        var getInputStream: java.lang.reflect.Method? = null
        var waitFor: java.lang.reflect.Method? = null
        var destroy: java.lang.reflect.Method? = null

        fun init(processClass: Class<*>) {
            if (methodsInitialized) return
            try {
                getInputStream = processClass.getMethod("getInputStream")
                waitFor = processClass.getMethod("waitFor")
                destroy = processClass.getMethod("destroy")
                methodsInitialized = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getCpuInfo(): CpuInfo = withContext(Dispatchers.IO) {
        val loads = calculateCpuLoad()
        val coreCount = Runtime.getRuntime().availableProcessors()
        val frequencies = mutableListOf<Long>()
        val onlineStatus = mutableListOf<Boolean>()

        for (i in 0 until coreCount) {
            frequencies.add(readFreq(i))
            onlineStatus.add(isCoreOnline(i))
        }

        CpuInfo(
            loadPercent = loads.first,
            coreLoads = loads.second,
            frequencies = frequencies,
            onlineStatus = onlineStatus
        )
    }

    private suspend fun calculateCpuLoad(): Pair<Float, List<Float>> {
        var totalLoad = 0f
        val coreLoads = mutableListOf<Float>()
        
        // Try reading /proc/stat directly first
        var statContent: String? = null
        if (isProcStatRestricted != true) {
            try {
                val directContent = File("/proc/stat").readText()
                if (directContent.isNotEmpty() && directContent.startsWith("cpu ")) {
                    statContent = directContent
                    isProcStatRestricted = false
                }
            } catch (_: Exception) {
                isProcStatRestricted = true
            }
        }
        
        // Fallback to Shizuku if direct read failed
        if (statContent == null) {
            statContent = readShellCommand("cat /proc/stat", 300)
        }

        if (!statContent.isNullOrEmpty()) {
            try {
                val reader = BufferedReader(StringReader(statContent))
                var line = reader.readLine()

                // Overall CPU
                if (line != null && line.startsWith("cpu ")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val idle = parts[4].toLong()
                        val total = parts.subList(1, 8.coerceAtMost(parts.size)).mapNotNull { it.toLongOrNull() }.sum()

                        if (lastCpuTotal > 0) {
                            val diffTotal = total - lastCpuTotal
                            val diffIdle = idle - lastCpuIdle
                            totalLoad = if (diffTotal > 0) (diffTotal - diffIdle).toFloat() / diffTotal * 100f else 0f
                        }
                        lastCpuTotal = total
                        lastCpuIdle = idle
                    }
                }

                // Per core CPU
                var coreIdx = 0
                while (true) {
                    line = reader.readLine()
                    if (line == null || !line.startsWith("cpu")) break 
                    if (!line.startsWith("cpu$coreIdx")) {
                        if (!line.startsWith("cpu")) break 
                        continue 
                    }
                    
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        val idle = parts[4].toLong()
                        val total = parts.subList(1, 8.coerceAtMost(parts.size)).mapNotNull { it.toLongOrNull() }.sum()

                        if (lastCoreTotals.size <= coreIdx) {
                            lastCoreTotals.add(total)
                            lastCoreIdles.add(idle)
                            coreLoads.add(0f)
                        } else {
                            val diffTotal = total - lastCoreTotals[coreIdx]
                            val diffIdle = idle - lastCoreIdles[coreIdx]
                            val load = if (diffTotal > 0) (diffTotal - diffIdle).toFloat() / diffTotal * 100f else 0f
                            coreLoads.add(load.coerceIn(0f, 100f))
                            lastCoreTotals[coreIdx] = total
                            lastCoreIdles[coreIdx] = idle
                        }
                    }
                    coreIdx++
                }
                reader.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Pair(totalLoad.coerceIn(0f, 100f), coreLoads)
    }

    private suspend fun readFreq(core: Int): Long {
        val path = workingCoreFreqPaths[core] ?: "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq"
        
        // Attempt direct read first if not blocked
        if (!blockedDirectPaths.contains(path)) {
            try {
                val freq = File(path).readText().trim().toLong()
                workingCoreFreqPaths[core] = path
                return freq
            } catch (_: Exception) {
                blockedDirectPaths.add(path)
            }
        }

        // Fallback to Shell if not blocked
        if (!blockedShellPaths.contains(path)) {
            val output = readShellCommand("cat $path", 300)
            if (!output.isNullOrEmpty()) {
                workingCoreFreqPaths[core] = path
                return output.trim().toLongOrNull() ?: 0L
            }
        }
        return 0L
    }
    
    private suspend fun isCoreOnline(core: Int): Boolean {
        val path = workingCoreOnlinePaths[core] ?: "/sys/devices/system/cpu/cpu$core/online"
        
        // Try direct
        if (!blockedDirectPaths.contains(path)) {
            try {
                val content = File(path).readText().trim()
                workingCoreOnlinePaths[core] = path
                return content == "1"
            } catch (_: Exception) {
                blockedDirectPaths.add(path)
            }
        }

        // Try shell
        if (!blockedShellPaths.contains(path)) {
            val output = readShellCommand("cat $path", 300)
            if (output != null) {
                workingCoreOnlinePaths[core] = path
                return output.trim() == "1"
            }
        }
        return true // Assume online if we can't read it
    }

    private suspend fun readShellCommand(command: String, timeoutMs: Long = 2000): String? = withContext(Dispatchers.IO) {
        if (!checkShizukuPermission()) return@withContext null
        
        // Only block if we know this specific command/path is globally inaccessible via shell
        if (command.startsWith("cat ")) {
            val path = command.substring(4).trim()
            if (blockedShellPaths.contains(path)) return@withContext null
        }

        var process: Any? = null
        try {
            withTimeoutOrNull(timeoutMs) {
                val binder = Shizuku.getBinder() ?: return@withTimeoutOrNull null
                val service = IShizukuService.Stub.asInterface(binder)
                process = service.newProcess(arrayOf("sh", "-c", command), null, null)
                
                if (process == null) return@withTimeoutOrNull null
                
                val processClass = process!!.javaClass
                ShizukuReflection.init(processClass)

                val isObj = ShizukuReflection.getInputStream?.invoke(process) as? ParcelFileDescriptor
                    ?: return@withTimeoutOrNull null
                
                val output = StringBuilder()
                val reader = BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(isObj)))
                
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    output.append(line).append("\n")
                }
                
                ShizukuReflection.waitFor?.invoke(process)
                val result = output.toString()
                
                if (command.startsWith("cat ")) {
                    val path = command.substring(4).trim()
                    if (result.contains("denied", true) || result.contains("inaccessible", true) || 
                        result.contains("Not found", true)) {
                        blockedShellPaths.add(path)
                    }
                }
                
                result
            }
        } catch (_: Exception) {
            null
        } finally {
            try {
                if (process != null) {
                    ShizukuReflection.destroy?.invoke(process)
                }
            } catch (_: Exception) {}
        }
    }

    private fun checkShizukuPermission(): Boolean {
        try {
             if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return false
             if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return true
             return false
        } catch (_: Throwable) {
            return false
        }
    }



    suspend fun getRamInfo(): RamInfo {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val total = memInfo.totalMem / (1024 * 1024)
        val available = memInfo.availMem / (1024 * 1024)
        val used = total - available
        val percent = (used.toFloat() / total.toFloat()) * 100f

        var swapTotal: Long = 0
        var swapFree: Long = 0
        var swapCached: Long = 0
        
        try {
            // Priority 1: Direct read
            val path = "/proc/meminfo"
            var content: String? = null
            if (!blockedDirectPaths.contains(path)) {
                try { 
                    content = File(path).readText()
                } catch (_: Exception) {
                    blockedDirectPaths.add(path)
                }
            }
            
            // Priority 2: Shizuku fallback
            if (content.isNullOrEmpty() && !blockedShellPaths.contains(path)) {
                content = readShellCommand("cat $path", 500)
            }

            if (!content.isNullOrEmpty()) {
                val lines = content.split("\n")
                for (line in lines) {
                    val parts = line.split(" +".toRegex())
                    if (parts.size >= 2) {
                        when {
                            line.startsWith("SwapTotal:") -> swapTotal = parts[1].toLong() / 1024
                            line.startsWith("SwapFree:") -> swapFree = parts[1].toLong() / 1024
                            line.startsWith("SwapCached:") -> swapCached = parts[1].toLong() / 1024
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }

        val swapUsed = swapTotal - swapFree
        val swapPercent = if (swapTotal > 0) (swapUsed.toFloat() / swapTotal.toFloat()) * 100f else 0f

        return RamInfo(
            totalMb = total,
            usedMb = used,
            availableMb = available,
            usagePercent = percent,
            swapTotalMb = swapTotal,
            swapUsedMb = swapUsed,
            swapUsagePercent = swapPercent,
            swapCachedMb = swapCached
        )
    }

    suspend fun getBatteryInfo(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level / scale.toFloat() * 100).toInt() else 0
        
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        
        val voltage = (intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0) / 1000f
        val healthInt = intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN) ?: BatteryManager.BATTERY_HEALTH_UNKNOWN
        
        val health = when (healthInt) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Health: Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        // Microamps to milliamps. Note: some devices use microamps, some milliamps.
        val currentMicro = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentMicro > 10000 || currentMicro < -10000) currentMicro / 1000 else currentMicro
        
        // Power calculation W = V * I
        val powerW = (voltage * (currentMa.toFloat() / 1000f))

        return BatteryInfo(
            level = batteryPct,
            temperatureCelsius = temp,
            isCharging = isCharging,
            currentMa = currentMa,
            voltageV = voltage,
            health = health,
            powerW = powerW
        )
    }

    suspend fun getStorageInfo(): StorageInfo {
        var totalGb = 0f
        var availableGb = 0f
        var usedGb = 0f

        // Try StorageStatsManager first (API 26+) for accurate Total incl. System Partition
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasUsageStatsPermission()) {
            try {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val uuid = StorageManager.UUID_DEFAULT
                val totalBytes = storageStatsManager.getTotalBytes(uuid)
                val freeBytes = storageStatsManager.getFreeBytes(uuid)
                
                totalGb = totalBytes / (1024f * 1024f * 1024f)
                availableGb = freeBytes / (1024f * 1024f * 1024f)
                usedGb = totalGb - availableGb
            } catch (_: Exception) {
                // Fallback
            }
        }

        if (totalGb == 0f) {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            totalGb = (totalBlocks * blockSize) / (1024f * 1024f * 1024f)
            availableGb = (availableBlocks * blockSize) / (1024f * 1024f * 1024f)
            usedGb = totalGb - availableGb
        }
        
        val percent = if (totalGb > 0) (usedGb / totalGb) * 100f else 0f
        
        return StorageInfo(
            totalGb = totalGb,
            usedGb = usedGb,
            availableGb = availableGb,
            usagePercent = percent
        )
    }

    suspend fun getGpuInfo(): GpuInfo = withContext(Dispatchers.IO) {
        val loadPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/gpubusy",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/class/devfreq/1c00000.gpu/gpu_load",
            "/sys/class/devfreq/soc:qcom,kgsl-3d0/gpu_load",
            "/sys/kernel/gpu/gpu_busy"
        )
        
        val freqPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpuclk",
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/class/devfreq/1c00000.gpu/cur_freq",
            "/sys/class/devfreq/soc:qcom,kgsl-3d0/cur_freq",
            "/sys/kernel/gpu/gpu_clock"
        )
        
        var load: Int? = null
        val loadPathsToTry = if (workingGpuLoadPath != null) listOf(workingGpuLoadPath!!) + loadPaths else loadPaths
        
        for (path in loadPathsToTry) {
            if (load != null) break
            
            // 1. Try Direct Read
            if (!blockedDirectPaths.contains(path)) {
                try {
                    val content = File(path).readText().trim()
                    load = parseGpuLoad(content, path)
                    if (load != null) {
                        workingGpuLoadPath = path
                        break
                    }
                } catch (_: Exception) {
                    blockedDirectPaths.add(path)
                }
            }
            
            // 2. Try Shizuku Fallback
            if (load == null && !blockedShellPaths.contains(path)) {
                val content = readShellCommand("cat $path", 400)?.trim()
                load = parseGpuLoad(content, path)
                if (load != null) {
                    workingGpuLoadPath = path
                }
            }
        }
        
        var freq: Int? = null
        val freqPathsToTry = if (workingGpuFreqPath != null) listOf(workingGpuFreqPath!!) + freqPaths else freqPaths
        
        for (path in freqPathsToTry) {
            if (freq != null) break
            
            // 1. Direct
            if (!blockedDirectPaths.contains(path)) {
                try {
                    val content = File(path).readText().trim()
                    freq = parseGpuFreq(content)
                    if (freq != null && freq > 0) {
                        workingGpuFreqPath = path
                        break
                    }
                } catch (_: Exception) {
                    blockedDirectPaths.add(path)
                }
            }
            
            // 2. Shell
            if (freq == null && !blockedShellPaths.contains(path)) {
                val content = readShellCommand("cat $path", 400)?.trim()
                freq = parseGpuFreq(content)
                if (freq != null && freq > 0) {
                    workingGpuFreqPath = path
                }
            }
        }

        // Use cached static info to avoid 4+ Shizuku calls every cycle
        if (cachedGpuStaticInfo == null) {
            var model = readShellCommand("getprop ro.product.board")?.trim()
            if (model.isNullOrEmpty()) model = readShellCommand("getprop ro.board.platform")?.trim()
            
            var renderer = readShellCommand("getprop ro.hardware.egl")?.trim()
            if (renderer.isNullOrEmpty() || renderer == "null") {
                renderer = readShellCommand("getprop vendor.display.gpu_level")?.trim()
            }
            
            val apiVersion = readShellCommand("getprop ro.opengles.version")?.trim()
            val vendor = if (renderer?.lowercase()?.contains("adreno") == true) "Qualcomm" else null
            
            cachedGpuStaticInfo = GpuStaticInfo(
                renderer = renderer ?: "Qualcomm Adreno",
                model = model,
                vendor = vendor,
                apiVersion = apiVersion
            )
        }

        // Try to get GPU temperature
        var gpuTemp: Float? = null
        val tempPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/temp",
            "/sys/class/thermal/thermal_zone12/temp",
            "/sys/class/thermal/thermal_zone20/temp",
            "/sys/class/thermal/thermal_zone22/temp"
        )
        val tempPathsToTry = if (workingGpuTempPath != null) listOf(workingGpuTempPath!!) + tempPaths else tempPaths
        
        for (path in tempPathsToTry) {
            if (gpuTemp != null) break

            // 1. Direct
            if (!blockedDirectPaths.contains(path)) {
                try {
                    val content = File(path).readText().trim()
                    val t = content.toFloatOrNull()
                    if (t != null) {
                        val temp = if (t > 1000) t / 1000f else t
                        if (temp in -20f..120f) {
                            gpuTemp = temp
                            workingGpuTempPath = path
                            break
                        }
                    }
                } catch (_: Exception) {
                    blockedDirectPaths.add(path)
                }
            }

            // 2. Shell
            if (gpuTemp == null && !blockedShellPaths.contains(path)) {
                val content = readShellCommand("cat $path", 400)?.trim()
                val t = content?.toFloatOrNull()
                if (t != null) {
                    val temp = if (t > 1000) t / 1000f else t
                    if (temp in -20f..120f) {
                        gpuTemp = temp
                        workingGpuTempPath = path
                    }
                }
            }
        }

        val static = cachedGpuStaticInfo!!
        GpuInfo(
            loadPercent = load,
            frequencyMhz = freq,
            renderer = static.renderer,
            model = static.model,
            vendor = static.vendor,
            apiVersion = static.apiVersion,
            temperatureCelsius = gpuTemp,
            available = true
        )
    }

    suspend fun getRunningProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()
        // Use top because it gives %CPU easily. -n 1 for single iteration. -b for batch mode.
        val output = readShellCommand("top -b -n 1") 
        // Note: removed -s 9 as it's not supported on some toybox versions and causes failure
        
        if (!output.isNullOrEmpty()) {
            val lines = output.split("\n")
            var headerFound = false
            for (line in lines) {
                if (line.contains("PID") && line.contains("USER") && (line.contains("RES") || line.contains("RSS"))) {
                    headerFound = true
                    continue
                }
                if (headerFound && line.trim().isNotEmpty()) {
                    try {
                        val parts = line.trim().split(" +".toRegex())
                        if (parts.size >= 9) {
                            // Toybox top output varies, let's try common positions
                            // PID USER PR NI VIRT RES SHR S %CPU %MEM TIME+ ARGS
                            val pid = parts[0].toIntOrNull() ?: continue
                            val user = parts[1]
                            val resStr = parts[5] // Usually RES
                            val cpu = parts[8].replace(",", ".").toFloatOrNull() ?: 0f
                            val name = parts.last()
                            
                            val resKb = when {
                                resStr.endsWith("G") -> (resStr.dropLast(1).toDouble() * 1024 * 1024).toLong()
                                resStr.endsWith("M") -> (resStr.dropLast(1).toDouble() * 1024).toLong()
                                else -> resStr.toLongOrNull() ?: 0L
                            }

                            processes.add(ProcessInfo(
                                pid = pid,
                                name = name,
                                cpuUsage = cpu,
                                ramUsageMb = resKb / 1024f,
                                user = user,
                                command = name
                            ))
                        }
                    } catch (_: Exception) {}
                }
            }
        }
        processes.sortedByDescending { it.ramUsageMb }
    }

    suspend fun getStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        // 1. Get total used space from StatFs as our baseline
        val storageInfo = getStorageInfo()
        val totalUsedGb = storageInfo.usedGb
        
        // 2. Get App Data via StorageStatsManager (API 26+) implementation
        // This is much more accurate than dumpsys diskstats if permission is granted
        var appsKb = 0L
        var systemKb = 0L
        val hasUsageStats = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) hasUsageStatsPermission() else false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasUsageStats) {
            try {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val user = Process.myUserHandle()
                val uuid = StorageManager.UUID_DEFAULT
                
                val packageManager = context.packageManager
                val installedPackages = packageManager.getInstalledPackages(0)
                
                for (pkg in installedPackages) {
                    try {
                        val stats = storageStatsManager.queryStatsForPackage(uuid, pkg.packageName, user)
                        appsKb += (stats.appBytes + stats.dataBytes) / 1024
                        // Note: Cache is usually separate, often cleaned, but we can count it if we want
                        // appsKb += stats.cacheBytes / 1024 
                    } catch (_: Exception) {
                        // Some system packages might fail
                    }
                }
                
                // System size via StorageStatsManager if possible?
                // StorageStatsManager doesn't give "System" directly easily, usually total - user_data
            } catch (_: Exception) {
                // Fallback if anything goes wrong
                appsKb = 0L
            }
        }
        
        // Fallback to diskstats if usage stats permission defined or failed, OR if we want to cross-verify
        if (appsKb == 0L) {
             val diskstats = readShellCommand("dumpsys diskstats", 10000)
             diskstats?.split("\n")?.forEach { line ->
                val l = line.lowercase()
                if (l.contains("package-data:") || l.contains("app-data:") || l.contains("cache-data:")) {
                    appsKb += parseSizeKb(line)
                }
                if (l.contains("system:")) {
                    systemKb = parseSizeKb(line)
                }
            }
        }

        // 3. Get Media/Docs via direct 'du' commands on standard folders
        // This is much more reliable than parsing summaries
        val externalStorage = Environment.getExternalStorageDirectory().path
        val mediaFolders = mapOf(
            "images" to listOf("$externalStorage/DCIM", "$externalStorage/Pictures"),
            "videos" to listOf("$externalStorage/Movies", "$externalStorage/DCIM/Camera"),
            "audio" to listOf("$externalStorage/Music", "$externalStorage/Podcasts", "$externalStorage/Alarms", "$externalStorage/Notifications", "$externalStorage/Ringtones"),
            "documents" to listOf("$externalStorage/Download", "$externalStorage/Documents")
        )

        val results = mutableMapOf<String, Long>()
        mediaFolders.forEach { (category, paths) ->
            var categoryKb = 0L
            paths.forEach { path ->
                // du -sk returns size in KB. -L follows symlinks.
                val output = readShellCommand("du -sk $path", 800)
                if (!output.isNullOrEmpty()) {
                    // Output format: "123456\t/path/to/dir"
                    val size = output.trim().split(whitespaceRegex)[0].toLongOrNull() ?: 0L
                    categoryKb += size
                }
            }
            results[category] = categoryKb
        }

        val appG = appsKb / (1024f * 1024f)
        val imgG = (results["images"] ?: 0L) / (1024f * 1024f)
        val vidG = (results["videos"] ?: 0L) / (1024f * 1024f)
        val audG = (results["audio"] ?: 0L) / (1024f * 1024f)
        val docG = (results["documents"] ?: 0L) / (1024f * 1024f)
        
        // 4. Calculate System & Other
        // System = Total Used - (Apps + Media + Docs). 
        // This implicitly includes Firmware, System Data, and "Other" unaccounted files.
        val accountedGb = appG + imgG + vidG + audG + docG
        var sysG = (totalUsedGb - accountedGb).coerceAtLeast(0f)
        
        // Sanity check: If system seems incorrectly small (e.g. < 5GB on a large device), 
        // it might be because Apps over-counted (shared data).
        // But with StorageStatsManager, Total Used includes Firmware, so System should be ~20-40GB.
        
        // Separate "Other" if we want, but usually "System" covers the unknown overhead.
        // Let's stick "System" as the remainder to ensure the chart sums to 100% of used.
        val otherG = 0f 

        StorageBreakdown(
            appsGb = appG,
            imagesGb = imgG,
            videosGb = vidG,
            audioGb = audG,
            documentsGb = docG,
            systemGb = sysG,
            otherGb = otherG
        )
    }

    private fun parseGpuLoad(content: String?, path: String): Int? {
        if (content.isNullOrEmpty()) return null
        return try {
            if (path.endsWith("gpubusy")) {
                val match = gpuBusyRegex.find(content)
                if (match != null) {
                    val busy = match.groupValues[1].toLong()
                    val total = match.groupValues[2].toLong()
                    if (total > 0) (busy * 100 / total).toInt() else 0
                } else null
            } else {
                content.replace("%", "").split(whitespaceRegex)[0].toIntOrNull()
            }
        } catch (_: Exception) { null }
    }

    private fun parseGpuFreq(content: String?): Int? {
        if (content.isNullOrEmpty()) return null
        return try {
            val raw = content.split(whitespaceRegex)[0].toLongOrNull() ?: 0L
            if (raw > 2000000) (raw / 1000000).toInt()
            else if (raw > 2000) (raw / 1000).toInt()
            else raw.toInt()
        } catch (_: Exception) { null }
    }

    private fun parseSizeKb(line: String): Long {
        return try {
            val match = sizeRegex.find(line) ?: return 0L
            val value = match.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = match.groupValues[2].uppercase()
            
            when {
                unit.startsWith("G") -> (value * 1024 * 1024).toLong()
                unit.startsWith("M") -> (value * 1024).toLong()
                unit.startsWith("K") -> value.toLong()
                unit.startsWith("B") -> (value / 1024).toLong()
                else -> value.toLong() 
            }
        } catch (_: Exception) {
            0L
        }
    }


    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}
