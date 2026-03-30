package com.mustakim.bokbok.data.shell

import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🚀 PERFORMANCE ENGINE: KeepShell (Shizuku Edition)
 *
 * Maintains a persistent shell session via Shizuku.
 *
 * This version uses a refined reflection bypass to access his streams.
 * Instead of searching all methods broadly, we explicitly cast the process 
 * object if possible, or bind to the specific ShizukuRemoteProcess class.
 */
@Singleton
class KeepShell @Inject constructor() {

    private var process: Any? = null
    private var writer: BufferedWriter? = null
    private var reader: BufferedReader? = null

    private val isRunning = AtomicBoolean(false)
    private val _isServiceAvailable = MutableStateFlow(false)
    val isServiceAvailable = _isServiceAvailable.asStateFlow()

    private val mLock = Mutex()
    private val START_TAG = "|BOKBOK>>|"
    private val END_TAG   = "|<<BOKBOK|"

    suspend fun start(): Boolean = withContext(Dispatchers.IO) {
        if (isRunning.get()) return@withContext true

        try {
            Log.d("KeepShell", "Starting persistent shell session via Shizuku...")
            
            val binder: IBinder? = Shizuku.getBinder()
            if (binder == null) return@withContext false

            if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                return@withContext false
            }

            // 1. Get Shizuku's native newProcess method which handles wrapping the Streams
            val shizukuClass = rikka.shizuku.Shizuku::class.java
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess", 
                Array<String>::class.java, 
                Array<String>::class.java, 
                String::class.java
            )
            newProcessMethod.isAccessible = true

            // 2. Start sh process natively
            // This returns a ShizukuRemoteProcess, which inherits from java.lang.Process, 
            // so standard .inputStream/.outputStream properties just work.
            val remoteProcess = newProcessMethod.invoke(null, arrayOf("sh"), null, null) as? java.lang.Process
            
            if (remoteProcess == null) {
                Log.e("KeepShell", "CRITICAL: Shizuku newProcess returned null.")
                return@withContext false
            }
            
            process = remoteProcess

            // 3. Obtain standard streams
            val inputStream = remoteProcess.inputStream
            val outputStream = remoteProcess.outputStream
            val errorStream = remoteProcess.errorStream

            reader = BufferedReader(InputStreamReader(inputStream))
            writer = BufferedWriter(OutputStreamWriter(outputStream))

            // Background stderr drain
            Thread {
                try {
                    val errReader = errorStream.bufferedReader()
                    while (true) { errReader.readLine() ?: break }
                } catch (_: Exception) {}
            }.apply { isDaemon = true }.start()

            isRunning.set(true)
            _isServiceAvailable.value = true
            Log.d("KeepShell", "Persistent shell session [READY]")
            true
        } catch (e: Exception) {
            Log.e("KeepShell", "Shell startup failure: ${e.message}")
            isRunning.set(false)
            false
        }
    }



    suspend fun doCmd(command: String, timeoutMs: Long = 5000): String = withContext(Dispatchers.IO) {
        try {
            mLock.withLock {
                if (!isRunning.get() || writer == null || reader == null) {
                    if (!start()) {
                        return@withContext "error: shell not available"
                    }
                }
                val output = StringBuilder()
                
                // Wrap in markers
                writer?.write("\necho '$START_TAG'\n$command\necho '$END_TAG'\n")
                writer?.flush()

                var started = false
                val deadline = System.currentTimeMillis() + timeoutMs
                
                outer@ while (true) {
                    if (reader?.ready() == true) {
                        val line = reader?.readLine() ?: break@outer
                        when {
                            line.contains(START_TAG) -> started = true
                            line.contains(END_TAG)   -> break@outer
                            started -> output.append(line).append("\n")
                        }
                    } else {
                        if (System.currentTimeMillis() > deadline) break@outer
                        delay(10)
                    }
                }
                
                output.toString().trim()
            }
        } catch (e: Exception) {
            Log.e("KeepShell", "doCmd failed on command: $command", e)
            stop()
            "error: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    fun stop() {
        isRunning.set(false)
        _isServiceAvailable.value = false
        try {
            writer?.close()
            reader?.close()
            (process as? java.lang.Process)?.destroy()
        } catch (_: Exception) {}
        writer = null; reader = null; process = null
    }
}
