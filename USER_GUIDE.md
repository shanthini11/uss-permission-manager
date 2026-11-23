# Permission Tracker — User Guide

**Version 2.0 (fully offline)** · Group 8 · Usable Security and Privacy (CSE 347/652 | DES306/525) · IIIT-Delhi · Monsoon 2026

Team: Alen Sujoy · Manojna Reddy Kamaram · Shanthini Muralidhar · Shreeya Chawla · Vaishnavi Rai

---

## 1. What Permission Tracker does

Permission Tracker helps you **see, understand and manage** which apps on your Android phone can access
your camera, microphone, location, photos, contacts, messages and more.

- **See** every app's permissions in one place, sorted by risk.
- **Understand** each permission in plain language: what it can access, why apps ask, what happens if you say no, and the privacy risk.
- **Act**: choose a safer setting, and the app walks you through applying it and **checks that it worked**.
- **Track** changes over time in an encrypted 30-day history, with alerts when an app's access changes.
- **Protect** your account with two-factor sign-in (password + phone unlock), password checks before big changes and encrypted backups.
- **Private by design:** the app works fully offline. It has no internet permission, so nothing ever leaves your phone.

> **Why changes finish in Android Settings:** for your security, Android only lets its own Settings app
> change another app's permissions (otherwise any app could grant itself camera or SMS access).
> Permission Tracker explains, guides and verifies; you make the final switch in Android Settings, usually with one tap.

---

## 2. Installing the app

Requires **Android 7.0 or newer** and a **screen lock** (PIN, pattern or password; fingerprint/face optional). The file is `PermissionTracker-v2.0-offline.apk`.

### 2.1 Check you have the genuine file (recommended)
Only install the APK from our official link. To make sure the file wasn't modified, compare its SHA-256 checksum:

```
SHA-256: 5b334f2536ecb4ddbd58826615eec1d1166b01263a3302bda7cb0dbc45741ac7
```
The APK is signed by: `CN=Permission Tracker, OU=Group 8 USS, O=IIIT-Delhi, L=New Delhi, C=IN`
(signing certificate SHA-256 `ebcbeb6294ea942ef1dcf2b8cac34a2c3e7e17ea14dfc4e6893c55bd8d7ab716`).
- Windows (PowerShell): `Get-FileHash PermissionTracker-v2.0-offline.apk -Algorithm SHA256`
- macOS (Terminal): `shasum -a 256 PermissionTracker-v2.0-offline.apk`

### 2.2 On an Android phone
1. Download the APK to your phone (Google Drive, email or USB file transfer).
2. **Samsung phones:** Settings → Security and privacy → **Auto Blocker → Off** (Samsung blocks apps from outside the Play Store). Turn it back on after installing.
3. Open the APK. If asked "Allow installing apps from this source?", tap **Settings → Allow from this source**, then go back.
4. Tap **Install → Open**.

### 2.3 On a computer (Windows or Mac) with the Android emulator
1. Install **Android Studio** from <https://developer.android.com/studio> (on a Mac, pick *Apple chip* or *Intel chip* to match your Mac).
2. Open it → **More Actions → Virtual Device Manager → Create device** → choose a phone (e.g. Pixel 8) → choose the recommended system image → **Finish** → press **▶**.
3. **Set a screen lock on the emulator** (required for sign-in): emulator Settings → Security (or *Security & privacy*) → **Screen lock → PIN**, e.g. 1234. Optional: add a fingerprint there, then use the emulator's *⋯ → Fingerprint → Touch sensor* to scan it.
4. **Drag the APK file onto the emulator window.** Open Permission Tracker from the app drawer (swipe up).
5. Emulators have few apps installed: turn on **Demo mode** (§10) to explore with realistic sample apps.

---

## 3. Creating an account and signing in

Your account is created **on this phone only**. No email is sent and no internet is needed.

1. No screen lock yet? The app asks you to set one first (**Open security settings**), then come back.
2. Tap **Create an account**. Enter your name, email (used as your sign-in name) and a password.
   - At least **10 characters**, with a letter and a number; not a common password; not containing your email name. A checklist shows which rules are met.
3. **Confirm it's you:** unlock with your fingerprint, face or screen-lock PIN.
4. **Save your recovery key.** You'll see a 24-character key in 6 groups (e.g. `K7P7-HB8X-PW3S-2T2T-FVZ8-SCPY`). **Write it down on paper** and keep it safe. It's shown **only once**, and it's the only way to reset a forgotten password. Tick *I've written it down*, then type the two groups you're asked for.

**Signing in (two-factor):** your **password**, then your **phone unlock** (fingerprint, face or PIN). Both are checked on the phone.
- Reopening the app within 12 hours needs only the phone unlock. After **5 minutes in the background** the app locks itself.
- After **5 wrong passwords**, sign-in is locked for 30 seconds; each further wrong try doubles the wait (up to 15 minutes).

**Forgot your password?** Tap *Forgot password?* → enter your email, your **recovery key** and a new password → unlock your phone.
**Lost the recovery key too?** On that screen tap **Reset the app**: everything in the app is deleted and you start again. (No one, including us, can recover it.)

---

## 4. Home dashboard

- **% Protected ring:** the share of sensitive permission requests that are blocked or limited, with counts of critical, sensitive and total access allowed.
- **Permission Categories card:** your most-used permission types. Tap to open the Categories tab.
- **Background Risk card:** how many apps can use your data **while they're closed**. Tap to review them.
- **Needs your attention:** the apps you installed with the broadest access.

The bottom bar has four labelled tabs: **Home, Apps, Categories, History**. At the top right: 🔔 Notifications, 👤 Profile, ⚙ Settings.

## 5. Apps

- Every app with sensitive permissions, **your apps first, riskiest first**. Built-in apps are tagged **Pre-installed**.
- **Risk badges:** *High* (background tracking or very broad access), *Medium*, *Low*. Open an app to see **"Why it's high/medium/low risk"** in plain words.
- **Search** by name, and filter by **High risk**, **Background access** or **Camera, mic or location**.

### App page
- Counts of **Allowed / Critical / Background** permissions, the risk reasons, and permissions grouped into **Allowed** and **Not allowed**.
- **Revoke all**: remove all of the app's permissions (asks for your password and shows a warning first).
- **View in OS**: open the app's page in Android Settings.

## 6. Understanding and changing a permission

Tap any permission on an app page to open its sheet:
- **Right now:** the current access.
- **Why apps ask for this**, plus *More about this permission* (what it can access, what happens if you don't allow it, privacy risk).
- **Options** with tags, depending on the permission, e.g. *Allow once* (High privacy), *Allow only while using the app* (Recommended), *Approximate location only*, *Selected photos only*, *Always allow* (Broader access), *Don't allow* (Most private).

**To change it:**
1. Choose an option → **Apply setting**.
2. **Verify your identity:** enter your password. (For single changes it won't ask again for 2 minutes.)
3. Read the short steps ("Tap Permissions → Camera → Don't allow") → **Open Settings**. The first time, allow notifications so we can show the return banner.
4. Make the change in Android Settings. A banner appears: **"Change confirmed ✓ — Tap to return"**. Tap it.
5. You'll see **Permission updated** (or **Permission not updated** with *Try again* if it didn't change). The change is saved to History.

## 7. Categories and Background Risk

- **Categories** groups apps by permission type (Location, Camera, Microphone, Photos & Files, Contacts, SMS, …) with a sensitivity tag, a one-line insight and how many apps have access. Tap **Review** to list the apps; tap an app to open its permission sheet directly.
- **Background Risk** (from Home) lists apps that can use your data while closed, and why that matters.

## 8. History and Notifications

- **History** is your full, encrypted record of permission changes for the last **30 days**: a 7-day chart, what your apps can access, and logs grouped by day. Tap an entry for details (time, before → after, how it happened, device).
- **Notifications** (🔔) shows **recent alerts** (last 7 days) to review and dismiss. Dismissing an alert doesn't remove it from History.
- Permission Tracker checks for changes every time you open it and hourly in the background, so changes made in Android Settings or by an app asking you are also recorded ("Detected").
- A **weekly review reminder** nudges you to check your permissions (can be turned off).

## 9. Settings

| Setting | What it does |
|---|---|
| Permission Notifications | Alerts when an app's permissions change |
| Permission History | Keep the 30-day encrypted history |
| Periodic Review | Weekly review reminder |
| Backup & Restore | Encrypted backup file of your history and preferences |
| Privacy Information | Exactly what data is collected and where it's stored |
| About | Version, purpose and team |
| Delete Local Data | Remove history and preferences from this phone |
| Demo mode | Sample apps for trying the app (§10) |
| Show system apps | Include built-in Android components |

### Backup & Restore
- **Export backup:** enter your account password, choose a **backup password** (you'll need it to restore; we can't recover it), pick where to save the `.ptbackup` file.
- **Import backup:** pick the file, enter its password, **review** what's inside, then **Restore**. History is merged; Android's permissions are **not** changed.

### Delete Local Data
Removes history, preferences and Demo-mode changes from this phone (you stay signed in; Android's permissions and saved backup files aren't affected). Asks for your password and confirmation.

## 10. Demo mode

Settings → **Demo mode**. Shows 12 realistic sample apps (including a deliberately over-permissioned
"Super Flashlight") and a week of sample history, with a yellow banner on every screen. In Demo mode,
**Apply setting** and **Revoke all** change the sample apps directly, so you can try every flow without
leaving the app. Nothing on your phone changes.

## 11. Profile

- Your name and email.
- **Change password:** enter your current password and a new one (same rules as sign-up).
- **Security activity:** a log of sign-ins, unlocks, wrong passwords and lockouts on this phone. If you see wrong passwords you didn't type, someone tried to get in.
- **Log out:** you'll need your password and phone unlock to come back.

**Moving to a new phone:** v2.0 has no cloud sync (by design). Use **Backup & Restore** to move your history and preferences.

---

## 12. Security features you may notice

- **Screenshots are blocked** on password, recovery-key and backup-password screens, and they're hidden in Recents. This is deliberate (protects against screen-recording malware and shoulder-surfing); screenshots elsewhere work normally.
- **Phone unlock prompt** when signing in, creating the account, recovering or resetting.
- **Auto-lock** after 5 minutes away from the app.
- **Password checks** before Revoke all, backups and deleting data (every time), and before changing a permission (not repeated within 2 minutes).
- **Too many wrong passwords** locks sign-in for a short time that grows with each wrong try (max 15 minutes).
- **Other apps' floating windows are hidden** while Permission Tracker is open (Android 12+), and taps through overlays are ignored.
- **Lock-screen notifications** don't show app names.

See `SECURITY.md` in the repository for the full security design.

## 13. FAQ

**It says "Set up a screen lock".** The second sign-in factor uses your phone's lock. Add a PIN, pattern or password in Android Settings → Security, then return.

**It says my screen lock was changed and I must reset.** If you remove your phone's screen lock, Android destroys the key that protects the account. Reset the app and create a new account (restore a backup if you have one).

**Does the app need internet?** No. It has no internet permission at all.

**Why can't the app change permissions by itself?** Android reserves that for its own Settings app, for security. We guide you there and confirm the result.

**Why don't I see when apps actually used my camera?** Android doesn't share other apps' usage events with normal apps. History records permission *changes*; Demo mode shows the full design.

**"Install blocked" on Samsung.** Turn off Auto Blocker temporarily (§2.2).

**"App not installed".** Uninstall any older copy of Permission Tracker first, then install again.


## 14. Known limitations
- Permission changes are completed in Android Settings (Android restriction).
- Other apps' real-time camera/microphone usage isn't available to normal apps.
- No multi-device sync (it would need a server); use encrypted backups to move data.
- A forgotten password **and** lost recovery key can't be recovered; the app must be reset.
- Not distributed through the Play Store, so Play Integrity checks aren't available; verify the checksum (§2.1).

## 15. Privacy summary
Everything stays **on your phone, encrypted** with keys held by Android's secure hardware: your account
(name, email, and only a hash of your password), permission history and security log. The app has **no
internet permission**: no servers, no cloud, no third-party services, no analytics, no ads, no data selling.
Full details: **Settings → Privacy Information**.
