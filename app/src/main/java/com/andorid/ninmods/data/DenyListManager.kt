package com.andorid.ninmods.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class AppProcessItem(
    val processName: String,
    val isHidden: Boolean
)

data class InstalledAppItem(
    val appName: String,
    val packageName: String,
    val isSystemApp: Boolean,
    val isGameOrTarget: Boolean,
    val processes: List<AppProcessItem>,
    val isFullyHidden: Boolean,
    val isPartiallyHidden: Boolean
)

data class RootEnvironmentInfo(
    val rootAvailable: Boolean,
    val providerName: String,
    val isMagisk: Boolean,
    val denylistEnabled: Boolean,
    val totalHiddenPackages: Int,
    val totalHiddenProcesses: Int,
    val statusMessage: String
)

object DenyListManager {

    val TARGET_PACKAGES = setOf(
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.google.android.gms",
        "com.google.android.play.games",
        "com.android.vending"
    )

    /**
     * Inspects active root environment and Magisk DenyList configuration.
     */
    suspend fun detectEnvironment(): RootEnvironmentInfo = withContext(Dispatchers.IO) {
        val rootGranted = ShellExecutor.isRootAvailable()
        if (!rootGranted) {
            return@withContext RootEnvironmentInfo(
                rootAvailable = false,
                providerName = "Root Access Not Granted",
                isMagisk = false,
                denylistEnabled = false,
                totalHiddenPackages = 0,
                totalHiddenProcesses = 0,
                statusMessage = "Root permission (su) required. Grant root in Magisk, KernelSU, or APatch."
            )
        }

        // Test Magisk CLI
        val magiskVerRes = try {
            Shell.cmd("magisk -v").exec()
        } catch (_: Exception) {
            null
        }

        val isMagisk = magiskVerRes != null && magiskVerRes.isSuccess && magiskVerRes.out.isNotEmpty()
        val magiskVerString = if (isMagisk) magiskVerRes!!.out.firstOrNull()?.trim() ?: "Magisk" else null

        val providerName = when {
            isMagisk -> magiskVerString ?: "Magisk (Zygisk)"
            File("/data/adb/ksu").exists() || File("/data/adb/ksud").exists() -> "KernelSU (Kernel Mount Isolation)"
            File("/data/adb/ap").exists() -> "APatch (KernelPatch)"
            else -> "Superuser (Root Active)"
        }

        var denylistEnabled = false
        var hiddenPkgs = 0
        var hiddenProcs = 0
        var statusMsg = ""

        if (isMagisk) {
            val statusRes = Shell.cmd("magisk --denylist status").exec()
            denylistEnabled = statusRes.isSuccess

            val listRes = Shell.cmd("magisk --denylist ls").exec()
            if (listRes.isSuccess) {
                val lines = listRes.out.map { it.trim() }.filter { it.isNotBlank() }
                hiddenProcs = lines.size
                hiddenPkgs = lines.map { it.split("|")[0] }.distinct().size
            }

            statusMsg = if (denylistEnabled) {
                "Magisk DenyList is Active & Enforcing on Zygote"
            } else {
                "Magisk DenyList is currently Disabled. Toggle to activate."
            }
        } else {
            statusMsg = "Active Provider: $providerName. Native Magisk CLI not found."
        }

        RootEnvironmentInfo(
            rootAvailable = true,
            providerName = providerName,
            isMagisk = isMagisk,
            denylistEnabled = denylistEnabled,
            totalHiddenPackages = hiddenPkgs,
            totalHiddenProcesses = hiddenProcs,
            statusMessage = statusMsg
        )
    }

    /**
     * Reads all raw entries from Magisk DenyList.
     * Output format: "pkg|proc" or "pkg"
     */
    suspend fun getActiveDenyList(): Set<String> = withContext(Dispatchers.IO) {
        val result = mutableSetOf<String>()
        try {
            val res = Shell.cmd("magisk --denylist ls").exec()
            if (res.isSuccess) {
                for (line in res.out) {
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank()) {
                        result.add(trimmed)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    /**
     * Globally enable or disable Magisk DenyList enforcement.
     */
    suspend fun setDenyListEnabled(enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = if (enable) "magisk --denylist enable" else "magisk --denylist disable"
            val res = Shell.cmd(cmd).exec()
            res.isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Adds an entire app (and all its sub-processes) or a specific process to Magisk DenyList.
     */
    suspend fun addToDenyList(packageName: String, processName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = if (processName.isNullOrBlank()) {
                "magisk --denylist add $packageName"
            } else {
                "magisk --denylist add $packageName $processName"
            }
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes an entire app or a specific process from Magisk DenyList.
     */
    suspend fun removeFromDenyList(packageName: String, processName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val cmd = if (processName.isNullOrBlank()) {
                "magisk --denylist rm $packageName"
            } else {
                "magisk --denylist rm $packageName $processName"
            }
            Shell.cmd(cmd).exec().isSuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Scans installed applications on the device, extracts unique component process names,
     * and maps their live status against Magisk DenyList.
     */
    suspend fun getInstalledApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val denyList = getActiveDenyList()

        val flags = PackageManager.GET_ACTIVITIES or
                PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or
                PackageManager.GET_PROVIDERS

        val rawList: List<PackageInfo> = try {
            pm.getInstalledPackages(flags)
        } catch (_: Exception) {
            emptyList()
        }

        val appItems = mutableListOf<InstalledAppItem>()

        for (pkgInfo in rawList) {
            val appInfo = pkgInfo.applicationInfo ?: continue
            val pkgName = pkgInfo.packageName ?: continue
            val appLabel = try {
                appInfo.loadLabel(pm).toString()
            } catch (_: Exception) {
                pkgName
            }

            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isTarget = TARGET_PACKAGES.contains(pkgName) ||
                    pkgName.contains("freefire", ignoreCase = true)

            // Extract all unique process names across components
            val processesSet = mutableSetOf<String>()
            val baseProcess = appInfo.processName ?: pkgName
            processesSet.add(baseProcess)

            pkgInfo.activities?.forEach { act ->
                act.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
            }
            pkgInfo.services?.forEach { srv ->
                srv.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
            }
            pkgInfo.receivers?.forEach { rec ->
                rec.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
            }
            pkgInfo.providers?.forEach { prv ->
                prv.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
            }

            val processItems = processesSet.sorted().map { procName ->
                val isHidden = denyList.contains("$pkgName|$procName") ||
                        denyList.contains(pkgName)
                AppProcessItem(
                    processName = procName,
                    isHidden = isHidden
                )
            }

            val hiddenCount = processItems.count { it.isHidden }
            val isFullyHidden = processItems.isNotEmpty() && hiddenCount == processItems.size
            val isPartiallyHidden = hiddenCount > 0 && hiddenCount < processItems.size

            appItems.add(
                InstalledAppItem(
                    appName = appLabel,
                    packageName = pkgName,
                    isSystemApp = isSystem,
                    isGameOrTarget = isTarget,
                    processes = processItems,
                    isFullyHidden = isFullyHidden,
                    isPartiallyHidden = isPartiallyHidden
                )
            )
        }

        // Sort: Games & Target apps first, then alphabetically
        appItems.sortedWith(
            compareByDescending<InstalledAppItem> { it.isGameOrTarget }
                .thenByDescending { it.isFullyHidden || it.isPartiallyHidden }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * 1-Tap Quick Action: Auto-enables DenyList and cloaks Free Fire, Free Fire MAX,
     * and Google Play Services/Framework processes.
     */
    suspend fun quickCloakGames(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // 1. Ensure DenyList is enabled
            setDenyListEnabled(true)

            var addedCount = 0
            val pm = context.packageManager
            val flags = PackageManager.GET_ACTIVITIES or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_RECEIVERS or
                    PackageManager.GET_PROVIDERS

            for (targetPkg in TARGET_PACKAGES) {
                val pkgInfo = try {
                    pm.getPackageInfo(targetPkg, flags)
                } catch (_: Exception) {
                    null
                }

                if (pkgInfo != null) {
                    val appInfo = pkgInfo.applicationInfo
                    val processesSet = mutableSetOf<String>()
                    val baseProcess = appInfo?.processName ?: targetPkg
                    processesSet.add(baseProcess)

                    pkgInfo.activities?.forEach { act ->
                        act.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
                    }
                    pkgInfo.services?.forEach { srv ->
                        srv.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
                    }
                    pkgInfo.receivers?.forEach { rec ->
                        rec.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
                    }
                    pkgInfo.providers?.forEach { prv ->
                        prv.processName?.takeIf { it.isNotBlank() }?.let { processesSet.add(it) }
                    }

                    for (proc in processesSet) {
                        if (addToDenyList(targetPkg, proc)) {
                            addedCount++
                        }
                    }
                } else {
                    // Fallback: register package name directly even if not installed right now
                    if (addToDenyList(targetPkg)) {
                        addedCount++
                    }
                }
            }
            Result.success(addedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
