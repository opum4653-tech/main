package com.andorid.ninmods.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.lifecycle.lifecycleScope
import com.andorid.ninmods.R
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.UserRepository
import com.andorid.ninmods.databinding.ActivityLoginBinding
import com.andorid.ninmods.ui.MainActivity
import kotlinx.coroutines.launch

/**
 * Auth host. Holds a single FragmentContainerView and swaps between the
 * login, signup and forgot-password fragments. LoginActivity stays the
 * LAUNCHER entry point.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        if (savedInstanceState == null) {
            if (hasStoredSession()) {
                verifyStoredSession()
            } else {
                showAuthScreen(AuthScreenState.LOGIN)
            }
        }
    }

    private fun hasStoredSession(): Boolean =
        sessionManager.isLoggedIn &&
            sessionManager.rememberLogin &&
            !sessionManager.authToken.isNullOrBlank()

    /**
     * Strictly validates the remembered token against the backend before the
     * user is allowed into the app. A revoked token wipes the cache and drops
     * back to the login form.
     */
    private fun verifyStoredSession() {
        val token = sessionManager.authToken ?: return showAuthScreen(AuthScreenState.LOGIN)
        binding.verifyingOverlay.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = UserRepository.getProfile(token, forceRefresh = true)
            result.fold(
                onSuccess = { profile ->
                    sessionManager.userBalance = profile.balance
                    sessionManager.userName = profile.username
                    sessionManager.userEmail = profile.email
                    sessionManager.userRole = profile.role
                    profile.activeLicense?.let { license ->
                        if (license.licenseKey.isNotBlank()) {
                            sessionManager.activeLicenseKey = license.licenseKey
                            sessionManager.serverRemainingSeconds = license.remainingSeconds
                        }
                    }
                    binding.verifyingOverlay.visibility = View.GONE
                    navigateToMain()
                },
                onFailure = { error ->
                    binding.verifyingOverlay.visibility = View.GONE
                    val message = error.message.orEmpty()
                    val revoked = message.contains("401") ||
                        message.contains("Session expired", ignoreCase = true) ||
                        message.contains("SESSION_REVOKED", ignoreCase = true) ||
                        message.contains("Unauthorized", ignoreCase = true) ||
                        message.contains("token expired", ignoreCase = true) ||
                        message.contains("Invalid API key", ignoreCase = true)

                    if (revoked) {
                        sessionManager.logout()
                        UserRepository.clear()
                        Toast.makeText(
                            this@LoginActivity,
                            R.string.error_session_expired,
                            Toast.LENGTH_LONG
                        ).show()
                        showAuthScreen(AuthScreenState.LOGIN)
                    } else if (sessionManager.isLoggedIn && sessionManager.rememberLogin) {
                        // Offline tolerance: a reachable session that the server
                        // could not be asked about still gets in.
                        navigateToMain()
                    } else {
                        showAuthScreen(AuthScreenState.LOGIN)
                    }
                }
            )
        }
    }

    fun showAuthScreen(state: AuthScreenState) {
        val tag = state.name
        if (supportFragmentManager.findFragmentByTag(tag)?.isVisible == true) return

        val fragment: Fragment = when (state) {
            AuthScreenState.LOGIN -> LoginFragment()
            AuthScreenState.SIGNUP -> SignupFragment()
            AuthScreenState.FORGOT_PASSWORD -> ForgotPasswordFragment()
        }

        supportFragmentManager.commit {
            setCustomAnimations(
                R.anim.slide_in_right,
                R.anim.fade_out,
                R.anim.fade_in,
                R.anim.slide_out_right
            )
            replace(R.id.auth_container, fragment, tag)
        }
    }

    fun onLoginSucceeded(
        email: String,
        name: String,
        apiKey: String,
        role: String,
        balance: Double,
        activeKey: String?,
        remainingSeconds: Long,
        remember: Boolean
    ) {
        sessionManager.isLoggedIn = true
        sessionManager.userEmail = email
        sessionManager.userName = name
        sessionManager.authToken = apiKey
        sessionManager.rememberLogin = remember
        sessionManager.userRole = role
        sessionManager.userBalance = balance

        if (!activeKey.isNullOrBlank()) {
            sessionManager.activeLicenseKey = activeKey
        }
        if (remainingSeconds > 0) {
            sessionManager.serverRemainingSeconds = remainingSeconds
            sessionManager.licenseExpiryMillis = System.currentTimeMillis() + remainingSeconds * 1000L
        }

        Toast.makeText(this, getString(R.string.login_welcome, name), Toast.LENGTH_SHORT).show()
        navigateToMain()
    }

    private fun navigateToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    override fun onBackPressed() {
        val current = supportFragmentManager.findFragmentById(R.id.auth_container)
        if (current is SignupFragment || current is ForgotPasswordFragment) {
            showAuthScreen(AuthScreenState.LOGIN)
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        /** Lightweight stat check used by the auth fragments before submitting. */
        fun looksLikeEmail(value: String): Boolean {
            val trimmed = value.trim()
            return trimmed.contains('@') && trimmed.substringBefore('@').isNotBlank() && trimmed.substringAfter('@').contains('.')
        }
    }
}

enum class AuthScreenState { LOGIN, SIGNUP, FORGOT_PASSWORD }
