package com.group8.permissionmanagerapp_uss.data.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.FirebaseApp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.group8.permissionmanagerapp_uss.data.security.EncryptedFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A user-facing auth error message (never includes raw exception text). */
class AuthException(message: String) : Exception(message)

/**
 * Firebase email/password sign-in, plus local state for the second factor (email code) and
 * "Verify your identity" re-authentication before sensitive actions (UP8, UP9).
 */
class AuthRepository(context: Context) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    /** Which signed-in user has passed the email code on this device. Encrypted at rest. */
    private val sessionStore = EncryptedFileStore(File(context.filesDir, "session.bin"), "pm_session_key")
    private val _twoFactorUid = MutableStateFlow<String?>(null)
    val twoFactorUid: StateFlow<String?> = _twoFactorUid.asStateFlow()

    @Volatile private var lastReauthAt = 0L

    val currentUser: FirebaseUser? get() = auth.currentUser

    val userFlow: Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun loadSession() = withContext(Dispatchers.IO) {
        _twoFactorUid.value = sessionStore.read()?.decodeToString()?.takeIf { it.isNotBlank() }
    }

    suspend fun signUp(name: String, email: String, password: String): FirebaseUser = mapErrors {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user
            ?: throw AuthException("Couldn't create the account. Please try again.")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()).await()
        user
    }

    suspend fun signIn(email: String, password: String): FirebaseUser = mapErrors {
        auth.signInWithEmailAndPassword(email, password).await().user
            ?: throw AuthException("Email or password is incorrect.")
    }

    /** Always reports success to the UI, so the screen can't be used to discover which emails have accounts. */
    suspend fun sendPasswordReset(email: String) {
        runCatching { auth.sendPasswordResetEmail(email).await() }
            .onFailure { if (it is FirebaseNetworkException) throw AuthException("No internet connection. Try again.") }
    }

    /**
     * "Verify your identity": re-check the password before a sensitive action.
     *
     * Uses Firebase's password check endpoint instead of re-authenticating the session, because a
     * re-authentication would start a new login session and invalidate the server-side 2FA marker.
     * Firebase still rate-limits wrong guesses on the server.
     */
    suspend fun reauthenticate(password: String) {
        val email = auth.currentUser?.email ?: throw AuthException("You're signed out. Please sign in again.")
        val apiKey = FirebaseApp.getInstance().options.apiKey
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("returnSecureToken", false)
        }.toString()
        val (status, text) = try {
            withContext(Dispatchers.IO) {
                val conn = (URL("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey")
                    .openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 15_000
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                (code to (stream?.bufferedReader()?.use { it.readText() } ?: "")).also { conn.disconnect() }
            }
        } catch (_: Exception) {
            throw AuthException("No internet connection. Try again.")
        }
        when {
            status in 200..299 -> lastReauthAt = System.currentTimeMillis()
            "TOO_MANY_ATTEMPTS" in text -> throw AuthException("Too many attempts. Please wait a few minutes and try again.")
            else -> throw AuthException("That password isn't right.")
        }
    }

    /** Gets a fresh ID token so new custom claims (the server's 2FA marker) take effect immediately. */
    suspend fun refreshToken() = mapErrors {
        auth.currentUser?.getIdToken(true)?.await()
        Unit
    }

    /**
     * True if the server has marked THIS login session as 2FA-verified (custom claim `mfaSessions`
     * contains the session's `auth_time`). Firestore rules require the same.
     */
    suspend fun hasServerMfa(): Boolean = try {
        val claims = auth.currentUser?.getIdToken(false)?.await()?.claims.orEmpty()
        val authTime = (claims["auth_time"] as? Number)?.toLong()
        val sessions = (claims["mfaSessions"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }.orEmpty()
        authTime != null && authTime in sessions
    } catch (_: Exception) {
        // Offline: trust the local flag; Firestore will enforce the real check when online.
        true
    }

    /** Forgets the local "2FA passed" flag so the email code is asked again. */
    suspend fun clearTwoFactor() = withContext(Dispatchers.IO) {
        sessionStore.delete()
        _twoFactorUid.value = null
    }

    /** True if the password was confirmed in the last [REAUTH_GRACE_MS] (avoids asking repeatedly). */
    fun recentlyVerified(): Boolean = System.currentTimeMillis() - lastReauthAt < REAUTH_GRACE_MS

    suspend fun idToken(): String = mapErrors {
        auth.currentUser?.getIdToken(false)?.await()?.token
            ?: throw AuthException("You're signed out. Please sign in again.")
    }

    suspend fun markTwoFactorPassed(uid: String) = withContext(Dispatchers.IO) {
        sessionStore.write(uid.toByteArray())
        _twoFactorUid.value = uid
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        auth.signOut()
        sessionStore.delete()
        _twoFactorUid.value = null
        lastReauthAt = 0
    }

    private suspend fun <T> mapErrors(block: suspend () -> T): T = try {
        block()
    } catch (e: AuthException) {
        throw e
    } catch (_: FirebaseAuthWeakPasswordException) {
        throw AuthException("That password is too weak. Use at least 8 characters with letters and numbers.")
    } catch (_: FirebaseAuthUserCollisionException) {
        throw AuthException("An account with this email already exists. Try signing in instead.")
    } catch (_: FirebaseAuthInvalidCredentialsException) {
        throw AuthException("Email or password is incorrect.")
    } catch (_: FirebaseAuthInvalidUserException) {
        throw AuthException("Email or password is incorrect.")
    } catch (_: FirebaseTooManyRequestsException) {
        throw AuthException("Too many attempts. Please wait a few minutes and try again.")
    } catch (_: FirebaseNetworkException) {
        throw AuthException("No internet connection. Try again.")
    } catch (_: Exception) {
        throw AuthException("Something went wrong. Please try again.")
    }

    companion object {
        const val REAUTH_GRACE_MS = 2 * 60 * 1000L
    }
}

/** Awaits a Play Services Task without pulling in an extra library. */
@Suppress("UNCHECKED_CAST")
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) cont.resume(task.result as T)
        else cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
    }
}
