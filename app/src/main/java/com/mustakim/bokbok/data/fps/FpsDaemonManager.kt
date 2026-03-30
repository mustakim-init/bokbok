package com.mustakim.bokbok.data.fps

import android.content.Context
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*

/**
 * FpsDaemonManager
 *
 * Manages the fps_daemon native binary lifecycle and reads real-time FPS
 * from it over a Unix domain socket.
 *
 * The daemon uses a uretprobe on libgui.so's queueBuffer() - the same
 * technique used by Scene/vtools - which works for ALL apps including
 * Vulkan games, regardless of renderer.
 *
 * Usage:
 *   val mgr = FpsDaemonManager(context)
 *   mgr.start(scope)
 *   mgr.setTargetPid(12345)
 *   mgr.fps.collect { fps -> ... }
 *   mgr.stop()
 */
class FpsDaemonManager(private val context: Context) {

    companion object {
        private const val TAG = "FpsDaemonManager"
        private const val SOCKET_NAME = "bokbok-fps"
        private const val DAEMON_NAME = "fps_daemon"
        
        // Use external storage for staging (accessible to ADB shell)
        private fun getDaemonPath(context: Context): String {
            val externalDir = context.getExternalFilesDir(null)
            return if (externalDir != null) {
                "${externalDir.absolutePath}/$DAEMON_NAME"
            } else {
                "${context.filesDir.absolutePath}/$DAEMON_NAME"
            }
        }
        
        private const val RECONNECT_DELAY_MS = 1000L
    }

    private val _fps = MutableStateFlow(0f)
    val fps: StateFlow<Float> = _fps

    private var daemonProcess: Process? = null
    private var socket: LocalSocket? = null
    private var reader: BufferedReader? = null
    private var writer: PrintWriter? = null
    private var managerScope: CoroutineScope? = null
    private var currentPid: Int = 0

    private var shizukuExecutor: (suspend (String) -> Unit)? = null

    private var shizukuGetter: (suspend (String) -> String)? = null
    private var shizukuRaw: (suspend (String) -> String)? = null

    // ----------------------------------------------------------------
    //  Public API
    // ----------------------------------------------------------------

    /**
     * Starts the daemon and begins the connection loop.
     * Call this once when your overlay service starts.
     */
    fun start(
        scope: CoroutineScope,
        executeShizukuCommand: suspend (String) -> Unit,
        executeShizukuCommandAndGet: suspend (String) -> String,
        executeShizukuCommandRaw: suspend (String) -> String
    ) {
        managerScope = scope
        shizukuExecutor = executeShizukuCommand
        shizukuGetter = executeShizukuCommandAndGet
        shizukuRaw = executeShizukuCommandRaw  // ← store it
        scope.launch(Dispatchers.IO) {
            extractDaemon()
            launchDaemon(executeShizukuCommand, executeShizukuCommandAndGet, executeShizukuCommandRaw)
            connectionLoop()
        }
    }

    /**
     * Tells the daemon which PID to monitor.
     * Call this whenever the game changes.
     */
    fun setTargetPid(pid: Int) {
        currentPid = pid
        scope_send("pid $pid")
    }

    /**
     * Convenience: resolve PID from package name and set it.
     * Uses Shizuku shell to call pidof.
     */
    suspend fun setTargetPackage(
        packageName: String,
        executeShizukuCommandAndGet: suspend (String) -> String
    ) {
        try {
            // 🚀 SYN-STYLE: Robust PID detection (bypassing pidof limitations on Vivo/iQOO)
            // 1. Primary: Get PID from the currently resumed activity in SurfaceFlinger/Activities
            val dumpsysCmd = "dumpsys activity activities | grep -E 'topResumedActivity|mFocusedApp|VisibleActivityProcess' | grep $packageName"
            val focusInfo = executeShizukuCommandAndGet(dumpsysCmd)
            
            var pid = 0
            // Extract PID from patterns like: 1234:com.example/u0a123
            val pidMatch = Regex("([0-9]+):$packageName").find(focusInfo)
            if (pidMatch != null) {
                pid = pidMatch.groupValues[1].toIntOrNull() ?: 0
            }
            
            // 2. Fallback: pidof (Multiple)
            if (pid == 0) {
                val pidStr = executeShizukuCommandAndGet("pidof $packageName").trim()
                pid = pidStr.split(" ").firstOrNull()?.toIntOrNull() ?: 0
            }

            if (pid > 0) {
                Log.d(TAG, "Resolved $packageName -> pid $pid (Scene Method)")
                setTargetPid(pid)
            } else {
                Log.w(TAG, "Could not resolve PID for $packageName")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setTargetPackage failed", e)
        }
    }

    /**
     * Stops monitoring and shuts down the daemon.
     */
    fun stop() {
        scope_send("stop")
        disconnect()
        daemonProcess?.destroyForcibly()
        daemonProcess = null
        _fps.value = 0f
    }

    // ----------------------------------------------------------------
    //  Daemon lifecycle
    // ----------------------------------------------------------------

    /**
     * Copies fps_daemon from APK assets directly to internal storage.
     * This bypasses /data/local/tmp/ and protects the binary from cleanup.
     */
    private fun extractDaemon() {
        try {
            val daemonOutputFile = File(getDaemonPath(context))
            Log.d(TAG, "Extracting $DAEMON_NAME to protected storage: ${daemonOutputFile.absolutePath}...")
            
            context.assets.open(DAEMON_NAME).use { input ->
                daemonOutputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            // Set executable bits (Owner: App UID)
            daemonOutputFile.setExecutable(true, false)
            daemonOutputFile.setReadable(true, false)

            Log.d(TAG, "Daemon extracted and marked executable in internal storage")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract daemon to internal storage", e)
        }
    }

    /**
     * Launches the native daemon from the app's private files directory.
     * Includes aggressive whitelisting to prevent the system from killing it.
     */
    private suspend fun launchDaemon(
        executeShizukuCommand: suspend (String) -> Unit,
        executeShizukuCommandAndGet: suspend (String) -> String,
        executeShizukuCommandRaw: suspend (String) -> String
    ) {
        try {
            val path = getDaemonPath(context)
            val pkg = context.packageName
            
            // 1. Kill existing instances
            executeShizukuCommand("pkill -9 -x $DAEMON_NAME 2>/dev/null; true")
            delay(200)

            // 2. Ensure permissions (Shizuku shell needs to be able to execute from app dir)
            executeShizukuCommand("chmod 755 $path")

            // 3. Launch with nohup
            // Revision: Copy to /data/local/tmp and exec immediately to solve Permission Denied
            // and outrun the Vivo security scavenger.
            val tmpPath = "/data/local/tmp/$DAEMON_NAME"
            executeShizukuCommandRaw("cp $path $tmpPath && chmod 777 $tmpPath && nohup $tmpPath > /data/local/tmp/fps_daemon.log 2>&1 &")
            delay(500)

            // 4. Aggressive Keep-Alive Whitelisting
            executeShizukuCommand("dumpsys deviceidle whitelist +$pkg; " +
                    "am set-standby-bucket $pkg active; " +
                    "am set-bg-restriction-level --user 0 $pkg unrestricted; " +
                    "am unfreeze --sticky $pkg; " +
                    "true")

            // 5. Verification
            val log = executeShizukuCommandAndGet("cat /data/local/tmp/fps_daemon.log")
            Log.d(TAG, "Daemon log: $log")

            val pid = executeShizukuCommandAndGet("pidof $DAEMON_NAME")
            Log.d(TAG, "Daemon PID: $pid")

            Log.d(TAG, "Daemon launched from protected storage via Shizuku")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch daemon from protected storage", e)
        }
    }

    // ----------------------------------------------------------------
    //  Socket connection loop
    // ----------------------------------------------------------------

    private suspend fun connectionLoop() {
        // Wait a moment for daemon to start listening
        delay(500)

        while (managerScope?.isActive == true) {
            try {
                Log.d("FpsDaemonManager", "Attempting to connect to abstract socket: $SOCKET_NAME")
                val sock = LocalSocket()
                sock.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
                socket = sock
                reader = BufferedReader(InputStreamReader(sock.inputStream))
                writer = PrintWriter(BufferedWriter(OutputStreamWriter(sock.outputStream)), true)
                Log.d("FpsDaemonManager", "Socket connected.")

                // If we have a pending target, send it now
                if (currentPid > 0) {
                    Log.d("FpsDaemonManager", "Socket connected. Sending PID: $currentPid")
                    writer?.println("pid $currentPid")
                    Log.d("FpsDaemonManager", "PID $currentPid sent successfully to native daemon.")
                }
                readLoop()
            } catch (e: Exception) {
                Log.w(TAG, "Socket error: ${e.message}, reconnecting...")
            } finally {
                disconnect()
            }

            // Check if daemon died and restart it
            if (daemonProcess?.isAlive == false) {
                Log.w(TAG, "Daemon died, restarting...")
                shizukuExecutor?.let { cmd ->
                    shizukuGetter?.let { get ->
                        shizukuRaw?.let { raw ->
                            launchDaemon(cmd, get, raw)
                        }
                    }
                }
            }

            delay(RECONNECT_DELAY_MS)
        }
    }

    private fun connect() {
        val sock = LocalSocket()
        // Use abstract namespace to bypass filesystem permission checks in /data/local/tmp/
        sock.connect(LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT))
        socket = sock
        reader = BufferedReader(InputStreamReader(sock.inputStream))
        writer = PrintWriter(BufferedWriter(OutputStreamWriter(sock.outputStream)), true)
        Log.d(TAG, "Connected to daemon")
    }

    private suspend fun readLoop() {
        val r = reader ?: return
        while (managerScope?.isActive == true) {
            val line = withContext(Dispatchers.IO) {
                try { r.readLine() } catch (_: Exception) { null }
            } ?: break

            when {
                line.startsWith("fps ") -> {
                    val fps = line.removePrefix("fps ").trim().toFloatOrNull() ?: 0f
                    _fps.value = fps
                }
                line == "ready" -> {
                    Log.d(TAG, "Daemon ready")
                    // Re-send target if we had one
                    if (currentPid > 0) scope_send("pid $currentPid")
                }
            }
        }
    }

    private fun disconnect() {
        try { writer?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        writer = null
        reader = null
        socket = null
        _fps.value = 0f
    }

    private fun scope_send(msg: String) {
        managerScope?.launch(Dispatchers.IO) {
            try {
                writer?.println(msg)
            } catch (e: Exception) {
                Log.w(TAG, "send failed: $msg")
            }
        }
    }
}
