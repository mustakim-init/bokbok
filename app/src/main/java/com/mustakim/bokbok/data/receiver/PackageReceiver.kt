package com.mustakim.bokbok.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mustakim.bokbok.data.repository.GameRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PackageReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: GameRepository

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_PACKAGE_ADDED || 
            action == Intent.ACTION_PACKAGE_REMOVED || 
            action == Intent.ACTION_PACKAGE_REPLACED) {
            
            android.util.Log.d("PackageReceiver", "Package change detected: $action. Invalidating cache.")
            repository.invalidateCache()
        }
    }
}
