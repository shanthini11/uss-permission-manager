package com.group8.permissionmanagerapp_uss.data.backup

import android.content.Context
import android.net.Uri
import android.os.Build
import com.group8.permissionmanagerapp_uss.AppInfo
import com.group8.permissionmanagerapp_uss.data.history.HistoryRepository
import com.group8.permissionmanagerapp_uss.data.history.PermissionEvent
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

/** What a backup contains: permission history and preferences (lo-fi "What is included"). */
@Serializable
data class BackupContents(
    val format: Int = FORMAT,
    val createdAt: Long,
    val deviceName: String,
    val appVersion: String,
    val events: List<PermissionEvent>,
    val notificationsEnabled: Boolean,
    val historyEnabled: Boolean,
    val periodicReview: Boolean,
) {
    companion object {
        const val FORMAT = 1
    }
}

class BackupException(message: String) : Exception(message)

/**
 * Creates and restores encrypted local backup files (refinements #11–#18).
 * Files go wherever the user picks with Android's file picker; nothing is uploaded.
 */
class BackupRepository(
    context: Context,
    private val history: HistoryRepository,
    private val preferences: UserPreferencesRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val resolver = context.applicationContext.contentResolver
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(uri: Uri, password: CharArray) {
        val prefs = preferences.preferences.first()
        val contents = BackupContents(
            createdAt = now(),
            deviceName = deviceName(),
            appVersion = AppInfo.VERSION,
            events = history.exportEvents(),
            notificationsEnabled = prefs.notificationsEnabled,
            historyEnabled = prefs.historyEnabled,
            periodicReview = prefs.periodicReview,
        )
        val bytes = json.encodeToString(BackupContents.serializer(), contents).toByteArray()
        val encrypted = withContext(Dispatchers.Default) { BackupCrypto.encrypt(bytes, password) }
        withContext(Dispatchers.IO) {
            val out = resolver.openOutputStream(uri, "wt") ?: throw BackupException("Couldn't write to that location.")
            out.use { it.write(encrypted) }
        }
        preferences.setLastBackupAt(contents.createdAt)
    }

    /** Reads a picked file, refusing anything too large to be one of our backups. */
    suspend fun readFile(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val input = resolver.openInputStream(uri) ?: throw BackupException("Couldn't open that file.")
        input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val n = stream.read(buffer)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw BackupException("That file is too large to be a ${AppInfo.NAME} backup.")
                out.write(buffer, 0, n)
            }
            val bytes = out.toByteArray()
            if (!BackupCrypto.isBackup(bytes)) throw BackupException("That file isn't a ${AppInfo.NAME} backup.")
            bytes
        }
    }

    /** Decrypts and validates. Throws [BackupException] with a user-facing message. */
    suspend fun open(bytes: ByteArray, password: CharArray): BackupContents {
        val plain = try {
            withContext(Dispatchers.Default) { BackupCrypto.decrypt(bytes, password) }
        } catch (_: BackupCrypto.WrongPasswordOrCorruptException) {
            throw BackupException("Wrong password, or the backup file is damaged.")
        } catch (_: BackupCrypto.NotABackupException) {
            throw BackupException("That file isn't a ${AppInfo.NAME} backup.")
        }
        val contents = try {
            json.decodeFromString(BackupContents.serializer(), plain.decodeToString())
        } catch (_: Exception) {
            throw BackupException("The backup file is damaged and can't be read.")
        }
        if (contents.format > BackupContents.FORMAT) {
            throw BackupException("This backup was made with a newer version of ${AppInfo.NAME}.")
        }
        return sanitize(contents)
    }

    /** Restores history (merged) and review preferences. Never changes Android's actual permissions. */
    suspend fun restore(contents: BackupContents): Int {
        val added = history.importEvents(contents.events)
        preferences.restoreReviewPreferences(contents.notificationsEnabled, contents.historyEnabled, contents.periodicReview)
        return added
    }

    /** Drops malformed or oversized entries so a crafted file can't inject junk into History. */
    private fun sanitize(c: BackupContents): BackupContents {
        val t = now()
        val events = c.events
            .asSequence()
            .filter { e ->
                e.id.length in 1..64 &&
                    e.appLabel.length in 1..100 &&
                    e.packageName.length <= 255 &&
                    (e.packageName.isEmpty() || PACKAGE.matches(e.packageName)) &&
                    e.timestamp in 0..t
            }
            .take(MAX_EVENTS)
            .map { it.copy(isDemo = false) }
            .toList()
        return c.copy(
            deviceName = c.deviceName.take(60).filter { !it.isISOControl() }.ifBlank { "Unknown device" },
            appVersion = c.appVersion.take(20),
            events = events,
        )
    }

    private fun deviceName(): String {
        val maker = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        return if (Build.MODEL.startsWith(maker, ignoreCase = true)) Build.MODEL else "$maker ${Build.MODEL}"
    }

    companion object {
        const val MAX_FILE_BYTES = 5 * 1024 * 1024
        const val MAX_EVENTS = 10_000
        private val PACKAGE = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")
    }
}
