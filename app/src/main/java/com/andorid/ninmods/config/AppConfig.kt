package com.andorid.ninmods.config

/**
 * NINMODS APP CONFIGURATION
 * Change BASE_URL here to switch backend domain.
 *
 * All network endpoints across Login, SignUp, Reset Password, Profile, and
 * License verification derive strictly from this single configuration file.
 */
object AppConfig {

    /**
     * PRIMARY SERVER DOMAIN / HOST
     * Change this single line to point the entire app to any domain or server:
     * - Production:  "https://ninmods.smmtor.com"
     * - Custom:      "https://yourdomain.com"
     * - Localhost:   "http://10.0.2.2:3000" (Android Emulator)
     */
    const val BASE_URL: String = "https://ninmods.smmtor.com"

    const val VERSION_NAME: String = "v1.4.5"
    const val VERSION_CODE: Int = 145

    /**
     * API Version route prefix matching app_backend routes
     */
    const val API_PREFIX: String = "/api/v2"

    /**
     * Full API Base URL
     */
    val API_BASE_URL: String
        get() = "${BASE_URL.trimEnd('/')}$API_PREFIX"

    // Authentication Endpoints (routes/api.js)
    val LOGIN_URL: String get() = "$API_BASE_URL/auth/login"
    val SIGNUP_URL: String get() = "$API_BASE_URL/auth/signup"
    val RESET_PASSWORD_URL: String get() = "$API_BASE_URL/auth/reset-password"

    // Profile & User Endpoints
    val PROFILE_URL: String get() = "$API_BASE_URL/profile"
    val PROFILE_UPDATE_URL: String get() = "$API_BASE_URL/profile/update"

    // License & App Endpoints
    val APP_ACTIVATE_URL: String get() = "$API_BASE_URL/app/activate"
    val APP_HEARTBEAT_URL: String get() = "$API_BASE_URL/app/heartbeat"
    val APP_VERSION_URL: String get() = "$API_BASE_URL/app/version"

    // Connection Timeouts
    const val TIMEOUT_MILLIS: Int = 15000
}
