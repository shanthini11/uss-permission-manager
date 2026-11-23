# Permission Tracker — Security Design (v2.0, fully offline)

Group 8 · Usable Security and Privacy · IIIT-Delhi · Monsoon 2026

This document maps the security and privacy requirements from our Phase I report to how the app
meets them, lists the threats we designed against, and states known limitations honestly.

> **v2.0 change:** the course does not allow third-party services, so v2.0 removes Firebase,
> Google Apps Script and the email provider entirely. Everything (account, 2-factor sign-in,
> history) now runs **on the phone only**. The app has **no internet permission**.

## 1. Architecture at a glance

| Part | Runs where | Holds |
|---|---|---|
| Android app (Kotlin, Jetpack Compose) | The user's phone, nothing else | Account (password hash, recovery-key hash), encrypted history, encrypted security log, preferences |
| Android Keystore (secure hardware / TEE) | The phone | AES-256 keys that never leave the hardware; one key usable only right after a phone unlock |

There is **no server, no cloud, no third-party service and no network access**. The `INTERNET`,
`ACCESS_NETWORK_STATE` and `ACCESS_WIFI_STATE` permissions are explicitly stripped from the merged
manifest (`tools:node="remove"`), so even a library cannot send data. Check:
`aapt dump permissions PermissionTracker-v2.0-offline.apk`.

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
| SR6 | Show what data is used by which apps | Category → app lists; Background Risk page |
| SR7 | Granular access levels | Approximate vs precise location, selected vs all photos, foreground vs background, recognised and shown separately |
| SR8 | Usage history | 30-day encrypted Permission History, change detection on open and hourly in the background |
| SR9 | Explicit feedback after changes | "Permission updated / not updated / revoked / not revoked" results based on re-reading Android's state |
| SR10 | Don't collect or store personal data unnecessarily | No network at all; no analytics/ads; Android auto-backup disabled; see Privacy Information screen |
| UP8 | Two-factor authentication | **Password (knowledge) + phone unlock (possession/biometric)**, both checked on the phone; see 3.2 |
| UP9 | Verify identity before critical actions | Password re-check **every time** before Revoke All, Delete Local Data and backup export/import; before a single permission change too, but not repeated within 2 minutes (usability trade-off) |
| UP6 | Multi-device management | Not in v2.0: syncing devices needs a server, which the course rules don't allow. Encrypted backup files move history between phones instead |
| UP7 | Backup and delete | Password-encrypted backup files; Delete Local Data with confirmation; Reset the app |

## 3. Security controls

### 3.1 Account and password storage
- The account lives only on this phone. The password is **never stored**: only a
  **PBKDF2-HMAC-SHA256 hash, 200,000 iterations, random 16-byte salt**. Comparison is constant-time.
- Password rules: at least **10 characters**, a letter and a number, at most 128, not on the
  common-password blocklist, and must not contain the email name.
- The account file is itself **AES-256-GCM encrypted** with an Android Keystore key, so even the
  hash is unreadable without the phone's secure hardware.
- Error messages are generic ("Email or password is incorrect").

### 3.2 Two-factor sign-in, fully on-device
1. **Password**, checked against the PBKDF2 hash.
2. **Phone unlock** (fingerprint, face, or screen-lock PIN/pattern/password) through Android's
   `BiometricPrompt`. This is not just a UI check: at sign-up the app encrypts a random 32-byte
   secret with a Keystore key created with `setUserAuthenticationRequired(true)` and a 30-second
   validity. Android's secure hardware refuses to use that key unless the phone was unlocked in the
   last 30 seconds. Signing in decrypts the secret and compares its SHA-256 with the stored value.
   A modified app or a script cannot fake this step.
- A screen lock is **required**: without one the app asks the user to set it up first.
- After a full sign-in, reopening within 12 hours needs only the phone unlock. Logging out, or
  after 12 hours, needs the password again.
- **Auto-lock:** after 5 minutes in the background the app locks and asks for the phone unlock again.
- If the phone's screen lock is removed, Android destroys the key; the account can then no longer be
  opened and must be reset (fail closed).

### 3.3 Brute-force protection
- 5 free attempts, then a lockout of 30 s that **doubles with each further wrong attempt, up to
  15 minutes**. The counter is shared across sign-in, Verify your identity, Change password and
  recovery, stored encrypted, and survives restarts (killing the app doesn't reset it).
- "2 tries left" warning before the lock.
- Each guess costs a 200,000-iteration PBKDF2, so offline guessing of a stolen hash is slow, and
  the hash is encrypted with a hardware key anyway.

### 3.4 Account recovery without a server
- At sign-up the app shows a **24-character recovery key** (Crockford Base32, 120 bits of
  randomness, e.g. `K7P7-HB8X-PW3S-2T2T-FVZ8-SCPY`) **once**. The user must tick "I've written it
  down" and then re-type two random groups to prove it.
- Only a PBKDF2 hash of the key is stored. Recovery needs email + recovery key + a fresh phone
  unlock, and is subject to the same lockout.
- If both password and recovery key are lost, **Reset the app** (after a phone unlock) deletes the
  account and all app data. Nobody, including us, can recover it: there is no backdoor.

### 3.5 Data protection on the phone
- Account, auth state, security log and Permission History are encrypted with **AES-256-GCM**
  using separate keys generated inside the **Android Keystore**. GCM detects tampering.
- `allowBackup=false` plus data-extraction rules excluding everything: no cloud backup or
  device-to-device copy of app data.
- Passwords are never put in Compose's saved state; backup passwords are wiped from memory after use.
- **Security activity log** (Profile): sign-ins, unlocks, wrong passwords, lockouts, password
  changes, so the owner can spot someone trying to get in. Encrypted, capped at 300 entries.

### 3.6 Backups
- Encrypted with a user-chosen password: **PBKDF2-HMAC-SHA256 (120,000 iterations, random salt) →
  AES-256-GCM** with the header authenticated. A wrong password or edited file is rejected.
- Imported files are untrusted: 5 MB limit, magic/version check, schema validation, and every entry
  is sanitised before merging. Restoring never changes Android's permissions.

### 3.7 Platform hardening
- **No network permissions** (stripped from the merged manifest). Network security config still
  forbids cleartext and user CAs as defence in depth.
- Only the launcher activity is exported; it reads a single boolean from intents.
- `taskAffinity=""` against task hijacking (StrandHogg).
- Tapjacking: `filterTouchesWhenObscured`, and on Android 12+ `setHideOverlayWindows(true)` hides
  other apps' overlays while ours is visible.
- `FLAG_SECURE` on password, recovery-key and backup-password screens (blocks screenshots, recording
  and the Recents preview). Debug builds skip it so the emulator view works.
- Notifications use private visibility; PendingIntents are immutable and explicit.
- Release builds are shrunk and obfuscated with R8; verbose logging is stripped.

### 3.8 Input validation
- Email, name and password are validated before saving; names are length-limited and stripped of
  control and bidirectional-override characters (Unicode spoofing).
- Recovery keys are normalised (case, spaces, dashes, O→0, I/L→1) and must match the alphabet and length.
- Search input capped at 60 characters.

### 3.9 Denial-of-service (DoS) protection
Scope agreed with the course instructor: single-source DoS is in scope; DDoS is not. With no
server, there is nothing remote to flood; the remaining targets are on the phone.

| Target | Attack | Protection |
|---|---|---|
| Sign-in / recovery | Rapid password guessing to lock the owner out or burn CPU | Exponential lockout capped at 15 min (the owner is never locked out for long); PBKDF2 runs off the main thread |
| The app | Huge or crafted backup file | 5 MB limit, format check, entry cap (10,000) and validation before anything is restored |
| The app | Malformed intent extras from another app to crash it on launch | Intent parsing wrapped; anything unexpected is ignored |
| The app | Corrupted or tampered encrypted files | Rejected and treated as empty, no crash |
| Storage | Unbounded growth | History capped at 30 days / 5,000 entries; security log 300 entries |
| Battery | Background work | Change check runs hourly via WorkManager; the "Tap to return" watcher stops after 3 minutes |

## 4. Known limitations

| Limitation | Why | Mitigation |
|---|---|---|
| Can't change other apps' permissions directly | Android restricts this to system apps | Guided flow + verification of the real result |
| Can't see when other apps actually use the camera/microphone | Android doesn't expose this to normal apps | History records permission *changes*; Demo mode shows the full design |
| No multi-device sync | Needs a server; third-party services aren't allowed | Encrypted backup files |
| Forgotten password + lost recovery key = data lost | No server, no backdoor (by design) | Recovery key shown once and confirmed; Reset the app starts fresh |
| Someone who knows the PIN **and** the password can sign in | Both factors are compromised | Security activity log shows sign-ins; lockout on wrong passwords |
| Lockout uses the phone's clock | Changing the clock needs the phone unlocked; each change only gives one extra guess and waits keep doubling | 200k-iteration hashing; phone-unlock factor still required to finish sign-in |
| Rooted phone | Root bypasses the app sandbox | Data encrypted with hardware Keystore keys; the device-bound key still requires a real unlock |
| Emulators without a screen lock | The second factor needs one | The app guides the tester to set a PIN (see USER_GUIDE) |

## 5. Security self-audit

| # | Area | What we tried | Finding | Status |
|---|---|---|---|---|
| 1 | Network | `aapt dump permissions` on the release APK | No INTERNET / network-state permissions | ✅ Secure by design |
| 2 | 2nd factor bypass | Skip the unlock prompt by calling sign-in directly | Decrypting the secret fails without a real unlock in the last 30 s (Keystore enforced) | ✅ Secure |
| 3 | Brute force | Many wrong passwords, then kill and restart the app | Lockout persists (stored encrypted), doubles up to 15 min | ✅ Secure (unit tested) |
| 4 | Stored password | Inspect `files/` in private storage | Account file is AES-GCM ciphertext; inside is only a PBKDF2 hash | ✅ Secure |
| 5 | Recovery key | Guess or brute force | 120-bit key, hashed, same lockout | ✅ Secure |
| 6 | Screen lock removed | Remove PIN, re-add it, open app | Key invalidated; app asks to reset (fails closed) | ✅ Secure |
| 7 | Identity re-check | Change permissions repeatedly after one password entry | High-impact actions always ask | ✅ Fixed (v1) |
| 8 | Tapjacking | Overlay over "Apply"/"Revoke All" | Touches through overlays ignored; overlays hidden on Android 12+ | ✅ Secure |
| 9 | Exported components | Merged release manifest | Only the launcher activity | ✅ Secure |
| 10 | Backups | Wrong password, edited bytes, renamed photo, 5 MB+ file | All rejected | ✅ Secure (unit tested) |
| 11 | DoS | Malformed intent extras | Ignored, no crash | ✅ Secure |
| 12 | Screen capture | Screenshot the password/recovery-key screens | Blocked in release builds | ✅ Secure (testers see a black screenshot) |
| 13 | Secrets in repo | Search repo and history | Release keystore and passwords are git-ignored | ✅ Secure |

## 6. Threats from the course lectures (5–8) and how we handle them

| Lecture | Threat | How it's handled | Fully protected? |
|---|---|---|---|
| 8 | **Buffer overflow** | All our code is Kotlin (type-safe, bounds-checked); no native code of our own; Android's DEP/NX, ASLR and stack protectors cover platform libraries | ⚠️ Yes for our code; bugs in Android itself are outside our control |
| 8 | **SQL injection** | The app never builds SQL; WorkManager's internal database uses parameterised queries | ✅ |
| 8 | **JavaScript injection / XSS** | No WebView or HTML rendering; all text shown as plain text | ✅ |
| 8 | **JSON / command injection** | JSON built and parsed by kotlinx.serialization with typed schemas; no `eval`/`exec` | ✅ |
| 8 | **Malicious code execution** | No dynamic code loading, no downloaded code | ✅ |
| 8 | **Credentials over the network / TLS problems** | Nothing is ever sent over a network: the app has no internet permission | ✅ N/A by design |
| 8 | **Display spoofing via Unicode** (U+202E) | Bidirectional control characters stripped from names | ✅ |
| 7 | **Reverse engineering (Apktool)** | R8 obfuscation; no secrets in the APK; security relies on Keystore keys, not hidden code | ⚠️ Obfuscation slows analysis but can't prevent it |
| 7 | **Password cracking** | PBKDF2 200k iterations + salt, file encrypted with a hardware key, common-password blocklist, lockout, and the phone-unlock factor | ✅ |
| 7 | **Port/network scanning** | No server, no listening ports, no network permission | ✅ N/A |
| 7 | **Fault injection** | Corrupted/tampered encrypted files rejected without crashing; atomic writes; failed Settings changes show "Not updated" | ✅ |
| 7 | **Race conditions** | Account and history writes serialised with mutexes | ✅ |
| 7 | **Misconfiguration / file permissions** | Backups disabled; only launcher exported; all files in private storage and encrypted | ✅ |
| 6 | **Re-identification / metadata exposure** | Nothing leaves the phone: no servers, analytics or email provider see anything | ✅ |
| 6 | **Repackaged malicious apps (Zombinder)** | The user guide publishes the APK's SHA-256 checksum and signing certificate | ⚠️ Can't be fully prevented outside the Play Store |
| 5 | **DoS** | See §3.9 | ✅ single-source / ❌ DDoS out of scope (and no server to attack) |
| 5 | **Sniffing, TCP hijacking** | No network traffic at all | ✅ N/A |
| 5 | **Social engineering / phishing** | Recovery key screen says never to share it; plain-language explanations; no codes sent that could be phished | ⚠️ Education reduces but can't eliminate it |
| 5 | **Behaviour-based detection (IDS idea)** | Flags suspicious permission behaviour (background access, sudden grants); security log flags repeated wrong passwords | — |

### What we cannot protect against (honestly)
- **Rooted or compromised phones**, and bugs in Android itself.
- **Repackaged copies of our APK** distributed by someone else.
- **A person who knows both the phone's PIN and the user's password.**

## 7. Testing
- JVM unit tests: permission parsing and risk scoring, change verification, change detection,
  input validation (10-character minimum, common-password blocklist, email-name check, Unicode
  spoofing), password hashing (salting, verification), lockout schedule, recovery-key generation and
  normalisation, backup encryption (wrong password, tampering, non-backup files).
- Manual tests on emulator and a real phone: sign-up with recovery key, sign-in with password +
  unlock, auto-lock, lockout, recovery, reset, permission change flow, Revoke All, history,
  notifications, backup/restore/delete. See TEST_PLAN.md.
