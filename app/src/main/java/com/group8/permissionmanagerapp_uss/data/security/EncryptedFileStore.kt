package com.group8.permissionmanagerapp_uss.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores a small file encrypted with AES-256-GCM. The key is generated inside the Android
 * Keystore and never leaves it, so the file is unreadable if copied off the device (SR10).
 *
 * File layout: [12-byte IV][ciphertext + 16-byte GCM tag]. GCM also detects tampering:
 * a modified file fails to decrypt and is treated as empty rather than trusted.
 */
class EncryptedFileStore(
    private val file: File,
    private val keyAlias: String,
) {
    private val atomic = AtomicFile(file)

    /** Returns the decrypted bytes, or null if there is no file or it can't be decrypted. */
    fun read(): ByteArray? {
        if (!file.exists()) return null
        return try {
            val data = atomic.readFully()
            if (data.size <= IV_SIZE) return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, data, 0, IV_SIZE))
            cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
        } catch (_: Exception) {
            // Corrupted, tampered with, or the key was wiped (e.g. lock screen removed on some devices).
            null
        }
    }

    /** Encrypts and writes atomically, so a crash mid-write never leaves a half-written file. */
    fun write(plain: ByteArray) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain)
        file.parentFile?.mkdirs()
        val out = atomic.startWrite()
        try {
            out.write(iv)
            out.write(encrypted)
            atomic.finishWrite(out)
        } catch (e: Exception) {
            atomic.failWrite(out)
            throw e
        }
    }

    fun delete() {
        atomic.delete()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
        const val TAG_BITS = 128
    }
}
