package com.group8.permissionmanagerapp_uss.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.AppContainer
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.auth.AuthException
import com.group8.permissionmanagerapp_uss.data.auth.AuthRepository
import com.group8.permissionmanagerapp_uss.data.auth.InputValidation
import com.group8.permissionmanagerapp_uss.data.auth.RecoveryKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.SecureRandom

/** Where the user is before reaching the app. */
sealed interface AuthGate {
    data object Loading : AuthGate
    /** The phone has no secure lock screen, which the second sign-in factor needs. */
    data object ScreenLockMissing : AuthGate
    data object NoAccount : AuthGate
    data object SignedOut : AuthGate
    /** Recently signed in: a phone unlock alone reopens the app. */
    data class Locked(val name: String) : AuthGate
    /** Account just created: show the recovery key once, then confirm two groups of it. */
    data class ShowRecoveryKey(val key: String, val confirmGroups: List<Int>) : AuthGate
    data class SignedIn(val name: String, val email: String) : AuthGate
}

/** A step that runs after Android's unlock prompt succeeds. */
sealed interface PendingUnlock {
    val title: String
    data class CreateAccount(val name: String, val email: String, val password: String) : PendingUnlock {
        override val title = "Confirm it's you to secure your account"
    }
    data object CompleteSignIn : PendingUnlock { override val title = "Unlock to finish signing in" }
    data object QuickUnlock : PendingUnlock { override val title = "Unlock Permission Tracker" }
    data class Recover(val email: String, val key: String, val password: String) : PendingUnlock {
        override val title = "Confirm it's you to reset your password"
    }
    data object ResetApp : PendingUnlock { override val title = "Confirm it's you to reset the app" }
}

data class AuthFormState(
    val busy: Boolean = false,
    val error: String? = null,
    val pendingUnlock: PendingUnlock? = null,
)

class AuthViewModel(
    private val auth: AuthRepository,
    private val container: AppContainer?,
) : ViewModel() {

    private val _gate = MutableStateFlow<AuthGate>(AuthGate.Loading)
    val gate: StateFlow<AuthGate> = _gate.asStateFlow()

    private val _form = MutableStateFlow(AuthFormState())
    val form: StateFlow<AuthFormState> = _form.asStateFlow()

    init {
        refreshGate()
        viewModelScope.launch {
            auth.signedIn.collect { signedIn ->
                if (!signedIn && _gate.value is AuthGate.SignedIn) refreshGate()
            }
        }
    }

    /** Works out the starting screen: screen lock missing / create account / sign in / unlock. */
    fun refreshGate() {
        viewModelScope.launch {
            val account = auth.account()
            _gate.value = when {
                !auth.isDeviceSecure() -> AuthGate.ScreenLockMissing
                account == null -> AuthGate.NoAccount
                auth.signedIn.value -> AuthGate.SignedIn(account.name, account.email)
                auth.quickUnlockAllowed() -> AuthGate.Locked(account.name)
                else -> AuthGate.SignedOut
            }
        }
    }

    fun clearError() = _form.update { it.copy(error = null) }

    // ---- create account ----

    fun signUp(name: String, email: String, password: String, confirm: String) {
        val e = email.trim()
        InputValidation.nameError(name)?.let { return fail(it) }
        InputValidation.emailError(e)?.let { return fail(it) }
        if (!InputValidation.passwordRules(password, e).allMet) return fail("Your password doesn't meet all the rules below.")
        if (password != confirm) return fail("The passwords don't match.")
        if (!auth.isDeviceSecure()) return fail("Set up a screen lock (PIN, pattern or password) first.")
        _form.update { it.copy(error = null, pendingUnlock = PendingUnlock.CreateAccount(name.trim(), e, password)) }
    }

    /** Recovery key saved and two groups confirmed. */
    fun finishRecoveryKey(answers: List<String>): Boolean {
        val g = _gate.value as? AuthGate.ShowRecoveryKey ?: return false
        val groups = RecoveryKey.groups(g.key)
        val ok = g.confirmGroups.zip(answers).all { (index, answer) ->
            RecoveryKey.normalize(answer) == groups[index]
        }
        if (!ok) {
            fail("Those groups don't match your recovery key. Check what you wrote down.")
            return false
        }
        _form.value = AuthFormState()
        refreshGate()
        return true
    }

    // ---- sign in ----

    fun signIn(email: String, password: String) {
        InputValidation.emailError(email.trim())?.let { return fail(it) }
        if (password.isEmpty()) return fail("Enter your password.")
        run {
            auth.checkPassword(email, password)
            _form.update { it.copy(pendingUnlock = PendingUnlock.CompleteSignIn) }
        }
    }

    fun quickUnlock() = _form.update { it.copy(error = null, pendingUnlock = PendingUnlock.QuickUnlock) }

    /** "Use password instead" on the unlock screen. */
    fun usePasswordInstead() {
        viewModelScope.launch {
            auth.signOut()
            _form.value = AuthFormState()
            refreshGate()
        }
    }

    // ---- forgot password ----

    fun recover(email: String, key: String, password: String, confirm: String) {
        val e = email.trim()
        InputValidation.emailError(e)?.let { return fail(it) }
        if (!RecoveryKey.isWellFormed(key)) return fail("Enter all 24 characters of your recovery key.")
        if (!InputValidation.passwordRules(password, e).allMet) return fail("Your new password doesn't meet all the rules.")
        if (password != confirm) return fail("The passwords don't match.")
        _form.update { it.copy(error = null, pendingUnlock = PendingUnlock.Recover(e, key, password)) }
    }

    fun resetApp() = _form.update { it.copy(error = null, pendingUnlock = PendingUnlock.ResetApp) }

    // ---- unlock prompt results ----

    fun onUnlockSucceeded() {
        val pending = _form.value.pendingUnlock ?: return
        _form.update { it.copy(pendingUnlock = null) }
        run {
            when (pending) {
                is PendingUnlock.CreateAccount -> {
                    val key = auth.createAccount(pending.name, pending.email, pending.password)
                    _gate.value = AuthGate.ShowRecoveryKey(key, pickTwoGroups())
                }
                PendingUnlock.CompleteSignIn -> {
                    auth.completeWithDeviceUnlock(fullSignIn = true)
                    refreshGate()
                }
                PendingUnlock.QuickUnlock -> {
                    auth.completeWithDeviceUnlock(fullSignIn = false)
                    refreshGate()
                }
                is PendingUnlock.Recover -> {
                    auth.recoverWithKey(pending.email, pending.key, pending.password)
                    refreshGate()
                }
                PendingUnlock.ResetApp -> {
                    auth.resetAccount()
                    container?.wipeAllAppData()
                    refreshGate()
                }
            }
        }
    }

    fun onUnlockFailed(message: String) = _form.update { it.copy(pendingUnlock = null, error = message) }

    // ---- signed in ----

    fun signOut() {
        viewModelScope.launch {
            auth.signOut()
            _form.value = AuthFormState()
            refreshGate()
        }
    }

    /** Called when the app returns after being in the background too long. */
    fun autoLock() {
        if (_gate.value !is AuthGate.SignedIn) return
        auth.lock()
        refreshGate()
    }

    private fun pickTwoGroups(): List<Int> {
        val r = SecureRandom()
        val first = r.nextInt(6)
        var second = r.nextInt(6)
        while (second == first) second = r.nextInt(6)
        return listOf(first, second).sorted()
    }

    private fun fail(message: String) = _form.update { it.copy(error = message, busy = false) }

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
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                AuthViewModel(app.container.auth, app.container)
            }
        }
    }
}
