package com.group8.permissionmanagerapp_uss.data.auth

import android.app.KeyguardManager
import android.content.Context
import com.group8.permissionmanagerapp_uss.data.security.DeviceBoundKey
import com.group8.permissionmanagerapp_uss.data.security.EncryptedFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/** A user-facing auth error message (never includes raw exception text). */
class AuthException(message: String) : Exception(message)

data class AccountInfo(val name: String, val email: String)

/**
 * On-device account — no server, no third-party service, no network.
 *
 * Sign-in needs BOTH factors:
 *  1. the password (verified against a PBKDF2 hash), and
 *  2. a fresh phone unlock (fingerprint/face/screen-lock PIN), proven by decrypting a secret with a
 *     Keystore key that only works for 30 s after an unlock ([DeviceBoundKey]).
 * Account data is stored AES-256-GCM encrypted with a separate Keystore key ([EncryptedFileStore]).
 */
class AuthRepository(private val context: Context, private val now: () -> Long = System::currentTimeMillis) {

    @Serializable
    private data class Account(
        val name: String,
        val email: String,
        val salt: String,
        val hash: String,
        val iterations: Int,
        val recoverySalt: String,
        val recoveryHash: String,
        val recoveryIterations: Int,
        /** SHA-256 of the secret that the device-bound key protects (the possession factor). */
        val unlockSecretHash: String,
        val createdAt: Long,
    )

    @Serializable
    private data class State(
        val failures: Int = 0,
        val lockedUntil: Long = 0,
        val signedIn: Boolean = false,
        val lastFullSignInAt: Long = 0,
    )

    @Serializable
    data class SecurityEvent(val time: Long, val type: String)

    @Serializable
    private data class EventLog(val events: List<SecurityEvent> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }
    private val accountStore = EncryptedFileStore(File(context.filesDir, "account.bin"), "pm_account_key")
    private val stateStore = EncryptedFileStore(File(context.filesDir, "auth_state.bin"), "pm_auth_state_key")
    private val logStore = EncryptedFileStore(File(context.filesDir, "security_log.bin"), "pm_security_log_key")
    private val unlockFile = File(context.filesDir, "unlock.bin")
    private val deviceKey = DeviceBoundKey("pm_device_unlock_key")
    private val mutex = Mutex()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    @Volatile private var lastReauthAt = 0L

    // ---- status ----

    /** A secure lock screen (PIN, pattern, password, biometrics) is required for the second factor. */
    fun isDeviceSecure(): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

    suspend fun hasAccount(): Boolean = loadAccount() != null

    suspend fun account(): AccountInfo? = loadAccount()?.let { AccountInfo(it.name, it.email) }

    /** True if the last full sign-in is recent enough that a phone unlock alone reopens the app. */
    suspend fun quickUnlockAllowed(): Boolean {
        val s = loadState()
        return s.signedIn && now() - s.lastFullSignInAt < SESSION_MS
    }

    /** Milliseconds until another password attempt is allowed (0 if not locked out). */
    suspend fun lockoutRemaining(): Long = (loadState().lockedUntil - now()).coerceAtLeast(0)

    // ---- account creation ----

    /**
     * Creates the account. Call right after a successful phone unlock (the device-bound key needs it).
     * @return the recovery key to show the user once.
     */
    suspend fun createAccount(name: String, email: String, password: String): String = mutex.withLock {
        if (loadAccount() != null) throw AuthException("An account already exists on this phone.")
        if (!isDeviceSecure()) throw AuthException("Set up a screen lock (PIN, pattern or password) first.")
        withContext(Dispatchers.Default) {
            val recovery = RecoveryKey.generate()
            val pwHash = PasswordHasher.hash(password.toCharArray())
            val rkHash = PasswordHasher.hash(RecoveryKey.normalize(recovery).toCharArray(), RECOVERY_ITERATIONS)
            val secret = ByteArray(32).also(SecureRandom()::nextBytes)
            try {
                deviceKey.ensureExists()
                unlockFile.writeBytes(deviceKey.encrypt(secret))
            } catch (_: DeviceBoundKey.NotUnlockedException) {
                throw AuthException("Please unlock with your fingerprint, face or PIN to continue.")
            } catch (_: Exception) {
                throw AuthException("Couldn't set up secure storage. Check that a screen lock is set, then try again.")
            }
            saveAccount(
                Account(
                    name = name, email = email.trim().lowercase(),
                    salt = pwHash.salt, hash = pwHash.hash, iterations = pwHash.iterations,
                    recoverySalt = rkHash.salt, recoveryHash = rkHash.hash, recoveryIterations = rkHash.iterations,
                    unlockSecretHash = sha256(secret), createdAt = now(),
                )
            )
            saveState(State(signedIn = true, lastFullSignInAt = now()))
            logLocked("Account created")
            _signedIn.value = true
            recovery
        }
    }

    // ---- sign-in ----

    /** Step 1 of sign-in: email + password (rate-limited). Doesn't sign in yet. */
    suspend fun checkPassword(email: String, password: String) = mutex.withLock {
        val acc = loadAccount() ?: throw AuthException("Email or password is incorrect.")
        enforceLockout()
        val ok = acc.email.equals(email.trim(), ignoreCase = true) && withContext(Dispatchers.Default) {
            PasswordHasher.verify(password.toCharArray(), PasswordHasher.Hash(acc.salt, acc.hash, acc.iterations))
        }
        recordAttempt(ok)
        if (!ok) throw AuthException(wrongMessage())
    }

    /** Step 2 of sign-in (and quick unlock): proves a real phone unlock just happened. */
    suspend fun completeWithDeviceUnlock(fullSignIn: Boolean) = mutex.withLock {
        val acc = loadAccount() ?: throw AuthException("No account on this phone.")
        verifyDeviceUnlock(acc)
        val s = loadState()
        saveState(s.copy(signedIn = true, lastFullSignInAt = if (fullSignIn) now() else s.lastFullSignInAt))
        logLocked(if (fullSignIn) "Signed in" else "Unlocked")
        _signedIn.value = true
    }

    /** Auto-lock (app in background too long): the session stays, but a phone unlock is needed. */
    fun lock() {
        if (_signedIn.value) _signedIn.value = false
    }

    suspend fun signOut() = mutex.withLock {
        saveState(loadState().copy(signedIn = false))
        logLocked("Logged out")
        _signedIn.value = false
        lastReauthAt = 0
    }

    // ---- verify identity / change password ----

    /** "Verify your identity": password re-check before a sensitive action (shares the lockout). */
    suspend fun reauthenticate(password: String) = mutex.withLock {
        val acc = loadAccount() ?: throw AuthException("No account on this phone.")
        enforceLockout()
        val ok = withContext(Dispatchers.Default) {
            PasswordHasher.verify(password.toCharArray(), PasswordHasher.Hash(acc.salt, acc.hash, acc.iterations))
        }
        recordAttempt(ok)
        if (!ok) throw AuthException(wrongMessage(passwordOnly = true))
        lastReauthAt = now()
    }

    fun recentlyVerified(): Boolean = now() - lastReauthAt < REAUTH_GRACE_MS

    suspend fun changePassword(current: String, newPassword: String) = mutex.withLock {
        val acc = loadAccount() ?: throw AuthException("No account on this phone.")
        enforceLockout()
        val ok = withContext(Dispatchers.Default) {
            PasswordHasher.verify(current.toCharArray(), PasswordHasher.Hash(acc.salt, acc.hash, acc.iterations))
        }
        recordAttempt(ok)
        if (!ok) throw AuthException(wrongMessage(passwordOnly = true))
        val h = withContext(Dispatchers.Default) { PasswordHasher.hash(newPassword.toCharArray()) }
        saveAccount(acc.copy(salt = h.salt, hash = h.hash, iterations = h.iterations))
        logLocked("Password changed")
    }

    // ---- recovery ----

    /** Forgot password: recovery key + a fresh phone unlock -> set a new password. */
    suspend fun recoverWithKey(email: String, recoveryKey: String, newPassword: String) = mutex.withLock {
        val acc = loadAccount() ?: throw AuthException("No account on this phone.")
        enforceLockout()
        val ok = acc.email.equals(email.trim(), ignoreCase = true) && RecoveryKey.isWellFormed(recoveryKey) &&
            withContext(Dispatchers.Default) {
                PasswordHasher.verify(
                    RecoveryKey.normalize(recoveryKey).toCharArray(),
                    PasswordHasher.Hash(acc.recoverySalt, acc.recoveryHash, acc.recoveryIterations),
                )
            }
        recordAttempt(ok)
        if (!ok) throw AuthException("Email or recovery key is incorrect.")
        verifyDeviceUnlock(acc)
        val h = withContext(Dispatchers.Default) { PasswordHasher.hash(newPassword.toCharArray()) }
        saveAccount(acc.copy(salt = h.salt, hash = h.hash, iterations = h.iterations))
        saveState(State(signedIn = true, lastFullSignInAt = now()))
        logLocked("Password reset with recovery key")
        _signedIn.value = true
    }

    /** Lost password AND recovery key: wipe the account (after a phone unlock). Caller wipes app data. */
    suspend fun resetAccount() = mutex.withLock {
        val acc = loadAccount()
        if (acc != null && unlockFile.exists()) {
            // Must be the phone's owner: needs a fresh unlock. If the key was invalidated (screen lock
            // removed and re-added), the data is unrecoverable anyway, so allow the reset.
            try {
                verifyDeviceUnlock(acc)
            } catch (e: AuthException) {
                if (e.message != KEY_INVALIDATED) throw e
            }
        }
        withContext(Dispatchers.IO) {
            accountStore.delete(); stateStore.delete(); logStore.delete(); unlockFile.delete()
        }
        deviceKey.delete()
        _signedIn.value = false
        lastReauthAt = 0
    }

    // ---- security activity log ----

    suspend fun securityEvents(): List<SecurityEvent> = mutex.withLock { loadLog().events.sortedByDescending { it.time } }

    // ---- internals ----

    private fun verifyDeviceUnlock(acc: Account) {
        val secret = try {
            deviceKey.decrypt(unlockFile.readBytes())
        } catch (_: DeviceBoundKey.NotUnlockedException) {
            throw AuthException("Please unlock with your fingerprint, face or PIN to continue.")
        } catch (_: DeviceBoundKey.InvalidatedException) {
            throw AuthException(KEY_INVALIDATED)
        } catch (_: Exception) {
            throw AuthException("Couldn't verify the phone unlock. Please try again.")
        }
        if (!MessageDigest.isEqual(sha256(secret).toByteArray(), acc.unlockSecretHash.toByteArray())) {
            throw AuthException("Couldn't verify the phone unlock. Please try again.")
        }
    }

    private suspend fun enforceLockout() {
        val remaining = loadState().lockedUntil - now()
        if (remaining > 0) {
            throw AuthException("Too many wrong attempts. Try again in ${formatWait(remaining)}.")
        }
    }

    private suspend fun recordAttempt(ok: Boolean) {
        val s = loadState()
        if (ok) {
            saveState(s.copy(failures = 0, lockedUntil = 0))
        } else {
            val failures = s.failures + 1
            val wait = LockoutPolicy.lockoutMillis(failures)
            saveState(s.copy(failures = failures, lockedUntil = if (wait > 0) now() + wait else 0))
            logLocked(if (wait > 0) "Wrong password · locked for ${formatWait(wait)}" else "Wrong password")
        }
    }

    private suspend fun wrongMessage(passwordOnly: Boolean = false): String {
        val s = loadState()
        val base = if (passwordOnly) "That password isn't right." else "Email or password is incorrect."
        val left = LockoutPolicy.FREE_ATTEMPTS - s.failures
        return when {
            s.lockedUntil > now() -> "$base Too many wrong attempts, so it's locked for ${formatWait(s.lockedUntil - now())}."
            left in 1..2 -> "$base $left ${if (left == 1) "try" else "tries"} left before a short lock."
            else -> base
        }
    }

    private fun formatWait(ms: Long): String {
        val s = (ms + 999) / 1000
        return if (s < 60) "$s seconds" else "${(s + 59) / 60} minutes"
    }

    private suspend fun logLocked(type: String) {
        val log = loadLog()
        val events = (log.events + SecurityEvent(now(), type)).takeLast(MAX_EVENTS)
        withContext(Dispatchers.IO) { logStore.write(json.encodeToString(EventLog.serializer(), EventLog(events)).toByteArray()) }
    }

    private suspend fun loadLog(): EventLog = withContext(Dispatchers.IO) {
        logStore.read()?.let { runCatching { json.decodeFromString(EventLog.serializer(), it.decodeToString()) }.getOrNull() }
    } ?: EventLog()

    private suspend fun loadAccount(): Account? = withContext(Dispatchers.IO) {
        accountStore.read()?.let { runCatching { json.decodeFromString(Account.serializer(), it.decodeToString()) }.getOrNull() }
    }

    private suspend fun saveAccount(a: Account) = withContext(Dispatchers.IO) {
        accountStore.write(json.encodeToString(Account.serializer(), a).toByteArray())
    }

    private suspend fun loadState(): State = withContext(Dispatchers.IO) {
        stateStore.read()?.let { runCatching { json.decodeFromString(State.serializer(), it.decodeToString()) }.getOrNull() }
    } ?: State()

    private suspend fun saveState(s: State) = withContext(Dispatchers.IO) {
        stateStore.write(json.encodeToString(State.serializer(), s).toByteArray())
    }

    private fun sha256(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val REAUTH_GRACE_MS = 2 * 60 * 1000L
        /** After this long (or after logging out) the password is needed again, not just a phone unlock. */
        const val SESSION_MS = 12 * 60 * 60 * 1000L
        /** Leaving the app for this long locks it (phone unlock to reopen). */
        const val AUTO_LOCK_MS = 5 * 60 * 1000L
        private const val RECOVERY_ITERATIONS = 50_000
        private const val MAX_EVENTS = 300
        const val KEY_INVALIDATED =
            "Your phone's screen lock was changed, so this account can no longer be unlocked. Reset the app to start again."
    }
}
