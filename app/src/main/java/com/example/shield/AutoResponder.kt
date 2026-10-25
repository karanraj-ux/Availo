package com.example.shield

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AutoResponder {
    fun handleMissedCall(context: Context, phoneNumber: String, contactName: String) {
        (context.applicationContext as com.example.ShieldApplication).applicationScope.launch(Dispatchers.IO) {
            try {
                val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
                val masterKill = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.MASTER_KILL_SWITCH, false)
                if (masterKill) return@launch

                val masterAutoReply = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.AUTO_REPLY_ENABLED, false)
                val autoRespondMissedCall = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.AUTO_RESPOND_MISSED_CALL, false) || masterAutoReply
                if (!autoRespondMissedCall || phoneNumber.isBlank()) return@launch

                // Discard alphanumeric shortcodes
                if (phoneNumber.any { it.isLetter() } || phoneNumber.replace(Regex("[^0-9+]"), "").length < 7) {
                    Log.d("AutoResponder", "Ignoring non-phone missed call: $phoneNumber")
                    return@launch
                }

                val rules = settingsRepo.getNumberReplyRulesSync()
                val callerDigits = com.example.data.repository.normalizePhoneDigits(phoneNumber)
                val matchingRule = rules.firstOrNull { rule ->
                    !rule.isGroup && rule.replySms && (
                        (rule.number.isNotBlank() && com.example.data.repository.normalizePhoneDigits(rule.number).let { rDigits ->
                            rDigits.length >= 7 && (callerDigits.endsWith(rDigits) || rDigits.endsWith(callerDigits))
                        }) ||
                        (rule.name.isNotBlank() && (contactName.contains(rule.name, ignoreCase = true) || phoneNumber.contains(rule.name, ignoreCase = true)))
                    )
                }

                val isFocusActive = isFocusActive(settingsRepo)

                if (matchingRule != null) {
                    if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_IGNORE) {
                        Log.d("AutoResponder", "Rule matched for $phoneNumber, but action is IGNORE. Skipping.")
                        return@launch
                    }
                    if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_ALLOW) {
                        Log.d("AutoResponder", "Rule matched for $phoneNumber, action is ALLOW. Skipping auto-reply.")
                        return@launch
                    }
                    if (!matchingRule.alwaysActive && !isFocusActive) {
                        Log.d("AutoResponder", "Rule matched for $phoneNumber, but requires active Focus Mode")
                        return@launch
                    }
                    if (matchingRule.replies.isNotEmpty() && isSafeToReply(context, phoneNumber)) {
                        matchingRule.replies.forEachIndexed { idx, reply ->
                            if (idx > 0) kotlinx.coroutines.delay(1500)
                            sendSms(context, phoneNumber, reply)
                        }
                    }
                } else {
                    // Check fallback
                    val fallbackAll = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.FALLBACK_REPLY_ALL_SMS, false)
                    val tier = com.example.calls.CallHandlingManager.getRelationshipTier(context, phoneNumber)
                    val isVip = tier == "Inner Circle"
                    if ((!fallbackAll && !isVip) || !isFocusActive) {
                        Log.d("AutoResponder", "Missed call from $phoneNumber skipped - fallback reply all is OFF and caller is not VIP")
                        return@launch
                    }
                    if (isSafeToReply(context, phoneNumber)) {
                        val replyMessage = generateReply(context, "MISSED_CALL", phoneNumber)
                        sendSms(context, phoneNumber, replyMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoResponder", "Crash prevented in handleMissedCall", e)
            }
        }
    }

    fun handleIncomingSms(context: Context, sender: String, message: String) {
        (context.applicationContext as com.example.ShieldApplication).applicationScope.launch(Dispatchers.IO) {
            try {
                val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
                val masterKill = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.MASTER_KILL_SWITCH, false)
                if (masterKill) return@launch

                val autoRespondSms = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.AUTO_RESPOND_SMS, false)
                if (!autoRespondSms || sender.isBlank()) return@launch

                // 1. Transactional & Shortcode Protection: Drop bank alerts, OTPs, courier notices
                if (sender.any { it.isLetter() } || sender.replace(Regex("[^0-9+]"), "").length < 7) {
                    Log.d("AutoResponder", "Ignoring transactional or shortcode SMS from $sender")
                    return@launch
                }

                val rules = settingsRepo.getNumberReplyRulesSync()
                val senderDigits = com.example.data.repository.normalizePhoneDigits(sender)
                val matchingRule = rules.firstOrNull { rule ->
                    !rule.isGroup && rule.replySms && (
                        (rule.number.isNotBlank() && com.example.data.repository.normalizePhoneDigits(rule.number).let { rDigits ->
                            rDigits.length >= 7 && (senderDigits.endsWith(rDigits) || rDigits.endsWith(senderDigits))
                        }) ||
                        (rule.name.isNotBlank() && sender.contains(rule.name, ignoreCase = true))
                    )
                }

                val isFocusActive = isFocusActive(settingsRepo)

                if (matchingRule != null) {
                    if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_IGNORE) {
                        Log.d("AutoResponder", "SMS rule matched for $sender, but action is IGNORE. Skipping.")
                        return@launch
                    }
                    if (matchingRule.action == com.example.data.repository.NumberReplyRule.ACTION_ALLOW) {
                        Log.d("AutoResponder", "SMS rule matched for $sender, action is ALLOW. Skipping auto-reply.")
                        return@launch
                    }
                    if (!matchingRule.alwaysActive && !isFocusActive) {
                        Log.d("AutoResponder", "SMS rule matched for $sender, but requires active Focus Mode")
                        return@launch
                    }
                    if (matchingRule.replies.isNotEmpty() && isSafeToReply(context, sender)) {
                        matchingRule.replies.forEachIndexed { idx, reply ->
                            if (idx > 0) kotlinx.coroutines.delay(1500)
                            sendSms(context, sender, reply)
                        }
                    }
                } else {
                    // Check fallback
                    val fallbackAll = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.FALLBACK_REPLY_ALL_SMS, false)
                    val tier = com.example.calls.CallHandlingManager.getRelationshipTier(context, sender)
                    val isVip = tier == "Inner Circle"
                    if ((!fallbackAll && !isVip) || !isFocusActive) {
                        Log.d("AutoResponder", "SMS from $sender ignored - fallback reply all is OFF and sender is not VIP")
                        return@launch
                    }
                    if (isSafeToReply(context, sender)) {
                        val replyMessage = generateReply(context, "SMS", sender)
                        sendSms(context, sender, replyMessage)
                    }
                }
            } catch (e: Exception) {
                Log.e("AutoResponder", "Crash prevented in handleIncomingSms", e)
            }
        }
    }

    private fun isFocusActive(settingsRepo: com.example.data.repository.SettingsRepository): Boolean {
        val ghostMode = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.GHOST_MODE, false)
        val calendarGhost = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.CALENDAR_GHOST_MODE_ACTIVE, false)
        val sleepGhost = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.SLEEP_GHOST_MODE_ACTIVE, false)
        val overrideDnd = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.OVERRIDE_DND, false)
        return ghostMode || calendarGhost || sleepGhost || overrideDnd
    }

    private fun isSafeToReply(context: Context, phoneNumber: String): Boolean {
        // Check Relationship Tier
        val tier = com.example.calls.CallHandlingManager.getRelationshipTier(context, phoneNumber)
        if (tier == "Blocked" || tier == "Muted") {
            Log.d("AutoResponder", "Loop Prevention: Caller is $tier. No auto-reply sent to $phoneNumber.")
            return false
        }

        // Basic loop/bot avoidance: Ignore short codes or empty numbers
        val strippedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        if (strippedNumber.length < 7) {
            Log.w("AutoResponder", "Loop Prevention: Ignoring short code or invalid number: $phoneNumber")
            return false
        }
        val prefs = context.getSharedPreferences("auto_reply_history", Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()

        // Global Hourly Circuit Breaker: prevent carrier plan exhaustion or spam bans (max 20 total SMS/hr)
        val globalHourStart = prefs.getLong("global_sms_window_start", 0L)
        val globalSmsCount = prefs.getInt("global_sms_count", 0)
        if (currentTime - globalHourStart > 3600000) {
            prefs.edit().putLong("global_sms_window_start", currentTime).putInt("global_sms_count", 1).apply()
        } else {
            if (globalSmsCount >= 20) {
                Log.w("AutoResponder", "Carrier Safety Limit: Global safety ceiling of 20 SMS/hour reached. Dropping reply.")
                return false
            }
            prefs.edit().putInt("global_sms_count", globalSmsCount + 1).apply()
        }

        // Per-Contact Cooldown: user-configurable limit (default 3, 0 means unlimited)
        val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
        val contactHourlyLimit = settingsRepo.getIntSync(com.example.data.repository.SettingsRepository.AUTO_REPLY_HOURLY_LIMIT_PER_CONTACT, 3)

        val lastReplyTime = prefs.getLong("time_$strippedNumber", 0L)
        val replyCount = prefs.getInt("count_$strippedNumber", 0)
        if (currentTime - lastReplyTime > 3600000) {
            prefs.edit()
                .putLong("time_$strippedNumber", currentTime)
                .putInt("count_$strippedNumber", 1)
                .apply()
            return true
        }
        if (contactHourlyLimit > 0 && replyCount >= contactHourlyLimit) {
            Log.w("AutoResponder", "Loop Prevention: Rate limit ($contactHourlyLimit/hr) reached for auto-replies to $strippedNumber")
            return false
        }
        
        prefs.edit()
            .putInt("count_$strippedNumber", replyCount + 1)
            .putLong("time_$strippedNumber", currentTime)
            .apply()
        return true
    }
    private suspend fun generateReply(context: Context, eventType: String, phoneNumber: String): String {
        val settingsRepo = (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
        val tier = com.example.calls.CallHandlingManager.getRelationshipTier(context, phoneNumber)
        
        val rawTemplate = when (tier) {
            "Inner Circle" -> settingsRepo.getStringSync(com.example.data.repository.SettingsRepository.VIP_REPLY_MSG, "Hey, my phone is on silent. If this is an emergency (or if you are helping me find my phone), reply with the exact word URGENT and it will sound an alarm.")
            "Standard" -> settingsRepo.getStringSync(com.example.data.repository.SettingsRepository.STANDARD_REPLY_MSG, "Hi, I am currently focused or away. I will get back to you as soon as I can.")
            else -> settingsRepo.getStringSync(com.example.data.repository.SettingsRepository.UNKNOWN_REPLY_MSG, "I do not accept direct calls from unknown numbers to prevent spam. If this is important, please message me.")
        }

        var resolved = MessageDispatcherHelper.resolveDynamicPlaceholders(
            context = context,
            template = rawTemplate,
            senderName = phoneNumber,
            senderNumber = phoneNumber,
            settingsRepo = settingsRepo
        )

        // Attach configured file (document, PDF, photo, catalog link)
        val attachedFileName = settingsRepo.getStringSync(com.example.data.repository.SettingsRepository.AUTO_REPLY_ATTACHED_FILE_NAME, "")
        val attachedFileUrl = settingsRepo.getStringSync(com.example.data.repository.SettingsRepository.AUTO_REPLY_ATTACHED_FILE_URL, "")
        if (attachedFileUrl.isNotBlank()) {
            val label = if (attachedFileName.isNotBlank()) "📎 Attached ($attachedFileName)" else "📎 Attached File"
            resolved = "$resolved\n$label: $attachedFileUrl"
        }

        val attachLoc = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.AUTO_REPLY_ATTACH_LOCATION, false)
        val appendVipLoc = settingsRepo.getBooleanSync(com.example.data.repository.SettingsRepository.APPEND_LOCATION_TO_VIP, false)
        if ((attachLoc || (tier == "Inner Circle" && appendVipLoc)) && !resolved.contains("maps.google.com")) {
            val locLink = MessageDispatcherHelper.getLocationLink(context, settingsRepo)
            resolved = "$resolved\n📍 Location: $locLink"
        }

        return resolved
    }
    private fun sendSms(context: Context, phoneNumber: String, content: String) {
        try {
            if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.SEND_SMS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Log.e("AutoResponder", "Permission denied for SEND_SMS, silently exiting.")
                return
            }
            val sm: android.telephony.SmsManager = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                context.getSystemService(android.telephony.SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                android.telephony.SmsManager.getDefault()
            }
            val parts = sm.divideMessage(content)
            if (parts.size > 1) {
                sm.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                sm.sendTextMessage(phoneNumber, null, content, null, null)
            }
            Log.d("AutoResponder", "Auto-responded natively via SMS to $phoneNumber")
        } catch (e: Exception) {
            Log.e("AutoResponder", "Failed to auto-respond natively via SMS", e)
        }
    }
}
