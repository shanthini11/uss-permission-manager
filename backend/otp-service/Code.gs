/**
 * Permission Tracker — free email 2FA service (Google Apps Script).
 *
 * Why this exists: Firebase Cloud Functions need the paid Blaze plan. Apps Script is free.
 * Email goes out through Brevo's free plan (300/day) when configured, with the owner's Gmail
 * (~100/day) as a fallback: ~390 codes/day in total, all free, no card.
 *
 * Security design:
 *  - Every request must include a valid Firebase ID token; we verify it with Google's
 *    Identity Toolkit API, so only signed-in users can request a code, and only for THEIR OWN email.
 *  - Codes are 6 digits, expire after 5 minutes, and are stored only as an HMAC (never in plain text).
 *  - Max 5 wrong attempts per code; max 5 codes per 15 minutes per user; 60-second resend cooldown;
 *    daily caps protect the free email quotas.
 *  - No secrets live in the app or in this file: FIREBASE_API_KEY, OTP_SECRET, BREVO_API_KEY and
 *    BREVO_SENDER_EMAIL are Script Properties.
 *
 * Setup: see SETUP.md in this folder.
 */

const CODE_TTL_SECONDS = 300;        // 5 minutes
const MAX_ATTEMPTS = 5;
const MAX_SENDS_PER_WINDOW = 5;
const SEND_WINDOW_SECONDS = 900;     // 15 minutes
const RESEND_COOLDOWN_SECONDS = 60;
// Email capacity (all free): Brevo first (300/day free plan), then the owner's Gmail (~100/day).
const BREVO_DAILY_CAP = 290;         // stay just under Brevo's free 300/day
const GMAIL_RESERVE = 5;             // leave a few Gmail sends for the owner

function doPost(e) {
  const lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    const body = JSON.parse((e && e.postData && e.postData.contents) || '{}');
    const user = verifyIdToken_(body.idToken);
    if (!user) return json_({ ok: false, error: 'unauthenticated' });

    if (body.action === 'send') return json_(sendCode_(user));
    if (body.action === 'verify') return json_(verifyCode_(user, String(body.code || '')));
    return json_({ ok: false, error: 'bad_request' });
  } catch (err) {
    // Never echo internal errors back to the client.
    console.error(err);
    return json_({ ok: false, error: 'server_error' });
  } finally {
    lock.releaseLock();
  }
}

function sendCode_(user) {
  const cache = CacheService.getScriptCache();
  const uid = user.localId;

  if (cache.get('cooldown:' + uid)) return { ok: false, error: 'cooldown', retryAfter: RESEND_COOLDOWN_SECONDS };

  const sends = Number(cache.get('sends:' + uid) || '0');
  if (sends >= MAX_SENDS_PER_WINDOW) return { ok: false, error: 'rate_limited' };

  const code = randomCode_();
  const delivered = deliver_(
    user.email,
    'Your Permission Tracker verification code',
    'Your Permission Tracker verification code is: ' + code + '\n\n' +
      'It expires in 5 minutes. Never share this code with anyone.\n\n' +
      "If you didn't try to sign in, someone may know your password. Change it in the app right away."
  );
  // Only store the code if an email actually went out.
  if (!delivered) return { ok: false, error: 'daily_limit' };

  const expiresAt = Date.now() + CODE_TTL_SECONDS * 1000;
  cache.put('otp:' + uid, JSON.stringify({ h: hmac_(uid + ':' + code), a: 0, e: expiresAt }), CODE_TTL_SECONDS);
  cache.put('sends:' + uid, String(sends + 1), SEND_WINDOW_SECONDS);
  cache.put('cooldown:' + uid, '1', RESEND_COOLDOWN_SECONDS);
  return { ok: true, expiresIn: CODE_TTL_SECONDS };
}

/**
 * Sends with Brevo (free 300/day) if configured, otherwise / when exhausted with Gmail (~100/day).
 * Together that's ~390 codes per day, and the limits reset daily.
 */
function deliver_(to, subject, text) {
  const props = PropertiesService.getScriptProperties();
  const brevoKey = props.getProperty('BREVO_API_KEY');
  const brevoSender = props.getProperty('BREVO_SENDER_EMAIL');

  if (brevoKey && brevoSender && countToday_('brevo') < BREVO_DAILY_CAP) {
    const res = UrlFetchApp.fetch('https://api.brevo.com/v3/smtp/email', {
      method: 'post',
      contentType: 'application/json',
      headers: { 'api-key': brevoKey, accept: 'application/json' },
      payload: JSON.stringify({
        sender: { name: 'Permission Tracker', email: brevoSender },
        to: [{ email: to }],
        subject: subject,
        textContent: text,
      }),
      muteHttpExceptions: true,
    });
    if (res.getResponseCode() >= 200 && res.getResponseCode() < 300) {
      incrementToday_('brevo');
      return true;
    }
    console.warn('Brevo send failed with HTTP ' + res.getResponseCode() + '; falling back to Gmail');
  }

  if (MailApp.getRemainingDailyQuota() > GMAIL_RESERVE) {
    MailApp.sendEmail({ to: to, subject: subject, body: text, name: 'Permission Tracker' });
    return true;
  }
  return false;
}

/** Per-day counters kept in Script Properties (persist across the day, unlike the 6-hour cache). */
function countToday_(provider) {
  const key = 'count:' + provider + ':' + Utilities.formatDate(new Date(), 'UTC', 'yyyyMMdd');
  return Number(PropertiesService.getScriptProperties().getProperty(key) || '0');
}

function incrementToday_(provider) {
  const props = PropertiesService.getScriptProperties();
  const today = Utilities.formatDate(new Date(), 'UTC', 'yyyyMMdd');
  const key = 'count:' + provider + ':' + today;
  props.setProperty(key, String(Number(props.getProperty(key) || '0') + 1));
  // Tidy up counters from previous days.
  Object.keys(props.getProperties())
    .filter(function (k) { return k.indexOf('count:') === 0 && k.slice(-8) !== today; })
    .forEach(function (k) { props.deleteProperty(k); });
}

function verifyCode_(user, code) {
  if (!/^\d{6}$/.test(code)) return { ok: false, error: 'invalid_code' };
  const cache = CacheService.getScriptCache();
  const key = 'otp:' + user.localId;
  const raw = cache.get(key);
  if (!raw) return { ok: false, error: 'expired' };

  const entry = JSON.parse(raw);
  const remaining = Math.floor((entry.e - Date.now()) / 1000);
  if (remaining <= 0) {
    cache.remove(key);
    return { ok: false, error: 'expired' };
  }
  if (entry.a >= MAX_ATTEMPTS) {
    cache.remove(key);
    return { ok: false, error: 'too_many_attempts' };
  }
  if (entry.h !== hmac_(user.localId + ':' + code)) {
    entry.a += 1;
    // Wrong guesses never extend the code's lifetime.
    cache.put(key, JSON.stringify(entry), remaining);
    return { ok: false, error: 'invalid_code', attemptsLeft: MAX_ATTEMPTS - entry.a };
  }
  cache.remove(key); // one-time use
  return { ok: true };
}

/** Verifies a Firebase ID token by asking Google's Identity Toolkit who it belongs to. */
function verifyIdToken_(idToken) {
  if (!idToken || typeof idToken !== 'string' || idToken.length > 4096) return null;
  const apiKey = PropertiesService.getScriptProperties().getProperty('FIREBASE_API_KEY');
  const res = UrlFetchApp.fetch(
    'https://identitytoolkit.googleapis.com/v1/accounts:lookup?key=' + encodeURIComponent(apiKey),
    {
      method: 'post',
      contentType: 'application/json',
      payload: JSON.stringify({ idToken: idToken }),
      muteHttpExceptions: true,
    }
  );
  if (res.getResponseCode() !== 200) return null;
  const users = JSON.parse(res.getContentText()).users;
  if (!users || !users.length || !users[0].email) return null;
  return users[0]; // { localId, email, ... }
}

function randomCode_() {
  // UUIDs come from a cryptographically secure generator; hash and reduce to 6 digits.
  const digest = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, Utilities.getUuid() + Utilities.getUuid());
  let n = 0;
  for (let i = 0; i < 6; i++) n = (n * 256 + (digest[i] & 0xff)) % 1000000;
  return ('000000' + n).slice(-6);
}

function hmac_(value) {
  const secret = PropertiesService.getScriptProperties().getProperty('OTP_SECRET');
  const sig = Utilities.computeHmacSha256Signature(value, secret);
  return Utilities.base64Encode(sig);
}

function json_(obj) {
  return ContentService.createTextOutput(JSON.stringify(obj)).setMimeType(ContentService.MimeType.JSON);
}

/** Run this once from the editor to authorize Gmail + URL fetch, and to check the properties. */
function setupCheck() {
  const props = PropertiesService.getScriptProperties();
  if (!props.getProperty('FIREBASE_API_KEY')) throw new Error('Add Script Property FIREBASE_API_KEY');
  if (!props.getProperty('OTP_SECRET')) throw new Error('Add Script Property OTP_SECRET');
  MailApp.getRemainingDailyQuota();
  UrlFetchApp.fetch('https://www.google.com', { muteHttpExceptions: true });
  const brevo = props.getProperty('BREVO_API_KEY') && props.getProperty('BREVO_SENDER_EMAIL');
  console.log(
    'Setup OK. Gmail sends left today: ' + MailApp.getRemainingDailyQuota() +
      (brevo ? '. Brevo enabled (' + (BREVO_DAILY_CAP - countToday_('brevo')) + ' left today).'
             : '. Brevo NOT set up (optional, adds 300/day): see SETUP.md.')
  );
}
