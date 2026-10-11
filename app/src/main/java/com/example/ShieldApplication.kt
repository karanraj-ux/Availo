package com.example

import android.app.Application
import com.example.di.AppContainer
import com.example.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ShieldApplication : Application() {
    lateinit var container: AppContainer
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        container = DefaultAppContainer(this)
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val name = "VIP Call Alerts"
            val descriptionText = "Notifications for VIP DND bypass calls"
            val importance = android.app.NotificationManager.IMPORTANCE_HIGH
            val channel = android.app.NotificationChannel("vip_calls", name, importance).apply {
                description = descriptionText
                setBypassDnd(true)
            }
            val notificationManager: android.app.NotificationManager =
                getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
            val generalChannel = android.app.NotificationChannel("general", "General Notifications", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(generalChannel)
        }
    }
}
