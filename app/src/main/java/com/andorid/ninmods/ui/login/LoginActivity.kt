package com.andorid.ninmods.ui.login

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.andorid.ninmods.R
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.UserRepository
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.MainActivity
import com.andorid.ninmods.ui.setup.InstallationSetupScreen
import com.andorid.ninmods.ui.theme.APatchTheme

class LoginActivity : ComponentActivity() {

    private lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        setContent {
            APatchTheme {
                val context = this@LoginActivity
                val isOverlayMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)
                val isStorageMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

                var isVerifyingSession by remember {
                    mutableStateOf(sessionManager.isLoggedIn && sessionManager.rememberLogin && !sessionManager.authToken.isNullOrBlank())
                }

                var showSetup by remember {
                    mutableStateOf(!sessionManager.isEnvironmentSetup || isOverlayMissing || isStorageMissing)
                }

                // Strictly verify active session token with backend on startup
                LaunchedEffect(Unit) {
                    if (isVerifyingSession) {
                        val token = sessionManager.authToken ?: ""
                        if (token.isBlank()) {
                            sessionManager.logout()
                            UserRepository.clear()
                            isVerifyingSession = false
                        } else {
                            val result = AuthApiClient.getProfileWithEtag(token)
                            result.fold(
                                onSuccess = { (profile, _, _) ->
                                    if (profile != null) {
                                        sessionManager.userBalance = profile.balance
                                        sessionManager.userName = profile.username
                                        sessionManager.userRole = profile.role
                                        UserRepository.seedProfile(profile)
                                    }
                                    isVerifyingSession = false
                                    navigateToMain()
                                },
                                onFailure = { err ->
                                    val isRevoked = err.message?.let { msg ->
                                        msg.contains("401") ||
                                        msg.contains("Session expired", ignoreCase = true) ||
                                        msg.contains("SESSION_REVOKED", ignoreCase = true) ||
                                        msg.contains("Unauthorized", ignoreCase = true) ||
                                        msg.contains("token expired", ignoreCase = true) ||
                                        msg.contains("Invalid API key", ignoreCase = true)
                                    } ?: false

                                    if (isRevoked) {
                                        // Password changed or logged in from another device: wipe stale cache
                                        sessionManager.logout()
                                        UserRepository.clear()
                                        Toast.makeText(
                                            context,
                                            "Session expired: Password changed or account logged in on another device.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        // Offline / connection timeout tolerance: if user had remembered session, allow entry
                                        if (sessionManager.isLoggedIn && sessionManager.rememberLogin) {
                                            navigateToMain()
                                            return@fold
                                        }
                                    }
                                    isVerifyingSession = false
                                }
                            )
                        }
                    }
                }

                if (isVerifyingSession) {
                    // Sleek authentic splash state while verifying session
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_n),
                                contentDescription = "Logo",
                                modifier = Modifier.size(64.dp)
                            )
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                text = "Verifying secure session...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    AnimatedContent(
                        targetState = showSetup,
                        transitionSpec = {
                            if (targetState) {
                                (slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(340, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(340)))
                                    .togetherWith(slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(340, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(340)))
                            } else {
                                (slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(340, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(340)))
                                    .togetherWith(slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(340, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(340)))
                            }
                        },
                        label = "SetupToLoginTransition"
                    ) { isSetupVisible ->
                        if (isSetupVisible) {
                            InstallationSetupScreen(
                                onCompleteSetup = {
                                    sessionManager.isEnvironmentSetup = true
                                    showSetup = false
                                },
                                onSkipSetup = {
                                    sessionManager.isEnvironmentSetup = true
                                    showSetup = false
                                }
                            )
                        } else {
                            LoginScreen(
                                initialEmail = sessionManager.userEmail ?: "",
                                onLoginSuccess = { email, name, apiKey, licenseRemainingSec, role, balance, activeKey, remember ->
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

                                    if (licenseRemainingSec != null && licenseRemainingSec > 0) {
                                        sessionManager.serverRemainingSeconds = licenseRemainingSec
                                        sessionManager.licenseExpiryMillis = System.currentTimeMillis() + (licenseRemainingSec * 1000L)
                                    }

                                    Toast.makeText(this@LoginActivity, "Welcome, $name", Toast.LENGTH_SHORT).show()
                                    navigateToMain()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}
