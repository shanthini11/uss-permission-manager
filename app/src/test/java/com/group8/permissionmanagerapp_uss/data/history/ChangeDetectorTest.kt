package com.group8.permissionmanagerapp_uss.data.history

import com.group8.permissionmanagerapp_uss.data.model.AccessLevel
import com.group8.permissionmanagerapp_uss.data.model.InstalledApp
import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.PermissionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChangeDetectorTest {

    private var nextId = 0
    private fun id() = "e${nextId++}"

    private fun app(pkg: String, vararg grants: Pair<String, Boolean>): InstalledApp =
        PermissionResolver.buildApp(
            packageName = pkg,
            label = pkg,
            isSystemApp = false,
            permissions = PermissionResolver.resolve(grants.associate { (p, g) -> "android.permission.$p" to g }),
            lastUpdatedAt = 0,
        )

    @Test
    fun `first check records nothing`() {
        val apps = listOf(app("a", "CAMERA" to true))
        assertTrue(ChangeDetector.diff(null, apps, 1, ::id).isEmpty())
    }

    @Test
    fun `no change produces no events`() {
        val apps = listOf(app("a", "CAMERA" to true))
        val snap = ChangeDetector.snapshotOf(apps, 0)
        assertTrue(ChangeDetector.diff(snap, apps, 1, ::id).isEmpty())
    }

    @Test
    fun `revoked permission is recorded with before and after`() {
        val before = ChangeDetector.snapshotOf(listOf(app("a", "CAMERA" to true)), 0)
        val events = ChangeDetector.diff(before, listOf(app("a", "CAMERA" to false)), 5, ::id)
        val e = events.single()
        assertEquals(PermissionCategory.CAMERA, e.category)
        assertEquals(AccessLevel.WHILE_IN_USE, e.from)
        assertEquals(AccessLevel.NOT_ALLOWED, e.to)
        assertEquals(EventSource.DETECTED, e.source)
        assertEquals(5L, e.timestamp)
    }

    @Test
    fun `upgrade to background location is recorded`() {
        val before = ChangeDetector.snapshotOf(listOf(app("a", "ACCESS_FINE_LOCATION" to true, "ACCESS_BACKGROUND_LOCATION" to false)), 0)
        val after = listOf(app("a", "ACCESS_FINE_LOCATION" to true, "ACCESS_BACKGROUND_LOCATION" to true))
        assertEquals(AccessLevel.ALWAYS, ChangeDetector.diff(before, after, 1, ::id).single().to)
    }

    @Test
    fun `new app reports only granted permissions`() {
        val before = ChangeDetector.snapshotOf(emptyList(), 0)
        val events = ChangeDetector.diff(before, listOf(app("new", "CAMERA" to true, "RECORD_AUDIO" to false)), 1, ::id)
        val e = events.single()
        assertEquals(PermissionCategory.CAMERA, e.category)
        assertNull(e.from)
    }
}
