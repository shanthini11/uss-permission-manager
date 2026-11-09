package com.group8.permissionmanagerapp_uss.data.backup

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Password-based encryption for backup files (refinement #12 "Create Backup").
 *
 * The device Keystore key can't be used here because a backup must be restorable on another phone,
 * so the key is derived from a password the user chooses: PBKDF2-HMAC-SHA256 (random salt) -> AES-256-GCM.
 *
 * File layout: "PTBK" | version(1) | salt(16) | iv(12) | ciphertext+tag.
 * The header is authenticated (AAD), so any change to the file makes decryption fail.
 */
object BackupCrypto {
    private val MAGIC = byteArrayOf('P'.code.toByte(), 'T'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION: Byte = 1
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val ITERATIONS = 120_000
    private const val HEADER_SIZE = 4 + 1 + SALT_SIZE + IV_SIZE

    class WrongPasswordOrCorruptException : Exception("Wrong password or damaged backup file")
    class NotABackupException : Exception("This file isn't a Permission Tracker backup")

    fun encrypt(plain: ByteArray, password: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + salt + iv
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(plain)
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        if (!isBackup(data)) throw NotABackupException()
        val salt = data.copyOfRange(5, 5 + SALT_SIZE)
        val iv = data.copyOfRange(5 + SALT_SIZE, HEADER_SIZE)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(data, 0, HEADER_SIZE)
            cipher.doFinal(data, HEADER_SIZE, data.size - HEADER_SIZE)
        } catch (_: GeneralSecurityException) {
            throw WrongPasswordOrCorruptException()
        }
    }

    fun isBackup(data: ByteArray): Boolean =
        data.size > HEADER_SIZE + TAG_BITS / 8 &&
            data.copyOfRange(0, 4).contentEquals(MAGIC) &&
            data[4] == VERSION

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
