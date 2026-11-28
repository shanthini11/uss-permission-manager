package com.group8.permissionmanagerapp_uss.data.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * Repackaging check (Zombinder-style attacks, peer-testing finding): a modified copy of the APK must be
 * re-signed with someone else's key, so the release build checks that it is signed with our certificate.
 *
 * This raises the bar but is not absolute: an attacker who edits the code can also remove the check.
 * The published APK checksum and Play Store distribution remain the stronger defences.
 */
object AppIntegrity {
    /** SHA-256 of the official release signing certificate (also printed in the user guide). */
    const val OFFICIAL_CERT_SHA256 = "ebcbeb6294ea942ef1dcf2b8cac34a2c3e7e17ea14dfc4e6893c55bd8d7ab716"

    fun isOfficialBuild(context: Context): Boolean =
        runCatching { signingCertDigests(context).contains(OFFICIAL_CERT_SHA256) }.getOrDefault(false)

    @Suppress("DEPRECATION")
    private fun signingCertDigests(context: Context): Set<String> {
        val pm = context.packageManager
        val name = context.packageName
        val certs: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo ?: return emptySet()
            val signers = if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
            signers.orEmpty().map { it.toByteArray() }
        } else {
            pm.getPackageInfo(name, PackageManager.GET_SIGNATURES).signatures.orEmpty().map { it.toByteArray() }
        }
        return certs.map { sha256(it) }.toSet()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
