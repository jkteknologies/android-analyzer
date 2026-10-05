---
description: "Task list for feature 002-home-screen implementation"
---

# Tasks: Home Screen

**Input**: Design documents from `/specs/002-home-screen/` (plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md)

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/device-readers.md, contracts/ui-contracts.md — all present.

**Tests**: Included. Constitution Principle V (NON-NEGOTIABLE) mandates the local unit-test gate, and plan.md's Testing section fixes the JVM JUnit scope; test tasks are therefore part of every story. Write tests first, confirm they fail, then implement.

**Organization**: Tasks grouped by user story (spec.md US1–US4, priority order) so each story is an independently testable increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- File paths use the conventions below

## Path Conventions

Single-module Android app (plan.md Project Structure). Abbreviations used throughout:

- `MAIN` = `app/src/main/java/com/jktechnologies/androidanalyzer`
- `TEST` = `app/src/test/java/com/jktechnologies/androidanalyzer`
- `RES` = `app/src/main/res`
- No Gradle/dependency changes anywhere in this feature (research.md R-16: zero new dependencies).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Package skeleton for the new layers (no dependencies, no build config to change)

- [X] T001 Create package directories `MAIN/data`, `MAIN/domain`, `MAIN/ui/home`, `MAIN/ui/settings`, `MAIN/ui/theme` and mirrored test packages under `TEST/domain`, `TEST/data`, `TEST/ui/home`, `TEST/ui/theme` (plan.md Project Structure)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The seams every story consumes — the reader-interface contract and the shared string resources

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T002 [P] Create the reader interface family in `MAIN/data/DeviceReaders.kt` exactly per [contracts/device-readers.md](./contracts/device-readers.md): `fun interface MemoryReader/StorageReader/BatteryReader/CoreCountReader` (each `fun read(): T?`), `fun interface ApplicationCounter` (`fun count(): ApplicationInventory?`), `interface ThemePreferenceStore` (`load(): ThemePreference`, `save(preference)`); document the common clauses in KDoc — one-shot, `null` = unavailable, main-thread hostile, no state
- [X] T003 [P] Add all feature string resources to `RES/values/strings.xml` (English only, spec assumption "Formatting"): figure labels "Memory", "Internal storage", "Battery", "Processor", "Applications"; value templates with positional args (e.g. `memory_value`: "Available %1$s · Allocated %2$s", `storage_value`: "Free %1$s · Used %2$s", `battery_value`: "%1$d%% · %2$s", `applications_value`: "%1$s (%2$s)"); neutral placeholder "—" (`figure_placeholder`); unavailability text `figure_unavailable` ("Not available"); charging states `charging_charging` ("Charging"), `charging_not_charging` ("Not charging"); footer buttons `footer_home` ("Home screen"), `footer_settings` ("Settings"); settings title `settings_title` ("Settings"), theme setting label `theme_label` ("Theme"), options `theme_light` ("Light"), `theme_dark` ("Dark"), `theme_system` ("System default"); plus TalkBack name-and-value templates per figure (ui-contracts H-8). Note: footer/theme strings are unused until US3/US4 — an interim unused-resource lint warning is acceptable (warnings do not fail the build)

**Checkpoint**: Foundation ready — user story implementation can now begin

---

## Phase 3: User Story 1 - Device Resource and App Overview (Priority: P1) 🎯 MVP

**Goal**: Home screen shows the one-shot device overview — memory (available/allocated), internal storage (free/used), battery (level/charging state), processor core count, and application counts "non-system (total)" — with per-figure placeholders, unavailability handling, and re-read on every presentation.

**Independent Test**: Launch on an Android 15+ device/emulator and compare every figure against device-reported values (`dumpsys battery`, `/proc/meminfo`, `df /data`, `pm list packages` counts) — quickstart.md scenarios M-1, M-2, M-3. Delivers a working analyzer home screen on its own.

### Tests for User Story 1 (write first; confirm they FAIL)

- [X] T004 [P] [US1] Write failing unit tests in `TEST/domain/FiguresTest.kt` covering data-model validation rules V-1..V-6, quoting the constraints: figure transitions strictly `any → Loading` on cycle start, `Loading → Available(value) | Unavailable`, Available/Unavailable terminal per cycle (V-1); `allocatedBytes = totalBytes − availableBytes` and `usedBytes = totalBytes − availableBytes` derived from one reading (V-2); all byte figures `≥ 0` and pair sums `= totalBytes` (V-3); `totalBytes > 0`, `0 ≤ availableBytes ≤ totalBytes`, violation ⇒ figure unavailable — never clamped (V-4); `0 ≤ levelPercent ≤ 100` else unavailable (V-5); `CoreCount.count ≥ 1` (V-6)
- [X] T005 [P] [US1] Write failing unit tests: `TEST/domain/ApplicationInventoryTest.kt` for V-7/V-9 — `nonSystemCount ≥ 0`, `totalCount ≥ 0`, `nonSystemCount ≤ totalCount`, `systemCount = totalCount − nonSystemCount` (V-7); display string exact form `"N (M)"` incl. `"36 (121)"`, `"0 (121)"`, and locale grouping for large counts (V-9) — and `TEST/data/AppClassifierTest.kt` for V-8 — `flags` with `FLAG_SYSTEM` set ⇒ system; `FLAG_SYSTEM or FLAG_UPDATED_SYSTEM_APP` (updated preinstalled app) ⇒ still system (FR-005); neither ⇒ non-system

### Implementation for User Story 1

- [X] T006 [P] [US1] Implement figure domain models in `MAIN/domain/Figures.kt` making T004 pass: `FigureUiState<T>` sealed (`Loading`, `Available(value)`, `Unavailable`), `MemoryReading`, `StorageReading` (derived `allocatedBytes`/`usedBytes` computed at construction from the single reading), `BatteryReading`, `CoreCount`, with factory-from-raw-reads validation that returns `null` on V-4/V-5/V-6 violations (pure Kotlin, no Android imports)
- [X] T007 [P] [US1] Implement `ApplicationInventory` in `MAIN/domain/ApplicationInventory.kt` making V-7/V-9 pass: counts + derived `systemCount` + locale-aware display formatting `"N (M)"`
- [X] T008 [P] [US1] Implement the pure classifier in `MAIN/data/AppClassifier.kt` making V-8 pass: `fun isSystemApplication(flags: Int): Boolean = (flags and ApplicationInfo.FLAG_SYSTEM) != 0` (flag constant is compile-time inlined — JVM-test-safe, research.md R-01)
- [X] T009 [US1] Implement platform readers in `MAIN/data/AndroidDeviceReaders.kt` (depends on T002, T006): `MemoryReader` via one `ActivityManager.getMemoryInfo` (`totalMem`, `availMem`); `StorageReader` via one `StatFs(Environment.getDataDirectory())` (`totalBytes`, `availableBytes` — not `freeBytes`, R-03); `BatteryReader` via one-shot `context.registerReceiver(null, IntentFilter(BatteryManager.ACTION_BATTERY_CHANGED))` (`EXTRA_LEVEL*100/EXTRA_SCALE`, `EXTRA_STATUS ∈ {CHARGING, FULL}` ⇒ charging, R-04); `CoreCountReader` via `Runtime.getRuntime().availableProcessors()` (R-05). Each performs exactly one platform read, validates via the T006 factories, returns `null` on unavailable — no receivers registered, no caching
- [X] T010 [US1] Implement `ApplicationCounter` in `MAIN/data/AndroidDeviceReaders.kt` (depends on T008, T002): `PackageManager.getInstalledApplications(0)`, classify each entry with `isSystemApplication(applicationInfo.flags)`, return `ApplicationInventory(nonSystemCount, totalCount)`; null/exception propagates as unavailable
- [X] T011 [US1] Add `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />` to `app/src/main/AndroidManifest.xml` with a comment citing the justification (FR-004 total count requires full enumeration; FR-013/Constitution VIII justification in research.md R-01); keep the existing `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` removal directives untouched; no other manifest changes
- [X] T012 [P] [US1] Implement locale-aware formatting in `MAIN/ui/home/FigureFormatting.kt` + unit tests `TEST/ui/home/FigureFormattingTest.kt`: byte amounts via `Formatter.formatShortFileSize` (R-12), counts via `NumberFormat.getInstance(locale)`, battery as whole percent, charging-state and value strings resolved from the T003 resources; inject locale for testability
- [X] T013 [US1] Implement `HomeStateHolder` in `MAIN/ui/home/HomeStateHolder.kt` + unit tests `TEST/ui/home/HomeStateHolderTest.kt` with fake readers (depends on T002, T006, T007, T009, T010): owns a single `Executors.newSingleThreadExecutor()`, posts results via `Handler(Looper.getMainLooper())` (R-09); a read cycle resets every figure to `Loading` then fills each independently — fake returns `null` or throws ⇒ `Unavailable` (FR-012/FR-014); triggers wired for entering-composition and lifecycle `ON_RESUME` via `DefaultLifecycleObserver` (FR-011, R-10); executor shut down when the holder is discarded; tests must not require Android framework classes (fake the main-thread posting via an injected poster)
- [X] T014 [US1] Implement `HomeScreen` composable in `MAIN/ui/home/HomeScreen.kt` (depends on T012, T013): exactly five figure groups — Memory, Internal storage, Battery, Processor, Applications (FR-001/FR-003); renders immediately, each figure independently shows placeholder → value/unavailable (ui-contracts H-1..H-5); per-figure merged semantics "label: value-or-indication" for TalkBack (H-8, FR-015); sp-based typography, wrapping rows, no fixed heights (H-9)
- [X] T015 [US1] Wire `MainActivity.kt` to host `HomeScreen` (replacing `PlaceholderScreen`), start the read cycle on the FR-011 triggers; delete `MAIN/ui/PlaceholderScreen.kt` and `TEST/ui/PlaceholderScreenTest.kt`
- [X] T016 [US1] Quality gate: run `./scripts/verify.sh` and fix until green (Constitution V); manually run quickstart M-1, M-2, M-3 on a device/emulator

**Checkpoint**: MVP — User Story 1 fully functional and independently testable

---

## Phase 4: User Story 2 - System Theme Following (Priority: P2)

**Goal**: With no user configuration, the app renders dark when the device is in dark mode and light otherwise, following live switches without restart.

**Independent Test**: `adb shell cmd uimode night yes|no` with the app foreground and backgrounded (quickstart M-4). Logically independent of US1 (works against any hosted screen); executed after US1's checkpoint to avoid `MainActivity` edit conflicts.

### Tests for User Story 2 (write first; confirm they FAIL)

- [X] T017 [P] [US2] Write failing unit tests in `TEST/domain/ThemePreferenceTest.kt` covering data-model §4/V-10: effective resolution `LIGHT → Light`, `DARK → Dark`, `SYSTEM → systemInDarkMode ? Dark : Light` (both branches); missing or corrupt persisted value falls back to `SYSTEM`; persistence round-trip through a store fake (save→load returns the saved value)

### Implementation for User Story 2

- [X] T018 [P] [US2] Implement `ThemePreference` in `MAIN/domain/ThemePreference.kt` making T017 pass: enum `LIGHT | DARK | SYSTEM` (`SYSTEM` the default, FR-009) + pure `effectiveTheme(preference, systemInDarkMode)` (pure Kotlin, no Android imports)
- [X] T019 [P] [US2] Implement `SharedPreferencesThemeStore` in `MAIN/data/SharedPreferencesThemeStore.kt` (depends on T002, T018): `ThemePreferenceStore` over a single string key `theme_preference` (enum name); `load()` maps missing/corrupt → `SYSTEM`; `save()` uses `apply()` (R-07); unit-testable via an in-memory `ThemePreferenceStore` fake exercising the contract
- [X] T020 [US2] Implement `AppTheme` in `MAIN/ui/theme/AppTheme.kt` (depends on T018, T019) and wire it in `MainActivity.kt`: `lightColorScheme()` / `darkColorScheme()` static palettes (R-06 — no dynamic color), scheme selected by `effectiveTheme(preference, isSystemInDarkTheme())`, preference loaded once at activity creation via the store; system theme switches follow live via recomposition and apply correctly on resume (FR-006, SC-004, ui-contracts T-1..T-4)
- [X] T021 [US2] Quality gate: `./scripts/verify.sh` green; manually run quickstart M-4 (live switch foreground + correct theme after backgrounded switch)

**Checkpoint**: Stories 1 AND 2 both work independently

---

## Phase 5: User Story 3 - Footer Navigation to Settings (Priority: P2)

**Goal**: Persistent footer with exactly two buttons — "Home screen" (left), "Settings" (right) — with current-destination indication, hosting the settings screen shell; system back returns home.

**Independent Test**: Tap each footer button from each screen and observe the presented screen and the footer indication; rapid alternating taps stay consistent (quickstart M-5, ui-contracts U-1..U-6). Integrates US1's `HomeScreen` and US2's `AppTheme`; the settings surface itself is a title-only shell here (its single setting arrives in US4 — no placeholder text, edge case S-1).

- [X] T022 [US3] Implement the app shell in `MAIN/ui/AnalyzerApp.kt`: `Destination` enum (`HOME` initial, `SETTINGS`) held in a `mutableStateOf` (R-08, data-model §5); Scaffold with a persistent footer on every screen containing exactly two buttons "Home screen" left / "Settings" right (FR-007), the current destination visually indicated e.g. filled vs tonal (FR-008); content switches `HomeScreen` ↔ `SettingsScreen`; `BackHandler` on `SETTINGS` returns `HOME` (ui-contracts U-1..U-6); entering `HOME` triggers the home read cycle (FR-011 in-app return)
- [X] T023 [P] [US3] Implement `SettingsScreen` shell in `MAIN/ui/settings/SettingsScreen.kt`: titled surface ("Settings") reserving exactly one setting slot for the theme control (delivered in US4) — no other sections, no "coming soon" (S-1); screen-reader operable header semantics (A-1)
- [X] T024 [US3] Wire `MainActivity.kt` to host `AppTheme { AnalyzerApp(...) }` replacing the direct `HomeScreen` host; footer buttons expose button role + selected-state semantics (A-1)
- [X] T025 [US3] Quality gate: `./scripts/verify.sh` green; manually run quickstart M-5 including the rapid-tap edge case and the system-back gesture

**Checkpoint**: Two-screen application shell complete over the themed home screen

---

## Phase 6: User Story 4 - Manual Theme Selection (Priority: P3)

**Goal**: The settings screen's single setting — theme — offers Light / Dark / System default, applies immediately app-wide, and persists across restarts.

**Independent Test**: Select each option and observe the immediate appearance change; force-stop and relaunch after a manual selection (quickstart M-6, SC-005). Depends on US2's theme machinery and US3's settings screen.

- [ ] T026 [P] [US4] Implement the theme selector in `MAIN/ui/settings/SettingsScreen.kt`: exactly one setting, three selectable rows (Light / Dark / System default) wired to the T003 strings, `System default` selected initially (FR-009), selection raises a callback (S-1..S-6); rows expose selectable semantics with announced state (A-1/S-6)
- [ ] T027 [US4] Lift theme preference into app state in `MainActivity.kt` (depends on T019, T020, T026): hold `themePreference` as Compose state loaded from `ThemePreferenceStore` at creation; on selection, `save()` then update state so `AppTheme` recomposes immediately — no restart (FR-010); corrupt-store fallback already covered by T017
- [ ] T028 [US4] Quality gate: `./scripts/verify.sh` green; manually run quickstart M-6 (immediacy + persistence across force-stop/relaunch) and the M-7 largest-font-scale walkthrough (revert `font_scale` afterwards)

**Checkpoint**: All four stories complete — full feature behavior per spec

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Cross-story verification that individual story gates cannot cover

- [ ] T029 [P] Accessibility sweep (FR-015): walk home + settings with TalkBack (or semantics inspection) across all figure states (Loading/Available/Unavailable), footer buttons, and radio rows; confirm the unavailability indication is announced distinctly from any value (H-8) and nothing clips/overlaps at font scale 2.0 (H-9); fix findings in `MAIN/ui/home/HomeScreen.kt`, `MAIN/ui/AnalyzerApp.kt`, `MAIN/ui/settings/SettingsScreen.kt`
- [ ] T030 [P] Update `README.md`: note the new `QUERY_ALL_PACKAGES` permission and its one-line justification, and point manual validation at `specs/002-home-screen/quickstart.md`
- [ ] T031 Final gate: clean `./scripts/verify.sh` run (build + unit tests + lint) green; full quickstart M-1..M-8 pass; confirm the manifest delta for the whole feature is exactly the single `QUERY_ALL_PACKAGES` permission and no new components/receivers/services (SC-006, Constitution VIII); re-verify Constitution Principles I–IX compliance for the delivered code and record any deviation with rationale in plan.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **User Stories (Phases 3–6)**: All depend on Phase 2; executed sequentially here in priority order (US1 → US2 → US3 → US4) to avoid `MainActivity`/`strings.xml` edit conflicts
- **Polish (Phase 7)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Depends only on Foundation — fully independent MVP
- **US2 (P2)**: Logically independent (would theme any hosted screen); executed after US1 only to avoid `MainActivity` conflicts
- **US3 (P2)**: Composes US1's `HomeScreen` and US2's `AppTheme` into the shell; settings surface is title-only until US4
- **US4 (P3)**: Depends on US2 (theme machinery) + US3 (settings screen host)

### Within Each User Story

- Tests first (must FAIL before implementation), models before services, services before UI, wiring last, quality gate closes the story
- Task-level dependencies: T009/T010 ← T002+T006/T008; T013 ← T009, T010; T014 ← T012, T013; T020 ← T018, T019; T027 ← T019, T020, T026

### Parallel Opportunities

- T002 ∥ T003 (Foundation)
- T004 ∥ T005, then T006 ∥ T007 ∥ T008, and T012 alongside (all distinct files, US1)
- T017, then T018 ∥ T019 (US2)
- T029 ∥ T030 (Polish)
- Cross-story parallelism is possible with different developers (US2 could proceed during US1) at the cost of `MainActivity` merge coordination

---

## Parallel Example: User Story 1

```bash
# Launch all US1 test tasks together:
Task: "T004 Figure domain tests in TEST/domain/FiguresTest.kt"
Task: "T005 Inventory + classifier tests in TEST/domain/ApplicationInventoryTest.kt, TEST/data/AppClassifierTest.kt"

# Then launch all US1 model tasks together:
Task: "T006 Figure models in MAIN/domain/Figures.kt"
Task: "T007 ApplicationInventory in MAIN/domain/ApplicationInventory.kt"
Task: "T008 Classifier in MAIN/data/AppClassifier.kt"
Task: "T012 Formatting in MAIN/ui/home/FigureFormatting.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) + Phase 2 (Foundation)
2. Complete Phase 3 (US1) through its quality gate T016
3. **STOP and VALIDATE**: quickstart M-1..M-3 on a device/emulator
4. The analyzer's core value — the device overview — is shippable here

### Incremental Delivery

1. Foundation → 2. US1 home figures (MVP) → 3. US2 system theming → 4. US3 footer navigation → 5. US4 manual theme → 6. Polish + final gate; each step leaves `./scripts/verify.sh` green and adds value without breaking predecessors

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- Commit after each task or logical group; work only on `feature/002-home-screen` (Constitution II); never push from the VM (Constitution III)
- Every code-generating task ends with its story's `./scripts/verify.sh` gate — no code is presented before tests + lint pass (Constitution V)
- Zero new dependencies (R-16): any perceived need for a library is a design question — escalate to plan.md, do not add silently (Constitution VII)
- The single new permission `QUERY_ALL_PACKAGES` is pre-justified (R-01); any additional permission is a Constitution VIII violation
