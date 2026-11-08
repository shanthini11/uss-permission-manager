package com.group8.permissionmanagerapp_uss.ui.history

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.history.HistoryRepository
import com.group8.permissionmanagerapp_uss.data.history.PermissionEvent
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import com.group8.permissionmanagerapp_uss.ui.common.TimeFormat
import com.group8.permissionmanagerapp_uss.ui.components.AccessChip
import com.group8.permissionmanagerapp_uss.ui.components.AppAvatar
import com.group8.permissionmanagerapp_uss.ui.components.DemoBanner
import com.group8.permissionmanagerapp_uss.ui.components.EmptyState
import com.group8.permissionmanagerapp_uss.ui.components.SectionCard
import com.group8.permissionmanagerapp_uss.work.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class AlertFilter(val label: String, val matches: (PermissionEvent) -> Boolean) {
    ALL("All", { true }),
    ALLOWED("Allowed", { !it.isReminder && it.isGrant }),
    DENIED("Denied", { !it.isReminder && !it.isGrant }),
    SYSTEM("Reminders", { it.isReminder }),
}

data class NotificationsUiState(
    val isLoading: Boolean = true,
    val isDemo: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val alerts: List<PermissionEvent> = emptyList(),
    val filter: AlertFilter = AlertFilter.ALL,
)

/** Recent alerts you haven't dismissed yet (last 7 days). History keeps the full record. */
class NotificationsViewModel(
    private val history: HistoryRepository,
    preferences: UserPreferencesRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val filter = MutableStateFlow(AlertFilter.ALL)
    private var isDemo = false

    val uiState: StateFlow<NotificationsUiState> =
        combine(history.events, preferences.preferences, filter) { events, prefs, f ->
            isDemo = prefs.demoMode
            val cutoff = now() - 7 * TimeFormat.DAY
            NotificationsUiState(
                isLoading = false,
                isDemo = prefs.demoMode,
                notificationsEnabled = prefs.notificationsEnabled,
                alerts = events.filter { it.isDemo == prefs.demoMode && !it.dismissed && it.timestamp >= cutoff && f.matches(it) },
                filter = f,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotificationsUiState())

    init {
        viewModelScope.launch { history.ensureLoaded() }
    }

    fun onFilterChange(value: AlertFilter) {
        filter.value = value
    }

    fun dismiss(id: String) = viewModelScope.launch { history.dismiss(id) }

    fun dismissAll() = viewModelScope.launch { history.dismissAll(isDemo) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                NotificationsViewModel(app.container.history, app.container.preferences)
            }
        }
    }
}

/** Lo-fi "Notifications": recent permission alerts, each with View permission / Dismiss. */
@Composable
fun NotificationsScreen(
    onViewPermission: (packageName: String, category: PermissionCategory) -> Unit,
    onOpenHistory: () -> Unit,
    onStartReview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationsViewModel = viewModel(factory = NotificationsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val now = System.currentTimeMillis()

    var canPost by remember { mutableStateOf(Notifier(context).canPost()) }
    LifecycleResumeEffect(Unit) {
        canPost = Notifier(context).canPost()
        onPauseOrDispose { }
    }
    val requestPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canPost = granted
    }

    if (state.isLoading) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.isDemo) item { DemoBanner() }

        item {
            SectionCard {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        if (state.alerts.isNotEmpty()) TextButton(onClick = { viewModel.dismissAll() }) { Text("Dismiss all") }
                    }
                    // Explains the difference from History (lo-fi finding UP2).
                    Text(
                        "New permission changes and reminders from the last 7 days. Dismiss them once you've checked. " +
                            "Your full record stays in Permission History.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (!canPost && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && state.notificationsEnabled) {
            item {
                SectionCard {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Get alerts while the app is closed", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("We'll tell you when an app's permissions change.", style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }) { Text("Turn on") }
                    }
                }
            }
        }

        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertFilter.entries.forEach { f ->
                    FilterChip(selected = state.filter == f, onClick = { viewModel.onFilterChange(f) }, label = { Text(f.label) })
                }
            }
        }

        if (state.alerts.isEmpty()) {
            item { EmptyState("You're all caught up", "No new permission alerts. Past changes are in Permission History.") }
        }

        state.alerts.groupBy { TimeFormat.dayGroup(it.timestamp, now) }.forEach { (group, alerts) ->
            item(key = "n-$group") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(group.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text("${alerts.size} ${if (alerts.size == 1) "alert" else "alerts"}", style = MaterialTheme.typography.labelSmall)
                }
            }
            items(alerts, key = { it.id }) { alert ->
                AlertCard(
                    alert = alert,
                    onView = {
                        if (alert.isReminder) onStartReview()
                        else alert.category?.let { onViewPermission(alert.packageName, it) }
                    },
                    onDismiss = { viewModel.dismiss(alert.id) },
                )
            }
        }

        item {
            SectionCard {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) {
                        Text("View Permission History", modifier = Modifier.weight(1f))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlertCard(alert: PermissionEvent, onView: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    SectionCard {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (alert.isReminder) {
                    Icon(Icons.Outlined.EventRepeat, contentDescription = null)
                } else {
                    AppAvatar(alert.packageName, alert.appLabel, size = 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (alert.isReminder) "Weekly permission review" else alert.appLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        when {
                            alert.isReminder -> "It's been a week. Take a minute to check which apps can access your data."
                            alert.isGrant && alert.from?.isGranted != true -> "was granted access to:"
                            !alert.isGrant -> "lost access to:"
                            else -> "access changed:"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(TimeFormat.time(context, alert.timestamp), style = MaterialTheme.typography.labelSmall)
            }
            if (!alert.isReminder && alert.category != null && alert.to != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${alert.category.title} — ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    AccessChip(alert.to)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onView, modifier = Modifier.weight(1f)) {
                    Text(if (alert.isReminder) "Start review" else "View permission")
                }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Dismiss") }
            }
        }
    }
}
