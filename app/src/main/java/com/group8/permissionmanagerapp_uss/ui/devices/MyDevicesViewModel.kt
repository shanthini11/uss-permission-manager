package com.group8.permissionmanagerapp_uss.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.devices.DeviceException
import com.group8.permissionmanagerapp_uss.data.devices.DeviceInfo
import com.group8.permissionmanagerapp_uss.data.devices.DeviceRepository
import com.group8.permissionmanagerapp_uss.data.devices.JoinRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** #7 confirm, #8 removed, #9 error. */
sealed interface RemoveStep {
    data object Idle : RemoveStep
    data class Confirm(val devices: List<DeviceInfo>) : RemoveStep
    data object Working : RemoveStep
    data class Removed(val count: Int) : RemoveStep
    data class Error(val message: String, val devices: List<DeviceInfo>) : RemoveStep
}

data class DevicesListState(val loading: Boolean = true, val devices: List<DeviceInfo> = emptyList())

class MyDevicesViewModel(private val repo: DeviceRepository) : ViewModel() {

    val list: StateFlow<DevicesListState> = repo.observeDevices()
        .map { DevicesListState(loading = false, devices = it) }
        .catch { emit(DevicesListState(loading = false)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DevicesListState())

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    private val _selecting = MutableStateFlow(false)
    val selecting: StateFlow<Boolean> = _selecting.asStateFlow()

    private val _remove = MutableStateFlow<RemoveStep>(RemoveStep.Idle)
    val remove: StateFlow<RemoveStep> = _remove.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun setSelecting(on: Boolean) {
        _selecting.value = on
        if (!on) _selected.value = emptySet()
    }

    fun toggle(device: DeviceInfo) {
        if (device.isThisDevice) return
        _selected.update { if (device.id in it) it - device.id else it + device.id }
    }

    fun askRemove(devices: List<DeviceInfo>) {
        val others = devices.filterNot { it.isThisDevice }
        if (others.isNotEmpty()) _remove.value = RemoveStep.Confirm(others)
    }

    fun confirmRemove() {
        val targets = when (val s = _remove.value) {
            is RemoveStep.Confirm -> s.devices
            is RemoveStep.Error -> s.devices
            else -> return
        }
        _remove.value = RemoveStep.Working
        viewModelScope.launch {
            _remove.value = try {
                repo.removeDevices(targets.map { it.id })
                setSelecting(false)
                RemoveStep.Removed(targets.size)
            } catch (e: DeviceException) {
                RemoveStep.Error(e.message ?: "Couldn't remove the device.", targets)
            }
        }
    }

    fun dismissRemove() {
        _remove.value = RemoveStep.Idle
    }

    fun setShareSummary(enabled: Boolean) {
        viewModelScope.launch {
            try {
                repo.setShareSummary(enabled)
            } catch (e: DeviceException) {
                _message.value = e.message
            }
        }
    }

    fun refreshThisDevice() {
        viewModelScope.launch {
            try {
                repo.syncThisDevice()
                _message.value = "Summary updated"
            } catch (e: DeviceException) {
                _message.value = e.message
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MyDevicesViewModel((this[APPLICATION_KEY] as PermissionManagerApp).container.devices) }
        }
    }
}

/** On an approved device: shows a code when another device asks to join (refinement #4, other side). */
data class ApprovalState(val request: JoinRequest? = null, val code: String? = null, val busy: Boolean = false, val error: String? = null)

class JoinApprovalViewModel(private val repo: DeviceRepository) : ViewModel() {
    private val _state = MutableStateFlow(ApprovalState())
    val state: StateFlow<ApprovalState> = _state.asStateFlow()

    /** Codes this phone issued, kept in memory only so they can be shown again until they expire. */
    private val issued = mutableMapOf<String, Pair<String, Long>>()
    private val hidden = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            repo.observeIncomingRequests().catch { }.collect { requests ->
                val next = requests.firstOrNull { it.id !in hidden }
                if (next == null) {
                    _state.value = ApprovalState()
                } else if (next.id != _state.value.request?.id || !next.hasCode) {
                    show(next)
                } else {
                    _state.update { it.copy(request = next) }
                }
            }
        }
    }

    private fun show(request: JoinRequest) {
        _state.value = ApprovalState(request = request, code = validIssued(request.id))
        if (_state.value.code != null) return
        viewModelScope.launch {
            try {
                val code = repo.issueCode(request.id)
                if (code != null) issued[request.id] = code to (System.currentTimeMillis() + com.group8.permissionmanagerapp_uss.data.devices.DeviceCode.TTL_MS)
                _state.update { if (it.request?.id == request.id) it.copy(code = code ?: validIssued(request.id)) else it }
            } catch (e: DeviceException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    private fun validIssued(id: String): String? = issued[id]?.takeIf { it.second > System.currentTimeMillis() }?.first

    fun deny() {
        val request = _state.value.request ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repo.denyRequest(request.id)
                hidden += request.id
                _state.value = ApprovalState()
            } catch (e: DeviceException) {
                _state.update { it.copy(busy = false, error = e.message) }
            }
        }
    }

    /** "Hide": stop showing this request on this phone (it stays pending for other devices). */
    fun hide() {
        _state.value.request?.let { hidden += it.id }
        _state.value = ApprovalState()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { JoinApprovalViewModel((this[APPLICATION_KEY] as PermissionManagerApp).container.devices) }
        }
    }
}
