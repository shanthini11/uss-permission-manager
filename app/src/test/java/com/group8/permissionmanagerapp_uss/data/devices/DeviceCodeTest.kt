package com.group8.permissionmanagerapp_uss.data.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCodeTest {
    private val device = "123e4567-e89b-12d3-a456-426614174000"

    @Test
    fun `codes are always 6 digits`() {
        repeat(200) {
            val code = DeviceCode.newCode()
            assertEquals(6, code.length)
            assertTrue(code.all { it.isDigit() })
        }
    }

    @Test
    fun `salt and hash have the lengths the Firestore rules expect`() {
        val salt = DeviceCode.newSalt()
        assertEquals(32, salt.length)
        assertEquals(64, DeviceCode.hash("123456", device, salt).length)
    }

    @Test
    fun `correct code matches, wrong code does not`() {
        val salt = DeviceCode.newSalt()
        val hash = DeviceCode.hash("482913", device, salt)
        assertTrue(DeviceCode.matches("482913", device, salt, hash))
        assertFalse(DeviceCode.matches("482914", device, salt, hash))
    }

    @Test
    fun `a code issued for one device does not work for another`() {
        val salt = DeviceCode.newSalt()
        val hash = DeviceCode.hash("482913", device, salt)
        assertFalse(DeviceCode.matches("482913", "aaaaaaaa-e89b-12d3-a456-426614174000", salt, hash))
    }

    @Test
    fun `same code with a different salt hashes differently`() {
        assertNotEquals(
            DeviceCode.hash("111111", device, DeviceCode.newSalt()),
            DeviceCode.hash("111111", device, DeviceCode.newSalt()),
        )
    }
}
