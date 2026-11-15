# NINMODS Deep Architectural Specification

This document details the internal systems, root mechanics, networking protocols, and security layers of the `ninmods` client.

---

## 1. Interactive Root Process Bridge (`InteractiveProcessBridge.kt`)

### Lifecycle & Mechanics
The application executes native C++ binaries (`ninmods-kernel` and `gemini-cli`) using superuser privileges (`su`) without spawning external terminal windows or visible shells:

1. **Clean Process Initialization:**
   - Detects the installed superuser binary (`/system/bin/su`, `/data/adb/ap/bin/su`, `/data/adb/magisk/su`).
   - Before executing the binary, issues an aggressive teardown script to terminate stale background daemons:
     `killall -9 '$binName' 2>/dev/null; killall -9 ninmods-kernel 2>/dev/null; pkill -9 -f gemini_proc 2>/dev/null; rm -f /dev/.gemini_proc_*`
2. **Environment Variable Injection:**
   - Exports environment variables directly into the child shell:
     - `NIN_DEVICE_HWID`: Device hardware identifier.
     - `NIN_LICENSE_KEY`: User's active VIP license key.
     - `NIN_SERVER_URL`: Backend endpoint (`https://ninmods.smmtor.com`).
     - `LD_LIBRARY_PATH`: Shared APEX runtime and vendor library paths.
3. **Execution Mode:**
   - Replaces the shell process with the binary using `exec '$safePath'` to guarantee 1:1 signal and exit code propagation.
4. **Interactive Terminal Pipe & License Detection:**
   - Listens asynchronously to stdout/stderr streams via coroutine buffer.
   - Detects authentication prompts:
     `[?] Enter License / Access Key:`
   - Feeds the license key directly via stdin pipe:
     `out.writeBytes("$key\n"); out.flush()`
   - Once validated, waits for the `Awaiting START signal...` prompt and injects `START\n` to instruct the binary to render the floating overlay.

---

## 2. Mod Package Download & Extraction Engine (`ModDownloader.kt`)

### Dual-Stage Safe Buffering Pipeline
1. **Network Stream Stage:**
   - Streams the HTTP/HTTPS payload into `context.cacheDir` (`tmp_dl_...`).
   - Because `cacheDir` is managed strictly by Android for the app UID, write operations are guaranteed never to hit `EACCES` or root lockouts.
2. **Permission Self-Healing Stage:**
   - Invokes superuser command before touching destination directories:
     `mkdir -p '$targetDir' && chown -R $appUid:$appUid '$targetDir' && chmod -R 777 '$targetDir'`
3. **Dual-Engine Extraction Stage:**
   - Primary: Fast in-memory Java `ZipInputStream` with Zip-Slip path sanitization.
   - Fallback: If any file cannot be deleted/overwritten due to immutable Linux flags or root locks, invokes root toybox/unzip:
     `toybox unzip -o '$zipFile' -d '$targetDir' && chown -R $appUid:$appUid '$targetDir'`
4. **Executable Rights Enforcement:**
   - Applies `chmod 755` across extracted binaries and scripts to allow immediate execution.

---

## 3. Anti-Blacklist & Hardware Identity Spoofer (`DeviceIdentityManager.kt`)

### Authentic Hardware Preservation Doctrine
- The client reads real hardware identifiers at startup using `Build` fields:
  - `brand`: `Build.BRAND`
  - `model`: `Build.MODEL`
  - `manufacturer`: `Build.MANUFACTURER`
  - `device`: `Build.DEVICE`
- These brand/model values are displayed automatically and remain unchanged.

### Dynamic Token Randomization
1. **IMEI (International Mobile Equipment Identity):**
   - Generates 15-digit numeric strings adhering strictly to the ISO/IEC 7812 Luhn checksum algorithm.
2. **Android ID (SSAID):**
   - Generates random 16-character hexadecimal strings.
   - Injects the new SSAID directly into the Android system settings XML:
     `/data/system/users/0/settings_ssaid.xml`
   - Updates `settings put secure android_id` for immediate userland reflection.
3. **Hardware Serial Number:**
   - Updates volatile Android properties:
     `resetprop -n ro.serialno <new_serial>`
     `resetprop -n ro.boot.serialno <new_serial>`
4. **WiFi MAC Address:**
   - Generates 6-byte MAC strings with the locally administered bit (bit 1 of byte 0) set to `1` and multicast bit set to `0`.
5. **Garena Blacklist Token Sanitization:**
   - Erases guest account databases, shared preferences, and hardware tracking tokens:
     - `/data/data/com.dts.freefireth/shared_prefs/`
     - `/data/data/com.dts.freefiremax/shared_prefs/`
     - `/sdcard/Android/data/com.dts.freefireth/files/`
     - `/sdcard/Android/data/com.dts.freefiremax/files/`
