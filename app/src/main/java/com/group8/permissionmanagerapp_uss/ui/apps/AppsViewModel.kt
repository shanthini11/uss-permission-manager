package com.group8.permissionmanagerapp_uss.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.RiskLevel
import com.group8.permissionmanagerapp_uss.data.model.Sensitivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class AppFilter(val label: String, val matches: (InstalledApp) -> Boolean) {
    ALL("All", { true }),
    HIGH_RISK("High risk", { it.riskLevel == RiskLevel.HIGH }),
    BACKGROUND("Background access", { it.hasBackgroundAccess }),
    CRITICAL("Camera, mic or location", { app ->
        app.grantedPermissions.any { it.category.sensitivity == Sensitivity.CRITICAL }
    }),
}

data class AppsUiState(
    val isLoading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val totalApps: Int = 0,
    val query: String = "",
    val filter: AppFilter = AppFilter.ALL,
    val isDemo: Boolean = false,
)

class AppsViewModel(private val repository: AppsRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(AppFilter.ALL)

    val uiState: StateFlow<AppsUiState> =
        combine(repository.apps, query, filter) { snapshot, q, f ->
            val trimmed = q.trim()
            val visible = snapshot.apps.filter { app ->
                f.matches(app) && (trimmed.isEmpty() ||
                    app.label.contains(trimmed, ignoreCase = true) ||
                    app.packageName.contains(trimmed, ignoreCase = true))
            }
            AppsUiState(
                isLoading = false,
                apps = visible,
                totalApps = snapshot.apps.size,
                query = q,
                filter = f,
                isDemo = snapshot.isDemo,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppsUiState())

    fun onQueryChange(value: String) {
        // Cap length: search input is only ever matched locally, but keep it bounded.
        query.value = value.take(MAX_QUERY_LENGTH)
    }

    fun onFilterChange(value: AppFilter) {
        filter.value = value
    }

    /** Re-read permissions, e.g. after the user returns from system Settings. */
    fun refresh() = repository.refresh()

    companion object {
        private const val MAX_QUERY_LENGTH = 60

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                AppsViewModel(app.container.appsRepository)
            }
        }
    }
}
