package com.andorid.ninmods.data

import android.content.Context
import android.content.SharedPreferences

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "ninmods_auth_session"
        private const val KEY_IS_SETUP = "is_environment_setup"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_EMAIL = "user_email"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_PASSWORD = "user_password"
        private const val KEY_REMEMBER = "remember_login"
        private const val KEY_LICENSE_EXPIRY = "license_expiry_time"
    }

    var licenseExpiryMillis: Long
        get() = prefs.getLong(KEY_LICENSE_EXPIRY, 0L)
        set(value) = prefs.edit().putLong(KEY_LICENSE_EXPIRY, value).apply()

    fun clearLicense() {
        prefs.edit()
            .remove(KEY_LICENSE_EXPIRY)
            .remove("server_remaining_sec")
            .remove("sync_uptime_ms")
            .remove("sync_wall_time_ms")
            .remove("active_license_key")
            .apply()
    }

    /**
     * Server-Authoritative License Duration (Immune to Device Clock Tampering).
     * Uses SystemClock.elapsedRealtime() which is monotonic and cannot be modified by user settings.
     */
    var serverRemainingSeconds: Long
        get() = prefs.getLong("server_remaining_sec", 0L)
        set(value) {
            val nowWall = System.currentTimeMillis()
            val nowUptime = android.os.SystemClock.elapsedRealtime()
            prefs.edit()
                .putLong("server_remaining_sec", value)
                .putLong("sync_uptime_ms", nowUptime)
                .putLong("sync_wall_time_ms", nowWall)
                .putLong("sync_boot_time_ms", nowWall - nowUptime)
                .apply()
        }

    val liveRemainingSeconds: Long
        get() {
            val base = serverRemainingSeconds
            if (base <= 0L) return 0L
            val syncUptime = prefs.getLong("sync_uptime_ms", 0L)
            if (syncUptime == 0L) return base

            val currentUptime = android.os.SystemClock.elapsedRealtime()
            val syncWall = prefs.getLong("sync_wall_time_ms", 0L)
            val syncBootTime = prefs.getLong("sync_boot_time_ms", 0L)
            val currentBootTime = System.currentTimeMillis() - currentUptime
            val hasRebooted = (syncBootTime > 0L && kotlin.math.abs(currentBootTime - syncBootTime) > 5000L) || (currentUptime < syncUptime)

            val elapsedSec: Long = if (!hasRebooted) {
                // Monotonic counter: Immune to user date/time adjustments
                (currentUptime - syncUptime) / 1000L
            } else {
                // Device reboot detected: fallback to wall-clock delta
                if (syncWall > 0L) {
                    val wallDelta = (System.currentTimeMillis() - syncWall) / 1000L
                    kotlin.math.max(0L, wallDelta)
                } else {
                    0L
                }
            }
            return kotlin.math.max(0L, base - elapsedSec)
        }

    var globalNamespace: Boolean
        get() = prefs.getBoolean("global_namespace", true)
        set(value) = prefs.edit().putBoolean("global_namespace", value).apply()

    var legacySu: Boolean
        get() = prefs.getBoolean("legacy_su", false)
        set(value) = prefs.edit().putBoolean("legacy_su", value).apply()

    var isEnvironmentSetup: Boolean
        get() = prefs.getBoolean(KEY_IS_SETUP, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_SETUP, value).apply()

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    var authToken: String?
        get() = prefs.getString(KEY_AUTH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_AUTH_TOKEN, value).apply()

    var userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, "")
        set(value) = prefs.edit().putString(KEY_USER_EMAIL, value).apply()

    var userName: String?
        get() = prefs.getString(KEY_USER_NAME, "")
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var userRole: String
        get() = prefs.getString("user_role", "User") ?: "User"
        set(value) = prefs.edit().putString("user_role", value).apply()

    var userBalance: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("user_balance", java.lang.Double.doubleToLongBits(0.0)))
        set(value) = prefs.edit().putLong("user_balance", java.lang.Double.doubleToLongBits(value)).apply()

    var userSpent: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong("user_spent", java.lang.Double.doubleToLongBits(0.0)))
        set(value) = prefs.edit().putLong("user_spent", java.lang.Double.doubleToLongBits(value)).apply()

    var activeLicenseKey: String?
        get() = prefs.getString("active_license_key", null)
        set(value) = prefs.edit().putString("active_license_key", value).apply()

    var rememberLogin: Boolean
        get() = prefs.getBoolean(KEY_REMEMBER, true)
        set(value) = prefs.edit().putBoolean(KEY_REMEMBER, value).apply()

    var userId: Int
        get() = prefs.getInt("user_id", 0)
        set(value) = prefs.edit().putInt("user_id", value).apply()

    fun logout() {
        // Keep environment setup status, clear all active user session tokens
        val isSetup = isEnvironmentSetup
        val remember = rememberLogin
        val savedEmail = if (remember) userEmail else ""
        prefs.edit().clear().apply()
        isEnvironmentSetup = isSetup
        if (remember && !savedEmail.isNullOrBlank()) {
            userEmail = savedEmail
            rememberLogin = true
        }
    }
}
