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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            scope.launch {
                if (repository.hasActiveSnapshots()) {
                    android.util.Log.d("BootReceiver", "Reverting optimizations after boot...")
                    repository.revertAllOptimizations()
                }
            }
        }
    }
}
