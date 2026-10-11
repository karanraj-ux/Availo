package com.example.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

object WidgetUpdater {
    fun updateWidgetState(context: Context, mode: String, content: String) {
        val prefs = context.getSharedPreferences("widget_state", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("mode", mode)
            .putString("content", content)
            .apply()

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, MasterWidgetProvider::class.java)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
        
        for (appWidgetId in appWidgetIds) {
            MasterWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
