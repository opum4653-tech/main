package com.andorid.ninmods.ui.setup

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.andorid.ninmods.R
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.ui.theme.APatchTheme
import com.andorid.ninmods.util.RootDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * APatch Official Installation & Setup Screen.
 * Fully unified with APatch UI component hierarchy and typography.
 * Allows users to review, grant, or ignore permissions on initial launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallationSetupScreen(
    onCompleteSetup: () -> Unit,
    onSkipSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var isRootGranted by remember { mutableStateOf(false) }
    var isOverlayGranted by remember { mutableStateOf(false) }
    var isStorageGranted by remember { mutableStateOf(false) }
    var isNotificationGranted by remember { mutableStateOf(false) }
    var rootManagerName by remember { mutableStateOf("Detecting...") }

    fun checkAllPermissions() {
        scope.launch {
            // 1. Superuser / Root check
            val rootOk = withContext(Dispatchers.IO) {
                ShellExecutor.isRootAvailable()
            }
            isRootGranted = rootOk
            val detector = withContext(Dispatchers.IO) { RootDetector.detect(context) }
            rootManagerName = detector.managerName

            // 2. Overlay / Window check
            isOverlayGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }

            // 3. Storage / Files check
            isStorageGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            }

            // 4. Notifications check
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            val notifEnabled = nm?.areNotificationsEnabled() ?: false
            isNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifEnabled && ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                notifEnabled
            }
        }
    }

    LaunchedEffect(Unit) {
        checkAllPermissions()
    }

    // Real-time re-check when user returns from settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkAllPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        onSkipSetup()
    }

    APatchTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Installation & Setup",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onSkipSetup) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // APatch Environment Overview Card
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
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_n),
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "System Environment",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Configure required platform permissions for kernel driver optimization and floating overlay.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

        // APatch Permission List Category
        Text(
            text = "REQUIRED PERMISSIONS & SYSTEM ROOT",
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
                // 1. Superuser / Root Access Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                val granted = withContext(Dispatchers.IO) { ShellExecutor.executeRoot("id") }
                                if (granted) {
                                    isRootGranted = true
                                    Toast.makeText(context, "Superuser access granted!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Root manager: $rootManagerName (Access denied)", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = if (isRootGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Superuser (Root) Access",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isRootGranted) "Granted ($rootManagerName)" else "Required for RAM allocation ($rootManagerName)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isRootGranted) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isRootGranted) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    val granted = withContext(Dispatchers.IO) { ShellExecutor.executeRoot("id") }
                                    if (granted) {
                                        isRootGranted = true
                                        Toast.makeText(context, "Root granted!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Root request denied ($rootManagerName)", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = "Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                // 2. Display Over Other Apps (Overlay) Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            }
                        }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Layers,
                        contentDescription = null,
                        tint = if (isOverlayGranted) Color(0xFF81C784) else Color(0xFFFFB4AB),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Display Over Other Apps",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOverlayGranted) "Active & Allowed" else "Required for floating menu overlay",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isOverlayGranted) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isOverlayGranted) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = "Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                // 3. Storage / All Files Management Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } else {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
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
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Storage & Files Management",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isStorageGranted) "Full Access Granted" else "Download, extract (.zip) & load engine",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isStorageGranted) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isStorageGranted) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                } else {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = "Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )

                // 4. Notifications Permission Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
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
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Notifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isNotificationGranted) "Enabled" else "Background daemon alerts & download tracking",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isNotificationGranted) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isNotificationGranted) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Granted",
                            tint = Color(0xFF81C784),
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(text = "Grant", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // Actions Category
        Text(
            text = "SETUP COMPLETION",
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onCompleteSetup,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(
                        text = "Complete Setup & Continue",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onSkipSetup,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Skip / Ignore Permissions for Now",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}
