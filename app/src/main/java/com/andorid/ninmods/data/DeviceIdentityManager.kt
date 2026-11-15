package com.andorid.ninmods.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import kotlin.random.Random

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
        // TAC prefix for popular modern devices (Samsung / Xiaomi / OnePlus)
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
     * Applies new identity parameters across Android system settings and props live.
     * Also resets Free Fire game guest identifiers if requested.
     */
    suspend fun applyNewIdentity(
        newAndroidId: String,
        newSerial: String,
        newImei1: String,
        newImei2: String,
        resetGameGuest: Boolean
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cmdBuilder = StringBuilder()

            // 1. Update Global/Secure Android ID (SSAID)
            if (newAndroidId.isNotBlank()) {
                cmdBuilder.append("settings put secure android_id '$newAndroidId'\n")
                // Target per-app SSAID tables on modern Android
                cmdBuilder.append("content insert --uri content://settings/secure --bind name:s:android_id --bind value:s:'$newAndroidId' 2>/dev/null\n")
            }

            // 2. Setprop device properties live
            if (newSerial.isNotBlank()) {
                cmdBuilder.append("setprop ro.serialno '$newSerial' 2>/dev/null\n")
                cmdBuilder.append("setprop ro.boot.serialno '$newSerial' 2>/dev/null\n")
                cmdBuilder.append("resetprop -n ro.serialno '$newSerial' 2>/dev/null\n")
                cmdBuilder.append("resetprop -n ro.boot.serialno '$newSerial' 2>/dev/null\n")
            }

            if (newImei1.isNotBlank()) {
                cmdBuilder.append("setprop ril.gsm.imei '$newImei1' 2>/dev/null\n")
                cmdBuilder.append("resetprop -n ril.gsm.imei '$newImei1' 2>/dev/null\n")
                cmdBuilder.append("setprop persist.radio.imei '$newImei1' 2>/dev/null\n")
            }

            if (newImei2.isNotBlank()) {
                cmdBuilder.append("setprop ril.gsm.imei2 '$newImei2' 2>/dev/null\n")
                cmdBuilder.append("resetprop -n ril.gsm.imei2 '$newImei2' 2>/dev/null\n")
                cmdBuilder.append("setprop persist.radio.imei2 '$newImei2' 2>/dev/null\n")
            }

            // 3. Clear game blacklisted device cache / guest ID
            if (resetGameGuest) {
                // Free Fire Global & MAX guest tokens & hardware device cache
                cmdBuilder.append("rm -rf /sdcard/Android/data/com.dts.freefireth/files/guest* 2>/dev/null\n")
                cmdBuilder.append("rm -rf /sdcard/Android/data/com.dts.freefiremax/files/guest* 2>/dev/null\n")
                cmdBuilder.append("rm -rf /data/data/com.dts.freefireth/shared_prefs/device_id* 2>/dev/null\n")
                cmdBuilder.append("rm -rf /data/data/com.dts.freefiremax/shared_prefs/device_id* 2>/dev/null\n")
                cmdBuilder.append("rm -rf /data/data/com.dts.freefireth/files/guest* 2>/dev/null\n")
                cmdBuilder.append("rm -rf /data/data/com.dts.freefiremax/files/guest* 2>/dev/null\n")
                cmdBuilder.append("rm -rf /data/media/0/com.garena* 2>/dev/null\n")
                cmdBuilder.append("rm -rf /sdcard/com.garena* 2>/dev/null\n")
            }

            val res = ShellExecutor.executeRoot(cmdBuilder.toString())
            if (res.isSuccess) {
                Result.success("Hardware identity and Anti-Blacklist rules applied successfully!")
            } else {
                Result.failure(Exception(res.output.ifBlank { "Command execution failed" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
