package com.mustakim.bokbok.data.service.quicktile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.mustakim.bokbok.data.service.ScreenRecordService
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import javax.inject.Inject
import com.mustakim.bokbok.viewmodel.ScreenRecordViewModel
import androidx.activity.viewModels

/**
 * A transparent activity used to request MediaProjection permission when
 * triggered from outside the main UI (e.g., Quick Settings Tile or Widget).
 */
@AndroidEntryPoint
class ProjectionActivity : ComponentActivity() {

    private val viewModel: ScreenRecordViewModel by viewModels()

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Timber.i("MediaProjection permission granted via ProjectionActivity")
            viewModel.startRecording(result.resultCode, result.data!!)
        } else {
            Timber.w("MediaProjection permission denied or cancelled")
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if we should stop or start
        val action = intent.action
        if (action == "STOP") {
            viewModel.stopRecording()
            finish()
            return
        }

        Timber.i("ProjectionActivity created to request permission")
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }
}
