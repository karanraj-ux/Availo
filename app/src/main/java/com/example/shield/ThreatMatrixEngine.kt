package com.example.shield

import android.content.Context
import android.content.Intent
import android.service.notification.StatusBarNotification
import android.telephony.SmsManager
import android.util.Log

enum class ThreatType {
    SAFE, UPI_FRAUD, PHISHING
}

object ThreatMatrixEngine {
    private var activeCallNumber: String? = null
    private var callStartTime: Long = 0L
    private var isCallActive: Boolean = false

    fun onCallAnswered(context: Context, number: String) {
        val isContact = com.example.calls.CallHandlingManager.isNumberInContacts(context, number)
        if (isContact) {
            Log.d("ThreatMatrixEngine", "Call answered from saved contact. Safe.")
            return
        }
        
        Log.d("ThreatMatrixEngine", "Unsaved call answered. Monitoring for threats.")
        activeCallNumber = number
        callStartTime = System.currentTimeMillis()
        isCallActive = true
    }
    
    fun onWhatsAppCallStarted(context: Context, callerInfo: String) {
        // Scammers use WhatsApp VoIP. Treat as active unsaved call for threat monitoring.
        Log.d("ThreatMatrixEngine", "WhatsApp Call detected from: $callerInfo. Monitoring for threats.")
        activeCallNumber = callerInfo
        callStartTime = System.currentTimeMillis()
        isCallActive = true
    }

    fun onCallEnded() {
        activeCallNumber = null
        callStartTime = 0L
        isCallActive = false
        Log.d("ThreatMatrixEngine", "Call ended. Threat monitoring paused.")
    }

    fun evaluateMessageThreat(body: String): ThreatType {
        val lowerBody = body.lowercase()
        
        // 1. Phishing Matrix
        val hasUrl = listOf("http://", "https://", "bit.ly", "ngrok", "wa.me", "t.me", ".apk", "tinyurl").any { lowerBody.contains(it) }
        val urgencyKeywords = listOf(
            "kyc", "account blocked", "sim blocked", "card suspended", "electricity bill", "pan card",
            "disconnect power", "update immediately", "lottery winner", "claim prize", "action required",
            "bank alert", "debit card blocked", "netbanking expired"
        )
        val hasUrgency = urgencyKeywords.any { lowerBody.contains(it) }
        
        if (hasUrl && hasUrgency) return ThreatType.PHISHING

        // 2. Reverse UPI Fraud Matrix
        val hasMoneyKeywords = listOf("receive", "claim", "cashback", "refund", "won")
        val hasUpiKeywords = listOf("upi pin", "enter pin", "upi id")
        
        if (hasMoneyKeywords.any { lowerBody.contains(it) } && hasUpiKeywords.any { lowerBody.contains(it) }) {
            return ThreatType.UPI_FRAUD
        }

        return ThreatType.SAFE
    }

    fun onSmsReceived(context: Context, sender: String, body: String): Boolean {
        // Do not ghost-wipe or alert on SMS; return false to allow normal processing
        return false
    }
    
    fun checkNotificationForThreat(context: Context, title: String, text: String, packageName: String): Boolean {
        return false
    }

    private fun handleAutomatedDefense(context: Context, sender: String, threatType: ThreatType) {
        // No-op: keep completely silent
    }

    private fun triggerIntervention(context: Context) {
        // No-op: removed scam/threat intervention popups
    }
}
