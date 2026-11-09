package com.group8.permissionmanagerapp_uss.data.devices

import android.content.Context
import com.group8.permissionmanagerapp_uss.data.security.EncryptedFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * This install's random device ID (not a hardware identifier, for privacy) and which account it
 * has been approved for. Stored encrypted; survives sign-out but not uninstall.
 */
class DeviceIdentity(context: Context) {
    @Serializable
    private data class Stored(val deviceId: String, val approvedForUid: String? = null)

    private val store = EncryptedFileStore(File(context.filesDir, "device.bin"), "pm_device_key")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var cached: Stored? = null

    suspend fun deviceId(): String = load().deviceId

    suspend fun isApprovedFor(uid: String): Boolean = load().approvedForUid == uid

    suspend fun setApprovedFor(uid: String?) = mutex.withLock {
        val updated = loadLocked().copy(approvedForUid = uid)
        save(updated)
    }

    private suspend fun load(): Stored = mutex.withLock { loadLocked() }

    private suspend fun loadLocked(): Stored {
        cached?.let { return it }
        val read = withContext(Dispatchers.IO) {
            store.read()?.let { runCatching { json.decodeFromString(Stored.serializer(), it.decodeToString()) }.getOrNull() }
        }
        return read ?: Stored(UUID.randomUUID().toString()).also { save(it) }
    }

    private suspend fun save(value: Stored) {
        withContext(Dispatchers.IO) { store.write(json.encodeToString(Stored.serializer(), value).toByteArray()) }
        cached = value
    }
}
