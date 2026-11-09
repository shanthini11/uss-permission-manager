package com.group8.permissionmanagerapp_uss.ui.appdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.history.HistoryRepository
import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.PermissionChoice
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import com.group8.permissionmanagerapp_uss.work.ReturnWatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A change the user chose in the sheet, waiting to be made in Android Settings. */
data class PendingChange(val category: PermissionCategory, val choice: PermissionChoice)

/** Outcome shown after a change (lo-fi "Permission Updated" / "Permission Not Updated"). */
sealed interface ChangeResult {
    val appLabel: String
    val category: PermissionCategory
    val choice: PermissionChoice

    data class Updated(
        override val appLabel: String,
        override val category: PermissionCategory,
        override val choice: PermissionChoice,
        val appliedInDemo: Boolean,
    ) : ChangeResult

    data class NotUpdated(
        override val appLabel: String,
        override val category: PermissionCategory,
        override val choice: PermissionChoice,
        /** What the app actually has now; null if Settings couldn't be opened. */
        val actual: AccessLevel?,
    ) : ChangeResult
}

/** Outcome of Revoke All (lo-fi "Permission Revoked" / "Permission Not Revoked"). */
data class RevokeAllResult(
    val appLabel: String,
    val revoked: List<PermissionCategory>,
    val stillAllowed: List<PermissionCategory>,
    val appliedInDemo: Boolean,
) {
    val complete: Boolean get() = stillAllowed.isEmpty()
}

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val app: InstalledApp? = null,
    val isDemo: Boolean = false,
    /** True when the app was uninstalled or the package name is unknown. */
    val notFound: Boolean = false,
    /** Non-null while the "Finish in Android Settings" guide is showing. */
    val guide: PendingChange? = null,
    val result: ChangeResult? = null,
    /** Warning step before Revoke All (users valued this in the lo-fi study). */
    val confirmRevokeAll: Boolean = false,
    /** Non-null while the Revoke All guide is showing: the categories to switch off. */
    val revokeAllGuide: List<PermissionCategory>? = null,
    val revokeAllResult: RevokeAllResult? = null,
)

/**
 * App detail + the permission-change flow:
 * sheet -> (Demo: apply directly) / (Real: guide -> Android Settings -> verify on return) -> result.
 *
 * What is being verified is kept in [SavedStateHandle], so the check still happens if Android
 * kills our process while the user is in the Settings app. Every confirmed change is logged to History.
 */
class AppDetailViewModel(
    private val packageName: String,
    private val appsRepository: AppsRepository,
    private val history: HistoryRepository,
    private val preferences: UserPreferencesRepository,
    private val returnWatcher: ReturnWatcher,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    /** Reload, e.g. when returning from the system Settings screen; verifies any pending change. */
    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val demo = preferences.preferences.first().demoMode
            val app = appsRepository.loadApp(packageName, demo)
            val verified = app?.let { verifyAwaitingChange(it) }
            val revokeVerified = app?.let { verifyAwaitingRevokeAll(it) }
            _uiState.update {
                it.copy(
                    isLoading = false,
                    app = app,
                    isDemo = demo,
                    notFound = app == null,
                    result = verified ?: it.result,
                    revokeAllResult = revokeVerified ?: it.revokeAllResult,
                )
            }
        }
    }

    // ---- Single permission change ----

    /** User tapped "Apply setting" in the sheet. */
    fun requestChange(category: PermissionCategory, choice: PermissionChoice) {
        val state = _uiState.value
        val app = state.app ?: return
        val current = app.accessFor(category) ?: return
        if (choice == PermissionChoice.current(current)) return

        if (state.isDemo) {
            appsRepository.applyDemoChange(packageName, category, choice)
            viewModelScope.launch { history.recordInAppChange(app, category, current.access, choice.target, isDemo = true) }
            _uiState.update {
                it.copy(result = ChangeResult.Updated(app.label, category, choice, appliedInDemo = true))
            }
            refresh()
        } else {
            _uiState.update { it.copy(guide = PendingChange(category, choice)) }
        }
    }

    /** User confirmed the guide; the screen is about to open Android Settings. */
    fun onGuideConfirmed() {
        val pending = _uiState.value.guide ?: return
        val from = _uiState.value.app?.accessFor(pending.category)?.access
        savedState[KEY_CATEGORY] = pending.category.name
        savedState[KEY_CHOICE] = pending.choice.name
        savedState[KEY_FROM] = from?.name
        returnWatcher.watch(packageName, pending.category, pending.choice)
        _uiState.update { it.copy(guide = null) }
    }

    fun onSettingsOpenFailed() {
        returnWatcher.stop()
        val category = savedState.get<String>(KEY_CATEGORY)
        val choice = savedState.get<String>(KEY_CHOICE)
        clearAwaiting()
        val app = _uiState.value.app ?: return
        if (category == null || choice == null) return
        _uiState.update {
            it.copy(
                result = ChangeResult.NotUpdated(
                    app.label, PermissionCategory.valueOf(category), PermissionChoice.valueOf(choice), actual = null,
                ),
            )
        }
    }

    fun dismissGuide() = _uiState.update { it.copy(guide = null) }

    fun dismissResult() = _uiState.update { it.copy(result = null) }

    /** "Try again" on the Not Updated result: show the guide again for the same change. */
    fun retry() {
        val result = _uiState.value.result as? ChangeResult.NotUpdated ?: return
        _uiState.update { it.copy(result = null, guide = PendingChange(result.category, result.choice)) }
    }

    private suspend fun verifyAwaitingChange(app: InstalledApp): ChangeResult? {
        val categoryName = savedState.get<String>(KEY_CATEGORY) ?: return null
        val choiceName = savedState.get<String>(KEY_CHOICE) ?: return null
        val fromName = savedState.get<String>(KEY_FROM)
        clearAwaiting()

        val category = runCatching { PermissionCategory.valueOf(categoryName) }.getOrNull() ?: return null
        val choice = runCatching { PermissionChoice.valueOf(choiceName) }.getOrNull() ?: return null
        val actual = app.accessFor(category)?.access ?: AccessLevel.NOT_ALLOWED

        return if (choice.matches(actual)) {
            val from = fromName?.let { runCatching { AccessLevel.valueOf(it) }.getOrNull() }
            history.recordInAppChange(app, category, from, actual, isDemo = false)
            ChangeResult.Updated(app.label, category, choice, appliedInDemo = false)
        } else {
            ChangeResult.NotUpdated(app.label, category, choice, actual)
        }
    }

    private fun clearAwaiting() {
        savedState.remove<String>(KEY_CATEGORY)
        savedState.remove<String>(KEY_CHOICE)
        savedState.remove<String>(KEY_FROM)
    }

    // ---- Revoke All ----

    fun requestRevokeAll() {
        if (_uiState.value.app?.grantedPermissions.isNullOrEmpty()) return
        _uiState.update { it.copy(confirmRevokeAll = true) }
    }

    fun cancelRevokeAll() = _uiState.update { it.copy(confirmRevokeAll = false, revokeAllGuide = null) }

    /** User accepted the warning. Demo: revoke now. Real: show which switches to turn off. */
    fun confirmRevokeAll() {
        val state = _uiState.value
        val app = state.app ?: return
        val granted = app.grantedPermissions
        if (state.isDemo) {
            granted.forEach { appsRepository.applyDemoChange(packageName, it.category, PermissionChoice.DENY) }
            viewModelScope.launch {
                granted.forEach { history.recordInAppChange(app, it.category, it.access, AccessLevel.NOT_ALLOWED, isDemo = true) }
            }
            _uiState.update {
                it.copy(
                    confirmRevokeAll = false,
                    revokeAllResult = RevokeAllResult(app.label, granted.map { p -> p.category }, emptyList(), appliedInDemo = true),
                )
            }
            refresh()
        } else {
            _uiState.update { it.copy(confirmRevokeAll = false, revokeAllGuide = granted.map { p -> p.category }) }
        }
    }

    /** The Revoke All guide was confirmed; the screen is about to open Android Settings. */
    fun onRevokeAllGuideConfirmed() {
        val categories = _uiState.value.revokeAllGuide ?: return
        savedState[KEY_REVOKE_ALL] = categories.joinToString(",") { it.name }
        returnWatcher.watchRevokeAll(packageName, categories)
        _uiState.update { it.copy(revokeAllGuide = null) }
    }

    fun onRevokeAllSettingsFailed() {
        returnWatcher.stop()
        savedState.remove<String>(KEY_REVOKE_ALL)
        val app = _uiState.value.app ?: return
        _uiState.update {
            it.copy(revokeAllResult = RevokeAllResult(app.label, emptyList(), app.grantedPermissions.map { p -> p.category }, false))
        }
    }

    fun dismissRevokeAllResult() = _uiState.update { it.copy(revokeAllResult = null) }

    fun retryRevokeAll() {
        val result = _uiState.value.revokeAllResult ?: return
        _uiState.update { it.copy(revokeAllResult = null, revokeAllGuide = result.stillAllowed) }
    }

    private suspend fun verifyAwaitingRevokeAll(app: InstalledApp): RevokeAllResult? {
        val raw = savedState.get<String>(KEY_REVOKE_ALL) ?: return null
        savedState.remove<String>(KEY_REVOKE_ALL)
        val requested = raw.split(",").mapNotNull { name -> PermissionCategory.entries.firstOrNull { it.name == name } }
        val (still, revoked) = requested.partition { app.accessFor(it)?.access?.isGranted == true }
        revoked.forEach { history.recordInAppChange(app, it, null, AccessLevel.NOT_ALLOWED, isDemo = false) }
        return RevokeAllResult(app.label, revoked, still, appliedInDemo = false)
    }

    companion object {
        private const val KEY_CATEGORY = "awaiting_category"
        private const val KEY_CHOICE = "awaiting_choice"
        private const val KEY_FROM = "awaiting_from"
        private const val KEY_REVOKE_ALL = "awaiting_revoke_all"

        fun factory(packageName: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                AppDetailViewModel(
                    packageName = packageName,
                    appsRepository = app.container.appsRepository,
                    history = app.container.history,
                    preferences = app.container.preferences,
                    returnWatcher = app.container.returnWatcher,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
