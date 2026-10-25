package com.example.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceStarredContact(
    val id: String,
    val name: String,
    val number: String,
    val cleanNumber: String
)

fun queryDeviceStarredContacts(context: Context): List<DeviceStarredContact> {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return emptyList()
    }
    val results = mutableListOf<DeviceStarredContact>()
    val seenNumbers = mutableSetOf<String>()
    try {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1"
        val sortOrder = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"

        context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

            while (cursor.moveToNext()) {
                val id = if (idIdx >= 0) cursor.getString(idIdx) ?: "" else ""
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: "Unknown" else "Unknown"
                val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                val clean = number.filter { it.isDigit() }

                if (clean.isNotEmpty() && !seenNumbers.contains(clean)) {
                    seenNumbers.add(clean)
                    results.add(DeviceStarredContact(id = id, name = name, number = number, cleanNumber = clean))
                }
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("StarredContactsDialog", "Failed to query starred contacts", e)
    }
    return results
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StarredContactsDialog(
    currentAllowed: String,
    currentMode: String,
    onDismiss: () -> Unit,
    onSave: (allowedContacts: String, mode: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var starredList by remember { mutableStateOf<List<DeviceStarredContact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedMode by remember { mutableStateOf(if (currentMode.isNotBlank()) currentMode else "SELECTED_STARRED") }

    // Set of allowed clean numbers or "name (number)" entries
    val selectedAllowed = remember {
        val set = mutableStateListOf<String>()
        if (currentAllowed.isNotBlank()) {
            currentAllowed.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach {
                set.add(it)
            }
        }
        set
    }

    fun reloadStarredContacts() {
        scope.launch(Dispatchers.IO) {
            val list = queryDeviceStarredContacts(context)
            withContext(Dispatchers.Main) {
                starredList = list
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        reloadStarredContacts()
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                            if (idIndex >= 0) {
                                val id = cursor.getString(idIndex)
                                val name = if (nameIndex >= 0) cursor.getString(nameIndex) else "Unknown"
                                
                                // Star this contact in Android Contacts database
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                    val values = ContentValues().apply {
                                        put(ContactsContract.Contacts.STARRED, 1)
                                    }
                                    context.contentResolver.update(
                                        ContactsContract.Contacts.CONTENT_URI,
                                        values,
                                        "${ContactsContract.Contacts._ID} = ?",
                                        arrayOf(id)
                                    )
                                }

                                // Get phone number
                                context.contentResolver.query(
                                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                    "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                    arrayOf(id),
                                    null
                                )?.use { phoneCursor ->
                                    if (phoneCursor.moveToFirst()) {
                                        val num = phoneCursor.getString(0) ?: ""
                                        val entry = "$name ($num)"
                                        withContext(Dispatchers.Main) {
                                            if (!selectedAllowed.contains(entry)) {
                                                selectedAllowed.add(entry)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    reloadStarredContacts()
                } catch (e: Exception) {
                    android.util.Log.e("StarredContactsDialog", "Error starring contact", e)
                }
            }
        }
    }

    val filteredList = remember(starredList, searchQuery) {
        if (searchQuery.isBlank()) starredList
        else starredList.filter {
            it.name.contains(searchQuery, ignoreCase = true) || it.number.contains(searchQuery)
        }
    }

    fun isContactSelected(contact: DeviceStarredContact): Boolean {
        return selectedAllowed.any { allowed ->
            val cleanAllowed = allowed.filter { it.isDigit() }
            allowed.contains(contact.name, ignoreCase = true) ||
            (cleanAllowed.isNotEmpty() && contact.cleanNumber.isNotEmpty() &&
                (cleanAllowed == contact.cleanNumber ||
                 (cleanAllowed.length >= 7 && contact.cleanNumber.length >= 7 && cleanAllowed.takeLast(10) == contact.cleanNumber.takeLast(10))))
        }
    }

    fun toggleContact(contact: DeviceStarredContact) {
        val entry = "${contact.name} (${contact.number})"
        val existing = selectedAllowed.find { allowed ->
            val cleanAllowed = allowed.filter { it.isDigit() }
            allowed.contains(contact.name, ignoreCase = true) ||
            (cleanAllowed.isNotEmpty() && contact.cleanNumber.isNotEmpty() &&
                (cleanAllowed == contact.cleanNumber ||
                 (cleanAllowed.length >= 7 && contact.cleanNumber.length >= 7 && cleanAllowed.takeLast(10) == contact.cleanNumber.takeLast(10))))
        }
        if (existing != null) {
            selectedAllowed.remove(existing)
        } else {
            selectedAllowed.add(entry)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(Color(0xFFFFB300).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(22.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Starred DND Filter",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Choose who bypasses Silent Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Selector
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = "SELECTED_STARRED" }
                        ) {
                            RadioButton(
                                selected = selectedMode == "SELECTED_STARRED",
                                onClick = { selectedMode = "SELECTED_STARRED" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("Selected Starred Only (Recommended)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text("Only the contacts checked below will ring through DND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedMode = "ALL_STARRED" }
                        ) {
                            RadioButton(
                                selected = selectedMode == "ALL_STARRED",
                                onClick = { selectedMode = "ALL_STARRED" }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text("All Starred Contacts", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text("Any contact marked starred on your phone bypasses DND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search & Quick Action Row
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search starred contacts...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Selection Stats & Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countAllowed = if (selectedMode == "ALL_STARRED") starredList.size else starredList.count { isContactSelected(it) }
                    Text(
                        text = if (selectedMode == "ALL_STARRED") "All ${starredList.size} contacts allowed" else "$countAllowed of ${starredList.size} allowed",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(
                            onClick = {
                                starredList.forEach { c ->
                                    val entry = "${c.name} (${c.number})"
                                    if (!selectedAllowed.contains(entry)) selectedAllowed.add(entry)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Select All", style = MaterialTheme.typography.labelSmall)
                        }
                        TextButton(
                            onClick = { selectedAllowed.clear() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Contact List
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                    } else if (starredList.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "No Starred Contacts on Device",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Star your closest family & friends in Google Contacts, or tap below to pick and star someone now.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = { contactPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Pick & Star Contact")
                            }
                        }
                    } else if (filteredList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No contacts match \"$searchQuery\"", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredList, key = { it.id + it.cleanNumber }) { contact ->
                                val isSelected = isContactSelected(contact)
                                val isDimmed = selectedMode == "ALL_STARRED"

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected && !isDimmed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isDimmed) { toggleContact(contact) }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Initials Avatar
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .background(
                                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = contact.name.take(1).uppercase(),
                                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = contact.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            if (contact.number.isNotBlank()) {
                                                Text(
                                                    text = contact.number,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        if (isDimmed) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = "Always Allowed",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        } else {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = { toggleContact(contact) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Footer button to star more contacts
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedButton(
                                    onClick = { contactPickerLauncher.launch(null) },
                                    modifier = Modifier.fillMaxWidth().height(44.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Star Another Contact from Phone", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val finalString = selectedAllowed.joinToString(",")
                            onSave(finalString, selectedMode)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save VIP Preferences", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
