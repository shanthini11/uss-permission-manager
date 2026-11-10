package com.group8.permissionmanagerapp_uss.data.auth

import com.group8.permissionmanagerapp_uss.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the free verification service (backend/otp-service/Code.gs on Google Apps Script).
 * Email codes and device join codes are generated, stored and checked on the server; the server
 * also sets the 2FA custom claim and adds approved devices, which Firestore rules rely on.
 */
class OtpService(
    private val serviceUrl: String,
    private val auth: AuthRepository,
) {
    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String, val retryAfterSeconds: Int? = null) : Result
    }

    /** Result of a device action; [code] is set only by `issueDeviceCode`. */
    data class DeviceResult(val ok: Boolean, val message: String? = null, val code: String? = null)

    @Serializable
    private data class Response(
        val ok: Boolean = false,
        val error: String? = null,
        val retryAfter: Int? = null,
        val attemptsLeft: Int? = null,
        val code: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = serviceUrl.startsWith("https://script.google.com/")

    suspend fun sendCode(): Result = toResult(call("send", emptyMap()))

    suspend fun verifyCode(code: String): Result {
        if (!CODE_REGEX.matches(code)) return Result.Error("Enter the 6-digit code from the email.")
        return toResult(call("verify", mapOf("code" to code)))
    }

    suspend fun device(action: String, fields: Map<String, String>): DeviceResult {
        val r = call(action, fields)
        return DeviceResult(ok = r.ok, message = if (r.ok) null else message(r), code = r.code)
    }

    private suspend fun call(action: String, fields: Map<String, String>): Response {
        if (!isConfigured) return Response(error = "not_configured")
        val token = try {
            auth.idToken()
        } catch (_: AuthException) {
            return Response(error = "unauthenticated")
        }
        // Built with a JSON library, so names and codes can never break out of their strings.
        val body = buildJsonObject {
            put("action", action)
            put("idToken", token)
            fields.forEach { (k, v) -> put(k, v) }
        }.toString()
        return try {
            val text = withContext(Dispatchers.IO) { post(serviceUrl, body) }
            json.decodeFromString(Response.serializer(), text)
        } catch (_: Exception) {
            Response(error = "network")
        }
    }

    private fun toResult(r: Response): Result =
        if (r.ok) Result.Ok else Result.Error(message(r), if (r.error == "cooldown") r.retryAfter else null)

    private fun message(r: Response): String = when (r.error) {
        "invalid_code" -> if (r.attemptsLeft != null) {
            "That code isn't right. ${r.attemptsLeft} ${if (r.attemptsLeft == 1) "try" else "tries"} left."
        } else "That code isn't right."
        "expired" -> "That code has expired. Request a new one."
        "too_many_attempts" -> "Too many wrong tries. Request a new code."
        "cooldown" -> "Please wait a minute before asking for another code."
        "rate_limited" -> "Too many codes requested. Please wait 15 minutes."
        "daily_limit" -> "The email service has reached today's limit. Please try again tomorrow."
        "unauthenticated" -> "Your sign-in expired. Please sign in again."
        "mfa_required" -> "Please confirm it's you with an email code again (log out and sign in)."
        "not_first" -> "Your account already has a device. This phone needs to be approved."
        "not_approved_device" -> "This phone isn't an approved device on your account."
        "no_request" -> "That request no longer exists. Request a new code."
        "code_active" -> "A code for this request is already showing on another of your devices."
        "not_configured" -> "The verification service isn't set up yet (see backend/otp-service/SETUP.md)."
        "network" -> "Couldn't reach the verification service. Check your internet connection."
        else -> "Something went wrong with ${AppInfo.NAME}'s verification service. Please try again."
    }

    /**
     * Apps Script answers a POST with a redirect to a one-time result URL that must be fetched with GET.
     * Redirects are followed manually and only to Google-owned HTTPS hosts.
     */
    private fun post(url: String, body: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { it.write(body.toByteArray()) }
        val code = conn.responseCode
        if (code in 300..399) {
            val location = conn.getHeaderField("Location") ?: error("Redirect without location")
            conn.disconnect()
            val target = URL(location)
            require(target.protocol == "https" && (target.host.endsWith(".googleusercontent.com") || target.host.endsWith(".google.com"))) {
                "Unexpected redirect host"
            }
            val get = (target.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            return get.inputStream.bufferedReader().use { it.readText() }.also { get.disconnect() }
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private companion object {
        val CODE_REGEX = Regex("^\\d{6}$")
        const val TIMEOUT_MS = 20_000
    }
}
