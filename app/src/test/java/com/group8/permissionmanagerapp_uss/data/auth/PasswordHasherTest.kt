package com.group8.permissionmanagerapp_uss.data.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordHasherTest {

    // Low iteration count keeps the unit tests fast; production uses PasswordHasher.ITERATIONS.
    private val fast = 1_000

    @Test
    fun `correct password verifies, wrong one doesn't`() {
        val stored = PasswordHasher.hash("blue-river-42".toCharArray(), fast)
        assertTrue(PasswordHasher.verify("blue-river-42".toCharArray(), stored))
        assertFalse(PasswordHasher.verify("blue-river-43".toCharArray(), stored))
        assertFalse(PasswordHasher.verify("".toCharArray(), stored))
    }

    @Test
    fun `same password gets a different salt and hash each time`() {
        val a = PasswordHasher.hash("blue-river-42".toCharArray(), fast)
        val b = PasswordHasher.hash("blue-river-42".toCharArray(), fast)
        assertNotEquals(a.salt, b.salt)
        assertNotEquals(a.hash, b.hash)
    }

    @Test
    fun `hash never contains the password and has expected sizes`() {
        val stored = PasswordHasher.hash("blue-river-42".toCharArray(), fast)
        assertFalse(stored.hash.contains("blue"))
        assertEquals(32, stored.salt.length)   // 16 bytes hex
        assertEquals(64, stored.hash.length)   // 256 bits hex
        assertEquals(fast, stored.iterations)
    }

    @Test
    fun `production iteration count is high`() {
        assertTrue(PasswordHasher.ITERATIONS >= 100_000)
    }

    @Test
    fun `lockout starts after five failures, doubles, and is capped`() {
        repeat(LockoutPolicy.FREE_ATTEMPTS) { assertEquals(0L, LockoutPolicy.lockoutMillis(it)) }
        assertEquals(30_000L, LockoutPolicy.lockoutMillis(5))
        assertEquals(60_000L, LockoutPolicy.lockoutMillis(6))
        assertEquals(120_000L, LockoutPolicy.lockoutMillis(7))
        assertEquals(15 * 60_000L, LockoutPolicy.lockoutMillis(20))
        assertEquals(15 * 60_000L, LockoutPolicy.lockoutMillis(10_000))
    }
}
