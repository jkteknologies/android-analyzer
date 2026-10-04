# Tasks: Android App Skeleton

**Input**: Design documents from `/home/dev/git/android-analyzer/specs/001-android-app-skeleton/`
**Prerequisites**: plan.md (required), research.md, data-model.md, contracts/ (ui-placeholder.md, verification.md), quickstart.md

**Tests**: The spec explicitly requires unit tests for this feature (FR-004: "device-free unit tests are mandatory"; spec Assumptions: "Unit tests are the required gate for this feature"). Test tasks are therefore **included** in User Story 2.

## Format: `[ID] [P?] [Story?] Description`
- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story the task belongs to (e.g., US1, US2, US3) — only story-scoped tasks carry this label
- Include EXACT file paths in descriptions

## User Story Priorities
- **US1 – Launchable Skeleton App (P1)**: Core deliverable; every other story exists to prove it
- **US2 – One-Command Local Verification (P2)**: Quality gate for US1
- **US3 – CI Pipeline (P2)**: Same checks as US2, enforced on every push/PR

---

## Phase 1: Setup (Project Skeleton)

- [x] T001 [P] Create `settings.gradle.kts` with plugin/repositories management: declare `google()`, `mavenCentral()`, and `gradlePluginPortal()` repositories (dependencyResolutionManagement + pluginManagement) and register the single `:app` module
- [x] T002 [P] Create `gradle/libs.versions.toml` version catalog as the single source of truth with exact pinned versions from research.md: AGP `9.4.1` (built-in Kotlin — no standalone kotlin-android plugin alias), Kotlin stdlib `2.4.20`, Compose BOM `2026.09.00`, activity-compose `1.13.0`, lifecycle-runtime-ktx `2.11.0`, JUnit `4.13.2`
- [x] T003 Create root `build.gradle.kts` declaring the plugin aliases from the version catalog with `apply false` (AGP only; no standalone Kotlin plugin per R-01)
- [x] T004 Generate the Gradle wrapper pinned to distribution `gradle-9.8.0-bin.zip`: create `gradle/wrapper/gradle-wrapper.properties` + `gradle/wrapper/gradle-wrapper.jar`, plus `gradlew` (with executable bit) and `gradlew.bat` per plan.md project structure

**Checkpoint**: Gradle project skeleton resolves; wrapper downloads Gradle 9.8.0 on first run.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user story work can begin until this phase is complete — the app module, manifest, and build configuration are prerequisites for ALL stories.

- [x] T005 Create `app/build.gradle.kts` implementing data-model.md S-1 exactly: `applicationId = "com.jkteknologies.androidanalyzer"`, `minSdk = 35`, `targetSdk = 35`, `compileSdk = 35`, `versionCode = 1`, `versionName = "0.1.0"`, `compileOptions` source/target compatibility `21` and Kotlin `jvmTarget = "21"`; enable Compose build features; compose platform from Compose BOM `2026.09.00`; dependencies ONLY: `androidx.activity:activity-compose:1.13.0`, `androidx.lifecycle:lifecycle-runtime-ktx:2.11.0`, Compose UI/foundation/material3 via BOM, `testImplementation junit:junit:4.13.2` (no networking, no DI, no Coroutines, no appcompat — Constitution VII); configure built-in AGP lint with errors fatal and warnings never failing the build (R-09; no lint.xml, no custom rules, no `abortOnError=false`)
- [x] T006 [P] Create `app/proguard-rules.pro` with placeholder header comments only (no custom rules required for this feature)
- [x] T007 Create `app/src/main/AndroidManifest.xml` implementing data-model.md S-2: **zero** `<uses-permission>` elements, exactly **one** `<activity>` — `MainActivity` in package `com.jkteknologies.androidanalyzer`, `android:exported="true"`, `android:label="@string/app_name"`, with the single launcher intent filter (`MAIN` action + `LAUNCHER` category); no other components

**Checkpoint**: Foundation ready — user story implementation can now begin in parallel.

---

## Phase 3: User Story 1 — Launchable Skeleton App (Priority: P1) 🎯 MVP

**Goal**: A clean checkout builds an installable app that launches on Android 15 (API 35)+ and displays a full-screen placeholder reading "Android Analyzer" within 5 seconds, requesting zero permissions.

**Independent Test**: Can be fully tested by building the project from a clean checkout, installing the produced application package on an emulator, launching it, and observing the placeholder screen. (Verbatim from spec.md: "building the project from a clean checkout, installing the produced application package on an emulator, launching it, and observing the placeholder screen.")

### Implementation for User Story 1

- [x] T008 [P] [US1] Create `app/src/main/res/values/strings.xml` defining `app_name` = "Android Analyzer" (sole string resource; U-1 text must come from this resource)
- [x] T009 [P] [US1] Create `app/src/main/res/values/themes.xml` with a minimal Material 3 (`Theme.Material3`) theme — no AppCompat bridge, no compat shims (U-3: pure androidx/Compose stack on API 35)
- [x] T010 [P] [US1] Create adaptive launcher icon XML under `app/src/main/res/mipmap-anydpi-v26/` (foreground/background drawables as needed; manifest icon reference resolves)
- [x] T011 [US1] Create `app/src/main/java/com/jkteknologies/androidanalyzer/ui/PlaceholderScreen.kt`: a stateless, parameter-free `@Composable` that renders a Material 3 `Surface` filling the entire screen and centers `Text(stringResource(R.string.app_name))` — satisfies U-1 (exact text "Android Analyzer"), U-5 (no network/background work), U-6 (identical output on rotation/process death because there is no state)
- [x] T012 [US1] Create `app/src/main/java/com/jkteknologies/androidanalyzer/MainActivity.kt`: the sole Activity (matching the manifest entry from T007 — exported, launcher intent), whose `onCreate` calls `setContent {}` → Material theme → `PlaceholderScreen()`; no ViewModel, no saved-instance-state handling, no side effects — satisfies U-2 (cold start ≤5 s) and U-3 (targets SDK 35 with no compat shims)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently — `./gradlew build` succeeds and the app installs, launches, and shows "Android Analyzer".

---

## Phase 4: User Story 2 — One-Command Local Verification (Priority: P2)

**Goal**: One documented entry point (`./scripts/verify.sh`) builds the project, runs all unit tests, and runs all linters on a machine with no devices and no network, reporting a clear pass/fail.

**Independent Test**: Verbatim from spec.md: "executing the documented verification command on a clean checkout and confirming it builds, runs all tests, runs all linters, and reports a clear pass/fail result."

### Tests for User Story 2 (REQUIRED per FR-004 — not optional)

- [x] T013 [P] [US2] Create `app/src/test/java/com/jkteknologies/androidanalyzer/PlaceholderScreenTest.kt`: JUnit 4 test class (JUnit `4.13.2` via `testImplementation`, plain kotlin-test/JUnit assertions) with at least one test; per data-model.md S-4, tests may verify only string-resource constants and pure helper functions — **no UI rendering assertions, no Robolectric, no instrumentation, and no `android.*` framework classes** (JVM stubs throw RuntimeException); a failing test must surface class + method name through `testDebugUnitTest`

### Implementation for User Story 2

- [x] T014 [P] [US2] Create `scripts/verify.sh` (POSIX `sh`, `set -euo pipefail`, executable bit, no arguments) implementing contracts/verification.md exactly: (1) precheck — validate `JAVA_HOME` → `$JAVA_HOME/bin/java` is JDK 21 and `ANDROID_HOME` or `ANDROID_SDK_ROOT` points to an SDK containing `platforms/` + `build-tools/`, on failure print an actionable message naming the missing variable, what to install, and a pointer to README, then exit 1; (2) run the fixed check set in exact order: `./gradlew build testDebugUnitTest lint`; (3) print consolidated output — on success exactly `PASS: build, testDebugUnitTest, lint`; on failure name the first failing check, the Gradle task, and the detail (test class/method for test failures, `file>: issue-id>` for lint errors); (4) exit 0 on success, 1 on failure; warnings never affect the exit code
- [x] T015 [P] [US2] Document verification in `README.md`: the exact command `./scripts/verify.sh`, prerequisites (JDK 21 on `JAVA_HOME`; Android SDK with `platforms;android-35`, platform-tools, AGP-selected build-tools on `ANDROID_HOME`/`ANDROID_SDK_ROOT`), expected success/failure output, and report file locations (`app/build/reports/tests/`, `app/build/reports/lint-results.txt`) — satisfies FR-011 and the verification.md contract

**Checkpoint**: At this point, User Story 2 should be fully functional and testable independently — running `./scripts/verify.sh` on a clean checkout produces a clear pass, and an intentionally broken test/lint produces a named failure.

---

## Phase 5: User Story 3 — CI Pipeline (Priority: P2)

**Goal**: Every push and every pull request runs the build, unit tests, and linters on GitHub-hosted infrastructure and reports a clear pass/fail with no manual intervention.

**Independent Test**: Verbatim from spec.md: "opening a pull request and observing that the CI pipeline builds the project, runs the test suite, runs the linters, and reports a clear pass/fail status."

### Implementation for User Story 3

- [x] T016 [US3] Create `.github/workflows/ci.yml` implementing research.md R-13: trigger on every `push` and every `pull_request`; run on `ubuntu-latest`; set up JDK 21 Temurin via `actions/setup-java`; enable dependency caching via `gradle/actions/setup-gradle`; job steps execute `./gradlew build testDebugUnitTest lint` **directly** (CI must NOT invoke `scripts/verify.sh` per contracts/verification.md); rely on the GitHub-hosted preinstalled Android SDK (no sdkmanager step); no emulator, no devices, no credentials/secrets (FR-008); keep cold-run time within SC-003 (≤15 min)

**Checkpoint**: At this point, User Story 3 should be fully functional and testable independently — a PR shows a green check with build + test + lint status, and a deliberately broken change shows a red check naming the failure.

---

## Phase N: Polish & Cross-Cutting Concerns

- [x] T017 [P] Run `./scripts/verify.sh` end-to-end on a clean checkout and confirm the consolidated `PASS: build, testDebugUnitTest, lint` output within SC-002 (≤10 min) — full US2 acceptance pass
- [x] T018 [P] Permission audit: build the debug APK and run `aapt2 dump permissions` on it; output MUST be empty (U-4, data-model.md S-2, Constitution VIII — zero unjustified permissions)
- [x] T019 [P] Offline audit: after the first warm run, confirm `./gradlew build --offline` succeeds (SC-006, FR-009, FR-010 — no network/background at build or runtime; research.md R-14)
- [x] T020 [P] Budget checks: time `./gradlew build` clean (SC-001 ≤5 min) and review CI cold-run duration for SC-003 (≤15 min); document actuals if useful
- [x] T021 Finalize `README.md` against quickstart.md: prerequisites including the one-time dev-VM installs (Temurin 21; cmdline-tools + `sdkmanager "platforms;android-35" "platform-tools"`), expected first-run Gradle downloads (R-14), CI badge + CI behavior summary, the optional manual launch check, and the Expected Outcomes table (FR-011)

---

## Dependencies & Execution Order

### Phase Dependencies
- **Setup (Phase 1)** → **Foundational (Phase 2)**: the app module (T005) and manifest (T007) require the version catalog (T002) and root build file (T003)
- **Foundational (Phase 2)** → ALL user stories: no story work starts before the build config and manifest exist
- **US1 (Phase 3)**, **US2 (Phase 4)**, **US3 (Phase 5)**: US2 and US3 verify the same fixed check set and are independent of each other; both conceptually depend on US1's code existing to build, so US1 should complete first (or at least in the same working tree) before verify/CI runs are meaningfully exercised
- **Polish (Phase N)**: requires US1+US2+US3 complete (audits run the real build/verify)

### User Story Dependencies
- US1 depends only on Foundational; it is the MVP slice
- US2 depends on Foundational (Gradle config) and is most meaningful after US1 (there must be an app to verify); its test file (T013) and script (T014) can be authored in parallel with late US1 tasks file-wise
- US3 depends on Foundational; the workflow (T016) mirrors US2's check set but runs it directly, so it does not call verify.sh and has no code dependency on US2

### Within Each User Story
- Resources before composables (T008–T010 → T011 → T012): `PlaceholderScreen` reads `R.string.app_name`, `MainActivity` renders `PlaceholderScreen`
- Test file, script, and docs within US2 are mutually independent (all `[P]`)
- US3 is a single task

### Parallel Opportunities
- Setup: T001 + T002 (different files)
- Foundational: T006 alongside T005/T007
- US1: T008 + T009 + T010 fully parallel; T011/T012 sequential after
- US2: T013 + T014 + T015 fully parallel
- Polish: T017 + T018 + T019 + T020 fully parallel (T018–T020 need the app built; T021 last)

### Parallel Example: User Story 1
```bash
# Launch all resource tasks together (different files, no interdependencies):
Task T008: "Create app/src/main/res/values/strings.xml"
Task T009: "Create app/src/main/res/values/themes.xml"
Task T010: "Create app/src/main/res/mipmap-anydpi-v26/ icons"
# Then sequentially: T011 (PlaceholderScreen) → T012 (MainActivity)
```

### Parallel Example: User Story 2
```bash
# All three US2 tasks touch different files:
Task T013: "Create app/src/test/java/com/jkteknologies/androidanalyzer/PlaceholderScreenTest.kt"
Task T014: "Create scripts/verify.sh"
Task T015: "Document verification in README.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)
1. Complete Phase 1 + Phase 2 (setup + foundational)
2. Complete US1 (T008–T012) → `./gradlew build` succeeds; app installs and shows "Android Analyzer"
3. Stop and demo: installable skeleton with zero permissions is shippable on its own
4. US1 alone satisfies Constitution gates I–IX for a skeleton feature

### Incremental Delivery
- After US1: manually verifiable product (install + launch)
- After US2: one-command local quality gate (`./scripts/verify.sh`)
- After US3: automated quality gate on every push/PR
- Polish closes the remaining success criteria (SC-002, SC-006, permission/offline audits)

### Parallel Team Strategy
- Single developer: execute phases strictly in order; parallelize only the `[P]` tasks within a phase
- Two developers: one takes US1 (T008–T012) while the other drafts US2's file-independent artifacts (T013–T015); both merge after the Foundational checkpoint

---

## Notes
- The command name `./scripts/verify.sh` is contractual (contracts/verification.md): do not rename or extend it without a spec change
- All versions come from `gradle/libs.versions.toml` (T002); SDK levels live in `app/build.gradle.kts` (T005) — drift from research.md pins fails Constitution VII review
- AGP 9.4.1 has built-in Kotlin support: do NOT add a standalone `org.jetbrains.kotlin.android` plugin (R-01)
- Lint errors are fatal; lint warnings are visible but never fail the build or affect exit codes (R-09, spec edge cases)
- CI never invokes `verify.sh`; it runs the Gradle checks directly (R-13, contracts/verification.md)
- This feature intentionally has NO domain entities, NO database, NO network, NO ViewModels (data-model.md) — reject any task creep that adds them
