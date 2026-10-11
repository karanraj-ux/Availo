package com.example.calls

import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
import android.util.Log
import com.example.ShieldApplication
import com.example.data.repository.SettingsRepository
import com.example.data.repository.normalizePhoneDigits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class TruecallerNotificationListenerService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getString(android.app.Notification.EXTRA_TEXT) ?: ""
        val packageName = sbn.packageName ?: ""

        // WhatsApp Call Detection & DND Handling + Chat Auto-Reply
        if (packageName.contains("whatsapp", ignoreCase = true)) {
            if (isWhatsAppCall(sbn)) {
                handleWhatsAppCall(sbn, title)
            } else {
                handleWhatsAppMessage(sbn, title, text)
            }
        }

        // Check if Smart Spam Reader is enabled
        val settingsRepo = (applicationContext as ShieldApplication).container.settingsRepository
        val smartSpamEnabled = settingsRepo.getBooleanSync(SettingsRepository.SMART_SPAM_READER, false)
        
        if (!smartSpamEnabled) return

        if (packageName.contains("truecaller", ignoreCase = true)) {
            val lowerTitle = title.lowercase()
            val lowerText = text.lowercase()
            
            Log.d("TruecallerNL", "Truecaller notification posted: Title=$title, Text=$text")
            val isSpam = lowerTitle.contains("spam") || lowerText.contains("spam") || lowerTitle.contains("spammer") || lowerText.contains("spammer")
            
            if (isSpam) {
                Log.d("TruecallerNL", "Spam signature detected via Notification Listener! Firing endCall().")
                rejectCall(settingsRepo)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val packageName = sbn.packageName ?: ""
        if (packageName.contains("whatsapp", ignoreCase = true) && isWhatsAppCall(sbn)) {
            CallHandlingManager.restoreAudioState(this)
        }
    }

    private fun isWhatsAppCall(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        if (notification.category == android.app.Notification.CATEGORY_CALL) return true

        val extras = notification.extras
        val text = (extras.getString(android.app.Notification.EXTRA_TEXT) ?: "").lowercase()
        val title = (extras.getString(android.app.Notification.EXTRA_TITLE) ?: "").lowercase()
        val subText = (extras.getString(android.app.Notification.EXTRA_SUB_TEXT) ?: "").lowercase()

        val callKeywords = listOf("call", "incoming", "ringing", "audio", "video", "calling")
        val matchesKeyword = callKeywords.any { text.contains(it) || title.contains(it) || subText.contains(it) }

        val actions = notification.actions
        val hasCallActions = actions != null && actions.any { action ->
            val label = action.title?.toString()?.lowercase() ?: ""
            label.contains("decline") || label.contains("reject") || label.contains("dismiss") || label.contains("answer")
        }

        return matchesKeyword || hasCallActions
    }

    private fun isFocusModeActive(settingsRepo: SettingsRepository): Boolean {
        val masterKill = settingsRepo.getBooleanSync(SettingsRepository.MASTER_KILL_SWITCH, false)
        if (masterKill) return false

        val pauseEndTime = settingsRepo.getLongSync(SettingsRepository.GHOST_MODE_PAUSE_END_TIME, 0L)
        if (pauseEndTime > System.currentTimeMillis()) return false

        val ghostMode = settingsRepo.getBooleanSync(SettingsRepository.GHOST_MODE, false)
        val calendarGhost = settingsRepo.getBooleanSync(SettingsRepository.CALENDAR_GHOST_MODE_ACTIVE, false)
        val sleepGhost = settingsRepo.getBooleanSync(SettingsRepository.SLEEP_GHOST_MODE_ACTIVE, false)
        val overrideDnd = settingsRepo.getBooleanSync(SettingsRepository.OVERRIDE_DND, false)

        return ghostMode || calendarGhost || sleepGhost || overrideDnd
    }

    private fun isStarredOrChosenContact(callerTitle: String, settingsRepo: SettingsRepository): Boolean {
        if (callerTitle.isBlank()) return false

        // 1. Check user's selected VIP callers in settings
        val vipString = settingsRepo.getStringSync(SettingsRepository.VIP_CALLERS, "")
        if (vipString.isNotBlank()) {
            val vips = vipString.split(",")
            for (vip in vips) {
                val cleanVip = vip.trim()
                if (cleanVip.isBlank()) continue
                val namePart = if (cleanVip.contains("(")) cleanVip.substringBefore("(").trim() else cleanVip
                val numPart = if (cleanVip.contains("(") && cleanVip.contains(")")) 
                    cleanVip.substringAfter("(").substringBefore(")").replace(Regex("[^0-9+]"), "")
                else cleanVip.replace(Regex("[^0-9+]"), "")

                if (namePart.isNotBlank() && callerTitle.contains(namePart, ignoreCase = true)) {
                    return true
                }
                val callerDigits = callerTitle.replace(Regex("[^0-9]"), "")
                val numDigits = numPart.replace(Regex("[^0-9]"), "")
                if (numDigits.length >= 7 && callerDigits.length >= 7) {
                    if (callerDigits.endsWith(numDigits) || numDigits.endsWith(callerDigits)) {
                        return true
                    }
                }
            }
        }

        // 2. Query Android Contacts database for STARRED contact
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            try {
                val cleanDigits = callerTitle.replace(Regex("[^0-9]"), "")
                if (cleanDigits.length >= 6) {
                    val uri = android.net.Uri.withAppendedPath(
                        android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        android.net.Uri.encode(callerTitle)
                    )
                    contentResolver.query(
                        uri,
                        arrayOf(android.provider.ContactsContract.PhoneLookup.STARRED),
                        null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.STARRED)
                            if (idx >= 0 && cursor.getInt(idx) == 1) {
                                return true
                            }
                        }
                    }
                }

                contentResolver.query(
                    android.provider.ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.Contacts.STARRED),
                    "${android.provider.ContactsContract.Contacts.DISPLAY_NAME} = ? OR ${android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
                    arrayOf(callerTitle, callerTitle),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(android.provider.ContactsContract.Contacts.STARRED)
                        if (idx >= 0 && cursor.getInt(idx) == 1) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("TruecallerNL", "Error checking starred contact for $callerTitle", e)
            }
        }

        return false
    }

    private fun handleWhatsAppCall(sbn: StatusBarNotification, callerTitle: String) {
        val settingsRepo = (applicationContext as ShieldApplication).container.settingsRepository
        if (!isFocusModeActive(settingsRepo)) {
            Log.d("TruecallerNL", "Focus Mode is inactive; WhatsApp call allowed through: $callerTitle")
            return
        }

        val isStarred = isStarredOrChosenContact(callerTitle, settingsRepo)

        if (isStarred) {
            Log.d("TruecallerNL", "WhatsApp call from Starred Contact: $callerTitle. Bypassing DND and silent mode!")
            CallHandlingManager.bypassSilentForWhatsAppVip(this, callerTitle)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appDb = (applicationContext as ShieldApplication).container.database
                    appDb.smsLogDao().insert(
                        com.example.data.SmsLogEntity(
                            timestamp = System.currentTimeMillis(),
                            sender = callerTitle.ifBlank { "Starred Contact" },
                            message = "WhatsApp Call Allowed (Starred VIP DND Bypass)",
                            targetNumber = "",
                            status = "DND_BYPASS"
                        )
                    )
                } catch (e: Exception) {
                    Log.e("TruecallerNL", "Failed to log WhatsApp VIP bypass", e)
                }
            }
            return
        }

        // Unknown Number / Unstarred Call during Focus Mode: Reject the call immediately
        try {
            val actions = sbn.notification.actions
            var declined = false
            if (actions != null) {
                for (action in actions) {
                    val actionLabel = action.title?.toString()?.lowercase() ?: ""
                    if (actionLabel.contains("decline") || actionLabel.contains("reject") || actionLabel.contains("dismiss") || actionLabel.contains("hang")) {
                        action.actionIntent.send()
                        declined = true
                        Log.d("TruecallerNL", "WhatsApp call rejected via notification action for: $callerTitle")
                        break
                    }
                }
            }
            cancelNotification(sbn.key)
            Log.d("TruecallerNL", "WhatsApp call notification dismissed for non-starred: $callerTitle (declined=$declined)")

            CoroutineScope(Dispatchers.IO).launch {
                settingsRepo.incrementSpamBlockedCount()
                try {
                    val appDb = (applicationContext as ShieldApplication).container.database
                    appDb.smsLogDao().insert(
                        com.example.data.SmsLogEntity(
                            timestamp = System.currentTimeMillis(),
                            sender = callerTitle.ifBlank { "Unknown WhatsApp Number" },
                            message = if (declined) "WhatsApp Call Rejected (Unknown Number - Focus Mode)" else "WhatsApp Call Silenced (Focus Mode)",
                            targetNumber = "",
                            status = "CALL_DEFLECTED"
                        )
                    )
                } catch (e: Exception) {
                    Log.e("TruecallerNL", "Failed to log WhatsApp deflection", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TruecallerNL", "Error handling WhatsApp call", e)
        }
    }

    private fun rejectCall(settingsRepo: SettingsRepository) {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (androidx.core.app.ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ANSWER_PHONE_CALLS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                @Suppress("DEPRECATION") telecomManager.endCall()
                Log.d("TruecallerNL", "Call successfully rejected via TelecomManager.")
                
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { 
                    settingsRepo.incrementSpamBlockedCount() 
                    try {
                        val appDb = (applicationContext as ShieldApplication).container.database
                        appDb.smsLogDao().insert(com.example.data.SmsLogEntity(
                            timestamp = System.currentTimeMillis(),
                            sender = "Unknown Truecaller Caller",
                            message = "Spam Call Blocked (Notification)",
                            targetNumber = "",
                            status = "SPAM_BLOCKED"
                        ))
                    } catch (e: Exception) {
                        Log.e("TruecallerNL", "Failed to log spam block", e)
                    }
                }
            } else {
                Log.e("TruecallerNL", "Missing ANSWER_PHONE_CALLS permission.")
            }
        } catch (e: Exception) {
            Log.e("TruecallerNL", "Failed to reject call", e)
        }
    }

    private fun isKnownSavedContact(callerTitle: String): Boolean {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false
        }
        return try {
            val cleanDigits = callerTitle.replace(Regex("[^0-9]"), "")
            if (cleanDigits.length >= 6) {
                val uri = android.net.Uri.withAppendedPath(
                    android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    android.net.Uri.encode(callerTitle)
                )
                contentResolver.query(uri, arrayOf(android.provider.ContactsContract.PhoneLookup._ID), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) return true
                }
            }
            val cursor = contentResolver.query(
                android.provider.ContactsContract.Contacts.CONTENT_URI,
                arrayOf(android.provider.ContactsContract.Contacts._ID),
                "${android.provider.ContactsContract.Contacts.DISPLAY_NAME} = ? OR ${android.provider.ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} = ?",
                arrayOf(callerTitle, callerTitle),
                null
            )
            val exists = cursor?.moveToFirst() == true
            cursor?.close()
            exists
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private val inProgressSenders = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    }

    private fun isWhatsAppGroup(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification ?: return false
        val extras = notification.extras ?: return false
        if (extras.getBoolean(android.app.Notification.EXTRA_IS_GROUP_CONVERSATION, false)) return true
        val conversationTitle = extras.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString()
        if (!conversationTitle.isNullOrBlank()) return true
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
        val title = extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        if (!subText.isNullOrBlank() && !title.isNullOrBlank() && subText != title) {
            return true
        }
        return false
    }

    private data class WhatsAppDetails(
        val isGroup: Boolean,
        val groupName: String,
        val senderTitle: String,
        val effectiveText: String,
        val isMedia: Boolean
    )

    private fun extractWhatsAppDetails(sbn: StatusBarNotification, title: String, text: String): WhatsAppDetails {
        val extras = sbn.notification?.extras
        val isGroup = isWhatsAppGroup(sbn)
        val convTitle = extras?.getCharSequence(android.app.Notification.EXTRA_CONVERSATION_TITLE)?.toString()?.trim() ?: ""
        val subText = extras?.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        
        val groupName = if (isGroup) {
            when {
                convTitle.isNotBlank() -> convTitle
                subText.isNotBlank() && !subText.contains("message", ignoreCase = true) -> subText
                title.contains(":") -> title.substringBefore(":").trim()
                title.contains("(") && title.endsWith(")") -> title.substringAfter("(").substringBeforeLast(")").trim()
                else -> title.trim()
            }
        } else ""

        val sender = if (isGroup) {
            when {
                convTitle.isNotBlank() && title.isNotBlank() && title != convTitle -> {
                    if (title.contains(":")) title.substringAfter(":").trim() else title.trim()
                }
                title.contains(":") -> title.substringAfter(":").trim()
                else -> title.trim()
            }
        } else {
            title.trim()
        }

        val isMedia = text.isBlank()
        val effectiveText = if (isMedia) "[media]" else text.trim()

        return WhatsAppDetails(
            isGroup = isGroup,
            groupName = groupName,
            senderTitle = sender,
            effectiveText = effectiveText,
            isMedia = isMedia
        )
    }

    private fun handleWhatsAppMessage(sbn: StatusBarNotification, rawTitle: String, rawText: String) {
        val settingsRepo = (applicationContext as ShieldApplication).container.settingsRepository
        val masterKill = settingsRepo.getBooleanSync(SettingsRepository.MASTER_KILL_SWITCH, false)
        if (masterKill) return

        val details = extractWhatsAppDetails(sbn, rawTitle, rawText)
        if (details.effectiveText.startsWith("You:", ignoreCase = true)) return

        // Auto-Discovery: Record detected contact or group so user can easily add them to rules with 1 tap
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (details.isGroup && details.groupName.isNotBlank()) {
                    settingsRepo.recordDiscoveredWhatsAppChat(details.groupName, isGroup = true, details.effectiveText)
                } else if (!details.isGroup && details.senderTitle.isNotBlank()) {
                    settingsRepo.recordDiscoveredWhatsAppChat(details.senderTitle, isGroup = false, details.effectiveText)
                }
            } catch (e: Exception) {
                Log.e("TruecallerNL", "Error saving discovered WhatsApp chat", e)
            }
        }

        val focusActive = isFocusModeActive(settingsRepo)
        val autoReplyWhatsapp = settingsRepo.getBooleanSync(SettingsRepository.AUTO_RESPOND_WHATSAPP, false)

        val cleanKey = if (details.isGroup) {
            "grp_" + details.groupName.replace(Regex("[^a-zA-Z0-9+]"), "").lowercase()
        } else {
            "dm_" + details.senderTitle.replace(Regex("[^a-zA-Z0-9+]"), "").lowercase()
        }
        val prefs = getSharedPreferences("whatsapp_reply_history", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        // 1. Group Chat Isolation & Ruleset Resolution
        val allRules = settingsRepo.getNumberReplyRulesSync()
        val matchingRule = if (details.isGroup) {
            allRules.firstOrNull { rule ->
                rule.isGroup && (
                    rule.name.equals(details.groupName, ignoreCase = true) ||
                    rule.number.equals(details.groupName, ignoreCase = true) ||
                    (details.groupName.isNotBlank() && rule.name.isNotBlank() && details.groupName.contains(rule.name, ignoreCase = true)) ||
                    (details.groupName.isNotBlank() && rule.name.isNotBlank() && rule.name.contains(details.groupName, ignoreCase = true))
                )
            }
        } else {
            findNumberReplyRule(details.senderTitle, allRules)
        }

        // WhatsApp Groups: 100% ignored by default unless a designated group rule exists
        if (details.isGroup) {
            if (matchingRule == null) {
                Log.d("TruecallerNL", "WhatsApp group message ignored (${details.groupName}) - no explicit group rule configured")
                return
            }
            if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_IGNORE) {
                Log.d("TruecallerNL", "WhatsApp group message silenced (${details.groupName}) - rule action is IGNORE")
                return
            }
            if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_ALLOW) {
                Log.d("TruecallerNL", "WhatsApp group message allowed (${details.groupName}) - rule action is ALLOW (no auto-reply)")
                return
            }
            // Action is REPLY
            if (!matchingRule.alwaysActive && !focusActive) {
                Log.d("TruecallerNL", "WhatsApp group rule for ${details.groupName} skipped: requires active Focus Mode")
                return
            }
        } else {
            // 1-on-1 Direct Chat
            if (matchingRule != null) {
                if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_IGNORE) {
                    Log.d("TruecallerNL", "WhatsApp message from ${details.senderTitle} silenced - rule action is IGNORE")
                    return
                }
                if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_ALLOW) {
                    Log.d("TruecallerNL", "WhatsApp message from ${details.senderTitle} allowed through - rule action is ALLOW")
                    return
                }
                // Action is REPLY
                if (!matchingRule.alwaysActive && !focusActive) {
                    Log.d("TruecallerNL", "WhatsApp rule for ${details.senderTitle} skipped: requires active Focus Mode")
                    return
                }
            } else {
                // Unlisted 1-on-1 contact
                if (!focusActive && !autoReplyWhatsapp) {
                    return
                }
                val fallbackReplyAll = settingsRepo.getBooleanSync(SettingsRepository.FALLBACK_REPLY_ALL_WHATSAPP, false)
                if (!fallbackReplyAll) {
                    Log.d("TruecallerNL", "WhatsApp 1-on-1 message from unlisted sender (${details.senderTitle}) ignored - fallback reply all is OFF")
                    return
                }
                if (!focusActive) {
                    Log.d("TruecallerNL", "WhatsApp fallback reply all is ON, but Focus Mode is inactive")
                    return
                }
            }
        }

        // ---- DEDUP: exactly one reply per distinct message ----
        val fingerprint = if (details.isMedia) {
            "$cleanKey|media|${now / 60000}"
        } else {
            "$cleanKey|${details.effectiveText.trim().hashCode()}"
        }
        val lastFp = prefs.getString("fp_$cleanKey", null)
        val lastFpTime = prefs.getLong("fp_time_$cleanKey", 0L)
        if (fingerprint == lastFp && now - lastFpTime < 3 * 60 * 1000L) {
            Log.d("TruecallerNL", "WhatsApp dedup: already replied to this message from $cleanKey, skipping")
            return
        }

        // Emergency Keyword Override
        val trimmed = details.effectiveText.trim()
        val lower = trimmed.lowercase()
        val isEmergency = !details.isMedia && (lower == "#urgent" || lower.startsWith("#urgent ") ||
                lower == "#emergency" || lower.startsWith("#emergency ") ||
                trimmed.equals("URGENT", ignoreCase = false) ||
                trimmed.equals("EMERGENCY", ignoreCase = false))

        val botResult = if (isEmergency) {
            com.example.shield.WhatsAppBotInterpreter.interpretMessage(this, details.senderTitle, rawText, settingsRepo)
        } else null

        // Loop prevention: safety cap per contact/group (max 5 per hour)
        val hourStart = prefs.getLong("time_$cleanKey", 0L)
        var count = prefs.getInt("count_$cleanKey", 0)
        val hourReset = now - hourStart > 3600000
        if (!isEmergency) {
            if (!hourReset && count >= 5) {
                Log.d("TruecallerNL", "WhatsApp hourly safety cap reached for $cleanKey")
                return
            }
        }

        // ---- Choose reply text(s) ----
        val replies: List<String>
        val logStatus: String
        val logSender = if (details.isGroup) details.groupName else details.senderTitle

        if (matchingRule != null) {
            replies = matchingRule.replies
            logStatus = if (matchingRule.isGroup) "WHATSAPP_GROUP_RULE" else "WHATSAPP_NUMBER_RULE"
        } else if (isEmergency) {
            replies = listOf(botResult?.replyText
                ?: "⚠️ [EMERGENCY ALERT DELIVERED]\nYour urgent message bypassed silent mode and sounded the phone alarm. The user has been alerted.")
            logStatus = "WHATSAPP_EMERGENCY"
        } else {
            val isStarred = isStarredOrChosenContact(details.senderTitle, settingsRepo)
            val isKnownContact = isStarred || isKnownSavedContact(details.senderTitle)
            val single = if (isStarred) {
                settingsRepo.getStringSync(SettingsRepository.VIP_REPLY_MSG, "Hey, my phone is on silent. If this is an emergency, message URGENT.")
            } else if (!isKnownContact) {
                settingsRepo.getStringSync(SettingsRepository.UNKNOWN_REPLY_MSG, "I am currently in Focus Mode and do not take unsaved incoming messages right away. I will get back to you shortly.")
            } else {
                settingsRepo.getStringSync(SettingsRepository.STANDARD_REPLY_MSG, "Hi, I am currently focused or away. I will get back to you as soon as I can.")
            }
            replies = listOf(single)
            logStatus = if (isStarred) "WHATSAPP_VIP_REPLY" else "WHATSAPP_AUTO_REPLY"
        }

        if (replies.isEmpty()) return

        // ---- Send ----
        if (findReplyAction(sbn) == null) {
            Log.w("TruecallerNL", "WhatsApp auto-reply skipped: notification has no RemoteInput reply action ($logSender)")
            return
        }
        sendWhatsAppReplies(sbn, cleanKey, replies)
        Log.d("TruecallerNL", "WhatsApp auto-reply queued for $logSender (${replies.size} message(s), rule=$logStatus)")

        prefs.edit()
            .putString("fp_$cleanKey", fingerprint)
            .putLong("fp_time_$cleanKey", now)
            .putLong("time_$cleanKey", if (hourReset) now else hourStart)
            .putInt("count_$cleanKey", if (hourReset) 1 else count + 1)
            .apply()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val appDb = (applicationContext as ShieldApplication).container.database
                appDb.smsLogDao().insert(
                    com.example.data.SmsLogEntity(
                        timestamp = System.currentTimeMillis(),
                        sender = logSender,
                        message = "WhatsApp Auto-Reply (${replies.size}): ${replies.joinToString(" | ").take(500)}",
                        targetNumber = "",
                        status = logStatus
                    )
                )
            } catch (e: Exception) {
                Log.e("TruecallerNL", "Error logging WhatsApp reply", e)
            }
        }
    }

    /** Finds the direct-reply (RemoteInput) action on a WhatsApp notification, if any. */
    private fun findReplyAction(sbn: StatusBarNotification): Pair<android.app.Notification.Action, android.app.RemoteInput>? {
        val actions = sbn.notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs
            if (!remoteInputs.isNullOrEmpty()) {
                return action to remoteInputs[0]
            }
        }
        return null
    }

    /**
     * Sends each reply as its own WhatsApp message with a short delay between them,
     * protected by an in-progress sender lock to avoid interleaved bursts.
     */
    private fun sendWhatsAppReplies(sbn: StatusBarNotification, senderKey: String, replies: List<String>) {
        val (action, remoteInput) = findReplyAction(sbn) ?: return
        if (!inProgressSenders.add(senderKey)) {
            Log.d("TruecallerNL", "Already actively sending replies to $senderKey, skipping concurrent burst")
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                replies.forEachIndexed { index, replyText ->
                    if (index > 0) kotlinx.coroutines.delay(1500)
                    try {
                        val replyBundle = android.os.Bundle()
                        replyBundle.putCharSequence(remoteInput.resultKey, replyText)
                        val replyIntent = android.content.Intent()
                        android.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, replyBundle)
                        action.actionIntent.send(this@TruecallerNotificationListenerService, 0, replyIntent)
                        Log.d("TruecallerNL", "WhatsApp reply part ${index + 1}/${replies.size} sent")
                    } catch (e: Exception) {
                        Log.e("TruecallerNL", "Failed to send WhatsApp reply part ${index + 1}", e)
                    }
                }
            } finally {
                kotlinx.coroutines.delay(1000)
                inProgressSenders.remove(senderKey)
            }
        }
    }

    /**
     * Matches an incoming WhatsApp sender against the user's per-number reply rules.
     * The notification title is the contact name for saved contacts and the raw
     * number for unsaved ones, so we try digit matching, name matching, then a
     * contacts-database lookup of the title.
     */
    private fun findNumberReplyRule(
        senderTitle: String,
        rules: List<com.example.data.repository.NumberReplyRule>
    ): com.example.data.repository.NumberReplyRule? {
        if (rules.isEmpty()) return null
        val titleDigits = normalizePhoneDigits(senderTitle)
        for (rule in rules) {
            val ruleDigits = normalizePhoneDigits(rule.number)
            if (ruleDigits.length < 7) continue
            // 1. Direct digit match (unsaved numbers appear as digits in the title)
            if (titleDigits.length >= 7 &&
                (titleDigits.endsWith(ruleDigits) || ruleDigits.endsWith(titleDigits))) {
                return rule
            }
            // 2. Name match (saved contacts appear as names in the title)
            if (rule.name.isNotBlank() && senderTitle.contains(rule.name, ignoreCase = true)) {
                return rule
            }
            // 3. Resolve the title through the contacts database
            val resolved = resolveTitleToNumber(senderTitle)
            if (resolved != null) {
                val resolvedDigits = normalizePhoneDigits(resolved)
                if (resolvedDigits.length >= 7 &&
                    (resolvedDigits.endsWith(ruleDigits) || ruleDigits.endsWith(resolvedDigits))) {
                    return rule
                }
            }
        }
        return null
    }

    private fun resolveTitleToNumber(title: String): String? {
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = android.net.Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(title)
            )
            contentResolver.query(
                uri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.NUMBER),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(android.provider.ContactsContract.PhoneLookup.NUMBER)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
