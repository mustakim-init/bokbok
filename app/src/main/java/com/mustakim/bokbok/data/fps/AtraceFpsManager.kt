package com.mustakim.bokbok.data.fps

import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku
import moe.shizuku.server.IShizukuService
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 🚀 PERFORMANCE ENGINE: AtraceFpsManager (Non-Root Edition)
 *
 * Calculates FPS by streaming kernel trace events via Shizuku.
 * This is the same high-accuracy technique used by non-root FPS tools.
 *
 * It monitors 'SurfaceFlinger' events in the 'gfx' category.
 */
@Singleton
class AtraceFpsManager @Inject constructor() {

    private val _fps = MutableStateFlow(0f)
    val fps = _fps.asStateFlow()

    private var job: Job? = null
    private var scope: CoroutineScope? = null
    
    private val TAG = "AtraceFpsManager"

    fun start(externalScope: CoroutineScope) {
        if (job?.isActive == true) return
        scope = externalScope
        job = externalScope.launch(Dispatchers.IO) {
            monitorLoop()
        }
    }

    fun stop() {
        job?.cancel()
        _fps.value = 0f
    }

    private suspend fun monitorLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                if (!Shizuku.pingBinder()) {
                    delay(2000)
                    continue
                }

                val binder = Shizuku.getBinder() ?: break
                val service = IShizukuService.Stub.asInterface(binder)
                
                // 🚀 TRICK: Inject a continuous shell loop that performs async tracing and accurate timestamp math
                val script = """
                    BB="/data/local/tmp/busybox"
                    while true; do
                        window_pkg=${'$'}(${'$'}BB cat /data/local/tmp/bokbok_current_window 2>/dev/null | ${'$'}BB tr -d '\r\n')
                        if [ -z "${'$'}window_pkg" ]; then
                            echo "0"
                            sleep 1
                            continue
                        fi
                        
                        pid=${'$'}(${'$'}BB pidof "${'$'}window_pkg" | ${'$'}BB awk '{print ${'$'}1}')
                        if [ -z "${'$'}pid" ]; then
                            echo "0"
                            sleep 1
                            continue
                        fi
                        
                        atrace --async_start gfx view
                        sleep 1
                        
                        awk_script='
                        BEGIN { first = 0; last = 0; count = 0 }
                        /eglSwapBuffers/ {
                            ts = ${'$'}6
                            sub(":", "", ts)
                            if (ts > 0) {
                                if (first == 0) first = ts
                                last = ts
                                count++
                            }
                        }
                        END {
                            if (count > 2 && last > first) {
                                fps = count / (last - first)
                                printf "%d\n", fps
                            } else {
                                print "0"
                            }
                        }'
                        
                        atrace --async_dump | ${'$'}BB grep -F "B|${'$'}pid|" | ${'$'}BB awk "${'$'}awk_script"
                    done
                """.trimIndent()
                
                Log.d(TAG, "Starting atrace async loop via Shizuku...")
                val remoteProcess = service.newProcess(arrayOf("sh", "-c", script), null, null)
                val pfd = remoteProcess?.inputStream
                
                if (pfd == null) {
                    Log.e(TAG, "Failed to get input stream from Shizuku process")
                    delay(5000)
                    continue
                }

                val reader = BufferedReader(InputStreamReader(ParcelFileDescriptor.AutoCloseInputStream(pfd)))

                // Read the continuous FPS stream from our injected script
                while (currentCoroutineContext().isActive) {
                    val line = withContext(Dispatchers.IO) {
                        try { reader.readLine() } catch (e: Exception) { null }
                    } ?: break
                    
                    val parsedFps = line.trim().toFloatOrNull()
                    if (parsedFps != null) {
                        _fps.value = parsedFps.coerceIn(0f, 240f)
                    }
                }
                
                remoteProcess.destroy()
                Log.w(TAG, "Atrace stream disconnected, restarting in 2s...")
                delay(2000)
            } catch (e: Exception) {
                Log.e(TAG, "Atrace monitoring error", e)
                delay(5000)
            }
        }
    }
}
