package com.mustakim.bokbok.util

import com.mustakim.bokbok.data.service.BokBokAgentService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuRemoteProcess
import java.io.BufferedReader

object ShizukuUtils {

    suspend fun isShizukuAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
    }

    suspend fun enableAccessibilityService(): Boolean = withContext(Dispatchers.IO) {
        if (!isShizukuAvailable()) return@withContext false
        
        val serviceId = BokBokAgentService.SERVICE_ID
        // Step 1: Grant WRITE_SECURE_SETTINGS just in case
        executeCommand("pm grant com.mustakim.bokbok android.permission.WRITE_SECURE_SETTINGS")
        
        // Step 2: Enable the physical service
        executeCommand("settings put secure enabled_accessibility_services $serviceId")
        executeCommand("settings put secure accessibility_enabled 1")
        
        // Return true if the service instance is now active
        BokBokAgentService.getInstance() != null
    }

    private suspend fun executeCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            val binder = rikka.shizuku.Shizuku.getBinder()
            if (binder != null) {
                val service = moe.shizuku.server.IShizukuService.Stub.asInterface(binder)
                val process = service.newProcess(arrayOf("sh", "-c", command), null, null)
                
                if (process != null) {
                    val getInputStream = process.javaClass.getMethod("getInputStream")
                    val pfd = getInputStream.invoke(process) as? android.os.ParcelFileDescriptor
                    
                    val output = pfd?.let { fd ->
                        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { 
                            it.readText() 
                        }
                    } ?: ""
                    
                    val waitFor = process.javaClass.getMethod("waitFor")
                    waitFor.invoke(process)
                    
                    return@withContext output
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShizukuUtils", "Command failed: $command", e)
        }
        ""
    }
}
