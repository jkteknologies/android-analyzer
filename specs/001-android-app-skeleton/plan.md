# Implementation Plan: Launchable Android Skeleton with One-Command Verification and CI

**Branch**: `001-android-app-skeleton` (work branch: `feature/001-init`) | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-android-app-skeleton/spec.md`

## Summary

Deliver a minimal, launchable Android app ("Android Analyzer") that builds from a clean checkout with a single standard command, installs and launches on Android 15 (API 35)+ showing a placeholder screen, and performs zero networking, permissions, or background work. The skeleton ships exactly one documented local verification entry point (build + unit tests + linters, consolidated pass/fail — FR-006) and a GitHub Actions pipeline (US3) that reproduces the same checks on every push/PR on GitHub-hosted runners with no devices, emulators, or credentials. Technical approach per Constitution VI–VII: Kotlin + Jetpack Compose (Material 3), single Activity, Gradle Kotlin DSL with a version catalog, JUnit-only device-free tests, platform built-in Lint, and the absolute minimum dependency set with each dependency justified.

## Technical Context

**Language/Version**: Kotlin (latest stable 2.x) on JDK 21 LTS (Temurin); Gradle (latest stable) via wrapper, Android Gradle Plugin (latest stable). Exact pinned versions and their resolution rationale are recorded in [research.md](./research.md).

**Primary Dependencies** (each justified per Constitution VII — minimal set, rationale in research.md):
- `androidx.activity:activity-compose` + Compose UI/Foundation/Material3 (Compose BOM) + `androidx.lifecycle` — required to render the placeholder UI on the modern stack Constitution VI mandates; a single-Activity Compose screen has no lighter alternative.
- JUnit 4 (AGP default unit-test engine) — required by US2/FR-004 device-free tests; minimal choice, already on the unit-test classpath.
- Android Lint (built into AGP) — the linter for FR-005; no third-party linter needed.
- Explicitly excluded: networking, serialization, DI, image loading, Coroutines, appcompat/support libraries (FR-003 forbids backports), any analytics/ads/tracking (Constitution VIII).

**Storage**: N/A — the skeleton involves no persistent data (spec "Key Entities": not applicable).

**Testing**: JUnit unit tests on the local JVM via the Gradle `test` source set — no device or emulator (FR-004, Constitution IV). Instrumented tests are out of scope (spec assumption). Test or lint failures exit non-zero and name the failing test / file+rule (US2, FR-005).

**Target Platform**: Android 15 (API 35) and newer. `minSdk = targetSdk = compileSdk = 35`; no support libraries, no compat shims (FR-003, Constitution VI).

**Project Type**: mobile-app — a single Gradle module `app` (no multi-module split: one screen, no shared logic to modularize).

**Performance Goals**: Clean build ≤ 5 min (SC-001); local verify ≤ 10 min (SC-002); cold-cache CI ≤ 15 min (SC-003); cold launch shows placeholder ≤ 5 s on an API 35 emulator (SC-005).

**Constraints**: Zero manifest permissions (FR-009); zero network/background work, fully offline app (FR-010, SC-006); verification runs entirely on the isolated dev VM with no device or external service (Constitution IV); only the host operator pushes to the remote — the VM never pushes (Constitution III); dependencies must be freely licensed (Constitution VIII).

**Scale/Scope**: One module, one screen, a handful of unit tests, one CI workflow, one verification script — roughly 10–20 source files. Analyzer functionality, theming, localization, and release signing are out of scope.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Spec-Driven Development | ✅ PASS | Every requirement traces to `spec.md`; the FR-006 entry-point name is fixed here and in research.md §6. |
| II. Feature Branches, `master` Integration | ✅ PASS | Work proceeds on `feature/001-init`; host operator merges to `master`. |
| III. Host-Only Remote Push (NON-NEGOTIABLE) | ✅ PASS | This feature only writes repo files (including the CI workflow definition); nothing pushes from the VM. |
| IV. Isolated VM Development | ✅ PASS | Verification = build + JVM unit tests + lint; no device, emulator, or external service required (US2, FR-004). |
| V. Local Quality Gates (NON-NEGOTIABLE) | ✅ PASS | US2 mandates exactly one entry point running tests + linters before delivery; CI re-verifies on the host side (US3). |
| VI. Modern Android-Only | ✅ PASS | min API 35 pinned by clarification; no support libraries, no compat shims. |
| VII. Modern Stack, Minimal Dependencies | ✅ PASS | Compose/M3/AGP/Gradle/JUnit only, each justified above and in research.md; prefer platform built-ins (Lint, Gradle test runner). |
| VIII. F-Droid-First | ✅ PASS | All toolchain/library deps are open source; zero permissions; no ads/tracking; AGPL-3.0 already in repo. |
| IX. Efficiency and Security | ✅ PASS | No background work, no network, no storage — nothing in this feature can drain battery or leak data. |

**Gate result: no violations. Re-checked again after Phase 1 design (see end of this file).**

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
.github/
└── workflows/
    └── ci.yml                      # US3: build + test + lint on every push/PR (ubuntu-latest)

app/
├── build.gradle.kts                # SDK 35 pins, Kotlin options, lint options, dependency list
├── proguard-rules.pro              # Placeholder kept for AGP defaults (release config out of scope)
└── src/
    ├── main/
    │   ├── AndroidManifest.xml     # Application declared with zero permissions (FR-009)
    │   ├── java/com/jkteknologies/androidanalyzer/
    │   │   ├── MainActivity.kt     # Single Activity hosting the Compose placeholder screen
    │   │   └── ui/
    │   │       └── PlaceholderScreen.kt    # Composable rendering the app name (FR-002)
    │   └── res/
    │       ├── values/
    │       │   ├── strings.xml     # app_name = "Android Analyzer" (FR-002)
    │       │   └── themes.xml      # Minimal Material 3 theme bridge for the Activity
    │       └── mipmap-anydpi-v26/  # AGP-generated adaptive launcher icon defaults
    └── test/
        └── java/com/jkteknologies/androidanalyzer/
            └── PlaceholderScreenTest.kt    # Device-free JUnit tests (US2, FR-004)

build.gradle.kts                    # Root project: plugin aliases from the version catalog
settings.gradle.kts                 # Repositories + `:app` module registration
gradle/
├── libs.versions.toml              # Version catalog: single source of truth for pinned versions
└── wrapper/
    ├── gradle-wrapper.jar
    ├── gradle-wrapper.properties   # Pins the Gradle distribution (part of FR-001 single command)
    └── (gradlew, gradlew.bat at repo root) # Standard wrapper launchers

gradlew / gradlew.bat               # THE standard single build command (FR-001)
scripts/
└── verify.sh                       # FR-006: THE one documented local verification entry point

README.md                           # Updated: prerequisites, build command, verify command, CI badge
```

**Structure Decision**: Single-module Android Gradle project (`app`) with standard `src/main` + `src/test` source sets — the smallest layout satisfying US1/US2 while leaving room for future feature modules. The canonical build command is the Gradle wrapper (`./gradlew build`), while the FR-006 verification entry point is `scripts/verify.sh`, which performs a prerequisite precheck (JAVA_HOME/Android SDK) then runs the fixed check set — `build`, `testDebugUnitTest`, `lint` — with consolidated pass/fail and named failures. CI (`.github/workflows/ci.yml`) mirrors the same check set on ubuntu-latest with a preinstalled Android SDK, a Temurin JDK, Gradle caching, and no emulator/devices/credentials.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — table intentionally empty.

---

## Post-Phase 1 Constitution Re-Check

*Re-evaluated after Phase 1 design artifacts (research.md, data-model.md, contracts/, quickstart.md) were produced.*

All nine principles remain ✅ PASS. The design introduced no new dependencies, no permissions, no devices/emulators, no VM-side pushes, and no multi-module or abstraction-layer complexity. Verification remains one entry point (`scripts/verify.sh`) and CI remains a single workflow with no device or credential needs. **Gate still passes; Complexity Tracking stays empty.**
