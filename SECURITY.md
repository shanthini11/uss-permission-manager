# Permission Tracker — Security Design

Group 8 · Usable Security and Privacy · IIIT-Delhi · Monsoon 2026

This document maps the security and privacy requirements from our Phase I report to how the app
meets them, lists the threats we designed against, and states known limitations honestly.

## 1. Architecture at a glance

| Part | Runs where | Holds |
|---|---|---|
| Android app (Kotlin, Jetpack Compose) | The user's phone | Permission data (read live from Android), encrypted history, encrypted session/device flags, preferences |
| Firebase Authentication (free Spark plan) | Google | Email + password accounts, custom claim `mfaSessions` |
| Cloud Firestore (free Spark plan) | Google, `asia-south2` | Device list, optional device summaries, review preferences |
| Verification service (Google Apps Script, free) | Google | Email codes and device join codes (5 min), service-account access for admin actions |

Android only lets its own Settings app change another app's permissions (the
`GRANT/REVOKE_RUNTIME_PERMISSIONS` permissions are reserved for system apps). This is a security
feature: otherwise any app could silently grant itself camera or SMS access. Permission Tracker
therefore explains, tracks and guides; the user makes the final switch in Android Settings and the
app verifies the result.

## 2. Requirements from our Phase I report

| ID | Requirement | How it is met |
|---|---|---|
| SR1 | See which apps have access to what data | Apps list, app detail, Permission Categories, Home dashboard, all read live from Android's PackageManager |
| SR2 | Clearly indicate sensitive permissions | CRITICAL / SENSITIVE / LOW tags; red "Always allowed" for background access; risk badges with plain-language reasons |
| SR3 | Support least privilege | Granular options in the permission sheet (Allow once, While using = Recommended, Approximate location, Selected photos only); Background Risk page |
| SR4 | Revoke permissions even after granting | Per-permission change flow and Revoke All, both verified against Android's real state afterwards |
| SR5 | Explain risks of each option | Every category has "What it can access / Why apps ask / If you don't allow it / Privacy risk"; each option has a description and tag |
| SR6 | Show what data is used by which apps | Category → app lists; device summaries across the user's phones |
| SR7 | Granular access levels | Approximate vs precise location, selected vs all photos, foreground vs background, recognised and shown separately |
| SR8 | Usage history | 30-day encrypted Permission History, change detection on open and hourly in the background |
| SR9 | Explicit feedback after changes | "Permission updated / not updated / revoked / not revoked" results based on re-reading Android's state; one-tap "Change confirmed" banner |
| SR10 | Don't collect or store personal data unnecessarily | No analytics/ads; history never leaves the phone; Android auto-backup disabled; device summaries optional; see Privacy Information screen |
| UP8 | Two-factor authentication | Email one-time code after every password sign-in, enforced **server-side** (see 3.2) |
| UP9 | Verify identity before critical actions | Password re-check **every time** before Revoke All, removing devices, Delete Local Data and backup export/import; before a single permission change too, but not repeated within 2 minutes (usability trade-off for frequent, low-risk changes) |
| UP6 | Multi-device management | My Devices with server-enforced approval of new devices (see 3.3) |
| UP7 | Backup and delete | Password-encrypted backup files; Delete Local Data with confirmation |

## 3. Security controls

### 3.1 Authentication
- Firebase email/password; minimum 8 characters with a letter and a number (checked in the app, enforced again by Firebase).
- Error messages never reveal whether an email has an account ("Email or password is incorrect";
  password reset always says "If an account exists…").
- Wrong passwords are rate-limited by Firebase on the server.

### 3.2 Server-enforced two-factor authentication
- After the password, a 6-digit code is emailed. Codes are generated and checked **only on the
  server**, stored as an HMAC (never in plain text), expire after 5 minutes, allow 5 attempts,
  have a 60-second resend cooldown and a 5-per-15-minutes cap.
- On a correct code, the server adds that login session's `auth_time` to the user's
  `mfaSessions` custom claim (most recent 5 kept).
- **Firestore rules require the request's `auth_time` to be in `mfaSessions`.** Signing in with
  only the password, including directly through Firebase's REST API, yields no data. A newer
  password-only sign-in is rejected because its `auth_time` isn't listed.
- "Verify your identity" checks the password via Firebase's password endpoint without starting a
  new session, so it doesn't invalidate the 2FA marker.

### 3.3 Server-enforced device approval
- Each install has a random device ID (not a hardware identifier).
- **Firestore rules forbid clients from creating devices.** Only the verification service (admin
  credentials) adds a device: the first device when the account has none, or a new device after
  either (a) a join code issued at the request of an already-approved device, or (b) a fresh email code.
- Join codes live only on the server (HMAC, 5 minutes, 5 attempts) and are bound to the requesting device.
- Removing a device deletes its record; the removed phone must be approved again.

### 3.4 Data protection on the phone
- Permission History and the session/device flags are encrypted with **AES-256-GCM** using a key
  generated inside the **Android Keystore** (it never leaves secure hardware). GCM detects tampering.
- `allowBackup=false` plus data-extraction rules excluding everything: no cloud backup or
  device-to-device copy of app data.
- Firestore uses an in-memory cache only (no device summaries written to disk).
- Passwords are never put in Compose's saved state; backup passwords are wiped from memory after use.

### 3.5 Backups
- Encrypted with a user-chosen password: **PBKDF2-HMAC-SHA256 (120,000 iterations, random salt) →
  AES-256-GCM** with the header authenticated. A wrong password or edited file is rejected.
- Imported files are untrusted: 5 MB limit, magic/version check, schema validation, and every entry
  is sanitised (lengths, package-name format, timestamps) before merging.
- Restoring never changes Android's permissions.

### 3.6 Network
- HTTPS only (`cleartextTrafficPermitted=false`); only system CAs trusted, so user-installed
  interception certificates are ignored.
- Requests to the verification service carry a Firebase ID token that the server verifies on every call.
  Redirects are followed only to Google-owned HTTPS hosts. Request bodies are built with a JSON library.

### 3.7 Platform hardening
- Only the launcher activity is exported; it reads a single boolean from intents.
- `taskAffinity=""` against task hijacking (StrandHogg); `filterTouchesWhenObscured` against tapjacking overlays.
- `FLAG_SECURE` on password, code and backup-password screens and on the device-approval dialog
  (blocks screenshots/recording and the Recents preview). Debug builds skip it so the emulator view works.
- Notifications use private visibility: app names aren't shown on the lock screen.
- PendingIntents are immutable and explicit.
- Release builds are shrunk and obfuscated with R8; verbose logging is stripped.

### 3.8 Input validation
- Email, name and password checked client-side and again by Firebase.
- Firestore rules validate every client write: allowed keys, types, string lengths, list sizes,
  timestamps not in the future, immutable identity fields.
- Search input capped; device names stripped of control characters and length-limited (client and server).

## 4. Known limitations

| Limitation | Why | Mitigation |
|---|---|---|
| Can't change other apps' permissions directly | Android restricts this to system apps | Guided flow + verification of the real result; explained in the app and user guide |
| Can't see when other apps actually use the camera/microphone | Android doesn't expose other apps' access events to normal apps | History records permission *changes*; Demo mode shows the full History design |
| Account takeover requires password + email access | 2FA is email-based | Codes are short-lived and rate-limited; device approval adds a further step for new phones |
| Device approval trusts any approved device of the account | Devices share one account identity | An attacker would already need password + email code; removing a device revokes it |
| Verification service quota (~390 emails/day) | Free tiers | Daily caps with a clear message; codes only needed once per phone |
| Local data on a rooted phone | Root bypasses app sandboxing | Sensitive local data is encrypted with a Keystore key |

## 5. Testing
- 41 JVM unit tests: permission parsing and risk scoring, change verification, change detection,
  input validation, email masking, backup encryption (wrong password, tampering, non-backup files).
- Manual tests on emulator and a real Samsung phone: sign-in + 2FA, permission change flow with
  verification, Revoke All, history/notifications, backup/restore/delete, and multi-device add/approve/remove.
