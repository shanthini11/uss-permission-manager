package com.group8.permissionmanagerapp_uss.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionResolverTest {

    private fun perms(vararg pairs: Pair<String, Boolean>) =
        pairs.associate { (name, granted) -> "android.permission.$name" to granted }

    @Test
    fun `untracked permissions are ignored`() {
        val result = PermissionResolver.resolve(perms("INTERNET" to true, "VIBRATE" to true))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `requested but not granted is NOT_ALLOWED`() {
        val result = PermissionResolver.resolve(perms("CAMERA" to false))
        assertEquals(AccessLevel.NOT_ALLOWED, result.single().access)
    }

    @Test
    fun `camera granted is while in use`() {
        val result = PermissionResolver.resolve(perms("CAMERA" to true))
        assertEquals(AccessLevel.WHILE_IN_USE, result.single().access)
    }

    @Test
    fun `background location is ALWAYS and marks app background`() {
        val result = PermissionResolver.resolve(
            perms("ACCESS_FINE_LOCATION" to true, "ACCESS_COARSE_LOCATION" to true, "ACCESS_BACKGROUND_LOCATION" to true)
        )
        val location = result.single()
        assertEquals(AccessLevel.ALWAYS, location.access)
        assertTrue(location.isBackground)
    }

    @Test
    fun `coarse location only is LIMITED (granular access)`() {
        val result = PermissionResolver.resolve(
            perms("ACCESS_FINE_LOCATION" to false, "ACCESS_COARSE_LOCATION" to true)
        )
        assertEquals(AccessLevel.LIMITED, result.single().access)
    }

    @Test
    fun `selected photos only is LIMITED`() {
        val result = PermissionResolver.resolve(
            perms("READ_MEDIA_IMAGES" to false, "READ_MEDIA_VISUAL_USER_SELECTED" to true)
        )
        assertEquals(AccessLevel.LIMITED, result.single().access)
    }

    @Test
    fun `full photo access is ALLOWED`() {
        val result = PermissionResolver.resolve(
            perms("READ_MEDIA_IMAGES" to true, "READ_MEDIA_VISUAL_USER_SELECTED" to true)
        )
        assertEquals(AccessLevel.ALLOWED, result.single().access)
    }

    @Test
    fun `background permission alone without foreground still counts as ALWAYS`() {
        val result = PermissionResolver.resolve(perms("BODY_SENSORS_BACKGROUND" to true))
        assertEquals(AccessLevel.ALWAYS, result.single().access)
    }

    @Test
    fun `multiple permissions in a category collapse into one row`() {
        val result = PermissionResolver.resolve(perms("READ_CONTACTS" to true, "WRITE_CONTACTS" to false))
        assertEquals(1, result.size)
        assertEquals(PermissionCategory.CONTACTS, result.single().category)
        assertEquals(AccessLevel.ALLOWED, result.single().access)
    }

    @Test
    fun `critical categories are sorted first`() {
        val result = PermissionResolver.resolve(
            perms("POST_NOTIFICATIONS" to true, "READ_CONTACTS" to true, "CAMERA" to true)
        )
        assertEquals(
            listOf(PermissionCategory.CAMERA, PermissionCategory.CONTACTS, PermissionCategory.NOTIFICATIONS),
            result.map { it.category },
        )
    }

    @Test
    fun `denied permissions add no risk`() {
        val result = PermissionResolver.resolve(perms("CAMERA" to false, "RECORD_AUDIO" to false))
        assertEquals(0, PermissionResolver.riskScore(result))
        assertEquals(RiskLevel.LOW, PermissionResolver.riskLevel(result))
    }

    @Test
    fun `background access to a critical permission is always HIGH risk`() {
        val result = PermissionResolver.resolve(
            perms("ACCESS_COARSE_LOCATION" to true, "ACCESS_BACKGROUND_LOCATION" to true)
        )
        assertEquals(RiskLevel.HIGH, PermissionResolver.riskLevel(result))
    }

    @Test
    fun `limited access scores lower than full access`() {
        val limited = PermissionResolver.resolve(perms("ACCESS_COARSE_LOCATION" to true))
        val full = PermissionResolver.resolve(perms("ACCESS_FINE_LOCATION" to true))
        assertTrue(PermissionResolver.riskScore(limited) < PermissionResolver.riskScore(full))
    }

    @Test
    fun `camera alone is LOW, camera plus microphone is MEDIUM`() {
        val low = PermissionResolver.resolve(perms("CAMERA" to true))
        assertEquals(RiskLevel.LOW, PermissionResolver.riskLevel(low))

        val medium = PermissionResolver.resolve(perms("CAMERA" to true, "RECORD_AUDIO" to true))
        assertEquals(RiskLevel.MEDIUM, PermissionResolver.riskLevel(medium))
    }

    @Test
    fun `typical chat app stays MEDIUM so High risk keeps its meaning`() {
        val chat = PermissionResolver.resolve(
            perms("CAMERA" to true, "RECORD_AUDIO" to true, "READ_CONTACTS" to true, "READ_MEDIA_IMAGES" to true)
        )
        assertEquals(RiskLevel.MEDIUM, PermissionResolver.riskLevel(chat))
    }

    @Test
    fun `very broad foreground access is HIGH`() {
        val broad = PermissionResolver.resolve(
            perms("CAMERA" to true, "RECORD_AUDIO" to true, "READ_CONTACTS" to true, "READ_SMS" to true,
                "ACCESS_FINE_LOCATION" to true)
        )
        assertEquals(RiskLevel.HIGH, PermissionResolver.riskLevel(broad))
    }

    @Test
    fun `risk reasons put background access first and skip denied permissions`() {
        val result = PermissionResolver.resolve(
            perms("CAMERA" to true, "RECORD_AUDIO" to false,
                "ACCESS_FINE_LOCATION" to true, "ACCESS_BACKGROUND_LOCATION" to true)
        )
        val reasons = PermissionResolver.riskReasons(result)
        assertEquals("Can use your location even when the app is closed", reasons.first())
        assertTrue(reasons.none { "microphone" in it })
    }

    @Test
    fun `every tracked android permission maps to exactly one category`() {
        val all = PermissionCategory.entries.flatMap { it.androidPermissions }
        assertEquals("Duplicate permission across categories", all.size, all.toSet().size)
        all.forEach { assertTrue(PermissionCategory.forAndroidPermission(it) != null) }
    }
}
