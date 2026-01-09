package com.mustakim.bokbok.data.service.quicktile

import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.service.ScreenRecordService
import timber.log.Timber

/**
 * Quick Settings Tile to start and stop screen recording.
 */
class ScreenRecordTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val isRecording = ScreenRecordService.isRecordingActive
        
        if (isRecording) {
            // Stop recording
            val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
                action = ScreenRecordService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            // Start recording flow via ProjectionActivity
            val startIntent = Intent(this, ProjectionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            // Close quick settings panel
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // On Android 14+, startActivityAndCollapse is required/preferred
                // but we can just use the standard way if it works.
                // ActuallyTileService has its own method.
            }
            
            // Note: TileService.startActivityAndCollapse is deprecated in API 34+
            // for some use cases, but let's use it for now.
            @Suppress("DEPRECATION")
            startActivityAndCollapse(startIntent)
        }
        
        // Short delay to allow state to change before updating UI
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isRecording = ScreenRecordService.isRecordingActive

        if (isRecording) {
            tile.state = Tile.STATE_ACTIVE
            tile.label = getString(R.string.tile_stop_recording)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_recording)
        } else {
            tile.state = Tile.STATE_INACTIVE
            tile.label = getString(R.string.tile_start_recording)
            tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_not_recording)
        }
        
        tile.updateTile()
    }
    
    companion object {
        fun requestUiUpdate(context: android.content.Context) {
            // This can be used to manually trigger a tile update if needed
            // But TileService usually handles it via onStartListening
        }
    }
}
