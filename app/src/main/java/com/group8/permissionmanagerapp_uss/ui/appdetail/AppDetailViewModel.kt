package com.group8.permissionmanagerapp_uss.ui.appdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.AppsRepository
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppDetailUiState(
    val isLoading: Boolean = true,
    val app: InstalledApp? = null,
    val isDemo: Boolean = false,
    /** True when the app was uninstalled or the package name is unknown. */
    val notFound: Boolean = false,
)

class AppDetailViewModel(
    private val packageName: String,
    private val appsRepository: AppsRepository,
    private val preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppDetailUiState())
    val uiState: StateFlow<AppDetailUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Reload, e.g. when returning from the system Settings screen. */
    fun refresh() {
        viewModelScope.launch {
            val demo = preferences.preferences.first().demoMode
            val app = appsRepository.loadApp(packageName, demo)
            _uiState.update {
                AppDetailUiState(isLoading = false, app = app, isDemo = demo, notFound = app == null)
            }
        }
    }

    companion object {
        fun factory(packageName: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                AppDetailViewModel(packageName, app.container.appsRepository, app.container.preferences)
            }
        }
    }
}
