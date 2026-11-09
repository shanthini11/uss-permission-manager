package com.group8.permissionmanagerapp_uss.ui.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group8.permissionmanagerapp_uss.PermissionManagerApp
import com.group8.permissionmanagerapp_uss.data.auth.OtpService
import com.group8.permissionmanagerapp_uss.data.devices.DeviceException
import com.group8.permissionmanagerapp_uss.data.devices.DeviceIdentity
import com.group8.permissionmanagerapp_uss.data.devices.DeviceInfo
import com.group8.permissionmanagerapp_uss.data.devices.DeviceRepository
import com.group8.permissionmanagerapp_uss.data.devices.DeviceStatus
import com.group8.permissionmanagerapp_uss.data.devices.JoinRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface DeviceGate {
    data object Checking : DeviceGate
    data object Approved : DeviceGate
    /** #3 Add New Device. */
    data class Setup(val otherDevices: List<DeviceInfo>, val defaultName: String) : DeviceGate
    /** #4 Verify New Device (and #6 error state via [DeviceForm.error] / denied request). */
    data class Pending(val request: JoinRequest, val otherDevices: List<DeviceInfo>) : DeviceGate
    /** #5 New Device Added. */
    data object Added : DeviceGate
    data object Removed : DeviceGate
    data class Offline(val message: String) : DeviceGate
}

data class DeviceForm(
    val busy: Boolean = false,
    val error: String? = null,
    val emailCodeSent: Boolean = false,
)

/** Decides whether this phone may enter the app for the signed-in account (refinements #2–#6). */
class DeviceGateViewModel(
    private val devices: DeviceRepository,
    private val identity: DeviceIdentity,
    private val otp: OtpService,
    private val currentUid: () -> String?,
) : ViewModel() {

    private val _gate = MutableStateFlow<DeviceGate>(DeviceGate.Checking)
    val gate: StateFlow<DeviceGate> = _gate.asStateFlow()

    private val _form = MutableStateFlow(DeviceForm())
    val form: StateFlow<DeviceForm> = _form.asStateFlow()

    private var requestWatch: Job? = null

    init {
        check()
    }

    fun check() {
        _gate.value = DeviceGate.Checking
        viewModelScope.launch {
            try {
                apply(devices.checkStatus())
            } catch (e: DeviceException) {
                // Offline: a phone that was already approved keeps working locally.
                val uid = currentUid()
                _gate.value = if (uid != null && identity.isApprovedFor(uid)) DeviceGate.Approved
                else DeviceGate.Offline(e.message ?: "No connection.")
            }
        }
    }

    private suspend fun apply(status: DeviceStatus) {
        when (status) {
            DeviceStatus.Approved -> {
                _gate.value = DeviceGate.Approved
                runCatching { devices.syncThisDevice() }
            }
            is DeviceStatus.NeedsSetup ->
                if (status.first) {
                    // First device on the account: nothing to verify against.
                    devices.registerFirstDevice(devices.defaultName())
                    _gate.value = DeviceGate.Approved
                } else {
                    _gate.value = DeviceGate.Setup(status.otherDevices, devices.defaultName())
                }
            is DeviceStatus.Pending -> showPending(status.request, status.otherDevices)
            DeviceStatus.Removed -> _gate.value = DeviceGate.Removed
        }
    }

    private suspend fun showPending(request: JoinRequest, others: List<DeviceInfo>) {
        _gate.value = DeviceGate.Pending(request, others)
        requestWatch?.cancel()
        val id = devices.thisDeviceId()
        requestWatch = viewModelScope.launch {
            devices.observeOwnRequest(id).collect { live ->
                val current = _gate.value as? DeviceGate.Pending ?: return@collect
                if (live != null) _gate.value = current.copy(request = live)
            }
        }
    }

    /** #3 Continue. */
    fun requestToJoin(name: String, importPreferences: Boolean) = run {
        devices.requestToJoin(name, importPreferences)
        apply(devices.checkStatus())
    }

    /** #4 Verify with the code shown on another device. */
    fun verifyCode(code: String) = run {
        if (code.length != 6) throw DeviceException("Enter the 6-digit code.")
        when (val r = devices.verifyJoinCode(code)) {
            DeviceRepository.VerifyResult.Ok -> added()
            is DeviceRepository.VerifyResult.Error -> throw DeviceException(r.message)
        }
    }

    /** #6 "Request a new code". */
    fun requestNewCode() = run {
        devices.resetRequest()
        _form.update { it.copy(error = null) }
    }

    fun sendEmailCode() = run {
        when (val r = otp.sendCode()) {
            OtpService.Result.Ok -> _form.update { it.copy(emailCodeSent = true) }
            is OtpService.Result.Error -> throw DeviceException(r.message)
        }
    }

    fun verifyEmailCode(code: String) = run {
        when (val r = otp.verifyCode(code)) {
            OtpService.Result.Ok -> {
                devices.approveAfterEmailCode()
                added()
            }
            is OtpService.Result.Error -> throw DeviceException(r.message)
        }
    }

    fun cancelRequest() = run {
        devices.cancelRequest()
        requestWatch?.cancel()
        apply(devices.checkStatus())
    }

    /** "Removed" screen: start adding this phone again. */
    fun addAgain() = run { apply(devices.checkStatus()) }

    fun continueAfterAdded() {
        _gate.value = DeviceGate.Approved
        viewModelScope.launch { runCatching { devices.syncThisDevice() } }
    }

    private fun added() {
        requestWatch?.cancel()
        _form.value = DeviceForm()
        _gate.value = DeviceGate.Added
    }

    private fun run(block: suspend () -> Unit) {
        if (_form.value.busy) return
        _form.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                _form.update { it.copy(busy = false) }
            } catch (e: DeviceException) {
                _form.update { it.copy(busy = false, error = e.message) }
            } catch (_: Exception) {
                _form.update { it.copy(busy = false, error = "Something went wrong. Please try again.") }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as PermissionManagerApp).container
                DeviceGateViewModel(c.devices, c.deviceIdentity, c.otp, currentUid = { c.auth.currentUser?.uid })
            }
        }
    }
}
