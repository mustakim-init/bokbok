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
import kotlinx.coroutines.*
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.os.storage.StorageManager
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.StringReader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject


import javax.inject.Singleton

@Singleton
class DeviceMonitorRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configDao: com.mustakim.bokbok.data.local.dao.SystemConfigDao,
    private val keepShell: com.mustakim.bokbok.data.shell.KeepShell,
    private val daemonManager: com.mustakim.bokbok.data.shell.DaemonManager
) {

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
    private var cachedSoCModel: String? = null
    private var cachedMaxPowerLevel: Int? = null
    private var cachedCpuMaxFreqs = mutableMapOf<Int, Long>()
    
    // Configuration cache to avoid redundant DB writes
    private val persistedValues = mutableMapOf<String, String?>()
    private var isShizukuAuthorizedCached: Boolean? = null

    // Persistent storage keys
    private object Keys {
        const val GPU_LOAD_PATH = "gpu_load_path"
        const val GPU_FREQ_PATH = "gpu_freq_path"
        const val GPU_TEMP_PATH = "gpu_temp_path"
        const val CPU_TEMP_PATH = "cpu_temp_path"
        const val SOC_MODEL = "soc_model"
        const val CPU_MAX_FREQS = "cpu_max_freqs"
        const val GPU_RENDERER = "gpu_renderer"
        const val GPU_MODEL = "gpu_model"
        const val GPU_VENDOR = "gpu_vendor"
        const val BATTERY_DESIGN_CAP = "battery_design_cap"
        const val BATTERY_MAX_CAP = "battery_max_cap"
    }

    private var hasLoadedPersistentData = false

    private suspend fun ensurePersistentDataLoaded() {
        if (hasLoadedPersistentData) return
        coroutineScope {
            try {
                // Initialize cache and working paths
                persistedValues[Keys.GPU_LOAD_PATH] = configDao.getString(Keys.GPU_LOAD_PATH)
                persistedValues[Keys.GPU_FREQ_PATH] = configDao.getString(Keys.GPU_FREQ_PATH)
                persistedValues[Keys.GPU_TEMP_PATH] = configDao.getString(Keys.GPU_TEMP_PATH)
                persistedValues[Keys.CPU_TEMP_PATH] = configDao.getString(Keys.CPU_TEMP_PATH)
                persistedValues[Keys.SOC_MODEL] = configDao.getString(Keys.SOC_MODEL)
                persistedValues[Keys.GPU_RENDERER] = configDao.getString(Keys.GPU_RENDERER)
                persistedValues[Keys.GPU_MODEL] = configDao.getString(Keys.GPU_MODEL)
                persistedValues[Keys.GPU_VENDOR] = configDao.getString(Keys.GPU_VENDOR)
                persistedValues[Keys.CPU_MAX_FREQS] = configDao.getString(Keys.CPU_MAX_FREQS)
                persistedValues[Keys.BATTERY_DESIGN_CAP] = configDao.getString(Keys.BATTERY_DESIGN_CAP)
                persistedValues[Keys.BATTERY_MAX_CAP] = configDao.getString(Keys.BATTERY_MAX_CAP)
                
                workingGpuLoadPath = persistedValues[Keys.GPU_LOAD_PATH]
                workingGpuFreqPath = persistedValues[Keys.GPU_FREQ_PATH]
                workingGpuTempPath = persistedValues[Keys.GPU_TEMP_PATH]
                workingCpuTempPath = persistedValues[Keys.CPU_TEMP_PATH]
                cachedSoCModel = persistedValues[Keys.SOC_MODEL]
                
                android.util.Log.d("BB-PERF", "Loaded config - GPU Load: $workingGpuLoadPath, CPU Temp: $workingCpuTempPath, SoC: $cachedSoCModel")

                val renderer = persistedValues[Keys.GPU_RENDERER]
                if (renderer != null) {
                    cachedGpuStaticInfo = GpuStaticInfo(
                        renderer = renderer,
                        model = persistedValues[Keys.GPU_MODEL],
                        vendor = persistedValues[Keys.GPU_VENDOR],
                        apiVersion = null
                    )
                }

                val maxFreqsStr = persistedValues[Keys.CPU_MAX_FREQS]
                if (!maxFreqsStr.isNullOrEmpty()) {
                    maxFreqsStr.split(",").forEach {
                        val parts = it.split(":")
                        if (parts.size == 2) {
                            val core = parts[0].toIntOrNull()
                            val freq = parts[1].toLongOrNull()
                            if (core != null && freq != null) {
                                cachedCpuMaxFreqs[core] = freq
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("BB-PERF", "Error loading persistent config", e)
            }
        }
        hasLoadedPersistentData = true
    }

    private fun persist(key: String, value: String?) {
        if (value == null) return
        if (persistedValues[key] == value) return // Avoid redundant writes
        
        android.util.Log.d("BB-PERF", "Saving config: $key -> $value")
        persistedValues[key] = value
        // Fire and forget persistence
        CoroutineScope(Dispatchers.IO).launch {
            configDao.putString(key, value)
        }
    }

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

    suspend fun getCpuInfo(): CpuInfo = coroutineScope {
        ensurePersistentDataLoaded()
        val coreCount = Runtime.getRuntime().availableProcessors()
        
        // We immediately fall back to the native batch queries.
        // --- FALLBACK: Batch all frequent shell queries into ONE Shizuku process ---
        val batchResults = readShellCommands(listOf(
            "cat /proc/stat",
            "cat /sys/devices/system/cpu/cpu*/online",
            "cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_cur_freq",
            "getprop sys.vivo.project.ramsize",
            "for f in /sys/class/thermal/thermal_zone*; do echo \$(cat \"${'$'}f\"/type):\$(cat \"${'$'}f\"/temp); done"
        ), 1500)

        val statContent = batchResults.getOrNull(0)
        val onlineContent = batchResults.getOrNull(1)
        val freqContent = batchResults.getOrNull(2)
        val vivoRamSize = batchResults.getOrNull(3)?.trim()?.toLongOrNull()
        val thermBatch = batchResults.getOrNull(4)

        val loadsAsync = async { 
            if (isProcStatRestricted != true) {
                try {
                    val direct = File("/proc/stat").readText()
                    isProcStatRestricted = false
                    parseCpuStat(direct)
                } catch (_: Exception) {
                    isProcStatRestricted = true
                    parseCpuStat(statContent)
                }
            } else {
                parseCpuStat(statContent)
            }
        }
        
        val batchFrequenciesAsync = async { parseFreqsBatch(freqContent, coreCount) }
        val batchOnlineAsync = async { parseOnlineBatch(onlineContent, coreCount) }
        val batchMaxFreqsAsync = async { readMaxFreqsBatch(coreCount) }
        val socAsync = async { 
            if (cachedSoCModel != null) cachedSoCModel else {
                getSoCModel().also { 
                    cachedSoCModel = it
                    persist(Keys.SOC_MODEL, it)
                }
            }
        }
        val tempAsync = async { 
            if (workingCpuTempPath != null) {
                readTemp(workingCpuTempPath!!) 
            } else {
                parseThermalBatch(thermBatch)
            }
        }
        val ramAsync = async {
            if (vivoRamSize != null && vivoRamSize > 0) {
                 val ram = getRamInfo()
                 ram.copy(totalMb = vivoRamSize * 1024) 
            } else {
                 getRamInfo()
            }
        }

        // Await results
        val loads = loadsAsync.await()
        val batchFrequencies = batchFrequenciesAsync.await()
        val batchOnline = batchOnlineAsync.await()
        val batchMaxFreqs = batchMaxFreqsAsync.await()
        
        val frequencies = mutableListOf<Long>()
        val maxFrequencies = mutableListOf<Long>()
        val onlineStatus = mutableListOf<Boolean>()

        for (i in 0 until coreCount) {
            frequencies.add(batchFrequencies[i] ?: readFreq(i))
            onlineStatus.add(batchOnline[i] ?: isCoreOnline(i))
            maxFrequencies.add(batchMaxFreqs[i] ?: readMaxFreq(i))
        }
        
        val soc = socAsync.await()
        val temp = tempAsync.await()
        val ram = ramAsync.await()

        // Calculate Clusters (e.g. "1x 3.30GHz, 4x 2.61GHz")
        val clusters = maxFrequencies.groupingBy { it }.eachCount()
            .toSortedMap(compareByDescending { it })
            .entries.joinToString(", ") { (freq, count) ->
                val freqGhz = "%.2f".format(freq / 1000000f)
                "${count}x ${freqGhz}GHz"
            }
        
        CpuInfo(
            loadPercent = loads.first,
            coreCount = coreCount,
            coreLoads = loads.second,
            frequencies = frequencies,
            onlineStatus = onlineStatus,
            socName = soc,
            temperatureCelsius = temp,
            architecture = clusters
        )
    }

    private suspend fun readMaxFreq(core: Int): Long {
         cachedCpuMaxFreqs[core]?.let { return it }
         val path = "/sys/devices/system/cpu/cpu$core/cpufreq/cpuinfo_max_freq"
         // Try direct
         val freq = try {
             val content = File(path).readText().trim()
             content.toLongOrNull() ?: 0L
         } catch (_: Exception) {
             // Try shell
             readShellCommand("cat $path", 300)?.trim()?.toLongOrNull() ?: 0L
         }
         if (freq > 0) cachedCpuMaxFreqs[core] = freq
         return freq
    }

    private fun parseCpuStat(statContent: String?): Pair<Float, List<Float>> {
        if (statContent.isNullOrEmpty()) return Pair(0f, emptyList())
        var totalLoad = 0f
        val coreLoads = mutableListOf<Float>()
        
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
        return Pair(totalLoad.coerceIn(0f, 100f), coreLoads)
    }

    private fun parseFreqsBatch(content: String?, coreCount: Int): Map<Int, Long> {
        val result = mutableMapOf<Int, Long>()
        if (!content.isNullOrEmpty()) {
            content.lines().filter { it.isNotBlank() }.forEachIndexed { index, s ->
                if (index < coreCount) result[index] = s.trim().toLongOrNull() ?: 0L
            }
        }
        return result
    }

    private fun parseOnlineBatch(content: String?, coreCount: Int): Map<Int, Boolean> {
        val result = mutableMapOf<Int, Boolean>()
        if (!content.isNullOrEmpty()) {
             content.lines().filter { it.isNotBlank() }.forEachIndexed { index, s ->
                if (index < coreCount) result[index] = s.trim() == "1"
            }
        }
        return result
    }

    private fun parseThermalBatch(content: String?): Float? {
        if (content.isNullOrEmpty()) return null
        
        val cpuZones = listOf("cpu-0-0-usr", "cpu-1-0-usr", "cpu-0-usr", "cpu-1-usr", "soc", "package", "processor", "tsens_tz_sensor0")
        var bestTemp: Float? = null
        var bestPath: String? = null
        
        try {
            content.lines().forEachIndexed { index, line ->
                if (line.isBlank() || !line.contains(":")) return@forEachIndexed
                val parts = line.split(":")
                if (parts.size != 2) return@forEachIndexed
                
                val type = parts[0].lowercase()
                val tempRaw = parts[1].toFloatOrNull() ?: return@forEachIndexed
                val temp = if (tempRaw > 1000) tempRaw / 1000f else tempRaw
                
                if (temp !in 10f..110f) return@forEachIndexed
                
                // Priority matching
                for (target in cpuZones) {
                    if (type.contains(target)) {
                        bestTemp = temp
                        bestPath = "/sys/class/thermal/thermal_zone$index/temp"
                        break
                    }
                }
            }
        } catch (_: Exception) {}
        
        if (bestPath != null) {
            workingCpuTempPath = bestPath
            persist(Keys.CPU_TEMP_PATH, bestPath)
        }
        
        return bestTemp
    }

    private suspend fun readTemp(path: String): Float? {
        try {
            val content = if (!blockedDirectPaths.contains(path)) {
                try { File(path).readText().trim() } catch (_: Exception) { 
                    blockedDirectPaths.add(path)
                    readShellCommand("cat $path", 300)?.trim()
                }
            } else {
                readShellCommand("cat $path", 300)?.trim()
            }
            
            val t = content?.toFloatOrNull()
            if (t != null) {
                val temp = if (t > 1000) t / 1000f else t
                if (temp in 10f..110f) return temp
            }
        } catch (_: Exception) {}
        return null
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
    
    private suspend fun readMaxFreqsBatch(coreCount: Int): Map<Int, Long> {
        if (cachedCpuMaxFreqs.size >= coreCount) return cachedCpuMaxFreqs
        val result = mutableMapOf<Int, Long>()
        val output = readShellCommand("cat /sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq", 500)
        if (!output.isNullOrEmpty()) {
            output.lines().filter { it.isNotBlank() }.forEachIndexed { index, s ->
                val freq = s.trim().toLongOrNull() ?: 0L
                if (index < coreCount) {
                    result[index] = freq
                    if (freq > 0) cachedCpuMaxFreqs[index] = freq
                }
            }
        }
        if (cachedCpuMaxFreqs.isNotEmpty()) {
            val encoded = cachedCpuMaxFreqs.entries.joinToString(",") { "${it.key}:${it.value}" }
            persist(Keys.CPU_MAX_FREQS, encoded)
        }
        return result
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

        // Try shell - Note: Usually batch will have caught this, but fallback just in case
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

        // 🚀 Use the persistent shell bridge
        val result = keepShell.doCmd(command)
        if (result.startsWith("error:")) null else result
    }

    private suspend fun readShellCommands(commands: List<String>, timeoutMs: Long = 2000): List<String?> = withContext(Dispatchers.IO) {
        if (!checkShizukuPermission()) return@withContext List(commands.size) { null }
        
        val sep = "--BATCH-SEP--"
        val bb = "/data/local/tmp/busybox"
        // Combine commands: cmd1 2>&1; echo --SEP--; cmd2 2>&1; echo --SEP--; ...
        val bigCommand = commands.joinToString(separator = "; $bb echo \"$sep\"; ") { "$it 2>&1" }
        
        try {
            // 🚀 Use the persistent shell bridge
            val fullResult = keepShell.doCmd(bigCommand)
            if (fullResult.startsWith("error:")) return@withContext List(commands.size) { null }

            val parts = fullResult.split(sep)
            commands.indices.map { i ->
                val result = parts.getOrNull(i)?.trim()
                
                // Auto-block failing paths
                val cmd = commands[i]
                if (cmd.startsWith("cat ")) {
                    val path = cmd.subSequence(4, cmd.length).toString().split(" ")[0] // Handle "cat path 2>&1"
                    if (result != null && (result.contains("denied", true) || 
                        result.contains("inaccessible", true) || 
                        result.contains("Not found", true))) {
                        android.util.Log.w("BB-PERF", "Blocking path: $path (Reason: $result)")
                        blockedShellPaths.add(path)
                    }
                }
                result
            }
        } catch (_: Exception) {
            List(commands.size) { null }
        }
    }

    private fun checkShizukuPermission(): Boolean {
        if (isShizukuAuthorizedCached == true) return true
        
        try {
             if (Shizuku.isPreV11() || Shizuku.getVersion() < 11) return false
             val granted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
             
             // One-time load from persistence if not already authorized in session
             if (isShizukuAuthorizedCached == null) {
                 MainScope().launch(Dispatchers.IO) {
                     val cached = configDao.getString("shizuku_authorized")
                     if (cached == "true") isShizukuAuthorizedCached = true
                 }
             }

             if (granted) {
                 if (isShizukuAuthorizedCached != true) {
                     isShizukuAuthorizedCached = true
                     MainScope().launch(Dispatchers.IO) {
                         configDao.putString("shizuku_authorized", "true")
                     }
                 }
                 return true
             }
             return false
        } catch (_: Throwable) {
            return false
        }
    }



    suspend fun getRamInfo(): RamInfo = withContext(Dispatchers.IO) {
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

        RamInfo(
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
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentMicro = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentMa = if (currentMicro > 10000 || currentMicro < -10000) currentMicro / 1000 else currentMicro
        val powerW = (voltage * (currentMa.toFloat() / 1000f))

        // --- COMPREHENSIVE HEALTH % LOGIC ---
        var healthPercent: Int? = null

        // 1. Try Capacity Ratio (Most accurate if available)
        var designCap = persistedValues[Keys.BATTERY_DESIGN_CAP]?.toIntOrNull() ?: getBatteryDesignCapacity()?.also {
            persist(Keys.BATTERY_DESIGN_CAP, it.toString())
        }
        var maxCap = persistedValues[Keys.BATTERY_MAX_CAP]?.toIntOrNull() ?: getBatteryMaxCapacity()?.also {
            persist(Keys.BATTERY_MAX_CAP, it.toString())
        }
        if (designCap != null && maxCap != null && designCap > 0) {
            healthPercent = (maxCap * 100 / designCap).coerceAtMost(100)
        }
        
        // 2. Try Direct Sysfs Health Percentage
        if (healthPercent == null || healthPercent <= 0) {
            healthPercent = getBatteryHealthDirect()
        }
        
        // 3. Try Dumpsys Battery Parsing (Essential for ROMs like FuntouchOS/Vivo)
        if (healthPercent == null || healthPercent <= 0) {
            healthPercent = getBatteryHealthFromDumpsys()
        }

        // 4. Try Manufacturer Specific System Properties
        if (healthPercent == null || healthPercent <= 0) {
            healthPercent = getBatteryHealthFromSystemProps()
        }
        
        // 5. THE "OTHER WAY": Calculated from Estimated vs Profile Design
        // If system blocks reading sysfs, it often still tracks estimated capacity in batterystats.
        if (healthPercent == null || healthPercent <= 0) {
            val estimatedCap = getEstimatedCapacity()
            if (estimatedCap != null && estimatedCap > 0) {
                 // Try getting design capacity from PowerProfile (Internal API)
                 if (designCap == null || designCap <= 0) {
                     designCap = getDesignCapacityFromPowerProfile()
                 }
                 
                 // If we have both, calculate
                 if (designCap != null && designCap > 0) {
                     healthPercent = (estimatedCap * 100 / designCap).coerceAtMost(100)
                     maxCap = estimatedCap // Update maxCap so UI shows the estimated value
                 }
            }
        }
        
        // 6. Vivo Specific Hard-coded Fallbacks for known models like iQOO Z9 Turbo
        if (healthPercent == null || healthPercent <= 0 || designCap == null || designCap <= 0) {
            val marketName = readShellCommand("getprop ro.vivo.market.name")?.lowercase() ?: ""
            if (marketName.contains("z9 turbo")) {
                designCap = 6000 // Marketing value is often 6000 or 6400, user said 6400
                if (marketName.contains("lasting")) designCap = 6400
                
                if (maxCap == null || maxCap <= 0) maxCap = designCap
                if (healthPercent == null) healthPercent = 100
            }
        }

        // Final validation
        if (healthPercent != null && (healthPercent <= 0 || healthPercent > 100)) {
            healthPercent = null
        }
        
        val deepSleep = getDeepSleepPercent()

        return BatteryInfo(
            level = batteryPct,
            temperatureCelsius = temp,
            isCharging = isCharging,
            currentMa = currentMa,
            voltageV = voltage,
            health = health,
            healthPercent = healthPercent,
            powerW = powerW,
            designCapacityMah = designCap,
            maxCapacityMah = maxCap,
            deepSleepPercent = deepSleep
        )
    }

    suspend fun getStorageInfo(): StorageInfo = withContext(Dispatchers.IO) {
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
        
        StorageInfo(
            totalGb = totalGb,
            usedGb = usedGb,
            availableGb = availableGb,
        usagePercent = percent
        )
    }

    suspend fun getGpuInfo(): GpuInfo = coroutineScope {
        ensurePersistentDataLoaded()
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

        val tempPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/temp",
            "/sys/class/kgsl/kgsl-3d0/temperature",
            "/sys/class/thermal/thermal_zone12/temp",
            "/sys/class/thermal/thermal_zone20/temp",
            "/sys/class/thermal/thermal_zone22/temp"
        )

        // Batch all frequent GPU shell queries if paths are already discovered
        if (workingGpuLoadPath != null || workingGpuFreqPath != null || workingGpuTempPath != null) {
            val cmdList = mutableListOf<String>()
            if (workingGpuLoadPath != null) cmdList.add("cat $workingGpuLoadPath")
            if (workingGpuFreqPath != null) cmdList.add("cat $workingGpuFreqPath")
            if (workingGpuTempPath != null) cmdList.add("cat $workingGpuTempPath")
            
            val batchResults = readShellCommands(cmdList, 800)
            var batchIdx = 0
            
            val loadAsync = async {
                val content = if (workingGpuLoadPath != null) batchResults.getOrNull(batchIdx++) else null
                parseGpuLoad(content, workingGpuLoadPath ?: "") ?: run {
                    // Discovery fallback if cached path failed
                    discoverGpuPath(loadPaths, ::parseGpuLoad, Keys.GPU_LOAD_PATH) { workingGpuLoadPath = it }
                }
            }
            
            val freqAsync = async {
                val content = if (workingGpuFreqPath != null) batchResults.getOrNull(batchIdx++) else null
                parseGpuFreq(content)?.takeIf { it > 0 } ?: run {
                    discoverGpuPath(freqPaths, { c, _ -> parseGpuFreq(c) }, Keys.GPU_FREQ_PATH) { workingGpuFreqPath = it }
                }
            }
            
            val tempAsync = async {
                val content = if (workingGpuTempPath != null) batchResults.getOrNull(batchIdx++) else null
                val t = content?.toFloatOrNull()
                if (t != null) {
                    val temp = if (t > 1000) t / 1000f else t
                    if (temp in -20f..120f) return@async temp
                }
                discoverGpuTempPath(tempPaths)
            }
            
            val staticInfoAsync = async { 
                if (cachedGpuStaticInfo != null) cachedGpuStaticInfo else discoverGpuStaticInfo()
            }

            val powerLevelAsync = async { readShellCommand("cat /sys/class/kgsl/kgsl-3d0/cur_pwrlevel")?.trim()?.toIntOrNull() }
            val maxPowerLevelAsync = async {
                if (cachedMaxPowerLevel != null) cachedMaxPowerLevel else {
                    readShellCommand("cat /sys/class/kgsl/kgsl-3d0/max_pwrlevel")?.trim()?.toIntOrNull().also { cachedMaxPowerLevel = it }
                }
            }
            
            val si = staticInfoAsync.await()
            return@coroutineScope GpuInfo(
                loadPercent = loadAsync.await() ?: 0,
                frequencyMhz = freqAsync.await() ?: 0,
                temperatureCelsius = tempAsync.await(),
                renderer = si?.renderer ?: "Unknown",
                model = si?.model,
                vendor = si?.vendor,
                apiVersion = si?.apiVersion,
                powerLevel = powerLevelAsync.await(),
                maxPowerLevel = maxPowerLevelAsync.await(),
                available = true
            )
        }

        // Full discovery mode (only happens once or if paths break)
        val loadAsync = async { discoverGpuPath(loadPaths, ::parseGpuLoad, Keys.GPU_LOAD_PATH) { workingGpuLoadPath = it } }
        val freqAsync = async { discoverGpuPath(freqPaths, { c, _ -> parseGpuFreq(c) }, Keys.GPU_FREQ_PATH) { workingGpuFreqPath = it } }
        val tempAsync = async { discoverGpuTempPath(tempPaths) }
        val staticInfoAsync = async { discoverGpuStaticInfo() }
        val powerLevelAsync = async { readShellCommand("cat /sys/class/kgsl/kgsl-3d0/cur_pwrlevel")?.trim()?.toIntOrNull() }
        val maxPowerLevelAsync = async {
            if (cachedMaxPowerLevel != null) cachedMaxPowerLevel else {
                readShellCommand("cat /sys/class/kgsl/kgsl-3d0/max_pwrlevel")?.trim()?.toIntOrNull().also { cachedMaxPowerLevel = it }
            }
        }
        val gpuTemp = tempAsync.await()
        val staticResult = staticInfoAsync.await()
        val pwrLevel = powerLevelAsync.await()
        val maxPwrLevel = maxPowerLevelAsync.await()

        GpuInfo(
            loadPercent = loadAsync.await() ?: 0,
            frequencyMhz = freqAsync.await() ?: 0,
            renderer = staticResult?.renderer ?: "Unknown",
            model = staticResult?.model,
            vendor = staticResult?.vendor,
            apiVersion = staticResult?.apiVersion,
            temperatureCelsius = gpuTemp,
            powerLevel = pwrLevel,
            maxPowerLevel = maxPwrLevel,
            available = true
        )
    }

    private var workingCpuTempPath: String? = null

    private suspend fun discoverGpuPath(paths: List<String>, parser: (String?, String) -> Int?, key: String, onFound: (String) -> Unit): Int? {
        for (path in paths) {
            // Try direct
            if (!blockedDirectPaths.contains(path)) {
                try {
                    val content = File(path).readText().trim()
                    val result = parser(content, path)
                    if (result != null) {
                        onFound(path); persist(key, path); return result
                    }
                } catch (_: Exception) { blockedDirectPaths.add(path) }
            }
            // Try shell
            if (!blockedShellPaths.contains(path)) {
                val content = readShellCommand("cat $path", 400)
                val result = parser(content, path)
                if (result != null) {
                    onFound(path); persist(key, path); return result
                }
            }
        }
        return null
    }

    private suspend fun discoverGpuTempPath(paths: List<String>): Float? {
        for (path in paths) {
            val content = if (!blockedDirectPaths.contains(path)) {
                try { File(path).readText().trim() } catch (_: Exception) {
                    blockedDirectPaths.add(path)
                    readShellCommand("cat $path", 300)
                }
            } else {
                readShellCommand("cat $path", 300)
            }
            
            val t = content?.trim()?.toFloatOrNull()
            if (t != null) {
                val temp = if (t > 1000) t / 1000f else t
                if (temp in -20f..120f) {
                    workingGpuTempPath = path
                    persist(Keys.GPU_TEMP_PATH, path)
                    return temp
                }
            }
        }
        return null
    }

    private suspend fun discoverGpuStaticInfo(): GpuStaticInfo? {
        if (cachedGpuStaticInfo != null) return cachedGpuStaticInfo
        
        val batchOutput = readShellCommands(listOf(
            "getprop ro.product.board",
            "getprop ro.board.platform",
            "dumpsys SurfaceFlinger | grep \"GLES:\"",
            "getprop ro.hardware.egl",
            "getprop ro.opengles.version"
        ), 1500)
        
        val board = batchOutput.getOrNull(0)
        val platform = batchOutput.getOrNull(1)
        val sfOutput = batchOutput.getOrNull(2)
        val egl = batchOutput.getOrNull(3)
        val glVerStr = batchOutput.getOrNull(4)
        
        var model = if (!board.isNullOrEmpty()) board else platform
        var renderer: String? = null
        var apiVersion: String? = null
        
        if (!sfOutput.isNullOrEmpty() && sfOutput.contains("GLES:")) {
            val glesLine = sfOutput.substringAfter("GLES:").trim()
            val parts = glesLine.split(",").map { it.trim() }
            if (parts.size >= 2) renderer = parts[1].replace("(TM)", "").replace("(R)", "").trim()
            if (parts.size >= 3) apiVersion = parts[2].substringBefore(" V@").substringBefore(" (").trim()
        }
        
        if (renderer.isNullOrEmpty()) renderer = egl
        if (apiVersion.isNullOrEmpty()) {
            val glVersion = glVerStr?.toIntOrNull()
            if (glVersion != null) apiVersion = "OpenGL ES ${(glVersion shr 16) and 0xFF}.${glVersion and 0xFF}"
        }
        
        val vendor = if (renderer?.lowercase()?.contains("adreno") == true) "Qualcomm" 
                     else if (renderer?.lowercase()?.contains("mali") == true) "ARM" 
                     else null
                     
        val info = GpuStaticInfo(renderer ?: "Unknown", model, vendor, apiVersion)
        cachedGpuStaticInfo = info
        persist(Keys.GPU_RENDERER, info.renderer)
        persist(Keys.GPU_MODEL, info.model)
        persist(Keys.GPU_VENDOR, info.vendor)
        return info
    }

    private suspend fun getCpuTemperature(): Float? {
        val tempPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone1/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        )
        val pathsToTry = if (workingCpuTempPath != null) listOf(workingCpuTempPath!!) + tempPaths else tempPaths

        for (path in pathsToTry) {
            try {
                val content = File(path).readText().trim()
                val t = content.toFloatOrNull()
                if (t != null) {
                    val temp = if (t > 1000) t / 1000f else t
                    if (temp in 10f..100f) {
                        workingCpuTempPath = path
                        persist(Keys.CPU_TEMP_PATH, path)
                        return temp
                    }
                }
            } catch (_: Exception) {}
            
            val shellContent = readShellCommand("cat $path", 300)?.trim()
            val st = shellContent?.toFloatOrNull()
            if (st != null) {
                val temp = if (st > 1000) st / 1000f else st
                if (temp in 10f..100f) {
                    workingCpuTempPath = path
                    persist(Keys.CPU_TEMP_PATH, path)
                    return temp
                }
            }
        }
        return null
    }

    private suspend fun getSoCModel(): String? = withContext(Dispatchers.IO) {
        val model = readShellCommand("getprop ro.soc.model")?.trim() ?: ""
        val hardware = readShellCommand("getprop ro.hardware")?.trim() ?: ""
        val board = readShellCommand("getprop ro.board.platform")?.trim() ?: ""
        val vivoPlatform = readShellCommand("getprop ro.vivo.product.platform")?.trim() ?: ""
        
        // Map Snapdragon model numbers (SMxxxx) to marketing names - check MOST SPECIFIC first
        val modelMappings = mapOf(
            "SM8635" to "Snapdragon 8s Gen 3",
            "SM8650" to "Snapdragon 8 Gen 3",
            "SM8550" to "Snapdragon 8 Gen 2",
            "SM8475" to "Snapdragon 8+ Gen 1",
            "SM8450" to "Snapdragon 8 Gen 1",
            "SM8350" to "Snapdragon 888",
            "SM8250" to "Snapdragon 865",
            "SM7675" to "Snapdragon 7+ Gen 3",
            "SM7550" to "Snapdragon 7 Gen 3",
            "SM7475" to "Snapdragon 7+ Gen 2",
            "SM7450" to "Snapdragon 7 Gen 1",
            "SM6450" to "Snapdragon 6 Gen 1"
        )
        
        // Priority 1: Check exact model number (most accurate)
        for ((key, value) in modelMappings) {
            if (model.contains(key, ignoreCase = true)) return@withContext value
            if (vivoPlatform.contains(key, ignoreCase = true)) return@withContext value
        }
        
        // Map board platforms (codenames) if model not found
        val boardMappings = mapOf(
            "pineapple" to "Snapdragon 8 Gen 3",
            "kalama" to "Snapdragon 8 Gen 2",
            "taro" to "Snapdragon 8 Gen 1",
            "lahaina" to "Snapdragon 888",
            "kona" to "Snapdragon 865",
            "cliffs" to "Snapdragon 8s Gen 3"
        )
        
        // Priority 2: Check board platform codename
        for ((key, value) in boardMappings) {
            if (board.equals(key, ignoreCase = true)) return@withContext value
            if (vivoPlatform.equals(key, ignoreCase = true)) return@withContext value
        }
        
        // Priority 3: Return raw values if no mapping found
        if (model.isNotEmpty()) return@withContext model
        if (hardware.isNotEmpty()) return@withContext hardware
        if (board.isNotEmpty()) return@withContext board
        
        return@withContext null
    }

    private fun getDeepSleepPercent(): Int {
        val uptime = SystemClock.uptimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        if (elapsed == 0L) return 0
        val sleep = elapsed - uptime
        return (sleep * 100 / elapsed).toInt()
    }


    private suspend fun getBatteryDesignCapacity(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full_design",
            "/sys/class/power_supply/battery/design_capacity",
            "/sys/class/power_supply/bms/charge_full_design",
            "/sys/class/power_supply/bms/design_capacity",
            "/sys/class/power_supply/bq27xxx-battery/charge_full_design",
            "/sys/class/power_supply/battery/uevent", // Check uevent file
            "/sys/class/power_supply/bms/uevent"
        )
        for (path in paths) {
            var content: String? = null
            
            // Try direct read first
            if (!blockedDirectPaths.contains(path)) {
                try {
                     content = File(path).readText().trim()
                } catch (_: Exception) {
                     blockedDirectPaths.add(path)
                }
            }
            
            // Fallback to shell
            if (content == null) {
                 content = readShellCommand("cat $path", 300)?.trim()
            }
            
            if (content == null) continue
            
            // If it's a uevent file, parse it
            if (path.endsWith("uevent")) {
                val lines = content.split("\n")
                for (line in lines) {
                    if (line.contains("POWER_SUPPLY_CHARGE_FULL_DESIGN=")) {
                        val cap = line.substringAfter("=").toIntOrNull()
                        if (cap != null && cap > 0) return normalizeCapacity(cap)
                    }
                }
            } else {
                val cap = content.toIntOrNull()
                if (cap != null && cap > 0) return normalizeCapacity(cap)
            }
        }
        return null
    }

    private suspend fun getBatteryMaxCapacity(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/charge_full",
            "/sys/class/power_supply/battery/capacity_full",
            "/sys/class/power_supply/bms/charge_full",
            "/sys/class/power_supply/bms/capacity_full",
            "/sys/class/power_supply/bq27xxx-battery/charge_full",
            "/sys/class/power_supply/battery/uevent",
            "/sys/class/power_supply/bms/uevent"
        )
        for (path in paths) {
            var content: String? = null
            
            // Try direct read first
            if (!blockedDirectPaths.contains(path)) {
                try {
                     content = File(path).readText().trim()
                } catch (_: Exception) {
                     blockedDirectPaths.add(path)
                }
            }
            
            // Fallback to shell
            if (content == null) {
                 content = readShellCommand("cat $path", 300)?.trim()
            }
            
            if (content == null) continue
            
            if (path.endsWith("uevent")) {
                val lines = content.split("\n")
                for (line in lines) {
                    if (line.contains("POWER_SUPPLY_CHARGE_FULL=")) {
                        val cap = line.substringAfter("=").toIntOrNull()
                        if (cap != null && cap > 0) return normalizeCapacity(cap)
                    }
                }
            } else {
                val cap = content.toIntOrNull()
                if (cap != null && cap > 0) return normalizeCapacity(cap)
            }
        }
        return null
    }

    private fun normalizeCapacity(cap: Int): Int {
        // Assume uAh if > 100,000, else mAh
        return if (cap > 100000) cap / 1000 else cap
    }
    
    // Alternative: Get direct health percentage from sysfs
    private suspend fun getBatteryHealthDirect(): Int? {
        val paths = listOf(
            "/sys/class/power_supply/battery/battery_health",
            "/sys/class/power_supply/bms/battery_health",
            "/sys/class/power_supply/battery/capacity_level",
            "/sys/class/power_supply/battery/soh" // State of Health
        )
        for (path in paths) {
            var content: String? = null
            
            if (!blockedDirectPaths.contains(path)) {
                try {
                     content = File(path).readText().trim()
                } catch (_: Exception) {
                     blockedDirectPaths.add(path)
                }
            }
            
            if (content == null) {
                 content = readShellCommand("cat $path", 300)?.trim()
            }
            
            val pct = content?.toIntOrNull()
            if (pct != null && pct in 1..100) {
                return pct
            }
        }
        return null
    }

    private suspend fun getBatteryHealthFromDumpsys(): Int? {
        val output = readShellCommand("dumpsys battery", 500) ?: return null
        
        // 1. Look for custom health condition fields (Common in Vivo/OPPO)
        // Example: "  health condition: 98"
        val conditionLine = output.lines().find { it.contains("condition", ignoreCase = true) }
        if (conditionLine != null) {
            val pct = conditionLine.substringAfter(":").trim().filter { it.isDigit() }.toIntOrNull()
            if (pct != null && pct in 1..100) return pct
        }

        // 2. Look for "Charge Full" ratio in dumpsys if available
        // Example: "  Charge Full: 4500000" and "  Charge Full Design: 5000000"
        val fullLine = output.lines().find { it.contains("Charge Full", ignoreCase = true) && !it.contains("Design") }
        val designLine = output.lines().find { it.contains("Charge Full Design", ignoreCase = true) }
        if (fullLine != null && designLine != null) {
            val full = fullLine.substringAfter(":").trim().filter { it.isDigit() }.toLongOrNull()
            val design = designLine.substringAfter(":").trim().filter { it.isDigit() }.toLongOrNull()
            if (full != null && design != null && design > 0) {
                return (full * 100 / design).toInt().coerceIn(1, 100)
            }
        }

        return null
    }

    private suspend fun getBatteryHealthFromSystemProps(): Int? {
        val props = listOf(
            "persist.sys.vivo.battery_health",
            "persist.vendor.vivo.battery_health",
            "sys.battery.health",
            "persist.sys.battery.health"
        )
        for (prop in props) {
            val value = readShellCommand("getprop $prop")?.trim()?.toIntOrNull()
            if (value != null && value in 1..100) return value
        }
        return null
    }

    private suspend fun getEstimatedCapacity(): Int? {
        // Run dumpsys batterystats and look for "Estimated battery capacity: xxxx mAh"
        val output = readShellCommand("dumpsys batterystats | grep \"Estimated battery capacity\"", 2000) ?: return null
        
        // Expected format: "  Estimated battery capacity: 4500 mAh"
        val match = Regex("Estimated battery capacity:\\s*(\\d+)").find(output)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun getDesignCapacityFromPowerProfile(): Int? {
        return try {
            val powerProfileClass = Class.forName("com.android.internal.os.PowerProfile")
            val constructor = powerProfileClass.getConstructor(Context::class.java)
            val powerProfile = constructor.newInstance(context)
            
            // "battery.capacity" constant value is "battery.capacity"
            val getAveragePower = powerProfileClass.getMethod("getAveragePower", String::class.java)
            val capacity = getAveragePower.invoke(powerProfile, "battery.capacity") as Double
            
            capacity.toInt()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getRunningProcesses(): List<ProcessInfo> = withContext(Dispatchers.IO) {
        val processes = mutableListOf<ProcessInfo>()
        // 1. Try BusyBox top (standard format) first
        var output = readShellCommand("/data/local/tmp/busybox top -n 1")
        var isBusyBox = !output.isNullOrEmpty() && !output.contains("not found")
        
        // 2. Fallback to toybox if BusyBox failed
        if (!isBusyBox) {
            output = readShellCommand("top -b -n 1 -o PID,USER,RES,CPU,NAME")
        }

        if (!output.isNullOrEmpty()) {
            val lines = output.split("\n")
            var headerFound = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue
                
                // Identify header based on tool type
                if (isBusyBox) {
                    if (trimmed.contains("PID") && trimmed.contains("COMMAND")) {
                        headerFound = true
                        continue
                    }
                } else {
                    if (trimmed.contains("PID") && trimmed.contains("NAME")) {
                        headerFound = true
                        continue
                    }
                }

                if (headerFound) {
                    try {
                        val parts = trimmed.split(" +".toRegex())
                        if (isBusyBox && parts.size >= 8) {
                            // BusyBox: PID PPID USER STAT VSZ %VSZ %CPU COMMAND
                            val pid = parts[0].toIntOrNull() ?: continue
                            val user = parts[2]
                            val cpu = parts[6].replace(",", ".").replace("%", "").toFloatOrNull() ?: 0f
                            val name = parts.last()
                            processes.add(ProcessInfo(pid, name, cpu, 0f, user, name))
                        } else if (!isBusyBox && parts.size >= 5) {
                            // Toybox: PID,USER,RES,CPU,NAME
                            val pid = parts[0].toIntOrNull() ?: continue
                            val user = parts[1]
                            val resStr = parts[2]
                            val cpu = parts[3].replace(",", ".").toFloatOrNull() ?: 0f
                            val name = parts[4]
                            
                            val resKb = when {
                                resStr.endsWith("G") -> (resStr.dropLast(1).toDouble() * 1024 * 1024).toLong()
                                resStr.endsWith("M") -> (resStr.dropLast(1).toDouble() * 1024).toLong()
                                resStr.endsWith("K") -> (resStr.dropLast(1).toDouble()).toLong()
                                else -> resStr.toLongOrNull() ?: 0L
                            }
                            processes.add(ProcessInfo(pid, name, cpu, resKb / 1024f, user, name))
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
            val raw = if (path.endsWith("gpubusy")) {
                val match = gpuBusyRegex.find(content)
                if (match != null) {
                    val busy = match.groupValues[1].toLong()
                    val total = match.groupValues[2].toLong()
                    if (total > 0) (busy * 100 / total).toInt() else 0
                } else null
            } else {
                content.replace("%", "").split(whitespaceRegex)[0].toIntOrNull()
            }
            
            // 🚀 SYN-STYLE Sanity Check (Handles raw ticks vs percentages)
            when {
                raw == null -> null
                raw > 1000 -> (raw / 1000).coerceAtMost(100)
                raw > 100 -> (raw / 10).coerceAtMost(100)
                else -> raw.coerceAtMost(100)
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

    suspend fun getRawSystemProps(): Map<String, String> = withContext(Dispatchers.IO) {
        val props = mutableMapOf<String, String>()
        val output = readShellCommand("getprop") ?: return@withContext emptyMap()
        
        // getprop output format: [prop.name]: [value]
        val regex = Regex("\\[(.+?)\\]: \\[(.*?)\\]")
        output.lines().forEach { line ->
            regex.find(line)?.let { match ->
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                
                // Filter for useful hardware/identity props to avoid token bloat
                if (key.startsWith("ro.product.") || 
                    key.startsWith("ro.soc.") || 
                    key.startsWith("ro.board.") || 
                    key.startsWith("ro.hardware") ||
                    key.startsWith("ro.vivo.product") ||
                    key.startsWith("ro.boot.dpcfg") ||
                    key.contains("chipname") ||
                    key.contains("platform")
                ) {
                    props[key] = value
                }
            }
        }
        props
    }

    suspend fun getRawBatteryProps(): Map<String, String> = withContext(Dispatchers.IO) {
        val props = mutableMapOf<String, String>()
        val output = readShellCommand("getprop") ?: return@withContext emptyMap()
        
        val regex = Regex("\\[(.+?)\\]: \\[(.*?)\\]")
        output.lines().forEach { line ->
            regex.find(line)?.let { match ->
                val key = match.groupValues[1]
                val value = match.groupValues[2]
                
                if (key.contains("battery") || key.contains("charge") || key.contains("pwrlevel")) {
                    props[key] = value
                }
            }
        }
        props
    }

    suspend fun startDaemon() = daemonManager.deployAndStart()
}
