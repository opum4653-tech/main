# NINMODS Refactor Log

Chronological record of the professional-standard rebuild: dead-code removal, bug fixes,
the XML resource foundation, and the migration from Compose-only screens to XML layouts
hosted by real Android components (Activity / Fragment / RecyclerView adapters).

Version at start of this pass: `1.5.6` (`versionCode` 156).
Branch `main`, base commit `8639e6d`.

---

## 0. Environment constraint (blocking builds, not code)

- No Android SDK is installed on the build host (`ANDROID_SDK_ROOT=C:\Android\Sdk` does not exist,
  no `local.properties`). `gradlew` fails with `SDK location not found`.
- Every change below is verified **statically only** (grep for references, resource cross-checks,
  manifest/layout id audits). No compile has been run.
- To build: create `local.properties` with `sdk.dir=<path to a real Android SDK>`, then
  `gradlew :app:assembleDebug`.

---

## 1. Dead-code removal

Definition used: a symbol or file is dead when a recursive grep over `app/src/main/**/*.kt`
plus the resource cross-reference scan finds zero call sites outside its own declaration.

### 1.1 Whole files deleted

| File | Reason |
|---|---|
| `ui/component/WarningCard.kt` | Composable never invoked from any screen |
| `ui/dialogs/UserDepositBottomSheet.kt` | Never instantiated; deposit flow uses `SubmitDepositScreen` |

### 1.2 Dead composables deleted (file kept)

| File | Symbol |
|---|---|
| `ui/component/SettingsItem.kt` | `RadioItem` |
| `ui/dialogs/AppBottomSheet.kt` | `AppCustomBottomSheet` (non-selection sheet variant) |
| `ui/component/BroadcastNotificationCard.kt` | `InAppBroadcastModalDialog` |
| `ui/dialogs/ModActionDialogs.kt` | `ApatchConfirmDownloadDialog`, `ApatchWarningDialog`, `ApatchLicenseKeyDialog`, `ApatchRootRequiredDialog` |

Kept in `ModActionDialogs.kt` because they are live: `ApatchAnimatedDialog`,
`ApatchUnifiedModDialog`, `ApatchDownloadDialog`.

### 1.3 Dead network API deleted (`network/AuthApiClient.kt`)

- `sendHeartbeat(...)` — no caller.
- `fetchAppConfig(...)` and its model `AppConfigData` — no caller; the app reads `AppConfig` constants.
- `getProfile(apiKey)` single-argument wrapper — superseded by `getProfileWithEtag`.
- Legacy 6-argument `submitBugReport` overload — superseded by the 10-argument overload.
- `AppConfig.APP_HEARTBEAT_URL` removed with its last consumer.

### 1.4 Dead state / model members deleted

| File | Symbol |
|---|---|
| `data/SessionManager.kt` | `userSpent`, `KEY_USER_PASSWORD` |
| `network/AuthApiClient.kt` | `AuthorizeApiClient.DownloadState` |
| `data/DeviceIdentityManager.kt` | `DeviceIdentityState.isGameBlacklisted` |
| `ui/MainViewModel.kt` | `getDriversForMode`, `getMatchingDrivers` |
| `ui/MainViewModel.kt` | `LoaderUiState.matchingDrivers` |
| `data/DeviceNodeManager.kt` | `isDriverActive` |
| `data/DriverRepository.kt` | `getAllDrivers` |
| `data/InteractiveProcessBridge.kt` | `executionStatus` state + its five assignments |
| `data/UserRepository.kt` | `profileState`, `invalidate()` |

### 1.5 Import hygiene

- Unused imports stripped in 10+ files by automated sweep.
- UTF-8 BOM removed from 18 Kotlin source files (BOM breaks `kotlinc` on some toolchains).

### 1.6 Resources deleted

- 22 unused XML drawables. Kept: `ic_launcher.png`, `logo_n.png`, `ic_user_profile.xml`.
- `values-bn/strings.xml` — mojibake, unreadable, unreferenced.
- `values/arrays.xml` — mojibake, unreferenced.
- `Theme.APatch` and `Theme.APatch.WebUI` from `themes.xml` — both superseded.
- `assets/kernel/driver_loader.sh` — actually an ARM64 ELF binary with a `.sh` extension.
- `assets/kernel/sec_bypass.sh` — unreferenced.

`assets/external/.gitkeep` and `assets/kernel/.gitkeep` retained; both asset dirs are now empty
and are populated at runtime by `ModDownloader`.

---

## 2. Bug fixes

### 2.1 Version desync
`config/AppConfig.kt` reported `1.5.4` / code `154` while `app/build.gradle.kts` shipped
`1.5.6` / `156`. The About panel and the `X-Client-Version` header disagreed with the installed
APK. Synced `VERSION_NAME = "v1.5.6"`, `VERSION_CODE = 156`.

### 2.2 Root bridge cleanup wrote into a consumed stream
`InteractiveProcessBridge.cleanup()` wrote `"exit\n"` to the child's stdin after the exec'd
binary had already consumed and closed it. The write threw on some ROMs and aborted the rest of
the teardown. Replaced with `outputStream?.close()` and nulled `readerJob` so a subsequent
`start()` cannot observe a stale job.

### 2.3 Broken line-join in driver matching
`DriverRepository.findMatchingDrivers` had a malformed multi-line expression (chained calls split
across lines without continuation), which Kotlin parses as separate statements. Repaired.

### 2.4 Bug report screen used a removed overload
`ReportBugScreen` still called the 6-argument `submitBugReport`. Repointed to the type-safe
10-argument overload `(title, description, deviceModel, androidVersion, appVersion,
kernelVersion, isRoot, licenseKey, hwid, logcatTrace)` and removed the duplicated `val payload`
block that had been left behind.

---

## 3. XML resource foundation (new)

The app was Compose-only: no themes beyond `Theme.APatch`, no strings file that survived,
no dimens, no reusable drawables. Everything was hardcoded in Kotlin. This section establishes
the resource layer that the new XML screens consume.

### 3.1 `res/values/colors.xml`
Full `nin_*` palette: surface ladder (`nin_background` → `nin_terminal_bg`), accents
(`nin_primary` / `nin_secondary` / `nin_tertiary` + containers), text (`nin_text_primary`,
`nin_text_secondary`, `nin_text_disabled`), outlines, semantic states (`nin_success`,
`nin_warning`, `nin_error`, `nin_info` each with a container), mod identity accents
(`nin_kernel_accent` violet, `nin_external_accent` cyan), terminal log levels
(`nin_log_system/root/error/cancel/default`), transparent helpers (`nin_transparent`,
`nin_scrim`, `nin_ripple`).
Legacy `apatch_*` aliases are retained so the old theme references and the Compose palette
keep resolving during the migration window.

### 3.2 `res/values/dimens.xml`
- Spacing scale `space_xxs` (2dp) → `space_3xl` (32dp).
- Screen gutters and top/bottom insets.
- Card geometry: `radius_card` 20dp (matches `docs/rules.md`), `radius_item` 14dp,
  `radius_button` 14dp, `radius_pill` 999dp.
- Icon buckets `icon_sm/md/lg` and `icon_box_sm/md/lg`.
- Touch targets: `touch_target` 48dp (accessibility floor), `row_min_height` 56dp,
  `button_height` 48dp.
- Text sizes `text_display` 28sp → `text_micro` 10sp, `text_clock` 30sp.
- Terminal and drawer dimensions.

### 3.3 `res/values/strings.xml`
Rewritten as a static-only set. Everything is now a resource: `app_name`, brand, all
`nav_*` drawer labels, generic actions (`action_back`, `action_refresh`, `action_download`,
`action_start`, `action_stop`, `action_sign_out`, …), states, license clock captions,
dashboard section labels, support labels, and error copy. No screen hardcodes user-facing text
once ported.

### 3.4 `res/values/themes.xml`
- `Theme.DriverLoader` — parent `Theme.Material3.Dark.NoActionBar`, maps the whole M3 colour
  role set onto the `nin_*` palette, dark status/navigation bars, cutout `shortEdges`.
- `Theme.DriverLoader.Launch` — identical window background with the preview window disabled, so
  the launcher transition has no flash.
- `Theme.DriverLoader.Dialog` — `ThemeOverlay.Material3.Dark` based, for XML dialogs.

### 3.5 Drawables (shape / selector)

| File | Use |
|---|---|
| `bg_card` | Outer card surface with 1dp outline |
| `bg_card_variant` | Nested / secondary card |
| `bg_terminal` | Terminal output box |
| `bg_pill`, `bg_pill_selected` | Filter and status pills |
| `bg_circle` | Icon background circles |
| `bg_button_primary` | Primary button, with disabled + pressed states |
| `bg_button_outline` | Secondary button, with disabled + pressed states |
| `bg_input` | Text field background, with disabled state |
| `bg_chip_filter` | Selectable filter chip |
| `bg_row_selectable` | Drawer / list row, with selected + pressed states |
| `bg_dropzone` | Dashed upload target |
| `bg_badge_success`, `bg_badge_error`, `bg_badge_warning` | Status badges |
| `bg_chip_plain` | Static label chip |
| `dot_status_active`, `dot_status_inactive` | 8dp status dots |

### 3.6 Vector icons (29)

`ic_dashboard`, `ic_driver`, `ic_shield`, `ic_fingerprint`, `ic_settings`, `ic_support`,
`ic_wallet`, `ic_license_key`, `ic_profile`, `ic_bug`, `ic_lock`, `ic_chat`, `ic_arrow_back`,
`ic_menu`, `ic_refresh`, `ic_download`, `ic_play`, `ic_pause`, `ic_check`, `ic_close`,
`ic_warning`, `ic_info`, `ic_error`, `ic_timer`, `ic_notifications`, `ic_expand_more`,
`ic_search`, `ic_delete`, `ic_check_circle`.

All are 24dp / 24 viewport, monochrome, tinted by `?attr/colorControlNormal` so a layout can
recolour them with `app:tint` or `android:tint`.

---

## 4. Component migration (in progress)

Compose screens being replaced one for one by an XML layout plus a host component.
The data layer is untouched — `ShellExecutor`, `InteractiveProcessBridge`, `DriverRepository`,
`DenyListManager`, `DeviceIdentityManager`, `ModDownloader`, `SessionManager`, `UserRepository`
and `AuthApiClient` stay exactly as they are. Only the presentation layer moves.

See section 5 for the file inventory as it lands.

---

## 5. Change inventory (resources and components)

### 5.1 Layouts (`res/layout`, 35 files)

| Screen | Layout | Host component |
|---|---|---|
| Auth shell | `activity_login.xml` | `LoginActivity` |
| Login form | `fragment_login.xml` | `LoginFragment` |
| Signup form | `fragment_signup.xml` | `SignupFragment` |
| Forgot password | `fragment_forgot_password.xml` | `ForgotPasswordFragment` |
| Main shell | `activity_main.xml` | `MainActivity` |
| Drawer header | `nav_header.xml` | inflated by `NavigationView` |
| Dashboard | `fragment_dashboard.xml` | `DashboardFragment` |
| Driver loader | `fragment_loader.xml` | `LoaderFragment` |
| Daily list / root cloak | `fragment_denylist.xml` | `DenyListFragment` |
| Anti-blacklist / IMEI | `fragment_identity.xml` | `IdentityFragment` |
| My licenses | `fragment_licenses.xml` | `LicensesFragment` |
| Add funds (wallet) | `fragment_add_funds.xml` | `AddFundsFragment` |
| Deposit form | `fragment_submit_deposit.xml` | `SubmitDepositFragment` |
| Profile | `fragment_profile.xml` | `ProfileFragment` |
| Change password | `fragment_change_password.xml` | `ChangePasswordFragment` |
| Settings | `fragment_settings.xml` | `SettingsFragment` |
| Report bug | `fragment_report_bug.xml` | `ReportBugFragment` |
| Support inbox | `fragment_support.xml` | `SupportFragment` |
| Create ticket | `fragment_create_ticket.xml` | `CreateTicketFragment` |
| Ticket chat | `fragment_ticket_chat.xml` | `TicketChatFragment` |
| Mod flow | `dialog_mod_flow.xml` | `ModFlowDialog` |
| Buy key sheet | `sheet_purchase_key.xml` | `PurchaseKeySheet` |
| Generic picker sheet | `sheet_selection.xml` | `SelectionSheet` |
| Identity field block | `partial_identity_field.xml` | 8 `<include>`s in `fragment_identity.xml` |
| Mod row | `item_mod_row.xml` | `ModRowAdapter` |
| App row | `item_app_row.xml` | `AppListAdapter` |
| Process row | `item_process_row.xml` | `AppListAdapter` (nested) |
| Log line | `item_log_line.xml` | `LogLineAdapter`, `ModFlowDialog` |
| License key row | `item_license_key.xml` | `LicenseKeyAdapter` |
| Plan row | `item_plan_row.xml` | `PurchaseKeySheet.PlanAdapter` |
| Payment method row | `item_payment_method.xml` | `SubmitDepositFragment.PaymentMethodAdapter` |
| Transaction row | `item_transaction_row.xml` | `TransactionAdapter` |
| Ticket row | `item_ticket_row.xml` | `TicketAdapter` |
| Message row | `item_message_row.xml` | `TicketChatFragment.MessageAdapter` |
| Sheet row | `item_sheet_row.xml` | `SelectionSheet.RowAdapter` |

### 5.2 New support resources

- `res/menu/drawer_menu.xml` — three groups (Workspace / Account / Support), nine `nav_*` items matching `Destination`.
- `res/menu/top_app_bar.xml` — toolbar actions.
- `res/anim/slide_in_right.xml`, `slide_out_right.xml`, `fade_in.xml`, `fade_out.xml` — `openSubScreen` + auth swap transitions.
- `res/color/nav_item_tint.xml` — drawer item tint selector.

### 5.3 Components written

| File | Role |
|---|---|
| `ui/MainActivity.kt` | Drawer shell, `Destination` enum, `openSubScreen`, back handling, session verify, `signOut` |
| `ui/login/LoginActivity.kt` | Auth host, stored-session verify, `showAuthScreen`, `onLoginSucceeded` |
| `ui/login/LoginFragment.kt` | Credential submit, inline errors, HWID attach |
| `ui/login/SignupFragment.kt` | Registration with confirm-password validation |
| `ui/login/ForgotPasswordFragment.kt` | Reset dispatch |
| `ui/dashboard/DashboardFragment.kt` | Broadcast banner, license clock, kernel + external lists |
| `ui/dashboard/DashboardViewModel.kt` | Broadcast feed only |
| `ui/loader/LoaderFragment.kt` | Kernel release, state cards, QX/RT modes, terminal |
| `ui/loader/LogLineAdapter.kt` | Colour-coded log lines |
| `ui/denylist/DenyListFragment.kt` | Environment readout, global switch, search, quick cloak |
| `ui/denylist/AppListAdapter.kt` | Expandable app/process rows |
| `ui/identity/IdentityFragment.kt` | Eight identity fields, randomize/reset/apply/wipe |
| `ui/licenses/LicensesFragment.kt` | Live clock, key list, copy, HWID reset |
| `ui/licenses/LicenseKeyAdapter.kt` | Key rows with reset budget |
| `ui/deposit/AddFundsFragment.kt` | Balance + transaction ledger |
| `ui/deposit/SubmitDepositFragment.kt` | Gateway list, amount/TrxID submit |
| `ui/profile/ProfileFragment.kt` | Identity header, balance, license block, action rows |
| `ui/profile/ChangePasswordFragment.kt` | Password update |
| `ui/settings/SettingsFragment.kt` | Permissions, namespace flags, update check, reboot sheet |
| `ui/settings/ReportBugFragment.kt` | Bug report with live logcat capture |
| `ui/support/SupportFragment.kt` | Ticket inbox with filter |
| `ui/support/TicketAdapter.kt` | Ticket rows with status colouring |
| `ui/support/CreateTicketFragment.kt` | Subject/body/priority submit |
| `ui/support/TicketChatFragment.kt` | Thread view, aligned bubbles, reply |
| `ui/mod/ModEngine.kt` | Shared scan/self-heal/download/launch per variant |
| `ui/mod/ModRuntime.kt` | One bridge + one engine per variant, process-wide |
| `ui/mod/ModRowAdapter.kt` | Kernel/external row styling |
| `ui/clock/LicenseClock.kt` | Monotonic per-second countdown flow |
| `ui/dialogs/ModFlowDialog.kt` | Six-stage mod launch state machine |
| `ui/dialogs/PurchaseKeySheet.kt` | Plan list + wallet purchase |
| `ui/dialogs/SelectionSheet.kt` | Generic single-choice sheet |

### 5.4 Compose removal

Deleted after the XML hosts landed: `ui/compose/`, `ui/theme/`, `ui/component/`, `ui/setup/`, `ui/signup/`, `ui/forgotpassword/`, `ui/dashboard/kernel_mods/`, `ui/dashboard/external_mods/`, `util/ui/`, plus
`DashboardScreen.kt`, `DriverLoaderScreen.kt`, `DenyListScreen.kt`, `LicenseClockCard.kt`,
`AppBottomSheet.kt`, `ModActionDialogs.kt`, `PurchaseKeyBottomSheet.kt`, `AddFundsScreen.kt`,
`SubmitDepositScreen.kt`, `DeviceIdentityScreen.kt`, `MyLicensesScreen.kt`, `LoginScreen.kt`,
`ProfileScreen.kt`, `ChangePasswordScreen.kt`, `ReportBugScreen.kt`, `CreateTicketScreen.kt`,
`UserSupportScreen.kt`, `UserTicketChatScreen.kt`.

Final tree: 46 Kotlin files, 35 layouts, 37 drawables. `androidx.compose` appears nowhere.

### 5.5 Post-migration fixes

- `fragment_licenses.xml` was missing the root `xmlns:app` declaration while using `app:tint` and
  the `app:icon` / `app:iconSize` attributes. Declared it on the `SwipeRefreshLayout` root.
- `fragment_dashboard.xml`, `item_payment_method.xml`, `item_sheet_row.xml` declared `xmlns:app`
  inline on a child element. Hoisted to the root element of each file.
- `ProfileFragment` imported `Destination` without using it; `LoginActivity` imported
  `AuthApiClient` without using it; `DenyListFragment` imported `CompoundButton` without using
  it. Removed.
- `AddFundsFragment` carried a mojibake middle-dot separator and fully-qualified binding class
  references. Rewritten to import `TransactionItem`, `ItemTransactionRowBinding`,
  `ContextCompat` and `Currency`, with a real `·` separator.
- `LicenseKeyAdapter` was building its label from `licenses_my_keys` by string surgery. Now uses
  the game type as the label and shows `DURATION · STATUS` in the pill.
- `ReportBugFragment` sent `session.isLoggedIn` as the `isRoot` flag. Now calls
  `ShellExecutor.isRootAvailable()` off the main thread.
- `ForgotPasswordFragment` called `R.string.forgot_password_sent` with no argument while the
  string carries `%1$s`. Now passes the submitted email.
- `ModFlowDialog` used fully-qualified `RecyclerView` / `ContextCompat` names inside the terminal
  adapter. Imported properly.
- 14 unused drawables removed (`bg_button_primary`, `bg_button_outline`, `bg_input`,
  `bg_chip_filter`, `bg_chip_plain`, `bg_card_variant`, `bg_pill_selected`, `bg_dropzone`,
  `bg_badge_warning`, `dot_status_active`, `dot_status_inactive`, `ic_error`, `ic_info`,
  `ic_user_profile`).

### 5.6 Static verification performed

- Every `@string/*` referenced from Kotlin resolves against `values/strings.xml` (406 keys).
- Every `R.id.*` referenced from Kotlin exists in some `@+id` across `res/`.
- Every `R.drawable/color/dimen/anim/menu/layout` reference resolves.
- Every `binding.<field>` maps to an id in that host's layout (verified per file with the layout
  map, and globally as a fallback).
- Every `@string/@color/@dimen/@style/@drawable` referenced from XML resolves, except three
  library-provided symbols: `string/appbar_scrolling_view_behavior`,
  `style/TextAppearance.Material3.LabelMedium`, `style/TextAppearance.Material3.TitleLarge`.
- All XML files parse; no duplicate `xmlns:app`; no file uses `app:` without declaring it.
- No unresolved `com.andorid.ninmods.*` imports; no unused imports remain; braces balanced.
- No `androidx.compose` or deleted-symbol references remain in the source tree.
- No compile has been run — see section 0.

