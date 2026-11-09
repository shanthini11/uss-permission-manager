package com.group8.permissionmanagerapp_uss.ui.auth

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.MarkEmailRead
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.group8.permissionmanagerapp_uss.AppInfo
import com.group8.permissionmanagerapp_uss.data.auth.InputValidation
import com.group8.permissionmanagerapp_uss.ui.common.SecureScreen
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors
import kotlinx.coroutines.delay

private enum class AuthPage { LANDING, SIGN_IN, SIGN_UP, FORGOT }

/** Everything before the main app: landing, sign in/up, reset, account created, email code. */
@Composable
fun AuthFlow(gate: AuthGate, viewModel: AuthViewModel) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    when (gate) {
        AuthGate.Loading, is AuthGate.SignedIn ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

        AuthGate.SignedOut -> SignedOutFlow(form, viewModel)

        is AuthGate.NeedsCode ->
            if (gate.justCreated && !form.accountCreatedSeen) AccountCreatedScreen(gate.email, form, viewModel)
            else CodeScreen(gate.email, form, viewModel)
    }
}

@Composable
private fun SignedOutFlow(form: AuthFormState, viewModel: AuthViewModel) {
    var page by rememberSaveable { mutableStateOf(AuthPage.LANDING) }
    val go: (AuthPage) -> Unit = {
        viewModel.clearError()
        viewModel.clearResetSent()
        page = it
    }
    BackHandler(enabled = page != AuthPage.LANDING) { go(AuthPage.LANDING) }

    when (page) {
        AuthPage.LANDING -> LandingScreen(onSignIn = { go(AuthPage.SIGN_IN) }, onSignUp = { go(AuthPage.SIGN_UP) })
        AuthPage.SIGN_IN -> SignInScreen(form, viewModel, onForgot = { go(AuthPage.FORGOT) }, onSignUp = { go(AuthPage.SIGN_UP) })
        AuthPage.SIGN_UP -> SignUpScreen(form, viewModel, onSignIn = { go(AuthPage.SIGN_IN) })
        AuthPage.FORGOT -> ForgotPasswordScreen(form, viewModel, onBackToSignIn = { go(AuthPage.SIGN_IN) })
    }
}

/** Lo-fi "Landing Page": Sign in / Sign up. */
@Composable
private fun LandingScreen(onSignIn: () -> Unit, onSignUp: () -> Unit) {
    AuthScaffold(
        title = "Know which apps can access your data",
        subtitle = "See every app's permissions in plain language, spot risky access, and keep a private record of changes.",
    ) {
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Sign in", busy = false, onClick = onSignIn)
        OutlinedButton(onClick = onSignUp, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Text("Create an account", style = MaterialTheme.typography.titleSmall)
        }
        CenteredNote("Your permission data stays on this phone. Your account is only used to sign in and protect your settings.")
    }
}

/** Lo-fi "Sign In": email, password with eye toggle, Sign in, Forgot password. */
@Composable
private fun SignInScreen(form: AuthFormState, viewModel: AuthViewModel, onForgot: () -> Unit, onSignUp: () -> Unit) {
    SecureScreen()
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords use remember (not rememberSaveable) so they never go into the saved-state bundle.
    var password by remember { mutableStateOf("") }
    AuthScaffold(title = "Sign in") {
        ErrorBanner(form.error)
        EmailField(email, { email = it })
        PasswordField(password, { password = it }, onDone = { viewModel.signIn(email, password) })
        PrimaryButton("Sign in", busy = form.busy) { viewModel.signIn(email, password) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onForgot) { Text("Forgot password?") }
            TextButton(onClick = onSignUp) { Text("Create an account") }
        }
        CenteredNote("After your password, we'll email you a 6-digit code to confirm it's you.")
    }
}

/** Lo-fi "Sign Up", with a live password checklist. */
@Composable
private fun SignUpScreen(form: AuthFormState, viewModel: AuthViewModel, onSignIn: () -> Unit) {
    SecureScreen()
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val rules = InputValidation.passwordRules(password)

    AuthScaffold(title = "Create your account") {
        ErrorBanner(form.error)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(InputValidation.MAX_NAME) },
            label = { Text("Full name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        EmailField(email, { email = it })
        PasswordField(password, { password = it }, imeAction = ImeAction.Next)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Rule("At least 8 characters", rules.length)
            Rule("Contains a letter", rules.letter)
            Rule("Contains a number", rules.digit)
        }
        PasswordField(confirm, { confirm = it }, label = "Confirm password", onDone = { viewModel.signUp(name, email, password, confirm) })
        if (confirm.isNotEmpty() && confirm != password) {
            Text("The passwords don't match.", color = LocalRiskColors.current.high, style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton("Create account", busy = form.busy) { viewModel.signUp(name, email, password, confirm) }
        TextButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) { Text("Already have an account? Sign in") }
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

/** Lo-fi "Reset Link Sent". The message is the same whether or not the account exists. */
@Composable
private fun ForgotPasswordScreen(form: AuthFormState, viewModel: AuthViewModel, onBackToSignIn: () -> Unit) {
    var email by rememberSaveable { mutableStateOf("") }
    val sentTo = form.resetSentTo
    if (sentTo != null) {
        AuthScaffold(title = "Check your email") {
            Icon(Icons.Outlined.MarkEmailRead, contentDescription = null, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally))
            Text(
                "If an account exists for $sentTo, we've sent a link to reset your password. It may take a minute to arrive; check your spam folder too.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            PrimaryButton("Back to sign in", busy = false, onClick = onBackToSignIn)
        }
        return
    }
    AuthScaffold(title = "Reset your password", subtitle = "Enter your account email and we'll send you a reset link.") {
        ErrorBanner(form.error)
        EmailField(email, { email = it }, imeAction = ImeAction.Done)
        PrimaryButton("Send reset link", busy = form.busy) { viewModel.sendPasswordReset(email) }
        TextButton(onClick = onBackToSignIn, modifier = Modifier.fillMaxWidth()) { Text("Back to sign in") }
    }
}

/** Lo-fi "Account Created". */
@Composable
private fun AccountCreatedScreen(email: String, form: AuthFormState, viewModel: AuthViewModel) {
    AuthScaffold(title = "Account created") {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = LocalRiskColors.current.low,
            modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            "Your ${AppInfo.NAME} account is ready. Next, we'll send a 6-digit code to $email to confirm it's really yours.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        ErrorBanner(form.error)
        PrimaryButton("Continue", busy = form.busy) { viewModel.continueAfterAccountCreated() }
        TextButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth()) { Text("Sign out") }
    }
}

/** Refinement #1: "Verify your email with 2-factor authentication" — 6-digit code, expires in 5 minutes. */
@Composable
private fun CodeScreen(email: String, form: AuthFormState, viewModel: AuthViewModel) {
    SecureScreen()
    var code by rememberSaveable { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val secondsLeft = ((form.codeExpiresAt - now) / 1000).coerceAtLeast(0)
    val resendIn = ((form.resendAvailableAt - now) / 1000).coerceAtLeast(0)

    AuthScaffold(title = "Verify it's you", subtitle = "Enter the 6-digit code we emailed to ${maskEmail(email)}.") {
        ErrorBanner(form.error)
        if (!form.codeSent) {
            Text("Tap below and we'll email you a code.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Send code", busy = form.busy) { viewModel.resendCode() }
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { input -> code = input.filter { it.isDigit() }.take(6) },
                label = { Text("6-digit code") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 8.sp, textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                if (secondsLeft > 0) "Code expires in ${secondsLeft / 60}:${"%02d".format(secondsLeft % 60)}"
                else "This code has expired. Request a new one.",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (secondsLeft > 0) MaterialTheme.colorScheme.onSurfaceVariant else LocalRiskColors.current.high,
            )
            PrimaryButton("Verify", busy = form.busy, enabled = code.length == 6) { viewModel.verifyCode(code) }
            TextButton(onClick = viewModel::resendCode, enabled = resendIn == 0L && !form.busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (resendIn > 0) "Resend code in ${resendIn}s" else "Resend code")
            }
        }
        CenteredNote("Can't find it? Check your spam folder. Never share this code with anyone.")
        TextButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) { Text("Use a different account") }
    }
}

/** "manojna@gmail.com" -> "ma•••••@gmail.com" so the full address isn't shown on screen. */
internal fun maskEmail(email: String): String {
    val at = email.indexOf('@')
    if (at <= 0) return email
    val visible = email.take(minOf(2, at))
    return visible + "•".repeat((at - visible.length).coerceAtLeast(3)) + email.substring(at)
}
