package com.andorid.ninmods.util

import java.io.File

object DeviceNodeManager {

    val TARGET_DEVICE_NODES = listOf(
        "/dev/BYNb",
        "/dev/mem_tool",
        "/dev/nh",
        "/dev/wanbai"
    )

    fun checkActiveNodes(): List<String> {
        val found = mutableListOf<String>()

        // 1. Root inspection via LibSu shell to bypass SELinux restrictions
        try {
            val checkCmd = "for n in /dev/BYNb /dev/mem_tool /dev/nh /dev/wanbai /dev/RT* /dev/rt* /dev/QX* /dev/qx*; do [ -e \"\$n\" ] && echo \"\$n\"; done; cat /proc/modules 2>/dev/null | grep -E 'BYNb|mem_tool|nh|wanbai|rt|RT|qx|QX|suvhwvwrtnb|Driver' | awk '{print \"/dev/\"\$1}'"
            val result = com.topjohnwu.superuser.Shell.cmd(checkCmd).exec()
            if (result.isSuccess) {
                for (line in result.out) {
                    val trimmed = line.trim()
                    if (trimmed.isNotBlank() && !found.contains(trimmed)) {
                        found.add(trimmed)
                    }
                }
            }
        } catch (_: Throwable) {}

        // 2. Direct filesystem fallback
        if (found.isEmpty()) {
            for (path in TARGET_DEVICE_NODES) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        found.add(path)
                    }
                } catch (_: Throwable) {}
            }
        }

        // 3. /proc/modules fallback
        if (found.isEmpty()) {
            try {
                val procModules = File("/proc/modules")
                if (procModules.exists() && procModules.canRead()) {
                    val text = procModules.readText()
                    for (mod in listOf("BYNb", "mem_tool", "nh", "wanbai", "suvhwvwrtnb", "rtnb", "RTdrivers")) {
                        if (text.contains(mod, ignoreCase = true)) {
                            found.add("/dev/$mod")
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        return found.distinct()
    }
}
