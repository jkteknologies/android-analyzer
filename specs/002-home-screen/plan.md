# Implementation Plan: Home Screen

**Branch**: `002-home-screen` (work branch: `feature/002-home-screen`) | **Date**: 2026-10-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-home-screen/spec.md`

## Summary

Turn the 001 placeholder skeleton into the analyzer's first real screens: a home screen
showing a one-shot device overview — memory (available/allocated), internal storage
(free/used), battery (level/charging state), processor core count, and installed-app
counts ("non-system (total including system)") — plus a persistent two-button footer
navigating between home and settings, and a single setting (theme: Light / Dark /
System default) that defaults to following the system. Every figure renders instantly
with a neutral placeholder, fills in independently as its read completes, and shows a
distinct "Not available" indication when its read fails — the screen never blocks and
never invents values. Technical approach (per [research.md](./research.md)): **zero new
dependencies** and **exactly one new permission** (`QUERY_ALL_PACKAGES`, strictly
required for the total app count and justified per FR-013 / Constitution VIII); all
device reads are one-shot platform APIs behind fakeable interfaces
([contracts/device-readers.md](./contracts/device-readers.md)); navigation is hand-rolled
two-destination state; theming is Material 3 `light/darkColorScheme` +
`isSystemInDarkTheme()` with the preference persisted in `SharedPreferences`.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (built-in via AGP 9.4.1), JDK 21 target, Gradle 9.8.0
wrapper — inherited unchanged from feature 001 (see
[../001-android-app-skeleton/plan.md](../001-android-app-skeleton/plan.md)).

**Primary Dependencies**: **No additions.** Existing set (Compose BOM `2026.09.00` — ui,
foundation, material3; activity-compose 1.13.0; lifecycle-runtime-ktx 2.11.0; JUnit 4;
AGP lint) covers the feature. New capability comes from platform APIs:
`ActivityManager.MemoryInfo`, `StatFs`, sticky `ACTION_BATTERY_CHANGED`,
`PackageManager.getInstalledApplications`, `Runtime.availableProcessors()`,
`SharedPreferences`, `java.util.concurrent` executor + main `Handler`
(Constitution VII; verdict in [research.md R-16](./research.md)).

**Storage**: `SharedPreferences` (platform built-in) holding exactly one string — the
theme preference (R-07). Resource figures and app counts are intentionally not stored
(snapshot-only, spec assumption "Snapshot behavior").

**Testing**: JUnit 4 on the local JVM via the existing `test` source set — domain
validation rules V-1..V-10, app classification, formatting, theme resolution, and
figure-state transitions, all through fake readers (R-14). No Robolectric, no
instrumented tests; UI and accessibility validated manually per
[quickstart.md](./quickstart.md) (Constitution IV — no KVM-reliable emulator).

**Target Platform**: Android 15+ — `minSdk = targetSdk = 35`, `compileSdk = 37`
(unchanged from 001; Constitution VI).

**Project Type**: mobile-app, single Gradle module `app`.

**Performance Goals**: all figures presented ≤ 2 s after launch with the layout rendering
immediately (SC-001, FR-014); theme selection visible < 1 s (SC-005); reads complete in
one background-thread pass over five platform calls (R-09).

**Constraints**: reads only while the home screen is visible — no background work, no
polling, no network (FR-011, SC-006, Constitution VIII–IX); at most the single
`QUERY_ALL_PACKAGES` permission added (FR-013, SC-006); figures non-negative and
pair-sums ≤ totals (FR-002); English strings only; no crash on unreadable figures
(FR-012); screen-reader semantics + largest-font-scale layout intact (FR-015).

**Scale/Scope**: two screens, ~10–15 new Kotlin files plus string resources and tests;
no new Gradle modules, no manifest components beyond the existing activity.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Spec-Driven Development (NON-NEGOTIABLE) | ✅ PASS | Every FR-001..FR-015 and US1–US4 maps to artifacts here; permission decision FR-013 anticipated by the spec and resolved in research.md R-01. |
| II. Feature Branch Workflow | ✅ PASS | All work on `feature/002-home-screen`; `master` untouched; merge only after gates pass. |
| III. Host-Only Remote Push (NON-NEGOTIABLE) | ✅ PASS | Plan writes only repo files; nothing here pushes. |
| IV. Isolated VM Development | ✅ PASS | Automated gates are build + JVM unit tests + lint (same `scripts/verify.sh` entry point as 001); no device/instrumented dependency; manual checks documented as optional device/emulator steps. |
| V. Local Quality Gates (NON-NEGOTIABLE) | ✅ PASS | `./scripts/verify.sh` remains the single mandated pre-delivery gate; new unit tests added to it via the standard `test` source set. |
| VI. Modern Android-Only | ✅ PASS | minSdk 35 unchanged; every chosen API (package visibility, sticky battery read, StatFs bytes methods, M3 dynamic-free schemes) is modern-first with no compat shims. |
| VII. Modern Stack, Minimal Dependencies | ✅ PASS | **Zero new dependencies**: theming, navigation, async, and persistence solved with the existing Compose stack and platform built-ins; each alternative-that-would-add-a-dep documented and rejected in research.md R-06..R-09. |
| VIII. F-Droid-First | ✅ PASS w/ justification | Exactly one permission added: `QUERY_ALL_PACKAGES` — strictly required to enumerate **all** applications for FR-004/SC-003 (no `<queries>` subset can yield the total), install-time `normal` permission, maps 1:1 to the on-screen app-count feature. Play's restriction of this permission is explicitly N/A (F-Droid target). No network, ads, tracking, proprietary deps (SC-006). |
| IX. Efficiency and Security | ✅ PASS | One-shot reads only while home is visible; single executor thread; no receivers/services/jobs added (FR-011, R-10); battery impact ≈ five short binder/syscalls per home presentation. Data at rest: one non-sensitive enum string. |

**Gate result: no violations; Principle VIII carries the single-permission justification
required by FR-013.** Re-evaluated after Phase 1 design below.

## Project Structure

### Documentation (this feature)

```text
specs/002-home-screen/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/
│   ├── device-readers.md  # internal Kotlin seam: one-shot reader interfaces
│   └── ui-contracts.md    # screens, footer, theme & accessibility clauses
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/
├── AndroidManifest.xml                      # + <uses-permission QUERY_ALL_PACKAGES> (R-01); no new components
├── java/com/jkteknologies/androidanalyzer/
│   ├── MainActivity.kt                      # hosts AppUiState: destination + themePreference (R-06..R-08)
│   ├── data/
│   │   ├── DeviceReaders.kt                 # interface family (contracts/device-readers.md)
│   │   ├── AndroidDeviceReaders.kt          # platform implementations (ActivityManager, StatFs, battery, cores, PackageManager)
│   │   ├── AppClassifier.kt                 # isSystemApplication(flags) — pure (FR-005)
│   │   └── SharedPreferencesThemeStore.kt   # ThemePreferenceStore impl (R-07)
│   ├── domain/
│   │   ├── Figures.kt                       # FigureUiState + readings + validation (data-model §1–2)
│   │   ├── ApplicationInventory.kt          # counts + display format (data-model §3)
│   │   └── ThemePreference.kt               # enum + effective-theme resolution (data-model §4)
│   └── ui/
│       ├── AnalyzerApp.kt                   # shell: footer (FR-007/008) + destination switch + BackHandler
│       ├── theme/AppTheme.kt                # M3 schemes, system-follow, preference wiring (FR-006/010)
│       ├── home/HomeScreen.kt               # figure rows, placeholders, unavailable states (FR-001..014)
│       ├── home/HomeStateHolder.kt          # read orchestration: executor + Handler, FR-011 triggers (R-09/R-10)
│       ├── home/FigureFormatting.kt         # locale-aware formatting (R-12)
│       └── settings/SettingsScreen.kt       # the single theme setting (FR-009)
├── res/values/strings.xml                   # + all labels/values/unavailability strings (FR-015)
└── …                                        # (PlaceholderScreen.kt retired by AnalyzerApp)

app/src/test/java/com/jkteknologies/androidanalyzer/
├── domain/                                  # validation V-1..V-10, classification (FR-005), "N (M)" format
├── ui/home/                                 # HomeStateHolder transitions with fake readers (FR-012/014)
└── ui/theme/                                # preference resolution + persistence fallback (FR-006/009/010)

scripts/verify.sh                            # unchanged 001 entry point now also runs the new suites
```

**Structure Decision**: single-module app (as 001 — two screens and a domain seam do not
warrant modules), extended with `data/` (platform implementations), `domain/` (pure,
JVM-testable models per data-model.md), and `ui/` split by screen. The reader-interface
seam is the only allowed boundary between Android APIs and domain logic
(contracts/device-readers.md).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

None — no violations. The one added permission is a spec-anticipated requirement
(FR-013), justified under Principle VIII above and in research.md R-01.

---

## Post-Design Constitution Re-Check (after Phase 1)

Re-evaluated against the generated artifacts ([research.md](./research.md),
[data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)):

- **I** — every FR maps to a designed artifact (FR-001..015 → data-model figures,
  ui-contracts clauses H/S/T/A, quickstart scenarios M-1..M-8). ✅
- **V/IV** — validation path is `scripts/verify.sh` + manual quickstart only; nothing
  requires unavailable infrastructure. ✅
- **VII** — design adds zero dependencies; executor/handler, SharedPreferences,
  hand-rolled navigation, static M3 palettes chosen over coroutines/DataStore/
  Navigation-Compose/dynamic-color precisely to honor the minimal-dependency rule. ✅
- **VIII** — manifest delta limited to `QUERY_ALL_PACKAGES` (design does not add
  receivers, services, providers, or network code; SC-006 holds by construction). ✅
- **IX** — one-shot reads gated on home visibility (R-10); no persistent state beyond
  one enum string. ✅
- **II/III/VI** — unaffected by the design (branch discipline, no remote writes, API 35+
  surface only). ✅

**Final gate result: PASS — no violations; proceed to `/speckit-tasks`.**

---

## Implementation Deviations (T031 log, 2026-10-05)

Recorded per the final-gate mandate; none changes designed behavior:

1. **Package spelling in tasks.md paths**: the `MAIN`/`TEST` abbreviations say
   `jktechnologies`, but the actual package (feature 001, manifest, namespace)
   is `com.jktecnologies.androidanalyzer` — all new files use the real package.
2. **`ThemePreference` enum delivered with US1 (early)**: T002's contract-typed
   `ThemePreferenceStore` cannot compile without the enum, and the T016 gate
   runs before T018. The tested resolution logic (`effectiveTheme`,
   `fromPersisted`) still landed with US2 tests-first as planned.
3. **Battery action constant**: research.md R-04 cites
   `BatteryManager.ACTION_BATTERY_CHANGED`; the platform constant lives on
   `android.content.Intent` (same value). Implemented via `Intent`'s constant.
4. **`QUERY_ALL_PACKAGES` lint suppression**: AGP lint reports
   `QueryAllPackagesPermission` as an error; suppressed on the element
   (`tools:ignore`) because the permission decision is pre-justified (R-01,
   Constitution VIII) and Play policy does not apply to the F-Droid target.
5. **JVM-test seams**: `Formatter.formatShortFileSize` is an Android class that
   throws on the JVM, so `FigureFormatting` takes an injected
   `(Long) -> String` byte formatter (production wires the platform Formatter) —
   the realization of T012's "inject locale for testability". Likewise
   `HomeStateHolder` takes an injected `ExecutorService` + `ResultPoster`
   (poster injection was already prescribed by T013).
6. **`HomeStateHolder` catches `Throwable`**: honoring FR-012's no-crash
   guarantee over Kotlin's generic-exception-catch lint warning.

Final Constitution re-check (delivered code): I–IX all PASS — zero new
dependencies (`app/build.gradle.kts` untouched by this feature), exactly one
permission added with no new components, all reads one-shot and gated on home
visibility, single executor thread, work confined to `feature/002-home-screen`,
no remote push. Automated gates green; quickstart M-1..M-8 remain manual
device/emulator checks (Constitution IV split).
