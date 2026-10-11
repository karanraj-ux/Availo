package com.example.ui.screens

import kotlinx.coroutines.launch


import android.Manifest
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Quickreply
import androidx.compose.material.icons.rounded.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.MainViewModel
import com.example.data.repository.NumberReplyRule
import com.example.ui.viewmodels.SettingsViewModel


@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectScreen(viewModel: MainViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                divider = {}
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.Quickreply, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Auto-Reply", fontWeight = FontWeight.Bold) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.CallSplit, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Forwarding", fontWeight = FontWeight.Bold) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        if (selectedTab == 0) {
            AutoReplyTab(viewModel)
        } else {
            ForwardingTab(viewModel)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoReplyTab(viewModel: MainViewModel) {

    val context = LocalContext.current
    val snackbarHostState = com.example.LocalSnackbarHostState.current
    var showPrivacyPledge by remember { mutableStateOf(false) }
    var showRuleDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<NumberReplyRule?>(null) }
    val scope = rememberCoroutineScope()
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
        )
    )
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    val hasSmsPerm = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasCallLogPerm = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val hasPhoneStatePerm = androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[Manifest.permission.SEND_SMS] == true
        val callLogGranted = permissions[Manifest.permission.READ_CALL_LOG] == true
        val phoneStateGranted = permissions[Manifest.permission.READ_PHONE_STATE] == true
        
        if (smsGranted && (callLogGranted || phoneStateGranted)) {
            settingsViewModel.updateAutoRespondMissedCall(true)
            settingsViewModel.updateAutoRespondSms(true)
        } else if (smsGranted) {
            settingsViewModel.updateAutoRespondSms(true)
            settingsViewModel.updateAutoRespondMissedCall(false)
        } else {
            settingsViewModel.updateAutoRespondMissedCall(false)
            settingsViewModel.updateAutoRespondSms(false)
        }
    }

    if (showPrivacyPledge) {
        PrivacyPledgeDialog(
            onConfirm = {
                showPrivacyPledge = false
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.READ_CALL_LOG,
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.RECEIVE_SMS
                    )
                )
            },
            onDismiss = {
                showPrivacyPledge = false
            }
        )
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            
                            if (hasPhoneIndex >= 0 && idIndex >= 0) {
                                val hasPhone = cursor.getInt(hasPhoneIndex)
                                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "Unknown"
                                
                                if (hasPhone > 0) {
                                    val id = cursor.getString(idIndex)
                                    context.contentResolver.query(
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
                                        null, 
                                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", 
                                        arrayOf(id), 
                                        null
                                    )?.use { phones ->
                                        if (phones.moveToFirst()) {
                                            val numIndex = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                            if (numIndex >= 0) {
                                                val number = phones.getString(numIndex)
                                                val currentList = settingsState.autoReplyRestrictedNumbers
                                                val newList = if (currentList.isEmpty()) name else "$currentList,$name"
                                                settingsViewModel.updateAutoReplyRestrictedNumbers(newList)
                                            }
                                        }
                                    }
                                } else {
                                    val currentList = settingsState.autoReplyRestrictedNumbers
                                    val newList = if (currentList.isEmpty()) name else "$currentList,$name"
                                    settingsViewModel.updateAutoReplyRestrictedNumbers(newList)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // 1. Top Section: Triggers & Explanation
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "When to Auto-Reply",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Choose the triggers that prompt Availo to send an automated SMS reply.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // Missed Calls Trigger
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.PhoneMissed, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "On Missed Phone Calls",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Sends an SMS when you miss a call",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingsState.autoRespondMissedCall,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (hasSmsPerm && (hasCallLogPerm || hasPhoneStatePerm)) {
                                        settingsViewModel.updateAutoRespondMissedCall(true)
                                    } else {
                                        showPrivacyPledge = true
                                    }
                                } else {
                                    settingsViewModel.updateAutoRespondMissedCall(false)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                    
                    // Incoming SMS Trigger
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Sms, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "On Incoming SMS Messages",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Sends an SMS reply when someone texts you",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingsState.autoRespondSms,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    if (hasSmsPerm) {
                                        settingsViewModel.updateAutoRespondSms(true)
                                    } else {
                                        showPrivacyPledge = true
                                    }
                                } else {
                                    settingsViewModel.updateAutoRespondSms(false)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)
                        )
                    }

                    // Incoming WhatsApp Trigger
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0xFF25D366).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Message, contentDescription = null, tint = Color(0xFF25D366))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "On Incoming WhatsApp Chats",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Direct offline auto-reply via notification listener",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settingsState.autoRespondWhatsapp,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    val notifEnabled = androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
                                    if (notifEnabled) {
                                        settingsViewModel.updateAutoRespondWhatsapp(true)
                                    } else {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        context.startActivity(intent)
                                        settingsViewModel.updateAutoRespondWhatsapp(true)
                                    }
                                } else {
                                    settingsViewModel.updateAutoRespondWhatsapp(false)
                                }
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF25D366))
                        )
                    }

                    if (!settingsState.autoRespondMissedCall && !settingsState.autoRespondSms && !settingsState.autoRespondWhatsapp) {
                        Text(
                            "Turn on one or more triggers above to activate smart auto-replies.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // WhatsApp Offline Bot & Message Interpreter Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color(0xFF25D366).copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Quickreply, contentDescription = null, tint = Color(0xFF25D366))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Enable WhatsApp Auto-Reply",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "Instant replies over the internet • 3 recipient tiers",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF25D366),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Switch(
                            checked = settingsState.autoRespondWhatsapp,
                            onCheckedChange = { isChecked ->
                                settingsViewModel.updateAutoRespondWhatsapp(isChecked)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF25D366))
                        )
                    }

                    Text(
                        "When Focus Mode is on, Availo rejects unknown WhatsApp calls, allows starred/VIP contacts through DND silent mode, and instantly sends your chosen auto-reply message over the internet to incoming chats.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // Showcase: Everyday WhatsApp Sleep / Morning Auto-Reply
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF075E54).copy(alpha = 0.08f)),
                        border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ChatBubble, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Everyday Example: Peaceful Morning & Sleep Shield",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                "When someone texts 'Good morning!' while you are asleep or in focus mode, Availo answers instantly over the internet with your pre-set response:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Simulated Incoming WhatsApp Bubble
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(topStart = 0.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text("Friend / Partner", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF25D366))
                                        Text("Good morning! Are you awake yet? ☕", style = MaterialTheme.typography.bodySmall)
                                        Text("07:15 AM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
                                    }
                                }
                            }

                            // Simulated Auto-Reply WhatsApp Bubble
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                Surface(
                                    color = Color(0xFFDCF8C6),
                                    shape = RoundedCornerShape(topStart = 12.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                        Text("Availo (Auto-Reply)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF075E54))
                                        Text("Still resting! Phone is on silent until 8:30 AM. Will catch up shortly ☀️", style = MaterialTheme.typography.bodySmall, color = Color.Black)
                                        Row(modifier = Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                                            Text("07:15 AM", style = MaterialTheme.typography.labelSmall, color = Color.DarkGray)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(Icons.Rounded.DoneAll, contentDescription = null, tint = Color(0xFF34B7F1), modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                    // WhatsApp Specific Target Rule Quick Action
                    val whatsAppRules = settingsState.numberReplyRules.filter { it.isGroup }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "WhatsApp Target Rules",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                if (whatsAppRules.isEmpty()) "0 group rules active • Tap + to configure" else "${whatsAppRules.size} group ${if (whatsAppRules.size == 1) "rule" else "rules"} active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF075E54),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Button(
                            onClick = {
                                editingRule = NumberReplyRule(
                                    name = "",
                                    number = "",
                                    isGroup = true,
                                    action = NumberReplyRule.ACTION_REPLY,
                                    replies = listOf("Currently away from WhatsApp in focus mode. Will get back to you shortly.")
                                )
                                showRuleDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF25D366),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add WhatsApp Rule", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Add WhatsApp Rule", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (whatsAppRules.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(whatsAppRules.size) { idx ->
                                val rule = whatsAppRules[idx]
                                SuggestionChip(
                                    onClick = {
                                        editingRule = rule
                                        showRuleDialog = true
                                    },
                                    label = { Text(rule.name.ifBlank { "Group" }.take(20), style = MaterialTheme.typography.labelSmall) },
                                    icon = { Icon(Icons.Rounded.Group, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }
                    }

                }
            }
        }

        // 2. Targeted Auto-Reply Rules (Specific Contacts & WhatsApp Groups) - Primary Architecture
        item {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        "Targeted Rules (Contacts & Groups)",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        "Primary responder • 0-spam guarantee",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Button(
                                onClick = { editingRule = null; showRuleDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add Rule", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Rule", fontWeight = FontWeight.Bold)
                            }
                        }

                        Text(
                            "Configure dedicated auto-replies or ignore rules for specific people or WhatsApp groups. By default, Availo ONLY responds to rules in this list — strangers, banks, and general groups remain completely silent.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Quick Add Shortcuts Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AssistChip(
                                onClick = {
                                    editingRule = NumberReplyRule(
                                        name = "",
                                        number = "",
                                        isGroup = true,
                                        action = NumberReplyRule.ACTION_REPLY,
                                        replies = listOf("Currently away in focus mode. Will get back soon!")
                                    )
                                    showRuleDialog = true
                                },
                                label = { Text("+ WhatsApp Group", fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Group, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = Color(0xFF25D366).copy(alpha = 0.12f),
                                    labelColor = Color(0xFF075E54)
                                ),
                                border = BorderStroke(1.dp, Color(0xFF25D366).copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp)
                            )
                            AssistChip(
                                onClick = {
                                    editingRule = NumberReplyRule(
                                        name = "",
                                        number = "",
                                        isGroup = false,
                                        action = NumberReplyRule.ACTION_REPLY,
                                        replies = listOf("Currently busy and will get back to you shortly.")
                                    )
                                    showRuleDialog = true
                                },
                                label = { Text("+ Phone Contact", fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Discovered Recent WhatsApp Chats Quick-Add Chips
                        val discoveredChats = settingsState.discoveredWhatsAppChats
                        if (discoveredChats.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "Detected from WhatsApp (Tap to add rule):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                androidx.compose.foundation.lazy.LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(discoveredChats.size) { idx ->
                                        val chat = discoveredChats[idx]
                                        SuggestionChip(
                                            onClick = {
                                                editingRule = NumberReplyRule(
                                                    name = chat.title,
                                                    number = if (chat.isGroup) chat.title else "",
                                                    isGroup = chat.isGroup,
                                                    action = NumberReplyRule.ACTION_REPLY
                                                )
                                                showRuleDialog = true
                                            },
                                            label = {
                                                Text("+ ${chat.title.take(20)}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                                            },
                                            icon = {
                                                Icon(
                                                    if (chat.isGroup) Icons.Rounded.Group else Icons.Rounded.Person,
                                                    contentDescription = null,
                                                    tint = if (chat.isGroup) Color(0xFF25D366) else MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        val numberRules = settingsState.numberReplyRules
                        if (numberRules.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(Color(0xFF25D366).copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Rule, contentDescription = null, tint = Color(0xFF075E54), modifier = Modifier.size(24.dp))
                                    }
                                    Text(
                                        "No Targeted Rules Yet",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Configure auto-reply messages or silence notifications for specific WhatsApp groups or individual contacts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                editingRule = NumberReplyRule(
                                                    name = "",
                                                    number = "",
                                                    isGroup = true,
                                                    action = NumberReplyRule.ACTION_REPLY,
                                                    replies = listOf("Currently away in focus mode. Will get back shortly!")
                                                )
                                                showRuleDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = Color(0xFF25D366),
                                                contentColor = Color.White
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+ WhatsApp Rule", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                editingRule = null
                                                showRuleDialog = true
                                            },
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("+ Contact Rule", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        } else {
                                numberRules.forEach { rule ->
                                    key(rule.number + rule.name + rule.action) {
                                        Surface(
                                            shape = RoundedCornerShape(16.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(40.dp)
                                                        .background(
                                                            when {
                                                                rule.action == NumberReplyRule.ACTION_IGNORE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                                                                rule.isGroup -> Color(0xFF25D366).copy(alpha = 0.15f)
                                                                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                            },
                                                            CircleShape
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        when {
                                                            rule.action == NumberReplyRule.ACTION_IGNORE -> Icons.Rounded.Block
                                                            rule.isGroup -> Icons.Rounded.Group
                                                            else -> Icons.Rounded.Person
                                                        },
                                                        contentDescription = null,
                                                        tint = when {
                                                            rule.action == NumberReplyRule.ACTION_IGNORE -> MaterialTheme.colorScheme.error
                                                            rule.isGroup -> Color(0xFF25D366)
                                                            else -> MaterialTheme.colorScheme.primary
                                                        },
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            if (rule.name.isNotBlank()) rule.name else rule.number,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        if (rule.name.isNotBlank() && rule.number.isNotBlank() && !rule.isGroup) {
                                                            Text(
                                                                " • ${rule.number}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.padding(vertical = 4.dp)
                                                    ) {
                                                        // Group or Contact Badge
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (rule.isGroup) Color(0xFF25D366).copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer
                                                        ) {
                                                            Text(
                                                                if (rule.isGroup) "WhatsApp Group" else "Direct Contact",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (rule.isGroup) Color(0xFF075E54) else MaterialTheme.colorScheme.onPrimaryContainer,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        // Action Badge
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = when (rule.action) {
                                                                NumberReplyRule.ACTION_IGNORE -> MaterialTheme.colorScheme.errorContainer
                                                                NumberReplyRule.ACTION_ALLOW -> MaterialTheme.colorScheme.tertiaryContainer
                                                                else -> MaterialTheme.colorScheme.secondaryContainer
                                                            }
                                                        ) {
                                                            Text(
                                                                when (rule.action) {
                                                                    NumberReplyRule.ACTION_IGNORE -> "🔕 Ignored"
                                                                    NumberReplyRule.ACTION_ALLOW -> "🔔 VIP Allow"
                                                                    else -> "💬 Auto-Reply"
                                                                },
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = when (rule.action) {
                                                                    NumberReplyRule.ACTION_IGNORE -> MaterialTheme.colorScheme.onErrorContainer
                                                                    NumberReplyRule.ACTION_ALLOW -> MaterialTheme.colorScheme.onTertiaryContainer
                                                                    else -> MaterialTheme.colorScheme.onSecondaryContainer
                                                                },
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        if (rule.alwaysActive) {
                                                            Surface(
                                                                shape = RoundedCornerShape(6.dp),
                                                                color = MaterialTheme.colorScheme.surfaceVariant
                                                            ) {
                                                                Text(
                                                                    "Daily",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    val statusDesc = when (rule.action) {
                                                        NumberReplyRule.ACTION_IGNORE -> "Silently ignored • Zero spam responses"
                                                        NumberReplyRule.ACTION_ALLOW -> "Allowed through silent & Focus modes"
                                                        else -> {
                                                            val preview = rule.replies.firstOrNull() ?: ""
                                                            "${rule.replies.size} ${if (rule.replies.size == 1) "reply" else "replies"}: ${preview.take(45)}${if (preview.length > 45) "…" else ""}"
                                                        }
                                                    }
                                                    Text(
                                                        statusDesc,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }
                                                IconButton(onClick = { editingRule = rule; showRuleDialog = true }) {
                                                    Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                                                }
                                                IconButton(onClick = {
                                                    settingsViewModel.setNumberReplyRules(numberRules.filterNot { 
                                                        (it.number == rule.number && it.name == rule.name) 
                                                    })
                                                }) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showRuleDialog) {
                        val base = editingRule
                        var dlgIsGroup by remember(base) { mutableStateOf(base?.isGroup ?: false) }
                        var dlgAction by remember(base) { mutableStateOf(base?.action ?: NumberReplyRule.ACTION_REPLY) }
                        var dlgName by remember(base) { mutableStateOf(base?.name ?: "") }
                        var dlgNumber by remember(base) { mutableStateOf(base?.number ?: "") }
                        var dlgAlwaysActive by remember(base) { mutableStateOf(base?.alwaysActive ?: false) }
                        var dlgReplySms by remember(base) { mutableStateOf(base?.replySms ?: true) }
                        var dlgReplies by remember(base) { mutableStateOf((base?.replies ?: emptyList()).ifEmpty { listOf("") }) }

                        val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
                            if (uri != null) {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                                            if (cursor.moveToFirst()) {
                                                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                                                if (nameIndex >= 0) {
                                                    val pickedName = cursor.getString(nameIndex) ?: ""
                                                    dlgName = pickedName
                                                }
                                                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                                                val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                                                if (idIndex >= 0 && hasPhoneIndex >= 0 && cursor.getInt(hasPhoneIndex) > 0) {
                                                    val contactId = cursor.getString(idIndex)
                                                    context.contentResolver.query(
                                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                                        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                                        arrayOf(contactId),
                                                        null
                                                    )?.use { pCursor ->
                                                        if (pCursor.moveToFirst()) {
                                                            val numIdx = pCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                                            if (numIdx >= 0) {
                                                                dlgNumber = pCursor.getString(numIdx) ?: ""
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                            }
                        }

                        AlertDialog(
                            onDismissRequest = { showRuleDialog = false },
                            title = { 
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (dlgIsGroup) Icons.Rounded.Group else Icons.Rounded.Person,
                                        contentDescription = null,
                                        tint = if (dlgIsGroup) Color(0xFF25D366) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (base == null) {
                                            if (dlgIsGroup) "Add WhatsApp Group Rule" else "Add Contact Rule"
                                        } else {
                                            if (dlgIsGroup) "Edit WhatsApp Group Rule" else "Edit Contact Rule"
                                        },
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Target Type Selector: Direct Contact vs WhatsApp Group
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChip(
                                            selected = !dlgIsGroup,
                                            onClick = { dlgIsGroup = false },
                                            label = { Text("Direct Contact") },
                                            leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = dlgIsGroup,
                                            onClick = { dlgIsGroup = true },
                                            label = { Text("WhatsApp Group") },
                                            leadingIcon = { Icon(Icons.Rounded.Group, contentDescription = null, tint = Color(0xFF25D366), modifier = Modifier.size(16.dp)) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    // Action Selector: Auto-Reply vs Ignore vs Allow
                                    Text("Rule Action:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = dlgAction == NumberReplyRule.ACTION_REPLY,
                                            onClick = { dlgAction = NumberReplyRule.ACTION_REPLY },
                                            label = { Text("Auto-Reply") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = dlgAction == NumberReplyRule.ACTION_IGNORE,
                                            onClick = { dlgAction = NumberReplyRule.ACTION_IGNORE },
                                            label = { Text("Ignore") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        FilterChip(
                                            selected = dlgAction == NumberReplyRule.ACTION_ALLOW,
                                            onClick = { dlgAction = NumberReplyRule.ACTION_ALLOW },
                                            label = { Text("VIP Allow") },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    if (!dlgIsGroup) {
                                        OutlinedButton(
                                            onClick = { contactPickerLauncher.launch(null) },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Pick from Contacts")
                                        }
                                        OutlinedTextField(
                                            value = dlgName,
                                            onValueChange = { dlgName = it },
                                            label = { Text("Contact Name (optional)") },
                                            placeholder = { Text("e.g. Mom, Rahul") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = dlgNumber,
                                            onValueChange = { dlgNumber = it },
                                            label = { Text("Phone Number") },
                                            placeholder = { Text("+91 98765 43210") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Checkbox(
                                                checked = dlgReplySms,
                                                onCheckedChange = { dlgReplySms = it }
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Also apply rule to incoming SMS", style = MaterialTheme.typography.bodySmall)
                                        }
                                    } else {
                                        OutlinedTextField(
                                            value = dlgName,
                                            onValueChange = { dlgName = it },
                                            label = { Text("Exact WhatsApp Group Name") },
                                            placeholder = { Text("e.g. Family Group, Core Team") },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        Text(
                                            "Quick Group Suggestions (Tap to fill):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            listOf("Family", "Work", "Office Group", "Friends", "Project Team", "Society / Colony").forEach { grp ->
                                                SuggestionChip(
                                                    onClick = { dlgName = grp },
                                                    label = { Text(grp, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }
                                        Text(
                                            if (dlgAction == NumberReplyRule.ACTION_IGNORE)
                                                "Messages in this group will be silenced with zero auto-reply spam."
                                            else
                                                "Availo will ONLY trigger when this specific group messages. All other groups stay silent.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                            .padding(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Daily Rule (Always Active)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                            Text(
                                                if (dlgAlwaysActive) "Applies daily even when Availo focus mode is off" else "Only applies during active Availo / Focus mode",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = dlgAlwaysActive,
                                            onCheckedChange = { dlgAlwaysActive = it }
                                        )
                                    }

                                    if (dlgAction == NumberReplyRule.ACTION_REPLY) {
                                        Text(
                                            "Quick Presets (Tap to use, fully editable):",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val presets = listOf(
                                                "🚗 Driving" to "I am currently driving and will get back to you once I park safely.",
                                                "💼 In Meeting" to "In an important meeting right now. Will reply as soon as it ends.",
                                                "🌙 Resting" to "Phone is on silent. If this is an emergency, reply URGENT.",
                                                "🏃 Away" to "Stepped away from my phone. I'll get back to you shortly."
                                            )
                                            presets.forEach { (label, text) ->
                                                SuggestionChip(
                                                    onClick = {
                                                        dlgReplies = if (dlgReplies.isEmpty()) listOf(text) else {
                                                            dlgReplies.toMutableList().also { it[0] = text }
                                                        }
                                                    },
                                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                                )
                                            }
                                        }

                                        Text(
                                            "Custom Message (Type anything, change anytime):",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        dlgReplies.forEachIndexed { i, rep ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                OutlinedTextField(
                                                    value = rep,
                                                    onValueChange = { v ->
                                                        dlgReplies = dlgReplies.toMutableList().also { it[i] = v }
                                                    },
                                                    label = { Text("Reply ${i + 1}") },
                                                    modifier = Modifier.weight(1f),
                                                    shape = RoundedCornerShape(12.dp),
                                                    minLines = 2,
                                                    maxLines = 4
                                                )
                                                if (dlgReplies.size > 1) {
                                                    IconButton(onClick = {
                                                        dlgReplies = dlgReplies.toMutableList().also { it.removeAt(i) }
                                                    }) {
                                                        Icon(Icons.Filled.Close, contentDescription = "Remove reply")
                                                    }
                                                }
                                            }
                                        }
                                        OutlinedButton(
                                            onClick = { dlgReplies = dlgReplies + "" },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Add another reply message", fontWeight = FontWeight.SemiBold)
                                        }
                                    } else if (dlgAction == NumberReplyRule.ACTION_IGNORE) {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "No auto-replies will be sent. Incoming messages from this target will be muted and dismissed silently.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "VIP Priority: Messages and calls from this target will bypass silent mode and ring through.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        val cleanReplies = dlgReplies.map { it.trim() }.filter { it.isNotBlank() }
                                        val targetId = if (dlgIsGroup) dlgName.trim() else dlgNumber.trim()
                                        val canSave = (targetId.isNotBlank() || dlgName.isNotBlank()) &&
                                                (dlgAction != NumberReplyRule.ACTION_REPLY || cleanReplies.isNotEmpty())
                                        if (canSave) {
                                            val newRule = NumberReplyRule(
                                                number = if (dlgIsGroup) dlgName.trim() else dlgNumber.trim(),
                                                name = dlgName.trim(),
                                                action = dlgAction,
                                                replies = if (dlgAction == NumberReplyRule.ACTION_REPLY) cleanReplies else emptyList(),
                                                isGroup = dlgIsGroup,
                                                replySms = dlgReplySms,
                                                alwaysActive = dlgAlwaysActive
                                            )
                                            settingsViewModel.setNumberReplyRules(
                                                settingsState.numberReplyRules.filterNot {
                                                    (base != null && it.number == base.number && it.name == base.name) ||
                                                    (!dlgIsGroup && it.number == newRule.number && !it.isGroup) ||
                                                    (dlgIsGroup && it.name == newRule.name && it.isGroup)
                                                } + newRule
                                            )
                                            showRuleDialog = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dlgIsGroup) Color(0xFF25D366) else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (base == null) "Save Rule" else "Update Rule", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showRuleDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    // 3. Fallback Messages for Unlisted Senders (Secondary / Opt-in)
                    var fallbackExpanded by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { fallbackExpanded = !fallbackExpanded },
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.AltRoute, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            "General Fallback (Unlisted Senders)",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            if (settingsState.fallbackReplyAllWhatsapp || settingsState.fallbackReplyAllSms) "Fallback enabled (during Focus mode)" else "Disabled (Availo is silent to unlisted senders)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (settingsState.fallbackReplyAllWhatsapp || settingsState.fallbackReplyAllSms) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(
                                    if (fallbackExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Text(
                                "By default, Availo remains silent for unlisted numbers and groups to prevent unwanted messaging. Enable below only if you want an away message sent to any unlisted person during Focus mode.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Opt-In Switch: Fallback WhatsApp
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Reply to unlisted WhatsApp 1-on-1 chats", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("Sends fallback reply to strangers / unlisted contacts", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = settingsState.fallbackReplyAllWhatsapp,
                                    onCheckedChange = { settingsViewModel.updateFallbackReplyAllWhatsapp(it) }
                                )
                            }

                            // Opt-In Switch: Fallback SMS
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Reply to unlisted mobile SMS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text("Never replies to bank shortcodes or OTPs", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = settingsState.fallbackReplyAllSms,
                                    onCheckedChange = { settingsViewModel.updateFallbackReplyAllSms(it) }
                                )
                            }

                            AnimatedVisibility(visible = fallbackExpanded || settingsState.fallbackReplyAllWhatsapp || settingsState.fallbackReplyAllSms) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                                    Text(
                                        "Fallback Messages by Relationship Tier",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )

                                    // VIPs
                                    OutlinedTextField(
                                        value = settingsState.vipReplyMsg,
                                        onValueChange = { settingsViewModel.updateVipReplyMsg(it) },
                                        label = { Text("Inner Circle (VIP Contacts)") },
                                        supportingText = { Text("Sent only to numbers you marked as VIP in Availo") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = 2,
                                        maxLines = 4
                                    )

                                    // Standard Contacts
                                    OutlinedTextField(
                                        value = settingsState.standardReplyMsg,
                                        onValueChange = { 
                                            settingsViewModel.updateStandardReplyMsg(it)
                                            settingsViewModel.updateBusyReplyMessage(it)
                                        },
                                        label = { Text("Saved Contacts (Address Book)") },
                                        supportingText = { Text("Sent to any known number saved in your phone") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = 2,
                                        maxLines = 4
                                    )

                                    // Unknown Strangers
                                    OutlinedTextField(
                                        value = settingsState.unknownReplyMsg,
                                        onValueChange = { settingsViewModel.updateUnknownReplyMsg(it) },
                                        label = { Text("Unknown Numbers (Strangers / Unsaved)") },
                                        supportingText = { Text("Sent to unlisted numbers when fallback is enabled") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        minLines = 2,
                                        maxLines = 4
                                    )
                                }
                            }
                        }
                    }

                    // 4. Rate-Limiting & Safety Protocol
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(4.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Safety & Rate-Limiting Rules", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(
                                "• WhatsApp Groups: 100% ignored unless explicitly added to your ruleset.\n• Transactional & OTP SMS: Banks, delivery bots, and shortcodes are never auto-replied.\n• Burst & Loop Protection: Max 3 replies per contact/hour with message fingerprinting.\n• Telecom Limit Safeguard: Hard limit of 20 SMS/hour to protect your carrier plan.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        
        // 3. Premium Explanation Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.AutoAwesome, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Why use Auto-Responder?",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    BenefitRow(
                        icon = Icons.Rounded.Favorite,
                        title = "Polite Professionalism",
                        description = "Never leave someone hanging. Automatically acknowledge important calls when you're busy."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BenefitRow(
                        icon = Icons.Rounded.PrivacyTip,
                        title = "100% On-Device",
                        description = "Your contacts and messages are never sent to a cloud server."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BenefitRow(
                        icon = Icons.Rounded.SettingsSuggest,
                        title = "Smart Context",
                        description = "Only replies to actual missed calls, not spam or blocked numbers."
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForwardingTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    val snackbarHostState = com.example.LocalSnackbarHostState.current
    var showPrivacyPledge by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val settingsViewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            (context.applicationContext as com.example.ShieldApplication).container.settingsRepository
        )
    )
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    
    val ruleViewModel: com.example.ui.viewmodels.PhoneRuleViewModel = viewModel(
        factory = com.example.ui.viewmodels.PhoneRuleViewModel.Factory(
            (context.applicationContext as com.example.ShieldApplication).container.phoneRuleRepository
        )
    )
    val rules by ruleViewModel.rules.collectAsStateWithLifecycle(initialValue = emptyList())
    var showInboxPicker by remember { mutableStateOf(false) }

    


    val targetPhonePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            
                            if (hasPhoneIndex >= 0 && idIndex >= 0) {
                                val hasPhone = cursor.getInt(hasPhoneIndex)
                                if (hasPhone > 0) {
                                    val id = cursor.getString(idIndex)
                                    context.contentResolver.query(
                                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI, 
                                        null, 
                                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?", 
                                        arrayOf(id), 
                                        null
                                    )?.use { phones ->
                                        if (phones.moveToFirst()) {
                                            val numIndex = phones.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                            if (numIndex >= 0) {
                                                val number = phones.getString(numIndex)
                                                settingsViewModel.updateSmsForwardTarget(number ?: "")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

            val callPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions[Manifest.permission.CALL_PHONE] == true && permissions[Manifest.permission.ANSWER_PHONE_CALLS] == true) {
            settingsViewModel.updateAutoForwardCalls(true)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Call permissions are required to forward calls") }
        }
    }


        val smsForwardPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val sendGranted = results[Manifest.permission.SEND_SMS] == true
        val receiveGranted = results[Manifest.permission.RECEIVE_SMS] == true
        val readGranted = results[Manifest.permission.READ_SMS] == true
        if (sendGranted && receiveGranted && readGranted) {
            settingsViewModel.updateSmsForwardingEnabled(true)
        } else {
            scope.launch { snackbarHostState.showSnackbar("SMS Send, Receive, and Read permissions are required") }
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            targetPhonePickerLauncher.launch(null)
        } else {
            scope.launch { snackbarHostState.showSnackbar("Contacts permission is required to pick a number") }
        }
    }
    

    
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.READ_SMS] == true) {
            showInboxPicker = true
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(top = 16.dp, start = 24.dp, end = 24.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        
        // Call Forwarding Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.PhoneForwarded, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("Call Forwarding Rules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    
                    Column(modifier = Modifier.padding(top = 16.dp)) {
                            OutlinedTextField(
                                value = settingsState.forwardPhone,
                                onValueChange = { settingsViewModel.updateForwardPhone(it) },
                                label = { Text("Secondary Phone (Call Forwarding Target)") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { 
                                        if (!settingsState.autoForwardCalls) {
                                            if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS))
                                            } else {
                                                settingsViewModel.updateAutoForwardCalls(true)
                                            }
                                        } else {
                                            settingsViewModel.updateAutoForwardCalls(false)
                                        }
                                    }.padding(16.dp).fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Auto-Forward Unknown Calls", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                        Text("Forwards callers not in Important Contacts to your secondary phone and auto-replies to them.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(
                                        checked = settingsState.autoForwardCalls,
                                                                                                                        onCheckedChange = { isChecked -> 
                                            if (isChecked) {
                                                if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ANSWER_PHONE_CALLS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                                                    callPermissionLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS))
                                                } else {
                                                    settingsViewModel.updateAutoForwardCalls(true)
                                                }
                                            } else {
                                                settingsViewModel.updateAutoForwardCalls(false)
                                            }
                                        }
                                    )
                                }
                            }

                            PhoneRulesUI(ruleViewModel)
                        }
                }
            }
        }
        
        // Contextual Integrations Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(24.dp), spotColor = Color.Black.copy(alpha = 0.05f)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.AutoAwesome, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Contextual Integrations",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    BenefitRow(
                        icon = Icons.Rounded.Event,
                        title = "Calendar Sync (Silent Guard)",
                        description = "Automatically block all non-VIP calls when you have a busy event on your calendar."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BenefitRow(
                        icon = Icons.Rounded.Videocam,
                        title = "Deep Work Detection",
                        description = "Instantly mute calls when you are in a Zoom, Teams, or Meet video conference."
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    BenefitRow(
                        icon = Icons.Rounded.DirectionsCar,
                        title = "Drive Mode",
                        description = "Activate automatic WhatsApp redirect replies when connected to your car's Bluetooth."
                    )
                }
            }
        }
    }
}

@Composable
fun PrivacyPledgeDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Privacy Pledge & Permissions", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Availo operates 100% offline on your device. We respect your privacy completely.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "To enable the Auto-Responder and the 'URGENT' emergency keyword, we need SMS and Call Log access. Modern Android versions restrict these for security, but we need them specifically for these two features.",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "• We DO NOT read your personal OTPs.\n• We DO NOT harvest your contacts.\n• Availo makes ZERO network requests to external servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("I Understand & Agree")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
