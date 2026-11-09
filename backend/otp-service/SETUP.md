# Free email 2FA service (Google Apps Script)

Permission Tracker sends 6-digit sign-in codes by email. Firebase's own server functions need a
paid plan, so this small script runs free on Google Apps Script and sends mail from your Gmail.

## One-time setup (about 10 minutes)

1. Go to <https://script.google.com> (signed in with the same Gmail as Firebase) → **New project**.
2. Rename it `Permission Tracker OTP` (click "Untitled project").
3. Delete the sample code and paste the whole of [`Code.gs`](Code.gs). Click **Save** (💾).
4. Add the two secret settings: ⚙ **Project Settings** (left bar) → scroll to **Script Properties** →
   **Add script property**:
   | Property | Value |
   |---|---|
   | `FIREBASE_API_KEY` | Your Firebase Web API key: Firebase console → ⚙ Project settings → General → "Web API Key". (It's also `current_key` in `app/google-services.json`.) |
   | `OTP_SECRET` | Any long random text, e.g. 40+ random letters and numbers. Don't reuse a password. |
5. Back in the **Editor**, choose `setupCheck` in the function dropdown → **Run**. Google asks for
   permission ("send email as you", "connect to external service") → allow. The log should say
   `Setup OK`.
   - If you see "Google hasn't verified this app": **Advanced → Go to Permission Tracker OTP (unsafe)**.
     It's your own script, so this is expected.
6. **Deploy → New deployment** → ⚙ type **Web app**:
   - Execute as: **Me**
   - Who has access: **Anyone** (the script itself checks the Firebase sign-in on every request)
   - **Deploy** → copy the **Web app URL** (ends in `/exec`).
7. In the Android project, open `local.properties` (project root, not committed to git) and add:
   ```
   otp.service.url=https://script.google.com/macros/s/XXXXXXXX/exec
   ```
8. Rebuild the app.

## Recommended: add Brevo for 300 more emails a day (free, no card)

Gmail alone allows about 100 emails a day. With Brevo the service can send about **390 codes a day**.

1. Sign up at <https://www.brevo.com> (free plan, no card needed).
2. **Senders, domains & dedicated IPs → Senders → Add a sender**: use your Gmail address and
   confirm the verification email Brevo sends you.
3. **SMTP & API → API keys → Generate a new API key**. Copy it (it starts with `xkeysib-`).
4. In Apps Script → ⚙ Project Settings → Script Properties, add:
   | Property | Value |
   |---|---|
   | `BREVO_API_KEY` | the key from step 3 |
   | `BREVO_SENDER_EMAIL` | the sender address you verified in step 2 |
5. Run `setupCheck` again. The log should say `Brevo enabled`.
6. **Deploy → Manage deployments → ✏ Edit → Version: New version → Deploy** (the URL stays the same).

## Limits
- Brevo free: 300/day (the script stops at 290). Gmail: about 100/day (the script keeps 5 spare).
- Limits reset every day, and each tester only needs a code once per phone (until they log out).
- If both run out, the app shows "The email service has reached today's limit. Please try again tomorrow."
- If you change `Code.gs`, use **Deploy → Manage deployments → Edit → New version** so the URL stays the same.
