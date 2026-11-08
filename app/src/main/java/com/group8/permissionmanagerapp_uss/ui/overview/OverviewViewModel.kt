package com.group8.permissionmanagerapp_uss.ui.overview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.AppsSnapshot
import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.AppPermission
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.RiskLevel
import com.group8.permissionmanagerapp_uss.data.model.Sensitivity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** An app together with its access to one category. */
data class AppAccess(val app: InstalledApp, val permission: AppPermission)

/** One row of the Permission Categories page. */
data class CategorySummary(
    val category: PermissionCategory,
    /** Every app that requested this category; allowed first, then by risk. */
    val apps: List<AppAccess>,
) {
    val requestedCount: Int get() = apps.size
    val grantedCount: Int get() = apps.count { it.permission.access.isGranted }
    val backgroundCount: Int get() = apps.count { it.permission.isBackground }
    val limitedCount: Int get() = apps.count { it.permission.access == AccessLevel.LIMITED }

    /** One-line insight shown on the category card (lo-fi: "4 apps have background access"). */
    val highlight: String
        get() = when {
            backgroundCount > 0 -> "${plural(backgroundCount, "app")} can use it in the background"
            category == PermissionCategory.FILES_PHOTOS && grantedCount - limitedCount > 0 ->
                "${plural(grantedCount - limitedCount, "app")} can see all your photos and files"
            limitedCount > 0 -> "Limited access only for ${plural(limitedCount, "app")}"
            grantedCount == 0 -> "No apps allowed. Requested by ${plural(requestedCount, "app")}"
            else -> "$grantedCount of ${plural(requestedCount, "app")} allowed, foreground only"
        }
}

data class OverviewUiState(
    val isLoading: Boolean = true,
    val isDemo: Boolean = false,
    val appCount: Int = 0,
    val categories: List<CategorySummary> = emptyList(),
    val totalAllowed: Int = 0,
    val criticalAllowed: Int = 0,
    val sensitiveAllowed: Int = 0,
    /** Share of critical/sensitive requests that are blocked or limited, 0..100. */
    val protectionPercent: Int = 0,
    val backgroundAccess: List<AppAccess> = emptyList(),
    /** Apps worth reviewing first: high-risk apps the user installed (falls back to medium). */
    val attentionApps: List<InstalledApp> = emptyList(),
) {
    val backgroundAppCount: Int get() = backgroundAccess.map { it.app.packageName }.distinct().size
}

/** Derives dashboard, category and background-risk summaries from the current app snapshot. */
class OverviewViewModel(private val repository: AppsRepository) : ViewModel() {

    val uiState: StateFlow<OverviewUiState> = repository.apps
        .map(::summarize)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewUiState())

    fun refresh() = repository.refresh()

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                OverviewViewModel(app.container.appsRepository)
            }
        }

        internal fun summarize(snapshot: AppsSnapshot): OverviewUiState {
            val apps = snapshot.apps
            val allAccess = apps.flatMap { app -> app.permissions.map { AppAccess(app, it) } }

            val categories = allAccess
                .groupBy { it.permission.category }
                .map { (category, list) ->
                    CategorySummary(
                        category = category,
                        apps = list.sortedWith(
                            compareByDescending<AppAccess> { it.permission.access.isGranted }
                                .thenByDescending { it.permission.isBackground }
                                .thenByDescending { it.app.riskScore }
                        ),
                    )
                }
                .sortedWith(
                    compareBy<CategorySummary> { it.category.sensitivity.ordinal }
                        .thenByDescending { it.grantedCount }
                )

            val sensitiveRequests = allAccess.filter { it.permission.category.sensitivity != Sensitivity.LOW }
            val protectedCount = sensitiveRequests.count {
                !it.permission.access.isGranted || it.permission.access == AccessLevel.LIMITED
            }
            val granted = allAccess.filter { it.permission.access.isGranted }

            val userApps = apps.filterNot { it.isSystemApp }
            val attention = userApps.filter { it.riskLevel == RiskLevel.HIGH }
                .ifEmpty { userApps.filter { it.riskLevel == RiskLevel.MEDIUM } }
                .take(5)

            return OverviewUiState(
                isLoading = false,
                isDemo = snapshot.isDemo,
                appCount = apps.size,
                categories = categories,
                totalAllowed = granted.size,
                criticalAllowed = granted.count { it.permission.category.sensitivity == Sensitivity.CRITICAL },
                sensitiveAllowed = granted.count { it.permission.category.sensitivity == Sensitivity.SENSITIVE },
                protectionPercent = if (sensitiveRequests.isEmpty()) 100
                else protectedCount * 100 / sensitiveRequests.size,
                backgroundAccess = allAccess
                    .filter { it.permission.isBackground }
                    .sortedWith(compareBy<AppAccess> { it.app.isSystemApp }.thenBy { it.permission.category.sensitivity.ordinal }),
                attentionApps = attention,
            )
        }
    }
}

internal fun plural(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"
