package com.andorid.ninmods.data

import android.content.Context
import android.content.SharedPreferences
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
    val isAPatch: Boolean,
    val isKernelSU: Boolean,
    val denylistEnabled: Boolean,
    val totalHiddenPackages: Int,
    val totalHiddenProcesses: Int,
    val statusMessage: String
)

/**
 * Universal Root Cloak & Isolation Engine.
 * Seamlessly harmonizes APatch (KernelPatch), KernelSU, and Magisk.
 */
object DenyListManager {

    private const val PREFS_NAME = "ninmods_root_cloak_prefs"
    private const val KEY_CLOAK_ENABLED = "cloak_global_enabled"
    private const val KEY_PROTECTED_ENTRIES = "protected_entries"

    val TARGET_PACKAGES = setOf(
        "com.dts.freefireth",
        "com.dts.freefiremax",
        "com.tencent.ig",
        "com.pubg.imobile",
        "com.google.android.gms",
        "com.google.android.play.games",
        "com.android.vending"
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Inspects active root environment and harmonizes APatch, KernelSU, and Magisk.
     */
    suspend fun detectEnvironment(context: Context): RootEnvironmentInfo = withContext(Dispatchers.IO) {
        val rootGranted = ShellExecutor.isRootAvailable()
        if (!rootGranted) {
            return@withContext RootEnvironmentInfo(
                rootAvailable = false,
                providerName = "Daily Protection Engine",
                isMagisk = false,
                isAPatch = false,
                isKernelSU = false,
                denylistEnabled = false,
                totalHiddenPackages = 0,
                totalHiddenProcesses = 0,
                statusMessage = "Root permission required"
            )
        }

        // Test Magisk CLI
        val magiskVerRes = try {
            Shell.cmd("magisk -v").exec()
        } catch (_: Exception) {
            null
        }
        val isMagisk = magiskVerRes != null && magiskVerRes.isSuccess && magiskVerRes.out.isNotEmpty()
        val isKSU = File("/data/adb/ksu").exists() || File("/data/adb/ksud").exists()
        val isAPatch = File("/data/adb/ap").exists() || (!isMagisk && !isKSU)

        val providerName = when {
            isMagisk -> "Magisk (Zygisk Cloak)"
            isAPatch -> "APatch (KernelPatch Cloak)"
            isKSU -> "KernelSU (Mount Isolation)"
            else -> "Superuser Engine"
        }

        val prefs = getPrefs(context)
        val activeEntries = getActiveDenyList(context)
        val hiddenPkgs = activeEntries.map { it.split("|")[0] }.distinct().size
        val hiddenProcs = activeEntries.size

        val denylistEnabled: Boolean = if (isMagisk) {
            val statusRes = Shell.cmd("magisk --denylist status").exec()
            if (statusRes.isSuccess) true else prefs.getBoolean(KEY_CLOAK_ENABLED, true)
        } else {
            prefs.getBoolean(KEY_CLOAK_ENABLED, true)
        }

        val statusMsg = if (denylistEnabled) "Isolation Active" else "Isolation Disabled"

        RootEnvironmentInfo(
            rootAvailable = true,
            providerName = providerName,
            isMagisk = isMagisk,
            isAPatch = isAPatch,
            isKernelSU = isKSU,
            denylistEnabled = denylistEnabled,
            totalHiddenPackages = hiddenPkgs,
            totalHiddenProcesses = hiddenProcs,
            statusMessage = statusMsg
        )
    }

    /**
     * Reads all active cloaked entries ("pkg" or "pkg|proc").
     */
    suspend fun getActiveDenyList(context: Context): Set<String> = withContext(Dispatchers.IO) {
        val prefs = getPrefs(context)
        val stored = prefs.getStringSet(KEY_PROTECTED_ENTRIES, emptySet())?.toMutableSet() ?: mutableSetOf()

        // Sync with Magisk if running on Magisk
        try {
            val res = Shell.cmd("magisk --denylist ls").exec()
            if (res.isSuccess) {
                for (line in res.out) {
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank()) {
                        stored.add(trimmed)
                    }
                }
            }
        } catch (_: Exception) {}

        stored
    }

    /**
     * Globally enable or disable Root Cloak enforcement across the entire device.
     */
    suspend fun setDenyListEnabled(context: Context, enable: Boolean): Boolean = withContext(Dispatchers.IO) {
        try {
            getPrefs(context).edit().putBoolean(KEY_CLOAK_ENABLED, enable).apply()

            // 1. Magisk compatibility
            Shell.cmd(if (enable) "magisk --denylist enable" else "magisk --denylist disable").exec()

            // 2. Global Android Properties Spoofing (Stock Device Cloaking)
            if (enable) {
                applyGlobalPropertySpoof()
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Spoofs Android build properties to make device appear as 100% stock non-root.
     */
    private fun applyGlobalPropertySpoof() {
        val commands = listOf(
            "setprop ro.build.tags release-keys",
            "setprop ro.build.type user",
            "setprop ro.debuggable 0",
            "setprop service.adb.root 0",
            "setprop ro.secure 1",
            "resetprop -n ro.build.tags release-keys 2>/dev/null",
            "resetprop -n ro.build.type user 2>/dev/null",
            "resetprop -n ro.debuggable 0 2>/dev/null",
            "resetprop -n service.adb.root 0 2>/dev/null"
        )
        for (cmd in commands) {
            try {
                Shell.cmd(cmd).exec()
            } catch (_: Exception) {}
        }
    }

    /**
     * Adds an app or specific process to the isolation list.
     */
    suspend fun addToDenyList(context: Context, packageName: String, processName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val entry = if (processName.isNullOrBlank()) packageName else "$packageName|$processName"
            val prefs = getPrefs(context)
            val current = prefs.getStringSet(KEY_PROTECTED_ENTRIES, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(entry)
            prefs.edit().putStringSet(KEY_PROTECTED_ENTRIES, current).apply()

            // 1. Try Magisk CLI if available
            val magiskCmd = if (processName.isNullOrBlank()) {
                "magisk --denylist add $packageName"
            } else {
                "magisk --denylist add $packageName $processName"
            }
            Shell.cmd(magiskCmd).exec()

            // 2. KernelSU compatibility
            Shell.cmd("ksud profile set $packageName root 0 2>/dev/null").exec()

            // 3. APatch / Shamiko sync if present
            try {
                if (File("/data/adb/shamiko").exists()) {
                    Shell.cmd("echo '$packageName' >> /data/adb/shamiko/whitelist 2>/dev/null").exec()
                }
            } catch (_: Exception) {}

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Removes an app or specific process from the isolation list.
     */
    suspend fun removeFromDenyList(context: Context, packageName: String, processName: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val entry = if (processName.isNullOrBlank()) packageName else "$packageName|$processName"
            val prefs = getPrefs(context)
            val current = prefs.getStringSet(KEY_PROTECTED_ENTRIES, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.remove(entry)
            current.remove(packageName)
            prefs.edit().putStringSet(KEY_PROTECTED_ENTRIES, current).apply()

            // Magisk CLI
            val magiskCmd = if (processName.isNullOrBlank()) {
                "magisk --denylist rm $packageName"
            } else {
                "magisk --denylist rm $packageName $processName"
            }
            Shell.cmd(magiskCmd).exec()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Scans installed applications and maps their status against active Root Cloak.
     */
    suspend fun getInstalledApps(context: Context): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val denyList = getActiveDenyList(context)

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
            val pkgName = pkgInfo.packageName ?: continue
            val appInfo = pkgInfo.applicationInfo ?: continue

            // Exclude our own app from being hidden
            if (pkgName == context.packageName) continue

            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isTarget = TARGET_PACKAGES.contains(pkgName) ||
                    pkgName.contains("freefire", ignoreCase = true) ||
                    pkgName.contains("pubg", ignoreCase = true)

            // Hide uninteresting system apps unless they are targets
            if (isSystem && !isTarget) continue

            val appLabel = appInfo.loadLabel(pm).toString()
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

        appItems.sortedWith(
            compareByDescending<InstalledAppItem> { it.isGameOrTarget }
                .thenByDescending { it.isFullyHidden || it.isPartiallyHidden }
                .thenBy { it.appName.lowercase() }
        )
    }

    /**
     * 1-Tap Quick Action: Auto-enables Root Cloak and isolates Free Fire, Free Fire MAX,
     * Google Play Services, and Game engines.
     */
    suspend fun quickCloakGames(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            setDenyListEnabled(context, true)
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
                        if (addToDenyList(context, targetPkg, proc)) {
                            addedCount++
                        }
                    }
                } else {
                    if (addToDenyList(context, targetPkg)) {
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
