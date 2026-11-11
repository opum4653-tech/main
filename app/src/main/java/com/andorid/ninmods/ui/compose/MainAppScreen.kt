package com.andorid.ninmods.ui.compose

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.andorid.ninmods.R
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.network.ActiveNotificationItem
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.network.UserTicketItem
import com.andorid.ninmods.ui.MainViewModel
import com.andorid.ninmods.ui.dashboard.DashboardScreen
import com.andorid.ninmods.ui.denylist.DenyListScreen
import com.andorid.ninmods.ui.deposit.AddFundsScreen
import com.andorid.ninmods.ui.loader.DriverLoaderScreen
import com.andorid.ninmods.ui.profile.ProfileScreen
import com.andorid.ninmods.ui.settings.SettingsScreen
import com.andorid.ninmods.ui.support.CreateTicketScreen
import com.andorid.ninmods.ui.support.UserSupportScreen
import com.andorid.ninmods.ui.support.UserTicketChatScreen
import kotlinx.coroutines.launch

enum class MainDestination(
    val title: String,
    val icon: ImageVector
) {
    DASHBOARD("Dashboard", Icons.Filled.Dashboard),
    SUPPORT("Live Support", Icons.Filled.SupportAgent),
    LOADER("Driver Loader", Icons.Filled.Build),
    DENYLIST("Daily List", Icons.Filled.Security),
    SETTINGS("General Settings", Icons.Filled.Settings),
    ADD_FUNDS("Add Funds", Icons.Filled.AccountBalanceWallet),
    CREATE_TICKET("Create Ticket", Icons.Filled.Add),
    TICKET_CHAT("Live Conversation", Icons.AutoMirrored.Filled.Chat),
    PROFILE("My Profile", Icons.Filled.AccountCircle)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(
    viewModel: MainViewModel,
    sessionManager: SessionManager,
    onSignOut: () -> Unit
) {
    var currentDestination by remember { mutableStateOf(MainDestination.DASHBOARD) }
    var selectedTicket by remember { mutableStateOf<UserTicketItem?>(null) }
    var lastBackPressTime by remember { mutableStateOf(0L) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var activeNotifications by remember { mutableStateOf<List<ActiveNotificationItem>>(emptyList()) }
    var popupNotificationToShow by remember { mutableStateOf<ActiveNotificationItem?>(null) }

    // Live sync user balance from profile API on startup
    LaunchedEffect(Unit) {
        val token = sessionManager.authToken ?: ""
        if (token.isNotBlank()) {
            AuthApiClient.getProfileWithEtag(token).onSuccess { (profileData, _, _) ->
                if (profileData != null) {
                    sessionManager.userBalance = profileData.balance
                    sessionManager.userName = profileData.username
                    sessionManager.userRole = profileData.role
                }
            }
        }
        AuthApiClient.getActiveNotifications().onSuccess { list ->
            activeNotifications = list
            val popup = list.firstOrNull { it.isPopup }
            if (popup != null) {
                popupNotificationToShow = popup
            }
        }
    }

    val isSubScreen = currentDestination == MainDestination.TICKET_CHAT ||
            currentDestination == MainDestination.DENYLIST ||
            currentDestination == MainDestination.CREATE_TICKET ||
            currentDestination == MainDestination.ADD_FUNDS

    BackHandler(enabled = true) {
        when {
            drawerState.isOpen -> {
                scope.launch { drawerState.close() }
            }
            currentDestination == MainDestination.TICKET_CHAT -> {
                currentDestination = MainDestination.SUPPORT
            }
            currentDestination == MainDestination.CREATE_TICKET -> {
                currentDestination = MainDestination.SUPPORT
            }
            currentDestination == MainDestination.ADD_FUNDS -> {
                currentDestination = MainDestination.DASHBOARD
            }
            currentDestination != MainDestination.DASHBOARD -> {
                currentDestination = MainDestination.DASHBOARD
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

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isSubScreen,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerTonalElevation = 0.dp,
                modifier = Modifier.width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    // Official Brand Header
                    Text(
                        text = "NinMods",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.padding(start = 4.dp, bottom = 14.dp)
                    )

                    // Native Profile Card with Real User Balance
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Circular Avatar
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, shape = CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_n),
                                    contentDescription = "User Avatar",
                                    modifier = Modifier.size(46.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = sessionManager.userName ?: "NinMods User",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Real User Balance Pill
                            val balanceStr = String.format(java.util.Locale.US, "%.2f", sessionManager.userBalance)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth(0.85f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$$balanceStr USD",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Authentic Navigation Items in exact specified order
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Dashboard
                        LoaderDrawerItem(
                            title = "Dashboard",
                            icon = Icons.Filled.Dashboard,
                            isSelected = currentDestination == MainDestination.DASHBOARD,
                            onClick = {
                                currentDestination = MainDestination.DASHBOARD
                                scope.launch { drawerState.close() }
                            }
                        )

                        // 2. Live Support
                        LoaderDrawerItem(
                            title = "Live Support",
                            icon = Icons.Filled.SupportAgent,
                            isSelected = currentDestination == MainDestination.SUPPORT,
                            onClick = {
                                currentDestination = MainDestination.SUPPORT
                                scope.launch { drawerState.close() }
                            }
                        )

                        // 3. Driver Loader
                        LoaderDrawerItem(
                            title = "Driver Loader",
                            icon = Icons.Filled.Build,
                            isSelected = currentDestination == MainDestination.LOADER,
                            onClick = {
                                currentDestination = MainDestination.LOADER
                                scope.launch { drawerState.close() }
                            }
                        )

                        // 4. Daily List (Magisk name forbidden)
                        LoaderDrawerItem(
                            title = "Daily List",
                            icon = Icons.Filled.Security,
                            isSelected = currentDestination == MainDestination.DENYLIST,
                            onClick = {
                                currentDestination = MainDestination.DENYLIST
                                scope.launch { drawerState.close() }
                            }
                        )

                        // 5. General Settings
                        LoaderDrawerItem(
                            title = "General Settings",
                            icon = Icons.Filled.Settings,
                            isSelected = currentDestination == MainDestination.SETTINGS,
                            onClick = {
                                currentDestination = MainDestination.SETTINGS
                                scope.launch { drawerState.close() }
                            }
                        )

                        // 6. Add Funds (moved from Profile card to drawer items)
                        LoaderDrawerItem(
                            title = "Add Funds",
                            icon = Icons.Filled.AccountBalanceWallet,
                            isSelected = currentDestination == MainDestination.ADD_FUNDS,
                            onClick = {
                                currentDestination = MainDestination.ADD_FUNDS
                                scope.launch { drawerState.close() }
                            }
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val displayTitle = when (currentDestination) {
                            MainDestination.TICKET_CHAT -> selectedTicket?.let { "Ticket #${it.id}" } ?: "Support Chat"
                            else -> currentDestination.title
                        }
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        if (currentDestination == MainDestination.TICKET_CHAT ||
                            currentDestination == MainDestination.CREATE_TICKET) {
                            IconButton(onClick = { currentDestination = MainDestination.SUPPORT }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else if (currentDestination == MainDestination.DENYLIST ||
                            currentDestination == MainDestination.ADD_FUNDS) {
                            IconButton(onClick = { currentDestination = MainDestination.DASHBOARD }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Filled.Menu,
                                    contentDescription = "Open Drawer Menu",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    actions = {
                        if (!isSubScreen) {
                            IconButton(onClick = { currentDestination = MainDestination.PROFILE }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_user_profile),
                                    contentDescription = "Profile",
                                    modifier = Modifier.size(24.dp),
                                    tint = if (currentDestination == MainDestination.PROFILE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    fadeIn(animationSpec = tween(280)) togetherWith fadeOut(animationSpec = tween(280))
                },
                label = "MainAppScreenDestinationTransition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) { destination ->
                when (destination) {
                    MainDestination.DASHBOARD -> DashboardScreen(
                        sessionManager = sessionManager,
                        onNavigateToDenyList = { currentDestination = MainDestination.DENYLIST }
                    )
                    MainDestination.LOADER -> DriverLoaderScreen(
                        viewModel = viewModel,
                        sessionManager = sessionManager
                    )
                    MainDestination.DENYLIST -> DenyListScreen(
                        onBack = { currentDestination = MainDestination.DASHBOARD }
                    )
                    MainDestination.SUPPORT -> UserSupportScreen(
                        sessionManager = sessionManager,
                        onSelectTicket = { ticket ->
                            selectedTicket = ticket
                            currentDestination = MainDestination.TICKET_CHAT
                        },
                        onNavigateToCreateTicket = {
                            currentDestination = MainDestination.CREATE_TICKET
                        }
                    )
                    MainDestination.CREATE_TICKET -> CreateTicketScreen(
                        sessionManager = sessionManager,
                        onNavigateBack = { currentDestination = MainDestination.SUPPORT },
                        onTicketCreated = { currentDestination = MainDestination.SUPPORT }
                    )
                    MainDestination.TICKET_CHAT -> {
                        val t = selectedTicket
                        if (t != null) {
                            UserTicketChatScreen(
                                ticket = t,
                                sessionManager = sessionManager,
                                onNavigateBack = { currentDestination = MainDestination.SUPPORT }
                            )
                        } else {
                            LaunchedEffect(Unit) { currentDestination = MainDestination.SUPPORT }
                        }
                    }
                    MainDestination.SETTINGS -> SettingsScreen(
                        sessionManager = sessionManager,
                        onSignOut = onSignOut
                    )
                    MainDestination.ADD_FUNDS -> AddFundsScreen(
                        sessionManager = sessionManager,
                        onNavigateBack = { currentDestination = MainDestination.DASHBOARD }
                    )
                    MainDestination.PROFILE -> ProfileScreen(
                        sessionManager = sessionManager,
                        onSignOut = onSignOut
                    )
                }
            }
        }
    }

    // Material 3 In-App Broadcast / Popup Alert Dialog
    popupNotificationToShow?.let { notif ->
        AlertDialog(
            onDismissRequest = { popupNotificationToShow = null },
            shape = RoundedCornerShape(22.dp),
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = notif.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!notif.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = notif.imageUrl,
                            contentDescription = "Alert Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(
                        text = notif.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { popupNotificationToShow = null },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
private fun LoaderDrawerItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
