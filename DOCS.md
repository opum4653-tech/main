# NINMODS — Enterprise Software Architecture & Technical Documentation

This document defines the architecture, workflow, and operational contracts of the NINMODS system.

---

## 1. Mod Architecture & Scope

- **Supported Mod Engines:**
  1. **NINMODS Kernel Mod** (`ninmods-kernel`): Native kernel-level overlay engine interacting with kernel drivers.
  2. **NINMODS External Mod** (`gemini-cli`): Standalone native memory overlay engine.
- **Execution Architecture:**
  - Both mod engines compile directly into pure native ARM64 ELF binaries.
  - Zero intermediate shell wrappers (`.sh` files eliminated).
  - Executed directly via superuser bridge (`su -c`) with standard process lifecycle management.
- **Dashboard Representation:**
  - The Dashboard features:
    1. **Digital License Expiry Countdown Clock** (`LicenseClockCard`).
    2. **Kernel Mod Card** (`KernelModsCard`) styled in Cyber Purple (`#AD47FA`).
    3. **External Mod Card** (`ExternalModsCard`) styled in Cyan Blue (`#00F2FE`).

---

## 2. Mod Engines & Packaging Specifications

### 2.1 Kernel Mod (`refer/Kernel/Kernel`)
- **Source Location:** `refer/Kernel/Kernel`
- **Repository:** `https://github.com/mohammadnillislam11-ai/kernel-mods.git`
- **Build Pipeline:** Automated via `.github/workflows/build.yml` with Android NDK `r25c`.
- **Output Artifact Format:** Pure native ARM64 ELF executable `ninmods-kernel` packaged into `ninmods-kernel.zip`.
- **Server Deployment Directory:** `downloads/kernel/ninmods-kernel.zip`
- **Mandatory Download URL:** `https://ninmods.smmtor.com/downloads/kernel/ninmods-kernel.zip`
- **Internal Storage Path:** `context.filesDir/kernel_mods/ninmods-kernel`

### 2.2 External Mod (`refer/nin`)
- **Source Location:** `refer/nin`
- **Repository:** `https://github.com/mohammadnillislam11-ai/nin_sh.git`
- **Build Pipeline:** Automated via `.github/workflows/build.yml` with Android NDK `r25c` and `llvm-strip`.
- **Output Artifact Format:** Pure native ARM64 ELF executable `gemini-cli` packaged into `gemini-cli.zip`.
- **Server Deployment Directory:** `downloads/external/gemini-cli.zip`
- **Mandatory Download URL:** `https://ninmods.smmtor.com/downloads/external/gemini-cli.zip`
- **Internal Storage Path:** `context.filesDir/external_mods/gemini-cli`

### 2.3 Kernel Drivers
- **Server Deployment Directory:** `downloads/Kernel_Drivers.zip`
- **Mandatory Download URL:** `https://ninmods.smmtor.com/downloads/Kernel_Drivers.zip`
- **Internal Storage Path:** `context.filesDir/drivers/`

---

## 3. Strict Download Invariants (Zero GitHub, Zero Bundled Assets, Strict Subfolders)

1. **NO BUNDLED ASSETS IN APK:**
   - Mod archives are NOT bundled inside the APK assets (`assets/` contains zero mod ZIPs).
   - All mod packages must be downloaded on demand by the user from the backend server.
2. **ZERO GITHUB DOWNLOAD LINKS:**
   - The app must NEVER use GitHub releases, raw GitHub URLs, or any external links for downloading mods or drivers.
   - All client downloads must route strictly through `https://ninmods.smmtor.com/downloads/...`.
3. **STRICT SUBFOLDER PATHS (NO FLAT ROOT URLS):**
   - External mod: `downloads/external/gemini-cli.zip`
   - Kernel mod: `downloads/kernel/ninmods-kernel.zip`
   - Flat root URLs (e.g. `/downloads/gemini-cli.zip` or `/downloads/ninmods-kernel.zip`) are strictly FORBIDDEN.
4. **DIRECT UPDATE ACTION:**
   - Both `ExternalModsCard` and `KernelModsCard` feature a direct CloudDownload icon button next to the status badge, allowing users to re-download the latest mod package from the server even when previously installed.

---

## 4. Dashboard Mod Interaction & Dialog Workflow

### Step 1: Download & Extraction (When NOT installed)
1. User taps the mod card or the CloudDownload icon button on Dashboard.
2. The app prompts user with the Material 3 Download Confirmation Dialog.
3. Upon confirmation, the app streams the `.zip` package strictly from the backend server (`https://ninmods.smmtor.com/downloads/external/gemini-cli.zip` or `https://ninmods.smmtor.com/downloads/kernel/ninmods-kernel.zip`).
4. Extracts the pure native ELF binary directly into place, sets executable permissions (`chmod 755`), and cleans up the archive.
5. Card status transitions to `READY`.

### Step 2: Single-Tap Launch & Background Prompt
1. User taps the mod card when binary is present.
2. The app initiates execution via root shell (`InteractiveProcessBridge`).
3. The native binary starts in memory and outputs the stdin authentication prompt:
   ```
   [?] Enter API Access Key:
   ```
4. `InteractiveProcessBridge` detects this stdout sequence and triggers `onLicensePrompt()`.

### Step 3: Two-Stage Verification & RAM Loading Gate (Verify -> Start)
1. The app displays the Material 3 License Key Dialog pre-filled with the active key.
2. When user taps "Verify":
   - Key is validated with backend (`/api/v2/app/activate`) and passed to binary stdin.
   - Binary verifies online via embedded `libcurl` + OpenSSL:
     ```
     [+] API Key validated successfully. Core engine authorized.
     [?] Ready to load into memory. Awaiting START signal...
     ```
   - "Verify" button transforms dynamically into green "Start" button (`Icons.Filled.PlayArrow`).
3. When user taps "Start":
   - App sends `"START\n"` to binary stdin.
   - Engine initializes native overlay (`ANativeWindowCreator` + ImGui) and renders floating menu.
   - Card status updates to `ACTIVE`.
4. If user taps an `ACTIVE` mod card, a confirmation dialog offers to "Stop Mod" and terminate cleanly.

---

## 5. Live License Clock Policy

1. **Card Visibility:**
   - Shown exclusively when an active subscription exists with remaining time > 0.
   - Automatically hidden if no active license exists or time has expired.
2. **Real-Time Digital Countdown:**
   - Ticks live every 1 second in `HH : MM : SS` digital format without page refresh.
   - Backed by monotonic Android system uptime synchronized with backend heartbeat (`/api/v2/app/heartbeat`).

---

## 6. Directory & File Matrix

```text
DriverLoader/
  app/src/main/assets/
    (zero bundled mod archives - on-demand download enforced)
  app/src/main/java/com/andorid/ninmods/
    data/
      InteractiveProcessBridge.kt      # Root shell stdin/stdout process manager
      ModDownloader.kt                 # HTTP streaming downloader & unzipper
      SessionManager.kt                # License duration and auth state
    ui/
      dashboard/
        DashboardScreen.kt             # Unified dashboard host
        external_mods/
          ExternalModsCard.kt          # External mod launcher card (Cyan Blue)
        kernel_mods/
          KernelModsCard.kt            # Kernel mod launcher card (Cyber Purple)
      clock/
        LicenseClockCard.kt            # Live countdown timer card
      dialogs/
        ModActionDialogs.kt            # M3 confirmation, download, & license dialogs
```

---

## 7. Official GitHub Release & Home Page Policy

1. **GitHub Home Page "Latest" Badge Invariant:**
   - Every production release (`v1.x.x`) MUST be explicitly pinned as the repository's "Latest Release" on the GitHub homepage sidebar (`/releases/latest`).
   - Old versions must never remain as the latest release.
2. **Automated Pipeline Enforcement:**
   - The release workflow triggers on semantic version tags and enforces `makeLatest: true`.
3. **Artifact Consistency Guarantee:**
   - Official release APK is mirrored simultaneously across GitHub Releases and server backend (`https://ninmods.smmtor.com/downloads/app-release.apk`).

---

## 8. Clean Comment and Documentation Formatting Rules

1. **Banned Formatting Patterns:**
   - Decorative box-drawing banners and framing lines (double lines, single lines, box corners, vertical dividers) are strictly prohibited in all code comments, documentation headers, and terminal outputs.
   - Non-English language text (Bengali, Hindi, or conversational text) is strictly forbidden in production source code, comments, logs, and technical documentation.
2. **Standard Comment Style:**
   - Single-line comments must use minimal standard tokens: `// Section Name` or `/* Section Name */`.
   - Documentation headers must use standard Markdown headings (`#`, `##`, `###`) without decorative frames.
