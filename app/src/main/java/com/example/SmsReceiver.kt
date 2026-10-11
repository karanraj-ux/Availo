package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECEIVE_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return
        }

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            // 1. Acquire WakeLock to prevent 1-minute Doze delay
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Shield:SmsReceiverWakeLock")
            wakeLock.acquire(15000L) // Hold for 15 seconds max

            val pendingResult = goAsync()
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            
            (context.applicationContext as com.example.ShieldApplication).applicationScope.launch(Dispatchers.IO) {
                try {
                    val sender = messages.firstOrNull()?.displayOriginatingAddress ?: ""
                    val body = messages.joinToString("") { it.displayMessageBody ?: "" }
                    
                    val subscription = intent.getIntExtra("subscription", -1)
                    val slot = intent.getIntExtra("slot", -1)
                    val phone = intent.getIntExtra("phone", -1)
                    val slotIndex = if (slot != -1) slot else if (phone != -1) phone else subscription
                    
                    if (sender.isNotEmpty() && body.isNotEmpty()) {
                        SmsProcessor.processReceivedMessage(context, sender, body, slotIndex)
                    }
                } finally {
                    pendingResult.finish()
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            }
        }
    }
}
