# Implementation Plan: Per-App Information and Details Screen

**Branch**: `004-per-app-info` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-per-app-info/spec.md`

## Summary

Add a Details tab with the full installed-application inventory, a restyled three-tab
rectangular footer, home-screen drill-down into a pre-filtered Details list, processor
core-tier breakdown, and an automatic-refresh setting — with **zero new dependencies**
and **one manifest addition** (the `PACKAGE_USAGE_STATS` appop declaration backing the
per-app storage figures, research R-02). The app inventory reuses the 002
`QUERY_ALL_PACKAGES` grant and `isSystemApplication` classifier already shipped, so the
home counts and the details marks cannot disagree (spec assumption, FR-011..FR-012).
Per-app storage comes from the platform's `StorageStatsManager` once the user grants
usage access through the standard Settings page (FR-008); per-app memory has **no
public read path on current Android** and renders the honest "Not available" indication
by design (user-confirmed clarification 2026-10-07, R-03). The Details screen is a
`LazyColumn` under a three-way filter (All/User/System) with pull-to-refresh reusing the
003 `PullToRefreshBox` pattern; its state holder mirrors `HomeStateHolder`'s
epoch/coalescing/no-reset architecture (R-07). Auto-refresh is a lifecycle-gated
coroutine ticker in the shell — no `WorkManager`, no background work at all (FR-017,
Constitution IX). Footer buttons become square-cornered, edge-to-edge, gapless
(`RectangleShape`, no padding/spacing — FR-002), and the hand-rolled `Destination`
state gains `DETAILS` with a home→details filter hand-off owned by the shell (R-05/R-06).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (AGP 9.4.1 built-in Kotlin support; JVM target 21)

**Primary Dependencies**: Jetpack Compose via BOM `2026.09.00` — Compose UI/Foundation
1.12.x, Material3 1.4.0; activity-compose 1.13.0; lifecycle-runtime-ktx 2.11.0; JUnit
4.13.2. Feature 004 adds **none** of these and no others (R-12, Constitution VII);
`StorageStatsManager`, `AppOpsManager`, `LazyColumn`, and `Collator` are platform
framework / stdlib APIs.

**Storage**: `SharedPreferences` (existing `android_analyzer` file) gains one key,
`refresh_mode`, mirroring the theme store pattern (R-09). No other persistence.

**Testing**: JUnit 4 JVM unit tests through the established fakes seam
([contracts/device-readers.md](../002-home-screen/contracts/device-readers.md), extended
by [contracts/app-inventory.md](./contracts/app-inventory.md)); AGP lint with
`abortOnError = true`. Entry point: `./scripts/verify.sh` (feature 001).

**Target Platform**: Android 15+ (`minSdk 35`, `targetSdk 35`, `compileSdk 37`),
single-activity Compose app.

**Performance Goals**: a filter change visibly applies in under a second on a 200+ app
inventory (SC-007); inventory + figures refresh settles within the 2 s budget inherited
from 003; per-app storage stats for ~200 apps stay a single background pass (R-02).

**Constraints**: offline-capable (no network); per-app storage available only after the
user grants usage access (FR-008) — inventory, marks, filter, and counts must work
without it; auto-refresh runs only while the app is visible (FR-017, Constitution IX);
8 GB VM build environment (root `gradle.properties` 2 g heap — pre-existing).

**Scale/Scope**: 3 screens, single-user local app. Touched surfaces: `AnalyzerApp.kt`,
`MainActivity.kt`, `ui/home/*`, `ui/settings/SettingsScreen.kt`, new `ui/details/*`,
`domain` (+3 small files), `data` (readers + one store), `strings.xml`,
`AndroidManifest.xml` (+1 appop declaration), +3 test files (see Project Structure).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Verdict | Evidence |
|---|-----------|---------|----------|
| I | Spec-driven development | PASS | [spec.md](./spec.md) authored via `$speckit-specify`, clarified with the user (3 confirmed answers, 0 open), this plan precedes any code. |
| II | Feature branch workflow | PASS | Work happens on `feature/004-per-app-info` (already checked out, created from `master` after 003 merged); no direct-to-`master` commits. |
| III | Host-only remote push | PASS | VM never pushes; plan and tasks contain no push steps. |
| IV | Isolated VM development | PASS | All automated verification is JVM unit tests + lint in the VM; grant flow, core tiers, auto-refresh cadence, and footer visuals verified via documented emulator/`adb` manual checks (quickstart.md). The emulator exercises the no-usage-access and single-cluster-tier fallback paths natively (R-02/R-04). |
| V | Local quality gates | PASS | `./scripts/verify.sh` (clean build + unit tests + lint) is the required gate before any change is presented as complete (quickstart.md §1). |
| VI | Modern Android-only | PASS | `minSdk 35` unchanged; uses current stable platform APIs only (`StorageStatsManager` is API 26+). |
| VII | Modern stack, minimal dependencies | PASS | **Zero new dependencies**: everything new is platform framework or already-pinned Compose (R-12). `LazyColumn` ships in the pinned Foundation; filter controls are stock Material3. |
| VIII | F-Droid-first distribution | PASS | **One manifest addition**: the `PACKAGE_USAGE_STATS` appop permission declaration — an install-time-silent special-access declaration whose user-visible purpose is the per-app storage figures (FR-006/FR-008), justified in R-02. App inventory reuses 002's already-reviewed `QUERY_ALL_PACKAGES`. No network, tracking, or proprietary components. |
| IX | Efficiency and security | PASS | Auto-refresh is a shell-owned coroutine ticker gated on `RESUMED` — zero background work, no `WorkManager`, no receivers (FR-017, R-08); coalescing absorbs tick overlap with in-flight reads. Per-app storage stats are one batched background pass per refresh (R-02). No new data at rest beyond one preference key; no data in transit. |

**Gate result: PASS — no violations. Phase 0 may proceed.** (Re-check after Phase 1
recorded at the end of research.md §"Post-design Constitution re-check".)

## Project Structure

### Documentation (this feature)

```text
specs/004-per-app-info/
├── plan.md                      # This file ($speckit-plan command output)
├── research.md                  # Phase 0 output ($speckit-plan command)
├── data-model.md                # Phase 1 output ($speckit-plan command)
├── quickstart.md                # Phase 1 output ($speckit-plan command)
├── contracts/
│   ├── app-inventory.md         # Phase 1 output: reader/store seam (JVM-fakeable)
│   ├── navigation-and-footer.md # Phase 1 output: 3-tab shell + restyle + hand-off
│   ├── details-screen.md        # Phase 1 output: list, filter, figures, row tap
│   └── refresh-mode.md          # Phase 1 output: setting, persistence, ticker
└── tasks.md                     # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/jktecnologies/androidanalyzer/
├── MainActivity.kt                          # Wire details holder, refresh-mode store, drill-down callback
├── ui/
│   ├── AnalyzerApp.kt                       # Destination.DETAILS, gapless rectangular footer,
│   │                                        #   pending-filter hand-off, auto-refresh ticker
│   ├── home/
│   │   ├── HomeScreen.kt                    # Split app row → two clickable entries; CoreTiers figure
│   │   ├── HomeStateHolder.kt               # processor: FigureUiState<CoreTiers>; unchanged cycle logic
│   │   └── FigureFormatting.kt              # +user/system counts, CoreTiers value; applicationsValue removed
│   ├── details/
│   │   ├── DetailsScreen.kt                 # NEW: filter + LazyColumn + grant hint + row tap + pull-to-refresh
│   │   └── DetailsStateHolder.kt            # NEW: mirrors HomeStateHolder (epoch, coalesce, isRefreshing) + filter
│   └── settings/
│       └── SettingsScreen.kt                # +Automatic refresh section (4 radio rows)
├── domain/
│   ├── Figures.kt                           # UNTOUCHED
│   ├── ApplicationInventory.kt              # UNTOUCHED (home counts; systemCount derived already)
│   ├── ThemePreference.kt                   # UNTOUCHED
│   ├── InstalledApps.kt                     # NEW: InstalledApp, AppClassification, AppInventory, AppCategoryFilter
│   ├── CoreTiers.kt                         # NEW: CoreTiers + CoreTier (tiers sum == total)
│   └── RefreshMode.kt                       # NEW: enum + intervalMillis + fromPersisted
└── data/
    ├── DeviceReaders.kt                     # +InstalledAppReader, CoreTierReader, UsageAccessStatus, RefreshModeStore
    ├── AndroidDeviceReaders.kt              # +implementations (packages, StorageStatsManager, sysfs tiers, AppOps)
    ├── AppClassifier.kt                     # UNTOUCHED (shared by counts and per-app marks)
    └── SharedPreferencesRefreshModeStore.kt # NEW: same prefs file, key `refresh_mode`

app/src/main/res/values/strings.xml          # footer/details/filter/mark/grant/empty/refresh strings (R-10)
app/src/main/AndroidManifest.xml             # +PACKAGE_USAGE_STATS appop declaration (R-02)
app/src/test/java/com/jktecnologies/androidanalyzer/
├── domain/InstalledAppsTest.kt              # NEW: factories, filter, Collator ordering
├── domain/CoreTiersTest.kt                  # NEW: validation, single-tier fallback shape
├── domain/RefreshModeTest.kt                # NEW: intervalMillis, fromPersisted default
├── ui/home/FigureFormattingTest.kt          # EXTENDED: new value formats
├── ui/details/DetailsStateHolderTest.kt     # NEW: cycle/filter/epoch behaviors
└── ui/home/HomeStateHolderTest.kt           # ADJUSTED: processor figure type change only
```

**Structure Decision**: the existing single-module feature-package layout (`ui/<screen>`
owning screen + state holder, `domain` pure models, `data` platform seam) is kept; the
one new package is `ui/details`, mirroring `ui/home`. No new modules, no abstractions
beyond the reader/store fun-interfaces that already define the app's only seam
(Constitution VII).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — table intentionally empty.
