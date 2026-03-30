package com.mustakim.bokbok.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mustakim.bokbok.data.repository.GameRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: GameRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            // Use goAsync() to keep the receiver alive until our coroutine completes
            val pendingResult = goAsync()
            
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                try {
                    // 1. Revert optimizations if needed
                    if (repository.hasActiveSnapshots()) {
                        android.util.Log.d("BootReceiver", "Reverting optimizations after boot...")
                        repository.revertAllOptimizations()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("BootReceiver", "Failed to revert optimizations", e)
                } finally {
                    pendingResult.finish()
                }
            }

            // 2. Trigger Resurrection flow (this is the ONLY place that enqueues resurrection on boot)
            // GameWatchdogService intentionally delays 30s before its first check to avoid racing with this.
            android.util.Log.d("BootReceiver", "Triggering AdbResurrectionWorker...")
            com.mustakim.bokbok.data.worker.AdbResurrectionWorker.enqueue(context)
            
            // 3. Start Sentinel Service
            com.mustakim.bokbok.data.service.GameWatchdogService.start(context)
        }
    }
}
