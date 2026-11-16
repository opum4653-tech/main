# NINMODS Project Memory & Evolution Log

This file serves as the permanent persistent memory and historical engineering log for the `ninmods` Android client and backend integrations. Every significant architecture decision, root cause analysis, bug fix, and feature addition is documented here to maintain full continuity across development sessions and agent invocations.

---

## 1. Project Identity & Architecture Overview

- **Repository:** `opum4653-tech/main` (Branch: `main`)
- **Package Name:** `com.andorid.ninmods`
- **Application Role:** Superuser Root Game Overlay Launcher & Driver Bridge
- **Current Version:** `v1.5.6` (Build Code: 156)
- **Primary Backend:** `https://ninmods.smmtor.com`
- **Base UI Theme:** Material 3 Dark Palette modeled after official APatch Android client.

---

## 2. Key Architectural Invariants & Decisions

### Decision 1: Pure APatch Material 3 UI Styling (XML View System)
- **Context:** Previously, miscellaneous screens introduced disparate styling (bright pink buttons, custom font weights, custom elevation shadows, disparate card radii).
- **Resolution:** Enforced uniform APatch theme across all screens, but expressed entirely through XML resources and the Android View system — Compose is gone from the project.
  - Background surface: `@color/nin_background`
  - Child cards: `@style/Widget.Nin.Card.Inner` on `MaterialCardView`, flat elevation
  - Action buttons: `@style/Widget.Nin.Button.Primary` / `.Outline`, pill corner shape
  - Typography: `Text.Nin.*` style family mapped to Material 3 text appearances
  - Sub-screen topbars: managed solely by `activity_main.xml`'s toolbar + `MainActivity.openSubScreen()`

### Decision 2: Minimalist Dashboard & Auxiliary Drawer Allocation
- **Context:** Overcrowded dashboard cluttered with DenyList and Anti-Blacklist cards.
- **Resolution:** Purged all auxiliary cards from the dashboard.
  - The Dashboard is strictly reserved for:
    1. Active Broadcast Announcement (if present)
    2. Digital License Countdown Clock
    3. Kernel Mod Card (Cyber Purple)
    4. External Mod Card (Cyan Blue)
  - `Daily List` (Root Cloak / DenyList) and `Anti-Blacklist & IMEI` are strictly navigated via Drawer items 6 and 7.
  - Compiled in XML as `fragment_dashboard.xml` hosted by `DashboardFragment.kt`.

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
- **Symptoms:** Navigating to the identity screen displayed two stacked topbars with two back arrow buttons.
- **Root Cause:** The identity screen rendered its own top bar while `MainActivity` was simultaneously rendering the global topbar because the destination was a sub-screen.
- **Fix:** Sub-screens no longer render a top bar. `MainActivity.openSubScreen()` owns title, back arrow and the action menu for every pushed fragment. `fragment_identity.xml` deliberately carries no toolbar.

---

## 4. Component Matrix & State Machines

| Component | File Path | Primary Role |
| :--- | :--- | :--- |
| `MainActivity` | `ui/MainActivity.kt` | Drawer shell, `Destination` enum, `FragmentContainerView` host, back handling, session verify |
| `LoginActivity` | `ui/login/LoginActivity.kt` | Auth host (LAUNCHER), swaps Login/Signup/ForgotPassword fragments |
| `DashboardFragment` | `ui/dashboard/DashboardFragment.kt` | Minimalist dashboard: broadcast, clock, kernel mod, external mod |
| `LoaderFragment` | `ui/loader/LoaderFragment.kt` | Kernel release readout, driver/root state, QX + RT modes, terminal |
| `DenyListFragment` | `ui/denylist/DenyListFragment.kt` | Root cloak: environment readout, global switch, per-process isolation |
| `IdentityFragment` | `ui/identity/IdentityFragment.kt` | Anti-Blacklist UI, real brand/model lock, dynamic hardware spoofer |
| `ModEngine` / `ModRuntime` | `ui/mod/ModEngine.kt`, `ui/mod/ModRuntime.kt` | Per-variant scan/self-heal/download/launch; one bridge per variant |
| `ModFlowDialog` | `ui/dialogs/ModFlowDialog.kt` | Six-stage mod launch state machine |
| `DeviceIdentityManager` | `data/DeviceIdentityManager.kt` | SSAID XML injection, Luhn IMEI generator, resetprop masking, token cleaner |
| `ModDownloader` | `data/ModDownloader.kt` | Resilient package downloader, cache buffering, self-healing root unpacker |
| `InteractiveProcessBridge` | `data/InteractiveProcessBridge.kt` | Su process lifecycle, terminal stdout pipe, stdin auth injection |
| `LicenseClock` | `ui/clock/LicenseClock.kt` | Monotonic per-second expiry countdown flow |

---

## 5. UI Layout Breaking Bugs & Path Toast Fixes

### Bug 1: Filter Chips Vertical Letter Wrapping (wallet & support screens)
- **Symptoms:** Filter chips (e.g. `REJECTED`, `ANSWERED`) collapsed horizontally into 1-character columns on standard DPI devices.
- **Root Cause:** The chip rows were laid out in a wrapping container without a scroll axis, and chip labels had no line limit.
- **Fix:** In the XML rebuild the filter chips were folded into the search/pull-to-refresh model (`fragment_support.xml` keeps a single `input_search`, `fragment_add_funds.xml` shows the full ledger). Every remaining label that can wrap carries `android:maxLines="1"` and is inside a `NestedScrollView`, so no chip can be squeezed into a character column. Date formatting lives in the row adapter (`TransactionAdapter.formatDate`) and converts raw UTC timestamps into `dd MMM, hh:mm a`.

### Bug 2: "Randomize All" Button Text Clipping (identity screen)
- **Symptoms:** The "Randomize All" button clipped the bottom word ("All" -> `^ll`).
- **Root Cause:** A fixed `44dp` height combined with the Material default horizontal padding forced the label onto two lines, which the fixed height clipped.
- **Fix:** `fragment_identity.xml` uses `android:minHeight` instead of a fixed height, `android:maxLines="1"`, `android:singleLine="true"`, `android:ellipsize="end"`, and a `Text.Nin.*` label style. No fixed height anywhere on that button.

### Bug 3: Raw Internal File Path Toast on Mod Download Error
- **Symptoms:** Downloading a mod on a device with restrictive root permissions displayed a raw system path toast (`/data/user/0/com.andorid.ninmods/files/kern...`).
- **Root Cause:** `ModDownloader.kt` fallback error formatter returned `e.localizedMessage` unformatted when `FileOutputStream` threw `FileNotFoundException` or `ENOENT`.
- **Fix:** Sanitized all error strings to map raw internal paths into clean, actionable user feedback ("Storage access error. Please grant Superuser access to NinMods."). Validated extracted zip outputs to never return a directory as an executable.
