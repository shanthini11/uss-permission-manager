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
 *  - No secrets live in the app or in this file: FIREBASE_API_KEY, OTP_SECRET, BREVO_API_KEY,
 *    BREVO_SENDER_EMAIL and SERVICE_ACCOUNT_JSON are Script Properties.
 *
 * Server-side enforcement (needs SERVICE_ACCOUNT_JSON, a Firebase service account key):
 *  - 2FA: after a correct email code, this login session's auth_time is added to the user's
 *    `mfaSessions` custom claim. Firestore rules only allow sessions listed there, so signing in
 *    with just a password (e.g. via Firebase's REST API) gets no data.
 *  - Devices: only this script can add a device to an account. Join codes live here (never in
 *    Firestore), and the device document is written with admin credentials after the code checks out.
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

    const authTime = tokenAuthTime_(body.idToken);

    if (body.action === 'send') return json_(sendCode_(user));
    if (body.action === 'verify') {
      const result = verifyCode_(user, String(body.code || ''));
      // Mark THIS login session as 2FA-verified (custom claim read by the Firestore rules).
      if (result.ok && adminConfigured_()) addMfaSession_(user, authTime);
      return json_(result);
    }

    // Device actions require a 2FA-verified session.
    if (!hasMfa_(user, authTime)) return json_({ ok: false, error: 'mfa_required' });
    const deviceId = String(body.deviceId || '');
    if (body.action === 'registerFirstDevice') return json_(registerFirstDevice_(user, deviceId, body));
    if (body.action === 'issueDeviceCode') return json_(issueDeviceCode_(user, String(body.approverDeviceId || ''), deviceId));
    if (body.action === 'verifyDeviceCode') return json_(verifyDeviceCode_(user, deviceId, String(body.code || '')));
    if (body.action === 'approveDeviceByEmail') {
      const check = verifyCode_(user, String(body.code || ''));
      return json_(check.ok ? createDeviceFromRequest_(user.localId, deviceId) : check);
    }
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

// ---------------------------------------------------------------------------------------------
// Server-side 2FA (custom claims)
// ---------------------------------------------------------------------------------------------

/** auth_time (seconds) from the ID token payload. Safe to read: the token was verified by accounts:lookup. */
function tokenAuthTime_(idToken) {
  try {
    const part = idToken.split('.')[1];
    const payload = JSON.parse(Utilities.newBlob(Utilities.base64DecodeWebSafe(part + '==='.slice((part.length + 3) % 4))).getDataAsString());
    return Number(payload.auth_time) || 0;
  } catch (e) {
    return 0;
  }
}

function claimsOf_(user) {
  try { return JSON.parse(user.customAttributes || '{}') || {}; } catch (e) { return {}; }
}

function hasMfa_(user, authTime) {
  const sessions = claimsOf_(user).mfaSessions;
  return authTime > 0 && Array.isArray(sessions) && sessions.indexOf(authTime) >= 0;
}

/** Adds this session's auth_time to the user's mfaSessions claim (keeps the 5 most recent). */
function addMfaSession_(user, authTime) {
  if (!authTime) return;
  const claims = claimsOf_(user);
  const sessions = (Array.isArray(claims.mfaSessions) ? claims.mfaSessions : []).filter(function (t) { return t !== authTime; });
  sessions.push(authTime);
  claims.mfaSessions = sessions.slice(-5);
  const sa = serviceAccount_();
  const res = UrlFetchApp.fetch('https://identitytoolkit.googleapis.com/v1/projects/' + sa.project_id + '/accounts:update', {
    method: 'post',
    contentType: 'application/json',
    headers: { Authorization: 'Bearer ' + accessToken_() },
    payload: JSON.stringify({ localId: user.localId, customAttributes: JSON.stringify(claims) }),
    muteHttpExceptions: true,
  });
  if (res.getResponseCode() !== 200) throw new Error('accounts:update failed: ' + res.getResponseCode());
}

// ---------------------------------------------------------------------------------------------
// Server-side device approval
// ---------------------------------------------------------------------------------------------

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

/** First device on an account: allowed only when the account has no devices yet. */
function registerFirstDevice_(user, deviceId, body) {
  if (!UUID_RE.test(deviceId)) return { ok: false, error: 'bad_request' };
  const existing = fsList_('users/' + user.localId + '/devices');
  if (existing.length > 0) return { ok: false, error: 'not_first' };
  writeDevice_(user.localId, deviceId, clean_(body.name, 40) || 'My phone', clean_(body.model, 40), clean_(body.os, 30));
  return { ok: true };
}

/** Called by an approved device: creates a join code for another device's request and returns it. */
function issueDeviceCode_(user, approverDeviceId, requestId) {
  if (!UUID_RE.test(approverDeviceId) || !UUID_RE.test(requestId)) return { ok: false, error: 'bad_request' };
  const uid = user.localId;
  if (!fsGet_('users/' + uid + '/devices/' + approverDeviceId)) return { ok: false, error: 'not_approved_device' };
  const request = fsGet_('users/' + uid + '/joinRequests/' + requestId);
  if (!request || str_(request, 'status') !== 'pending') return { ok: false, error: 'no_request' };

  const cache = CacheService.getScriptCache();
  const key = 'dev:' + uid + ':' + requestId;
  const current = cache.get(key);
  if (current && JSON.parse(current).e > Date.now()) return { ok: false, error: 'code_active' };

  const code = randomCode_();
  const expiresAt = Date.now() + CODE_TTL_SECONDS * 1000;
  cache.put(key, JSON.stringify({ h: hmac_(key + ':' + code), a: 0, e: expiresAt }), CODE_TTL_SECONDS);
  fsPatch_('users/' + uid + '/joinRequests/' + requestId, { codeExpiresAt: expiresAt }, ['codeExpiresAt']);
  return { ok: true, code: code, expiresAt: expiresAt };
}

/** Called by the new device with the code it was shown. */
function verifyDeviceCode_(user, deviceId, code) {
  if (!UUID_RE.test(deviceId) || !/^\d{6}$/.test(code)) return { ok: false, error: 'invalid_code' };
  const cache = CacheService.getScriptCache();
  const key = 'dev:' + user.localId + ':' + deviceId;
  const raw = cache.get(key);
  if (!raw) return { ok: false, error: 'expired' };
  const entry = JSON.parse(raw);
  const remaining = Math.floor((entry.e - Date.now()) / 1000);
  if (remaining <= 0) { cache.remove(key); return { ok: false, error: 'expired' }; }
  if (entry.a >= MAX_ATTEMPTS) { cache.remove(key); return { ok: false, error: 'too_many_attempts' }; }
  if (entry.h !== hmac_(key + ':' + code)) {
    entry.a += 1;
    cache.put(key, JSON.stringify(entry), remaining);
    return { ok: false, error: 'invalid_code', attemptsLeft: MAX_ATTEMPTS - entry.a };
  }
  cache.remove(key);
  return createDeviceFromRequest_(user.localId, deviceId);
}

/** Moves a join request into the devices collection (admin write; clients can't create devices). */
function createDeviceFromRequest_(uid, deviceId) {
  if (!UUID_RE.test(deviceId)) return { ok: false, error: 'bad_request' };
  const request = fsGet_('users/' + uid + '/joinRequests/' + deviceId);
  if (!request) return { ok: false, error: 'no_request' };
  writeDevice_(uid, deviceId, clean_(str_(request, 'name'), 40) || 'My phone', clean_(str_(request, 'model'), 40), clean_(str_(request, 'os'), 30));
  fsDelete_('users/' + uid + '/joinRequests/' + deviceId);
  return { ok: true };
}

function writeDevice_(uid, deviceId, name, model, os) {
  const now = Date.now();
  fsPatch_('users/' + uid + '/devices/' + deviceId, {
    name: name, model: model, os: os, platform: 'Android', addedAt: now, lastActive: now, shareSummary: true,
  });
}

function clean_(v, max) {
  return String(v || '').replace(/[\u0000-\u001f\u007f]/g, '').trim().slice(0, max);
}

// ---------------------------------------------------------------------------------------------
// Admin access: service account -> OAuth token; Firestore REST (admin writes bypass client rules)
// ---------------------------------------------------------------------------------------------

function adminConfigured_() {
  return !!PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT_JSON');
}

function serviceAccount_() {
  const raw = PropertiesService.getScriptProperties().getProperty('SERVICE_ACCOUNT_JSON');
  if (!raw) throw new Error('Add Script Property SERVICE_ACCOUNT_JSON');
  return JSON.parse(raw);
}

function accessToken_() {
  const cache = CacheService.getScriptCache();
  const cached = cache.get('sa_token');
  if (cached) return cached;
  const sa = serviceAccount_();
  const now = Math.floor(Date.now() / 1000);
  const b64 = function (s) { return Utilities.base64EncodeWebSafe(s).replace(/=+$/, ''); };
  const input = b64(JSON.stringify({ alg: 'RS256', typ: 'JWT' })) + '.' + b64(JSON.stringify({
    iss: sa.client_email,
    scope: 'https://www.googleapis.com/auth/cloud-platform https://www.googleapis.com/auth/datastore',
    aud: 'https://oauth2.googleapis.com/token',
    iat: now,
    exp: now + 3600,
  }));
  const signature = b64(Utilities.computeRsaSha256Signature(input, sa.private_key));
  const res = UrlFetchApp.fetch('https://oauth2.googleapis.com/token', {
    method: 'post',
    payload: { grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: input + '.' + signature },
    muteHttpExceptions: true,
  });
  if (res.getResponseCode() !== 200) throw new Error('Service account token failed: ' + res.getResponseCode());
  const token = JSON.parse(res.getContentText()).access_token;
  cache.put('sa_token', token, 3000);
  return token;
}

function fsUrl_(path) {
  return 'https://firestore.googleapis.com/v1/projects/' + serviceAccount_().project_id + '/databases/(default)/documents/' + path;
}

function fsFetch_(url, options) {
  options = options || {};
  options.headers = { Authorization: 'Bearer ' + accessToken_() };
  options.muteHttpExceptions = true;
  return UrlFetchApp.fetch(url, options);
}

function fsGet_(path) {
  const res = fsFetch_(fsUrl_(path));
  if (res.getResponseCode() === 404) return null;
  if (res.getResponseCode() !== 200) throw new Error('Firestore get failed: ' + res.getResponseCode());
  return JSON.parse(res.getContentText()).fields || {};
}

function fsList_(path) {
  const res = fsFetch_(fsUrl_(path) + '?pageSize=1');
  if (res.getResponseCode() !== 200) throw new Error('Firestore list failed: ' + res.getResponseCode());
  return JSON.parse(res.getContentText()).documents || [];
}

function fsPatch_(path, obj, mask) {
  let url = fsUrl_(path);
  if (mask) url += '?' + mask.map(function (f) { return 'updateMask.fieldPaths=' + encodeURIComponent(f); }).join('&');
  const fields = {};
  Object.keys(obj).forEach(function (k) { fields[k] = fsValue_(obj[k]); });
  const res = fsFetch_(url, { method: 'patch', contentType: 'application/json', payload: JSON.stringify({ fields: fields }) });
  if (res.getResponseCode() !== 200) throw new Error('Firestore write failed: ' + res.getResponseCode());
}

function fsDelete_(path) {
  fsFetch_(fsUrl_(path), { method: 'delete' });
}

function fsValue_(v) {
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return { integerValue: String(Math.round(v)) };
  return { stringValue: String(v) };
}

function str_(fields, name) {
  return fields && fields[name] && fields[name].stringValue ? fields[name].stringValue : '';
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
  if (!adminConfigured_()) throw new Error('Add Script Property SERVICE_ACCOUNT_JSON (see SETUP.md, step "Server-side enforcement")');
  accessToken_();
  fsList_('users');
  const brevo = props.getProperty('BREVO_API_KEY') && props.getProperty('BREVO_SENDER_EMAIL');
  console.log(
    'Setup OK. Gmail sends left today: ' + MailApp.getRemainingDailyQuota() +
      (brevo ? '. Brevo enabled (' + (BREVO_DAILY_CAP - countToday_('brevo')) + ' left today).'
             : '. Brevo NOT set up (optional, adds 300/day): see SETUP.md.')
  );
}
