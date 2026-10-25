# Implementation Plan: Availo Streamline, Starred DND Selection & UI Polish

Streamline **Availo** into a modern, privacy-first Availability & Focus Manager by stripping obsolete SMS auto-reply and Truecaller screen scraping, fixing UI spacing issues, elevating WhatsApp & WhatsApp Business auto-response, and implementing a granular Starred Contacts manager for Do Not Disturb (DND) bypass.

---

## 1. User Alignment & Decisions

Based on the clarifying answers:
* **Starred Contacts Filtering:** Implement a dedicated selector allowing users to choose **specific Starred Contacts from a list** who are permitted to bypass DND, rather than blanket-allowing all device starred contacts.
* **Feature Pruning:** Remove **Truecaller screen scraper (Accessibility Service)** and **Carrier SMS Auto-Reply & Scheduled SMS**. Keep **Carrier Forwarding** intact as requested.
* **Messaging Priority:** Maximize **WhatsApp & WhatsApp Business** auto-replies (100% on-device notification listener, zero carrier fees, zero high-risk telephony permissions).

---

## 2. Core Architectural Changes

```
┌────────────────────────────────────────────────────────────────────────┐
│                                AVAILO                                  │
├──────────────────────────┬─────────────────────────┬───────────────────┤
│  WhatsApp Auto-Reply     │   Smart DND & Screening │  Focus Automation │
│  • Personal & Business   │   • Specific Starred    │  • Google Cal Sync│
│  • Targeted rules        │     Contacts Selection  │  • Busy detection │
│  • Group & 1-on-1        │   • Telecom Call Screen │  • Custom presets │
│  • Cooldown throttling   │   • Repeat call bypass  │                   │
└──────────────────────────┴─────────────────────────┴───────────────────┘
```

### A. Granular Starred Contacts for DND Bypass
1. **Device Starred Contact Querying:**
   * Query `ContactsContract.CommonDataKinds.Phone.CONTENT_URI` filtering for `ContactsContract.Contacts.STARRED == 1`.
   * Retrieve contact ID, display name, photo URI (if present), and primary phone number.
2. **Interactive Selection UI:**
   * Provide a clean dialog/sheet in `DashboardScreen.kt` listing all starred contacts on the device.
   * Include checkboxes for each starred contact so users choose exactly which ones are in their **"Allowed DND Bypass"** list.
   * Provide a "Quick Add" to star and add any contact directly if not yet starred.
   * Display selected contacts as neat, removable M3 chips with avatar badges.
3. **Storage & Enforcement:**
   * Persist allowed starred contact keys (normalized phone numbers / contact IDs) in `SettingsRepository` (`ALLOWED_STARRED_DND_CONTACTS`).
   * In `CallHandlingManager.kt`, when incoming calls arrive, check if the caller is in the allowed starred contacts set before granting `Inner Circle` / DND bypass status.
   * In notification listeners, ensure only allowed contacts bypass WhatsApp call/message muting.

### B. Stripping Low-Impact / Risky Features
1. **Remove Truecaller Screen Scraper:**
   * Delete `TruecallerSpamAccessibilityService.kt` and its declaration in `AndroidManifest.xml`.
   * Remove the "Truecaller Smart Spam" card and "Grant Accessibility Access" from `ProtectScreen.kt`.
   * Replace with clean, standard telecom `CallScreeningService` reporting ("Silent Guard" native screening).
2. **Remove SMS Auto-Reply & Scheduled SMS:**
   * Remove `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS` from `AndroidManifest.xml` and diagnostic checks.
   * Remove SMS auto-reply toggles from `ConnectScreen.kt` and `AssistantOnboarding.kt`.
   * Remove `SmsReceiver.kt` SMS handling logic; keep call log / missed call detection purely local.
   * Remove SMS scheduled tasks from `ScheduleScreen.kt` (focusing schedules on Focus Mode / WhatsApp auto-reply schedules).

### C. UI & Spacing Refinements
1. **ConnectScreen Layout Fixes:**
   * Fix the header and button row for "WhatsApp Target Rules": change the `Row` to responsive wrapping or use a compact M3 `FilledTonalIconButton` / `IconButton` with a label so it never clips or wraps awkwardly on compact screens.
   * Enhance padding and card spacing across `AutoReplyTab`.
2. **DashboardScreen StatCards & Spacing:**
   * Optimize the 3 `StatCard` items (`Calls Deflected`, `Focus Today`, `Events`) on compact screens: use `Modifier.weight(1f)` with compact typography and single-line ellipsis to avoid card squishing or overflow.
   * Add consistent 16.dp–20.dp spacing between Hero Card, Vault Card, and Activity Stream.
3. **Visual Polish:**
   * Maintain the emerald, teal, and dark slate color palette.
   * Ensure touch targets are >= 48dp with standard M3 ripple states.

---

## 3. Implementation Steps

### Step 1: Data & Repository Updates
* Add `ALLOWED_STARRED_DND_CONTACTS` and `STARRED_SELECTION_MODE` to `SettingsRepository.kt`.
* Provide helper methods in `SettingsViewModel.kt` to load device starred contacts, toggle contact permission in the allowed set, and save preferences.

### Step 2: DND Starred Selection Modal & UI in DashboardScreen
* Build `StarredContactsSelectionDialog` in `app/src/main/java/com/example/ui/screens/StarredContactsDialog.kt`:
  * Lists all contacts marked starred on the device.
  * Search bar for quick filtering.
  * Checkbox toggle for each starred contact.
  * "Select All" / "Deselect All" convenience actions.
  * Empty state if user has no starred contacts, with an action to pick and star contacts.
* In `DashboardScreen.kt`, update the "Smart Silent Mode" card to display the chosen starred VIPs with an "Edit Starred VIPs" button that opens the selection dialog.

### Step 3: Call & Notification Filtering Integration
* Update `CallHandlingManager.kt`:
  * Check against the user's selected starred contacts set rather than blanket-allowing all starred contacts.
  * Keep repeat caller bypass (2 calls in 5 minutes) as emergency fail-safe.
* Update `TruecallerNotificationListenerService.kt` (clean up and rename reference to `WhatsAppNotificationListenerService` or `AvailoNotificationListenerService`):
  * Strip any Truecaller spam text parsing.
  * Verify WhatsApp caller against the selected starred list for call rejection/bypass.

### Step 4: Stripping Obsolete Features & Permission Cleanup
* Edit `AndroidManifest.xml`: Remove `BIND_ACCESSIBILITY_SERVICE`, `SEND_SMS`, `RECEIVE_SMS`, `READ_SMS`.
* Remove `TruecallerSpamAccessibilityService.kt`.
* Clean `ProtectScreen.kt`: remove Truecaller Accessibility card and switch.
* Clean `ConnectScreen.kt`: remove SMS incoming trigger switch; keep Carrier Forwarding and elevate WhatsApp Auto-Reply.

### Step 5: UI Spacing & Touch Target Adjustments
* In `ConnectScreen.kt`:
  * Refactor "WhatsApp Target Rules" row to prevent overflow and provide clean spacing.
* In `DashboardScreen.kt`:
  * Refine `StatCard` layout with flexible text sizing for narrow screens.
  * Adjust vertical spacing between cards.

### Step 6: Compilation & Verification
* Run `compile_applet` to verify that removing SMS/Truecaller code compiles cleanly without breaking remaining modules.
* Verify Robolectric / unit tests if applicable.

---

## 4. Verification Plan

* **Compilation:** Run `compile_applet` to confirm 0 compilation errors across all modules.
* **Starred Contacts Flow:** Test retrieving starred contacts from `ContactsContract` and selecting a subset.
* **WhatsApp Auto-Reply:** Verify `NotificationListenerService` properly triggers auto-replies for WhatsApp and WhatsApp Business without SMS permissions.
* **UI Responsiveness:** Inspect layout rendering on compact mobile screens to confirm no text clipping or cramped rows.
