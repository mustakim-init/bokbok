package com.mustakim.bokbok.data.repository

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.content.pm.PackageManager
import com.mustakim.bokbok.data.model.BatteryInfo
import com.mustakim.bokbok.data.model.CpuInfo
import com.mustakim.bokbok.data.model.GpuInfo
import com.mustakim.bokbok.data.model.RamInfo
import com.mustakim.bokbok.data.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.File
import java.io.StringReader
import java.io.InputStreamReader
import android.os.ParcelFileDescriptor

class DeviceMonitorRepository(private val context: Context) {

    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private var lastCpuTotal: Long = 0
    private var lastCpuIdle: Long = 0
    private var lastCoreTotals: MutableList<Long> = mutableListOf()
    private var lastCoreIdles: MutableList<Long> = mutableListOf()

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

    private fun calculateCpuLoad(): Pair<Float, List<Float>> {
        var totalLoad = 0f
        val coreLoads = mutableListOf<Float>()
        
        // Try reading /proc/stat directly first, then fallback to Shizuku
        var statContent: String? = null
        try {
            val directContent = File("/proc/stat").readText()
            // Validate if content is usable (not empty or restricted)
            if (directContent.isNotEmpty() && directContent.startsWith("cpu ")) {
                statContent = directContent
            }
        } catch (_: Exception) {
            // Direct read failed
        }
        
        // Fallback to Shizuku if direct read failed or returned invalid data
        if (statContent == null) {
            statContent = readShellCommand("cat /proc/stat")
        }

        if (!statContent.isNullOrEmpty()) {
            try {
                val reader = BufferedReader(StringReader(statContent))
                var line = reader.readLine()

                // Overall CPU
                if (line != null && line.startsWith("cpu ")) {
                    val parts = line.split(" +".toRegex())
                    val idle = parts[4].toLong()
                    val total = parts.subList(1, 8).map { it.toLong() }.sum()

                    if (lastCpuTotal > 0) {
                        val diffTotal = total - lastCpuTotal
                        val diffIdle = idle - lastCpuIdle
                        totalLoad = if (diffTotal > 0) (diffTotal - diffIdle).toFloat() / diffTotal * 100f else 0f
                    }
                    lastCpuTotal = total
                    lastCpuIdle = idle
                }

                // Per core CPU
                var coreIdx = 0
                while (true) {
                    line = reader.readLine()
                    if (line == null || !line.startsWith("cpu")) break 
                    // Note: /proc/stat contains cpu0, cpu1... then intr, ctxt etc.
                    // We only care about lines starting with "cpu" followed by a digit.
                    if (!line.startsWith("cpu$coreIdx")) {
                        // Sometimes cores might be skipped or list ends? 
                        // Usually it's sequential. If line is "intr", stop.
                        if (!line.startsWith("cpu")) break 
                        continue // Skip unexpected cpu lines if any
                    }
                    
                    val parts = line.split(" +".toRegex())
                    val idle = parts[4].toLong()
                    val total = parts.subList(1, 8).map { it.toLong() }.sum()

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
                    coreIdx++
                }
                reader.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return Pair(totalLoad.coerceIn(0f, 100f), coreLoads)
    }

    private fun readFreq(core: Int): Long {
        val path = "/sys/devices/system/cpu/cpu$core/cpufreq/scaling_cur_freq"
        return try {
            File(path).readText().trim().toLong()
        } catch (_: Exception) {
            // Try Shizuku if direct read fails
            val output = readShellCommand("cat $path")
            output?.trim()?.toLongOrNull() ?: 0L
        }
    }
    
    private fun isCoreOnline(core: Int): Boolean {
        return try {
            val path = "/sys/devices/system/cpu/cpu$core/online"
            val file = File(path)
            if (file.exists()) {
                file.readText().trim() == "1"
            } else true 
        } catch (_: Exception) {
            val output = readShellCommand("cat /sys/devices/system/cpu/cpu$core/online")
            output?.trim() == "1"
        }
    }

    private fun readShellCommand(command: String): String? {
        if (!checkShizukuPermission()) return null
        return try {
            var binder = Shizuku.getBinder()
            if (binder == null) return null
            
            val service = IShizukuService.Stub.asInterface(binder)
            val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
            val reader = BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(process.inputStream)))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
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



    fun getRamInfo(): RamInfo {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val total = memInfo.totalMem / (1024 * 1024)
        val available = memInfo.availMem / (1024 * 1024)
        val used = total - available
        val percent = (used.toFloat() / total.toFloat()) * 100f

        return RamInfo(
            totalMb = total,
            usedMb = used,
            availableMb = available,
            usagePercent = percent
        )
    }

    fun getBatteryInfo(): BatteryInfo {
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
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Unspecified Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> "Unknown"
        }

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val currentMa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000

        return BatteryInfo(
            level = batteryPct,
            temperatureCelsius = temp,
            isCharging = isCharging,
            currentMa = if (currentMa != 0) currentMa else null,
            voltageV = voltage,
            health = health
        )
    }

    fun getStorageInfo(): StorageInfo {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        val totalGb = (totalBlocks * blockSize) / (1024f * 1024f * 1024f)
        val availableGb = (availableBlocks * blockSize) / (1024f * 1024f * 1024f)
        val usedGb = totalGb - availableGb
        val percent = (usedGb / totalGb) * 100f
        
        return StorageInfo(
            totalGb = totalGb,
            usedGb = usedGb,
            availableGb = availableGb,
            usagePercent = percent
        )
    }

    suspend fun getGpuInfo(): GpuInfo = withContext(Dispatchers.IO) {
        // GPU paths discovered from vtools
        val loadPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
            "/sys/class/kgsl/kgsl-3d0/devfreq/gpu_load",
            "/sys/kernel/gpu/gpu_busy"
        )
        
        val freqPaths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq",
            "/sys/kernel/gpu/gpu_clock"
        )
        
        var load: Int? = null
        for (path in loadPaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    load = file.readText().trim().replace("%", "").toInt()
                    break
                }
            } catch (_: Exception) {}
        }
        
        var freq: Int? = null
        for (path in freqPaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    freq = (file.readText().trim().toLong() / 1000000).toInt() // Обычно в Гц или кГц
                    if (freq > 2000) freq /= 1000 // Корректировка если в Гц
                    break
                }
            } catch (_: Exception) {}
        }
        
        GpuInfo(
            loadPercent = load,
            frequencyMhz = freq,
            available = load != null || freq != null
        )
    }
}
