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

### 3.9 Denial-of-service (DoS) protection
Scope agreed with the course instructor: single-source DoS is in scope; distributed DoS (DDoS) is not.

| Target | Attack | Protection |
|---|---|---|
| Verification service (Apps Script) | Flood of junk requests to burn its daily quota | Cheap checks before any work: body ≤ 8 KB, JSON parse, action allow-list, token format/length. A circuit breaker stops calling Google when > 60 invalid tokens arrive per minute (returns "busy"). |
| Verification service | Flood from one signed-in account | 40 requests per account per 10 minutes, across all actions |
| Verification service | Lock contention blocking other users | Token checks happen outside the script lock; the lock is only held for short state changes, with a 10 s timeout returning "busy" |
| Verification service | Repeated token lookups | Verified tokens cached for 5 minutes by SHA-256 hash |
| Email codes | Email flooding / quota exhaustion | Codes only go to the account's own address; 60 s cooldown, 5 codes per 15 min per account, daily caps (Brevo 290, Gmail keeps 5 spare) |
| Email & device codes | Brute force by re-requesting codes (each new code resets its 5 tries) | Wrong codes are also counted per account: 10 wrong codes → verification locked for 1 hour. Max 5 join codes per request per hour |
| Firestore (free 20k writes/day for the whole project) | One account rewriting documents in a loop to exhaust the quota | Rules allow each document to be rewritten at most once every 20 s; the app syncs at most every 30 s; documents have size limits (≤ 10 top apps, bounded strings) |
| Firebase Auth | Password guessing, sign-up spam | Firebase's built-in server-side rate limiting and abuse protection |
| The app itself | Huge or crafted backup file | 5 MB limit, format check, entry cap (10,000) and validation before anything is restored |
| The app itself | Malformed intent extras from another app to crash it on launch | Intent parsing wrapped; anything unexpected is ignored |
| The app itself | Unbounded local storage growth | History capped at 30 days and 5,000 entries; search input capped at 60 characters |
| The app itself | Background work draining battery | Change check runs hourly via WorkManager; the "Tap to return" watcher stops after 3 minutes |

## 4. Known limitations

| Limitation | Why | Mitigation |
|---|---|---|
| Can't change other apps' permissions directly | Android restricts this to system apps | Guided flow + verification of the real result; explained in the app and user guide |
| Can't see when other apps actually use the camera/microphone | Android doesn't expose other apps' access events to normal apps | History records permission *changes*; Demo mode shows the full History design |
| Account takeover requires password + email access | 2FA is email-based | Codes are short-lived and rate-limited; device approval adds a further step for new phones |
| Device approval trusts any approved device of the account | Devices share one account identity | An attacker would already need password + email code; removing a device revokes it |
| Verification service quota (~390 emails/day) | Free tiers | Daily caps with a clear message; codes only needed once per phone |
| Local data on a rooted phone | Root bypasses app sandboxing | Sensitive local data is encrypted with a Keystore key; Firebase Auth's own sign-in token file is protected only by the sandbox (standard Firebase behaviour) |
| Firestore write quota under a distributed attack | Per-document throttles can't stop many accounts at once (DDoS, out of scope) | Throttles, size limits; Firebase App Check would help but needs Play Store distribution |
| Firebase's RecaptchaActivity is exported | Part of the Firebase Auth library | It only handles Firebase's own verification redirects; the unused GenericIdpActivity is removed |

## 5. Security self-audit (15 Nov 2026)

We attacked our own app the way peer testers would. Fixed issues are marked ✅ Fixed.

| # | Area | What we tried | Finding | Status |
|---|---|---|---|---|
| 1 | 2FA | Sign in through Firebase's REST API with only the password, then read Firestore | Was possible before server-side enforcement | ✅ Fixed: Firestore requires the server-set `mfaSessions` claim (403 PERMISSION_DENIED) |
| 2 | Device approval | Create a device document directly from a modified client | Was possible (client-side approval) | ✅ Fixed: rules deny device creation; only the service adds devices |
| 3 | Brute force | Re-request email codes to reset the 5-try limit | ~2,400 guesses/day possible | ✅ Fixed: 10 wrong codes per account → 1-hour lock |
| 4 | Brute force | Read the device join-code hash from Firestore and crack it offline | Possible when codes were hashed into Firestore | ✅ Fixed: codes now live only on the server |
| 5 | DoS | Flood the verification service with junk tokens | Each request cost a call to Google and held a global lock | ✅ Fixed: pre-checks, circuit breaker, token cache, lock only around state changes |
| 6 | DoS | Rewrite Firestore documents in a loop | Could exhaust the project's free daily writes | ✅ Fixed: 20 s per-document throttle in rules |
| 7 | DoS | Send the app an intent with a malformed extra | Could crash the app on launch | ✅ Fixed: intent parsing guarded |
| 8 | Exported components | List all exported components in the merged release manifest | Firebase's unused GenericIdpActivity was exported | ✅ Fixed: removed; the rest are ours (launcher) or system-permission protected |
| 9 | Backups | Wrong password, edited bytes, renamed photo, 5 MB+ file | All rejected | ✅ Already secure (unit tested) |
| 10 | Local storage | Inspect files in the app's private storage | History, session and device flags are AES-GCM encrypted; preferences hold no personal data | ✅ Already secure |
| 11 | Logging / secrets | Search code for logging; search repo and git history for keys | No logging in app code; `google-services.json`, `local.properties` and keys are git-ignored and were never committed | ✅ Already secure |
| 12 | Identity re-check | Change permissions repeatedly after one password entry | 2-minute grace applied to all actions | ✅ Fixed: high-impact actions always ask |
| 13 | Screen capture | Screenshot the password/code screens | Blocked in release builds | ✅ Already secure (by design; testers will see a black screenshot) |
| 14 | Network | Intercept with a user-installed CA | User CAs are not trusted; HTTPS only | ✅ Already secure |

## 6. Threats from the course lectures (5–8) and how we handle them

| Lecture | Threat | Applies to us? | How it's handled | Fully protected? |
|---|---|---|---|---|
| 8 | **Buffer overflow** (stack/heap smashing, return-to-libc) | Our code: no. Native libraries inside AndroidX/Firebase: in theory | All our code is **Kotlin on the JVM/ART** (a type-safe, bounds-checked language: the lecture's first listed defence). No C/C++/NDK code of our own. Android provides **DEP/NX, ASLR and stack protectors** for the few library `.so` files | ⚠️ Yes for our code. Bugs inside Google's native libraries or Android itself are outside our control; we rely on the platform's defences and up-to-date libraries |
| 8 | **SQL injection** | No | The app never builds SQL. Firestore is accessed with a typed SDK (no query strings); WorkManager's internal database uses parameterised queries | ✅ |
| 8 | **JavaScript injection / XSS** | No | No WebView and no HTML rendering anywhere. All text (including device names and app labels) is shown as plain text by Jetpack Compose | ✅ |
| 8 | **JSON / command injection** | Possible in principle | Requests are built with a JSON library (never string concatenation); the server uses `JSON.parse`/`JSON.stringify`, no `eval`; device names are length-limited and stripped of control characters on client and server | ✅ |
| 8 | **Shellcode / malicious code execution** | No | No dynamic code loading (no `DexClassLoader`, no downloaded code, no `exec`); R8 strips unused code | ✅ |
| 8 | **Credentials in GET requests / plaintext HTTP** | No | Passwords, codes and tokens always go in HTTPS **POST bodies**; cleartext HTTP is disabled | ✅ |
| 8 | **TLS / CA trust problems** | Partially | Only system CAs are trusted (user-installed interception CAs ignored) | ✅ (certificate pinning not used, as Google rotates certificates) |
| 8 | **Same-origin policy** | No | No web content or WebView | N/A |
| 8 | **Display spoofing via Unicode** (e.g. right-to-left override U+202E) | Yes (device names) | Bidirectional control characters stripped from names on client and server | ✅ |
| 7 | **Reconnaissance / reverse engineering (Apktool)** | Yes | R8 shrinks and obfuscates the release APK; no secrets in the APK (the Firebase config and script URL are public by design and every request is authenticated server-side) | ⚠️ Obfuscation slows analysis but can't prevent it; security never relies on hiding code |
| 7 | **Password cracking** (dictionary, brute force) | Yes | Firebase stores passwords hashed (scrypt) and rate-limits guesses server-side; sign-up rejects common passwords and passwords containing the email name; email 2FA means a cracked password alone isn't enough | ✅ |
| 7 | **Port/network scanning** | No | The app runs no server and opens no listening ports | N/A |
| 7 | **Fault injection / recovery** | Yes | Corrupted or tampered encrypted files are rejected and treated as empty (no crash); atomic file writes; failed Settings changes show "Not updated" with Try again; network failures show clear messages | ✅ |
| 7 | **Race conditions** | Yes | History writes serialised with a mutex; the server uses a script lock for state changes; join codes issued in a Firestore transaction | ✅ |
| 7 | **Misconfiguration** | Yes | Firestore rules deny by default; backups disabled; only the launcher activity exported; reviewed in the self-audit | ✅ |
| 7 | **Incorrect file permissions** | Yes | All app files are in private app storage; backups are written only where the user picks, and are encrypted | ✅ |
| 6 | **Re-identification from "anonymous" data** | Yes (an app list can fingerprint a person) | Device summaries are optional, limited to 10 apps, and readable only by the account owner; no analytics or data sharing | ✅ |
| 6 | **Metadata exposure** | Partially | Google (Firebase, Apps Script) and the email provider see that an account exists and when codes are sent | ⚠️ Inherent to using any cloud service; disclosed on the Privacy Information screen |
| 6 | **Repackaged malicious apps (Zombinder)** | Yes: someone could re-sign our APK with malware | Install only from our official Drive link; the user guide publishes the APK's **SHA-256 checksum** so users can verify the file | ⚠️ Can't be fully prevented outside the Play Store (Play Integrity needs Play distribution) |
| 5 | **DoS** | Yes | See §3.9 | ✅ single-source DoS / ❌ DDoS out of scope |
| 5 | **Packet sniffing, TCP hijacking** | Yes | All traffic is TLS (HTTPS) | ✅ |
| 5 | **Social engineering / phishing** | Yes | Code emails say "Never share this code"; device-join prompts say "If you don't recognise this, Deny and change your password"; plain-language explanations throughout | ⚠️ Education reduces but can't eliminate it |
| 5 | **Behaviour-based detection (IDS idea)** | Applied in spirit | The app flags suspicious *permission* behaviour: background access, broad access, sudden new grants (red warnings in History and Notifications) | — |

### What we cannot protect against (honestly)
- **Rooted or compromised phones:** root bypasses Android's sandbox; we limit the damage by encrypting sensitive local data with a Keystore key.
- **Bugs in Android, Google's libraries or Google's servers** (including any native-code buffer overflows there).
- **Repackaged copies of our APK** distributed by someone else.
- **DDoS** from many machines (out of scope per the instructor).
- **A user who gives away both their password and their email code.**

## 7. Testing
- 44 JVM unit tests: permission parsing and risk scoring, change verification, change detection,
  input validation (common-password blocklist, email-name check, Unicode spoofing), email masking,
  backup encryption (wrong password, tampering, non-backup files).
- Manual tests on emulator and a real Samsung phone: sign-in + 2FA, permission change flow with
  verification, Revoke All, history/notifications, backup/restore/delete, and multi-device add/approve/remove.
