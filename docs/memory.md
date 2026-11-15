# NINMODS Project Memory & Evolution Log

This file serves as the permanent persistent memory and historical engineering log for the `ninmods` Android client and backend integrations. Every significant architecture decision, root cause analysis, bug fix, and feature addition is documented here to maintain full continuity across development sessions and agent invocations.

---

## 1. Project Identity & Architecture Overview

- **Repository:** `opum4653-tech/main` (Branch: `main`)
- **Package Name:** `com.andorid.ninmods`
- **Application Role:** Superuser Root Game Overlay Launcher & Driver Bridge
- **Current Version:** `v1.5.4` (Build Code: 154)
- **Primary Backend:** `https://ninmods.smmtor.com`
- **Base UI Theme:** Material 3 Dark Palette modeled after official APatch Android client.

---

## 2. Key Architectural Invariants & Decisions

### Decision 1: Pure APatch Material 3 UI Styling
- **Context:** Previously, miscellaneous screens introduced disparate styling (bright pink buttons, custom font weights, custom elevation shadows, disparate card radii).
- **Resolution:** Enforced uniform APatch theme across all screens.
  - Background surface: `MaterialTheme.colorScheme.surfaceVariant`
  - Child cards: `MaterialTheme.colorScheme.surface` with `RoundedCornerShape(20.dp)`
  - Action buttons: Pill buttons with `RoundedCornerShape(12.dp)` or `16.dp`
  - Elevations: Flat `0.dp` across all cards.
  - Sub-screen topbars: Managed solely by `MainAppScreen.kt` global scaffold.

### Decision 2: Minimalist Dashboard & Auxiliary Drawer Allocation
- **Context:** Overcrowded dashboard cluttered with DenyList and Anti-Blacklist cards.
- **Resolution:** Purged all auxiliary cards from `DashboardScreen.kt`.
  - The Dashboard is strictly reserved for:
    1. Active Broadcast Announcement (if present)
    2. Digital License Countdown Clock
    3. Kernel Mod Card (Cyber Purple)
    4. External Mod Card (Cyan Blue)
  - `Daily List` (Root Cloak / DenyList) and `Anti-Blacklist & IMEI` are strictly navigated via Drawer items 6 and 7.

### Decision 3: Device Identity Engine — Real Hardware Lockdown
- **Context:** Previous spoofer implementation provided foreign brand presets (Google Pixel, Samsung S24 Ultra, OnePlus 12).
- **Issue:** Changing brand/model to another manufacturer causes hardware sensor mismatches, GPU driver incompatibilities, and unnatural detection fingerprints.
- **Resolution:**
  - Presets completely removed from UI and backend logic.
  - The installed device's real Brand, Model, Manufacturer, and Board are detected at runtime (`Build.BRAND`, `Build.MODEL`, `Build.MANUFACTURER`) and permanently preserved.
  - Only dynamic hardware tracking tokens are randomized:
    - Primary IMEI (Slot 1) & Secondary IMEI (Slot 2) with valid Luhn checksums
    - Android ID (SSAID) injected into `/data/system/users/0/settings_ssaid.xml`
    - Hardware Serial Number (`ro.serialno`) masked via `resetprop -n`
    - WiFi MAC address randomized with locally-administered bit set
    - Garena Free Fire guest account database and cache token purging

---

## 3. Incident Reports & Root Cause Analyses

### Incident 1: Mod Download Failure (`/data/user/0/.../kernel_mods/...: open failed: EACCES`)
- **Symptoms:** User reported mod download failed; clicking download resulted in an Android Toast displaying the truncated file path:
  `/data/user/0/com.andorid.ninmods/files/kern...`
- **Root Cause Analysis:**
  1. During previous root daemon execution or unpack operations, `/data/user/0/com.andorid.ninmods/files/kernel_mods/` or files inside it (e.g., `ninmods-kernel`) were touched or extracted by `su` (UID 0).
  2. Linux file permissions assigned ownership to `root:root` with mode `755` (`rwxr-xr-x`).
  3. When the user attempted to download or update the mod from the dashboard, `ModDownloader.downloadMod` ran inside the Android application process (UID `u0_a...`, non-root).
  4. Java `FileOutputStream(tempFile)` attempted to create/write files in `kernel_mods/`. The Linux kernel rejected the write operation with `EACCES (Permission denied)`.
  5. Java threw `FileNotFoundException: /data/user/0/com.andorid.ninmods/files/kernel_mods/...: open failed: EACCES`.
  6. The exception message was passed directly into `Toast.makeText`, showing the raw file path on screen.
- **Fix Implemented:**
  1. **Safe Cache Buffering:** `ModDownloader` now streams network payloads into `context.cacheDir` first (`tmp_dl_...`). `cacheDir` is always writable by the app UID and immune to root permission conflicts.
  2. **Self-Healing Ownership Alignment:** Before unpacking or moving, `ModDownloader` invokes root to self-heal permissions:
     `mkdir -p '$targetDir' && chown -R $appUid:$appUid '$targetDir' && chmod -R 777 '$targetDir'`
  3. **Resilient Dual-Engine Unpacker:** If Java `ZipInputStream` encounters OS locks when writing destination files, the system falls back to root toybox/unzip:
     `toybox unzip -o '$zipFile' -d '$targetDir' && chown -R $appUid:$appUid '$targetDir'`
  4. **Sanitized User Feedback:** Error messages no longer leak raw filesystem paths to toasts.

### Incident 2: Sub-Screen Duplicate Header & Navigation Collision
- **Symptoms:** Navigating to `DeviceIdentityScreen` displayed two stacked topbars with two back arrow buttons.
- **Root Cause:** `DeviceIdentityScreen.kt` declared an internal `TopAppBar` while `MainAppScreen.kt` was simultaneously rendering the global topbar because `isSubScreen` was true.
- **Fix:** Removed internal `TopAppBar` from `DeviceIdentityScreen.kt`. Global scaffold now cleanly manages title and back navigation.

---

## 4. Component Matrix & State Machines

| Component | File Path | Primary Role |
| :--- | :--- | :--- |
| `MainAppScreen` | `ui/compose/MainAppScreen.kt` | Root scaffold, navigation drawer, global topbar, back press handler |
| `DashboardScreen` | `ui/dashboard/DashboardScreen.kt` | Minimalist dashboard host (Broadcast, Clock, Kernel Mod, External Mod) |
| `KernelModsCard` | `ui/dashboard/kernel_mods/KernelModsCard.kt` | Cyber Purple card, root daemon bridge, floating overlay loader |
| `ExternalModsCard` | `ui/dashboard/external_mods/ExternalModsCard.kt` | Cyan Blue card, user-space in-memory overlay loader |
| `DeviceIdentityScreen` | `ui/identity/DeviceIdentityScreen.kt` | Anti-Blacklist UI, real brand/model lock, dynamic hardware spoofer |
| `DeviceIdentityManager` | `data/DeviceIdentityManager.kt` | SSAID XML injection, Luhn IMEI generator, resetprop masking, token cleaner |
| `ModDownloader` | `data/ModDownloader.kt` | Resilient package downloader, cache buffering, self-healing root unpacker |
| `InteractiveProcessBridge` | `data/InteractiveProcessBridge.kt` | Su process lifecycle, terminal stdout pipe, stdin auth injection |
| `LicenseClockCard` | `ui/clock/LicenseClockCard.kt` | Real-time digital expiry countdown clock |
