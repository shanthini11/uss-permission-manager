package com.group8.permissionmanagerapp_uss.data.auth

import java.security.SecureRandom

/**
 * 24-character recovery key (120 bits of randomness), shown once at sign-up as 6 groups of 4,
 * e.g. "K7P7-HB8X-PW3S-2T2T-FVZ8-SCPY". It lets the owner set a new password if they forget it,
 * without any server. Only a slow hash of it is stored.
 *
 * Alphabet: Crockford Base32 (no I, L, O, U) so it's easy to copy by hand without look-alike mistakes.
 */
object RecoveryKey {
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val LENGTH = 24
    const val GROUP = 4

    fun generate(random: SecureRandom = SecureRandom()): String =
        (1..LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

    /** "k7p7 hb8x-pw3s…" -> "K7P7HB8XPW3S…"; also maps common look-alikes (O→0, I/L→1). */
    fun normalize(input: String): String =
        input.uppercase()
            .filter { it.isLetterOrDigit() }
            .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
            .joinToString("")

    fun isWellFormed(input: String): Boolean {
        val n = normalize(input)
        return n.length == LENGTH && n.all { it in ALPHABET }
    }

    fun formatted(key: String): String = normalize(key).chunked(GROUP).joinToString("-")

    fun groups(key: String): List<String> = normalize(key).chunked(GROUP)
}
