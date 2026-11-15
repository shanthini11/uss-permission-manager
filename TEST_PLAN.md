# Permission Tracker — Manual Test Plan

Group 8 · Usable Security and Privacy · Version 1.0

Use this checklist to test every feature by hand. Tick each box, and for anything that fails write
down **what you did, what you expected, what happened**, plus a screenshot.

---

## 0. Setup

You test the **APK** (the same file classmates will get). You don't need to build the code.

### Option A — Android phone (easiest, most realistic)
1. Download `PermissionTracker-v1.0.apk` from the shared Drive link onto the phone.
2. **Samsung only:** Settings → Security and privacy → **Auto Blocker → Off** (turn it back on after installing).
3. Open the APK → if asked, allow **Install unknown apps** for Drive/Files → **Install** → **Open**.

### Option B — MacBook with the Android emulator
1. Install **Android Studio** for Mac from <https://developer.android.com/studio> (choose *Apple chip* or *Intel chip* to match your Mac: Apple menu → About This Mac).
2. Open Android Studio → **More Actions → Virtual Device Manager → Create device** → pick **Pixel 8** (or any phone) → choose the newest system image marked **Recommended** (download it) → **Finish**.
3. Press **▶** to start the emulator.
4. **Drag `PermissionTracker-v1.0.apk` from Finder onto the emulator window.** It installs in a few seconds; open it from the app drawer (swipe up).
5. Mouse = finger: click to tap, click-and-drag to scroll/swipe.

> Tip: the emulator has very few apps, so turn on **Demo mode** (Settings ⚙ → Demo mode) to test with
> 12 realistic sample apps. Turn it off to test with the emulator's real apps.

### Accounts
- Create **your own account** with an email you can open. Codes may land in **Spam**; mark them "Not spam".
- For lockout tests (§2.6) use a **second, throwaway account**, because it locks for 1 hour.

---

## 1. Landing, sign-up and sign-in

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 1.1 | Open the app | Landing page: shield logo, "Sign in", "Create an account" | ☐ |
| 1.2 | Create an account → type password `abc` | Checklist shows ✗ for 8 characters / letter / number | ☐ |
| 1.3 | Password `Password123` | "Not a common password" ✗; Create account shows an error | ☐ |
| 1.4 | Password containing your email name (e.g. `riya2026` for riya@…) | "Doesn't contain your email name" ✗ | ☐ |
| 1.5 | Confirm password different from password | "The passwords don't match." | ☐ |
| 1.6 | Invalid email (`abc@`) | "Enter a valid email address…" | ☐ |
| 1.7 | Valid details → Create account | **Account created** screen → Continue | ☐ |
| 1.8 | Email | 6-digit code arrives within ~1 min (check Spam) | ☐ |
| 1.9 | Code screen | Email shown partly hidden (e.g. ri•••@gmail.com); countdown from 5:00; "Resend code in 60s" | ☐ |
| 1.10 | Enter the code | Lands on **Home** | ☐ |
| 1.11 | Profile 👤 → Log out → confirm | Back to Landing | ☐ |
| 1.12 | Sign in with a **wrong password** | "Email or password is incorrect." (doesn't say which) | ☐ |
| 1.13 | Sign in correctly | Asks for a **new email code** → Home | ☐ |
| 1.14 | Forgot password → enter any email | "If an account exists… we've sent a link" (same message for unknown emails) | ☐ |
| 1.15 | Use the reset link from the email, then sign in with the new password | Works | ☐ |

## 2. Security behaviours

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 2.1 | Code screen → enter a wrong code | "That code isn't right. N tries left." | ☐ |
| 2.2 | Wait 5+ minutes, enter the old code | "That code has expired…" | ☐ |
| 2.3 | Tap Resend twice quickly | Second time: wait message / button disabled | ☐ |
| 2.4 | Try to screenshot the sign-in, code or password screens (release APK) | Screenshot is blocked or black (deliberate) | ☐ |
| 2.5 | Open Recents (□) while on the code screen | App preview is hidden/blank | ☐ |
| 2.6 | **Throwaway account:** enter 10 wrong codes (use Resend between) | "Too many wrong codes… locked for 1 hour" | ☐ |
| 2.7 | Lock screen while a permission-change notification is showing | Lock screen shows "Permission Tracker / Open … to review", not app names | ☐ |

## 3. Home dashboard

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 3.1 | Look at Home | "% PROTECTED" ring with Critical / Sensitive / Total counts | ☐ |
| 3.2 | Permission Categories card → tap | Opens the Categories tab | ☐ |
| 3.3 | Background Risk card → tap | Background risk page listing apps with "Always allowed" | ☐ |
| 3.4 | "Needs your attention" | Your riskiest installed apps (or a "nothing needs attention" message) | ☐ |
| 3.5 | Bottom bar | 4 tabs with **text labels**: Home, Apps, Categories, History | ☐ |

## 4. Apps list and app details

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 4.1 | Apps tab | Apps with icons, risk badge (High/Medium/Low), "Pre-installed" tag on built-in apps; your apps listed first | ☐ |
| 4.2 | Search an app name; then nonsense | Filters list; then "No apps match …" | ☐ |
| 4.3 | Filter chips (High risk / Background access / Camera, mic or location) | List narrows correctly | ☐ |
| 4.4 | Open an app | Header, Allowed / Critical / Background counts, "Why it's … risk" reasons, Allowed and Not allowed sections | ☐ |
| 4.5 | "View in OS" | Android's App info page for that app opens | ☐ |

## 5. Changing a permission (the main flow)

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 5.1 | Tap a permission (e.g. Camera) | Sheet: "CAMERA ACCESS · App · This device", Right now, Why apps ask, options with tags (High privacy / Recommended / Broader access / Most private), auto-revoke note | ☐ |
| 5.2 | "More about this permission" | Shows what it accesses, if you don't allow it, privacy risk | ☐ |
| 5.3 | Location options | Includes Approximate location only and Always allow | ☐ |
| 5.4 | Choose the current option | Button says "No change" (disabled) | ☐ |
| 5.5 | Choose a different option → Apply setting | **Verify your identity** asks for your password | ☐ |
| 5.6 | Wrong password | "That password isn't right." | ☐ |
| 5.7 | Correct password (Demo mode **on**) | "Permission updated" immediately; badge/counts update | ☐ |
| 5.8 | Demo mode **off**, real app → correct password | "Finish in Android Settings" with exact steps → Open Settings | ☐ |
| 5.9 | First time only | Asks to allow notifications → Allow | ☐ |
| 5.10 | In Android Settings make the change | Banner "Change confirmed ✓ … Tap to return" appears within ~2 s | ☐ |
| 5.11 | Tap the banner | Back in the app with "Permission updated" | ☐ |
| 5.12 | Repeat but **don't** change it in Settings, press Back | "Permission not updated" with Try again | ☐ |
| 5.13 | Change another permission within 2 minutes | No password asked (2-minute grace) | ☐ |

## 6. Revoke all

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 6.1 | App page → Revoke all | **Always** asks for password (even right after 5.13) | ☐ |
| 6.2 | After password | Warning listing what will be revoked | ☐ |
| 6.3 | Demo mode on → Revoke all | "Permissions revoked"; risk drops | ☐ |
| 6.4 | Real app → follow the steps in Settings | "Permissions revoked" or "Some permissions not revoked" + Try again | ☐ |

## 7. Categories and background risk

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 7.1 | Categories tab | Header counts; one card per type with sensitivity tag, app icons, insight line, progress bar | ☐ |
| 7.2 | Review on a card | Sheet listing every app that asked for it, with access level | ☐ |
| 7.3 | Tap an app in the sheet | App page opens **with that permission's sheet already open** | ☐ |

## 8. History and notifications

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 8.1 | History tab (Demo mode on) | 7-day chart, "What your apps can access" donut, logs grouped by day | ☐ |
| 8.2 | Filters All / Allowed / Denied | Logs filter | ☐ |
| 8.3 | Tap a log entry | Details: time, change (before → after), how, device, "Review this permission" | ☐ |
| 8.4 | 🔔 Notifications | Recent alerts with View permission / Dismiss; explanation of difference from History | ☐ |
| 8.5 | Dismiss an alert | Gone from Notifications but **still in History** | ☐ |
| 8.6 | Demo off: change a permission directly in Android Settings, return | New "Detected" entry in History and Notifications | ☐ |

## 9. Settings

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 9.1 | Settings ⚙ | Notifications, History, Periodic Review toggles; My Devices, Backup & Restore, Privacy Information, About, Delete Local Data; Demo mode, Show system apps | ☐ |
| 9.2 | Privacy Information | Clear sections about data, storage, backups, devices, Android limitation | ☐ |
| 9.3 | About | Version, purpose, research basis, team | ☐ |
| 9.4 | Demo mode on/off | Yellow banner on/off; sample vs real apps | ☐ |

## 10. Backup & restore

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 10.1 | Export backup | **Always** asks account password → choose backup password (twice) → pick save location | ☐ |
| 10.2 | After saving | "Backup created"; Last backup shows the time | ☐ |
| 10.3 | Import backup → pick file → **wrong** backup password | "Wrong password, or the backup file is damaged." | ☐ |
| 10.4 | Correct password | Review: date, device, entries, preferences → Restore → "Backup restored… Android permissions were NOT changed" | ☐ |
| 10.5 | Import a photo or other file | "That file isn't a Permission Tracker backup." | ☐ |

## 11. Delete local data

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 11.1 | Delete Local Data | Lists what will and won't be deleted | ☐ |
| 11.2 | Delete | **Always** asks password → confirmation warning → "Local data deleted" | ☐ |
| 11.3 | History tab | Empty; still signed in | ☐ |

## 12. Multi-device (needs two devices: phone + emulator, or two emulators)

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 12.1 | Device A: sign in | Goes straight to Home (first device) | ☐ |
| 12.2 | Settings → My Devices | "This device · Current device" | ☐ |
| 12.3 | Device B: sign in with the **same account** + email code | "Add this device" → name, Start fresh / Import preferences → Continue → "Verify this device" | ☐ |
| 12.4 | Device A (app open) | Popup "New device wants to join" with a 6-digit code | ☐ |
| 12.5 | Device B: wrong code | "That code isn't right. N tries left." | ☐ |
| 12.6 | Device B: right code | "Device added" → Home | ☐ |
| 12.7 | Both: My Devices | Both listed; tap the other → summary (apps, high risk, background, riskiest apps) | ☐ |
| 12.8 | Device A: Deny a new request instead | Device B shows "Couldn't add this device… denied" + Request a new code | ☐ |
| 12.9 | Device B: "Verify with email" | Email code approves the device | ☐ |
| 12.10 | Device A: Select devices → tick B → Remove selected | **Always** asks password → warning → "Device removed" | ☐ |
| 12.11 | Device B: close and reopen the app | "This device was removed" | ☐ |
| 12.12 | My Devices → toggle "Share this phone's summary" twice quickly | Second time: "Wait a few seconds and try again" (anti-abuse limit) | ☐ |

## 13. General robustness

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 13.1 | Rotate the device on several screens | No crash; content kept | ☐ |
| 13.2 | Turn on airplane mode, open the app | Home/Apps still work (local data) | ☐ |
| 13.3 | Airplane mode on, try to sign in | Clear "No internet connection" message | ☐ |
| 13.4 | Dark mode (phone settings) | Readable in dark theme | ☐ |
| 13.5 | Large font size (phone settings) | Text still readable, nothing cut off badly | ☐ |

---

### Reporting
For each failed row: **row number · device/emulator · steps · expected · actual · screenshot**.
