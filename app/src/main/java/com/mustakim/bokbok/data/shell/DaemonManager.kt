package com.mustakim.bokbok.data.shell

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 📦 DaemonManager
 *
 * Deploys and manages the background shell daemons (heartbeat.sh, monitor_daemon.sh).
 *
 * KEY DESIGN:
 * - Scripts are injected into /data/local/tmp/ via the elevated Shizuku shell.
 * - This overcomes the limitations of the app user being unable to write to system dirs.
 * - Temporary files (stats, logs) are created with world-readable permissions (666)
 *   so the app can read them via Java File API.
 */
@Singleton
class DaemonManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keepShell: KeepShell
) {
    // External storage path (protected from internal-only restrictive permissions)
    // ADB Shell (UID 2000) can access this directory on most devices.
    private val BASE_PATH = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
    
    // Script paths (Staged in external storage)
    private val SENTINEL_PATH = "$BASE_PATH/bokbok_sentinel.sh"
    private val MONITOR_PATH  = "$BASE_PATH/monitor_daemon.sh"
    
    // Stats path (read by Repository)
    val STATS_FILE: String = "$BASE_PATH/bokbok_monitor.now"

    private val _isDaemonRunning = MutableStateFlow(false)
    val isDaemonRunning = _isDaemonRunning.asStateFlow()

    suspend fun deployAndStart(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Inject scripts via Shizuku Shell
            deployScript("heartbeat.sh", SENTINEL_PATH)
            deployScript("monitor_daemon.sh", MONITOR_PATH)

            // 2. Stop any existing daemons (pkill -f)
            stopDaemon()

            // 3. Launch sentinel (heartbeat) as background process
            // Revision: Copy from staging to an executable path (/tmp) and run in one atomic chain 
            // to bypass both 'Permission denied' on internal data and the 'Security Scavenger' deletion.
            val sentinelTmp = "/data/local/tmp/bokbok_sentinel.sh"
            keepShell.doCmd("cp $SENTINEL_PATH $sentinelTmp && chmod 755 $sentinelTmp && nohup sh $sentinelTmp > /dev/null 2>&1 &")
            
            // 4. Launch monitor daemon as background process
            val monitorTmp = "/data/local/tmp/monitor_daemon.sh"
            keepShell.doCmd("cp $MONITOR_PATH $monitorTmp && chmod 755 $monitorTmp && nohup sh $monitorTmp > /dev/null 2>&1 &")

            // 5. Aggressive Whitelisting (Scene Pattern)
            // This prevents the system from killing the app or daemon in the background
            val pkg = context.packageName
            keepShell.doCmd("dumpsys deviceidle whitelist +$pkg; " +
                    "cmd appops set $pkg RUN_IN_BACKGROUND allow; " +
                    "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow; " +
                    "am set-standby-bucket $pkg active; " +
                    "am set-bg-restriction-level --user 0 $pkg unrestricted; " +
                    "am set-inactive --user 0 $pkg false; " +
                    "am unfreeze --sticky $pkg; " +
                    "am set-foreground-service-delegate --user 0 $pkg start; " +
                    "renice -n -20 -p $(pgrep -f bokbok_sentinel.sh); " +
                    "renice -n -20 -p $(pgrep -f monitor_daemon.sh); " +
                    "cmd package compile -m speed $pkg; " +
                    "true")

            Log.d("DaemonManager", "Daemons started in $BASE_PATH via Shizuku with Whitelisting.")
            _isDaemonRunning.value = true
            true
        } catch (e: Exception) {
            Log.e("DaemonManager", "Failed to start daemons: ${e.message}")
            false
        }
    }

    /**
     * Injects an asset file to a remote path by writing it directly to internal storage.
     * This bypasses /data/local/tmp/ and protects the script from aggressive cleanup.
     */
    private suspend fun deployScript(assetName: String, remotePath: String) {
        withContext(Dispatchers.IO) {
            val file = File(remotePath)
            Log.d("DaemonManager", "Extracting $assetName to protected storage: $remotePath...")
            
            // 💡 REFINEMENT: Inject the actual external path into the script for SHARED FILES ONLY!
            // This ensures scripts stay in /data/local/tmp/ for execution (noexec bypass)
            // but log/stat files are in $BASE_PATH so the app can read them.
            val content = context.assets.open(assetName).bufferedReader().use { it.readText() }
            val modifiedContent = content
                .replace("/data/local/tmp/bokbok_", "$BASE_PATH/bokbok_")
                .replace("/data/local/tmp/heartbeat_", "$BASE_PATH/heartbeat_")
            
            file.writeText(modifiedContent)
            
            // Set executable bits for the owner (the app)
            file.setExecutable(true, false)
            file.setReadable(true, false)

            // Ensure shell user can execute (chmod 755)
            keepShell.doCmd("chmod 755 $remotePath")
            
            // Verification
            val check = keepShell.doCmd("[ -f $remotePath ] && echo 'exists'")
            if (!check.contains("exists")) {
                throw Exception("Failed to verify script at $remotePath")
            }
            Log.d("DaemonManager", "Successfully verified $assetName at $remotePath")
        }
    }

    suspend fun stopDaemon() = withContext(Dispatchers.IO) {
        Log.d("DaemonManager", "Stopping existing daemon processes...")
        // Use pkill -f to find by script name. Multi-command for robustness.
        keepShell.doCmd("pkill -9 -f bokbok_sentinel.sh; pkill -9 -f monitor_daemon.sh; true")
        _isDaemonRunning.value = false
        delay(500)
    }

    suspend fun checkIsAlive(): Boolean = withContext(Dispatchers.IO) {
        val result = keepShell.doCmd("pgrep -f bokbok_sentinel.sh")
        val alive = result.trim().isNotEmpty() && !result.contains("error")
        _isDaemonRunning.value = alive
        Log.d("DaemonManager", "Sentinel alive check: $alive")
        alive
    }

    /**
     * Reads monitoring stats written by the daemon.
     * Note: File must be created with chmod 666 in the shell script.
     */
    suspend fun readStats(): Map<String, String> = withContext(Dispatchers.IO) {
        val statsFile = File(STATS_FILE)
        if (!statsFile.exists()) return@withContext emptyMap()
        try {
            statsFile.readText().lines().associate { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to ""
            }.filter { it.key.isNotBlank() }
        } catch (e: Exception) {
            Log.e("DaemonManager", "Error reading stats: ${e.message}")
            emptyMap()
        }
    }
}
