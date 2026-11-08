package com.andorid.ninmods.ui.compose

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.network.ActiveNotificationItem
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.MainViewModel
import com.andorid.ninmods.ui.component.InAppBroadcastModalDialog
import com.andorid.ninmods.ui.dashboard.DashboardScreen
import com.andorid.ninmods.ui.loader.DriverLoaderScreen
import com.andorid.ninmods.ui.profile.ProfileScreen
import com.andorid.ninmods.ui.settings.SettingsScreen

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size
import com.andorid.ninmods.R

enum class MainDestination(
    @StringRes val labelRes: Int,
    @DrawableRes val selectedDrawable: Int,
    @DrawableRes val unselectedDrawable: Int
) {
    DASHBOARD(
        labelRes = R.string.home,
        selectedDrawable = R.drawable.ic_nav_home_fill,
        unselectedDrawable = R.drawable.ic_nav_home
    ),
    LOADER(
        labelRes = R.string.nav_drivers,
        selectedDrawable = R.drawable.ic_nav_wrench,
        unselectedDrawable = R.drawable.ic_nav_wrench
    ),
    SETTINGS(
        labelRes = R.string.settings,
        selectedDrawable = R.drawable.ic_nav_gear,
        unselectedDrawable = R.drawable.ic_nav_gear
    ),
    PROFILE(
        labelRes = R.string.about,
        selectedDrawable = R.drawable.ic_user_profile,
        unselectedDrawable = R.drawable.ic_user_profile
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    sessionManager: SessionManager,
    onSignOut: () -> Unit
) {
    var currentDestination by remember { mutableStateOf(MainDestination.LOADER) }
    var previousDestination by remember { mutableStateOf(MainDestination.LOADER) }
    var lastBackPressTime by remember { mutableStateOf(0L) }
    val context = LocalContext.current

    var activeNotifications by remember { mutableStateOf<List<ActiveNotificationItem>>(emptyList()) }
    var popupNotificationToShow by remember { mutableStateOf<ActiveNotificationItem?>(null) }
    var dismissedNotificationIds by remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(Unit) {
        AuthApiClient.getActiveNotifications().onSuccess { list ->
            activeNotifications = list
            val popup = list.firstOrNull { it.isPopup }
            if (popup != null) {
                popupNotificationToShow = popup
            }
        }
    }

    // APatch Official BackHandler: Subpages/Tabs navigate back to Home; double back on Home to exit
    BackHandler(enabled = true) {
        when {
            currentDestination == MainDestination.PROFILE -> {
                currentDestination = previousDestination
            }
            currentDestination != MainDestination.LOADER -> {
                currentDestination = MainDestination.LOADER
            }
            else -> {
                val now = System.currentTimeMillis()
                if (now - lastBackPressTime < 2000L) {
                    (context as? Activity)?.finish()
                } else {
                    lastBackPressTime = now
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val bottomBarDestinations = listOf(
        MainDestination.DASHBOARD,
        MainDestination.LOADER,
        MainDestination.SETTINGS
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = currentDestination.labelRes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    if (currentDestination == MainDestination.PROFILE) {
                        IconButton(onClick = { currentDestination = previousDestination }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    if (currentDestination != MainDestination.PROFILE) {
                        IconButton(onClick = {
                            previousDestination = currentDestination
                            currentDestination = MainDestination.PROFILE
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_user_profile),
                                contentDescription = "Profile",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (currentDestination in bottomBarDestinations) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    tonalElevation = 8.dp
                ) {
                    NavigationBar(
                        modifier = Modifier.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
                        tonalElevation = 0.dp,
                        containerColor = Color.Transparent
                    ) {
                        bottomBarDestinations.forEach { dest ->
                            val isSelected = currentDestination == dest
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { currentDestination = dest },
                                icon = {
                                    Icon(
                                        painter = painterResource(id = if (isSelected) dest.selectedDrawable else dest.unselectedDrawable),
                                        contentDescription = stringResource(id = dest.labelRes),
                                        modifier = Modifier.size(24.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(id = dest.labelRes),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                },
                                alwaysShowLabel = false
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        // APatch Official Screen Transitions (tween 340ms, horizontal slide + fade for detail screens, fade for bottom tabs)
        AnimatedContent(
            targetState = currentDestination,
            transitionSpec = {
                if (targetState == MainDestination.PROFILE) {
                    // Navigate to detail page: slide in from right + fade in; previous slides out slightly to left + fades out
                    (slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = tween(340)
                    ) + fadeIn(animationSpec = tween(340))) togetherWith
                    (slideOutHorizontally(
                        targetOffsetX = { -it / 4 },
                        animationSpec = tween(340)
                    ) + fadeOut(animationSpec = tween(340)))
                } else if (initialState == MainDestination.PROFILE) {
                    // Returning from detail page: previous slides in from left; detail scales down + fades out
                    (slideInHorizontally(
                        initialOffsetX = { -it / 4 },
                        animationSpec = tween(340)
                    ) + fadeIn(animationSpec = tween(340))) togetherWith
                    (scaleOut(
                        targetScale = 0.9f,
                        animationSpec = tween(340)
                    ) + fadeOut(animationSpec = tween(340)))
                } else {
                    // Switching between bottom navigation tabs (Dashboard, Loader, Settings): APatch 340ms fade
                    fadeIn(animationSpec = tween(340)) togetherWith fadeOut(animationSpec = tween(340))
                }
            },
            label = "APatchDestinationTransition",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) { destination ->
            when (destination) {
                MainDestination.DASHBOARD -> DashboardScreen(
                    sessionManager = sessionManager,
                    activeBroadcast = activeNotifications.firstOrNull { it.id !in dismissedNotificationIds },
                    onDismissBroadcast = {
                        val active = activeNotifications.firstOrNull { it.id !in dismissedNotificationIds }
                        if (active != null) {
                            dismissedNotificationIds = dismissedNotificationIds + active.id
                        }
                    }
                )
                MainDestination.LOADER -> DriverLoaderScreen(
                    viewModel = viewModel,
                    sessionManager = sessionManager
                )
                MainDestination.SETTINGS -> SettingsScreen(
                    sessionManager = sessionManager,
                    onSignOut = onSignOut
                )
                MainDestination.PROFILE -> ProfileScreen(
                    sessionManager = sessionManager,
                    onSignOut = onSignOut
                )
            }
        }
    }

    popupNotificationToShow?.let { popup ->
        InAppBroadcastModalDialog(
            notification = popup,
            onDismiss = {
                dismissedNotificationIds = dismissedNotificationIds + popup.id
                popupNotificationToShow = null
            }
        )
    }
}
