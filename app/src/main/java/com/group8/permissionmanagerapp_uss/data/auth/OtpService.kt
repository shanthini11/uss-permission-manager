package com.group8.permissionmanagerapp_uss.data.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the free email-code service (backend/otp-service/Code.gs on Google Apps Script).
 * The code is generated, emailed and checked on the server; the app never sees it except as user input.
 */
class OtpService(
    private val serviceUrl: String,
    private val auth: AuthRepository,
) {
    sealed interface Result {
        data object Ok : Result
        data class Error(val message: String, val retryAfterSeconds: Int? = null) : Result
    }

    @Serializable
    private data class Response(
        val ok: Boolean = false,
        val error: String? = null,
        val retryAfter: Int? = null,
        val attemptsLeft: Int? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    val isConfigured: Boolean get() = serviceUrl.startsWith("https://script.google.com/")

    suspend fun sendCode(): Result = call("send", null)

    suspend fun verifyCode(code: String): Result {
        if (!CODE_REGEX.matches(code)) return Result.Error("Enter the 6-digit code from the email.")
        return call("verify", code)
    }

    private suspend fun call(action: String, code: String?): Result {
        if (!isConfigured) {
            return Result.Error("The email code service isn't set up yet (see backend/otp-service/SETUP.md).")
        }
        val token = try {
            auth.idToken()
        } catch (e: AuthException) {
            return Result.Error(e.message ?: "Please sign in again.")
        }
        val body = buildString {
            append("{\"action\":\"").append(action).append("\",\"idToken\":\"").append(token).append('"')
            if (code != null) append(",\"code\":\"").append(code).append('"')
            append('}')
        }
        return try {
            val text = withContext(Dispatchers.IO) { post(serviceUrl, body) }
            toResult(json.decodeFromString(Response.serializer(), text))
        } catch (_: Exception) {
            Result.Error("Couldn't reach the verification service. Check your internet connection.")
        }
    }

    private fun toResult(r: Response): Result = when {
        r.ok -> Result.Ok
        r.error == "invalid_code" && r.attemptsLeft != null ->
            Result.Error("That code isn't right. ${r.attemptsLeft} ${if (r.attemptsLeft == 1) "try" else "tries"} left.")
        r.error == "invalid_code" -> Result.Error("That code isn't right.")
        r.error == "expired" -> Result.Error("That code has expired. Tap \"Resend code\" to get a new one.")
        r.error == "too_many_attempts" -> Result.Error("Too many wrong tries. Tap \"Resend code\" to get a new one.")
        r.error == "cooldown" -> Result.Error("Please wait a minute before asking for another code.", r.retryAfter)
        r.error == "rate_limited" -> Result.Error("Too many codes requested. Please wait 15 minutes.")
        r.error == "daily_limit" -> Result.Error("The email service has reached today's limit. Please try again tomorrow.")
        r.error == "unauthenticated" -> Result.Error("Your sign-in expired. Please sign in again.")
        else -> Result.Error("Something went wrong. Please try again.")
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
            val host = URL(location).host
            require(URL(location).protocol == "https" && (host.endsWith(".googleusercontent.com") || host.endsWith(".google.com"))) {
                "Unexpected redirect host"
            }
            val get = (URL(location).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            return get.inputStream.bufferedReader().use { it.readText() }.also { get.disconnect() }
        }
        return conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    }

    private companion object {
        val CODE_REGEX = Regex("^\\d{6}$")
        const val TIMEOUT_MS = 15_000
    }
}
