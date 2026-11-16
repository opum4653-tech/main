package com.andorid.ninmods.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom

data class DeviceIdentityState(
    val imei1: String = "",
    val imei2: String = "",
    val androidId: String = "",
    val serialNumber: String = "",
    val macAddress: String = "",
    val bluetoothAddress: String = "",
    val buildId: String = "",
    val brand: String = "",
    val model: String = "",
    val manufacturer: String = "",
    val device: String = "",
    val fingerprint: String = ""
)

object DeviceIdentityManager {

    private val random = SecureRandom()

    private val TARGET_GAMES = listOf(
        "com.dts.freefireth",
        "com.dts.freefiremax"
    )

    /**
     * Reads the current real hardware identifiers of THIS INSTALLED DEVICE.
     * Preserves device brand and model exactly as is.
     */
    suspend fun getDeviceInfo(context: Context): DeviceIdentityState = withContext(Dispatchers.IO) {
        val rootAndroidId = ShellExecutor.executeRootWithOutput("settings get secure android_id 2>/dev/null").output.trim()
        val sysAndroidId = try {
            android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID)
        } catch (_: Exception) { null } ?: ""
        val androidId = when {
            rootAndroidId.isNotBlank() && rootAndroidId != "null" && rootAndroidId != "0000000000000000" -> rootAndroidId
            sysAndroidId.isNotBlank() && sysAndroidId != "null" && sysAndroidId != "0000000000000000" -> sysAndroidId
            else -> generateAndroidId()
        }

        var serial = ShellExecutor.executeRootWithOutput("getprop ro.serialno 2>/dev/null").output.trim()
        if (serial.isBlank() || serial.equals("unknown", ignoreCase = true)) {
            serial = ShellExecutor.executeRootWithOutput("getprop ro.boot.serialno 2>/dev/null").output.trim()
        }
        if (serial.isBlank() || serial.equals("unknown", ignoreCase = true)) {
            serial = ShellExecutor.executeRootWithOutput("cat /sys/class/android_usb/android0/iSerial 2>/dev/null").output.trim()
        }
        if (serial.isBlank() || serial.equals("unknown", ignoreCase = true)) {
            serial = ShellExecutor.executeRootWithOutput("resetprop ro.serialno 2>/dev/null").output.trim()
        }
        if (serial.isBlank() || serial.equals("unknown", ignoreCase = true)) {
            val buildSerial = try { Build.SERIAL } catch (_: Exception) { null } ?: ""
            serial = if (buildSerial.isNotBlank() && !buildSerial.equals("unknown", ignoreCase = true)) buildSerial else generateSerialNumber()
        }

        val imei1 = ShellExecutor.executeRootWithOutput("service call iphonesubinfo 1 | toybox cut -d \"'\" -f2 | toybox grep -Eo '[0-9]{15}' | head -n 1 2>/dev/null")
            .output.trim().ifBlank {
                ShellExecutor.executeRootWithOutput("getprop ril.gsm.imei 2>/dev/null").output.trim().ifBlank {
                    generateRealisticImei()
                }
            }

        val imei2 = ShellExecutor.executeRootWithOutput("service call iphonesubinfo 1 i32 2 | toybox cut -d \"'\" -f2 | toybox grep -Eo '[0-9]{15}' | head -n 1 2>/dev/null")
            .output.trim().ifBlank {
                ShellExecutor.executeRootWithOutput("getprop ril.gsm.imei2 2>/dev/null").output.trim().ifBlank {
                    generateRealisticImei()
                }
            }

        val mac = ShellExecutor.executeRootWithOutput("cat /sys/class/net/wlan0/address 2>/dev/null")
            .output.trim().ifBlank { "02:00:00:00:00:00" }

        val btMac = ShellExecutor.executeRootWithOutput("settings get secure bluetooth_address 2>/dev/null")
            .output.trim().ifBlank { generateMacAddress() }

        val brand = ShellExecutor.executeRootWithOutput("getprop ro.product.brand 2>/dev/null")
            .output.trim().ifBlank { Build.BRAND ?: "Android" }

        val model = ShellExecutor.executeRootWithOutput("getprop ro.product.model 2>/dev/null")
            .output.trim().ifBlank { Build.MODEL ?: "Device" }

        val manufacturer = ShellExecutor.executeRootWithOutput("getprop ro.product.manufacturer 2>/dev/null")
            .output.trim().ifBlank { Build.MANUFACTURER ?: "Android" }

        val device = ShellExecutor.executeRootWithOutput("getprop ro.product.device 2>/dev/null")
            .output.trim().ifBlank { Build.DEVICE ?: "generic" }

        val buildId = ShellExecutor.executeRootWithOutput("getprop ro.build.id 2>/dev/null")
            .output.trim().ifBlank { Build.ID ?: "QP1A.190711.020" }

        val fingerprint = ShellExecutor.executeRootWithOutput("getprop ro.build.fingerprint 2>/dev/null")
            .output.trim().ifBlank { Build.FINGERPRINT ?: "" }

        DeviceIdentityState(
            imei1 = imei1,
            imei2 = imei2,
            androidId = androidId,
            serialNumber = serial,
            macAddress = mac,
            bluetoothAddress = btMac,
            buildId = buildId,
            brand = brand,
            model = model,
            manufacturer = manufacturer,
            device = device,
            fingerprint = fingerprint
        )
    }

    /**
     * Generates a 15-digit valid IMEI adhering to Luhn algorithm.
     */
    fun generateRealisticImei(): String {
        // Standard TAC prefix range
        val tacPrefixes = listOf("86794504", "35412809", "86241005", "35892110", "86034105", "35678211", "86923405", "35209911")
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
     * Generates a randomized Wi-Fi or Bluetooth MAC address.
     */
    fun generateMacAddress(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        bytes[0] = ((bytes[0].toInt() and 0xFC) or 0x02).toByte()
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
        val rem = sum % 10
        return if (rem == 0) 0 else 10 - rem
    }

    /**
     * Real-world Low-Level Root Device Identity & Anti-Blacklist Engine:
     * - Retains device Brand & Model intact (no foreign brand spoofing).
     * - Spoofs dynamic identification parameters: Android ID (SSAID), IMEI 1/2, Serial, MAC.
     * - Directly patches /data/system/users/0/settings_ssaid.xml and settings provider.
     * - Sanitizes game guest tokens & device fingerprint caches.
     */
    suspend fun applyNewIdentity(
        newAndroidId: String,
        newSerial: String,
        newImei1: String,
        newImei2: String,
        newMac: String,
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

            // MAC Address Spoofing
            if (newMac.isNotBlank()) {
                script.append("settings put secure bluetooth_address '$newMac' 2>/dev/null\n")
                script.append("ifconfig wlan0 hw ether '$newMac' 2>/dev/null\n")
                script.append("ip link set wlan0 address '$newMac' 2>/dev/null\n")
            }

            // 4. Anti-Blacklist & Device Hash Sanitizer
            if (resetGameGuest) {
                for (pkg in TARGET_GAMES) {
                    script.append("rm -rf /data/data/$pkg/shared_prefs/device_id* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/shared_prefs/guest* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/shared_prefs/com.garena* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/shared_prefs/com.dts* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/files/guest* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/files/.tmp* 2>/dev/null\n")
                    script.append("rm -rf /data/data/$pkg/databases/device* 2>/dev/null\n")

                    script.append("rm -rf /sdcard/Android/data/$pkg/files/guest* 2>/dev/null\n")
                    script.append("rm -rf /sdcard/Android/data/$pkg/files/ff_guest* 2>/dev/null\n")
                }

                script.append("rm -rf /sdcard/com.garena* 2>/dev/null\n")
                script.append("rm -rf /sdcard/Android/obb/com.dts.freefireth/.tmp* 2>/dev/null\n")
                script.append("rm -rf /sdcard/Android/obb/com.dts.freefiremax/.tmp* 2>/dev/null\n")
                script.append("rm -rf /data/media/0/com.garena* 2>/dev/null\n")
            }

            val execRes = ShellExecutor.executeRootWithOutput(script.toString())
            if (execRes.isSuccess) {
                Result.success("Device identifiers refreshed & Anti-Blacklist tokens sanitized.")
            } else {
                Result.failure(Exception(execRes.output.ifBlank { "Failed to execute root identity changer" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
