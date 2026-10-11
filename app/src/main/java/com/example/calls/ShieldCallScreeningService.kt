package com.example.calls

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ShieldApplication
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.runBlocking
import com.example.data.SmsLogEntity

class ShieldCallScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart
        Log.d("ShieldCallScreening", "Incoming call from: $number")
        if (number == null) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            handleCall(number, callDetails)
        }
    }

    private suspend fun handleCall(number: String, callDetails: Call.Details) {
        val context = applicationContext
        val settingsRepo = (context as ShieldApplication).container.settingsRepository
        
        val isKillSwitchOn = settingsRepo.getBooleanSync(androidx.datastore.preferences.core.booleanPreferencesKey("master_kill_switch"), false)
        if (isKillSwitchOn) {
            Log.d("ShieldCallScreening", "App is in Dumb State. Allowing call.")
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val tier = CallHandlingManager.getRelationshipTier(context, number)
        val isVip = (tier == "Inner Circle")
        
        val ghostModeBase = settingsRepo.getBooleanSync(SettingsRepository.GHOST_MODE, false)
        val ghostModePauseEndTime = settingsRepo.getLongSync(SettingsRepository.GHOST_MODE_PAUSE_END_TIME, 0L)
        val ghostMode = ghostModeBase && System.currentTimeMillis() > ghostModePauseEndTime

        var responseBuilder = CallResponse.Builder()
        var rejected = false
        var silenced = false

        if (tier == "Blocked") {
            responseBuilder = responseBuilder.setDisallowCall(true).setRejectCall(true).setSkipCallLog(false).setSkipNotification(true)
            rejected = true
            logCall(number, "Blocked Caller", "BLOCKED_CALL")
        } else if (ghostMode && !isVip) {
            responseBuilder = responseBuilder.setDisallowCall(true).setRejectCall(true).setSkipCallLog(false).setSkipNotification(true)
            rejected = true
            settingsRepo.incrementSpamBlockedCount()
            logCall(number, "Blocked by Ghost Mode", "SPAM_BLOCKED")
            
            // Hand off to AutoResponder (it handles rate limiting, checking if SMS reply is enabled, etc.)
            com.example.shield.AutoResponder.handleMissedCall(applicationContext, number, "Unknown")
        } else if (tier == "Muted") {
            responseBuilder = responseBuilder.setSilenceCall(true)
            silenced = true
            logCall(number, "Muted Caller", "MUTED_CALL")
        }

        respondToCall(callDetails, responseBuilder.build())

        // Handle VIP audio hijack independently
        if (!rejected && !silenced && isVip) {
            CallHandlingManager.handleIncomingCall(context, number) // Trigger the volume bypass
        }
    }

    private suspend fun logCall(number: String, message: String, status: String) {
        try {
            val appDb = (applicationContext as ShieldApplication).container.database
            appDb.smsLogDao().insert(SmsLogEntity(
                timestamp = System.currentTimeMillis(),
                sender = number,
                message = message,
                targetNumber = "",
                status = status
            ))
        } catch (e: Exception) {
            Log.e("ShieldCallScreening", "Failed to log call", e)
        }
    }
    
}
