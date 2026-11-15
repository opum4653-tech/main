package com.andorid.ninmods.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.ui.compose.MainAppScreen
import com.andorid.ninmods.ui.login.LoginActivity
import com.andorid.ninmods.ui.setup.InstallationSetupScreen
import com.andorid.ninmods.ui.theme.APatchTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
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
                val context = this@MainActivity
                val isOverlayMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)
                val isStorageMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

                var showSetup by remember {
                    mutableStateOf(!sessionManager.isEnvironmentSetup || isOverlayMissing || isStorageMissing)
                }

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
                    label = "SetupToMainTransition"
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
                        MainAppScreen(
                            viewModel = viewModel,
                            sessionManager = sessionManager,
                            onSignOut = {
                                sessionManager.logout()
                                com.andorid.ninmods.data.UserRepository.clear()
                                val intent = Intent(this@MainActivity, LoginActivity::class.java)
                                startActivity(intent)
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}
