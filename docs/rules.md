# NINMODS Official Development & Design Rules

This document establishes the mandatory design, architecture, and coding invariants for all developers and AI agents working on the `ninmods` project. Violations of these rules cause visual regressions, compilation breaks, or user experience degradation.

---

## 1. UI & Visual Theme Hierarchy (Strict APatch Material 3)

1. **Official Theme System:**
   - All screens, dialogs, cards, and bottom sheets must strictly consume the official APatch Material 3 theme palette (`MaterialTheme.colorScheme`).
   - Arbitrary hardcoded colors (e.g. electric pink, neon green, custom gradients, or mismatched themes) are strictly forbidden.
   - Use theme semantic slots:
     - Container backgrounds: `MaterialTheme.colorScheme.surfaceVariant`
     - Nested item cards: `MaterialTheme.colorScheme.surface`
     - Primary actions: `MaterialTheme.colorScheme.primary` / `MaterialTheme.colorScheme.onPrimary`
     - Secondary text / hints: `MaterialTheme.colorScheme.onSurfaceVariant`
     - High-contrast text / titles: `MaterialTheme.colorScheme.onSurface`

2. **Card Geometries & Elevation Standards:**
   - Outer container cards: `RoundedCornerShape(20.dp)`
   - Inner interactive cards / item rows: `RoundedCornerShape(12.dp)` or `RoundedCornerShape(16.dp)`
   - Action buttons / badges: `RoundedCornerShape(12.dp)` or `RoundedCornerShape(16.dp)`
   - Elevation: Strictly flat design with `CardDefaults.cardElevation(defaultElevation = 0.dp)`. Never use drop shadows or custom elevation layers.

3. **Typography & Font Rules:**
   - Use official Material 3 typography tokens (`MaterialTheme.typography`):
     - Section headers: `MaterialTheme.typography.titleMedium` with `FontWeight.Bold`
     - Subtitles / metadata: `MaterialTheme.typography.bodySmall`
     - Badges / chips: `MaterialTheme.typography.labelSmall` or `labelLarge`
     - Body descriptions: `MaterialTheme.typography.bodyMedium`
   - Never import external font files or hardcode custom typeface families in individual composables.

4. **Animations & Transitions:**
   - Use official Compose M3 animations (`AnimatedVisibility`, `animateContentSize`, `Crossfade`).
   - Keep animation durations snappy: 150ms to 250ms with `FastOutSlowInEasing`.
   - Never introduce heavy particle effects, unoptimized frame loops, or blocking UI thread animations.

---

## 2. Navigation & Scaffold Architecture

1. **Single Global TopAppBar & Back Navigation:**
   - `MainAppScreen.kt` maintains the top-level global `Scaffold` and `TopAppBar`.
   - When navigating to any sub-screen (e.g., Profile, Anti-Blacklist, Settings, My Licenses):
     - `MainAppScreen` automatically displays the screen title and the back arrow navigation icon (`Icons.AutoMirrored.Filled.ArrowBack`).
     - Sub-screens must **NEVER** define their own internal `Scaffold` or nested `TopAppBar`.
     - Nested headers or duplicate back buttons are considered critical UI bugs.

2. **Dashboard Minimalism Invariant:**
   - The Dashboard screen must remain strictly minimal, focused, and uncluttered.
   - The Dashboard strictly displays only:
     1. `BroadcastNotificationCard` (visible only when active announcements exist)
     2. `LicenseClockCard` (Digital real-time countdown)
     3. `KernelModsCard` (Cyber Purple Ring 0 floating engine)
     4. `ExternalModsCard` (Cyan Blue official external mod)
   - Auxiliary cards (such as `Daily List` / `DenyListCard` and `Anti-Blacklist`) must **NEVER** be placed on the Dashboard.
   - Access to auxiliary features is strictly delegated to the **Side Navigation Drawer**.

---

## 3. Anti-Blacklist & Device Identity Invariants

1. **Authentic Device Brand & Model Preservation:**
   - The Anti-Blacklist engine must read and display the installed phone's genuine Brand and Model (`Build.BRAND`, `Build.MODEL`, `Build.MANUFACTURER`, `Build.DEVICE`).
   - Foreign brand presets (e.g., Google Pixel 8 Pro, Samsung S24 Ultra, OnePlus 12) are **STRICTLY FORBIDDEN**.
   - Spoofing the device model to a different phone causes manufacturer driver mismatches, GPU profile corruptions, and obvious detection flags.
   - The user's genuine phone model remains locked and unaltered.

2. **Dynamic Hardware Identifiers Randomization:**
   - The Anti-Blacklist spoofer randomizes ONLY dynamic tracking tokens:
     - IMEI (Slot 1) & IMEI (Slot 2) with valid Luhn checksum calculation
     - Android ID (SSAID) via direct XML injection into `/data/system/users/0/settings_ssaid.xml`
     - Hardware Serial Number (`ro.serialno` / `ro.boot.serialno`) via `resetprop -n`
     - WiFi MAC Address with locally-administered bit formatting
     - Garena Free Fire Guest & device tracking token cache sanitization

---

## 4. Root Operations & File System Safeguards

1. **UID Permission & Ownership Alignment:**
   - The Android application runs with an unprivileged Linux UID (`context.applicationInfo.uid`).
   - Any file or directory created or manipulated by root (`su`) inside `/data/user/0/com.andorid.ninmods/` must be immediately restored to the app's UID:
     `chown -R $appUid:$appUid '$path' && chmod -R 777 '$path'`
   - Failure to align ownership causes fatal `EACCES (Permission denied)` errors during subsequent app file writes.

2. **Buffer Streams in Safe App Cache:**
   - Network downloads must always stream into `context.cacheDir` first before moving or unpacking to internal directories.
   - Never write download chunks directly into root-managed directories.

---

## 5. Clean Code & Documentation Formatting

1. **Language Discipline:**
   - All source code, method names, variable names, log outputs, technical comments, and Markdown documentation must be written in clear, professional **English**.
   - Bengali, Hindi, slang, or conversational text is strictly prohibited in production repositories.

2. **No Decorative ASCII Boxes:**
   - Decorative box-drawing characters (`┌─┐`, `╔═╗`, `│`, `║`, `***`, double-line framing) are strictly banned.
   - Use standard single-line comments: `// Section Name` or `/* Section Name */`.
