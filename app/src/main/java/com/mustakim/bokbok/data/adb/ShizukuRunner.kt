package com.mustakim.bokbok.data.adb

import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Utility to run shell commands via Shizuku.
 */
object ShizukuRunner {

    suspend fun command(command: String): String = withContext(Dispatchers.IO) {
        val binder = Shizuku.getBinder() ?: throw Exception("Shizuku not running")
        val service = IShizukuService.Stub.asInterface(binder)
        
        val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
        val reader = BufferedReader(InputStreamReader(FileInputStream(process.inputStream.fileDescriptor)))
        val errorReader = BufferedReader(InputStreamReader(FileInputStream(process.errorStream.fileDescriptor)))
        
        val output = StringBuilder()
        val error = StringBuilder()
        
        reader.forEachLine { output.append(it).append("\n") }
        errorReader.forEachLine { error.append(it).append("\n") }
        
        val exitCode = process.waitFor()
        
        // Log stderr as warning instead of crashing — many valid commands write to stderr
        if (error.isNotEmpty()) {
            android.util.Log.w("ShizukuRunner", "stderr for '$command': ${error.toString().take(200)}")
        }
        
        // Only throw if command truly failed (non-zero exit) AND produced no useful output
        if (exitCode != 0 && output.isEmpty()) {
            throw Exception("Command failed (exit $exitCode): ${error.toString().take(500)}")
        }
        
        output.toString().trim()
    }
}
