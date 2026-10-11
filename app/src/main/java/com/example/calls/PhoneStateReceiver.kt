package com.example.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PhoneStateReceiver : BroadcastReceiver() {
    companion object {
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var isIncoming = false
        private var incomingNumber: String? = null
        
        var lastOutgoingNumber: String? = null
        var lastOffhookTime: Long = 0L
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
        @Suppress("DEPRECATION") if (intent.action == Intent.ACTION_NEW_OUTGOING_CALL) {
            lastOutgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
            isIncoming = false
        }
        
        if (intent.action == TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            @Suppress("DEPRECATION") val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            
            Log.d("PhoneStateReceiver", "State: $state, Number: $number")

            if (state == TelephonyManager.EXTRA_STATE_RINGING) {
                isIncoming = true
                incomingNumber = number
                lastState = state
                
                // VIP Divert & DND Bypass logic
                number?.let {
                    val pendingResult = goAsync()
                    (context.applicationContext as com.example.ShieldApplication).applicationScope.launch(Dispatchers.IO) {
                        try {
                            CallHandlingManager.handleIncomingCall(context, it)
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            } else if (state == TelephonyManager.EXTRA_STATE_OFFHOOK) {
                lastOffhookTime = System.currentTimeMillis()
                if (lastState != TelephonyManager.EXTRA_STATE_RINGING) {
                    isIncoming = false
                } else {
                    // Call was answered
                    incomingNumber?.let {
                        CallHandlingManager.handleCallAnswered(context, it)
                    }
                }
                lastState = state
            } else if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING) {
                    // Missed call detected! Fallback to CallLog if incomingNumber was null (Android 9+)
                    val missedNumber = incomingNumber ?: getLatestMissedCallNumber(context)
                    Log.d("PhoneStateReceiver", "Missed call from: $missedNumber")
                    missedNumber?.let {
                        val pendingResult = goAsync()
                        (context.applicationContext as com.example.ShieldApplication).applicationScope.launch(Dispatchers.IO) {
                            try {
                                CallHandlingManager.handleMissedCall(context, it)
                            } finally {
                                pendingResult.finish()
                            }
                        }
                    }
                } else if (lastState == TelephonyManager.EXTRA_STATE_OFFHOOK && !isIncoming) {
                    val duration = System.currentTimeMillis() - lastOffhookTime
                    if (duration < 5000) { // under 5 seconds (busy/dropped)
                        lastOutgoingNumber?.let { num ->
                            Log.d("PhoneStateReceiver", "Short outgoing call detected: $duration ms to $num")
                            val retryIntent = Intent("com.example.ACTION_SHOW_RETRY_SHEET")
                            retryIntent.putExtra("number", num)
                            retryIntent.setPackage(context.packageName)
                            context.sendBroadcast(retryIntent)
                        }
                    }
                }
                
                isIncoming = false
                incomingNumber = null
                lastState = state
            }
        }
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Crash prevented in Phone State", e)
        }
    }

    private fun getLatestMissedCallNumber(context: Context): String? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_CALL_LOG
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }
        return try {
            val cursor = context.contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(android.provider.CallLog.Calls.NUMBER, android.provider.CallLog.Calls.DATE, android.provider.CallLog.Calls.TYPE),
                null,
                null,
                "${android.provider.CallLog.Calls.DATE} DESC LIMIT 5"
            )
            cursor?.use {
                while (it.moveToNext()) {
                    val date = it.getLong(1)
                    val type = it.getInt(2)
                    if (System.currentTimeMillis() - date < 60000 && 
                        (type == android.provider.CallLog.Calls.MISSED_TYPE || type == android.provider.CallLog.Calls.REJECTED_TYPE)) {
                        return it.getString(0)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Error querying CallLog fallback", e)
            null
        }
    }
}
