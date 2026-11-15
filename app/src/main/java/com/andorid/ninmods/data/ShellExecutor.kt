package com.andorid.ninmods.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader

object ShellExecutor {

    sealed class ShellOutput {
        data class Line(val text: String) : ShellOutput()
        data class Complete(val exitCode: Int, val success: Boolean) : ShellOutput()
        data class Error(val message: String) : ShellOutput()
    }

    /**
     * Executes commands in a genuine root 'su' shell session.
     * Streams stdout and stderr line-by-line in real time.
     */
    fun executeRootCommand(commands: List<String>): Flow<ShellOutput> = flow {
        var process: Process? = null
        try {
            process = ProcessBuilder("su")
                .redirectErrorStream(false)
                .start()

            val outputStream = DataOutputStream(process.outputStream)
            for (cmd in commands) {
                outputStream.write((cmd + "\n").toByteArray(Charsets.UTF_8))
            }
            outputStream.write("exit $?\n".toByteArray(Charsets.UTF_8))
            outputStream.flush()

            val inputStream = BufferedReader(InputStreamReader(process.inputStream))
            val errorStream = BufferedReader(InputStreamReader(process.errorStream))

            var line: String?
            while (inputStream.readLine().also { line = it } != null) {
                emit(ShellOutput.Line(line!!))
            }

            while (errorStream.readLine().also { line = it } != null) {
                emit(ShellOutput.Line("[STDERR] ${line!!}"))
            }

            val exitCode = process.waitFor()
            emit(ShellOutput.Complete(exitCode = exitCode, success = exitCode == 0))

        } catch (e: Exception) {
            // Attempt fallback through /system/bin/su or /data/adb/ap/bin/su if generic 'su' isn't in default PATH
            try {
                val suBin = listOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/data/adb/ap/bin/su",
                    "/data/adb/magisk/su"
                ).firstOrNull { java.io.File(it).exists() } ?: "su"

                val joinedCmd = commands.joinToString(" && ")
                val fbProc = ProcessBuilder(suBin, "-c", joinedCmd).start()
                val reader = BufferedReader(InputStreamReader(fbProc.inputStream))
                var fbLine: String?
                while (reader.readLine().also { fbLine = it } != null) {
                    emit(ShellOutput.Line(fbLine!!))
                }
                val code = fbProc.waitFor()
                emit(ShellOutput.Complete(exitCode = code, success = code == 0))
            } catch (fe: Exception) {
                emit(ShellOutput.Error("Root execution error: ${e.message}"))
            }
        } finally {
            process?.destroy()
        }
    }.flowOn(Dispatchers.IO)

    data class CommandResult(val isSuccess: Boolean, val output: String)

    /**
     * Executes a single command as root synchronously and returns exit status with output.
     */
    fun executeRootWithOutput(command: String): CommandResult {
        return try {
            val res = com.topjohnwu.superuser.Shell.cmd(command).exec()
            val text = res.out.joinToString("\n")
            CommandResult(res.isSuccess, text)
        } catch (_: Exception) {
            try {
                val suBin = listOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/data/adb/ap/bin/su",
                    "/data/adb/magisk/su"
                ).firstOrNull { java.io.File(it).exists() } ?: "su"

                val proc = ProcessBuilder(suBin, "-c", command).start()
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val sb = StringBuilder()
                var l: String?
                while (reader.readLine().also { l = it } != null) {
                    sb.append(l).append("\n")
                }
                val exitCode = proc.waitFor()
                CommandResult(exitCode == 0, sb.toString().trim())
            } catch (e: Exception) {
                CommandResult(false, e.message ?: "")
            }
        }
    }

    /**
     * Executes a single command as root synchronously on the calling thread (or dispatcher).
     * Leverages LibSu with automatic fallback to ProcessBuilder.
     */
    fun executeRoot(command: String): Boolean {
        return try {
            val res = com.topjohnwu.superuser.Shell.cmd(command).exec()
            res.isSuccess
        } catch (_: Exception) {
            try {
                val suBin = listOf(
                    "/system/bin/su",
                    "/system/xbin/su",
                    "/data/adb/ap/bin/su",
                    "/data/adb/magisk/su"
                ).firstOrNull { java.io.File(it).exists() } ?: "su"

                val proc = ProcessBuilder(suBin, "-c", command).start()
                val exitCode = proc.waitFor()
                exitCode == 0
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * Checks if genuine Superuser (Root) access is granted and available.
     * Uses APatch official LibSu root verification with fallback checks.
     */
    fun isRootAvailable(): Boolean {
        return try {
            com.topjohnwu.superuser.Shell.isAppGrantedRoot() == true || com.topjohnwu.superuser.Shell.getShell().isRoot
        } catch (_: Exception) {
            val knownSuPaths = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/data/adb/ap/bin/su",
                "/data/adb/magisk/su",
                "/data/adb/ksu/bin/su"
            )
            val hasBinary = knownSuPaths.any { java.io.File(it).exists() }

            try {
                val suBin = knownSuPaths.firstOrNull { java.io.File(it).exists() } ?: "su"
                val proc = ProcessBuilder(suBin, "-c", "id").start()
                val reader = BufferedReader(InputStreamReader(proc.inputStream))
                val line = reader.readLine()
                val exitCode = proc.waitFor()
                exitCode == 0 && (line?.contains("uid=0") == true || hasBinary)
            } catch (_: Exception) {
                hasBinary
            }
        }
    }
}
