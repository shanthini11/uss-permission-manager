package com.group8.permissionmanagerapp_uss.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.backup.BackupContents
import com.group8.permissionmanagerapp_uss.data.backup.BackupException
import com.group8.permissionmanagerapp_uss.data.backup.BackupRepository
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Arrays

/** Each step of the lo-fi backup flow (refinements #11–#18). */
sealed interface BackupStep {
    data object Idle : BackupStep
    /** #12 Create Backup: choose a password. */
    data object ChoosePassword : BackupStep
    data object Working : BackupStep
    /** #13 Backup Created. */
    data object Created : BackupStep
    /** #14 Backup Creation Error. */
    data class CreateError(val message: String) : BackupStep
    /** #15 Import Backup: file picked, enter its password. */
    data class EnterPassword(val error: String? = null) : BackupStep
    /** #16 Review Backup. */
    data class Review(val contents: BackupContents) : BackupStep
    /** #17 Backup Restored. */
    data class Restored(val added: Int) : BackupStep
    /** #18 Backup Restoration Error. */
    data class RestoreError(val message: String) : BackupStep
}

class BackupViewModel(
    private val backup: BackupRepository,
    preferences: UserPreferencesRepository,
) : ViewModel() {

    private val _step = MutableStateFlow<BackupStep>(BackupStep.Idle)
    val step: StateFlow<BackupStep> = _step.asStateFlow()

    val lastBackupAt: StateFlow<Long?> = preferences.preferences.map { it.lastBackupAt }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Kept only between choosing a password and picking the save location; wiped right after. */
    private var exportPassword: CharArray? = null
    /** The picked backup file, kept only until restore finishes or is cancelled. */
    private var importBytes: ByteArray? = null

    fun startExport() {
        _step.value = BackupStep.ChoosePassword
    }

    /** Password chosen; the screen now opens the "save file" picker. */
    fun onExportPasswordChosen(password: CharArray) {
        wipeExportPassword()
        exportPassword = password
        _step.value = BackupStep.Working
    }

    fun onExportLocationPicked(uri: Uri?) {
        val password = exportPassword
        if (uri == null || password == null) {
            cancel()
            return
        }
        _step.value = BackupStep.Working
        viewModelScope.launch {
            _step.value = try {
                backup.export(uri, password)
                BackupStep.Created
            } catch (e: BackupException) {
                BackupStep.CreateError(e.message ?: "The backup couldn't be created.")
            } catch (_: Exception) {
                BackupStep.CreateError("The backup couldn't be created. Check there's enough space and try again.")
            } finally {
                wipeExportPassword()
            }
        }
    }

    fun onImportFilePicked(uri: Uri?) {
        if (uri == null) return
        _step.value = BackupStep.Working
        viewModelScope.launch {
            _step.value = try {
                importBytes = backup.readFile(uri)
                BackupStep.EnterPassword()
            } catch (e: BackupException) {
                BackupStep.RestoreError(e.message ?: "That file couldn't be opened.")
            } catch (_: Exception) {
                BackupStep.RestoreError("That file couldn't be opened.")
            }
        }
    }

    fun unlock(password: CharArray) {
        val bytes = importBytes ?: return cancel()
        _step.value = BackupStep.Working
        viewModelScope.launch {
            _step.value = try {
                BackupStep.Review(backup.open(bytes, password))
            } catch (e: BackupException) {
                BackupStep.EnterPassword(e.message)
            } finally {
                Arrays.fill(password, '\u0000')
            }
        }
    }

    fun confirmRestore() {
        val review = _step.value as? BackupStep.Review ?: return
        _step.value = BackupStep.Working
        viewModelScope.launch {
            _step.value = try {
                BackupStep.Restored(backup.restore(review.contents))
            } catch (_: Exception) {
                // Nothing partial is left behind: history is written in a single encrypted save.
                BackupStep.RestoreError("The backup couldn't be restored. Your existing data wasn't changed.")
            } finally {
                importBytes = null
            }
        }
    }

    fun cancel() {
        wipeExportPassword()
        importBytes = null
        _step.value = BackupStep.Idle
    }

    private fun wipeExportPassword() {
        exportPassword?.let { Arrays.fill(it, '\u0000') }
        exportPassword = null
    }

    override fun onCleared() {
        wipeExportPassword()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PermissionManagerApp
                BackupViewModel(app.container.backup, app.container.preferences)
            }
        }
    }
}
