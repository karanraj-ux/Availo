package com.example.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.R
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TogglesWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
        
        CoroutineScope(Dispatchers.IO).launch {
            when (action) {
                ACTION_TOGGLE_MASTER -> {
                    val current = settingsRepo.getBooleanSync(SettingsRepository.MASTER_KILL_SWITCH, false)
                    settingsRepo.updateBoolean(SettingsRepository.MASTER_KILL_SWITCH, !current)
                }
                ACTION_TOGGLE_GHOST -> {
                    val current = settingsRepo.getBooleanSync(SettingsRepository.GHOST_MODE, false)
                    settingsRepo.updateBoolean(SettingsRepository.GHOST_MODE, !current)
                }
                ACTION_TOGGLE_PAUSE -> {
                    val pauseEndTime = System.currentTimeMillis() + (60 * 60 * 1000L)
                    settingsRepo.updateLong(SettingsRepository.GHOST_MODE_PAUSE_END_TIME, pauseEndTime)
                }
                ACTION_TOGGLE_DND -> {
                    val current = settingsRepo.getBooleanSync(SettingsRepository.OVERRIDE_DND, false)
                    settingsRepo.updateBoolean(SettingsRepository.OVERRIDE_DND, !current)
                }
            }
            
            // Update widgets after state change
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, TogglesWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_MASTER = "com.example.widget.ACTION_TOGGLE_MASTER"
        const val ACTION_TOGGLE_GHOST = "com.example.widget.ACTION_TOGGLE_GHOST"
        const val ACTION_TOGGLE_PAUSE = "com.example.widget.ACTION_TOGGLE_PAUSE"
        const val ACTION_TOGGLE_DND = "com.example.widget.ACTION_TOGGLE_DND"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
            val isMasterKillOn = settingsRepo.getBooleanSync(SettingsRepository.MASTER_KILL_SWITCH, false)
            val isGhostOn = settingsRepo.getBooleanSync(SettingsRepository.GHOST_MODE, false)
            val pauseEndTime = settingsRepo.getLongSync(SettingsRepository.GHOST_MODE_PAUSE_END_TIME, 0L)
            val isPaused = System.currentTimeMillis() < pauseEndTime
            val isDndOn = settingsRepo.getBooleanSync(SettingsRepository.OVERRIDE_DND, false)

            val views = RemoteViews(context.packageName, R.layout.widget_toggles)

            // Setup colors based on state
            val activeColor = android.graphics.Color.parseColor("#4CAF50")
            val inactiveColor = android.graphics.Color.parseColor("#80000000")
            val masterColor = if (isMasterKillOn) android.graphics.Color.parseColor("#E53935") else inactiveColor

            views.setInt(R.id.btn_master, "setBackgroundColor", masterColor)
            views.setInt(R.id.btn_ghost, "setBackgroundColor", if (isGhostOn) activeColor else inactiveColor)
            views.setInt(R.id.btn_pause, "setBackgroundColor", if (isPaused) activeColor else inactiveColor)
            views.setInt(R.id.btn_dnd, "setBackgroundColor", if (isDndOn) activeColor else inactiveColor)

            // Set text for Master
            views.setTextViewText(R.id.btn_master, if (isMasterKillOn) "App KILLED" else "Kill Switch")

            // Setup intents
            views.setOnClickPendingIntent(R.id.btn_master, getPendingIntent(context, ACTION_TOGGLE_MASTER, 1))
            views.setOnClickPendingIntent(R.id.btn_ghost, getPendingIntent(context, ACTION_TOGGLE_GHOST, 2))
            views.setOnClickPendingIntent(R.id.btn_pause, getPendingIntent(context, ACTION_TOGGLE_PAUSE, 3))
            views.setOnClickPendingIntent(R.id.btn_dnd, getPendingIntent(context, ACTION_TOGGLE_DND, 4))

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun getPendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, TogglesWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
