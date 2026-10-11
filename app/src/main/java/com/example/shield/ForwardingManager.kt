package com.example.shield

import android.content.Context
import android.util.Log
import kotlinx.coroutines.launch

object ForwardingManager {

    fun forwardMessage(
        context: Context,
        title: String,
        message: String,
        type: String
    ) {
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
            
            // Broadcast Intent for Tasker / MacroDroid / other automation tools
            val allowAutomation = settingsRepo.getBooleanSync(androidx.datastore.preferences.core.booleanPreferencesKey("allow_external_automation"), false)
            if (allowAutomation) {
                try {
                    val intent = android.content.Intent("com.example.shield.RULE_TRIGGERED")
                    intent.putExtra("type", type)
                    intent.putExtra("title", title)
                    intent.putExtra("message", message)
                    intent.putExtra("rule_name", type)
                    context.sendBroadcast(intent)
                    Log.d("ForwardingManager", "Broadcasted automation intent: com.example.shield.RULE_TRIGGERED")
                } catch (e: Exception) {
                    Log.e("ForwardingManager", "Failed to broadcast automation intent", e)
                }
            }
        }
    }
}
