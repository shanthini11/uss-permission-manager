package com.group8.permissionmanagerapp_uss.data.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.UserNotAuthenticatedException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The second sign-in factor: an AES-256 key inside the Android Keystore that can only be used for
 * [VALIDITY_SECONDS] seconds after the user unlocks with their fingerprint, face or screen-lock PIN.
 *
 * This is enforced by the Keystore (secure hardware where available), not by the app's UI: even if
 * someone patches the app or hooks it to "skip" the unlock prompt, the key refuses to work and the
 * sign-in fails. The key is created only if the phone has a secure lock screen.
 */
class DeviceBoundKey(private val alias: String) {

    class NotUnlockedException : Exception("Device not unlocked recently")
    class InvalidatedException : Exception("Screen lock changed; key no longer usable")

    fun ensureExists() {
        val ks = keyStore()
        if (ks.containsAlias(alias)) return
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                VALIDITY_SECONDS,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(VALIDITY_SECONDS)
        }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
            init(builder.build())
            generateKey()
        }
    }

    /** Returns [IV(12)][ciphertext+tag]. Requires a recent device unlock. */
    fun encrypt(plain: ByteArray): ByteArray = guard {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.iv + cipher.doFinal(plain)
    }

    /** Requires a recent device unlock. */
    fun decrypt(data: ByteArray): ByteArray = guard {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data, 0, IV_SIZE))
        cipher.doFinal(data, IV_SIZE, data.size - IV_SIZE)
    }

    fun delete() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    private fun <T> guard(block: () -> T): T = try {
        block()
    } catch (_: UserNotAuthenticatedException) {
        throw NotUnlockedException()
    } catch (_: KeyPermanentlyInvalidatedException) {
        throw InvalidatedException()
    }

    private fun key(): SecretKey =
        (keyStore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey ?: throw InvalidatedException()

    private fun keyStore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        const val VALIDITY_SECONDS = 30
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SIZE = 12
    }
}
