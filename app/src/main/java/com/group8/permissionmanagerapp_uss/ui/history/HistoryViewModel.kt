package com.group8.permissionmanagerapp_uss.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.history.HistoryRepository
import com.group8.permissionmanagerapp_uss.data.history.PermissionEvent
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import com.group8.permissionmanagerapp_uss.ui.common.TimeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LogFilter(val label: String, val matches: (PermissionEvent) -> Boolean) {
    ALL("All", { true }),
    ALLOWED("Allowed", { it.isGrant }),
    DENIED("Denied", { !it.isGrant }),
}

data class DayBar(val label: String, val allowed: Int, val denied: Int)
data class ShareSlice(val label: String, val percent: Int)

data class HistoryUiState(
    val isLoading: Boolean = true,
    val isDemo: Boolean = false,
    val historyEnabled: Boolean = true,
    val logs: List<PermissionEvent> = emptyList(),
    val filter: LogFilter = LogFilter.ALL,
    val week: List<DayBar> = emptyList(),
    val weekAllowed: Int = 0,
    val weekDenied: Int = 0,
    /** Share of currently allowed access by permission type (lo-fi "Sensor Share"). */
    val share: List<ShareSlice> = emptyList(),
    val shareTypes: Int = 0,
)

/** Lo-fi "Permission Tracker / History": a 30-day record of permission changes (R11, SR8). */
class HistoryViewModel(
    private val history: HistoryRepository,
    appsRepository: AppsRepository,
    preferences: UserPreferencesRepository,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val filter = MutableStateFlow(LogFilter.ALL)

    val uiState: StateFlow<HistoryUiState> =
        combine(history.events, appsRepository.apps, preferences.preferences, filter) { events, snapshot, prefs, f ->
            val changes = events.filter { it.isDemo == prefs.demoMode && !it.isReminder }
            val t = now()
            val today = TimeFormat.startOfDay(t)
            val week = (6 downTo 0).map { daysAgo ->
                val start = today - daysAgo * TimeFormat.DAY
                val dayEvents = changes.filter { it.timestamp >= start && it.timestamp < start + TimeFormat.DAY }
                DayBar(
                    label = if (daysAgo == 0) "Today" else TimeFormat.weekdayShort(start),
                    allowed = dayEvents.count { it.isGrant },
                    denied = dayEvents.count { !it.isGrant },
                )
            }

            val granted = snapshot.apps.flatMap { it.grantedPermissions }.groupingBy { it.category.title }.eachCount()
            val total = granted.values.sum().coerceAtLeast(1)
            val sorted = granted.entries.sortedByDescending { it.value }
            val top = sorted.take(3).map { ShareSlice(it.key, it.value * 100 / total) }
            val rest = sorted.drop(3).sumOf { it.value }
            val share = if (rest > 0) top + ShareSlice("Other", 100 - top.sumOf { it.percent }) else top

            HistoryUiState(
                isLoading = false,
                isDemo = prefs.demoMode,
                historyEnabled = prefs.historyEnabled,
                logs = changes.filter(f.matches),
                filter = f,
                week = week,
                weekAllowed = week.sumOf { it.allowed },
                weekDenied = week.sumOf { it.denied },
                share = share,
                shareTypes = granted.size,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    init {
        viewModelScope.launch { history.ensureLoaded() }
    }

    fun onFilterChange(value: LogFilter) {
        filter.value = value
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                HistoryViewModel(app.container.history, app.container.appsRepository, app.container.preferences)
            }
        }
    }
}
