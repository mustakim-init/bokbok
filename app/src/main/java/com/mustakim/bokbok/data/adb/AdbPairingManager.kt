package com.mustakim.bokbok.data.adb

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.mustakim.bokbok.data.service.AdbPairingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "AdbPairingManager"
private const val PREFS_NAME = "adb_resurrection"
private const val KEY_PAIRED = "adb_paired"

sealed class ResurrectionSetupState {
    /** User hasn't done setup yet. */
    object NotSetup : ResurrectionSetupState()
    /** mDNS discovery is running, waiting for wireless ADB port. */
    object Discovering : ResurrectionSetupState()
    /** Discovery found a pairing port, waiting for user to enter code. */
    data class PairingCodeRequired(val port: Int) : ResurrectionSetupState()
    /** ADB port found, connecting and running Shizuku start script. */
    object Connecting : ResurrectionSetupState()
    /** Setup complete — resurrection is active. */
    object Active : ResurrectionSetupState()
    /** Something went wrong. */
    data class Error(val message: String) : ResurrectionSetupState()
}

private const val ADB_KEY_NAME = "BokBokResurrector"

@Singleton
class AdbPairingManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val adbKeyStore: AdbKeyStore,
    private val gameRepository: dagger.Lazy<com.mustakim.bokbok.data.repository.GameRepository>
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<ResurrectionSetupState>(
        if (prefs.getBoolean(KEY_PAIRED, false))
            ResurrectionSetupState.Active
        else
            ResurrectionSetupState.NotSetup
    )
    val state: StateFlow<ResurrectionSetupState> = _state

    fun getAdbPublicKey(): String {
        val adbKeyStore = com.mustakim.bokbok.data.adb.SharedPreferencesAdbKeyStore(
            context.getSharedPreferences("adb_resurrection", Context.MODE_PRIVATE)
        )
        val key = com.mustakim.bokbok.data.adb.AdbKey(adbKeyStore, ADB_KEY_NAME)
        return String(key.adbPublicKey).replace("\u0000", "").trim()
    }

    val isPaired: Boolean get() = prefs.getBoolean(KEY_PAIRED, false)

    /**
     * Starts the one-time pairing process by launching the AdbPairingService.
     * This handles discovery and pairing via a persistent notification with RemoteInput.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    fun startPairingService() {
        Log.d(TAG, "Starting AdbPairingService...")
        val intent = Intent(context, AdbPairingService::class.java).apply {
            action = AdbPairingService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    /**
     * Uses Shizuku to permanently authorize this app's ADB public key.
     * This eliminates the need for a PC or manual pairing if Shizuku is already running.
     */
    suspend fun selfAuthorizeViaShizuku(): Boolean = withContext(Dispatchers.IO) {
        try {
            val pubKey = getAdbPublicKey()
            Log.d(TAG, "Self-authorizing with public key: ${pubKey.take(20)}...")
            
            // Append the public key to adb_keys. 
            // We use 'sh -c' to ensure redirection works correctly.
            // We also check if it's already there to avoid duplicates.
            val command = "mkdir -p /data/misc/adb && " +
                    "grep -q '$pubKey' /data/misc/adb/adb_keys || " +
                    "echo '$pubKey' >> /data/misc/adb/adb_keys"
            
            ShizukuRunner.command(command)
            
            Log.d(TAG, "Self-authorization successful!")
            prefs.edit().putBoolean(KEY_PAIRED, true).apply()
            _state.value = ResurrectionSetupState.Active
            true
        } catch (e: Exception) {
            Log.e(TAG, "Self-authorization failed", e)
            _state.value = ResurrectionSetupState.Error("Self-authorization failed: ${e.message}")
            false
        }
    }

    /**
     * Starts the one-time pairing process by discovering the pairing port.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun startPairing(): Unit = withContext(Dispatchers.IO) {
        _state.value = ResurrectionSetupState.Discovering
        try {
            val portDeferred = CompletableDeferred<Int>()
            val mdns = AdbMdns(context, AdbMdns.TLS_PAIRING) { _, port ->
                if (!portDeferred.isCompleted) portDeferred.complete(port)
            }
            mdns.start()
            val port = withTimeoutOrNull(30_000) { portDeferred.await() }
            mdns.stop()

            if (port == null || port == -1) {
                _state.value = ResurrectionSetupState.Error("Pairing service not found. Make sure 'Pair device with pairing code' is open in Developer Options.")
            } else {
                _state.value = ResurrectionSetupState.PairingCodeRequired(port)
            }
        } catch (e: Exception) {
            _state.value = ResurrectionSetupState.Error(e.message ?: "Failed to discover pairing service.")
        }
    }

    /**
     * Performs the final pairing handshake with the given code.
     */
    suspend fun pair(port: Int, code: String): Boolean = withContext(Dispatchers.IO) {
        _state.value = ResurrectionSetupState.Connecting
        try {
            val adbKey = AdbKey(adbKeyStore, ADB_KEY_NAME)
            
            val client = AdbPairingClient("127.0.0.1", port, code, adbKey)
            if (client.pair()) {
                // Success! Now run the standard setup to start Shizuku
                runSetup()
            } else {
                _state.value = ResurrectionSetupState.Error("Pairing failed. Check the code and try again.")
                false
            }
        } catch (e: Exception) {
            _state.value = ResurrectionSetupState.Error(e.message ?: "Pairing failed.")
            false
        }
    }

    /**
     * Runs the setup: starts Shizuku and deploys heartbeat.
     * If port is null, it attempts to discover the CONNECT port first.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun runSetup(existingPort: Int? = null): Boolean = withContext(Dispatchers.IO) {
        _state.value = ResurrectionSetupState.Connecting
        try {
            var targetHost: String = "127.0.0.1"
            val port = existingPort ?: run {
                _state.value = ResurrectionSetupState.Discovering
                val deferred = CompletableDeferred<Pair<java.net.InetAddress?, Int>>()
                val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { h, p ->
                    if (!deferred.isCompleted) deferred.complete(h to p)
                }
                mdns.start()
                val result = withTimeoutOrNull(20_000) { deferred.await() }
                mdns.stop()
                
                targetHost = result?.first?.hostAddress ?: "127.0.0.1"
                result?.second
            }

            if (port == null || port == -1) {
                Log.e(TAG, "mDNS discovery timed out — is Wireless Debugging enabled?")
                _state.value = ResurrectionSetupState.Error(
                    "Could not find wireless ADB. Make sure Wireless Debugging is enabled in Developer Options."
                )
                return@withContext false
            }

            _state.value = ResurrectionSetupState.Connecting
            Log.d(TAG, "Connecting to ADB host: $targetHost, port: $port...")

            val key = AdbKey(adbKeyStore, ADB_KEY_NAME)
            AdbClient(targetHost, port, key).use { client ->
                client.connect()
                Log.d(TAG, "ADB connected. Running Shizuku start script...")

                // Start Shizuku
                client.command("shell:sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh") {
                    Log.d(TAG, "Shizuku start: ${String(it)}")
                }

                // Sync game list BEFORE Shizuku finishes initializing
                syncGameListViaClient(client)

                // Kill any existing instance just in case
                client.command("shell:pkill -f heartbeat.sh")

                // Promote standby bucket
                client.command("shell:cmd usage-stats set-standby-bucket com.mustakim.bokbok active")
            }

            prefs.edit().putBoolean(KEY_PAIRED, true).apply()
            _state.value = ResurrectionSetupState.Active
            Log.d(TAG, "Setup complete — resurrection is now active.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Setup failed", e)
            _state.value = ResurrectionSetupState.Error(
                when {
                    e.message?.contains("Connection refused") == true ->
                        "ADB connection refused. Make sure Wireless Debugging is enabled and the key is trusted."
                    e.message?.contains("timeout") == true ->
                        "Connection timed out. Make sure you're on the same Wi-Fi network."
                    else -> e.message ?: "Unknown error during setup."
                }
            )
            false
        }
    }

    /**
     * Syncs the game list to the device directly via an AdbClient connection,
     * bypassing Shizuku entirely. This ensures the game list is available
     * even before Shizuku has finished starting.
     */
    private suspend fun syncGameListViaClient(client: AdbClient) {
        try {
            val games = gameRepository.get().getGames().first()
            if (games.isEmpty()) {
                Log.d(TAG, "No games to sync via AdbClient.")
                client.command("shell:rm -f /data/local/tmp/bokbok_games.list")
                return
            }
            val packageList = games.joinToString("\\n") { it.packageName }
            Log.d(TAG, "Syncing ${games.size} games via AdbClient.")
            client.command("shell:echo -e \"$packageList\" > /data/local/tmp/bokbok_games.list && chmod 644 /data/local/tmp/bokbok_games.list")
        } catch (e: Exception) {
            Log.e(TAG, "Game list sync via AdbClient failed", e)
        }
    }

    fun resetSetup() {
        prefs.edit().putBoolean(KEY_PAIRED, false).apply()
        _state.value = ResurrectionSetupState.NotSetup
        Log.d(TAG, "Setup reset.")
    }

    /**
     * Attempts to silently reconnect to a new ADB port and redeploy the heartbeat script.
     * This is designed for boot completion or app updates.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun silentReconnect(): Boolean = withContext(Dispatchers.IO) {
        if (!isPaired) {
             Log.d(TAG, "Silent reconnect skipped: App not paired yet.")
             return@withContext false
        }
        
        Log.d(TAG, "Starting silent reconnect scan...")
        
        // 1. Discover Port
        val deferred = CompletableDeferred<Pair<java.net.InetAddress?, Int>>()
        val mdns = AdbMdns(context, AdbMdns.TLS_CONNECT) { h, p ->
             if (!deferred.isCompleted) deferred.complete(h to p)
        }
        mdns.start()
        val result = withTimeoutOrNull(15_000) { deferred.await() }
        mdns.stop()
        
        val host = result?.first?.hostAddress ?: "127.0.0.1"
        val port = result?.second ?: -1
        
        if (port == -1) {
             Log.e(TAG, "Silent reconnect failed: No wireless ADB service found.")
             return@withContext false
        }
        
        // 2. Connect & Deploy
        return@withContext try {
             val key = AdbKey(adbKeyStore, ADB_KEY_NAME)
             AdbClient(host, port, key).use { client ->
                 client.connect()
                 Log.d(TAG, "Silent connect success. Redeploying scripts...")
                 
                 // Kill old heartbeat
                 client.command("shell:pkill -f heartbeat.sh")
                 
                 // Start Shizuku
                 client.command("shell:sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh")
                 
                 // Sync game list BEFORE heartbeat restarts
                 syncGameListViaClient(client)
                 
                 Log.d(TAG, "Silent reconnect complete: Shizuku restarted. GameWatchdogService will handle heartbeat deployment.")
                 
                 Log.d(TAG, "Silent reconnect complete: Scripts restarted.")
                 true
             }
        } catch (e: Exception) {
             Log.e(TAG, "Silent reconnect execution failed", e)
             false
        }
    }
}
