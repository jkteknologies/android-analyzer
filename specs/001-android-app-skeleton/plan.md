# Implementation Plan: Launchable Android Skeleton with One-Command Verification and CI

**Branch**: `001-android-app-skeleton` (work branch: `feature/001-init`) | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-android-app-skeleton/spec.md`

## Summary

Deliver a minimal, launchable Android app ("Android Analyzer") that builds from a clean checkout with a single standard command, installs and launches on Android 15 (API 35)+ showing a placeholder screen, and performs zero networking, permissions, or background work. The skeleton ships exactly one documented local verification entry point (build + unit tests + linters, consolidated pass/fail — FR-006) and a GitHub Actions pipeline (US3) that reproduces the same checks on every push/PR on GitHub-hosted runners with no devices, emulators, or credentials. Technical approach per Constitution VI–VII: Kotlin + Jetpack Compose (Material 3), single Activity, Gradle Kotlin DSL with a version catalog, JUnit-only device-free tests, platform built-in Lint, and the absolute minimum dependency set with each dependency justified.

## Technical Context

**Language/Version**: Kotlin (latest stable 2.x) on JDK 21 LTS (Temurin); Gradle (latest stable) via wrapper, Android Gradle Plugin (latest stable). Exact pinned versions and their resolution rationale are recorded in [research.md](./research.md).

**Primary Dependencies** (each justified per Constitution VII — minimal set, rationale in research.md):
- `androidx.activity:activity-compose` + Compose UI/Foundation/Material3 (Compose BOM) + `androidx.lifecycle` — required to render the placeholder UI on the modern stack Constitution VI mandates; a single-Activity Compose screen has no lighter alternative.
- `org.jetbrains.kotlin.plugin.compose` `2.4.20` (Compose compiler Gradle plugin) — *[amended 2026-10-04, convergence T022]* required to compile `@Composable` code with Kotlin 2.0+ under AGP 9.x built-in Kotlin (R-01/R-04); version-matched to the Kotlin compiler AGP 9.4.1 embeds. Build-time only: no runtime code, no APK payload, no transitive libraries. The exclusion of a standalone `org.jetbrains.kotlin.android` plugin (R-01, task T003's "AGP only") is unaffected — that is a different plugin and stays excluded.
- JUnit 4 (AGP default unit-test engine) — required by US2/FR-004 device-free tests; minimal choice, already on the unit-test classpath.
- Android Lint (built into AGP) — the linter for FR-005; no third-party linter needed.
- Explicitly excluded: networking, serialization, DI, image loading, Coroutines, appcompat/support libraries (FR-003 forbids backports), any analytics/ads/tracking (Constitution VIII).

**Storage**: N/A — the skeleton involves no persistent data (spec "Key Entities": not applicable).

**Testing**: JUnit unit tests on the local JVM via the Gradle `test` source set — no device or emulator (FR-004, Constitution IV). Instrumented tests are out of scope (spec assumption). Test or lint failures exit non-zero and name the failing test / file+rule (US2, FR-005).

**Target Platform**: Android 15 (API 35) and newer. `minSdk = targetSdk = 35`; no support libraries, no compat shims (FR-003, Constitution VI). *[Amended 2026-10-04, convergence T023: `compileSdk = 37`, not 35 — the pinned 2026 library set (Compose BOM `2026.09.00`, lifecycle `2.11.0`, activity `1.13.0`) requires compiling against SDK 37. This changes the build-time platform only; the user-facing API-35 contract — `minSdk = targetSdk = 35`, installs and runs on Android 15+ — is unchanged (FR-003 intact). Clean-machine prerequisite is therefore `platforms;android-37.0`; see the research.md R-12 amendment, data-model.md S-1, and README.md.]*

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
gradle.properties                   # Daemon heap 2g — prevents D8/R8 OOM on clean builds (T026)
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

---

## Post-Implementation Amendments (Convergence, 2026-10-04)

Implementation-time decisions that deviate from the original plan text, recorded here with rationale per the Constitution Development Workflow (steps 3/5) and convergence tasks T022–T026. The Constitution Check above is unaffected — all nine principles still pass with these amendments in force.

1. **Compose compiler Gradle plugin (T022, Constitution VII)** — `org.jetbrains.kotlin.plugin.compose` `2.4.20` is applied (root + `:app`) and pinned in `gradle/libs.versions.toml`. AGP 9.x's built-in Kotlin (R-01) removes the standalone `org.jetbrains.kotlin.android` plugin but still requires the Compose compiler plugin whenever Compose is enabled with Kotlin 2.0+; its version must match the Kotlin compiler AGP embeds (`2.4.20`, R-04). Build-time only — no runtime code, no APK payload, no new transitive libraries. Also recorded inline in Technical Context > Primary Dependencies.
2. **`compileSdk = 37` (T023)** — the pinned 2026 library set (Compose BOM `2026.09.00`, lifecycle `2.11.0`, activity `1.13.0`) requires compiling against SDK 37; `minSdk = targetSdk = 35` are unchanged, so FR-003's user-facing API-35 contract is intact. Recorded inline in Target Platform above; research.md R-12 carries the amendment, and data-model.md S-1 plus quickstart.md prerequisites were aligned to `platforms;android-37.0` (README.md and the `scripts/verify.sh` precheck message already stated it). CI relies on the GitHub-hosted runner's preinstalled SDK (R-13) providing the compile platform; if a runner image ever lags behind SDK 37, add an explicit sdkmanager step to `ci.yml`.
3. **`themes.xml` window-theme parent (T024)** — the Activity theme parents from the platform `android:Theme.Material.Light.NoActionBar`, not a literal `Theme.Material3` style: that parent lives in `com.google.android.material` (Material Components), which the minimal-dependency set excludes (Constitution VII). Material 3 theming happens in Compose (`MaterialTheme` in `MainActivity`); the XML theme is only the pre-Compose window bridge. Supersedes the parenthetical "(`Theme.Material3`)" in task T009.
4. **Manifest permission merge-strip (T025)** — `app/src/main/AndroidManifest.xml` carries two `tools:node="remove"` directives (`<uses-permission>` and `<permission>` for `${applicationId}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) that strip the permission `androidx.core` (transitive via activity-compose) injects through manifest merging. This is the sanctioned mechanism upholding data-model.md S-2's zero-permission contract: the merged APK requests nothing (verified — `aapt2 dump permissions` on `app-debug.apk` is empty, task T018). S-2's "zero `<uses-permission>` elements" is understood as zero *requested* permissions in the merged result.
5. **Root `gradle.properties` (T026)** — `org.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=512m`. The Gradle default 512m daemon heap triggers a D8/R8 OutOfMemoryError while dexing the Compose debug APK on clean builds (8 GB dev VM); 2g is the Android-recommended baseline. Build infrastructure only — no app behavior. Added to the Project Structure inventory above.
