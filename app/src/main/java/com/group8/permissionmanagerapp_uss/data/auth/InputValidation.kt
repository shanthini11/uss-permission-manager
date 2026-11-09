package com.group8.permissionmanagerapp_uss.data.auth

/** Client-side input checks for the sign-in forms. Firebase validates again on the server. */
object InputValidation {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MAX_EMAIL = 254
    const val MAX_NAME = 50
    const val MAX_PASSWORD = 128
    const val MIN_PASSWORD = 8

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email address."
        email.length > MAX_EMAIL || !EMAIL.matches(email.trim()) -> "Enter a valid email address, like name@example.com."
        else -> null
    }

    fun nameError(name: String): String? = when {
        name.isBlank() -> "Enter your name."
        name.trim().length > MAX_NAME -> "Keep your name under $MAX_NAME characters."
        name.any { it.isISOControl() } -> "Your name contains characters that aren't allowed."
        else -> null
    }

    /** Each rule is shown as a live checklist on Sign Up. */
    data class PasswordRules(val length: Boolean, val letter: Boolean, val digit: Boolean, val notTooLong: Boolean) {
        val allMet: Boolean get() = length && letter && digit && notTooLong
    }

    fun passwordRules(password: String) = PasswordRules(
        length = password.length >= MIN_PASSWORD,
        letter = password.any { it.isLetter() },
        digit = password.any { it.isDigit() },
        notTooLong = password.length <= MAX_PASSWORD,
    )
}
