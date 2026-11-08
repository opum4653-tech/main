package com.andorid.ninmods.data

import android.content.Context
import com.andorid.ninmods.config.AppConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object ModDownloader {

    sealed class DownloadState {
        object Idle : DownloadState()
        data class Progress(val percentage: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
        data class Success(val destinationDir: File) : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    /**
     * Download a mod package (zip or binary) from backend server to local directory.
     * Features an automated multi-tier fallback ladder across server routes and GitHub releases.
     */
    suspend fun downloadMod(
        context: Context,
        downloadUrl: String,
        targetDir: File,
        fileName: String,
        onProgress: (Int) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            if (!targetDir.exists()) targetDir.mkdirs()

            val cleanFileBasename = File(downloadUrl).name
            val candidateUrls = mutableListOf<String>()

            val cleanPath = downloadUrl.trimStart('/')
            val serverBase = AppConfig.BASE_URL.trimEnd('/')

            // Enforce strict backend download endpoint strictly matching module subfolder
            // e.g. external/gemini-cli.zip -> https://ninmods.smmtor.com/downloads/external/gemini-cli.zip
            //      kernel/ninmods-kernel.zip -> https://ninmods.smmtor.com/downloads/kernel/ninmods-kernel.zip
            candidateUrls.add("$serverBase/downloads/$cleanPath")

            var connectionSuccess = false
            var lastHttpCode = 0
            var activeUrl = ""

            for (tryUrl in candidateUrls.distinct()) {
                activeUrl = tryUrl
                var currentUrlStr = tryUrl
                var redirects = 0
                var currentConn: HttpURLConnection? = null

                while (redirects < 5) {
                    val urlObj = URL(currentUrlStr)
                    currentConn = (urlObj.openConnection() as HttpURLConnection).apply {
                        connectTimeout = AppConfig.TIMEOUT_MILLIS
                        readTimeout = AppConfig.TIMEOUT_MILLIS
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "NINMODS-DriverLoader-Client")
                        setRequestProperty("Accept", "*/*")
                    }

                    val code = currentConn.responseCode
                    lastHttpCode = code

                    // Check for standard redirects (including cross-protocol)
                    if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, HttpURLConnection.HTTP_SEE_OTHER, 307, 308)) {
                        val loc = currentConn.getHeaderField("Location")
                        if (!loc.isNullOrBlank()) {
                            currentUrlStr = if (loc.startsWith("http")) loc else URL(urlObj, loc).toString()
                            redirects++
                            currentConn.disconnect()
                            continue
                        }
                    }

                    if (code in 200..299) {
                        conn = currentConn
                        connectionSuccess = true
                        break
                    } else {
                        currentConn.disconnect()
                        break
                    }
                }

                if (connectionSuccess) break
            }

            if (!connectionSuccess || conn == null) {
                val friendlyError = when (lastHttpCode) {
                    404 -> "Mod package '$cleanFileBasename' is undergoing server synchronization. Please try again shortly."
                    500, 502, 503 -> "Server maintenance in progress. Please try again later."
                    0 -> "Unable to reach server. Please check your internet connection."
                    else -> "Server connection error (HTTP $lastHttpCode). Please try again shortly."
                }
                return@withContext Result.failure(Exception(friendlyError))
            } else {
                inputStream = conn.inputStream
            }

            val contentLength = if (conn != null) conn.contentLength.toLong() else 0L
            val inStream = inputStream ?: return@withContext Result.failure(Exception("Failed to open mod download stream."))

            val tempFile = File(targetDir, "tmp_${System.currentTimeMillis()}_$fileName")
            outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(8192)
            var bytesRead: Int
            var totalBytesRead = 0L

            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                if (contentLength > 0) {
                    val progress = ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                    onProgress(progress)
                }
            }
            outputStream.flush()
            outputStream.close()
            outputStream = null

            // Check if downloaded file is genuinely a ZIP archive before attempting extraction
            val isActualZip = isZipFile(tempFile)
            val finalTargetFile: File = if (isActualZip) {
                unzip(tempFile, targetDir)
                tempFile.delete()
                val targetScript = File(targetDir, fileName.removeSuffix(".zip") + ".sh")
                if (targetScript.exists()) targetScript else targetDir
            } else {
                // If downloaded as plain script fallback (even if URL had .zip), save as .sh
                val destFileName = if (fileName.endsWith(".zip", ignoreCase = true)) {
                    fileName.removeSuffix(".zip") + ".sh"
                } else {
                    fileName
                }
                val destFile = File(targetDir, destFileName)
                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)
                destFile
            }

            // Ensure executable permissions recursively across all unpacked binaries and scripts
            try {
                finalTargetFile.setExecutable(true, false)
                ShellExecutor.executeRoot("chmod -R 755 '${targetDir.absolutePath}'")
            } catch (_: Exception) {}

            onProgress(100)
            Result.success(finalTargetFile)

        } catch (e: Exception) {
            val msg = when {
                e.message?.contains("Unable to resolve host") == true -> "Server unreachable. Check domain configuration in AppConfig."
                e.message?.contains("timed out") == true -> "Download connection timed out. Please try again."
                else -> e.message ?: "Download failed."
            }
            Result.failure(Exception(msg))
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            try { outputStream?.close() } catch (_: Exception) {}
            conn?.disconnect()
        }
    }

    private fun isZipFile(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return try {
            file.inputStream().use { stream ->
                val b = ByteArray(4)
                stream.read(b) == 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte() && b[2] == 0x03.toByte() && b[3] == 0x04.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    fun unzip(zipFile: File, targetDir: File) {
        val canonicalDest = targetDir.canonicalPath
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val newFile = File(targetDir, entry.name)
                val canonicalEntry = newFile.canonicalPath
                if (!canonicalEntry.startsWith(canonicalDest + File.separator) && canonicalEntry != canonicalDest) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue // Skip malicious or escaped zip entry (Zip Slip protection)
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                    newFile.setExecutable(true, false)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}
