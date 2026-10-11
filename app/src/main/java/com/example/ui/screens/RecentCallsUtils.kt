package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallMade
import androidx.compose.material.icons.rounded.CallMissed
import androidx.compose.material.icons.rounded.CallReceived
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

data class CallLogItem(
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long
)

fun getRecentCalls(context: Context): List<CallLogItem> {
    val list = mutableListOf<CallLogItem>()
    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALL_LOG) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return list
    }
    try {
        val cursor = context.contentResolver.query(
            android.provider.CallLog.Calls.CONTENT_URI,
            null,
            null,
            null,
            android.provider.CallLog.Calls.DATE + " DESC LIMIT 50"
        )
        cursor?.use {
            val numberIndex = it.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
            val nameIndex = it.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
            val typeIndex = it.getColumnIndex(android.provider.CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(android.provider.CallLog.Calls.DATE)
            while(it.moveToNext()) {
                list.add(CallLogItem(
                    number = it.getString(numberIndex) ?: "Unknown",
                    name = if (nameIndex != -1) it.getString(nameIndex) else null,
                    type = if (typeIndex != -1) it.getInt(typeIndex) else 0,
                    date = if (dateIndex != -1) it.getLong(dateIndex) else 0L
                ))
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return list
}

@Composable
fun RecentCallsList(context: Context, onCallClick: (String) -> Unit) {
    val recentCalls by androidx.compose.runtime.produceState<List<CallLogItem>>(initialValue = emptyList(), context) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            getRecentCalls(context)
        }
    }
    
    if (recentCalls.isEmpty()) {
        EmptyStateView("No recent calls", "Your call history will appear here.", Icons.Rounded.Call)
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(
                items = recentCalls,
                key = { call -> "${call.number}_${call.date}" }
            ) { call ->
                val icon = when (call.type) {
                    android.provider.CallLog.Calls.INCOMING_TYPE -> Icons.Rounded.CallReceived
                    android.provider.CallLog.Calls.OUTGOING_TYPE -> Icons.Rounded.CallMade
                    android.provider.CallLog.Calls.MISSED_TYPE -> Icons.Rounded.CallMissed
                    android.provider.CallLog.Calls.REJECTED_TYPE -> Icons.Rounded.CallMissed
                    else -> Icons.Rounded.Call
                }
                val iconTint = if (call.type == android.provider.CallLog.Calls.MISSED_TYPE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCallClick(call.number) }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(call.name ?: call.number, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (call.name != null) {
                            Text(call.number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(call.date))
                    Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun EmptyStateView(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Box(
                modifier = Modifier.size(96.dp).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = "Empty", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}
