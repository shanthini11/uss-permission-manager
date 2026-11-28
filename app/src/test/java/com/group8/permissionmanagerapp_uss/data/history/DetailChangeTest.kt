package com.group8.permissionmanagerapp_uss.data.history

import com.group8.permissionmanagerapp_uss.data.model.PermissionCategory
import com.group8.permissionmanagerapp_uss.data.model.PermissionResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailChangeTest {

    private fun app(vararg grants: Pair<String, Boolean>) = PermissionResolver.buildApp(
        packageName = "com.example.maps",
        label = "Maps",
        isSystemApp = false,
        permissions = PermissionResolver.resolve(grants.associate { (p, g) -> "android.permission.$p" to g }),
        lastUpdatedAt = 0,
    )

    @Test
    fun `approximate to precise background location is logged`() {
        val before = app("ACCESS_COARSE_LOCATION" to true, "ACCESS_FINE_LOCATION" to false, "ACCESS_BACKGROUND_LOCATION" to true)
        val after = app("ACCESS_COARSE_LOCATION" to true, "ACCESS_FINE_LOCATION" to true, "ACCESS_BACKGROUND_LOCATION" to true)
        val events = ChangeDetector.diff(ChangeDetector.snapshotOf(listOf(before), 0), listOf(after), 1) { "id" }
        val e = events.single()
        assertEquals(PermissionCategory.LOCATION, e.category)
        assertTrue(e.fromDetail!!.startsWith("Approximate"))
        assertTrue(e.toDetail!!.startsWith("Precise"))
    }

    @Test
    fun `no detail event when nothing changed`() {
        val a = app("ACCESS_FINE_LOCATION" to true)
        assertTrue(ChangeDetector.diff(ChangeDetector.snapshotOf(listOf(a), 0), listOf(a), 1) { "id" }.isEmpty())
    }

    @Test
    fun `ask every time is described honestly on Android 11 and later`() {
        val p = PermissionResolver.resolve(mapOf("android.permission.CAMERA" to false), sdkInt = 34).single()
        assertEquals(PermissionResolver.ASK_OR_DENIED, p.detail)
    }
}
