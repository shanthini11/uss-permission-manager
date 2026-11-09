package com.group8.permissionmanagerapp_uss.data.history

import android.content.Context
import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.security.EncryptedFileStore
import com.group8.permissionmanagerapp_uss.data.settings.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Permission History + Notifications storage, encrypted on this device (SR8, SR10).
 * Keeps 30 days of events (the retention promised on the Settings and Privacy screens).
 */
class HistoryRepository(
    context: Context,
    private val preferences: UserPreferencesRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val store = EncryptedFileStore(File(context.filesDir, "history.bin"), "pm_history_key")
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val state = MutableStateFlow<HistoryFile?>(null)

    /** All events, newest first. Loads lazily from disk on first collection. */
    val events: Flow<List<PermissionEvent>> = state.filterNotNull().map { file ->
        file.events.sortedByDescending { it.timestamp }
    }

    suspend fun ensureLoaded() {
        if (state.value != null) return
        mutex.withLock { loadLocked() }
    }

    /**
     * Compares [apps] with the last snapshot, records any changes and saves the new snapshot.
     * @return the newly recorded events (empty on the first ever check, or if history is off).
     */
    suspend fun recordDetected(apps: List<InstalledApp>): List<PermissionEvent> = mutate { file ->
        val t = now()
        val found = ChangeDetector.diff(file.snapshot, apps, t) { UUID.randomUUID().toString() }
        val recordable = if (historyEnabled()) found else emptyList()
        file.copy(events = file.events + recordable, snapshot = ChangeDetector.snapshotOf(apps, t)) to recordable
    }

    /**
     * Records a change the user made through our sheet. If a background check already logged the
     * same change a moment ago, that entry is relabelled instead of creating a duplicate.
     */
    suspend fun recordInAppChange(
        app: InstalledApp,
        category: PermissionCategory,
        from: AccessLevel?,
        to: AccessLevel,
        isDemo: Boolean,
    ) {
        mutate { file ->
            val t = now()
            val recent = file.events.lastOrNull {
                it.source == EventSource.DETECTED && it.packageName == app.packageName &&
                    it.category == category && it.to == to && t - it.timestamp < MERGE_WINDOW_MS
            }
            val events = when {
                !historyEnabled() -> file.events
                recent != null -> file.events.map { if (it.id == recent.id) it.copy(source = EventSource.IN_APP) else it }
                else -> file.events + PermissionEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = t,
                    packageName = app.packageName,
                    appLabel = app.label,
                    category = category,
                    from = from,
                    to = to,
                    source = EventSource.IN_APP,
                    isDemo = isDemo,
                )
            }
            // Keep the snapshot in step so the next background check doesn't log this change again.
            val snapshot = if (isDemo) file.snapshot else file.snapshot?.let { snap ->
                val appMap = snap.apps[app.packageName].orEmpty() + (category to to)
                snap.copy(apps = snap.apps + (app.packageName to appMap))
            }
            file.copy(events = events, snapshot = snapshot) to Unit
        }
    }

    suspend fun addReviewReminder(isDemo: Boolean) {
        mutate { file ->
            file.copy(
                events = file.events + PermissionEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = now(),
                    packageName = "",
                    appLabel = com.group8.permissionmanagerapp_uss.AppInfo.NAME,
                    category = null,
                    from = null,
                    to = null,
                    source = EventSource.REVIEW_REMINDER,
                    isDemo = isDemo,
                ),
            ) to Unit
        }
    }

    suspend fun dismiss(id: String) {
        mutate { file -> file.copy(events = file.events.map { if (it.id == id) it.copy(dismissed = true) else it }) to Unit }
    }

    suspend fun dismissAll(isDemo: Boolean) {
        mutate { file -> file.copy(events = file.events.map { if (it.isDemo == isDemo) it.copy(dismissed = true) else it }) to Unit }
    }

    /** Adds sample history the first time Demo mode is used, so charts and logs have content. */
    suspend fun seedDemoIfNeeded() {
        mutate { file ->
            if (file.demoSeeded) file to Unit
            else file.copy(events = file.events + DemoHistory.events(now()), demoSeeded = true) to Unit
        }
    }

    /** Real (non-demo) events for a backup file. */
    suspend fun exportEvents(): List<PermissionEvent> {
        ensureLoaded()
        return state.value?.events.orEmpty().filter { !it.isDemo }
    }

    /**
     * Merges events from a backup. Existing entries with the same id are kept; entries older than
     * the 30-day retention are dropped by [mutate]. Returns how many new entries were added.
     */
    suspend fun importEvents(events: List<PermissionEvent>): Int = mutate { file ->
        val existing = file.events.map { it.id }.toSet()
        val added = events.filter { it.id !in existing }.map { it.copy(isDemo = false) }
        file.copy(events = file.events + added) to added.size
    }

    /** Deletes all history and the snapshot (Settings > Delete Local Data). */
    suspend fun clearAll() {
        mutex.withLock {
            withContext(Dispatchers.IO) { store.delete() }
            state.value = HistoryFile()
        }
    }

    private suspend fun historyEnabled() = preferences.preferences.first().historyEnabled

    private suspend fun <T> mutate(block: suspend (HistoryFile) -> Pair<HistoryFile, T>): T = mutex.withLock {
        val current = state.value ?: loadLocked()
        val (updated, result) = block(current)
        val pruned = updated.copy(events = updated.events.filter { now() - it.timestamp <= RETENTION_MS })
        withContext(Dispatchers.IO) { store.write(json.encodeToString(HistoryFile.serializer(), pruned).toByteArray()) }
        state.value = pruned
        result
    }

    private suspend fun loadLocked(): HistoryFile {
        state.value?.let { return it }
        val loaded = withContext(Dispatchers.IO) {
            store.read()?.let { bytes ->
                runCatching { json.decodeFromString(HistoryFile.serializer(), bytes.decodeToString()) }.getOrNull()
            }
        } ?: HistoryFile()
        state.value = loaded
        return loaded
    }

    companion object {
        const val RETENTION_DAYS = 30
        private const val DAY_MS = 24L * 60 * 60 * 1000
        private const val RETENTION_MS = RETENTION_DAYS * DAY_MS
        private const val MERGE_WINDOW_MS = 10L * 60 * 1000
    }
}
