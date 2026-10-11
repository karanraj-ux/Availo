package com.example

import android.content.Context
import android.util.Log
import com.example.data.AppDatabase
import com.example.data.SmsLogEntity
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ProcessingResult(val status: String, val durationMs: Long)

object SmsProcessor {

    suspend fun processReceivedMessage(context: Context, sender: String, body: String, slotIndex: Int = -1, timestamp: Long = System.currentTimeMillis(), isSimulation: Boolean = false): ProcessingResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        try {
        val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
        
        val isKillSwitchOn = settingsRepo.getBooleanSync(androidx.datastore.preferences.core.booleanPreferencesKey("master_kill_switch"), false)
        
        if (isKillSwitchOn) {
            return@withContext ProcessingResult("KILLED_BY_MASTER_SWITCH", System.currentTimeMillis() - startTime)
        }
        
                // Phase 3: Emergency Protocol - Keyword Alarm
        val tier = com.example.calls.CallHandlingManager.getRelationshipTier(context, sender)
        if (body.contains("URGENT") && tier == "Inner Circle") {
            Log.d("SmsProcessor", "URGENT keyword detected from $sender. Triggering wake-up alarm.")
            try {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
                audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
                
                val uri = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM) 
                    ?: android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE)
                val ringtone = android.media.RingtoneManager.getRingtone(context, uri)
                ringtone.audioAttributes = android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                ringtone.play()
                
                kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                    delay(15000) // Play for 15 seconds
                    ringtone.stop()
                }
                
                // Also send a high priority notification
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val notif = androidx.core.app.NotificationCompat.Builder(context, "general")
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Emergency URGENT SMS")
                    .setContentText("From $sender: $body")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                    .setAutoCancel(true)
                    .build()
                nm.notify(sender.hashCode(), notif)
            } catch (e: Exception) {
                Log.e("SmsProcessor", "Failed to play urgent alarm", e)
            }
        }
        
        // Auto-pause ghost mode for 1 hour if a delivery/service SMS is received while Ghost Mode is active
        val lowerBody = body.lowercase()
        val isServiceMsg = sender.length <= 8 || sender.contains("-") || sender.any { it.isLetter() }
        val hasDeliveryKeyword = listOf("delivery", "out for delivery", "arriving today", "arriving now", "arrived", "zomato", "swiggy", "uber", "driver").any { lowerBody.contains(it) }
        val isGhostModeActive = settingsRepo.getBooleanSync(SettingsRepository.GHOST_MODE, false)
        
        if (isGhostModeActive && isServiceMsg && hasDeliveryKeyword && !isSimulation) {
            val oneHourMs = 60 * 60 * 1000L
            val pauseEndTime = System.currentTimeMillis() + oneHourMs
            settingsRepo.updateLong(SettingsRepository.GHOST_MODE_PAUSE_END_TIME, pauseEndTime)
            Log.d("SmsProcessor", "Delivery/Service SMS detected. Paused Ghost Mode for 1 hour.")

            // Notify user that ghost mode was paused so they are not surprised
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val notif = androidx.core.app.NotificationCompat.Builder(context, "general")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Ghost Mode Paused (60m)")
                    .setContentText("Incoming delivery from $sender. Incoming calls will ring for 1 hour.")
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .build()
                nm.notify(8888, notif)
            } catch (e: Exception) {
                Log.e("SmsProcessor", "Failed to post pause notification", e)
            }
        }

        // Old DB-based custom rules
        val customRules = (context.applicationContext as com.example.ShieldApplication).container.ruleRepository.getAllRulesSync()
        
        for (rule in customRules) {
            val trigger = rule.trigger
            val action = rule.action
            if (body.contains(trigger, ignoreCase = true) || sender.contains(trigger, ignoreCase = true)) {
                Log.d("SmsProcessor", "Custom Rule triggered: trigger -> action")
                if (action.contains("Tasker", ignoreCase = true) || action.contains("MacroDroid", ignoreCase = true)) {
                    com.example.shield.ForwardingManager.forwardMessage(context, sender, "[$trigger triggered] $body", "CUSTOM_RULE")
                }
            }
        }

        // Phase 4: Handle Auto Responder for SMS
        com.example.shield.AutoResponder.handleIncomingSms(context, sender, body)
        
        // Phase 5: Telecom SMS Call-Scheduling
        val telecomKeywords = listOf("is now available", "missed call from")
        if (telecomKeywords.any { body.contains(it, ignoreCase = true) }) {
            // Extract number (basic regex for Indian/international numbers)
            val matcher = java.util.regex.Pattern.compile("\\+?\\d{10,14}").matcher(body)
            if (matcher.find()) {
                val availableNumber = matcher.group()
                android.util.Log.d("SmsProcessor", "Telecom notification detected for availableNumber. Preparing scheduled call.")
                
                try {
                    val callIntent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
                        action = "com.example.ACTION_DIAL_NUMBER"
                        putExtra("DIAL_NUMBER", availableNumber)
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        context,
                        availableNumber.hashCode(),
                        callIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    val notif = androidx.core.app.NotificationCompat.Builder(context, "general")
                        .setSmallIcon(android.R.drawable.ic_menu_call)
                        .setContentTitle("Contact Available")
                        .setContentText("$availableNumber is available. Tap to call.")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                        .addAction(android.R.drawable.ic_menu_call, "Call Now", pendingIntent)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .build()
                    nm.notify(availableNumber.hashCode(), notif)
                } catch (e: Exception) {
                    android.util.Log.e("SmsProcessor", "Failed to show call notification", e)
                }
            }
        }
        
        val endTime = System.currentTimeMillis()
        val durationMs = Math.max(0L, endTime - startTime)

        return@withContext ProcessingResult("PROCESSED_LOCALLY", durationMs)
        } catch (e: Exception) {
            Log.e("SmsProcessor", "Crash prevented in SMS processing", e)
            ProcessingResult("CRASH_PREVENTED", System.currentTimeMillis() - startTime)
        }
    }
}
