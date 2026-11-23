package com.group8.permissionmanagerapp_uss.data.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Password storage for the on-device account. The password itself is never stored: only a
 * PBKDF2-HMAC-SHA256 hash with a random 16-byte salt and a high iteration count, so a stolen file
 * can't be reversed and each guess is deliberately slow (defence against password cracking).
 */
object PasswordHasher {
    const val ITERATIONS = 200_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16

    data class Hash(val salt: String, val hash: String, val iterations: Int)

    fun hash(password: CharArray, iterations: Int = ITERATIONS, random: SecureRandom = SecureRandom()): Hash {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        return Hash(encode(salt), encode(derive(password, salt, iterations)), iterations)
    }

    /** Constant-time comparison, so response timing doesn't reveal how close a guess was. */
    fun verify(password: CharArray, stored: Hash): Boolean {
        val candidate = derive(password, decode(stored.salt), stored.iterations)
        return MessageDigest.isEqual(candidate, decode(stored.hash))
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(b: ByteArray) = b.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun decode(s: String): ByteArray = ByteArray(s.length / 2) { i -> s.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}

/**
 * Brute-force protection: after 5 wrong passwords, a lockout of 30 seconds that doubles with every
 * further wrong attempt, up to 15 minutes. The counter is stored encrypted and survives restarts.
 */
object LockoutPolicy {
    const val FREE_ATTEMPTS = 5
    private const val BASE_MS = 30_000L
    private const val MAX_MS = 15 * 60_000L

    fun lockoutMillis(failures: Int): Long {
        if (failures < FREE_ATTEMPTS) return 0
        val shift = (failures - FREE_ATTEMPTS).coerceAtMost(10)
        return (BASE_MS shl shift).coerceAtMost(MAX_MS)
    }
}
