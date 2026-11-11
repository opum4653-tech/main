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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.R
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
        unifiedDialogState = UnifiedModDialogState.Downloading("SUPER MODS", 0)
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
                    unifiedDialogState = UnifiedModDialogState.Downloading("SUPER MODS", pct)
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
                Toast.makeText(context, "SUPER MODS downloaded successfully", Toast.LENGTH_SHORT).show()
            } else {
                val err = result.exceptionOrNull()?.message ?: "Download failed"
                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                unifiedDialogState = null
            }
        }
    }

    fun stopOverlay() {
        try {
            val intent = Intent(context, FloatingMenuService::class.java)
            context.stopService(intent)
            isRunning = false
            Toast.makeText(context, "SUPER MODS stopped.", Toast.LENGTH_SHORT).show()
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
                    terminalLogs = listOf("[*] License key required to activate SUPER MODS.")
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
                Toast.makeText(context, "SUPER MODS Started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to launch service: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Unified Dialogs for License, Root, Download, Stop
    unifiedDialogState?.let { state ->
        ApatchUnifiedModDialog(
            state = state,
            onDismiss = {
                downloadJob?.cancel()
                downloadJob = null
                unifiedDialogState = null
            },
            onConfirmDownload = {
                startDownload()
            },
            onCancelDownload = {
                downloadJob?.cancel()
                downloadJob = null
                unifiedDialogState = null
            },
            onConfirmStop = {
                unifiedDialogState = null
                stopOverlay()
            },
            onSubmitKey = { inputKey ->
                scope.launch {
                    val hwid = DeviceIdentifier.getHwid(context)
                    if (state is UnifiedModDialogState.LicenseAuth) {
                        unifiedDialogState = state.copy(
                            isSubmitting = true,
                            errorMessage = null,
                            terminalLogs = (state.terminalLogs + listOf("[*] Submitting key for online authorization...")).takeLast(40)
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
                        if (state is UnifiedModDialogState.LicenseAuth) {
                            unifiedDialogState = state.copy(
                                isSubmitting = false,
                                errorMessage = err,
                                terminalLogs = (state.terminalLogs + listOf("[-] $err")).takeLast(40)
                            )
                        }
                    }
                }
            }
        )
    }

    // Outer Card matching KernelModsCard & ExternalModsCard exactly
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "SUPER MOD",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { checkFiles() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Inner Mod Item Card
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isDownloaded) {
                            unifiedDialogState = UnifiedModDialogState.ConfirmDownload("SUPER MODS")
                        } else if (isRunning) {
                            unifiedDialogState = UnifiedModDialogState.StopConfirm("SUPER MODS")
                        } else {
                            launchOverlay()
                        }
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Official App Logo container
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isRunning -> MaterialTheme.colorScheme.primaryContainer
                            !isDownloaded -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isRunning) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Running",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp)
                                )
                            } else {
                                Image(
                                    painter = painterResource(id = R.drawable.logo_n),
                                    contentDescription = "SUPER MODS",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SUPER MODS",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = if (isRunning) "Overlay active & running in RAM" else "Native floating engine client",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Update / Re-download icon button if already downloaded
                    if (isDownloaded && !isRunning) {
                        IconButton(
                            onClick = {
                                unifiedDialogState = UnifiedModDialogState.ConfirmDownload("SUPER MODS")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CloudDownload,
                                contentDescription = "Update Mod",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Status badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isRunning -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            !isDownloaded -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = when {
                                isRunning -> "ACTIVE"
                                !isDownloaded -> "AVAILABLE"
                                else -> "READY"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                isRunning -> MaterialTheme.colorScheme.primary
                                !isDownloaded -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.secondary
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
