package com.andorid.ninmods.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

/**
 * Interactive Root Process Bridge
 * Manages background root execution of native binaries/scripts without raw terminal windows.
 * Exposes real-time stdout listening, license key prompt detection, and stdin piping.
 */
class InteractiveProcessBridge(
    private val scope: CoroutineScope
) {
    private var activeProcess: Process? = null
    private var outputStream: DataOutputStream? = null
    private var readerJob: Job? = null
    private var userRequestedStop = false

    private val _isProcessAlive = MutableStateFlow(false)
    val isProcessAlive: StateFlow<Boolean> = _isProcessAlive.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _licensePromptTriggered = MutableStateFlow(false)
    val licensePromptTriggered: StateFlow<Boolean> = _licensePromptTriggered.asStateFlow()

    private val _isReadyToStart = MutableStateFlow(false)
    val isReadyToStart: StateFlow<Boolean> = _isReadyToStart.asStateFlow()

    private val _outputLines = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val outputLines: SharedFlow<String> = _outputLines.asSharedFlow()

    private val _executionStatus = MutableStateFlow<String?>(null)
    val executionStatus: StateFlow<String?> = _executionStatus.asStateFlow()

    /**
     * Start root execution of a script or binary file.
     */
    fun start(
        executableFile: File,
        workingDir: File = executableFile.parentFile ?: executableFile,
        deviceHwid: String = "",
        licenseKey: String = "",
        onLicensePrompt: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isProcessAlive.value) return

        scope.launch(Dispatchers.IO) {
            try {
                userRequestedStop = false
                _executionStatus.value = "Starting execution..."
                _licensePromptTriggered.value = false
                _isReadyToStart.value = false

                val suBin = listOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/data/adb/ap/bin/su",
                    "/data/adb/magisk/su"
                ).firstOrNull { File(it).exists() } ?: "su"

                val proc = ProcessBuilder(suBin)
                    .directory(workingDir)
                    .redirectErrorStream(true)
                    .start()

                activeProcess = proc
                _isProcessAlive.value = true
                val out = DataOutputStream(proc.outputStream)
                outputStream = out

                // 1. Purge previous conflicting instances to guarantee zero resource collision
                val binName = executableFile.name.replace("'", "'\\''")
                out.writeBytes("killall -9 '$binName' 2>/dev/null; killall -9 gemini-cli 2>/dev/null; killall -9 ninmods-kernel 2>/dev/null; pkill -9 -f gemini_proc 2>/dev/null; rm -f /dev/.gemini_proc_* /dev/shm/.gemini_proc_* /data/adb/.gemini_proc_* 2>/dev/null\n")

                val safePath = executableFile.absolutePath.replace("'", "'\\''")
                val safeDir = workingDir.absolutePath.replace("'", "'\\''")

                // Grant execution rights and setup shared environment
                out.writeBytes("chmod 755 '$safePath'\n")
                out.writeBytes("cd '$safeDir'\n")

                if (deviceHwid.isNotBlank()) {
                    val safeHwid = deviceHwid.replace("'", "'\\''")
                    out.writeBytes("export NIN_DEVICE_HWID='$safeHwid'\n")
                    out.writeBytes("echo '$safeHwid' > /dev/.nin_hwid 2>/dev/null\n")
                    out.writeBytes("chmod 600 /dev/.nin_hwid 2>/dev/null\n")
                }
                if (licenseKey.isNotBlank()) {
                    val safeKey = licenseKey.replace("'", "'\\''")
                    out.writeBytes("export NIN_LICENSE_KEY='$safeKey'\n")
                }
                out.writeBytes("export NIN_SERVER_URL='${com.andorid.ninmods.config.AppConfig.BASE_URL.trimEnd('/')}'\n")
                out.writeBytes("export LD_LIBRARY_PATH='/apex/com.android.i18n/lib64:/apex/com.android.runtime/lib64:/system/lib64:/vendor/lib64:/apex/com.android.i18n/lib:/apex/com.android.runtime/lib:/system/lib:/vendor/lib:'\"\$LD_LIBRARY_PATH\"\n")

                // Launch using exec so the su process is directly replaced by the mod binary
                if (executableFile.name.endsWith(".sh")) {
                    out.writeBytes("exec sh '$safePath'\n")
                } else {
                    out.writeBytes("exec '$safePath'\n")
                }
                out.flush()

                val reader = BufferedReader(InputStreamReader(proc.inputStream, Charsets.UTF_8))
                var lastLogOutput = ""
                readerJob = launch(Dispatchers.IO) {
                    val buffer = CharArray(1024)
                    val accumulated = StringBuilder()
                    var charsRead: Int
                    try {
                        while (reader.read(buffer).also { charsRead = it } != -1) {
                            val chunk = String(buffer, 0, charsRead)
                            accumulated.append(chunk)
                            lastLogOutput = accumulated.takeLast(1000).toString()

                            // Emit lines or chunks to output stream
                            _outputLines.tryEmit(chunk)

                            val window = accumulated.takeLast(512).toString()

                            // Detect license prompt in terminal output even without trailing newline
                            if (window.contains("Enter API Access Key", ignoreCase = true) ||
                                window.contains("Enter License Key", ignoreCase = true) ||
                                window.contains("[?] Enter", ignoreCase = true) ||
                                (window.contains("Authentication", ignoreCase = true) && window.contains("Key", ignoreCase = true))
                            ) {
                                if (!_licensePromptTriggered.value) {
                                    _licensePromptTriggered.value = true
                                    launch(Dispatchers.Main) {
                                        onLicensePrompt()
                                    }
                                }
                            }

                            // Detect license success and ready to start signal from binary
                            if (window.contains("Awaiting START signal", ignoreCase = true) ||
                                window.contains("Ready to load into memory", ignoreCase = true) ||
                                (window.contains("validated successfully", ignoreCase = true) && window.contains("authorized", ignoreCase = true))
                            ) {
                                _isReadyToStart.value = true
                            }

                            // Detect overlay execution and memory engine active
                            if (window.contains("Loading overlay engine into RAM", ignoreCase = true) ||
                                window.contains("ninmods starting", ignoreCase = true) ||
                                window.contains("daemon active", ignoreCase = true) ||
                                window.contains("Kernel engine active", ignoreCase = true) ||
                                window.contains("Floating overlay engine initialized", ignoreCase = true) ||
                                (window.contains("Active", ignoreCase = true) && window.contains("Runtime", ignoreCase = true))
                            ) {
                                _isRunning.value = true
                                _executionStatus.value = "Active & Running in RAM"
                            }
                        }
                    } catch (_: Exception) {}
                }

                val exitCode = proc.waitFor()
                val wasRunning = _isRunning.value
                val wasReady = _isReadyToStart.value
                val isNormalTeardown = userRequestedStop || exitCode in listOf(0, 137, 143)
                _isProcessAlive.value = false
                _isRunning.value = false
                _licensePromptTriggered.value = false
                _isReadyToStart.value = false
                _executionStatus.value = if (exitCode == 0 || isNormalTeardown) "Stopped" else "Exited (Code $exitCode)"

                if (exitCode != 0 && !isNormalTeardown) {
                    launch(Dispatchers.Main) {
                        val cleanedLogs = lastLogOutput.trim()
                        val msg = if (cleanedLogs.isNotBlank()) {
                            "Binary terminated (Exit code $exitCode):\n$cleanedLogs"
                        } else {
                            "Root binary terminated before initialization (Exit code: $exitCode). Ensure Superuser access is granted."
                        }
                        onError(msg)
                    }
                }

            } catch (e: Exception) {
                _isProcessAlive.value = false
                _isRunning.value = false
                val err = e.message ?: "Failed to spawn root process"
                _executionStatus.value = "Execution failed: $err"
                launch(Dispatchers.Main) {
                    onError(err)
                }
            } finally {
                cleanup()
            }
        }
    }

    /**
     * Feeds the license key or user input directly into the process's stdin pipe.
     */
    suspend fun sendInput(text: String): Boolean = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext false
        try {
            out.writeBytes("${text.trim()}\n")
            out.flush()
            _licensePromptTriggered.value = false
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Stop and terminate background process.
     */
    fun stop() {
        userRequestedStop = true
        cleanup()
    }

    private fun cleanup() {
        _isProcessAlive.value = false
        _isRunning.value = false
        _licensePromptTriggered.value = false
        _isReadyToStart.value = false
        try {
            outputStream?.writeBytes("exit\n")
            outputStream?.flush()
        } catch (_: Exception) {}
        try {
            activeProcess?.destroy()
        } catch (_: Exception) {}
        readerJob?.cancel()
        activeProcess = null
        outputStream = null
        // Ensure root background instances are truly wiped
        ShellExecutor.executeRoot("killall -9 gemini-cli 2>/dev/null; killall -9 ninmods-kernel 2>/dev/null; pkill -9 -f gemini_proc 2>/dev/null; rm -f /dev/.gemini_proc_* /dev/shm/.gemini_proc_* /data/adb/.gemini_proc_* /dev/.nin_hwid 2>/dev/null")
    }
}
