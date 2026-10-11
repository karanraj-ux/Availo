package com.example.shield

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object GuardianProtocol {
    val GUARDIAN_NUMBER_KEY = stringPreferencesKey("guardian_number")
    val GUARDIAN_NAME_KEY = stringPreferencesKey("guardian_name")

    fun alertGuardian(context: Context, scammerNumber: String, reason: String) {
        val app = context.applicationContext as? com.example.ShieldApplication
        val appScope = app?.applicationScope ?: CoroutineScope(Dispatchers.IO)
        val settingsRepo = app?.container?.settingsRepository ?: SettingsRepository(context)

        appScope.launch {
            val guardianNum = settingsRepo.getStringSync(GUARDIAN_NUMBER_KEY, "")
            if (guardianNum.isNotEmpty()) {
                val message = """🚨 SHIELD ALERT: Threat Score > 85.
• Trigger: Long call with Unsaved Number ($scammerNumber).
• Event: $reason
• Action: Call immediately to verify. Potential scam.""".trimIndent()
                sendSms(context, guardianNum, message)
            } else {
                Log.d("GuardianProtocol", "No Guardian configured. Skipping SMS alert.")
            }
        }
    }
    
    fun alertThreatBlocked(context: Context, threatType: String, sender: String) {
        val app = context.applicationContext as? com.example.ShieldApplication
        val appScope = app?.applicationScope ?: CoroutineScope(Dispatchers.IO)
        val settingsRepo = app?.container?.settingsRepository ?: SettingsRepository(context)

        appScope.launch {
            val guardianNum = settingsRepo.getStringSync(GUARDIAN_NUMBER_KEY, "")
            if (guardianNum.isNotEmpty()) {
                val message = "🛡️ SHIELD UPDATE: A high-risk $threatType attempt from $sender was automatically blocked and hidden from the device."
                sendSms(context, guardianNum, message)
            }
        }
    }
    
    private fun sendSms(context: Context, targetNum: String, message: String) {
        try {
            val sm: SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            
            val parts = sm.divideMessage(message)
            if (parts.size > 1) {
                sm.sendMultipartTextMessage(targetNum, null, parts, null, null)
            } else {
                sm.sendTextMessage(targetNum, null, message, null, null)
            }
            Log.d("GuardianProtocol", "Sent alert to: $targetNum")
        } catch (e: Exception) {
            Log.e("GuardianProtocol", "Failed to send SMS", e)
        }
    }
}
