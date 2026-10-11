# Contributing Guidelines

Thank you for your interest in contributing to **Availo**! We welcome community contributions from open-source developers worldwide.

## How to Contribute
1. Fork the repository (`https://github.com/karanraj-ux/availo`).
2. Create a new branch for your feature or bugfix (`git checkout -b feature/your-feature-name`).
3. Commit your changes with clear, descriptive commit messages.
4. Push to your branch and open a Pull Request against `main`.

## Development & Build Setup
- **JDK:** OpenJDK 17 or 21 recommended (source/target Java 11 bytecode compatibility).
- **Android SDK:** Compile SDK 34, Min SDK 26 (Android 8.0 Oreo+).
- **Build tool:** Gradle with Kotlin DSL.
- **Run local unit tests:**
  ```bash
  gradle testDebugUnitTest
  ```
- **Build debug APK:**
  ```bash
  gradle assembleDebug
  ```

## Architectural Guidelines
- **100% Privacy by Design:** Availo is an offline, telemetry-free app. Pull requests that introduce tracking, analytics, remote logging, or proprietary cloud SDKs will not be accepted.
- **Kotlin & Jetpack Compose:** Write UI exclusively in Jetpack Compose adhering to Material Design 3 guidelines.
- **Room Database & SQLCipher:** Local storage uses hardware-backed encrypted SQLCipher SQLite. Database schema changes must include Room migrations and companion tests.
- **Accessibility & UX:** All interactive elements must provide minimum 48dp touch targets and descriptive accessibility semantics.

## Bug Reports & Feature Requests
Please use GitHub Issues to submit reports. Include:
- Device model and Android version.
- Exact reproduction steps.
- Sanitized logcat logs (ensure personal phone numbers and names are redacted).
