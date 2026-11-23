package com.group8.permissionmanagerapp_uss.data.auth

/** Input checks for the account forms (the repository re-checks before saving). */
object InputValidation {
    private val EMAIL = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MAX_EMAIL = 254
    const val MAX_NAME = 50
    const val MAX_PASSWORD = 128
    /** 10+ because the account is offline: a longer password makes guessing a stolen hash impractical. */
    const val MIN_PASSWORD = 10

    /**
     * Passwords that meet "8+ characters, a letter and a number" but are among the first guesses in
     * dictionary attacks (password-cracking defence, Lecture 7). Compared case-insensitively.
     */
    private val COMMON_PASSWORDS = setOf(
        "password1", "password12", "password123", "password1234", "passw0rd", "p@ssw0rd", "pa55word",
        "abc12345", "abcd1234", "abc123456", "qwerty123", "qwerty12", "qwerty1234", "1qaz2wsx", "1q2w3e4r",
        "q1w2e3r4", "zaq12wsx", "asdf1234", "iloveyou1", "welcome1", "welcome123", "letmein1", "admin123",
        "admin1234", "test1234", "user1234", "login123", "monkey123", "dragon123", "sunshine1", "princess1",
        "football1", "baseball1", "india123", "india@123", "delhi123", "test@123", "pass@123", "hello123",
        "12345678a", "123456789a", "a12345678", "1234abcd", "11111111a", "00000000a",
    )

    fun emailError(email: String): String? = when {
        email.isBlank() -> "Enter your email address."
        email.length > MAX_EMAIL || !EMAIL.matches(email.trim()) -> "Enter a valid email address, like name@example.com."
        else -> null
    }

    fun nameError(name: String): String? = when {
        name.isBlank() -> "Enter your name."
        name.trim().length > MAX_NAME -> "Keep your name under $MAX_NAME characters."
        name.any { it.isISOControl() || it in TextSafety.BIDI_CONTROLS } -> "Your name contains characters that aren't allowed."
        else -> null
    }

    /** Each rule is shown as a live checklist on Sign Up. */
    data class PasswordRules(
        val length: Boolean,
        val letter: Boolean,
        val digit: Boolean,
        val notTooLong: Boolean,
        val notCommon: Boolean = true,
        val notEmailName: Boolean = true,
    ) {
        val allMet: Boolean get() = length && letter && digit && notTooLong && notCommon && notEmailName
    }

    /** @param email if given, the part before "@" must not appear in the password. */
    fun passwordRules(password: String, email: String = ""): PasswordRules {
        val emailName = email.substringBefore('@').trim().lowercase()
        return PasswordRules(
            length = password.length >= MIN_PASSWORD,
            letter = password.any { it.isLetter() },
            digit = password.any { it.isDigit() },
            notTooLong = password.length <= MAX_PASSWORD,
            notCommon = password.lowercase() !in COMMON_PASSWORDS,
            notEmailName = emailName.length < 4 || !password.lowercase().contains(emailName),
        )
    }
}

/** Removes characters that can disguise text, e.g. right-to-left overrides used to spoof names. */
object TextSafety {
    val BIDI_CONTROLS = setOf(
        '‎', '‏', '‪', '‫', '‬', '‭', '‮',
        '⁦', '⁧', '⁨', '⁩', '؜',
    )

    fun clean(text: String, maxLength: Int): String =
        text.filter { !it.isISOControl() && it !in BIDI_CONTROLS }.trim().take(maxLength)
}
