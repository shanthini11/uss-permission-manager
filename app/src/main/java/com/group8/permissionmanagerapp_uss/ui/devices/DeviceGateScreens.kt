package com.group8.permissionmanagerapp_uss.ui.devices

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.PhonelinkErase
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.group8.permissionmanagerapp_uss.AppInfo
import com.group8.permissionmanagerapp_uss.data.devices.DeviceInfo
import com.group8.permissionmanagerapp_uss.ui.auth.AuthScaffold
import com.group8.permissionmanagerapp_uss.ui.auth.CenteredNote
import com.group8.permissionmanagerapp_uss.ui.auth.ErrorBanner
import com.group8.permissionmanagerapp_uss.ui.auth.PrimaryButton
import com.group8.permissionmanagerapp_uss.ui.common.SecureScreen
import com.group8.permissionmanagerapp_uss.ui.common.TimeFormat
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

/** Screens a phone sees before it's part of the account (refinements #3–#6). */
@Composable
fun DeviceGateFlow(gate: DeviceGate, viewModel: DeviceGateViewModel, onSignOut: () -> Unit) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    when (gate) {
        DeviceGate.Checking, DeviceGate.Approved ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is DeviceGate.Setup -> AddDeviceScreen(gate, form, viewModel, onSignOut)
        is DeviceGate.Pending -> VerifyDeviceScreen(gate, form, viewModel)
        DeviceGate.Added -> DeviceAddedScreen(viewModel)
        DeviceGate.Removed -> AuthScaffold(
            title = "This device was removed",
            subtitle = "This phone was removed from your account on another device. To use it again, it needs to be approved.",
        ) {
            Icon(Icons.Outlined.PhonelinkErase, contentDescription = null, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally))
            ErrorBanner(form.error)
            PrimaryButton("Add this device again", busy = form.busy) { viewModel.addAgain() }
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        }
        is DeviceGate.Offline -> AuthScaffold(title = "You're offline", subtitle = gate.message) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally))
            PrimaryButton("Try again", busy = false) { viewModel.check() }
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
        }
    }
}

/** Refinement #3 "Add New Device": name, OS, generated ID, start fresh or import. */
@Composable
private fun AddDeviceScreen(gate: DeviceGate.Setup, form: DeviceForm, viewModel: DeviceGateViewModel, onSignOut: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(gate.defaultName) }
    var importPrefs by rememberSaveable { mutableStateOf(true) }

    AuthScaffold(
        title = "Add this device",
        subtitle = "Your account is already used on another device. To keep your account safe, that device needs to approve this one.",
    ) {
        ErrorBanner(form.error)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Device name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        )
        SectionCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("Operating system", "Android ${Build.VERSION.RELEASE}")
                InfoLine("Your devices", gate.otherDevices.joinToString { it.name }.ifBlank { "—" })
            }
        }
        Column(Modifier.selectableGroup()) {
            Choice("Start fresh", "Use default settings on this phone.", !importPrefs) { importPrefs = false }
            Choice("Import my preferences", "Copy notification, history and weekly review settings from your account.", importPrefs) { importPrefs = true }
        }
        CenteredNote(
            "Once approved, this phone shares a short permission summary (device name, counts and its 10 riskiest apps) " +
                "with your other devices. You can turn this off in My Devices.",
        )
        PrimaryButton("Continue", busy = form.busy) { viewModel.requestToJoin(name, importPrefs) }
        TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text("Log out") }
    }
}

/** Refinement #4 "Verify New Device" (+ #6 error states). */
@Composable
private fun VerifyDeviceScreen(gate: DeviceGate.Pending, form: DeviceForm, viewModel: DeviceGateViewModel) {
    SecureScreen()
    val context = LocalContext.current
    var code by rememberSaveable { mutableStateOf("") }
    var emailCode by rememberSaveable { mutableStateOf("") }
    var useEmail by rememberSaveable { mutableStateOf(false) }
    val request = gate.request

    if (request.denied) {
        // #6 Error in Adding New Device.
        AuthScaffold(title = "Couldn't add this device", subtitle = "This request was denied on one of your other devices.") {
            ErrorBanner(form.error)
            PrimaryButton("Request a new code", busy = form.busy) { viewModel.requestNewCode() }
            TextButton(onClick = viewModel::cancelRequest, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
        }
        return
    }

    if (useEmail) {
        AuthScaffold(title = "Verify with email", subtitle = "We'll email a 6-digit code to your account's address instead.") {
            ErrorBanner(form.error)
            if (!form.emailCodeSent) {
                PrimaryButton("Send code", busy = form.busy) { viewModel.sendEmailCode() }
            } else {
                CodeField(emailCode) { emailCode = it }
                PrimaryButton("Verify", busy = form.busy, enabled = emailCode.length == 6) { viewModel.verifyEmailCode(emailCode) }
                TextButton(onClick = viewModel::sendEmailCode, modifier = Modifier.fillMaxWidth()) { Text("Resend code") }
            }
            TextButton(onClick = { useEmail = false }, modifier = Modifier.fillMaxWidth()) { Text("Use my other device instead") }
        }
        return
    }

    AuthScaffold(
        title = "Verify this device",
        subtitle = "Open ${AppInfo.NAME} on one of your other devices. It will show a 6-digit code. Enter it here.",
    ) {
        SectionCard {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("YOUR DEVICES", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                gate.otherDevices.forEach { d: DeviceInfo ->
                    Text("•  ${d.name} · last active ${TimeFormat.dateTime(context, d.lastActive)}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ErrorBanner(form.error)
        CodeField(code) { code = it }
        val status = when {
            !request.hasCode -> "Waiting for your other device to show a code…"
            (request.codeExpiresAt ?: 0) < System.currentTimeMillis() -> "The code has expired. Request a new one."
            else -> "A code is showing on your other device. It expires 5 minutes after it appears."
        }
        Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PrimaryButton("Verify", busy = form.busy, enabled = code.length == 6) { viewModel.verifyCode(code) }
        HorizontalDivider()
        TextButton(onClick = viewModel::requestNewCode, modifier = Modifier.fillMaxWidth()) { Text("Request a new code") }
        TextButton(onClick = { useEmail = true }, modifier = Modifier.fillMaxWidth()) { Text("Don't have your other device? Verify with email") }
        TextButton(onClick = viewModel::cancelRequest, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

/** Refinement #5 "New Device Added". */
@Composable
private fun DeviceAddedScreen(viewModel: DeviceGateViewModel) {
    AuthScaffold(title = "Device added") {
        Icon(
            Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = LocalRiskColors.current.low,
            modifier = Modifier.size(64.dp).align(Alignment.CenterHorizontally),
        )
        Text(
            "This phone is now connected to your account. You can see and remove your devices any time in Settings › My Devices.",
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton("Continue", busy = false) { viewModel.continueAfterAdded() }
    }
}

@Composable
private fun CodeField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(6)) },
        label = { Text("6-digit code") },
        singleLine = true,
        textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 8.sp, textAlign = TextAlign.Center),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(130.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Choice(title: String, subtitle: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
