package com.andorid.ninmods.ui.dashboard.kernel_mods

import android.content.Context
import android.os.Build
import android.os.Environment
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
import androidx.compose.material.icons.filled.Memory
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.andorid.ninmods.R
import com.andorid.ninmods.config.AppConfig
import com.andorid.ninmods.data.InteractiveProcessBridge
import com.andorid.ninmods.data.ModDownloader
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.ui.dialogs.ApatchUnifiedModDialog
import com.andorid.ninmods.ui.dialogs.UnifiedModDialogState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

// Cyber Purple theme color
private val CyberPurple = Color(0xFFAD47FA)

data class KernelModItem(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadPackage: String,
    val file: File,
    val isDownloaded: Boolean
)

@Composable
fun KernelModsCard(
    sessionManager: SessionManager? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val processBridge = remember { InteractiveProcessBridge(scope) }
    val isProcessRunning by processBridge.isRunning.collectAsState()
    val isLicensePromptTriggered by processBridge.licensePromptTriggered.collectAsState()
    val isReadyToStart by processBridge.isReadyToStart.collectAsState()

    var mods by remember { mutableStateOf<List<KernelModItem>>(emptyList()) }
    var activeModId by remember { mutableStateOf<String?>(null) }

    // Unified Dialog state holders
    var unifiedDialogState by remember { mutableStateOf<UnifiedModDialogState?>(null) }
    var selectedModForAction by remember { mutableStateOf<KernelModItem?>(null) }

    // Intercept output logs from running binary to stream into dialog and catch missing driver or failure
    LaunchedEffect(Unit) {
        processBridge.outputLines.collect { chunk ->
            val lines = chunk.split("\n").map { it.trimEnd() }.filter { it.isNotBlank() }
            val current = unifiedDialogState
            if (current is UnifiedModDialogState.LicenseAuth) {
                val updatedLogs = (current.terminalLogs + lines).takeLast(40)
                val failureMsg = lines.firstOrNull {
                    it.contains("Authentication failed", ignoreCase = true) ||
                    it.contains("Invalid or expired", ignoreCase = true) ||
                    it.contains("[-] ", ignoreCase = true)
                }
                unifiedDialogState = current.copy(
                    terminalLogs = updatedLogs,
                    errorMessage = failureMsg ?: current.errorMessage,
                    isSubmitting = if (failureMsg != null) false else current.isSubmitting
                )
            }
            val fatalDriverMsg = lines.firstOrNull {
                it.contains("Driver not loaded", ignoreCase = true) ||
                it.contains("driver not found", ignoreCase = true) ||
                it.contains("FATAL", ignoreCase = true)
            }
            if (fatalDriverMsg != null) {
                unifiedDialogState = UnifiedModDialogState.RootRequired(
                    "Kernel Driver Missing: $fatalDriverMsg\n\nPlease load your matching kernel driver in the Driver Loader tab before starting NinMods Kernel."
                )
                processBridge.stop()
                activeModId = null
            }
        }
    }

    // Automatically trigger license pop-up dialog when stdin prompt is detected in running binary
    LaunchedEffect(isLicensePromptTriggered) {
        if (isLicensePromptTriggered) {
            val initialKey = sessionManager?.activeLicenseKey ?: ""
            val current = unifiedDialogState
            if (current !is UnifiedModDialogState.LicenseAuth) {
                unifiedDialogState = UnifiedModDialogState.LicenseAuth(
                    initialKey = initialKey,
                    isSubmitting = false,
                    errorMessage = null,
                    isVerified = false
                )
            }
        }
    }

    LaunchedEffect(isReadyToStart) {
        if (isReadyToStart) {
            val current = unifiedDialogState
            if (current is UnifiedModDialogState.LicenseAuth) {
                unifiedDialogState = current.copy(
                    isSubmitting = false,
                    isVerified = true,
                    terminalLogs = (current.terminalLogs + listOf("[+] License validated! Engine authorized and ready.")).takeLast(40)
                )
            }
        }
    }

    fun refreshMods() {
        scope.launch {
            mods = withContext(Dispatchers.IO) {
                val internalDir = File(context.filesDir, "kernel_mods").also { if (!it.exists()) it.mkdirs() }

                val officialFileName = "ninmods-kernel"
                val officialZipName = "ninmods-kernel.zip"
                val officialFile = File(internalDir, officialFileName)

                // Self-healing: if zip exists but binary missing, unpack
                val internalZip = File(internalDir, officialZipName)
                if (internalZip.exists() && (!officialFile.exists() || officialFile.length() == 0L)) {
                    try {
                        ModDownloader.unzip(internalZip, internalDir)
                        officialFile.setExecutable(true, false)
                    } catch (_: Exception) {}
                }

                // Check Download folder
                try {
                    val publicDownloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val publicZip = File(publicDownloadDir, officialZipName)
                    if (publicZip.exists() && (!officialFile.exists() || officialFile.length() == 0L)) {
                        ModDownloader.unzip(publicZip, internalDir)
                        officialFile.setExecutable(true, false)
                    }
                    val publicBin = File(publicDownloadDir, officialFileName)
                    if (publicBin.exists() && (!officialFile.exists() || officialFile.length() == 0L)) {
                        publicBin.copyTo(officialFile, overwrite = true)
                        officialFile.setExecutable(true, false)
                    }
                } catch (_: Exception) {}

                val officialMod = KernelModItem(
                    id = officialFileName,
                    name = "NINMODS Kernel",
                    fileName = officialZipName,
                    downloadPackage = "kernel/$officialZipName",
                    file = officialFile,
                    isDownloaded = officialFile.exists() && officialFile.length() > 0L
                )

                // Optional custom kernel scripts from sdcard
                val sdcardDir = try {
                    File(Environment.getExternalStorageDirectory(), "NinMods/kernel_mods").also {
                        if (!it.exists()) it.mkdirs()
                    }
                } catch (_: Exception) { null }

                val customFiles = (sdcardDir?.listFiles()?.toList() ?: emptyList())
                    .filter { it.isFile && !it.name.startsWith(".") && (it.name.endsWith(".sh") || !it.name.contains(".")) && it.name != officialFileName }
                    .distinctBy { it.name }
                    .map { file ->
                        KernelModItem(
                            id = file.name,
                            name = formatModName(file.name),
                            fileName = file.name,
                            downloadPackage = file.name,
                            file = file,
                            isDownloaded = true
                        )
                    }

                listOf(officialMod) + customFiles
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshMods()
    }

    fun launchMod(mod: KernelModItem) {
        selectedModForAction = mod
        scope.launch {
            val isRooted = withContext(Dispatchers.IO) {
                ShellExecutor.isRootAvailable()
            }
            if (!isRooted) {
                unifiedDialogState = UnifiedModDialogState.RootRequired(
                    "Superuser Denied: Root access (APatch or KernelSU) is mandatory to interact with the Ring 0 kernel driver and render the floating overlay."
                )
                return@launch
            }

            activeModId = mod.id
            Toast.makeText(context, "Initializing ${mod.name}...", Toast.LENGTH_SHORT).show()

            val deviceHwid = com.andorid.ninmods.util.DeviceIdentifier.getHwid(context)
            val initialKey = sessionManager?.activeLicenseKey ?: ""

            processBridge.start(
                executableFile = mod.file,
                deviceHwid = deviceHwid,
                licenseKey = initialKey,
                onLicensePrompt = {
                    val current = unifiedDialogState
                    if (current !is UnifiedModDialogState.LicenseAuth) {
                        unifiedDialogState = UnifiedModDialogState.LicenseAuth(
                            initialKey = initialKey,
                            isSubmitting = false,
                            errorMessage = null,
                            isVerified = false
                        )
                    }
                },
                onError = { errMsg ->
                    unifiedDialogState = UnifiedModDialogState.RootRequired(
                        "Kernel Engine Error: $errMsg\n\nPlease ensure Superuser is granted and a compatible kernel driver is loaded."
                    )
                    activeModId = null
                }
            )
        }
    }

    var downloadJob by remember { mutableStateOf<Job?>(null) }

    fun startDownload(mod: KernelModItem) {
        selectedModForAction = mod
        unifiedDialogState = UnifiedModDialogState.Downloading(mod.name, 0)
        downloadJob?.cancel()
        downloadJob = scope.launch {
            val result = ModDownloader.downloadMod(
                context = context,
                downloadUrl = mod.downloadPackage,
                targetDir = mod.file.parentFile ?: File(context.filesDir, "kernel_mods"),
                fileName = mod.fileName,
                onProgress = { p ->
                    unifiedDialogState = UnifiedModDialogState.Downloading(mod.name, p)
                }
            )
            if (result.isSuccess) {
                Toast.makeText(context, "${mod.name} downloaded successfully!", Toast.LENGTH_SHORT).show()
                refreshMods()
                launchMod(mod)
            } else {
                Toast.makeText(context, result.exceptionOrNull()?.message ?: "Download failed", Toast.LENGTH_LONG).show()
                unifiedDialogState = null
            }
        }
    }

    // Unified Dialog
    unifiedDialogState?.let { state ->
        ApatchUnifiedModDialog(
            state = state,
            onDismiss = {
                downloadJob?.cancel()
                downloadJob = null
                if (state is UnifiedModDialogState.LicenseAuth) {
                    processBridge.stop()
                    activeModId = null
                }
                unifiedDialogState = null
            },
            onConfirmDownload = {
                selectedModForAction?.let { startDownload(it) }
            },
            onCancelDownload = {
                downloadJob?.cancel()
                downloadJob = null
                unifiedDialogState = null
            },
            onSubmitKey = { submittedKey ->
                if (state is UnifiedModDialogState.LicenseAuth) {
                    unifiedDialogState = state.copy(
                        isSubmitting = true,
                        errorMessage = null,
                        terminalLogs = (state.terminalLogs + listOf(
                            "[*] Verifying license key with authorization server...",
                            "[*] Synchronizing secure handshake..."
                        )).takeLast(40)
                    )
                    scope.launch {
                        val deviceHwid = com.andorid.ninmods.util.DeviceIdentifier.getHwid(context)

                        // 1. Concurrently synchronize activation with server API
                        val activationResult = com.andorid.ninmods.network.AuthApiClient.activateLicense(
                            licenseKey = submittedKey,
                            hwid = deviceHwid
                        )

                        if (activationResult.isSuccess) {
                            val licInfo = activationResult.getOrNull()
                            sessionManager?.let { sm ->
                                sm.activeLicenseKey = submittedKey
                                sm.serverRemainingSeconds = licInfo?.remainingSeconds ?: 86400L
                            }

                            // 2. Feed to stdin pipe if alive, or cleanly restart process with pre-authenticated key
                            val sent = processBridge.sendInput(submittedKey)
                            if (!sent || !processBridge.isProcessAlive.value) {
                                selectedModForAction?.let { mod ->
                                    processBridge.stop()
                                    processBridge.start(
                                        executableFile = mod.file,
                                        deviceHwid = deviceHwid,
                                        licenseKey = submittedKey,
                                        onLicensePrompt = {},
                                        onError = { errMsg ->
                                            val current = unifiedDialogState
                                            if (current is UnifiedModDialogState.LicenseAuth) {
                                                unifiedDialogState = current.copy(
                                                    isSubmitting = false,
                                                    errorMessage = errMsg,
                                                    terminalLogs = (current.terminalLogs + listOf("[-] $errMsg")).takeLast(40)
                                                )
                                            }
                                        }
                                    )
                                }
                            }

                            val current = unifiedDialogState
                            if (current is UnifiedModDialogState.LicenseAuth) {
                                unifiedDialogState = current.copy(
                                    isSubmitting = false,
                                    isVerified = true,
                                    terminalLogs = (current.terminalLogs + listOf(
                                        "[+] Cloud License Authorized!",
                                        "[+] Remaining: ${licInfo?.remainingSeconds?.let { it / 3600 } ?: 24} hours",
                                        "[+] Ready to load into memory."
                                    )).takeLast(40)
                                )
                            }
                        } else {
                            val serverErr = activationResult.exceptionOrNull()?.message ?: "Server verification rejected license key."
                            val current = unifiedDialogState
                            if (current is UnifiedModDialogState.LicenseAuth) {
                                unifiedDialogState = current.copy(
                                    isSubmitting = false,
                                    errorMessage = serverErr,
                                    terminalLogs = (current.terminalLogs + listOf("[-] $serverErr")).takeLast(40)
                                )
                            }
                        }
                    }
                }
            },
            onStartMod = {
                scope.launch {
                    processBridge.sendInput("START")
                    unifiedDialogState = null
                    Toast.makeText(context, "Loading kernel floating engine into RAM...", Toast.LENGTH_SHORT).show()
                }
            },
            onConfirmStop = {
                unifiedDialogState = null
                processBridge.stop()
                activeModId = null
                selectedModForAction?.let {
                    Toast.makeText(context, "${it.name} stopped.", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

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
                    imageVector = Icons.Filled.Memory,
                    contentDescription = null,
                    tint = CyberPurple,
                    modifier = Modifier.size(20.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = "KERNEL MOD",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CyberPurple
                )

                Spacer(modifier = Modifier.weight(1f))

                IconButton(
                    onClick = { refreshMods() },
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

            if (mods.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "No kernel mod configured",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 12.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    mods.forEach { mod ->
                        val isRunning = isProcessRunning && activeModId == mod.id

                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!mod.isDownloaded) {
                                        selectedModForAction = mod
                                        unifiedDialogState = UnifiedModDialogState.ConfirmDownload(mod.name)
                                    } else if (isRunning) {
                                        selectedModForAction = mod
                                        unifiedDialogState = UnifiedModDialogState.StopConfirm(mod.name)
                                    } else {
                                        launchMod(mod)
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
                                // Logo Container with Cyber Purple tint
                                Surface(
                                    shape = CircleShape,
                                    color = when {
                                        isRunning -> CyberPurple.copy(alpha = 0.25f)
                                        !mod.isDownloaded -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> CyberPurple.copy(alpha = 0.15f)
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
                                                tint = CyberPurple,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        } else {
                                            Image(
                                                painter = painterResource(id = R.drawable.logo_n),
                                                contentDescription = mod.name,
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
                                        text = mod.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = if (isRunning) "Ring 0 Engine Active • Connected to Driver" else "OB55 (1.132.1) • Ring 0 Floating Engine",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Update / Re-download icon button if already downloaded
                                if (mod.isDownloaded && !isRunning) {
                                    IconButton(
                                        onClick = {
                                            selectedModForAction = mod
                                            unifiedDialogState = UnifiedModDialogState.ConfirmDownload(mod.name)
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
                                        isRunning -> CyberPurple.copy(alpha = 0.18f)
                                        !mod.isDownloaded -> MaterialTheme.colorScheme.surfaceVariant
                                        else -> CyberPurple.copy(alpha = 0.12f)
                                    }
                                ) {
                                    Text(
                                        text = when {
                                            isRunning -> "ACTIVE"
                                            !mod.isDownloaded -> "AVAILABLE"
                                            else -> "READY"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            isRunning -> CyberPurple
                                            !mod.isDownloaded -> MaterialTheme.colorScheme.onSurfaceVariant
                                            else -> CyberPurple
                                        },
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatModName(fileName: String): String {
    val base = fileName.substringBeforeLast(".")
    return base.replace("_", " ").replace("-", " ")
        .split(" ")
        .filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
