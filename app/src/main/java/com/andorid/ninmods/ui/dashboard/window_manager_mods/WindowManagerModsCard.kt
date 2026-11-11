package com.andorid.ninmods.ui.dashboard.window_manager_mods

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.R
import com.andorid.ninmods.config.AppConfig
import com.andorid.ninmods.data.ModDownloader
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.ui.dialogs.ApatchUnifiedModDialog
import com.andorid.ninmods.ui.dialogs.UnifiedModDialogState
import com.andorid.ninmods.util.DeviceIdentifier
import com.ninmods.overlay.FloatingMenuService
import com.ninmods.overlay.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

@Composable
fun WindowManagerModsCard(
    sessionManager: SessionManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val wmDir = remember { File(context.filesDir, "window_manager").also { if (!it.exists()) it.mkdirs() } }
    val engineSoFile = remember { File(wmDir, "libnin_engine.so") }

    var isDownloaded by remember { mutableStateOf(engineSoFile.exists() && engineSoFile.length() > 0L) }
    var isRunning by remember { mutableStateOf(false) }

    var unifiedDialogState by remember { mutableStateOf<UnifiedModDialogState?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun checkFiles() {
        isDownloaded = engineSoFile.exists() && engineSoFile.length() > 0L
    }

    LaunchedEffect(Unit) {
        checkFiles()
    }

    fun startDownload() {
        unifiedDialogState = UnifiedModDialogState.Downloading("NINMODS WindowManager", 0)
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val zipFile = File(wmDir, "window_manager_mod.zip")
            val downloadUrl = "window_manager/window_manager_mod.zip"

            val result = ModDownloader.downloadMod(
                context = context,
                downloadUrl = downloadUrl,
                targetDir = wmDir,
                fileName = "window_manager_mod.zip",
                onProgress = { pct ->
                    unifiedDialogState = UnifiedModDialogState.Downloading("NINMODS WindowManager", pct)
                }
            )

            if (result.isSuccess) {
                // Extract libnin_engine.so from zip
                withContext(Dispatchers.IO) {
                    try {
                        ZipInputStream(FileInputStream(zipFile)).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                if (entry.name.endsWith("libnin_engine.so")) {
                                    FileOutputStream(engineSoFile).use { fos ->
                                        zis.copyTo(fos)
                                    }
                                    engineSoFile.setExecutable(true, false)
                                    break
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                checkFiles()
                unifiedDialogState = null
                Toast.makeText(context, "WindowManager Mod downloaded successfully", Toast.LENGTH_SHORT).show()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Download failed"
                unifiedDialogState = UnifiedModDialogState.DownloadError(err) { startDownload() }
            }
        }
    }

    fun stopOverlay() {
        try {
            val intent = Intent(context, FloatingMenuService::class.java)
            context.stopService(intent)
            isRunning = false
            Toast.makeText(context, "WindowManager Overlay Stopped", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun launchOverlay() {
        scope.launch {
            // 1. Root check
            val isRooted = withContext(Dispatchers.IO) { ShellExecutor.isRootAvailable() }
            if (!isRooted) {
                unifiedDialogState = UnifiedModDialogState.RootRequired(
                    "Superuser (Root) access was denied or device is not rooted. Root permissions (APatch, KernelSU, or Magisk) are required to attach to game memory."
                )
                return@launch
            }

            // 2. Overlay permission check
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                Toast.makeText(context, "Please grant 'Display over other apps' permission", Toast.LENGTH_LONG).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                context.startActivity(intent)
                return@launch
            }

            // 3. License verification gate
            val activeKey = sessionManager?.activeLicenseKey ?: ""
            val hwid = DeviceIdentifier.getHwid(context)

            if (activeKey.isBlank()) {
                // Prompt user to enter license key
                unifiedDialogState = UnifiedModDialogState.LicenseAuth(
                    initialKey = "",
                    isSubmitting = false,
                    errorMessage = null,
                    isVerified = false,
                    terminalLogs = listOf("[*] License key required to activate WindowManager Overlay.")
                )
                return@launch
            }

            // Verify license online with backend
            unifiedDialogState = UnifiedModDialogState.LicenseAuth(
                initialKey = activeKey,
                isSubmitting = true,
                errorMessage = null,
                isVerified = false,
                terminalLogs = listOf("[*] Verifying license with server...", "[*] HWID: $hwid")
            )

            val verifyRes = withContext(Dispatchers.IO) {
                AuthApiClient.verifyLicense(activeKey, hwid)
            }

            if (verifyRes.isFailure) {
                val errMsg = verifyRes.exceptionOrNull()?.message ?: "License verification failed"
                unifiedDialogState = UnifiedModDialogState.LicenseAuth(
                    initialKey = activeKey,
                    isSubmitting = false,
                    errorMessage = errMsg,
                    isVerified = false,
                    terminalLogs = listOf("[-] $errMsg")
                )
                return@launch
            }

            // License is valid! Load native engine and start service
            unifiedDialogState = null
            withContext(Dispatchers.IO) {
                if (engineSoFile.exists()) {
                    NativeBridge.loadLibraryFromPath(engineSoFile.absolutePath)
                }
            }

            try {
                val intent = Intent(context, FloatingMenuService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                isRunning = true
                Toast.makeText(context, "WindowManager Overlay Started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to launch service: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color(0xFF1E212A)),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header: Title & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { checkFiles() },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_launcher),
                            contentDescription = "WindowManager Mod",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "NINMODS WindowManager",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "OB55 (1.132.1) • Pure Native UI",
                            color = Color(0xFF9E9E9E),
                            fontSize = 12.sp
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when {
                        isRunning -> Color(0xFF1B5E20).copy(alpha = 0.5f)
                        isDownloaded -> Color(0xFF4A148C).copy(alpha = 0.5f)
                        else -> Color(0xFF263238).copy(alpha = 0.6f)
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .then(
                                    Modifier.padding(0.dp)
                                )
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = when {
                                    isRunning -> Color(0xFF00E676)
                                    isDownloaded -> Color(0xFFE10098)
                                    else -> Color(0xFF78909C)
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {}
                        }
                        Text(
                            text = when {
                                isRunning -> "RUNNING"
                                isDownloaded -> "READY"
                                else -> "OFFLINE"
                            },
                            color = when {
                                isRunning -> Color(0xFF00E676)
                                isDownloaded -> Color(0xFFE10098)
                                else -> Color(0xFFB0BEC5)
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Feature Highlights
            Text(
                text = "Pure Android WindowManager floating overlay with Electric Orchid menu, hardware-accelerated Canvas ESP, 0-100% headshot rate, sniper scope lock & zero camera snapping.",
                color = Color(0xFFB0BEC5),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Refresh Button
                IconButton(
                    onClick = { checkFiles() },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFF9E9E9E)
                    )
                }

                if (!isDownloaded) {
                    // Download Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { startDownload() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE10098)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "DOWNLOAD MOD",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else if (isRunning) {
                    // Stop Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { stopOverlay() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFD32F2F)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STOP OVERLAY",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // Launch Button
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable { launchOverlay() },
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFF00E676)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Launch",
                                tint = Color(0xFF10131A),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "START OVERLAY",
                                color = Color(0xFF10131A),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Unified Dialogs for License, Root, Download
    ApatchUnifiedModDialog(
        state = unifiedDialogState,
        onDismiss = { unifiedDialogState = null },
        onPurchaseKey = {},
        onSubmitKey = { inputKey ->
            scope.launch {
                val hwid = DeviceIdentifier.getHwid(context)
                val current = unifiedDialogState
                if (current is UnifiedModDialogState.LicenseAuth) {
                    unifiedDialogState = current.copy(
                        isSubmitting = true,
                        errorMessage = null,
                        terminalLogs = current.terminalLogs + listOf("[*] Submitting key for online authorization...")
                    )
                }

                val verifyRes = withContext(Dispatchers.IO) {
                    AuthApiClient.verifyLicense(inputKey, hwid)
                }

                if (verifyRes.isSuccess) {
                    sessionManager?.activeLicenseKey = inputKey
                    unifiedDialogState = null
                    launchOverlay()
                } else {
                    val err = verifyRes.exceptionOrNull()?.message ?: "Invalid license key"
                    val currentAuth = unifiedDialogState
                    if (currentAuth is UnifiedModDialogState.LicenseAuth) {
                        unifiedDialogState = currentAuth.copy(
                            isSubmitting = false,
                            errorMessage = err,
                            terminalLogs = currentAuth.terminalLogs + listOf("[-] $err")
                        )
                    }
                }
            }
        }
    )
}
