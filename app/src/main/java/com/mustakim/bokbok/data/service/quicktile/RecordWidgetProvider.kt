package com.mustakim.bokbok.data.service.quicktile

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.mustakim.bokbok.R
import com.mustakim.bokbok.data.service.ScreenRecordService

/**
 * Basic 1x1 widget to start/stop screen recording.
 */
class RecordWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == "TOGGLE_RECORDING") {
            val isRecording = ScreenRecordService.isRecordingActive
            if (isRecording) {
                // Stop recording
                val stopIntent = Intent(context, ScreenRecordService::class.java).apply {
                    action = ScreenRecordService.ACTION_STOP
                }
                context.startService(stopIntent)
            } else {
                // Start recording flow
                val startIntent = Intent(context, ProjectionActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(startIntent)
            }
        }
    }

    companion object {
        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val isRecording = ScreenRecordService.isRecordingActive
            val views = RemoteViews(context.packageName, R.layout.widget_record)

            // Update Icon
            val iconRes = if (isRecording) R.drawable.ic_tile_recording else R.drawable.ic_tile_not_recording
            views.setImageViewResource(R.id.widget_button, iconRes)

            // Set Click Intent
            val intent = Intent(context, RecordWidgetProvider::class.java).apply {
                action = "TOGGLE_RECORDING"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RecordWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }
}
