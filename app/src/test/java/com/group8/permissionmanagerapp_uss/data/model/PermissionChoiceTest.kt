package com.group8.permissionmanagerapp_uss.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies how "Permission Updated / Not Updated" is decided after returning from Android Settings. */
class PermissionChoiceTest {

    @Test
    fun `deny matches only when access is gone`() {
        assertTrue(PermissionChoice.DENY.matches(AccessLevel.NOT_ALLOWED))
        assertFalse(PermissionChoice.DENY.matches(AccessLevel.WHILE_IN_USE))
        assertFalse(PermissionChoice.DENY.matches(AccessLevel.LIMITED))
    }

    @Test
    fun `allow once reads as not allowed after the app closes`() {
        assertTrue(PermissionChoice.ALLOW_ONCE.matches(AccessLevel.NOT_ALLOWED))
        assertFalse(PermissionChoice.ALLOW_ONCE.matches(AccessLevel.ALWAYS))
    }

    @Test
    fun `while in use does not match always`() {
        assertTrue(PermissionChoice.WHILE_IN_USE.matches(AccessLevel.WHILE_IN_USE))
        assertFalse(PermissionChoice.WHILE_IN_USE.matches(AccessLevel.ALWAYS))
    }

    @Test
    fun `selected photos requires limited access, allow all rejects limited`() {
        assertTrue(PermissionChoice.SELECTED_ONLY.matches(AccessLevel.LIMITED))
        assertFalse(PermissionChoice.SELECTED_ONLY.matches(AccessLevel.ALLOWED))
        assertTrue(PermissionChoice.ALLOW_ALL.matches(AccessLevel.ALLOWED))
        assertFalse(PermissionChoice.ALLOW_ALL.matches(AccessLevel.LIMITED))
    }

    @Test
    fun `options offered depend on the category`() {
        assertTrue(PermissionChoice.ALWAYS in PermissionChoice.optionsFor(PermissionCategory.LOCATION))
        assertFalse(PermissionChoice.ALWAYS in PermissionChoice.optionsFor(PermissionCategory.CAMERA))
        assertEquals(
            listOf(PermissionChoice.ALLOW, PermissionChoice.DENY),
            PermissionChoice.optionsFor(PermissionCategory.CONTACTS),
        )
    }

    @Test
    fun `current choice reflects granular access`() {
        val approx = AppPermission(PermissionCategory.LOCATION, AccessLevel.LIMITED)
        assertEquals(PermissionChoice.APPROXIMATE, PermissionChoice.current(approx))
        val selectedPhotos = AppPermission(PermissionCategory.FILES_PHOTOS, AccessLevel.LIMITED)
        assertEquals(PermissionChoice.SELECTED_ONLY, PermissionChoice.current(selectedPhotos))
    }

    @Test
    fun `every option shown for a category maps back to itself`() {
        // If the user applies a choice in Demo mode, re-opening the sheet must pre-select that choice.
        PermissionCategory.entries.forEach { category ->
            PermissionChoice.optionsFor(category)
                .filter { it != PermissionChoice.ALLOW_ONCE } // stored as "not allowed", shown as Don't allow
                .forEach { choice ->
                    val after = AppPermission(category, choice.target)
                    assertEquals("$category / $choice", choice, PermissionChoice.current(after))
                }
        }
    }
}
