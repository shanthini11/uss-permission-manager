# Permission Tracker

**Course:** Usable Security and Privacy (CSE 347/652 | DES306/525), IIIT-Delhi, Monsoon 2026
**Group:** 8 — Alen Sujoy · Manojna Reddy Kamaram · Shanthini Muralidhar · Shreeya Chawla · Vaishnavi Rai

An Android app that helps people **see, understand and manage** which apps can access their camera,
microphone, location, photos, contacts and more. Built from our Phase I user research (71-person survey,
50-participant lo-fi usability study).

**v2.0 runs fully offline:** no servers, no third-party services, no internet permission. The
account and all data stay on the phone, protected by Android's hardware-backed Keystore.

## Documents
| File | What |
|---|---|
| [USER_GUIDE.md](USER_GUIDE.md) | How to install and use every feature |
| [SECURITY.md](SECURITY.md) | Security design, requirement mapping, DoS protection, self-audit, lecture threat mapping |
| [TEST_PLAN.md](TEST_PLAN.md) | Manual test checklist for every feature |

## Features
- Dashboard, apps list, permission categories and background-risk view with plain-language risk explanations
- Permission sheet with granular options; guided change through Android Settings with verification of the result
- Revoke All, encrypted 30-day history, change detection, notifications and weekly review reminders
- On-device account with two-factor sign-in: password + phone unlock (fingerprint, face or PIN)
- Recovery key, brute-force lockout, auto-lock, security activity log, change password
- Password re-check before sensitive actions
- Encrypted backup & restore, delete local data
- Demo mode for evaluating on an emulator

## Tech stack
Kotlin · Jetpack Compose (Material 3) · AndroidX Navigation, Lifecycle, DataStore, WorkManager, Biometric ·
Android Keystore (AES-256-GCM, user-authentication-bound keys) · PBKDF2-HMAC-SHA256 · kotlinx.serialization

## Installing (testers)
Install the release APK as described in [USER_GUIDE.md §2](USER_GUIDE.md#2-installing-the-app).
The phone or emulator needs a **screen lock (PIN, pattern or password)**.

## Building from source
No secrets or config files are needed for a debug build: open in Android Studio, let Gradle sync,
choose a device and press **Run**.

Release builds are signed with a key kept outside git (`keystore/`, `keystore.properties`); without it,
`assembleRelease` produces an unsigned APK.

Unit tests: `./gradlew :app:testDebugUnitTest`
