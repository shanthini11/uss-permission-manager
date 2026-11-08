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
import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.PermissionChoice
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
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

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val app: InstalledApp? = null,
    val isDemo: Boolean = false,
    /** True when the app was uninstalled or the package name is unknown. */
    val notFound: Boolean = false,
    /** Non-null while the "Finish in Android Settings" guide is showing. */
    val guide: PendingChange? = null,
    val result: ChangeResult? = null,
)

/**
 * App detail + the permission-change flow:
 * sheet -> (Demo: apply directly) / (Real: guide -> Android Settings -> verify on return) -> result.
 *
 * The change being verified is kept in [SavedStateHandle], so the check still happens if Android
 * kills our process while the user is in the Settings app.
 */
class AppDetailViewModel(
    private val packageName: String,
    private val appsRepository: AppsRepository,
    private val preferences: UserPreferencesRepository,
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
            _uiState.update {
                it.copy(
                    isLoading = false,
                    app = app,
                    isDemo = demo,
                    notFound = app == null,
                    result = verified ?: it.result,
                )
            }
        }
    }

    /** User tapped "Apply setting" in the sheet. */
    fun requestChange(category: PermissionCategory, choice: PermissionChoice) {
        val state = _uiState.value
        val app = state.app ?: return
        val current = app.accessFor(category) ?: return
        if (choice == PermissionChoice.current(current)) return

        if (state.isDemo) {
            appsRepository.applyDemoChange(packageName, category, choice)
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
        savedState[KEY_CATEGORY] = pending.category.name
        savedState[KEY_CHOICE] = pending.choice.name
        _uiState.update { it.copy(guide = null) }
    }

    fun onSettingsOpenFailed() {
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

    private fun verifyAwaitingChange(app: InstalledApp): ChangeResult? {
        val categoryName = savedState.get<String>(KEY_CATEGORY) ?: return null
        val choiceName = savedState.get<String>(KEY_CHOICE) ?: return null
        clearAwaiting()

        val category = runCatching { PermissionCategory.valueOf(categoryName) }.getOrNull() ?: return null
        val choice = runCatching { PermissionChoice.valueOf(choiceName) }.getOrNull() ?: return null
        val actual = app.accessFor(category)?.access ?: AccessLevel.NOT_ALLOWED

        return if (choice.matches(actual)) {
            ChangeResult.Updated(app.label, category, choice, appliedInDemo = false)
        } else {
            ChangeResult.NotUpdated(app.label, category, choice, actual)
        }
    }

    private fun clearAwaiting() {
        savedState.remove<String>(KEY_CATEGORY)
        savedState.remove<String>(KEY_CHOICE)
    }

    companion object {
        private const val KEY_CATEGORY = "awaiting_category"
        private const val KEY_CHOICE = "awaiting_choice"

        fun factory(packageName: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                AppDetailViewModel(
                    packageName = packageName,
                    appsRepository = app.container.appsRepository,
                    preferences = app.container.preferences,
                    savedState = createSavedStateHandle(),
                )
            }
        }
    }
}
