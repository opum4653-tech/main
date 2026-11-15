package com.andorid.ninmods.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class DriverRepository(private val context: Context) {

    val driversDir: File
        get() = File(context.filesDir, "drivers").also { if (!it.exists()) it.mkdirs() }

    /**
     * Checks if driver scripts are available locally for the given mode (QX or RT).
     */
    fun hasDriversForMode(mode: String): Boolean {
        val targetFolder = File(driversDir, mode)
        if (!targetFolder.exists()) return false
        val files = targetFolder.listFiles() ?: return false
        return files.any { f -> f.isFile && (f.name.endsWith(".sh") || f.name.endsWith(".ko.sh")) }
    }

    /**
     * Verifies local driver scripts. If missing, downloads Kernel_Drivers.zip directly from the backend server.
     */
    suspend fun extractDriversIfNeeded(onProgress: (String) -> Unit = {}): Boolean = withContext(Dispatchers.IO) {
        val qxDir = File(driversDir, "QX")
        val rtDir = File(driversDir, "RT")

        if (qxDir.exists() && qxDir.listFiles()?.isNotEmpty() == true &&
            rtDir.exists() && rtDir.listFiles()?.isNotEmpty() == true) {
            onProgress("NinMods drivers verified.")
            return@withContext true
        }

        try {
            onProgress("Downloading kernel drivers from server...")
            val downloadRes = downloadDriversFromServer { percent ->
                onProgress("Downloading drivers: $percent%")
            }
            if (downloadRes.isSuccess) {
                onProgress("Driver installation complete.")
                true
            } else {
                onProgress("Download failed: ${downloadRes.exceptionOrNull()?.message}")
                false
            }
        } catch (e: Exception) {
            onProgress("Driver setup failed: ${e.message}")
            false
        }
    }

    /**
     * Downloads Kernel_Drivers.zip from the backend server (/downloads/Kernel_Drivers.zip)
     * and extracts it into driversDir.
     */
    suspend fun downloadDriversFromServer(onProgress: (Int) -> Unit = {}): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val result = ModDownloader.downloadMod(
                context = context,
                downloadUrl = "Kernel_Drivers.zip",
                targetDir = driversDir,
                fileName = "Kernel_Drivers.zip",
                onProgress = onProgress
            )

            if (result.isSuccess) {
                // Ensure executable permissions on all extracted scripts
                try {
                    driversDir.walkTopDown().forEach { file ->
                        if (file.isFile && (file.name.endsWith(".sh") || file.name.endsWith(".ko.sh"))) {
                            file.setExecutable(true, false)
                            file.setReadable(true, false)
                        }
                    }
                    ShellExecutor.executeRoot("chmod -R 755 '${driversDir.absolutePath}'")
                } catch (_: Exception) {}

                Result.success(true)
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Driver download failed."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    /**
     * Locates matching driver script files (.ko.sh or .sh) based on kernel version and mode (QX / RT).
     * If exact kernel match is found, returns them.
     * If major.minor match is found, returns them.
     * Otherwise returns all available scripts for the mode so the user can choose.
     */
    suspend fun findMatchingDrivers(kernelVersion: String, mode: String): List<File> = withContext(Dispatchers.IO) {
        var targetFolder = File(driversDir, mode)
        if (!targetFolder.exists() || targetFolder.listFiles()?.isEmpty() != false) {
            downloadDriversFromServer()
            targetFolder = File(driversDir, mode)
        }

        val fileArray = targetFolder.listFiles() ?: return@withContext emptyList()
        val allFiles = fileArray.filter { f -> f.isFile && (f.name.endsWith(".sh") || f.name.endsWith(".ko.sh")) }

        if (allFiles.isEmpty()) {
            return@withContext emptyList()
        }

        // 1. Exact match (e.g. starts with 4.19.157)
        if (kernelVersion.isNotBlank()) {
            val exactMatches = allFiles.filter { f -> f.name.startsWith(kernelVersion, ignoreCase = true) }
            if (exactMatches.isNotEmpty()) {
                return@withContext exactMatches.sortedBy { it.name }
            }

            // 2. Major.Minor match (e.g. starts with 4.19)
            val majorMinor = if (kernelVersion.contains(".")) {
                val parts = kernelVersion.split(".")
                if (parts.size >= 2) "${parts[0]}.${parts[1]}" else kernelVersion
            } else kernelVersion

            val mmMatches = allFiles.filter { f -> f.name.startsWith(majorMinor, ignoreCase = true) }
            if (mmMatches.isNotEmpty()) {
                return@withContext mmMatches.sortedBy { it.name }
            }
        }

        // 3. Fallback: Return all available drivers for this mode so the dialog ALWAYS presents options
        allFiles.sortedBy { it.name }
    }

    suspend fun getAllDrivers(mode: String): List<File> = withContext(Dispatchers.IO) {
        findMatchingDrivers("", mode)
    }
}
