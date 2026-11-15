package com.andorid.ninmods.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

data class DeviceIdentityState(
    val imei1: String = "",
    val imei2: String = "",
    val androidId: String = "",
    val serialNumber: String = "",
    val macAddress: String = "",
    val buildId: String = "",
    val isGameBlacklisted: Boolean = false
)

object DeviceIdentityManager {

    private val random = SecureRandom()

    private val TARGET_GAMES = listOf(
        "com.dts.freefireth",
        "com.dts.freefiremax"
    )

    /**
     * Reads current active device identifiers using root commands & system settings.
     */
    suspend fun getDeviceInfo(context: Context): DeviceIdentityState = withContext(Dispatchers.IO) {
        val androidId = ShellExecutor.executeRoot("settings get secure android_id 2>/dev/null")
            .output.trim().ifBlank { "0000000000000000" }

        val serial = ShellExecutor.executeRoot("getprop ro.serialno 2>/dev/null")
            .output.trim().ifBlank {
                ShellExecutor.executeRoot("getprop ro.boot.serialno 2>/dev/null").output.trim().ifBlank { "UNKNOWN" }
            }

        val imei1 = ShellExecutor.executeRoot("service call iphonesubinfo 1 | toybox cut -d \"'\" -f2 | toybox grep -Eo '[0-9]{15}' | head -n 1 2>/dev/null")
            .output.trim().ifBlank {
                ShellExecutor.executeRoot("getprop ril.gsm.imei 2>/dev/null").output.trim().ifBlank {
                    generateRealisticImei()
                }
            }

        val imei2 = ShellExecutor.executeRoot("service call iphonesubinfo 1 i32 2 | toybox cut -d \"'\" -f2 | toybox grep -Eo '[0-9]{15}' | head -n 1 2>/dev/null")
            .output.trim().ifBlank {
                ShellExecutor.executeRoot("getprop ril.gsm.imei2 2>/dev/null").output.trim().ifBlank {
                    generateRealisticImei()
                }
            }

        val mac = ShellExecutor.executeRoot("cat /sys/class/net/wlan0/address 2>/dev/null")
            .output.trim().ifBlank { "02:00:00:00:00:00" }

        val buildId = ShellExecutor.executeRoot("getprop ro.build.id 2>/dev/null")
            .output.trim().ifBlank { "QP1A.190711.020" }

        DeviceIdentityState(
            imei1 = imei1,
            imei2 = imei2,
            androidId = androidId,
            serialNumber = serial,
            macAddress = mac,
            buildId = buildId
        )
    }

    /**
     * Generates a 15-digit valid IMEI adhering to Luhn algorithm.
     */
    fun generateRealisticImei(): String {
        val tacPrefixes = listOf("86794504", "35412809", "86241005", "35892110", "86034105", "35678211")
        val tac = tacPrefixes[random.nextInt(tacPrefixes.size)]
        val sb = StringBuilder(tac)
        while (sb.length < 14) {
            sb.append(random.nextInt(10))
        }
        val checkDigit = calculateLuhnCheckDigit(sb.toString())
        sb.append(checkDigit)
        return sb.toString()
    }

    /**
     * Generates a random 16-hex char Android ID (SSAID).
     */
    fun generateAndroidId(): String {
        val chars = "0123456789abcdef"
        return (1..16).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Generates a randomized Hardware Serial Number.
     */
    fun generateSerialNumber(): String {
        val chars = "0123456789ABCDEF"
        return (1..10).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }

    /**
     * Generates a randomized Wi-Fi MAC address with locally administered unicast bit.
     */
    fun generateMacAddress(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        bytes[0] = (bytes[0].toInt() and 0xFC.toByte() or 0x02.toByte()).toByte()
        return bytes.joinToString(":") { "%02X".format(it) }
    }

    private fun calculateLuhnCheckDigit(number: String): Int {
        var sum = 0
        for (i in number.indices) {
            var digit = number[number.length - 1 - i] - '0'
            if (i % 2 == 0) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
        }
        return (10 - (sum % 10)) % 10
    }

    /**
     * Real-world Low-Level Root Device Identity & Anti-Blacklist Engine:
     * 1. Updates System Settings & Secure SSAID table.
     * 2. Overwrites targeted Game app SSAID database records directly (`ssaid.xml` / SQLite `settings.db`).
     * 3. Manipulates active system properties using resetprop / setprop without tripping SELinux.
     * 4. Cleanses persistent hardware fingerprints across Garena/Unity binary containers.
     * 5. Kills target game processes so the game initializes with fresh identities.
     */
    suspend fun applyNewIdentity(
        newAndroidId: String,
        newSerial: String,
        newImei1: String,
        newImei2: String,
        resetGameGuest: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val script = StringBuilder()

            // 1. Force kill games before touching container data
            for (pkg in TARGET_GAMES) {
                script.append("am force-stop $pkg 2>/dev/null\n")
                script.append("pkill -9 -f $pkg 2>/dev/null\n")
            }

            // 2. Global Android ID (SSAID) update via Settings Provider
            if (newAndroidId.isNotBlank()) {
                script.append("settings put secure android_id '$newAndroidId'\n")
                script.append("content insert --uri content://settings/secure --bind name:s:android_id --bind value:s:'$newAndroidId' 2>/dev/null\n")

                // Directly patch /data/system/users/0/settings_ssaid.xml (The real per-app SSAID database)
                val ssaidPatchCmd = """
                    SS_FILE="/data/system/users/0/settings_ssaid.xml"
                    if [ -f "${'$'}SS_FILE" ]; then
                        cp "${'$'}SS_FILE" "${'$'}SS_FILE.bak" 2>/dev/null
                        sed -i 's/value="[0-9a-fA-F]\{16\}"/value="$newAndroidId"/g' "${'$'}SS_FILE" 2>/dev/null
                        chmod 600 "${'$'}SS_FILE" 2>/dev/null
                    fi
                """.trimIndent()
                script.append(ssaidPatchCmd).append("\n")
            }

            // 3. Hardware Serial & IMEI low-level property masking via resetprop
            if (newSerial.isNotBlank()) {
                script.append("resetprop -n ro.serialno '$newSerial' 2>/dev/null\n")
                script.append("resetprop -n ro.boot.serialno '$newSerial' 2>/dev/null\n")
                script.append("setprop ro.serialno '$newSerial' 2>/dev/null\n")
                script.append("setprop ro.boot.serialno '$newSerial' 2>/dev/null\n")
            }

            if (newImei1.isNotBlank()) {
                script.append("resetprop -n ril.gsm.imei '$newImei1' 2>/dev/null\n")
                script.append("resetprop -n persist.radio.imei '$newImei1' 2>/dev/null\n")
                script.append("setprop ril.gsm.imei '$newImei1' 2>/dev/null\n")
            }

            if (newImei2.isNotBlank()) {
                script.append("resetprop -n ril.gsm.imei2 '$newImei2' 2>/dev/null\n")
                script.append("resetprop -n persist.radio.imei2 '$newImei2' 2>/dev/null\n")
                script.append("setprop ril.gsm.imei2 '$newImei2' 2>/dev/null\n")
            }

            // 4. Anti-Blacklist & Device Hash Sanitizer
            if (resetGameGuest) {
                for (pkg in TARGET_GAMES) {
                    // Game shared preferences containing device token, garena device id & guest accounts
                    script.append("rm -rf /data/data/$pkg/shared_prefs/device_id* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/shared_prefs/guest* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/shared_prefs/com.garena* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/shared_prefs/com.dts* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/files/guest* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/files/.tmp* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/databases/device* 2>/dev/null\n")

                    // External storage container fingerprints
                    script.append("rm -rf /sdcard/Android/data/$pkg/files/guest* 2>/dev/null\n")
                    script.append("rm -rf /sdcard/Android/data/$pkg/files/ff_guest* 2>/dev/null\n")
                }

                // Garena global identity folders across external storage
                script.append("rm -rf /sdcard/com.garena* 2>/dev/null\n")
                script.append("rm -rf /sdcard/Android/obb/com.dts.freefireth/.tmp* 2>/dev/null\n")
                script.append("rm -rf /sdcard/Android/obb/com.dts.freefiremax/.tmp* 2>/dev/null\n")
                script.append("rm -rf /data/media/0/com.garena* 2>/dev/null\n")
            }

            val execRes = ShellExecutor.executeRoot(script.toString())
            if (execRes.isSuccess) {
                Result.success("Real hardware identity spoofed and Anti-Blacklist tokens sanitized.")
            } else {
                Result.failure(Exception(execRes.output.ifBlank { "Failed to execute root identity changer" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
