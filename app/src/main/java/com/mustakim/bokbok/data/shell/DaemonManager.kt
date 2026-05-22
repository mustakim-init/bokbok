package com.mustakim.bokbok.data.shell

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 📦 DaemonManager
 *
 * Deploys and manages the background sentinel (heartbeat.sh / bokbok_sentinel.sh).
 * The sentinel is an event-driven logcat listener that detects game launches
 * and triggers the overlay with 0% idle CPU overhead.
 *
 * KEY DESIGN:
 * - The script is injected into /data/local/tmp/ via the elevated Shizuku shell.
 * - A Mutex prevents race conditions from concurrent deployAndStart() calls.
 * - stopDaemon() uses precise pkill signatures to avoid killing other apps' logcat listeners.
 */
@Singleton
class DaemonManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keepShell: KeepShell
) {
    // /data/local/tmp is accessible by both the ADB shell (UID 2000) running the sentinel
    // and via Shizuku shell commands from the app. Sentinel v14 uses these paths.
    private val BASE_PATH = "/data/local/tmp"
    
    // Sentinel script path
    private val SENTINEL_PATH = "$BASE_PATH/bokbok_sentinel.sh"
    
    // Window file: written by sentinel, read by Repository to know the active game
    val WINDOW_FILE: String = "$BASE_PATH/bokbok_current_window"

    private val _isDaemonRunning = MutableStateFlow(false)
    val isDaemonRunning = _isDaemonRunning.asStateFlow()

    private val launchMutex = Mutex()

    suspend fun deployAndStart(): Boolean = launchMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                // 1. Inject binaries and scripts via Shizuku Base64
                deployScript("busybox", "$BASE_PATH/busybox")
                deployScript("heartbeat.sh", SENTINEL_PATH)

                // 2. Kill any existing sentinel + its logcat child
                stopDaemon()

                // 3. Launch sentinel as a background process
                keepShell.doCmd("chmod 755 $SENTINEL_PATH && nohup sh $SENTINEL_PATH > /dev/null 2>&1 &")

                // 4. Aggressive Whitelisting (Scene Pattern)
                val pkg = context.packageName
                keepShell.doCmd("dumpsys deviceidle whitelist +$pkg; " +
                        "cmd appops set $pkg RUN_IN_BACKGROUND allow; " +
                        "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow; " +
                        "am set-standby-bucket $pkg active; " +
                        "am set-bg-restriction-level --user 0 $pkg unrestricted; " +
                        "am set-inactive --user 0 $pkg false; " +
                        "am unfreeze --sticky $pkg; " +
                        "am set-foreground-service-delegate --user 0 $pkg start; " +
                        "renice -n -10 -p $(pgrep -f bokbok_sentinel.sh); " +
                        "cmd package compile -m speed $pkg; " +
                        "true")

                Log.d("DaemonManager", "Sentinel started in $BASE_PATH via Shizuku.")
                _isDaemonRunning.value = true
                true
            } catch (e: Exception) {
                Log.e("DaemonManager", "Failed to start sentinel: ${e.message}")
                false
            }
        }
    }

    private suspend fun deployScript(assetName: String, remotePath: String) =
        withContext(Dispatchers.IO) {
            try {
                Log.d("DaemonManager", "Injecting $assetName via Shizuku Base64...")
                
                // 1. Read asset and convert to Base64
                val bytes = context.assets.open(assetName).use { it.readBytes() }
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                
                // 2. Inject via Shizuku shell pipe (this bypasses app-user permission blocks)
                keepShell.doCmd("echo '$base64' | base64 -d > $remotePath && chmod 755 $remotePath")
                
                // 3. Verification
                val check = keepShell.doCmd("[ -f $remotePath ] && echo 'exists'")
                if (!check.contains("exists")) {
                    throw Exception("Failed to verify script at $remotePath")
                }
                Log.d("DaemonManager", "Successfully injected $assetName to $remotePath")
            } catch (e: Exception) {
                Log.e("DaemonManager", "Script injection failed: ${e.message}")
                throw e
            }
        }

    suspend fun stopDaemon() = withContext(Dispatchers.IO) {
        Log.d("DaemonManager", "Stopping sentinel processes...")
        // 1. Kill the sentinel script AND its child logcat listener.
        // 2. Clear the window file to prevent stale game detection.
        keepShell.doCmd("pkill -9 -f bokbok_sentinel.sh; " +
                "pkill -9 -f 'logcat -b events -v brief -s wm_set_resumed_activity am_resume_activity'; " +
                "rm -f $WINDOW_FILE; " +
                "true")
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

    suspend fun reportCurrentWindow(packageName: String) = withContext(Dispatchers.IO) {
        try {
            // Use shell to write to the file since app UID might be restricted in /data/local/tmp
            keepShell.doCmd("echo '$packageName' > $WINDOW_FILE && chmod 666 $WINDOW_FILE")
            Log.d("DaemonManager", "Reported window $packageName via Shizuku")
        } catch (e: Exception) {
            Log.e("DaemonManager", "Error reporting window: ${e.message}")
        }
    }
}
