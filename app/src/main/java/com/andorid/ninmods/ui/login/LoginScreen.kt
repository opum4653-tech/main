package com.andorid.ninmods.ui.login

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.R
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.forgotpassword.ForgotPasswordScreen
import com.andorid.ninmods.ui.signup.SignUpScreen
import com.andorid.ninmods.ui.theme.APatchTheme
import kotlinx.coroutines.launch

enum class AuthScreenState {
    LOGIN,
    SIGNUP,
    FORGOT_PASSWORD
}

@Composable
fun LoginScreen(
    initialEmail: String = "",
    onLoginSuccess: (
        email: String,
        name: String,
        apiKey: String,
        licenseRemainingSec: Long?,
        role: String,
        balance: Double,
        activeKey: String?,
        remember: Boolean
    ) -> Unit
) {
    var authState by remember { mutableStateOf(AuthScreenState.LOGIN) }

    // If on SignUp or ForgotPassword, hardware/gesture back press navigates back to Login screen
    BackHandler(enabled = authState != AuthScreenState.LOGIN) {
        authState = AuthScreenState.LOGIN
    }

    APatchTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            // APatch Official Motion: Horizontal slide + fade into subpages (SignUp/ForgotPassword), scaleOut on pop back
            AnimatedContent(
                targetState = authState,
                transitionSpec = {
                    if (targetState == AuthScreenState.SIGNUP || targetState == AuthScreenState.FORGOT_PASSWORD) {
                        (slideInHorizontally(
                            initialOffsetX = { it },
                            animationSpec = tween(340)
                        ) + fadeIn(animationSpec = tween(340))) togetherWith
                        (slideOutHorizontally(
                            targetOffsetX = { -it / 4 },
                            animationSpec = tween(340)
                        ) + fadeOut(animationSpec = tween(340)))
                    } else {
                        (slideInHorizontally(
                            initialOffsetX = { -it / 4 },
                            animationSpec = tween(340)
                        ) + fadeIn(animationSpec = tween(340))) togetherWith
                        (scaleOut(
                            targetScale = 0.9f,
                            animationSpec = tween(340)
                        ) + fadeOut(animationSpec = tween(340)))
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                label = "AuthAPatchTransition"
            ) { state ->
                when (state) {
                    AuthScreenState.LOGIN -> {
                        LoginContent(
                            initialEmail = initialEmail,
                            onSuccess = onLoginSuccess,
                            onNavigateToSignUp = { authState = AuthScreenState.SIGNUP },
                            onNavigateToForgotPassword = { authState = AuthScreenState.FORGOT_PASSWORD }
                        )
                    }
                    AuthScreenState.SIGNUP -> {
                        SignUpScreen(
                            onNavigateToLogin = { authState = AuthScreenState.LOGIN }
                        )
                    }
                    AuthScreenState.FORGOT_PASSWORD -> {
                        ForgotPasswordScreen(
                            onNavigateToLogin = { authState = AuthScreenState.LOGIN }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginContent(
    initialEmail: String = "",
    onSuccess: (
        email: String,
        name: String,
        apiKey: String,
        licenseRemainingSec: Long?,
        role: String,
        balance: Double,
        activeKey: String?,
        remember: Boolean
    ) -> Unit,
    onNavigateToSignUp: () -> Unit,
    onNavigateToForgotPassword: () -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var rememberMe by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    val performLogin: () -> Unit = {
        focusManager.clearFocus()
        if (email.trim().isEmpty() || password.trim().isEmpty()) {
            errorMessage = "Please enter email and password"
        } else {
            isLoading = true
            errorMessage = null
            scope.launch {
                val hwid = com.andorid.ninmods.util.DeviceIdentifier.getHwid(context)
                val result = AuthApiClient.login(email.trim(), password.trim(), hwid)
                isLoading = false
                if (result.isSuccess) {
                    val auth = result.getOrThrow()
                    val initialProfile = com.andorid.ninmods.network.UserProfileData(
                        id = auth.userId,
                        username = auth.username,
                        email = auth.email,
                        role = auth.role,
                        balance = auth.balance,
                        spent = 0.0,
                        currency = auth.currency,
                        status = "active",
                        apiKey = auth.apiKey,
                        createdAt = "",
                        activeLicense = auth.activeLicense
                    )
                    com.andorid.ninmods.data.UserRepository.seedProfile(initialProfile)

                    onSuccess(
                        auth.email.ifBlank { email.trim() },
                        auth.username.ifBlank { email.substringBefore("@") },
                        auth.apiKey,
                        auth.activeLicense?.remainingSeconds,
                        auth.role,
                        auth.balance,
                        auth.activeLicense?.licenseKey,
                        rememberMe
                    )
                } else {
                    errorMessage = AuthApiClient.sanitizeErrorMessage(result.exceptionOrNull()?.message)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(76.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo_n),
                contentDescription = "Logo",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "NinMods",
            style = MaterialTheme.typography.titleLarge,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Email Field
        TextField(
            value = email,
            onValueChange = {
                email = it
                if (errorMessage != null) errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email") },
            placeholder = { Text("Email") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = "Email",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password Field
        TextField(
            value = password,
            onValueChange = {
                password = it
                if (errorMessage != null) errorMessage = null
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            placeholder = { Text("••••••••") },
            singleLine = true,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Password",
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(imageVector = icon, contentDescription = "Toggle Visibility")
                }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    performLogin()
                }
            ),
            shape = RoundedCornerShape(20.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // Remember Me (Left) & Forgot Password (Right) Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "Remember",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(onClick = onNavigateToForgotPassword) {
                Text(
                    text = "Forgot password?",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage ?: "",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(start = 8.dp, bottom = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Login Button
        Button(
            onClick = { performLogin() },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Logging in...",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(
                        text = "Login",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Sign Up Navigation Link
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Don't have an account?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            TextButton(onClick = onNavigateToSignUp) {
                Text(
                    text = "Sign Up",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
