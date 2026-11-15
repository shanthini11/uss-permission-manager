package com.group8.permissionmanagerapp_uss.data.auth

import com.group8.permissionmanagerapp_uss.ui.auth.maskEmail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidationTest {

    @Test
    fun `valid emails pass`() {
        assertNull(InputValidation.emailError("name@example.com"))
        assertNull(InputValidation.emailError("  first.last+tag@uni.ac.in  "))
    }

    @Test
    fun `invalid emails fail`() {
        listOf("", "no-at-sign", "a@b", "a@b.", "<script>@x.com", "a b@c.com").forEach {
            assertNotNull("expected error for '$it'", InputValidation.emailError(it))
        }
        assertNotNull(InputValidation.emailError("a".repeat(250) + "@x.com"))
    }

    @Test
    fun `password rules`() {
        assertFalse(InputValidation.passwordRules("short1").allMet)
        assertFalse(InputValidation.passwordRules("onlyletters").allMet)
        assertFalse(InputValidation.passwordRules("12345678").allMet)
        assertTrue(InputValidation.passwordRules("letters123").allMet)
        assertFalse(InputValidation.passwordRules("a1".repeat(65)).allMet)
    }

    @Test
    fun `names are required, bounded and free of control characters`() {
        assertNotNull(InputValidation.nameError("   "))
        assertNotNull(InputValidation.nameError("x".repeat(51)))
        assertNotNull(InputValidation.nameError("Bad\u0000Name"))
        assertNull(InputValidation.nameError("Manojna Reddy"))
    }

    @Test
    fun `common passwords are rejected even if they meet the basic rules`() {
        val rules = InputValidation.passwordRules("Password123")
        assertTrue(rules.length && rules.letter && rules.digit)
        assertFalse(rules.notCommon)
        assertFalse(rules.allMet)
    }

    @Test
    fun `password containing the email name is rejected`() {
        assertFalse(InputValidation.passwordRules("manojna2026", "manojna@gmail.com").allMet)
        assertTrue(InputValidation.passwordRules("blue-river-42", "manojna@gmail.com").allMet)
    }

    @Test
    fun `bidi override characters are stripped from names`() {
        // U+202E would make "evil‮gpj.exe" display as "evilexe.jpg"
        assertEquals("Pixel 8gpj", TextSafety.clean("Pixel 8‮gpj", 40))
        assertNotNull(InputValidation.nameError("Mano‮jna"))
    }

    @Test
    fun `email is masked on screen`() {
        assertEquals("ma•••••@gmail.com", maskEmail("manojna@gmail.com"))
        assertEquals("a•••@x.com", maskEmail("a@x.com"))
    }
}
