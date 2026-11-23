# Permission Tracker — Manual Test Plan

Group 8 · Usable Security and Privacy · Version 2.0 (fully offline)

Use this checklist to test every feature by hand. Tick each box, and for anything that fails write
down **what you did, what you expected, what happened**, plus a screenshot.

---

## 0. Setup

You test the **APK** (the same file classmates will get). You don't need to build the code.

### Option A — Android phone (easiest, most realistic)
1. Download `PermissionTracker-v2.0-offline.apk` from the shared Drive link onto the phone. The phone must have a screen lock (PIN/pattern/password).
2. **Samsung only:** Settings → Security and privacy → **Auto Blocker → Off** (turn it back on after installing).
3. Open the APK → if asked, allow **Install unknown apps** for Drive/Files → **Install** → **Open**.

### Option B — MacBook with the Android emulator
1. Install **Android Studio** for Mac from <https://developer.android.com/studio> (choose *Apple chip* or *Intel chip* to match your Mac: Apple menu → About This Mac).
2. Open Android Studio → **More Actions → Virtual Device Manager → Create device** → pick **Pixel 8** (or any phone) → choose the newest system image marked **Recommended** (download it) → **Finish**.
3. Press **▶** to start the emulator.
4. **Set a screen lock:** emulator Settings → Security → Screen lock → **PIN** (e.g. 1234). Optional: add a fingerprint there; to "touch" it use the emulator toolbar **⋯ → Fingerprint → Touch sensor**.
5. **Drag `PermissionTracker-v2.0-offline.apk` from Finder onto the emulator window.** It installs in a few seconds; open it from the app drawer (swipe up).
6. Mouse = finger: click to tap, click-and-drag to scroll/swipe.

> Tip: the emulator has very few apps, so turn on **Demo mode** (Settings ⚙ → Demo mode) to test with
> 12 realistic sample apps. Turn it off to test with the emulator's real apps.

### Accounts
- The account exists **only on the device you create it on**. Any email works (nothing is sent).
- **Write down the recovery key** shown at sign-up; §2 uses it.

---

## 1. Landing, sign-up and sign-in

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 1.0 | Remove the device's screen lock, open the app | "Set up a screen lock" screen → Open security settings; after adding a PIN and returning, the landing page appears | ☐ |
| 1.1 | Open the app | Landing page: shield logo, "Works fully offline" note, "Create an account" | ☐ |
| 1.2 | Create an account → type password `abc` | Checklist shows ✗ for 10 characters / letter / number | ☐ |
| 1.3 | Password `Password123` | "Not a common password" ✗; Create account shows an error | ☐ |
| 1.4 | Password containing your email name (e.g. `riya2026` for riya@…) | "Doesn't contain your email name" ✗ | ☐ |
| 1.5 | Confirm password different from password | "The passwords don't match." | ☐ |
| 1.6 | Invalid email (`abc@`) | "Enter a valid email address…" | ☐ |
| 1.7 | Valid details → Create account | Phone-unlock prompt (fingerprint/face/PIN) | ☐ |
| 1.8 | Cancel the unlock prompt | Stays on the form with a message; no account created | ☐ |
| 1.9 | Create account again → unlock | **Recovery key** screen: 6 groups of 4 characters; Continue disabled until "I've written it down" is ticked | ☐ |
| 1.10 | Continue → type the two requested groups wrong | "Those don't match…" | ☐ |
| 1.11 | Type them correctly | Lands on **Home** | ☐ |
| 1.12 | Profile 👤 → Log out → confirm | Sign-in screen (email partly hidden) | ☐ |
| 1.13 | Sign in with a **wrong password** | "Email or password is incorrect." (doesn't say which) | ☐ |
| 1.14 | Correct password | Phone-unlock prompt → Home | ☐ |
| 1.15 | Correct password, then **cancel** the unlock prompt | Not signed in (password alone isn't enough) | ☐ |
| 1.16 | Close the app (swipe from Recents), reopen within 12 h | Only the phone unlock is asked → Home | ☐ |
| 1.17 | Leave the app (Home button) for 5+ minutes, return | App is locked; phone unlock needed | ☐ |

## 2. Security behaviours

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 2.1 | Log out, enter wrong passwords | After 3 wrong: "2 tries left before a short lock"; after 5: "locked for 30 seconds" | ☐ |
| 2.2 | Kill the app and reopen during the lock | Still locked (count survives restarts) | ☐ |
| 2.3 | Another wrong password after the lock ends | Lock doubles (about 1 minute) | ☐ |
| 2.4 | Try to screenshot the sign-in, recovery-key or password screens (release APK) | Screenshot is blocked or black (deliberate) | ☐ |
| 2.5 | Open Recents (□) while on the sign-in screen | App preview is hidden/blank | ☐ |
| 2.6 | Forgot password? → email + **wrong** recovery key | "Email or recovery key is incorrect." | ☐ |
| 2.7 | Forgot password? → correct key (any case/spacing) + new password → unlock | Signed in; the old password no longer works, the new one does | ☐ |
| 2.8 | Profile → Change password (wrong current / weak new / correct) | Error / rules error / "Password changed" | ☐ |
| 2.9 | Profile → Security activity | Lists Account created, Signed in, Wrong password, locks, Password changed with times | ☐ |
| 2.10 | Forgot password? → Reset the app → confirm → unlock | All data deleted; back to landing page; old account gone | ☐ |
| 2.11 | Lock screen while a permission-change notification is showing | Lock screen shows "Permission Tracker / Open … to review", not app names | ☐ |
| 2.12 | (Optional, destructive) Remove the phone's screen lock, re-add it, open the app | "Screen lock was changed… Reset the app" (fails closed) | ☐ |

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
| 9.1 | Settings ⚙ | Notifications, History, Periodic Review toggles; Backup & Restore, Privacy Information, About, Delete Local Data; Demo mode, Show system apps | ☐ |
| 9.2 | Privacy Information | Says everything stays on the phone, no internet permission; data, storage, backups, Android limitation | ☐ |
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

## 12. Moving data between devices (backup file)

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 12.1 | Device A: Export backup, copy the file to device B | File copied | ☐ |
| 12.2 | Device B: create its own account, Import backup with the backup password | History and preferences restored; Android permissions unchanged | ☐ |

## 13. General robustness

| # | Steps | Expected | ✓ |
|---|---|---|---|
| 13.1 | Rotate the device on several screens | No crash; content kept | ☐ |
| 13.2 | Turn on airplane mode, use every feature incl. sign-up and sign-in | Everything works (app is fully offline) | ☐ |
| 13.3 | Phone Settings → Apps → Permission Tracker → Permissions | No network permission listed (only Notifications) | ☐ |
| 13.4 | Dark mode (phone settings) | Readable in dark theme | ☐ |
| 13.5 | Large font size (phone settings) | Text still readable, nothing cut off badly | ☐ |

---

### Reporting
For each failed row: **row number · device/emulator · steps · expected · actual · screenshot**.
