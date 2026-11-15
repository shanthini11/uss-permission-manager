# Permission Tracker

**Course:** Usable Security and Privacy (CSE 347/652 | DES306/525), IIIT-Delhi, Monsoon 2026
**Group:** 8 — Alen Sujoy · Manojna Reddy Kamaram · Shanthini Muralidhar · Shreeya Chawla · Vaishnavi Rai

An Android app that helps people **see, understand and manage** which apps can access their camera,
microphone, location, photos, contacts and more. Built from our Phase I user research (71-person survey,
50-participant lo-fi usability study).

## Documents
| File | What |
|---|---|
| [USER_GUIDE.md](USER_GUIDE.md) | How to install and use every feature |
| [SECURITY.md](SECURITY.md) | Security design, requirement mapping, DoS protection, self-audit, lecture threat mapping |
| [TEST_PLAN.md](TEST_PLAN.md) | Manual test checklist for every feature |
| [backend/otp-service/SETUP.md](backend/otp-service/SETUP.md) | Setting up the free verification service |
| [backend/firestore.rules](backend/firestore.rules) | Firestore security rules |

## Features
- Dashboard, apps list, permission categories and background-risk view with plain-language risk explanations
- Permission sheet with granular options; guided change through Android Settings with verification of the result
- Revoke All, encrypted 30-day history, change detection, notifications and weekly review reminders
- Email/password sign-in with server-enforced email 2FA; password re-check before sensitive actions
- Encrypted backup & restore, delete local data
- Multi-device management with server-enforced device approval
- Demo mode for evaluating on an emulator

## Tech stack
Kotlin · Jetpack Compose (Material 3) · AndroidX Navigation, Lifecycle, DataStore, WorkManager ·
Firebase Authentication & Cloud Firestore (free Spark plan) · Google Apps Script verification service ·
kotlinx.serialization · Android Keystore (AES-256-GCM)

## Installing (testers)
Install the release APK as described in [USER_GUIDE.md §2](USER_GUIDE.md#2-installing-the-app).

## Building from source
Secrets and machine-specific config are **not** in the repository. To build you need:
1. `app/google-services.json` from the Firebase console (Project settings → Your apps → Android app).
2. `local.properties` with the verification service URL: `otp.service.url=https://script.google.com/macros/s/…/exec`
3. Open in Android Studio, let Gradle sync, choose a device and press **Run** (debug build).

Release builds are signed with a key kept outside git (`keystore/`, `keystore.properties`); without it,
`assembleRelease` produces an unsigned APK.

Unit tests: `./gradlew :app:testDebugUnitTest`
