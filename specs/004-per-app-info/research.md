# Research: Per-App Information and Details Screen

**Feature**: `004-per-app-info` | **Date**: 2026-10-07 | **Input**: [spec.md](./spec.md)

Resolves the open technical questions raised by the spec's assumptions and clarified
answers (per-app memory ceiling, usage-access grant, refresh intervals, row-tap action),
plus the Constitutional requirement that every dependency and permission be justified in
the plan. Evidence sources: the existing 001–003 code and artifacts, the Android 15/16
platform surface (`minSdk 35`, `compileSdk 37`), and the user's confirmations recorded in
spec.md §Clarifications. Each entry records the decision, rationale, and alternatives
considered.

Consolidated verdict up front:

- **Zero new dependencies; one manifest addition** — the `PACKAGE_USAGE_STATS` appop
  declaration that lets the user grant usage access for per-app storage (R-02). The app
  inventory needs no new permission: 002 already ships `QUERY_ALL_PACKAGES` (R-01).
- **Per-app memory has no public read path** for third-party apps on current Android;
  the reader returns `null` and the figure renders the honest "Not available" indication
  the user confirmed (R-03).
- **The Details screen is a second instance of the home screen's proven architecture**:
  state holder with epoch/coalescing/no-refresh-reset (003), `PullToRefreshBox`
  (003), readers as one-shot fun-interfaces (002) (R-07).
- **Auto-refresh is a lifecycle-gated coroutine ticker in the shell** — the only moving
  part is one `LaunchedEffect` loop; there is no background work of any kind (R-08).

---

## R-01 · Installed-application inventory: enumeration, marks, ordering

**Decision**: a new one-shot reader `InstalledAppReader` performs one
`PackageManager.getInstalledApplications(0)` enumeration and maps each entry to the
domain `InstalledApp`: `loadLabel(packageManager).toString()` as display name, the
package name as identity, classification via the existing **shared**
`isSystemApplication(flags)` bit test (data/AppClassifier.kt), and the list is sorted
alphabetically by display name in the domain factory (see data-model §2). The existing
`ApplicationCounter` keeps serving the home counts unchanged.

**Rationale**:

- `QUERY_ALL_PACKAGES` is already declared and reviewed (002 R-01, manifest comment) —
  enumerating every package needs **no new permission** (Constitution VIII).
- Reusing `isSystemApplication` for the per-app mark is what makes SC-001/SC-002's
  "counts and marks cannot disagree" hold by construction (spec assumption); the home
  count reader and the details list reader classify through the same pure function.
- `getInstalledApplications` returns `ApplicationInfo` cheaply (one binder call + per-app
  label resolution); `loadLabel` hits a PackageManager label cache, so a 200+ app list
  maps in well under the SC-007 budget off the main thread.
- Alphabetical ordering by display name is the spec's recorded assumption; `Collator`
  (JDK, locale-aware) sorts it correctly and stays JVM-testable.

**Alternatives considered**:

- *Replace `ApplicationCounter` with the new reader everywhere (derive home counts from
  the full list)*: one reader instead of two — but the home screen then pays for label
  resolution and per-app storage stats it never displays. Keeping the cheap counter for
  home is the smaller runtime cost for three extra lines of seam. Rejected.
- *Sort by package name*: stable but user-hostile (users know display names). Rejected
  against the spec assumption.

## R-02 · Per-app storage figures: `StorageStatsManager` + the usage-access appop

**Decision**: the `InstalledAppReader` fills each `InstalledApp.storageBytes` from
`StorageStatsManager.queryStatsForPackage(uid, packageName, user)` (`codeBytes +
dataBytes + cacheBytes`), **only when** usage access is granted; otherwise every
`storageBytes` is `null` and the UI shows the not-available indication plus one hint
row with a button that opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Grant state is
checked per read pass via `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS,
uid, packageName) == MODE_ALLOWED`, exposed to the UI by the same reader pass (see
data-model §5). The manifest gains `<uses-permission
android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>`
— an appop declaration, silent at install time; it changes no granted state by itself,
it makes the Settings usage-access toggle applicable to this app.

**Rationale**:

- On API 26+ this is the platform's only per-app storage API; `PackageStats`-era
  alternatives are hidden/deprecated. `queryStatsForPackage` explicitly requires the
  usage-stats appop — there is no lesser grant that works.
- The appop declaration is **not** a runtime permission: no prompt, no install-time
  dialog, purely the mechanism by which the user *can* grant special access — exactly
  the spec's FR-008 model (list works without; figures appear after granting). This is
  the standard pattern used by launchers and device analyzers.
- F-Droid position (Constitution VIII): the permission maps 1:1 to a user-visible
  feature (per-app storage figures), is not proprietary, and the app functions fully
  without it. Recorded in plan.md's gate table.
- Cost: one binder call per app. ~200 apps ≈ a few hundred ms on the background
  executor — inside the 2 s settle budget (SC from 003), and the pass is skippable:
  when access is not granted, the reader skips the per-app calls entirely.
- Emulator validation path (Constitution IV): the stock emulator's Settings exposes
  Usage access; quickstart M-2 drives the full deny → grant → figures-appear flow with
  `adb` cross-checks (`dumpsys package`, `pm`).

**Alternatives considered**:

- *Approximate per-app storage from APK file lengths*: readable without any grant, but
  reports only code size — a wrong answer to "storage consumed" (data + caches
  excluded), violating the never-display-a-wrong-number policy. Rejected.
- *Request the grant via a system dialog on first Details open*: no such dialog exists
  for appops; the only platform path is the Settings page, which is what FR-008
  specifies. N/A.
- *Skip per-app storage entirely until granted forever*: contradicts FR-006 once
  granted. Rejected.

## R-03 · Per-app memory: no public read path — honest "Not available"

**Decision**: `InstalledApp.memoryBytes` is `null` by design; the reader never attempts
a per-app RAM read. Every Details row renders the existing distinct
"figure_unavailable" indication in the memory slot.

**Rationale**:

- Current Android offers no API by which an ordinary app can read another app's RAM
  usage: `ActivityManager.getRunningAppProcesses()` returns only the caller's own
  processes on modern Android, `getProcessMemoryInfo` needs those foreign PIDs,
  `/proc/<pid>` of other UIDs is blocked by SELinux, and the usage-stats appop exposes
  usage *time*, not memory. The user confirmed keep-the-figure-show-not-available on
  2026-10-07 (spec §Clarifications).
- The existing `FigureUiState.Unavailable` rendering (distinct indication, never zero —
  002 FR-012) already encodes exactly this honesty; no new UI mechanism is needed.
- `ponytail:` ceiling note goes on the reader: the field exists so a future platform
  API or a privileged build can light it up without touching the UI contract.

**Alternatives considered**:

- *Drop the memory figure (storage-only rows)*: rejected by the user (clarification).
- *Show the app's own process memory as a proxy*: wrong number for every other app.
  Rejected.

## R-04 · Processor core tiers: sysfs frequencies with total-count fallback

**Decision**: a new one-shot `CoreTierReader` groups logical cores by their
`cpuinfo_max_freq` read from `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq`,
and returns `CoreTiers(totalCount, tiers)` where each tier is `(count, maxFrequencyHz)`
and counts sum to the total. **Fallback inside the reader**: if the sysfs walk fails
(SELinux/vendor variation) or yields a single frequency for all cores, the reader
returns single-tier `CoreTiers(total = Runtime.availableProcessors())`. The home figure
type becomes `FigureUiState<CoreTiers>`; formatting renders a single tier as today's
plain count and multiple tiers as "N cores: a × f₁ + b × f₂" (R-10).

**Rationale**:

- There is no public cluster API on Android; max-frequency grouping is the standard
  inference (little/big/prime cores sit at distinct max frequencies) and the sysfs
  cpufreq interface is world-readable on mainstream devices. "If possible" in the spec
  is honored: where the device (or the emulator — typically one cluster) does not
  distinguish, the display is exactly today's core count (FR-013, SC-008).
- Folding the fallback into the reader (rather than the UI) keeps the figure a single
  three-state value and the formatting rule trivial (`tiers.size == 1` → count only).
- Frequencies are grouped as distinct values, not binned — silicon ships exact
  per-cluster figures; binning would invent tiers that may not exist.

**Alternatives considered**:

- *`Build.SUPPORTED_ABIS`/`SoC` props parsing*: no core-type information. N/A.
- *Reader returns `null` on single-cluster and the UI falls back to the old
  `CoreCount` figure*: two figure types for one visual slot — more states to test for
  zero user-visible gain. Rejected.

## R-05 · Footer and navigation: third destination, gapless rectangle

**Decision**: `Destination` gains `DETAILS`; the footer becomes a `Row` with **no
outer padding and no `spacedBy`**, three equal (`weight(1f)`) `Button` (selected) /
`FilledTonalButton` (unselected) with `shape = RectangleShape`, filling the width — one
continuous square-cornered bar (FR-001..FR-003). The system back gesture returns to
`HOME` from both `DETAILS` and `SETTINGS` (`BackHandler(enabled = destination != HOME)`);
from `HOME` back exits as today. Footer label strings become "Home", "Details",
"Settings" (`footer_home` reworded; spec's own names for the three destinations).

**Rationale**:

- The hand-rolled `mutableStateOf` switch (002 R-08) extends by one enum entry and one
  `when` branch — adding the Navigation-Compose dependency for a three-tab flat shell
  would violate Constitution VII for zero capability gain.
- `RectangleShape` + removed spacing is the whole restyle: the existing filled/tonal
  selected-state semantics, a11y `selected` flags, and 48 dp-class default button
  heights carry over untouched (FR-003, footer a11y unchanged).
- Back-to-HOME from both non-home tabs matches the shell's existing mental model
  (002 U-5) and needs no back stack.

**Alternatives considered**:

- *Navigation-Compose*: rejected (dependency, Constitution VII).
- *Keep rounded buttons, only add the tab*: contradicts FR-002. Rejected.

## R-06 · Home → Details filter hand-off

**Decision**: the shell (`AnalyzerApp`) owns `pendingDetailsFilter: AppCategoryFilter?`.
Tapping a Home application entry calls `onOpenApplications(filter)` → the shell sets the
pending filter and switches `destination = DETAILS`; `DetailsScreen` applies a non-null
pending filter into its holder (`setFilter`, overriding whatever was chosen before) and
clears it. The Details filter otherwise survives in-session tab switches; a fresh
process starts at `ALL` (spec assumption).

**Rationale**:

- The override rule (FR-012: arrival from Home overrides a previously chosen filter) is
  a shell-level navigation concern — the pending-filter slot encodes exactly "an
  arrival directive", consumed once, and cannot desynchronize the footer state.
- Enum-with-payload would turn `Destination` into a sealed class and complicate the
  footer's selected-state checks; a parallel nullable slot is the smaller diff.

**Alternatives considered**:

- *Details always resets to `ALL` on tab entry*: violates the spec's "until the user
  changes it or arrives from a Home entry" assumption. Rejected.
- *Persist the filter*: nothing in the spec asks for cross-restart persistence.
  Rejected (YAGNI).

## R-07 · Details state holder: a second instance of the home architecture

**Decision**: `DetailsStateHolder` mirrors `HomeStateHolder`: one inventory figure
(`FigureUiState<AppInventory>`), a usage-access flag posted by the same pass, the 003
refresh semantics (`refresh()` no-reset + coalescing + epoch guard + `isRefreshing`),
presentation trigger `startReadCycle()` with reset-to-`Loading`, `shutdown()` on dispose
with executor replacement, `ResultPoster` + `executorFactory` injection seams. It
additionally owns `filter: AppCategoryFilter` (Compose state) and `setFilter()`.

**Rationale**:

- The 003 architecture (single-thread executor pass, FIFO poster ordering, epoch drop
  of superseded posts) already answers FR-009/FR-010 for this screen; re-deriving new
  semantics would be re-designing a solved problem. The holders stay separate classes:
  their figure sets, triggers, and lifecycles differ (Home has five figures and its own
  cycle timing; Details has one big inventory read) — a shared abstract holder would be
  the unrequested abstraction Constitution VII forbids.
- Filtering is pure domain logic (`AppCategoryFilter.apply`) over the posted inventory;
  the holder only stores the selection, so filter changes are instant recompositions,
  not re-reads (SC-007, FR-005).

**Alternatives considered**:

- *One generic `StateHolder<T>` for both screens*: parameterized trigger matrix and
  figure fan-out — abstraction nobody asked for. Rejected.
- *ViewModel*: introduces the lifecycle-viewmodel dependency and survives-process-death
  semantics nothing in the spec needs (the app already ships none). Rejected.

## R-08 · Automatic refresh: lifecycle-gated ticker in the shell

**Decision**: `AnalyzerApp` runs one `LaunchedEffect(mode, destination)` loop: when
`mode.intervalMillis != null` and the activity is at least `RESUMED`, it sleeps the
interval, then calls the visible screen's `refresh()`. Lifecycle gating uses the same
`DefaultLifecycleObserver` pattern the screens already use; when not resumed, the loop
idles on a state read (no ticking in background — FR-017). The pull-down gesture stays
available in every mode (FR-016). On-demand is simply the `intervalMillis == null`
no-op case — the default (FR-014).

**Rationale**:

- FR-017 forbids background refresh; a composition-scoped coroutine that only fires
  while resumed is the minimal platform-idiomatic mechanism — `WorkManager` exists for
  background/deferred work this feature must not do, so not using it is the compliant
  choice (Constitution IX: zero background work beats batched background work).
- Routing ticks to the visible screen's holder reuses the 003 coalescing guard for
  free: a tick landing during an in-flight pass is absorbed, never queued.
- Shell ownership means one ticker, one place where `mode` and `destination` meet;
  screens stay ignorant of the setting (and of each other).

**Alternatives considered**:

- *Per-screen tickers*: two loops, duplicated gating, and the hidden screen's ticker
  would need its own suppression. Rejected.
- *`WorkManager` periodic work*: background work by definition — violates FR-017 and
  drains battery for an app that is not visible. Rejected.

## R-09 · Refresh mode setting: domain enum + SharedPreferences store

**Decision**: `RefreshMode { ON_DEMAND, THIRTY_SECONDS, ONE_MINUTE, FIVE_MINUTES }`
with `intervalMillis: Long?` (`null` only for `ON_DEMAND`) and a
`fromPersisted(String?)` mapping (missing/corrupt → `ON_DEMAND`, mirroring
`ThemePreference`). Persistence is a `RefreshModeStore` interface implemented by
`SharedPreferencesRefreshModeStore` — same `android_analyzer` prefs file, new key
`refresh_mode`, enum-name value, `apply()` writes. `SettingsScreen` gains a second
radio section ("Automatic refresh", four rows, the same `ThemeOptionRow` pattern);
`MainActivity` wires load/save exactly as it does the theme.

**Rationale**:

- The intervals are the user-confirmed set (spec §Clarifications, FR-015); the enum
  makes the store value and the ticker arithmetic trivial and JVM-testable.
- Duplicating the theme-store shape (rather than generalizing to a key-value settings
  framework) is three small files vs an unrequested abstraction (Constitution VII).

**Alternatives considered**:

- *One generic `PreferencesStore<T>`*: two use cases do not justify a generic
  framework. Rejected.
- *Storing raw millis*: wider corrupt-value surface for zero benefit. Rejected.

## R-10 · Strings and value formatting

**Decision**: new string resources only where user-visible text appears: footer
(`footer_details` "Details"; `footer_home` reworded to "Home"), the two home entries
(`figure_user_applications`, `figure_system_applications`), Details screen (title,
filter labels All/User/System, marks System/User, per-app storage/memory templates,
usage-access hint + grant button, per-filter empty state), settings (refresh section
label + four option labels), processor tiers template (`processor_tiers_value`
"%1$d cores: %2$s"). `figure_applications` and `applications_value` become unused and
are **removed**. `FigureFormatting` gains `userApplicationsValue` /
`systemApplicationsValue` (plain locale-grouped counts) and `processorValue(CoreTiers)`
(single tier → count only, matching today's rendering; multi-tier → tiers string,
frequencies rendered as GHz with one decimal via locale formatting); per-app bytes
reuse the injected `formatBytes` seam. The 003 refresh a11y strings are reused
verbatim on Details (action + live region).

**Rationale**: every displayed figure keeps the established label + value + explicit
not-available rendering (FR-006/FR-007, H-4/H-5); templates live in resources so the
JVM formatting tests keep working with literal strings (002 R-12/R-14 pattern).

## R-11 · Testing strategy

**Decision**: all new logic is JVM-testable through the established seams — pure domain
factories/filters/mappings (`InstalledAppsTest`, `CoreTiersTest`, `RefreshModeTest`),
`FigureFormatting` with literal templates (extended `FigureFormattingTest`), and
`DetailsStateHolderTest` through `ResultPoster`/`executorFactory` fakes covering: the
VR-equivalent cycle behaviors (no-reset refresh, absorption, epoch drop, executor
replacement), the filter application over a faked inventory, and the pending-filter
override. `HomeStateHolderTest` is adjusted only for the processor figure's type
change. Manifest, grant flow, `StorageStatsManager` numbers, tier detection on real
silicon, auto-refresh cadence, and footer visuals are verified by the quickstart manual
matrix (Constitution IV accepts this split). No new test infrastructure.

## R-12 · Dependency and permission ledger (Constitution VII/VIII)

**Decision**: no library changes; the only dependency-adjacent additions are platform
framework classes (`StorageStatsManager`, `AppOpsManager`, `android.settings` intents,
`Collator`) and already-pinned Compose APIs (`LazyColumn` — Foundation; `RectangleShape`
— UI; filter row = stock Material3, no new widget). The single manifest change is the
`PACKAGE_USAGE_STATS` appop declaration (R-02). The permission surface after this
feature: `QUERY_ALL_PACKAGES` (002, unchanged) + `PACKAGE_USAGE_STATS` (appop,
user-granted special access) — both mapped to user-visible features in this plan.

---

## Post-design Constitution re-check (after Phase 1)

Re-evaluated 2026-10-07 against the Phase 1 artifacts
([data-model.md](./data-model.md),
[contracts/app-inventory.md](./contracts/app-inventory.md),
[contracts/navigation-and-footer.md](./contracts/navigation-and-footer.md),
[contracts/details-screen.md](./contracts/details-screen.md),
[contracts/refresh-mode.md](./contracts/refresh-mode.md),
[quickstart.md](./quickstart.md)):

- All Core Principles I–IX: **PASS** — verdicts unchanged from the plan.md gate table.
  The design adds no dependency (R-12), one appop permission declaration mapped to a
  user-visible feature (R-02, VIII), zero background work (R-08 — the ticker is
  composition-scoped and resume-gated, IX), and one new preference key (R-09). The new
  `ui/details` package mirrors the existing screen layout; no new abstractions beyond
  the reader/store fun-interfaces that already define the app's seam (VII).
- Complexity tracking table in [plan.md](./plan.md): remains empty — no violations to
  justify.
