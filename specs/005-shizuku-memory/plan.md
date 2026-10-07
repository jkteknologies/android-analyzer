# Implementation Plan: Shizuku-Sourced Per-App Memory Information

**Branch**: `005-shizuku-memory` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-shizuku-memory/spec.md`

## Summary

Fill the Details screen's per-app `memoryBytes` slot — reserved as not-available by 004 —
with real figures read through Shizuku, the user-chosen privileged-API helper (spec
FR-001/FR-003). The integration adds **one dependency family** (`dev.rikka.shizuku:api` +
`:provider` 13.1.5, MIT — the only sanctioned client path, R-01) and **one manifest
addition** (the `ShizukuProvider` declaration the library requires; no new permission, no
`<queries>` — 002's `QUERY_ALL_PACKAGES` already covers Shizuku's package, R-05).
Per-app memory is read through a **UserService**: a tiny class of ours that Shizuku runs
inside its server process with shell identity, where the *public* SDK
`ActivityManager.getRunningAppProcesses()` + `getProcessMemoryInfo()` legitimately see all
processes (R-02) — zero vendored hidden-API stubs, zero dumpsys parsing. The figure is
the summed PSS of the app's processes (`totalPss`), zero for installed-but-not-running
apps, not-available when access is absent or the read fails (R-06). The Details screen
gains one state-driven guidance row — Not installed / Outdated / Not running / Awaiting
authorization / Authorized — with the matching action (open Shizuku, or request
authorization through Shizuku's own dialog; no credential is ever stored, FR-005), and
the state holder re-reads by itself when authorization lands via Shizuku's binder and
permission listeners (R-04/R-08). Everything else — inventory, marks, filter, storage
figures, Home, Settings, footer — is untouched (FR-012).

## Technical Context

**Language/Version**: Kotlin 2.4.20 (AGP 9.4.1 built-in Kotlin support; JVM target 21);
AIDL enabled (`buildFeatures.aidl = true`) for the UserService interface (R-02)

**Primary Dependencies**: existing pinned set (Compose BOM `2026.09.00`, activity-compose
1.13.0, lifecycle-runtime-ktx 2.11.0, JUnit 4.13.2) **plus** `dev.rikka.shizuku:api:13.1.5`
and `dev.rikka.shizuku:provider:13.1.5` (MIT, R-01) — the single justified addition this
feature makes.

**Storage**: none. The analyzer persists nothing new; Shizuku's server remembers the
authorization, and the analyzer only observes it (FR-005, spec assumption).

**Testing**: JUnit 4 JVM unit tests through the fakes seam
([contracts/shizuku-memory.md](./contracts/shizuku-memory.md)) — domain aggregation/merge
rules and the extended Details state holder (change-source, authorization, coupling);
AGP lint with `abortOnError = true`. Entry point: `./scripts/verify.sh` (feature 001).
Shizuku-runtime behavior is the quickstart manual matrix's job (Constitution IV, R-10).

**Target Platform**: Android 15+ (`minSdk 35`, `targetSdk 35`, `compileSdk 37`),
single-activity Compose app. Shizuku-side requirement: server v13+ (R-03); current
Shizuku app v13.6.0 satisfies it.

**Performance Goals**: full Details refresh incl. per-app memory settles within the SC-005
budget (10 s for 200+ apps); one batched privileged read per pass, no per-app binder
calls (R-07); UI thread never blocks (binder transacts and the UserService bind wait live
on the existing background executor, R-04).

**Constraints**: offline (no network); memory figures only while Shizuku is installed,
running, and authorized — everything else must work without (FR-006); no credential or
token storage (FR-005); read-only privileged use (FR-010); no new analyzer permission
(FR-011, SC-006); 8 GB VM build environment (root `gradle.properties` 2 g heap —
pre-existing).

**Scale/Scope**: 3 screens (one touched), single-user local app. Touched surfaces:
`MainActivity.kt` (wiring), `ui/details/*` (state holder + guidance row), new
`domain/ShizukuMemory.kt`, new `data/shizuku/*` (UserService AIDL + impl + access
plumbing), `data/DeviceReaders.kt` (+4 seams), `data/AndroidDeviceReaders.kt`
(memory merge into the inventory read), `strings.xml`, `AndroidManifest.xml` (+1
provider), `app/build.gradle.kts` + `gradle/libs.versions.toml` (+2 library entries,
AIDL on), +2 test files / 1 extended (see Project Structure).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Verdict | Evidence |
|---|-----------|---------|----------|
| I | Spec-driven development | PASS | [spec.md](./spec.md) via `$speckit-specify`; `$speckit-clarify` ran with zero open questions (all taxonomy categories Clear); this plan precedes any code. |
| II | Feature branch workflow | PASS | Work on `feature/005-shizuku` (checked out, created from `master` after 004); no direct-to-`master` commits. |
| III | Host-only remote push | PASS | VM never pushes; plan and tasks contain no push steps. |
| IV | Isolated VM development | PASS | All automated verification is JVM unit tests + lint in the VM; every Shizuku-touching behavior sits behind fakeable seams (R-08/R-10), so no test needs the library. The state flow, figures-vs-grant coupling, and revoke/re-grant cycles are verified by the quickstart manual matrix on an emulator/device with `adb` (Shizuku installs and starts there via documented commands, quickstart §0). |
| V | Local quality gates | PASS | `./scripts/verify.sh` (clean build + unit tests + lint) gates every presented change (quickstart §1). |
| VI | Modern Android-only | PASS | `minSdk 35` unchanged; the UserService calls public SDK APIs only (R-02); Shizuku itself supports the whole window. |
| VII | Modern stack, minimal dependencies | PASS | **One justified dependency family**: `dev.rikka.shizuku:api` + `:provider` 13.1.5 (MIT, R-01) — the user explicitly requested Shizuku, and the library *is* its client protocol; hand-rolling the binder/permission/provider protocol would be a larger, less-auditable re-implementation of the same library. The alternative to it (vendored `IActivityManager` stubs, ~650 lines of AOSP-derived hidden API with version-fragile transaction codes) was rejected in favor of a UserService that needs **no** hidden API (R-02). No `kotlin-parcelize` (hand-written `CREATOR`, 10 lines). |
| VIII | F-Droid-first distribution | PASS | **Permission surface unchanged** (SC-006): no new `<uses-permission>`; the manifest gains only the library-required `ShizukuProvider` declaration (a component, not a permission request — R-05). No `<queries>` needed: 002's `QUERY_ALL_PACKAGES` already makes Shizuku's package visible (R-05). Client library is MIT; Shizuku itself is FOSS (Apache-2.0, distributed via Google Play,
GitHub, and the IzzyOnDroid repo — not the default F-Droid repo). No network, tracking, or proprietary components. |
| IX | Efficiency and security | PASS | One batched privileged read per refresh on the existing background executor — no per-app binder calls (R-07); no background work, polling, or receivers (listeners registered only while the Details holder lives and removed on dispose, R-04/R-08); privileged access used strictly read-only (FR-010); nothing sensitive persisted (FR-005); authorization happens only in Shizuku's own dialog (FR-005). |

**Gate result: PASS — no violations. Phase 0 may proceed.** (Re-check after Phase 1
recorded at the end of research.md §"Post-design Constitution re-check".)

## Project Structure

### Documentation (this feature)

```text
specs/005-shizuku-memory/
├── plan.md                    # This file ($speckit-plan command output)
├── research.md                # Phase 0 output ($speckit-plan command)
├── data-model.md              # Phase 1 output ($speckit-plan command)
├── quickstart.md              # Phase 1 output ($speckit-plan command)
├── contracts/
│   ├── shizuku-memory.md      # Phase 1 output: the four seams + UserService AIDL + merge rules
│   └── details-guidance.md    # Phase 1 output: state-driven guidance row + actions
└── tasks.md                   # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/aidl/com/jkteknologies/androidanalyzer/data/shizuku/
├── IAppMemoryService.aidl                 # NEW: UserService interface — List<AppProcessMemory>
└── AppProcessMemory.aidl                  # NEW: parcelable declaration
app/src/main/java/com/jkteknologies/androidanalyzer/
├── MainActivity.kt                        # Wire the 4 new seams into DetailsStateHolder
├── ui/
│   └── details/
│       ├── DetailsScreen.kt               # +ShizukuGuidanceRow (state text + open/allow action)
│       └── DetailsStateHolder.kt          # +shizukuAccessState posted per pass, requestAuthorization(),
│                                          #   change-source subscription + auto re-read on grant
├── domain/
│   └── ShizukuMemory.kt                   # NEW: ShizukuAccessState, ProcessMemory,
│                                          #   aggregateProcessMemory, memoryBytesFor
└── data/
    ├── DeviceReaders.kt                   # +AppMemoryReader, ShizukuAccessStatus, ShizukuAuthorizer,
    │                                      #   ShizukuChangeSource seams
    ├── AndroidDeviceReaders.kt            # installedAppReader merges memoryBytes (R-06) via the seams
    └── shizuku/
        ├── AppProcessMemory.kt            # NEW: Parcelable (hand-written CREATOR) + domain mapping
        ├── AppMemoryServiceImpl.kt        # NEW: runs INSIDE Shizuku's server process — public
        │                                  #   getRunningAppProcesses + getProcessMemoryInfo (R-02)
        ├── ShizukuMemorySource.kt         # NEW: AppMemoryReader impl — lazy bindUserService,
        │                                  #   cached connection, rebind after death (R-07)
        └── ShizukuAccess.kt               # NEW: status/authorizer/change-source impls over the
                                           #   Shizuku static API + SHIZUKU_PACKAGE const (R-03/R-04)

app/src/main/res/values/strings.xml        # +5 guidance strings + 2 button labels (R-09)
app/src/main/AndroidManifest.xml           # +ShizukuProvider declaration (R-05)
app/build.gradle.kts                       # +2 dependencies, buildFeatures.aidl = true
gradle/libs.versions.toml                  # +shizukuApi/shizukuProvider = 13.1.5 (R-01)
app/src/test/java/com/jkteknologies/androidanalyzer/
├── domain/ShizukuMemoryTest.kt            # NEW: aggregation + memoryBytesFor rules (V-S1/V-S2)
└── ui/details/DetailsStateHolderTest.kt  # EXTENDED: state posting, grant coupling, change-source
                                           #   auto re-read, sticky-no-refresh, request delegation
```

**Structure Decision**: the existing single-module feature-package layout is kept; the
one new package is `data/shizuku/`, collecting everything that imports
`rikka.shizuku.*` into one directory (the app's first third-party platform seam — its
isolation is deliberate, Constitution VII auditability). The AIDL parcelable/interface
mirror that package. No new modules, no new abstractions beyond the four fun-interface
seams that join the app's existing reader family.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — table intentionally empty.
