package com.andorid.ninmods.network

import com.andorid.ninmods.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ActiveLicenseInfo(
    val licenseKey: String,
    val durationType: String,
    val expiresAt: String,
    val remainingSeconds: Long,
    val maxDevices: Int,
    val devicesUsed: Int
)

data class UserProfileData(
    val id: Int,
    val username: String,
    val email: String,
    val role: String,
    val balance: Double,
    val spent: Double,
    val currency: String,
    val status: String,
    val apiKey: String,
    val createdAt: String,
    val activeLicense: ActiveLicenseInfo?,
    val totalKeys: Int = 0,
    val activeKeys: Int = 0
)

data class AppVersionData(
    val versionName: String,
    val versionCode: Int,
    val downloadUrl: String,
    val changelog: String,
    val isForceUpdate: Boolean
)

data class AppConfigData(
    val maintenanceMode: Boolean,
    val maintenanceMessage: String,
    val brandName: String,
    val telegramSupport: String
)

data class UserTicketItem(
    val id: Int,
    val subject: String,
    val department: String = "General Support",
    val status: String = "open",
    val priority: String = "medium",
    val lastReplyAt: String? = null,
    val createdAt: String? = null
)

data class UserTicketMessageItem(
    val id: Int,
    val userId: Int? = null,
    val adminId: Int? = null,
    val message: String,
    val senderName: String = "Support Team",
    val senderType: String = "user",
    val createdAt: String? = null
)

data class ActiveNotificationItem(
    val id: Int,
    val title: String,
    val message: String,
    val imageUrl: String? = null,
    val type: String = "info",
    val isPopup: Boolean = false,
    val createdAt: String = ""
)

data class LoginResponse(
    val success: Boolean,
    val message: String,
    val userId: Int,
    val username: String,
    val email: String,
    val role: String,
    val balance: Double = 0.0,
    val currency: String = "USD",
    val apiKey: String,
    val sessionToken: String,
    val activeLicense: ActiveLicenseInfo? = null
)

data class SignUpResponse(
    val success: Boolean,
    val message: String,
    val userId: Int,
    val username: String,
    val email: String,
    val apiKey: String
)

data class BasicResponse(
    val success: Boolean,
    val message: String
)

/**
 * Robust REST API Client for Authentication & User operations.
 * Endpoints are dynamically powered by AppConfig (single .env equivalent).
 */
object AuthApiClient {

    /**
     * POST /api/v2/auth/login
     */
    suspend fun login(
        usernameOrEmail: String,
        password: String,
        hwid: String = ""
    ): Result<LoginResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("username", usernameOrEmail.trim())
                put("password", password.trim())
                if (hwid.isNotBlank()) put("hwid", hwid.trim())
            }

            val (code, responseBody) = sendPostRequest(AppConfig.LOGIN_URL, payload.toString())
            val json = parseJsonResponse(responseBody, code)

            if (code in 200..299 && (json.optBoolean("success", false) || json.optBoolean("status", false))) {
                val activeLicJson = json.optJSONObject("active_license")
                val activeLic = if (activeLicJson != null) {
                    ActiveLicenseInfo(
                        licenseKey = activeLicJson.optString("license_key", ""),
                        durationType = activeLicJson.optString("duration_type", ""),
                        expiresAt = activeLicJson.optString("expires_at", ""),
                        remainingSeconds = activeLicJson.optLong("remaining_seconds", 0L),
                        maxDevices = activeLicJson.optInt("max_devices", 1),
                        devicesUsed = activeLicJson.optInt("devices_used", 1)
                    )
                } else null

                val result = LoginResponse(
                    success = true,
                    message = json.optString("message", "Login successful"),
                    userId = json.optInt("user_id", 0),
                    username = json.optString("username", usernameOrEmail),
                    email = json.optString("email", ""),
                    role = json.optString("role", "user"),
                    balance = json.optDouble("balance", 0.0),
                    currency = json.optString("currency", "USD"),
                    apiKey = json.optString("api_key", ""),
                    sessionToken = json.optString("session_token", ""),
                    activeLicense = activeLic
                )
                Result.success(result)
            } else {
                val rawMsg = json.optString("error", json.optString("message", "Invalid login credentials (HTTP $code)"))
                val errorMsg = sanitizeErrorMessage(rawMsg, code)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * POST /api/v2/auth/signup
     * Strictly accepts: username, email, password, password_again
     */
    suspend fun signup(
        username: String,
        email: String,
        password: String,
        passwordAgain: String = password
    ): Result<SignUpResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("username", username.trim())
                put("email", email.trim())
                put("password", password.trim())
                put("password_again", passwordAgain.trim())
            }

            val (code, responseBody) = sendPostRequest(AppConfig.SIGNUP_URL, payload.toString())
            val json = parseJsonResponse(responseBody, code)

            if (code in 200..299 && (json.optBoolean("success", false) || json.optBoolean("status", false))) {
                val result = SignUpResponse(
                    success = true,
                    message = json.optString("message", "Account created successfully!"),
                    userId = json.optInt("user_id", 0),
                    username = json.optString("username", username),
                    email = json.optString("email", email),
                    apiKey = json.optString("api_key", "")
                )
                Result.success(result)
            } else {
                val rawMsg = json.optString("error", json.optString("message", "Registration failed (HTTP $code)"))
                val errorMsg = sanitizeErrorMessage(rawMsg, code)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * POST /api/v2/auth/reset-password
     */
    suspend fun resetPassword(
        usernameOrEmail: String,
        newPassword: String = ""
    ): Result<BasicResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                if (newPassword.isNotBlank()) {
                    put("username", usernameOrEmail.trim())
                    put("newPassword", newPassword.trim())
                } else {
                    put("email", usernameOrEmail.trim())
                }
            }

            val (code, responseBody) = sendPostRequest(AppConfig.RESET_PASSWORD_URL, payload.toString())
            val json = parseJsonResponse(responseBody, code)

            if (code in 200..299 && (json.optBoolean("success", false) || json.optBoolean("status", false))) {
                val msg = json.optString("message", "Password reset successful!")
                Result.success(BasicResponse(success = true, message = msg))
            } else {
                val rawMsg = json.optString("error", json.optString("message", "Password reset failed (HTTP $code)"))
                val errorMsg = sanitizeErrorMessage(rawMsg, code)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * POST /api/v2/profile/update (Authenticated password update)
     */
    suspend fun updatePassword(
        apiKey: String,
        newPassword: String
    ): Result<BasicResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("password", newPassword.trim())
            }

            val headers = mapOf(
                "Authorization" to "Bearer ${apiKey.trim()}",
                "api-key" to apiKey.trim()
            )

            val (code, responseBody) = sendPostRequest(AppConfig.PROFILE_UPDATE_URL, payload.toString(), headers)
            val json = parseJsonResponse(responseBody, code)

            if (code in 200..299 && (json.optBoolean("success", false) || json.optBoolean("status", false))) {
                val msg = json.optString("message", "Password updated successfully!")
                Result.success(BasicResponse(success = true, message = msg))
            } else {
                val errorMsg = json.optString("error", json.optString("message", "Failed to update password (HTTP $code)"))
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * GET /api/v2/profile (Fetch server-authoritative profile data with ETag support)
     */
    suspend fun getProfileWithEtag(
        apiKey: String,
        cachedEtag: String? = null
    ): Result<Triple<UserProfileData?, String?, Boolean>> = withContext(Dispatchers.IO) {
        try {
            val headers = mutableMapOf(
                "Authorization" to "Bearer ${apiKey.trim()}",
                "api-key" to apiKey.trim()
            )
            if (!cachedEtag.isNullOrBlank()) {
                headers["If-None-Match"] = cachedEtag
            }

            val response = sendGetRequest(AppConfig.PROFILE_URL, headers)
            val code = response.code

            // 304 Not Modified: Payload unchanged on server
            if (code == 304) {
                return@withContext Result.success(Triple(null, cachedEtag, true))
            }

            val json = parseJsonResponse(response.body, code)
            val isSuccess = code in 200..299 && (
                json.optBoolean("success", false) ||
                json.optString("status") == "success" ||
                json.has("profile") ||
                json.has("data")
            )

            if (isSuccess) {
                val dataObj = json.optJSONObject("data")
                val prof = json.optJSONObject("profile") 
                    ?: dataObj?.optJSONObject("profile") 
                    ?: dataObj 
                    ?: json

                val activeLicJson = prof.optJSONObject("active_license")
                val activeLic = if (activeLicJson != null) {
                    ActiveLicenseInfo(
                        licenseKey = activeLicJson.optString("license_key", ""),
                        durationType = activeLicJson.optString("duration_type", "Standard"),
                        expiresAt = activeLicJson.optString("expires_at", ""),
                        remainingSeconds = activeLicJson.optLong("remaining_seconds", 0L),
                        maxDevices = activeLicJson.optInt("max_devices", 1),
                        devicesUsed = activeLicJson.optInt("devices_used", 1)
                    )
                } else null

                val statsJson = prof.optJSONObject("license_stats")

                val data = UserProfileData(
                    id = prof.optInt("id", 0),
                    username = prof.optString("username", "User"),
                    email = prof.optString("email", ""),
                    role = prof.optString("role", "user"),
                    balance = prof.optDouble("balance", 0.0),
                    spent = prof.optDouble("spent", 0.0),
                    currency = prof.optString("currency", "USD"),
                    status = prof.optString("status", "active"),
                    apiKey = prof.optString("api_key", apiKey),
                    createdAt = prof.optString("created_at", ""),
                    activeLicense = activeLic,
                    totalKeys = statsJson?.optInt("total_keys", 0) ?: 0,
                    activeKeys = statsJson?.optInt("active_keys", 0) ?: 0
                )
                val newEtag = response.etag
                Result.success(Triple(data, newEtag, false))
            } else {
                val errorMsg = json.optString("error", json.optString("message", "Failed to fetch profile (HTTP $code)"))
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * Backward-compatible getProfile call routed via UserRepository for caching
     */
    suspend fun getProfile(apiKey: String): Result<UserProfileData> {
        return com.andorid.ninmods.data.UserRepository.getProfile(apiKey)
    }

    /**
     * GET /api/v2/app/version (Check for official software updates)
     */
    suspend fun checkAppVersion(appType: String = "loader"): Result<AppVersionData> = withContext(Dispatchers.IO) {
        try {
            val url = "${AppConfig.APP_VERSION_URL}?app_type=$appType"
            val (code, responseBody) = sendGetRequest(url)
            val json = parseJsonResponse(responseBody, code)
            if (code in 200..299 && (json.optBoolean("success", false) || json.has("version_name"))) {
                val data = AppVersionData(
                    versionName = json.optString("version_name", "1.2.8"),
                    versionCode = json.optInt("version_code", 128),
                    downloadUrl = json.optString("download_url", ""),
                    changelog = json.optString("changelog", ""),
                    isForceUpdate = json.optBoolean("is_force_update", false)
                )
                Result.success(data)
            } else {
                Result.failure(Exception(json.optString("error", "Version check failed")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * POST /api/v2/app/heartbeat (Sync license timer with server in real time)
     */
    suspend fun sendHeartbeat(
        licenseKey: String,
        hwid: String,
        sessionToken: String = ""
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("license_key", licenseKey.trim())
                put("hwid", hwid.trim())
                if (sessionToken.isNotBlank()) put("session_token", sessionToken.trim())
            }
            val (code, responseBody) = sendPostRequest(AppConfig.APP_HEARTBEAT_URL, payload.toString())
            val json = parseJsonResponse(responseBody, code)
            if (code in 200..299 && (json.optBoolean("success", false) || json.optString("status") == "active")) {
                val remSec = json.optLong("remaining_seconds", 0L)
                Result.success(remSec)
            } else {
                val err = json.optString("message", json.optString("error", "Heartbeat failed"))
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * GET /api/v2/app/config (Check maintenance mode and server announcements)
     */
    suspend fun fetchAppConfig(): Result<AppConfigData> = withContext(Dispatchers.IO) {
        try {
            val (code, responseBody) = sendGetRequest("${AppConfig.API_BASE_URL}/app/config")
            val json = parseJsonResponse(responseBody, code)
            if (code in 200..299 && json.optBoolean("success", false)) {
                val brand = json.optJSONObject("brand")
                val config = AppConfigData(
                    maintenanceMode = json.optBoolean("maintenance_mode", false),
                    maintenanceMessage = json.optString("maintenance_message", ""),
                    brandName = brand?.optString("name", "NINMODS") ?: "NINMODS",
                    telegramSupport = brand?.optString("telegram_support", "https://t.me/ninmods") ?: "https://t.me/ninmods"
                )
                Result.success(config)
            } else {
                Result.failure(Exception(json.optString("error", "Failed to retrieve configuration")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * POST /api/v2/app/bug-report (Send crash diagnostics to systems engineering)
     */
    suspend fun submitBugReport(
        licenseKey: String,
        hwid: String,
        deviceModel: String,
        kernelVersion: String,
        logcatTrace: String,
        description: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("license_key", licenseKey.trim())
                put("hwid", hwid.trim())
                put("device_model", deviceModel.trim())
                put("android_version", android.os.Build.VERSION.RELEASE)
                put("kernel_version", kernelVersion.trim())
                put("logcat_trace", logcatTrace)
                put("description", description.trim())
            }
            val (code, responseBody) = sendPostRequest("${AppConfig.API_BASE_URL}/app/bug-report", payload.toString())
            val json = parseJsonResponse(responseBody, code)
            if (code in 200..299 && json.optBoolean("success", false)) {
                val msg = json.optString("message", "Bug report received successfully.")
                Result.success(msg)
            } else {
                Result.failure(Exception(json.optString("error", "Bug report submission failed")))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * POST /api/v2/app/activate (Direct server-side license verification)
     */
    suspend fun verifyLicense(
        licenseKey: String,
        hwid: String
    ): Result<ActiveLicenseInfo> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("license_key", licenseKey.trim())
                put("hwid", hwid.trim())
            }
            val (code, responseBody) = sendPostRequest(AppConfig.APP_ACTIVATE_URL, payload.toString())
            val json = parseJsonResponse(responseBody, code)

            if (code in 200..299 && (json.optBoolean("success", false) || json.optBoolean("status", false))) {
                val data = json.optJSONObject("data") ?: json
                val info = ActiveLicenseInfo(
                    licenseKey = data.optString("license_key", licenseKey),
                    durationType = data.optString("duration_type", "standard"),
                    expiresAt = data.optString("expires_at", ""),
                    remainingSeconds = data.optLong("remaining_seconds", 0L),
                    maxDevices = data.optInt("max_devices", 1),
                    devicesUsed = data.optInt("devices_used", 1)
                )
                Result.success(info)
            } else {
                val err = json.optString("error", json.optString("message", "License validation failed"))
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(Exception(sanitizeNetworkException(e)))
        }
    }

    /**
     * Alias for activateLicense
     */
    suspend fun activateLicense(
        licenseKey: String,
        hwid: String
    ): Result<ActiveLicenseInfo> = verifyLicense(licenseKey, hwid)

    /**
     * Parse server response body into JSON, stripping any Cloudflare / Nginx / Apache HTML error pages.
     * Prevents raw HTML tags from ever leaking onto the UI.
     */
    fun parseJsonResponse(body: String, statusCode: Int): JSONObject {
        val trimmed = body.trim()
        if (trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE", ignoreCase = true) || trimmed.contains("<html", ignoreCase = true)) {
            val friendlyMsg = when (statusCode) {
                502, 503, 504 -> "Server is temporarily offline for maintenance. Please try again shortly."
                500 -> "Server encountered an internal error. Please try again later."
                403 -> "Server access denied by security gateway."
                404 -> "Requested endpoint was not found on server."
                else -> "Server returned an invalid HTML response (HTTP $statusCode)."
            }
            return JSONObject().apply {
                put("success", false)
                put("status", false)
                put("error", friendlyMsg)
            }
        }
        return try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            JSONObject().apply {
                put("success", false)
                put("status", false)
                put("error", "Unexpected server response format.")
            }
        }
    }

    fun sanitizeErrorMessage(rawError: String?, httpCode: Int = 200): String {
        val msg = rawError?.trim() ?: ""
        return when {
            msg.isBlank() -> if (httpCode >= 500) "Server is temporarily unavailable. Please try again later." else "Request failed. Please try again."
            // Database / SQL / Backend internal errors
            msg.contains("doesn't exist", ignoreCase = true) ||
            msg.contains("does not exist", ignoreCase = true) ||
            msg.contains("table", ignoreCase = true) ||
            msg.contains("smmtor", ignoreCase = true) ||
            msg.contains("mysql", ignoreCase = true) ||
            msg.contains("mariadb", ignoreCase = true) ||
            msg.contains("SQLSTATE", ignoreCase = true) || 
            msg.contains("PDOException", ignoreCase = true) || 
            msg.contains("database", ignoreCase = true) ||
            msg.contains("syntax error", ignoreCase = true) ||
            msg.contains("Access denied", ignoreCase = true) ||
            msg.contains("Unknown column", ignoreCase = true) ||
            msg.contains("column", ignoreCase = true) ||
            msg.contains("query", ignoreCase = true) ||
            msg.contains("fatal", ignoreCase = true) ||
            msg.contains("exception", ignoreCase = true) ||
            msg.contains("ECONNREFUSED", ignoreCase = true) ||
            msg.contains("Internal Server Error", ignoreCase = true) ||
            httpCode in 500..599 ->
                "Server is currently under maintenance. Please try again shortly."
            // Network / Connection issues
            msg.contains("Unable to resolve host", ignoreCase = true) || msg.contains("Cannot resolve server", ignoreCase = true) ->
                "Server unreachable. Please check your internet connection."
            msg.contains("failed to connect", ignoreCase = true) || msg.contains("Connection refused", ignoreCase = true) ->
                "Unable to connect to backend server. Server may be offline."
            msg.contains("timed out", ignoreCase = true) || msg.contains("SocketTimeout", ignoreCase = true) ->
                "Server connection timed out. Please try again."
            // Authentication credentials
            msg.contains("Invalid login credentials", ignoreCase = true) ||
            msg.contains("Invalid credentials", ignoreCase = true) || 
            msg.contains("Invalid username or password", ignoreCase = true) || 
            msg.contains("Invalid email or password", ignoreCase = true) ||
            msg.contains("Password incorrect", ignoreCase = true) ||
            msg.contains("User not found", ignoreCase = true) ->
                "Invalid email or password."
            msg.contains("Account suspended", ignoreCase = true) || msg.contains("banned", ignoreCase = true) ->
                "Your account is suspended. Please contact support."
            else -> msg
        }
    }

    fun sanitizeNetworkException(e: Exception): String {
        val msg = e.message ?: ""
        return sanitizeErrorMessage(msg, 0)
    }

    /**
     * GET /api/v2/notifications/active
     */
    suspend fun getActiveNotifications(): Result<List<ActiveNotificationItem>> = withContext(Dispatchers.IO) {
        try {
            val response = sendGetRequest(AppConfig.NOTIFICATIONS_ACTIVE_URL)
            if (response.code in 200..299) {
                val json = JSONObject(response.body)
                val arr = json.optJSONArray("notifications")
                val list = mutableListOf<ActiveNotificationItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            ActiveNotificationItem(
                                id = item.optInt("id", 0),
                                title = item.optString("title", ""),
                                message = item.optString("message", ""),
                                imageUrl = item.optString("image_url").takeIf { it.isNotBlank() && it != "null" },
                                type = item.optString("type", "info"),
                                isPopup = item.optInt("is_popup", 0) == 1 || item.optBoolean("is_popup", false),
                                createdAt = item.optString("created_at", "")
                            )
                        )
                    }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to fetch notifications: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST /api/v2/app/bug-report
     */
    suspend fun submitBugReport(
        title: String,
        description: String,
        deviceModel: String,
        androidVersion: String,
        appVersion: String,
        kernelVersion: String,
        isRoot: Boolean,
        licenseKey: String? = null,
        hwid: String? = null,
        logcatTrace: String? = null,
        userId: Int? = null
    ): Result<BasicResponse> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("title", title.trim())
                put("description", description.trim())
                put("device_model", deviceModel)
                put("android_version", androidVersion)
                put("app_version", appVersion)
                put("kernel_version", kernelVersion)
                put("is_root", if (isRoot) 1 else 0)
                if (!licenseKey.isNullOrBlank()) put("license_key", licenseKey)
                if (!hwid.isNullOrBlank()) put("hwid", hwid)
                if (!logcatTrace.isNullOrBlank()) put("logcat_trace", logcatTrace)
                if (userId != null && userId > 0) put("user_id", userId)
            }

            val (code, body) = sendPostRequest(AppConfig.BUG_REPORT_URL, payload.toString())
            if (code in 200..299) {
                val json = JSONObject(body)
                Result.success(
                    BasicResponse(
                        success = json.optBoolean("success", true),
                        message = json.optString("message", "Bug report received successfully.")
                    )
                )
            } else {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val errMsg = json?.optString("error") ?: "Server returned error ($code)"
                Result.failure(Exception(errMsg))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GET /api/v2/support/tickets (Fetch my support tickets)
     */
    suspend fun fetchMyTickets(apiKey: String): Result<List<UserTicketItem>> = withContext(Dispatchers.IO) {
        try {
            val headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}", "api-key" to apiKey.trim())
            val response = sendGetRequest(AppConfig.SUPPORT_TICKETS_URL, headers)
            if (response.code in 200..299) {
                val json = JSONObject(response.body)
                val arr = json.optJSONArray("tickets")
                val list = mutableListOf<UserTicketItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            UserTicketItem(
                                id = item.optInt("id", 0),
                                subject = item.optString("subject", ""),
                                department = item.optString("department", "General Support"),
                                status = item.optString("status", "open"),
                                priority = item.optString("priority", "medium"),
                                lastReplyAt = item.optString("last_reply_at").takeIf { it.isNotBlank() && it != "null" },
                                createdAt = item.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                            )
                        )
                    }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to load tickets: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST /api/v2/support/tickets (Submit a new support ticket)
     */
    suspend fun createMyTicket(
        apiKey: String,
        subject: String,
        message: String,
        priority: String = "medium"
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("subject", subject.trim())
                put("message", message.trim())
                put("priority", priority.trim())
                put("department", "General Support")
            }
            val headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}", "api-key" to apiKey.trim())
            val (code, body) = sendPostRequest(AppConfig.SUPPORT_TICKETS_URL, payload.toString(), headers)
            if (code in 200..299) {
                val json = JSONObject(body)
                val ticketId = json.optInt("ticket_id", 0)
                Result.success(ticketId)
            } else {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val err = json?.optString("error") ?: "Server returned HTTP $code"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * GET /api/v2/support/tickets/:id (Fetch conversation thread)
     */
    suspend fun fetchTicketMessages(apiKey: String, ticketId: Int): Result<List<UserTicketMessageItem>> = withContext(Dispatchers.IO) {
        try {
            val headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}", "api-key" to apiKey.trim())
            val response = sendGetRequest("${AppConfig.SUPPORT_TICKETS_URL}/$ticketId", headers)
            if (response.code in 200..299) {
                val json = JSONObject(response.body)
                val arr = json.optJSONArray("messages")
                val list = mutableListOf<UserTicketMessageItem>()
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        list.add(
                            UserTicketMessageItem(
                                id = item.optInt("id", 0),
                                userId = if (item.isNull("user_id")) null else item.optInt("user_id"),
                                adminId = if (item.isNull("admin_id")) null else item.optInt("admin_id"),
                                message = item.optString("message", ""),
                                senderName = item.optString("sender_name", "Support Team"),
                                senderType = item.optString("sender_type", "user"),
                                createdAt = item.optString("created_at").takeIf { it.isNotBlank() && it != "null" }
                            )
                        )
                    }
                }
                Result.success(list)
            } else {
                Result.failure(Exception("Failed to load messages: HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * POST /api/v2/support/tickets/:id/reply (Reply to conversation thread)
     */
    suspend fun replyTicketMessage(apiKey: String, ticketId: Int, message: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val payload = JSONObject().apply {
                put("message", message.trim())
            }
            val headers = mapOf("Authorization" to "Bearer ${apiKey.trim()}", "api-key" to apiKey.trim())
            val (code, body) = sendPostRequest("${AppConfig.SUPPORT_TICKETS_URL}/$ticketId/reply", payload.toString(), headers)
            if (code in 200..299) {
                Result.success(true)
            } else {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val err = json?.optString("error") ?: "Server returned HTTP $code"
                Result.failure(Exception(err))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun sendPostRequest(
        urlString: String,
        jsonBody: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<Int, String> {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = AppConfig.TIMEOUT_MILLIS
        conn.readTimeout = AppConfig.TIMEOUT_MILLIS
        conn.doInput = true
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        conn.setRequestProperty("Accept", "application/json")

        for ((k, v) in extraHeaders) {
            conn.setRequestProperty(k, v)
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
            writer.write(jsonBody)
            writer.flush()
        }

        val code = conn.responseCode
        val inputStream = if (code in 200..299) conn.inputStream else conn.errorStream

        val responseBody = if (inputStream != null) {
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                reader.readText()
            }
        } else {
            "{}"
        }

        conn.disconnect()
        return Pair(code, responseBody)
    }

    data class HttpResponse(
        val code: Int,
        val body: String,
        val headers: Map<String, String> = emptyMap()
    ) {
        val etag: String? get() = headers["etag"]
    }

    private fun sendGetRequest(
        urlString: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): HttpResponse {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = AppConfig.TIMEOUT_MILLIS
        conn.readTimeout = AppConfig.TIMEOUT_MILLIS
        conn.doInput = true
        conn.setRequestProperty("Accept", "application/json")

        for ((k, v) in extraHeaders) {
            conn.setRequestProperty(k, v)
        }

        val code = conn.responseCode
        val headerMap = mutableMapOf<String, String>()
        for ((k, v) in conn.headerFields) {
            if (k != null && v.isNotEmpty()) {
                headerMap[k.lowercase()] = v[0]
            }
        }

        val inputStream = if (code in 200..299) conn.inputStream else conn.errorStream

        val responseBody = if (inputStream != null) {
            BufferedReader(InputStreamReader(inputStream, "UTF-8")).use { reader ->
                reader.readText()
            }
        } else {
            "{}"
        }

        conn.disconnect()
        return HttpResponse(code, responseBody, headerMap)
    }
}
