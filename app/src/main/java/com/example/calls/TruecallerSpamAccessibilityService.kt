package com.example.calls

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.telecom.TelecomManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.ShieldApplication
import com.example.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TruecallerSpamAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Fast path: Only trigger if Smart Spam Reader is enabled
        val settingsRepo = (applicationContext as ShieldApplication).container.settingsRepository
        val smartSpamEnabled = settingsRepo.getBooleanSync(SettingsRepository.SMART_SPAM_READER, false)
        
        if (!smartSpamEnabled) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val rootNode = rootInActiveWindow ?: return
            
            if (event.packageName?.toString()?.contains("truecaller") == true) {
                if (checkForSpamIndicators(rootNode)) {
                    Log.d("TruecallerHack", "Spam signature (Red UI/Spam text) detected! Firing endCall().")
                    rejectCall()
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { 
                        settingsRepo.incrementSpamBlockedCount() 
                        try {
                            val appDb = (applicationContext as com.example.ShieldApplication).container.database
                            appDb.smsLogDao().insert(com.example.data.SmsLogEntity(
                                timestamp = System.currentTimeMillis(),
                                sender = "Unknown Truecaller Caller",
                                message = "Spam Call Blocked (Truecaller)",
                                targetNumber = "",
                                status = "SPAM_BLOCKED"
                            ))
                        } catch (e: Exception) {
                            Log.e("TruecallerHack", "Failed to log spam block", e)
                        }
                    }
                }
            }
        }
    }

    private fun checkForSpamIndicators(node: AccessibilityNodeInfo): Boolean {
        // Specifically look for Truecaller's signature Red UI Color resource IDs or text
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        
        // Truecaller typically uses specific resource names for spam banners (e.g., solid_red_background, spam_color)
        val hasRedSpamBackground = viewId.contains("spam") || viewId.contains("red") || viewId.contains("alert")
        
        var hasSpamText = false
        if (node.text != null) {
            val text = node.text.toString().lowercase()
            if (text.contains("spam") || text.contains("spammer")) {
                hasSpamText = true
            }
        }
        
        if (node.contentDescription != null) {
            val desc = node.contentDescription.toString().lowercase()
            if (desc.contains("spam")) hasSpamText = true
        }

        // We check if either the red UI container matches OR it explicitly says spam
        if (hasRedSpamBackground || hasSpamText) {
            return true
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (checkForSpamIndicators(child)) {
                    @Suppress("DEPRECATION")
                    child.recycle()
                    return true
                }
                @Suppress("DEPRECATION")
                child.recycle()
            }
        }
        return false
    }

    private fun rejectCall() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                @Suppress("DEPRECATION") telecomManager.endCall()
                Log.d("TruecallerHack", "Call successfully rejected via TelecomManager.")
            } else {
                Log.e("TruecallerHack", "Missing ANSWER_PHONE_CALLS permission.")
            }
        } catch (e: Exception) {
            Log.e("TruecallerHack", "Failed to reject call", e)
        }
    }

    override fun onInterrupt() {
        // Do nothing
    }
}
