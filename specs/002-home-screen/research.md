# Research: Home Screen

**Feature**: `002-home-screen` | **Date**: 2026-10-05 | **Input**: [spec.md](./spec.md)

Resolves the open technical questions raised by the spec (assumptions "App enumeration
access", "Snapshot behavior", plus the Constitutional requirement that every permission
and dependency be justified in the plan). Each entry records the decision, rationale, and
alternatives considered. Sources: official Android platform documentation and API
references (verified 2026-10-05), feature 001 artifacts (pinned toolchain), and the
spec's clarification sessions.

Consolidated verdict up front:

- **Zero new third-party dependencies** (R-16). Every new capability comes from Android
  platform APIs, `java.util.concurrent`, or the Compose stack already shipped by feature
  001.
- **Exactly one new permission**: `android.permission.QUERY_ALL_PACKAGES` (R-01),
  strictly required to enumerate all installed applications (FR-004, FR-013,
  Constitution VIII).

---

## R-01 · Enumerating and classifying installed applications

**Decision**: Declare `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>`
and enumerate with `PackageManager.getInstalledApplications(0)`. Classify an application
as a system application iff `(applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0`;
the non-system count is everything else, and the total count is the full list size.

**Rationale**:

- Since Android 11 (API 30), package visibility filtering removes other applications
  from `getInstalledApplications()`/`getInstalledPackages()` results unless the app
  declares matching `<queries>` entries or holds `QUERY_ALL_PACKAGES`. FR-004 requires
  the **total** count including system applications — full enumeration is strictly
  required, and no `<queries>` subset produces it (see alternatives).
- `QUERY_ALL_PACKAGES` is a normal, install-time permission (no runtime dialog, no
  background cost). Its heavy restriction is a **Google Play** policy; this project's
  distribution target is **F-Droid** (Constitution VIII), which has no such restriction.
  A device inventory displayed on-screen is exactly the user-visible feature the
  permission maps to, satisfying Constitution VIII's "any permission MUST be justified by
  a user-visible feature" and FR-013's plan-level justification requirement.
- `ApplicationInfo.flags` is populated even when `getInstalledApplications` is called
  with `0` additional flags, so no heavyweight `GET_*` match flags are needed — the
  enumeration stays cheap (one binder call, no icon/label loading).
- Updated system apps: when a preinstalled app is updated (e.g., via a store), its
  `ApplicationInfo` carries **both** `FLAG_UPDATED_SYSTEM_APP` **and** `FLAG_SYSTEM`
  (confirmed in platform behavior and the ApplicationInfo reference). Therefore the
  single `FLAG_SYSTEM` check classifies updated preinstalled apps as system — precisely
  the FR-005 rule resolved in clarification session 2026-10-05.

**Alternatives considered**:

- *`<queries>` with `MAIN`/`LAUNCHER` intent filter*: sees only apps that expose a
  launcher activity. It undercounts both the total (system services, shared libraries,
  and apps without launcher activities are invisible) and the non-system figure
  (user-installed apps without launchers are missed), and the undercount varies per
  device — a silent correctness violation of FR-004/SC-003. Rejected.
- *No visibility declaration at all*: the app would see essentially only itself — the
  feature is impossible. Rejected.
- *`QUERY_ALL_PACKAGES` alternatives on Play*: Play policy workarounds (accessibility
  apps, launchers) are irrelevant — the app does not target Play.

---

## R-02 · Device memory (RAM) reading

**Decision**: `ActivityManager.getMemoryInfo(ActivityManager.MemoryInfo())` — report
`totalMem` as the total and `availMem` as available; derive **allocated =
`totalMem − availMem`** from that single `MemoryInfo` object.

**Rationale**:

- One `getMemoryInfo` call is one atomic system-wide reading — both figures of the pair
  come from the same object, satisfying FR-002's "derived from a single reading of
  device state" by construction.
- `availMem` is the kernel-reported available memory; `totalMem` is total RAM accessible
  by the kernel. Their difference is the spec's "allocated" in the resolved broad
  meaning (device-wide usage by the system plus all applications, assumption
  "Allocated meaning") — not this app's own usage.
- The class is not deprecated and is fully valid on API 35+ (verified in the API
  reference); no permission is required.
- Non-negativity and the sum-≤-total invariant hold trivially by construction
  (0 ≤ availMem ≤ totalMem); validation still guards against a device reporting a
  nonsensical reading (edge case list) by mapping it to FR-012's unavailability
  indication.

**Alternatives considered**:

- *`/proc/meminfo` parsing*: same numbers via file reading with more parsing code and no
  contract; rejected.
- *`Debug.MemoryInfo`*: per-process memory, explicitly not the device-wide figure the
  spec resolved; rejected.
- *`ActivityManager.ProcessStats`/`StorageStatsManager`-style APIs*: heavier,
  permission-touched, built for aggregation over time windows; one-shot snapshot needs
  none of that; rejected.

## R-03 · Internal storage reading

**Decision**: `StatFs(Environment.getDataDirectory().absolutePath)` — report
`totalBytes` as total and `availableBytes` as free; derive **used =
`totalBytes − availableBytes`** from the same single `StatFs` instance.

**Rationale**:

- Internal storage = the data partition (`getDataDirectory()`), which is where the
  system, apps, and user data live (assumption "Allocated meaning").
- `availableBytes` (free and usable by any app) is the user-meaningful "free" figure —
  more correct than `freeBytes`, which includes reserved blocks the user cannot use.
- Both figures come from one `StatFs` object created once per read — the FR-002
  single-reading requirement holds by construction; no permission is needed.

**Alternatives considered**:

- *`StorageStatsManager`*: designed for per-UID storage attribution; total volume
  statistics there either duplicate `StatFs` or require extra plumbing. Rejected.
- *`Environment.getExternalStorageDirectory()`*: that is shared/external storage, out of
  scope by FR-003. Rejected.
- *`Os.statvfs` directly*: `StatFs` is the supported wrapper over the same data;
  raw `Os` adds errno handling for nothing. Rejected.

## R-04 · Battery level and charging state

**Decision**: One-shot read of the sticky `ACTION_BATTERY_CHANGED` broadcast:
`context.registerReceiver(null, IntentFilter(BatteryManager.ACTION_BATTERY_CHANGED))`.
Level = `EXTRA_LEVEL * 100 / EXTRA_SCALE`; charging state mapped from `EXTRA_STATUS`
(`BATTERY_STATUS_CHARGING`/`FULL` → charging; `DISCHARGING`/`NOT_CHARGING` → not
charging).

**Rationale**:

- Passing `null` as the receiver returns the last sticky intent **immediately, without
  registering anything** — it is the official documented pattern for a one-shot current
  status read, needs no permission, and registers no receiver, so it cannot degrade into
  the background monitoring FR-011 forbids.
- The null-receiver form is unaffected by the Android 14+ exported-receiver flag
  requirements (those apply to actual receiver registrations).
- Unavailability (FR-012) maps naturally: a `null` returned intent, or `EXTRA_LEVEL < 0`
  / `EXTRA_SCALE ≤ 0` (present on some emulators/KVM-less environments) yields the
  unavailability indication rather than an invented percentage. Per the spec's battery
  assumption, emulators report a simulated battery, so this is a fallback path, not the
  expected one.

**Alternatives considered**:

- *`BatteryManager.getIntProperty(BATTERY_PROPERTY_CAPACITY)`*: gives only the level —
  a second mechanism would be needed for charging state; one sticky read carries both.
  Rejected (two reads also split the "single reading" spirit).
- *Registering a real `BroadcastReceiver`*: that is continuous monitoring FR-011
  forbids. Rejected.

## R-05 · Processor core count

**Decision**: `Runtime.getRuntime().availableProcessors()`.

**Rationale**: Standard JVM/platform API; on Android it reports the number of CPU cores
the runtime can use. No permission, trivially cheap. Unavailability is not a real
platform state; the model still carries the same three-state figure shape for uniformity
(FR-012/FR-014 apply per-figure uniformly).

**Alternatives considered**: reading `/sys/devices/system/cpu/` — redundant and
needlessly low-level. Rejected.

---

## R-06 · Theme engine (system-follow + manual override)

**Decision**: Material 3 via Compose (already a 001 dependency): `lightColorScheme()` /
`darkColorScheme()` baseline palettes; effective theme = manual selection, or — when the
preference is *system default* — `isSystemInDarkTheme()`.

**Rationale**:

- FR-006/US2's "follows the system without restart" is native Compose behavior: a system
  dark-mode toggle is a configuration change; `isSystemInDarkTheme()` recomposes and the
  new scheme applies with no restart and no code beyond reading it (SC-004).
- On resume from background the same recomposition happens automatically, covering the
  background-switch edge case.
- Manual override (US4) is a top-level `MaterialTheme(colorScheme = …)` selection driven
  by app state; changing the radio option updates state → recomposition → immediate
  effect (FR-010's immediacy half, SC-005's sub-second requirement).

**Alternatives considered**:

- *Dynamic color (`dynamicDarkColorScheme`)*: zero-dependency and modern, but the spec
  demands only dark/light fidelity, and a fixed palette keeps visual verification
  deterministic on emulators that lack wallpaper sources. Deferred (could be a later
  feature); not needed for FR-006.
- *DayNight resources/AppCompat themes*: would reintroduce the appcompat/View stack the
  Compose-only skeleton deliberately avoids. Rejected.
- *`AndroidManifest` `uiMode` configChanges handling*: not needed — default activity
  recreation on uiMode change is correct and cheap for a two-screen Compose app.

## R-07 · Persisting the theme preference

**Decision**: `SharedPreferences` (platform built-in) with a single string key holding
`LIGHT` / `DARK` / `SYSTEM`; writes use `apply()` (async, no main-thread disk I/O on the
selection path), reads happen once at activity creation.

**Rationale**:

- One enum-sized value is squarely the "limited subset of a library's functionality"
  case Constitution VII prefers to solve without a new dependency: SharedPreferences is
  a platform class, adds zero APK weight and zero license surface (Constitution VIII).
- Persistence across restarts (FR-010, US4 scenario 4) needs nothing more than
  write-on-select + read-on-create.

**Alternatives considered**:

- *Jetpack DataStore (Preferences)*: the "modern" choice, but a new third-party
  dependency (+ coroutines/flow plumbing) for one string — fails Principle VII's
  substantial-functionality test. Rejected.
- *`dataStore` via `preferencesDataStore` delegate*: same dependency cost. Rejected.
- *A file in `filesDir` hand-rolled*: strictly worse than SharedPreferences for the same
  dependency-freeness. Rejected.

## R-08 · Navigation between home and settings

**Decision**: Hand-rolled in-memory navigation in the single Activity: a
`MutableStateFlow`-free plain `mutableStateOf(Destination.HOME)` (Compose state) in the
activity's composition; `AnalyzerApp` composable switches `HomeScreen`/`SettingsScreen`
by that state; `BackHandler` on the settings screen returns to home so the system back
gesture behaves as expected.

**Rationale**:

- Exactly two screens, no arguments, no deep links, no nested back stack — Navigation's
  whole feature set would be unused; Constitution VII rejects dependencies whose needed
  subset is trivial (here: one `when`).
- The footer's "current destination" indication (FR-008) is a direct read of the same
  state object, guaranteeing UI and navigation can't diverge.

**Alternatives considered**:

- *Navigation-Compose (`androidx.navigation:navigation-compose`)*: new dependency for
  zero used features. Rejected.
- *Two activities*: heavier manifest surface, slower transitions, and theme state would
  need cross-activity plumbing. Rejected.

## R-09 · Off-main-thread execution of the reads

**Decision**: A single shared `Executors.newSingleThreadExecutor()` owned by the home
state holder; each figure read is submitted once and its result is posted back with
`Handler(Looper.getMainLooper()).post { … }` into Compose state.

**Rationale**:

- The reads are binder/syscall calls (`getMemoryInfo`, `StatFs` construction,
  `getInstalledApplications`, sticky broadcast fetch) — cheap but not main-thread work;
  FR-014 requires the layout to render without waiting for any read, so reads must be
  async from first composition.
- `java.util.concurrent` + `Handler` are platform built-ins: no new dependency, keeping
  001's explicit exclusion of a coroutines dependency intact (Constitution VII).
  Compose's transitive coroutines are not relied upon.
- One executor thread, used only while the home screen is visible and shut down when the
  holder is cleared, keeps the resource footprint minimal (Constitution IX).

**Alternatives considered**:

- *`kotlinx-coroutines-android` (explicit dependency)*: idiomatic, but the needed subset
  (fire-one-task, hop-to-main) is exactly the trivial re-implementation case Principle
  VII directs away from. Rejected.
- *Reads on the main thread*: would block first frame against `getInstalledApplications`
  on app-dense devices and violate FR-014's non-blocking layout. Rejected.

## R-10 · When reads are triggered (FR-011)

**Decision**: A `DefaultLifecycleObserver` in the home content observes `ON_RESUME`
(covers launch and background resume); in-app navigation re-entry is covered by a
launch-triggered read keyed on the destination becoming HOME (the state holder starts a
read whenever the home screen enters composition / the holder is reactivated). Each
trigger resets every figure to the placeholder state, then replaces figures
independently as each read lands (FR-014). No timers, no listeners, no receivers —
nothing runs while home is not visible.

**Rationale**: FR-011 names three moments — launch, in-app return, background resume.
`ON_RESUME` + entering-composition covers exactly those with two platform hooks and zero
background machinery (Constitution VIII–IX, SC-006).

**Alternatives considered**: `LifecycleResumeEffect` from
`lifecycle-runtime-compose` — a new artifact for a one-line observer; rejected.
Pull-to-refresh or a refresh button — explicitly out of scope ("Snapshot behavior").

## R-11 · Per-figure UI state model

**Decision**: Each figure is an independent `FigureUiState<T>`:
`Loading` (neutral placeholder, e.g. an em-dash) → `Available(value)` or
`Unavailable` (distinct indication, e.g. "Not available"), reset to `Loading` on every
new read cycle.

**Rationale**: FR-012/FR-014 make per-figure independence load-bearing — the screen
renders immediately with placeholders, and one failing figure must neither block nor
crash the others. A sealed per-figure state is the direct encoding; details live in
[data-model.md](./data-model.md).

**Alternatives considered**: one all-or-nothing `ResourceSummary?` — would show either
everything or one global error, violating FR-014's independent replacement. Rejected.

## R-12 · Human-readable, locale-aware formatting

**Decision**: Byte amounts via `android.text.format.Formatter.formatShortFileSize`
(platform, locale-aware, e.g. "1.5 GB"); counts via `NumberFormat.getInstance(locale)`
with the paired form `"%s (%s)"`; battery as a whole-number percent; charging state and
all labels as English string resources (spec assumption "Formatting").

**Rationale**: Platform classes give correct locale grouping and unit rules with zero
dependencies; the paired count format directly produces the spec's "36 (121)" shape
(FR-004, edge case "very large number of applications").

**Alternatives considered**: hand-rolled binary (GiB) formatting — locale-naive and
redundant with the platform; rejected.

## R-13 · Accessibility (FR-015)

**Decision**: Every figure cell exposes merged semantics of label + value (or
placeholder / "Not available" text) so TalkBack announces name-and-value pairs; the
unavailability string is a distinct announcement, never a fake value. All text uses
`sp`-scaled Material typography; figure rows use intrinsic/wrapping heights (no fixed
heights, no clipping at font scale 2.0); footer buttons and radio options carry
state-descriptive semantics (`Role.Button`, selectable semantics for radio).

**Rationale**: Compose merges `semantics` into accessibility nodes automatically;
string-resource-driven labels keep the name-and-value contract testable via unit tests
on the formatted description. Largest-font-scale robustness (edge case, FR-015) is
layout discipline: wrapping containers, no `height(dp)`, scrollable screen body.

**Alternatives considered**: none — platform screen-reader support is the only path;
no dependency exists to add.

## R-14 · Testing strategy (Constitution IV/V)

**Decision**: JVM-only JUnit 4 (the 001 setup), with all platform reads behind the
reader interfaces of [contracts/device-readers.md](./contracts/device-readers.md):

- Derivation/validation logic (allocated, used, non-negative, sum ≤ total, nonsense
  readings → unavailable) — pure unit tests over the domain models.
- System-app classification — pure function over `ApplicationInfo.flags` bits (the flag
  constants are compile-time inlined, safe in JVM tests without Robolectric).
- Count formatting ("36 (121)", "0 (121)"), file-size formatting, battery percent math —
  pure tests with injected locale.
- Theme resolution (preference × system-dark → effective scheme) and persistence
  (in-memory fake store) — pure tests.
- Figure state transitions (Loading → Available/Unavailable, reset on re-read) — pure
  tests on the state holder with fake readers.

No Robolectric, no instrumented tests, no emulator-in-the-loop gates (VM has no KVM;
Constitution IV accepts JVM + manual emulator/device verification). UI/DXE checks run
manually per [quickstart.md](./quickstart.md).

**Alternatives considered**: *Robolectric* — a large dependency for what the interface
seam already makes testable; rejected. *Compose UI tests (`ui-test-junit4`)* —
instrumented or Robolectric-dependent; out of scope for this feature's local gates.

## R-15 · Manual verification data sources (emulator/device)

**Decision**: quickstart.md cross-checks displayed figures against shell-reported
truth: `adb shell dumpsys battery` (level/status), `adb shell cat /proc/meminfo`
(MemAvailable/MemTotal), `adb shell df /data` (storage), `adb shell pm list packages |
wc -l` and `adb shell pm list packages -3 | wc -l` (total and non-system counts —
`-3` matches our updated-system-apps-count-as-system rule), `adb shell cmd uimode night
yes|no` (dark mode switching), `adb shell settings put system font_scale 2.0` (largest
font scale).

**Rationale**: Every acceptance scenario needs an observable expected value; the shell
commands are the device-reported ground truth the spec's tests compare against.

## R-16 · Dependency & permission verdict (Constitution VII/VIII)

**Decision**: No new libraries, plugins, or test dependencies. One new manifest
permission: `QUERY_ALL_PACKAGES` (R-01). The manifest's existing receiver-permission
removal directives stay; the app still declares no runtime permissions, no internet, no
receivers, no services.

**Rationale**: Everything else in this feature is platform API surface
(`ActivityManager`, `StatFs`, sticky battery intent, `PackageManager`,
`SharedPreferences`, `Runtime`, `Handler`/`Executor`) — the "newest stable stack, minimal
dependencies" balance lands entirely on the platform and the existing Compose stack.

---

## Sources

- Package visibility filtering & QUERY_ALL_PACKAGES — developer.android.com/training/package-visibility; Google Play policy page (restriction noted, N/A for F-Droid target).
- `ApplicationInfo` flags (`FLAG_SYSTEM` retained by updated system apps) — developer.android.com/reference/android/content/pm/ApplicationInfo; Stack Overflow canonical threads (8784505, 12526184).
- `ActivityManager.MemoryInfo` (`availMem`, `totalMem`) — developer.android.com/reference/android/app/ActivityManager.MemoryInfo.
- `StatFs` + `Environment.getDataDirectory()` storage pattern — developer.android.com/reference/android/os/StatFs.
- One-shot battery via `registerReceiver(null, IntentFilter(ACTION_BATTERY_CHANGED))` — developer.android.com/training/monitoring-device-state/battery-monitoring.
- Feature 001 artifacts: [../001-android-app-skeleton/plan.md](../001-android-app-skeleton/plan.md) (pinned toolchain AGP 9.4.1 / Kotlin 2.4.20 / Gradle 9.8.0 / Compose BOM 2026.09.00), verify script & CI conventions.
