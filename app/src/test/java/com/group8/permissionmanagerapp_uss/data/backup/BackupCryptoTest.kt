package com.group8.permissionmanagerapp_uss.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {
    private val plain = "{\"events\":[1,2,3]}".toByteArray()

    @Test
    fun `round trip with the right password`() {
        val enc = BackupCrypto.encrypt(plain, "correct horse 1".toCharArray())
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, "correct horse 1".toCharArray()))
    }

    @Test(expected = BackupCrypto.WrongPasswordOrCorruptException::class)
    fun `wrong password is rejected`() {
        val enc = BackupCrypto.encrypt(plain, "right-pass-1".toCharArray())
        BackupCrypto.decrypt(enc, "wrong-pass-1".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordOrCorruptException::class)
    fun `tampered ciphertext is rejected`() {
        val enc = BackupCrypto.encrypt(plain, "pass-word-1".toCharArray())
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        BackupCrypto.decrypt(enc, "pass-word-1".toCharArray())
    }

    @Test(expected = BackupCrypto.WrongPasswordOrCorruptException::class)
    fun `tampered header (salt) is rejected`() {
        val enc = BackupCrypto.encrypt(plain, "pass-word-1".toCharArray())
        enc[6] = (enc[6].toInt() xor 1).toByte()
        BackupCrypto.decrypt(enc, "pass-word-1".toCharArray())
    }

    @Test(expected = BackupCrypto.NotABackupException::class)
    fun `random file is not a backup`() {
        BackupCrypto.decrypt("hello, I am a photo".toByteArray(), "x".toCharArray())
    }

    @Test
    fun `same data encrypts differently each time (random salt and IV)`() {
        val a = BackupCrypto.encrypt(plain, "pw-123456".toCharArray())
        val b = BackupCrypto.encrypt(plain, "pw-123456".toCharArray())
        assertNotEquals(a.toList(), b.toList())
        assertTrue(BackupCrypto.isBackup(a))
        assertFalse(BackupCrypto.isBackup(plain))
    }
}
