package com.group8.permissionmanagerapp_uss.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.group8.permissionmanagerapp_uss.AppInfo
import com.group8.permissionmanagerapp_uss.data.devices.DeviceInfo
import com.group8.permissionmanagerapp_uss.ui.auth.rememberIdentityGuard
import com.group8.permissionmanagerapp_uss.ui.common.SecureScreen
import com.group8.permissionmanagerapp_uss.ui.common.TimeFormat
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.ui.settings.ResultDialog
import com.group8.permissionmanagerapp_uss.ui.theme.LocalRiskColors

/** Refinement #2 "My Devices" + lo-fi "Remove Selected Devices". */
@Composable
fun MyDevicesScreen(
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    viewModel: MyDevicesViewModel = viewModel(factory = MyDevicesViewModel.Factory),
) {
    val list by viewModel.list.collectAsStateWithLifecycle()
    val selecting by viewModel.selecting.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val remove by viewModel.remove.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val identity = rememberIdentityGuard()
    identity.Dialog()
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAddInfo by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (list.loading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val others = list.devices.filterNot { it.isThisDevice }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Manage the devices connected to your account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (others.isNotEmpty()) {
                    TextButton(onClick = { viewModel.setSelecting(!selecting) }) { Text(if (selecting) "Done" else "Select devices") }
                }
            }
        }
        if (list.devices.isEmpty()) {
            item { EmptyState("No devices yet", "Couldn't load your devices. Check your internet connection.") }
        }
        item {
            SectionCard {
                Column {
                    list.devices.forEachIndexed { i, device ->
                        if (i > 0) HorizontalDivider()
                        DeviceRow(
                            device = device,
                            selecting = selecting,
                            selected = device.id in selected,
                            onClick = { if (selecting) viewModel.toggle(device) else detailId = device.id },
                        )
                    }
                }
            }
        }
        if (selecting) {
            item {
                Button(
                    onClick = {
                        val targets = list.devices.filter { it.id in selected }
                        identity.run("remove ${if (targets.size == 1) "this device" else "these devices"}") { viewModel.askRemove(targets) }
                    },
                    enabled = selected.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = LocalRiskColors.current.high),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Remove selected (${selected.size})") }
                Text(
                    "This phone can't be removed here. Use Log out in Profile instead.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            item {
                OutlinedButton(onClick = { showAddInfo = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add device")
                }
            }
        }
        list.devices.firstOrNull { it.isThisDevice }?.let { me ->
            item {
                SectionCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Share this phone's summary", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Lets your other devices see this phone's permission counts and its 10 riskiest apps. Only you can see it.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = me.shareSummary, onCheckedChange = viewModel::setShareSummary)
                    }
                }
            }
        }
    }

    list.devices.firstOrNull { it.id == detailId }?.let { device ->
        DeviceDetailSheet(
            device = device,
            onDismiss = { detailId = null },
            onRemove = {
                detailId = null
                identity.run("remove this device") { viewModel.askRemove(listOf(device)) }
            },
            onRefresh = viewModel::refreshThisDevice,
        )
    }

    if (showAddInfo) {
        AlertDialog(
            onDismissRequest = { showAddInfo = false },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            title = { Text("Add a device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1.  Install ${AppInfo.NAME} on the new phone.")
                    Text("2.  Sign in with this account (password + email code).")
                    Text("3.  A 6-digit code will appear here on this phone. Enter it on the new phone.")
                    Text("This makes sure nobody can add a device to your account without a phone you already trust.", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { Button(onClick = { showAddInfo = false }) { Text("Got it") } },
        )
    }

    when (val s = remove) {
        RemoveStep.Idle -> Unit
        RemoveStep.Working -> Unit
        // #7 Verify Device Before Removing (password already confirmed on this trusted phone).
        is RemoveStep.Confirm -> AlertDialog(
            onDismissRequest = viewModel::dismissRemove,
            icon = { Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = LocalRiskColors.current.high) },
            title = { Text(if (s.devices.size == 1) "Remove ${s.devices.first().name}?" else "Remove ${s.devices.size} devices?") },
            text = {
                Text(
                    "${s.devices.joinToString { it.name }} will be disconnected from your account. " +
                        "To use ${AppInfo.NAME} there again, it will need to be approved from this phone.",
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmRemove, colors = ButtonDefaults.buttonColors(containerColor = LocalRiskColors.current.high)) {
                    Text("Remove")
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissRemove) { Text("Cancel") } },
        )
        // #8 Device Removed.
        is RemoveStep.Removed -> ResultDialog(
            success = true,
            title = if (s.count == 1) "Device removed" else "Devices removed",
            message = "The ${if (s.count == 1) "device has" else "devices have"} been disconnected from your account.",
            onDone = viewModel::dismissRemove,
        )
        // #9 Error in Removing Device.
        is RemoveStep.Error -> ResultDialog(
            success = false,
            title = "Device not removed",
            message = s.message,
            onDone = viewModel::dismissRemove,
            onRetry = viewModel::confirmRemove,
        )
    }
}

@Composable
private fun DeviceRow(device: DeviceInfo, selecting: Boolean, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selecting) {
            Checkbox(checked = selected, onCheckedChange = null, enabled = !device.isThisDevice)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (device.isThisDevice) Icons.Filled.Check else Icons.Outlined.PhoneAndroid, contentDescription = null)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (device.isThisDevice) "This device" else device.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (device.isThisDevice) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text("Current device", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Text(
                "${if (device.isThisDevice) device.name + " · " else ""}Last active: ${TimeFormat.dateTime(context, device.lastActive)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A device's details and permission summary: "view permissions across devices" (UP6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceDetailSheet(device: DeviceInfo, onDismiss: () -> Unit, onRemove: () -> Unit, onRefresh: () -> Unit) {
    val context = LocalContext.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhoneAndroid, contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${device.model} · ${device.os}", style = MaterialTheme.typography.bodySmall)
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
            }
            SectionCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Line("Added", TimeFormat.dateTime(context, device.addedAt))
                    Line("Last active", TimeFormat.dateTime(context, device.lastActive))
                }
            }
            val s = device.summary
            if (s == null) {
                SectionCard {
                    Text(
                        if (device.shareSummary) "No summary yet. It appears after the device next opens ${AppInfo.NAME}."
                        else "This device doesn't share its permission summary.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Stat("Apps", s.appCount, Modifier.weight(1f))
                    Stat("High risk", s.highRisk, Modifier.weight(1f))
                    Stat("Background", s.background, Modifier.weight(1f))
                }
                if (s.topApps.isNotEmpty()) {
                    SectionCard {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("RISKIEST APPS ON THIS DEVICE", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            s.topApps.forEach { app ->
                                Column {
                                    Text("${app.label} · ${app.risk}", style = MaterialTheme.typography.titleSmall)
                                    Text(app.permissions.ifBlank { "No sensitive access" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text("Updated ${TimeFormat.dateTime(context, s.updatedAt)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (device.isThisDevice) {
                OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Update this phone's summary now") }
            } else {
                Text(
                    "To change permissions on ${device.name}, open ${AppInfo.NAME} on that device. Android only lets each phone change its own permissions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRemove,
                    colors = ButtonDefaults.buttonColors(containerColor = LocalRiskColors.current.high),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Remove this device") }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row {
        Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier) {
    SectionCard(modifier) {
        Column(Modifier.fillMaxWidth().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Shown on an approved phone when another device asks to join: the code to enter there. */
@Composable
fun JoinApprovalHost(viewModel: JoinApprovalViewModel = viewModel(factory = JoinApprovalViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val request = state.request ?: return
    SecureScreen()
    AlertDialog(
        onDismissRequest = {},
        icon = { Icon(Icons.Outlined.PhoneAndroid, contentDescription = null) },
        title = { Text("New device wants to join") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${request.name} (${request.model}, ${request.os}) is trying to connect to your account.")
                if (state.code != null) {
                    Text("If this is you, enter this code on that device:")
                    Text(
                        state.code!!.chunked(3).joinToString(" "),
                        style = MaterialTheme.typography.displaySmall.copy(letterSpacing = 4.sp),
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("The code expires in 5 minutes. Never share it with anyone else.", style = MaterialTheme.typography.bodySmall)
                } else if (state.error != null) {
                    Text(state.error!!, color = LocalRiskColors.current.high)
                } else if (request.hasCode) {
                    Text("A code for this request is showing on another of your devices.", style = MaterialTheme.typography.bodySmall)
                } else {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
                Text(
                    "If you don't recognise this, tap Deny and change your password: someone may know it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalRiskColors.current.high,
                )
            }
        },
        confirmButton = { Button(onClick = viewModel::hide) { Text("Hide") } },
        dismissButton = {
            TextButton(onClick = viewModel::deny, enabled = !state.busy) { Text("Deny", color = LocalRiskColors.current.high) }
        },
    )
}
