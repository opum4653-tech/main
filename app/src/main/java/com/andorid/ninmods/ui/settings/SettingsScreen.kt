package com.andorid.ninmods.ui.settings

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.component.SwitchItem
import com.andorid.ninmods.ui.dialogs.AppSelectionBottomSheet
import com.andorid.ninmods.ui.dialogs.ReportBugBottomSheet
import com.andorid.ninmods.ui.dialogs.SheetItem
import com.andorid.ninmods.util.RootDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    sessionManager: SessionManager,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var rootManagerName by remember { mutableStateOf("Detecting...") }
    var isRooted by remember { mutableStateOf(false) }
    var isSuGranted by remember { mutableStateOf(false) }
    var isStorageGranted by remember { mutableStateOf(false) }
    var isNotificationGranted by remember { mutableStateOf(false) }
    var showSetupDialog by remember { mutableStateOf(false) }

    var globalNamespace by remember { mutableStateOf(sessionManager.globalNamespace) }
    var legacySu by remember { mutableStateOf(sessionManager.legacySu) }
    var showRebootSheet by remember { mutableStateOf(false) }
    var showReportBugSheet by remember { mutableStateOf(false) }

    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateStatusText by remember { mutableStateOf("Tap to check latest release") }
    var isNewUpdateAvailable by remember { mutableStateOf(false) }
    var latestDownloadUrl by remember { mutableStateOf("") }

    fun checkForUpdates() {
        isCheckingUpdate = true
        updateStatusText = "Connecting to server..."
        scope.launch {
            AuthApiClient.checkAppVersion("loader")
                .onSuccess { ver ->
                    isCheckingUpdate = false
                    val currentVersionCode = com.andorid.ninmods.config.AppConfig.VERSION_CODE
                    if (ver.versionCode > currentVersionCode) {
                        isNewUpdateAvailable = true
                        latestDownloadUrl = ver.downloadUrl
                        updateStatusText = "New release available: ${ver.versionName}"
                    } else {
                        isNewUpdateAvailable = false
                        updateStatusText = "Up to date (${ver.versionName})"
                    }
                }
                .onFailure {
                    isCheckingUpdate = false
                    updateStatusText = "Running official build ${com.andorid.ninmods.config.AppConfig.VERSION_NAME}"
                }
        }
    }

    fun checkStorage(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun checkNotification(ctx: Context): Boolean {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val enabled = nm?.areNotificationsEnabled() ?: false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            enabled && ContextCompat.checkSelfPermission(
                ctx,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            enabled
        }
    }

    fun refreshAllStatuses() {
        isStorageGranted = checkStorage(context)
        isNotificationGranted = checkNotification(context)
        scope.launch(Dispatchers.IO) {
            val rootInfo = RootDetector.detect(context)
            val suTest = ShellExecutor.isRootAvailable()
            withContext(Dispatchers.Main) {
                rootManagerName = rootInfo.managerName
                isRooted = rootInfo.isRooted
                isSuGranted = suTest
            }
        }
    }

    val storageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshAllStatuses()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshAllStatuses()
    }

    LaunchedEffect(Unit) {
        refreshAllStatuses()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAllStatuses()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // PERMISSIONS & SYSTEM ROOT (Genuine real-time detection & grant triggers)
        Text(
            text = "PERMISSIONS & ROOT ENVIRONMENT",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                // 1. Root Manager Item (Live Detection)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            refreshAllStatuses()
                            Toast.makeText(context, "Root Manager: $rootManagerName", Toast.LENGTH_SHORT).show()
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = if (isRooted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Root Manager",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Detected: $rootManagerName",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isRooted) Color(0xFF81C784) else Color(0xFFFFB4AB)
                        )
                    }
                    Icon(
                        imageVector = if (isRooted) Icons.Filled.CheckCircle else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isRooted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 2. SuperUser Access (su) Item - Tap to request authorization
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                val granted = ShellExecutor.isRootAvailable()
                                withContext(Dispatchers.Main) {
                                    isSuGranted = granted
                                    val msg = if (granted) "SuperUser (uid=0) authorized!" else "SuperUser request denied."
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VpnKey,
                        contentDescription = null,
                        tint = if (isSuGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SuperUser (su) Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSuGranted) "Granted (uid=0)" else "Denied (Tap to Authorize Root Prompt)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSuGranted) Color(0xFF81C784) else Color(0xFFFFB4AB)
                        )
                    }
                    Icon(
                        imageVector = if (isSuGranted) Icons.Filled.CheckCircle else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isSuGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 3. Storage Permission Item - Tap to grant (Real All Files / Storage)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } else {
                                storageLauncher.launch(
                                    arrayOf(
                                        android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Folder,
                        contentDescription = null,
                        tint = if (isStorageGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage Permission",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isStorageGranted) "Granted" else "Denied (Tap to Grant All Files Access)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isStorageGranted) Color(0xFF81C784) else Color(0xFFFFB4AB)
                        )
                    }
                    Icon(
                        imageVector = if (isStorageGranted) Icons.Filled.CheckCircle else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isStorageGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 4. Notification Permission Item - Tap to grant (Real POST_NOTIFICATIONS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        tint = if (isNotificationGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Notification Permission",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isNotificationGranted) "Granted" else "Denied (Tap to Grant Notification)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isNotificationGranted) Color(0xFF81C784) else Color(0xFFFFB4AB)
                        )
                    }
                    Icon(
                        imageVector = if (isNotificationGranted) Icons.Filled.CheckCircle else Icons.Filled.Close,
                        contentDescription = null,
                        tint = if (isNotificationGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 5. Re-run Initial Setup & Permission Wizard
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showSetupDialog = true
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Permission Setup Wizard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Reconfigure root, overlay, storage, and notifications",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // APATCH ENVIRONMENT SWITCHES
        Text(
            text = "KERNEL CONFIGURATION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                SwitchItem(
                    icon = Icons.Filled.Layers,
                    title = "Global Namespace",
                    summary = "Mount namespace shared with system services",
                    checked = globalNamespace,
                    onCheckedChange = {
                        globalNamespace = it
                        sessionManager.globalNamespace = it
                    }
                )

                SwitchItem(
                    icon = Icons.Filled.Terminal,
                    title = "Legacy su Compatibility",
                    summary = "Provide /system/bin/su symlink",
                    checked = legacySu,
                    onCheckedChange = {
                        legacySu = it
                        sessionManager.legacySu = it
                    }
                )
            }
        }

        // DEVICE POWER & REBOOT (Using universal AppSelectionBottomSheet)
        Text(
            text = "DEVICE POWER",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRebootSheet = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Reboot Device",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reboot Device",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "System reboot, recovery, bootloader or soft reboot",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // SESSION
        Text(
            text = "SESSION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Session: Active",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = sessionManager.userName ?: "NinMods User",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSignOut() }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.size(16.dp))
                    Text(
                        text = "Sign Out",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        // COMMUNITY
        Text(
            text = "COMMUNITY",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/ninmods"))
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column {
                    Text(
                        text = "Telegram Channel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "@ninmods",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // DIAGNOSTICS & BUG REPORT
        Text(
            text = "DIAGNOSTICS & BUG REPORT",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showReportBugSheet = true }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.BugReport,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Report an Issue / Bug",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Submit crash telemetry & system logs to developer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // SOFTWARE UPDATES
        Text(
            text = "SOFTWARE UPDATES",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.size(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NinMods DriverLoader ${com.andorid.ninmods.config.AppConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = updateStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isNewUpdateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isCheckingUpdate) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else if (isNewUpdateAvailable && latestDownloadUrl.isNotBlank()) {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(latestDownloadUrl))
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Update")
                    }
                } else {
                    IconButton(onClick = { checkForUpdates() }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Check for Updates",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    // Universal Reusable Selection Bottom Sheet for Device Reboot
    if (showRebootSheet) {
        val rebootOptions = listOf(
            SheetItem(
                value = "reboot",
                title = "Normal Reboot",
                subtitle = "Restart system cleanly",
                icon = Icons.Filled.Refresh
            ),
            SheetItem(
                value = "recovery",
                title = "Reboot to Recovery",
                subtitle = "Boot into TWRP or OrangeFox recovery",
                icon = Icons.Filled.Terminal
            ),
            SheetItem(
                value = "bootloader",
                title = "Reboot to Bootloader",
                subtitle = "Boot into Fastboot mode",
                icon = Icons.Filled.Security
            ),
            SheetItem(
                value = "soft",
                title = "Soft Reboot",
                subtitle = "Restart Android runtime (Zygote)",
                icon = Icons.Filled.Refresh
            )
        )

        AppSelectionBottomSheet(
            title = "Reboot Device",
            subtitle = "Select desired reboot mode",
            items = rebootOptions,
            actionButtonText = "Reboot",
            onDismissRequest = { showRebootSheet = false },
            onItemSelected = { mode ->
                showRebootSheet = false
                scope.launch(Dispatchers.IO) {
                    when (mode) {
                        "reboot" -> ShellExecutor.executeRoot("svc power reboot || reboot")
                        "recovery" -> ShellExecutor.executeRoot("reboot recovery")
                        "bootloader" -> ShellExecutor.executeRoot("reboot bootloader")
                        "soft" -> ShellExecutor.executeRoot("setprop ctl.restart zygote")
                    }
                }
            }
        )
    }

    if (showSetupDialog) {
        com.andorid.ninmods.ui.dialogs.ApatchAnimatedDialog(
            onDismissRequest = { showSetupDialog = false }
        ) {
            com.andorid.ninmods.ui.setup.InstallationSetupScreen(
                onCompleteSetup = {
                    showSetupDialog = false
                    refreshAllStatuses()
                },
                onSkipSetup = {
                    showSetupDialog = false
                    refreshAllStatuses()
                }
            )
        }
    }

    if (showReportBugSheet) {
        ReportBugBottomSheet(
            sessionManager = sessionManager,
            onDismiss = { showReportBugSheet = false }
        )
    }
}
