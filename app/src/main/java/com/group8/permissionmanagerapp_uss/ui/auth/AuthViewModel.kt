package com.group8.permissionmanagerapp_uss.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.auth.AuthException
import com.group8.permissionmanagerapp_uss.data.auth.AuthRepository
import com.group8.permissionmanagerapp_uss.data.auth.InputValidation
import com.group8.permissionmanagerapp_uss.data.auth.OtpService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Where the user is in the sign-in journey. */
sealed interface AuthGate {
    data object Loading : AuthGate
    data object SignedOut : AuthGate
    /** Password accepted; waiting for the email code. [justCreated] shows "Account created" first. */
    data class NeedsCode(val email: String, val justCreated: Boolean) : AuthGate
    data class SignedIn(val name: String, val email: String) : AuthGate
}

/** Form state shared by the sign-in screens. */
data class AuthFormState(
    val busy: Boolean = false,
    val error: String? = null,
    val resetSentTo: String? = null,
    val codeSent: Boolean = false,
    /** Epoch millis when the current code expires (for the countdown). */
    val codeExpiresAt: Long = 0,
    /** Epoch millis until which "Resend code" is disabled. */
    val resendAvailableAt: Long = 0,
    val accountCreatedSeen: Boolean = false,
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val otp: OtpService,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    private val justCreated = MutableStateFlow(false)
    private val sessionLoaded = MutableStateFlow(false)

    val gate: StateFlow<AuthGate> =
        combine(auth.userFlow, auth.twoFactorUid, justCreated, sessionLoaded) { user, verifiedUid, created, loaded ->
            when {
                !loaded -> AuthGate.Loading
                user == null -> AuthGate.SignedOut
                verifiedUid != user.uid -> AuthGate.NeedsCode(user.email.orEmpty(), created)
                else -> AuthGate.SignedIn(user.displayName.orEmpty(), user.email.orEmpty())
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, AuthGate.Loading)

    init {
        viewModelScope.launch {
            auth.loadSession()
            // If this phone thinks 2FA passed but the server hasn't marked this login session
            // (e.g. signed in before server-side 2FA existed), ask for the email code again.
            val user = auth.currentUser
            if (user != null && auth.twoFactorUid.value == user.uid && !auth.hasServerMfa()) {
                auth.clearTwoFactor()
            }
            sessionLoaded.value = true
        }
    }

    fun clearError() = _form.update { it.copy(error = null) }

    fun signIn(email: String, password: String) {
        val e = email.trim()
        InputValidation.emailError(e)?.let { return fail(it) }
        if (password.isEmpty()) return fail("Enter your password.")
        run {
            auth.signIn(e, password)
            justCreated.value = false
            sendCodeInternal()
        }
    }

    fun signUp(name: String, email: String, password: String, confirm: String) {
        val e = email.trim()
        InputValidation.nameError(name)?.let { return fail(it) }
        InputValidation.emailError(e)?.let { return fail(it) }
        if (!InputValidation.passwordRules(password).allMet) return fail("Your password doesn't meet all the rules below.")
        if (password != confirm) return fail("The passwords don't match.")
        run {
            auth.signUp(name.trim(), e, password)
            justCreated.value = true
        }
    }

    /** "Continue" on the Account Created screen: sends the first email code. */
    fun continueAfterAccountCreated() {
        _form.update { it.copy(accountCreatedSeen = true) }
        run { sendCodeInternal() }
    }

    fun resendCode() = run { sendCodeInternal() }

    fun verifyCode(code: String) {
        val trimmed = code.trim()
        run {
            when (val r = otp.verifyCode(trimmed)) {
                is OtpService.Result.Ok -> {
                    val uid = auth.currentUser?.uid ?: throw AuthException("Please sign in again.")
                    // The server just added this session to the 2FA claim; refresh so Firestore sees it.
                    auth.refreshToken()
                    auth.markTwoFactorPassed(uid)
                    justCreated.value = false
                    _form.value = AuthFormState()
                }
                is OtpService.Result.Error -> throw AuthException(r.message)
            }
        }
    }

    fun sendPasswordReset(email: String) {
        val e = email.trim()
        InputValidation.emailError(e)?.let { return fail(it) }
        run {
            auth.sendPasswordReset(e)
            _form.update { it.copy(resetSentTo = e) }
        }
    }

    fun clearResetSent() = _form.update { it.copy(resetSentTo = null) }

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            justCreated.value = false
            _form.value = AuthFormState()
        }
    }

    private suspend fun sendCodeInternal() {
        when (val r = otp.sendCode()) {
            is OtpService.Result.Ok -> _form.update {
                it.copy(
                    codeSent = true,
                    codeExpiresAt = now() + CODE_TTL_MS,
                    resendAvailableAt = now() + RESEND_COOLDOWN_MS,
                    error = null,
                )
            }
            is OtpService.Result.Error -> {
                r.retryAfterSeconds?.let { s -> _form.update { it.copy(resendAvailableAt = now() + s * 1000L) } }
                throw AuthException(r.message)
            }
        }
    }

    private fun fail(message: String) = _form.update { it.copy(error = message, busy = false) }

    /** Runs an auth action with a busy indicator; shows only safe, user-facing messages. */
    private fun run(block: suspend () -> Unit) {
        if (_form.value.busy) return
        _form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                _form.update { it.copy(busy = false) }
            } catch (e: AuthException) {
                _form.update { it.copy(busy = false, error = e.message) }
            } catch (_: Exception) {
                _form.update { it.copy(busy = false, error = "Something went wrong. Please try again.") }
            }
        }
    }

    companion object {
        const val CODE_TTL_MS = 5 * 60 * 1000L
        const val RESEND_COOLDOWN_MS = 60 * 1000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                AuthViewModel(app.container.auth, app.container.otp)
            }
        }
    }
}
