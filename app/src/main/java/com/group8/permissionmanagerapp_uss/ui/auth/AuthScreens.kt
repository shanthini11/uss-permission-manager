package com.group8.permissionmanagerapp_uss.ui.auth

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.ScreenLockPortrait
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.group8.permissionmanagerapp_uss.AppInfo
import com.group8.permissionmanagerapp_uss.data.auth.InputValidation
import com.group8.permissionmanagerapp_uss.data.auth.RecoveryKey
import com.group8.permissionmanagerapp_uss.ui.common.SecureScreen
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

private enum class SignedOutPage { SIGN_IN, RECOVER }
private enum class NoAccountPage { LANDING, SIGN_UP }

/** Everything before the main app. Runs Android's unlock prompt whenever a step needs it. */
@Composable
fun AuthFlow(gate: AuthGate, viewModel: AuthViewModel) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(form.pendingUnlock) {
        val pending = form.pendingUnlock ?: return@LaunchedEffect
        DeviceUnlock.prompt(
            context = context,
            title = pending.title,
            subtitle = "Use your fingerprint, face, or your phone's PIN, pattern or password",
            onSuccess = viewModel::onUnlockSucceeded,
            onError = viewModel::onUnlockFailed,
        )
    }

    when (gate) {
        AuthGate.Loading, is AuthGate.SignedIn ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        AuthGate.ScreenLockMissing -> ScreenLockMissingScreen(viewModel)
        AuthGate.NoAccount -> NoAccountFlow(form, viewModel)
        AuthGate.SignedOut -> SignedOutFlow(form, viewModel)
        is AuthGate.Locked -> UnlockScreen(gate.name, form, viewModel)
        is AuthGate.ShowRecoveryKey -> RecoveryKeyScreen(gate, form, viewModel)
    }
}

/** The second factor is the phone's own unlock, so a secure lock screen is required. */
@Composable
private fun ScreenLockMissingScreen(viewModel: AuthViewModel) {
    val context = LocalContext.current
    LifecycleResumeEffect(Unit) {
        viewModel.refreshGate()
        onPauseOrDispose { }
    }
    AuthScaffold(
        title = "Set up a screen lock first",
        subtitle = "${AppInfo.NAME} uses your phone's unlock (fingerprint, face or PIN) as a second step when you sign in. " +
            "Your phone doesn't have a screen lock yet.",
    ) {
        Icon(Icons.Outlined.ScreenLockPortrait, contentDescription = null, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally))
        Text("1.  Open Security settings\n2.  Choose Screen lock → PIN (or pattern/password)\n3.  Come back here", style = MaterialTheme.typography.bodyMedium)
        PrimaryButton("Open Security settings", busy = false) {
            try {
                context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        TextButton(onClick = viewModel::refreshGate, modifier = Modifier.fillMaxWidth()) { Text("I've set it up") }
    }
}

@Composable
private fun NoAccountFlow(form: AuthFormState, viewModel: AuthViewModel) {
    var page by rememberSaveable { mutableStateOf(NoAccountPage.LANDING) }
    BackHandler(enabled = page != NoAccountPage.LANDING) {
        viewModel.clearError()
        page = NoAccountPage.LANDING
    }
    when (page) {
        NoAccountPage.LANDING -> AuthScaffold(
            title = "Know which apps can access your data",
            subtitle = "See every app's permissions in plain language, spot risky access, and keep a private record of changes.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WifiOff, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Works fully offline. Your account and data never leave this phone: no servers, no internet permission.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.height(4.dp))
            PrimaryButton("Create your account", busy = false) {
                viewModel.clearError()
                page = NoAccountPage.SIGN_UP
            }
        }
        NoAccountPage.SIGN_UP -> SignUpScreen(form, viewModel)
    }
}

/** Create account: name, email (your sign-in name), password with a live checklist. */
@Composable
private fun SignUpScreen(form: AuthFormState, viewModel: AuthViewModel) {
    SecureScreen()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords use remember (not rememberSaveable) so they never go into the saved-state bundle.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val rules = InputValidation.passwordRules(password, email)

    AuthScaffold(
        title = "Create your account",
        subtitle = "Stored only on this phone. You'll sign in with this password plus your phone's unlock.",
    ) {
        ErrorBanner(form.error)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(InputValidation.MAX_NAME) },
            label = { Text("Full name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        EmailField(email, { email = it })
        PasswordField(password, { password = it }, imeAction = ImeAction.Next)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Rule("At least ${InputValidation.MIN_PASSWORD} characters", rules.length)
            Rule("Contains a letter", rules.letter)
            Rule("Contains a number", rules.digit)
            if (password.isNotEmpty()) {
                Rule("Not a common password", rules.notCommon)
                Rule("Doesn't contain your email name", rules.notEmailName)
            }
        }
        PasswordField(confirm, { confirm = it }, label = "Confirm password", onDone = { viewModel.signUp(name, email, password, confirm) })
        if (confirm.isNotEmpty() && confirm != password) {
            Text("The passwords don't match.", color = LocalRiskColors.current.high, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton("Create account", busy = form.busy) { viewModel.signUp(name, email, password, confirm) }
        CenteredNote("Next, confirm with your fingerprint, face or PIN, then save your recovery key.")
    }
}

/** Shown once after sign-up: the recovery key, then a check that it was written down correctly. */
@Composable
private fun RecoveryKeyScreen(gate: AuthGate.ShowRecoveryKey, form: AuthFormState, viewModel: AuthViewModel) {
    SecureScreen()
    var saved by rememberSaveable { mutableStateOf(false) }
    var confirming by rememberSaveable { mutableStateOf(false) }
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    val groups = RecoveryKey.groups(gate.key)

    if (!confirming) {
        AuthScaffold(
            title = "Save your recovery key",
            subtitle = "If you forget your password, this key is the only way to reset it. Nobody else has a copy, not even us.",
        ) {
            Icon(Icons.Outlined.Key, contentDescription = null, modifier = Modifier.size(44.dp).align(Alignment.CenterHorizontally))
            Column(
                Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                groups.chunked(3).forEachIndexed { row, line ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        line.forEachIndexed { i, g ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${row * 3 + i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(g, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                            }
                        }
                    }
                }
            }
            Text(
                "Write it on paper and keep it somewhere safe, away from your phone. Screenshots are blocked on this screen for your protection.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                Modifier.fillMaxWidth().toggleable(value = saved, role = Role.Checkbox, onValueChange = { saved = it }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = saved, onCheckedChange = null)
                Spacer(Modifier.width(8.dp))
                Text("I've written down my recovery key", style = MaterialTheme.typography.bodyMedium)
            }
            PrimaryButton("Continue", busy = false, enabled = saved) { confirming = true }
        }
    } else {
        val (g1, g2) = gate.confirmGroups
        AuthScaffold(title = "Confirm your recovery key", subtitle = "Type groups ${g1 + 1} and ${g2 + 1} from the key you wrote down.") {
            ErrorBanner(form.error)
            GroupField("Group ${g1 + 1}", a) { a = it }
            GroupField("Group ${g2 + 1}", b) { b = it }
            PrimaryButton("Confirm", busy = false, enabled = a.length == 4 && b.length == 4) { viewModel.finishRecoveryKey(listOf(a, b)) }
            TextButton(onClick = { confirming = false; viewModel.clearError() }, modifier = Modifier.fillMaxWidth()) { Text("Show the key again") }
        }
    }
}

@Composable
private fun GroupField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.uppercase().filter { it.isLetterOrDigit() }.take(4)) },
        label = { Text(label) },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace, letterSpacing = 4.sp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun SignedOutFlow(form: AuthFormState, viewModel: AuthViewModel) {
    var page by rememberSaveable { mutableStateOf(SignedOutPage.SIGN_IN) }
    BackHandler(enabled = page != SignedOutPage.SIGN_IN) {
        viewModel.clearError()
        page = SignedOutPage.SIGN_IN
    }
    when (page) {
        SignedOutPage.SIGN_IN -> SignInScreen(form, viewModel) {
            viewModel.clearError()
            page = SignedOutPage.RECOVER
        }
        SignedOutPage.RECOVER -> RecoverScreen(form, viewModel) {
            viewModel.clearError()
            page = SignedOutPage.SIGN_IN
        }
    }
}

/** Sign in: password (factor 1), then the phone's unlock (factor 2). */
@Composable
private fun SignInScreen(form: AuthFormState, viewModel: AuthViewModel, onForgot: () -> Unit) {
    SecureScreen()
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AuthScaffold(title = "Sign in") {
        ErrorBanner(form.error)
        EmailField(email, { email = it })
        PasswordField(password, { password = it }, onDone = { viewModel.signIn(email, password) })
        PrimaryButton("Sign in", busy = form.busy) { viewModel.signIn(email, password) }
        TextButton(onClick = onForgot, modifier = Modifier.fillMaxWidth()) { Text("Forgot password?") }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("After your password, unlock with your fingerprint, face or PIN to confirm it's really you.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Forgot password: recovery key -> new password. Lost both: reset the app on this phone. */
@Composable
private fun RecoverScreen(form: AuthFormState, viewModel: AuthViewModel, onBack: () -> Unit) {
    SecureScreen()
    var email by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    val rules = InputValidation.passwordRules(password, email)
    val risk = LocalRiskColors.current

    AuthScaffold(title = "Reset your password", subtitle = "Use the 24-character recovery key you saved when you created your account.") {
        ErrorBanner(form.error)
        EmailField(email, { email = it })
        OutlinedTextField(
            value = key,
            onValueChange = { key = it.take(40) },
            label = { Text("Recovery key") },
            placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        PasswordField(password, { password = it }, label = "New password", imeAction = ImeAction.Next)
        if (password.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Rule("At least ${InputValidation.MIN_PASSWORD} characters, a letter and a number", rules.length && rules.letter && rules.digit)
                Rule("Not a common password", rules.notCommon)
            }
        }
        PasswordField(confirm, { confirm = it }, label = "Confirm new password")
        PrimaryButton("Reset password", busy = form.busy) { viewModel.recover(email, key, password, confirm) }
        TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
        TextButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Lost your recovery key too? Reset the app", color = risk.high)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            icon = { Icon(Icons.Outlined.LockReset, contentDescription = null, tint = risk.high) },
            title = { Text("Reset ${AppInfo.NAME}?") },
            text = {
                Text(
                    "This deletes your account, permission history and settings from this phone so you can start again. " +
                        "Android's app permissions are not changed. You'll confirm with your phone's unlock.",
                )
            },
            confirmButton = {
                Button(onClick = { confirmReset = false; viewModel.resetApp() }, colors = ButtonDefaults.buttonColors(containerColor = risk.high)) {
                    Text("Reset app")
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

/** Reopening the app within 12 hours of signing in: the phone's unlock is enough. */
@Composable
private fun UnlockScreen(name: String, form: AuthFormState, viewModel: AuthViewModel) {
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked) {
            asked = true
            viewModel.quickUnlock()
        }
    }
    AuthScaffold(title = "Welcome back${if (name.isNotBlank()) ", ${name.substringBefore(' ')}" else ""}", subtitle = "${AppInfo.NAME} is locked.") {
        Icon(Icons.Outlined.Fingerprint, contentDescription = null, modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally))
        ErrorBanner(form.error)
        PrimaryButton("Unlock", busy = form.busy) { viewModel.quickUnlock() }
        OutlinedButton(onClick = viewModel::usePasswordInstead, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Sign in with password instead")
        }
    }
}

@Composable
private fun Rule(text: String, met: Boolean) {
    val risk = LocalRiskColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (met) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = if (met) "Met" else "Not met",
            tint = if (met) risk.low else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** "manojna@gmail.com" -> "ma•••••@gmail.com" so the full address isn't shown on screen. */
internal fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    val visible = email.take(minOf(2, at))
    return visible + "•".repeat((at - visible.length).coerceAtLeast(3)) + email.substring(at)
}

