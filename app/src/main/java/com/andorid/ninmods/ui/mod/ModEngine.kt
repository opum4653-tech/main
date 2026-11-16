package com.andorid.ninmods.ui.mod

import android.content.Context
import android.os.Environment
import com.andorid.ninmods.data.InteractiveProcessBridge
import com.andorid.ninmods.data.ModDownloader
import com.andorid.ninmods.data.SessionManager
import com.andorid.ninmods.data.ShellExecutor
import com.andorid.ninmods.network.AuthApiClient
import com.andorid.ninmods.util.DeviceIdentifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * One row in either mod list (kernel or external). The [file] points at the
 * locally cached binary; [isDownloaded] is false until the payload is present.
 */
data class ModItem(
    val id: String,
    val name: String,
    val fileName: String,
    val downloadPackage: String,
    val file: File,
    val isDownloaded: Boolean
)

/**
 * Shared engine behind both mod cards. Kernel and external only differ in the
 * cache directory, the official binary/zip names and the server subfolder.
 */
class ModEngine(
    private val context: Context,
    val bridge: InteractiveProcessBridge,
    private val sessionManager: SessionManager?,
    val config: ModConfig
) {
    data class ModConfig(
        val cacheDirName: String,
        val officialBinary: String,
        val officialZip: String,
        val displayName: String,
        val serverFolder: String,
        val customExtensions: List<String>
    )

    /** Where a mod is on disk: cached, or present but stale. */
    fun cacheDir(): File = File(context.filesDir, config.cacheDirName).also { if (!it.exists()) it.mkdirs() }

    suspend fun scan(): List<ModItem> = withContext(Dispatchers.IO) {
        val dir = cacheDir()
        val binary = File(dir, config.officialBinary)
        val zip = File(dir, config.officialZip)

        // Self-heal: unpack the zip when the binary is missing or truncated.
        if (zip.exists() && (!binary.exists() || binary.length() == 0L)) {
            runCatching {
                ModDownloader.unzip(zip, dir)
                binary.setExecutable(true, false)
            }
        }

        // Also honor artifacts the user dropped into the public Downloads folder.
        runCatching {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val publicZip = File(downloads, config.officialZip)
            if (publicZip.exists() && (!binary.exists() || binary.length() == 0L)) {
                ModDownloader.unzip(publicZip, dir)
                binary.setExecutable(true, false)
            }
            val publicBin = File(downloads, config.officialBinary)
            if (publicBin.exists() && (!binary.exists() || binary.length() == 0L)) {
                publicBin.copyTo(binary, overwrite = true)
                binary.setExecutable(true, false)
            }
            val publicScript = File(downloads, "${config.officialBinary}.sh")
            if (publicScript.exists() && (!binary.exists() || binary.length() == 0L)) {
                publicScript.copyTo(binary, overwrite = true)
                binary.setExecutable(true, false)
            }
        }

        val official = ModItem(
            id = config.officialBinary,
            name = config.displayName,
            fileName = config.officialZip,
            downloadPackage = "${config.serverFolder}/${config.officialZip}",
            file = binary,
            isDownloaded = binary.exists() && binary.length() > 0L
        )

        val customRoot = runCatching {
            File(Environment.getExternalStorageDirectory(), "NinMods/${config.cacheDirName}").also {
                if (!it.exists()) it.mkdirs()
            }
        }.getOrNull()

        val custom = (customRoot?.listFiles()?.toList() ?: emptyList())
            .filter { file ->
                file.isFile &&
                    !file.name.startsWith(".") &&
                    file.name != config.officialBinary &&
                    followsExtensionRule(file, config.customExtensions)
            }
            .distinctBy { it.name }
            .map { file ->
                ModItem(
                    id = file.name,
                    name = prettyName(file.name),
                    fileName = file.name,
                    downloadPackage = file.name,
                    file = file,
                    isDownloaded = true
                )
            }

        listOf(official) + custom
    }

    suspend fun download(item: ModItem, onProgress: (Int) -> Unit): Result<File> = withContext(Dispatchers.IO) {
        ModDownloader.downloadMod(
            context = context,
            downloadUrl = item.downloadPackage,
            targetDir = item.file.parentFile ?: cacheDir(),
            fileName = item.fileName,
            onProgress = onProgress
        )
    }

    fun hasRoot(): Boolean = ShellExecutor.isRootAvailable()

    /** Launch the mod through the interactive root bridge. */
    fun launch(
        item: ModItem,
        onLicensePrompt: () -> Unit,
        onError: (String) -> Unit
    ) {
        val hwid = DeviceIdentifier.getHwid(context)
        val initialKey = sessionManager?.activeLicenseKey.orEmpty()
        bridge.start(
            executableFile = item.file,
            deviceHwid = hwid,
            licenseKey = initialKey,
            onLicensePrompt = onLicensePrompt,
            onError = onError
        )
    }

    /** Restart the mod with a key the server already accepted. */
    fun relaunchWithKey(item: ModItem, key: String, onError: (String) -> Unit) {
        val hwid = DeviceIdentifier.getHwid(context)
        bridge.stop()
        bridge.start(
            executableFile = item.file,
            deviceHwid = hwid,
            licenseKey = key,
            onLicensePrompt = {},
            onError = onError
        )
    }

    suspend fun activate(key: String): Result<com.andorid.ninmods.network.ActiveLicenseInfo> =
        withContext(Dispatchers.IO) {
            AuthApiClient.activateLicense(licenseKey = key, hwid = DeviceIdentifier.getHwid(context))
        }

    fun persistLicense(key: String, remainingSeconds: Long) {
        sessionManager?.let { session ->
            session.activeLicenseKey = key
            session.serverRemainingSeconds = if (remainingSeconds > 0) remainingSeconds else 86_400L
        }
    }

    fun stop() = bridge.stop()

    val isRunning: StateFlow<Boolean> get() = bridge.isRunning
    val isProcessAlive: StateFlow<Boolean> get() = bridge.isProcessAlive
    val licensePromptTriggered: StateFlow<Boolean> get() = bridge.licensePromptTriggered
    val readyToStart: StateFlow<Boolean> get() = bridge.isReadyToStart
    val outputLines: Flow<String> get() = bridge.outputLines

    private fun followsExtensionRule(file: File, extensions: List<String>): Boolean {
        if (extensions.isEmpty()) return !file.name.contains('.')
        return extensions.any { file.name.endsWith(it, ignoreCase = true) } ||
            (extensions.contains("*") && !file.name.contains('.'))
    }

    private fun prettyName(raw: String): String {
        val base = raw.substringBeforeLast('.')
        return base.split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }
            .ifBlank { raw }
    }

    companion object {
        val KERNEL = ModConfig(
            cacheDirName = "kernel_mods",
            officialBinary = "ninmods-kernel",
            officialZip = "ninmods-kernel.zip",
            displayName = "NINMODS Kernel",
            serverFolder = "kernel",
            customExtensions = listOf(".sh", "*")
        )

        val EXTERNAL = ModConfig(
            cacheDirName = "external_mods",
            officialBinary = "gemini-cli",
            officialZip = "gemini-cli.zip",
            displayName = "NINMODS External",
            serverFolder = "external",
            customExtensions = listOf(".sh")
        )
    }
}

/** Cancellable handle for an in-flight mod download. */
class DownloadHandle {
    var job: Job? = null
    fun cancel() {
        job?.cancel()
        job = null
    }
}
