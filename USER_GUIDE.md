# Permission Tracker — User Guide

**Version 1.0** · Group 8 · Usable Security and Privacy (CSE 347/652 | DES306/525) · IIIT-Delhi · Monsoon 2026

Team: Alen Sujoy · Manojna Reddy Kamaram · Shanthini Muralidhar · Shreeya Chawla · Vaishnavi Rai

---

## 1. What Permission Tracker does

Permission Tracker helps you **see, understand and manage** which apps on your Android phone can access
your camera, microphone, location, photos, contacts, messages and more.

- **See** every app's permissions in one place, sorted by risk.
- **Understand** each permission in plain language: what it can access, why apps ask, what happens if you say no, and the privacy risk.
- **Act**: choose a safer setting, and the app walks you through applying it and **checks that it worked**.
- **Track** changes over time in an encrypted 30-day history, with alerts when an app's access changes.
- **Protect** your account with email two-factor sign-in, password checks before big changes, encrypted backups and device approval.

> **Why changes finish in Android Settings:** for your security, Android only lets its own Settings app
> change another app's permissions (otherwise any app could grant itself camera or SMS access).
> Permission Tracker explains, guides and verifies; you make the final switch in Android Settings, usually with one tap.

---

## 2. Installing the app

Requires **Android 7.0 or newer**. The file is `PermissionTracker-v1.0.apk`.

### 2.1 Check you have the genuine file (recommended)
Only install the APK from our official link. To make sure the file wasn't modified, compare its SHA-256 checksum:

```
SHA-256: 2a4a6406ad431dde899bd3f13588c0d6856a1405f59dd338dc527bf4e5dbb253
```
The APK is signed by: `CN=Permission Tracker, OU=Group 8 USS, O=IIIT-Delhi, L=New Delhi, C=IN`.
- Windows (PowerShell): `Get-FileHash PermissionTracker-v1.0.apk -Algorithm SHA256`
- macOS (Terminal): `shasum -a 256 PermissionTracker-v1.0.apk`

### 2.2 On an Android phone
1. Download the APK to your phone (Google Drive, email or USB file transfer).
2. **Samsung phones:** Settings → Security and privacy → **Auto Blocker → Off** (Samsung blocks apps from outside the Play Store). Turn it back on after installing.
3. Open the APK. If asked "Allow installing apps from this source?", tap **Settings → Allow from this source**, then go back.
4. Tap **Install → Open**.

### 2.3 On a computer (Windows or Mac) with the Android emulator
1. Install **Android Studio** from <https://developer.android.com/studio> (on a Mac, pick *Apple chip* or *Intel chip* to match your Mac).
2. Open it → **More Actions → Virtual Device Manager → Create device** → choose a phone (e.g. Pixel 8) → choose the recommended system image → **Finish** → press **▶**.
3. **Drag the APK file onto the emulator window.** Open Permission Tracker from the app drawer (swipe up).
4. Emulators have few apps installed: turn on **Demo mode** (§10) to explore with realistic sample apps.

---

## 3. Creating an account and signing in

1. Tap **Create an account**. Enter your name, email and a password.
   - At least 8 characters, with a letter and a number; not a common password; not containing your email name. A checklist shows which rules are met.
2. **Account created** → **Continue**. We email you a **6-digit code**.
   - It expires in **5 minutes**. Can't find it? **Check your Spam folder** and mark it "Not spam".
   - "Resend code" is available after 60 seconds.
3. Enter the code → you're in.

**Every time you sign in** with your password, you'll also enter a fresh email code (two-factor authentication).
**Forgot your password?** Tap *Forgot password?* on the sign-in screen; we email you a reset link.

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
| My Devices | Manage the phones on your account (§11) |
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

## 11. Using more than one device

- The **first phone** you sign in on is added automatically.
- On **another phone**, after signing in you'll see **Add this device** → **Continue** → **Verify this device**.
  Open Permission Tracker on a phone that's already connected: it shows a **6-digit code**. Enter it on the new phone.
  No other phone at hand? Tap **Verify with email**.
- **My Devices** (Settings or Profile) lists your phones. Tap one to see its permission summary.
  **Select devices → Remove selected** disconnects a phone (password required); it must be approved again to reconnect.
- **Share this phone's summary** controls whether this phone's counts and 10 riskiest apps are visible to your other phones.
- If an unknown device asks to join, tap **Deny** and change your password.

---

## 12. Security features you may notice

- **Screenshots are blocked** on password, code and backup-password screens, and they're hidden in Recents. This is deliberate (protects against screen-recording malware and shoulder-surfing); screenshots elsewhere work normally.
- **Password checks** before Revoke all, removing devices, backups and deleting data (every time), and before changing a permission (not repeated within 2 minutes).
- **Too many wrong codes** locks verification for 1 hour. **Too many requests** shows "Please wait a few minutes".
- **Saving device settings twice within ~20 seconds** shows "Wait a few seconds and try again" (protection against abuse).
- **Lock-screen notifications** don't show app names.

See `SECURITY.md` in the repository for the full security design.

## 13. FAQ

**I didn't get the email code.** Check Spam/Promotions. Wait 60 seconds and tap *Resend code*. The service can send about 390 codes a day; if it's reached today's limit you'll see a message.

**Why can't the app change permissions by itself?** Android reserves that for its own Settings app, for security. We guide you there and confirm the result.

**Why don't I see when apps actually used my camera?** Android doesn't share other apps' usage events with normal apps. History records permission *changes*; Demo mode shows the full design.

**"Install blocked" on Samsung.** Turn off Auto Blocker temporarily (§2.2).

**"App not installed".** Uninstall any older copy of Permission Tracker first, then install again.

**The app says "You're offline".** Connect to the internet. Phones already approved keep working offline for local features.

## 14. Known limitations
- Permission changes are completed in Android Settings (Android restriction).
- Other apps' real-time camera/microphone usage isn't available to normal apps.
- Email delivery depends on free email services (~390 codes/day) and may land in Spam.
- Not distributed through the Play Store, so Play Integrity checks aren't available; verify the checksum (§2.1).

## 15. Privacy summary
Your permission history stays **on your phone, encrypted**. Your account (name, email) is stored by Google Firebase.
Your device list and optional device summaries are stored in your Firebase account and readable only by you.
Your email address is used to send sign-in codes. No analytics, no ads, no data selling.
Full details: **Settings → Privacy Information**.
