# Availo

<p align="center">
  <img src="assets/app-icon.png" alt="Availo app icon" width="160"/>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/License-MIT-green.svg" alt="MIT License"/>
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-blue.svg" alt="Android 8.0+"/>
  <img src="https://img.shields.io/badge/Target-Android%2014%20(API%2034)-09D3AC.svg" alt="Android 14 (API 34)"/>
  <img src="https://img.shields.io/badge/Privacy-100%25%20On--Device-purple.svg" alt="100% On-Device"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-orange.svg" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20M3-09D3AC.svg" alt="Jetpack Compose M3"/>
  <img src="https://img.shields.io/badge/Encryption-SQLCipher%20AES--256-blue.svg" alt="SQLCipher AES-256"/>
</p>

**Availo** is an open-source, 100% on-device Android communication gatekeeper and personal boundary automation engine. It manages your availability and guards your attention by automatically filtering calls and responding to messages while you are busy, sleeping, or working — rejecting unknown callers, letting VIPs through Do Not Disturb, and dispatching intelligent, fully customizable auto-replies over WhatsApp and SMS without cloud servers or third-party telemetry.

Source Repository: https://github.com/karanraj-ux/availo

---

## Why Availo Exists

Smartphones suffer from two opposite failure modes:
1. **They interrupt you when you need peace:** Spam callers, noisy group chats, and casual pings shatter your focus, rest, and sleep.
2. **Or they make you completely unreachable to those who matter:** Turning on Airplane Mode or Do Not Disturb cuts off real emergencies, hospital alerts, elderly parents, and trusted friends.

**Availo solves both.** It establishes granular, human-centered boundaries that communicate your availability with grace while preserving genuine reachability for what matters. Everything runs strictly on your device with hardware-backed encryption and zero analytics.

---

## Core Features & Real-World Use Cases

### 1. WhatsApp Auto-Reply & Group Rule Engine
- **Dedicated Targeted Rules (`+ Add Rule`):** Create explicit rules for specific WhatsApp Groups or individual direct chats.
- **WhatsApp Group Intelligence:** Group chats are 100% muted and ignored by default so you are never overwhelmed. Only the groups you explicitly add will ever receive replies.
- **Sequential Multi-Message Responses:** Configure multiple sequential messages (e.g., Message 1: *"Hey team! Stepping away until 3 PM."*, Message 2: *"Ping Alex for urgent deployments."*). Add as many as needed using the `+ Add another reply message` button.
- **Group Presets:** One-tap suggestion chips for common group titles (*Work Team*, *Family Circle*, *Project Alpha*, *Gaming Squad*).
- **Three-Tier Fallback Engine:**
  - **Inner Circle (VIP Contacts):** Marked VIPs receive warm, personalized context.
  - **Saved Contacts:** Address book contacts receive your standard polite message.
  - **Unknown Numbers:** Strangers receive an optional polite boundary or can be completely silent.
- **Native Quick-Reply Execution:** Sends replies directly via WhatsApp notification quick-action intents over your phone's internet connection. Zero WhatsApp web simulators, no root, no external bots.

### 2. Focus Mode (Silent Guard)
- **One-Tap Attention Shield:** Rejects unknown callers, mutes notifications, and coordinates instant auto-responses across both WhatsApp and SMS.
- **Customizable Reply Messages:** Never locked into rigid default strings. Edit, customize, or restore pre-crafted presets (*Deep Work*, *Quiet Hours*, *Meeting*, *Driving*) anytime.
- **Home-Screen Widgets:** Toggle Focus Mode, trigger a 1-hour quick break, or activate the master kill-switch right from your launcher.

### 3. Starred / VIP Call Bypass & Emergency Override
- **Inner Circle Breakthrough:** Starred contacts in your address book break through Do Not Disturb and ring your device.
- **Repeat Caller Urgency:** If anyone calls 3 times within 5 minutes, Availo recognizes persistent urgency and rings through.
- **Emergency Keywords:** Incoming messages containing `URGENT` or `#urgent` bypass silence and trigger an audible 15-second alarm for finding lost devices or handling real-life emergencies.

### 4. Smart Schedule & Calendar Sync
- **Calendar Focus Sync:** Automatically syncs with your device calendar. Whenever a meeting or event marked **Busy** begins, Focus Mode activates automatically and turns off when the event ends.
- **Keyword Filtering:** Trigger Focus based on event titles (e.g., *Sprint Review*, *Focus Block*, *Doctor*).
- **Scheduled Reminders & Auto-SMS:** Schedule messages and reminders locally using Android WorkManager.

### 5. Call Screening & Secondary Line Forwarding
- **Call Screening Integration:** Integrates with Android's native `CallScreeningService` to intercept and reject unwanted incoming calls before your ringtone sounds.
- **Selective Call Forwarding:** Forward unknown or non-VIP callers to a designated secondary phone line or voicemail while dispatching an automatic SMS or WhatsApp status to them.

---

## Capabilities Overview

| Capability | On-Device Implementation |
|---|---|
| **WhatsApp Auto-Reply** | Direct notification quick-reply via Android NotificationListenerService |
| **WhatsApp Group Rules** | Custom group target rules with sequential multi-messages & instant `+` rule builder |
| **SMS Auto-Reply** | Carrier SIM auto-responder with strict rate limiting |
| **VIP DND Bypass** | Contact lookup with DND policy override for starred numbers |
| **Repeat Call Detection** | SQLite-backed windowed frequency analyzer (e.g., 3 calls in 5 minutes) |
| **Calendar Sync** | Android CalendarProvider observer with WorkManager scheduling |
| **Emergency Override** | Keyword detector (`URGENT`, `#urgent`) with alarm audio manager bypass |
| **Call Forwarding** | Optional secondary number divert for non-VIP calls |
| **Local Encryption** | AES-256 SQLCipher encrypted Room database |
| **Launcher Widgets** | Android AppWidgetProvider with instant toggle & 1-hour focus actions |

---

## Safety & Abuse Prevention Safeguards

To prevent loops, carrier fees, or accidental spam, Availo enforces strict built-in safety rules:
1. **WhatsApp Groups Ignored by Default:** Only WhatsApp groups you explicitly configure with a rule are replied to.
2. **Loop & Bot Breaker:** Maximum of 3 auto-replies per contact per hour with message fingerprinting.
3. **Shortcode & OTP Protection:** Banks, two-factor authentication services, delivery alerts, and known shortcodes are never auto-replied.
4. **Carrier Safeguard:** Hard limit of 20 SMS messages per hour to protect your carrier cellular plan.

---

## Privacy by Design & Architecture

- **100% On-Device:** All call analysis, contact tiering, calendar syncing, and auto-replies execute exclusively on the local device.
- **Zero Telemetry:** No Google Analytics, no Firebase, no Mixpanel, no Sentry, no advertising SDKs, and zero developer tracking servers.
- **Hardware-Backed Encryption:** Rules, communication logs, and settings are stored in a local **SQLCipher (AES-256)** database encrypted with keys managed by the **Android Keystore System**.
- **No Contact Harvesting:** Address book entries are queried on-device via Android's local `ContactsContract` and never leave the phone.

---

## Android Permissions Reference

Availo requests only the permissions strictly necessary to perform boundary protection on-device:

| Permission | Purpose |
|---|---|
| `READ_PHONE_STATE` & `READ_CALL_LOG` | Detect incoming calls, identify callers, and trigger missed-call automation |
| `READ_CONTACTS` | Locally distinguish Starred/VIP contacts from saved contacts and unsaved numbers |
| `SEND_SMS` & `RECEIVE_SMS` | Dispatch automated SMS replies via your SIM and detect the `URGENT` emergency keyword |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Listen to incoming WhatsApp notifications to send direct quick-replies |
| `ACCESS_NOTIFICATION_POLICY` | Temporarily adjust Do Not Disturb mode for VIP calls or emergency alarms |
| `READ_CALENDAR` | Read event start/end times and busy status for Calendar Focus Sync |
| `CALL_PHONE` / `ANSWER_PHONE_CALLS` | Execute automated call forwarding rules if enabled |
| `RECEIVE_BOOT_COMPLETED` | Restore scheduled tasks and background monitors upon device restart |
| `USE_BIOMETRIC` | Optional biometric lock for app settings and sensitive rules |

---

## Building from Source

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or command-line Gradle
- JDK 17 or JDK 21
- Android SDK Platform 34 (Android 14)

### Clone & Compile
```bash
# Clone the repository
git clone https://github.com/karanraj-ux/availo.git
cd availo

# Run local JVM unit tests
gradle testDebugUnitTest

# Assemble debug APK
gradle assembleDebug
```
The compiled APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

---

## Open Source Community & Contributing

Availo is developed as an open-source project welcoming issues, feature suggestions, and pull requests.
- Please review [CONTRIBUTING.md](CONTRIBUTING.md) for architecture guidelines, code style, and test commands.
- See [PRIVACY_POLICY.md](PRIVACY_POLICY.md) for our detailed privacy statement.
- License: [MIT License](LICENSE)

*Availo — your availability, on your terms.*
