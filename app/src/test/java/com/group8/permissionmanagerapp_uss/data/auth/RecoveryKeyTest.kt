package com.group8.permissionmanagerapp_uss.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryKeyTest {

    @Test
    fun `generated keys are well formed and unique`() {
        val keys = (1..200).map { RecoveryKey.generate() }
        keys.forEach {
            assertEquals(RecoveryKey.LENGTH, it.length)
            assertTrue(RecoveryKey.isWellFormed(it))
            assertFalse(it.any { c -> c in "ILOU" })
        }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `formatted as six groups of four`() {
        val key = "K7P7HB8XPW3S2T2TFVZ8SCPY"
        assertEquals("K7P7-HB8X-PW3S-2T2T-FVZ8-SCPY", RecoveryKey.formatted(key))
        assertEquals(6, RecoveryKey.groups(key).size)
    }

    @Test
    fun `typing is forgiving - case, spaces, dashes and look-alikes`() {
        val key = "K7P7HB8XPW3S2T2TFVZ8SC01"
        assertEquals(key, RecoveryKey.normalize("k7p7 hb8x-pw3s 2t2t-fvz8-scOI"))
        assertEquals(key, RecoveryKey.normalize("K7P7-HB8X-PW3S-2T2T-FVZ8-SCOL"))
    }

    @Test
    fun `wrong length or characters are rejected`() {
        assertFalse(RecoveryKey.isWellFormed(""))
        assertFalse(RecoveryKey.isWellFormed("K7P7-HB8X-PW3S"))
        assertFalse(RecoveryKey.isWellFormed("K7P7HB8XPW3S2T2TFVZ8SCPYX"))
        assertFalse(RecoveryKey.isWellFormed("UUUUUUUUUUUUUUUUUUUUUUUU"))
    }
}
