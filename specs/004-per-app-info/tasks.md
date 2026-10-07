---
description: "Task list for feature 004-per-app-info implementation"
---

# Tasks: Per-App Information and Details Screen

**Input**: Design documents from `/specs/004-per-app-info/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/app-inventory.md](./contracts/app-inventory.md), [contracts/navigation-and-footer.md](./contracts/navigation-and-footer.md), [contracts/details-screen.md](./contracts/details-screen.md), [contracts/refresh-mode.md](./contracts/refresh-mode.md), [quickstart.md](./quickstart.md)

**Tests**: Included — Constitution Principle V (non-negotiable local quality gates) plus [quickstart.md](./quickstart.md) §1 make the V-A/V-R/V-D unit coverage part of this feature's design ([data-model.md](./data-model.md) §9). For **changed** classes the test change lands first and must be red before the implementation task runs; for **new** pure classes the class and its test file are created in one task and driven green together (a test referencing a missing class cannot compile, so compile-red would prove nothing).

**Organization**: Tasks grouped by user story. The three-destination shell + restyled footer (US2's contract N-clauses) sits in the **Foundational** phase because every story's verification navigates through it — 003 put the shared holder contract there for the same reason. US1 then delivers the Details screen (MVP), US3 the home drill-down, US4 auto-refresh, US5 core tiers.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1..US5)
- Paths are repository-relative (single `app/` module, plan.md Structure Decision)

## Path Conventions

Single Android module: sources under `app/src/main/java/com/jktecnologies/androidanalyzer/`, unit tests under `app/src/test/java/com/jktecnologies/androidanalyzer/`, resources under `app/src/main/res/values/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a green baseline before any change (Constitution V).

- [X] T001 Run `./scripts/verify.sh` from the repository root and confirm the existing suite (build + JVM unit tests + AGP lint) is green before any feature-004 change; record the baseline result — **PASS 2026-10-07** (`PASS: build, testDebugUnitTest, lint`, BUILD SUCCESSFUL in 20s)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The three-destination shell and the gapless rectangular footer — contracts [navigation-and-footer.md](./contracts/navigation-and-footer.md) N-1..N-4. Every user story's manual verification navigates through this substrate; US2's phase verifies it.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Extend `app/src/main/java/com/jktecnologies/androidanalyzer/ui/AnalyzerApp.kt` per N-1..N-4: add `Destination.DETAILS` and a third `when` branch; `AnalyzerApp` gains a `details: @Composable () -> Unit` slot; restyle `FooterBar` into one continuous bar — full-width `Row`, **no outer padding, no `spacedBy`**, three `weight(1f)` buttons each with `shape = RectangleShape` (FR-002), filled-vs-tonal selected indication and `selected` semantics preserved (FR-003); extend `BackHandler` to `enabled = destination != Destination.HOME` returning to `HOME` from both non-home tabs (N-4). In `app/src/main/res/values/strings.xml`: reword `footer_home` to "Home" and add `footer_details` = "Details" (N-3)
- [X] T003 Wire the third destination in `app/src/main/java/com/jktecnologies/androidanalyzer/MainActivity.kt`: pass `details = { }` (a temporary empty slot US1 fills in T010 — the app stays functional on every tab) and confirm `./gradlew :app:lintDebug` + `:app:testDebugUnitTest` stay green — **BUILD SUCCESSFUL 2026-10-07 (42s)**

**Checkpoint**: Shell builds with three gapless rectangular tabs switching correctly — user story implementation can begin.

---

## Phase 3: User Story 1 - Application Inventory with Per-App Figures (Priority: P1) 🎯 MVP

**Goal**: The Details tab lists every installed application with its system/user mark, All/User/System filter, per-app storage and memory figures (honest "Not available" where unreadable), the usage-access grant path, row tap → system app-info, and pull-to-refresh.

**Independent Test**: quickstart M-1..M-5 — list and marks match `pm list packages` ground truth; the deny→grant→refresh flow lights up storage figures; filters apply instantly; row tap opens the system app-info page; pull-to-refresh reconciles the inventory.

### Implementation for User Story 1

- [ ] T004 [P] [US1] Create `app/src/main/java/com/jktecnologies/androidanalyzer/domain/InstalledApps.kt` and its test `app/src/test/java/com/jktecnologies/androidanalyzer/domain/InstalledAppsTest.kt` covering V-A1..V-A3 ([data-model.md](./data-model.md) §1–3): `AppClassification` (SYSTEM/USER), `InstalledApp` fields (`packageName` identity, `displayName`, `classification`, `storageBytes: Long?`, `memoryBytes: Long?` — null = not available, never a substitute value, FR-006/FR-007) with factory rule V-A1 verbatim — "both names non-blank; any non-null byte value ≥ 0"; `AppInventory.create(apps)` with V-A2 verbatim — "package names unique; empty list allowed", Collator primary-strength sort by `displayName` owned by the factory (contract clause 4); `AppCategoryFilter.ALL/USER/SYSTEM` with pure `apply` (V-A3 — full list / matching subset, ordering preserved, no read triggered). No Android imports (JVM-testable)
- [ ] T005 [P] [US1] Add the Details strings to `app/src/main/res/values/strings.xml` per R-10: title, filter labels (All/User/System), marks (System/User), per-app figure templates (`app_storage_value` "Storage %1$s", `app_memory_value` "Memory %1$s"), usage-access hint + grant button, per-filter empty-state template. (Parallel-safe with T004 — different file)
- [ ] T006 [US1] Add the reader interfaces to `app/src/main/java/com/jktecnologies/androidanalyzer/data/DeviceReaders.kt` per [contracts/app-inventory.md](./contracts/app-inventory.md): `fun interface InstalledAppReader { fun read(): AppInventory? }` and `fun interface UsageAccessStatus { fun granted(): Boolean }`, subject to the 002 common clauses (one-shot, null = unavailable, main-thread hostile, pure mapping) plus clause 1 (single-pass figures) and clause 6 (no grant requests from the seam)
- [ ] T007 [US1] Implement the readers in `app/src/main/java/com/jktecnologies/androidanalyzer/data/AndroidDeviceReaders.kt` (R-01/R-02/R-03): one `getInstalledApplications(0)` enumeration → `loadLabel` display names, classification through the **shared** `isSystemApplication` (clause 2), `storageBytes` from `StorageStatsManager.queryStatsForPackage` (code + data + cache bytes) **only when** `UsageAccessStatus.granted()` — no per-package storage call otherwise, every `storageBytes` null (clause 1); `memoryBytes` always null with a `ponytail:` ceiling comment naming the missing platform API (R-03). `UsageAccessStatus` via `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, packageName) == MODE_ALLOWED`. In `app/src/main/AndroidManifest.xml` add the `<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS" tools:ignore="ProtectedPermissions"/>` appop declaration with a comment mirroring the 002 QUERY_ALL_PACKAGES note (R-02, Constitution VIII)
- [ ] T008 [US1] Create `app/src/main/java/com/jktecnologies/androidanalyzer/ui/details/DetailsStateHolder.kt` and its test `app/src/test/java/com/jktecnologies/androidanalyzer/ui/details/DetailsStateHolderTest.kt` covering V-D1..V-D6 through the existing fakes seam (`ResultPoster` + `executorFactory`): the 003 cycle rules verbatim on the inventory figure (presentation reset / refresh no-reset, absorption, epoch drop, executor replacement, `isRefreshing` ordering — V-D1, V-D5), reader null/throw → `Unavailable` + recovery (V-D2), `setFilter` changes selection only with zero reader calls (V-D3), `applyPendingFilter` overrides a chosen filter (V-D4), `usageAccessGranted: Boolean?` posted by the pass and coupled to null `storageBytes` when false (V-D6, [data-model.md](./data-model.md) §5/§7)
- [ ] T009 [US1] Create `app/src/main/java/com/jktecnologies/androidanalyzer/ui/details/DetailsScreen.kt` per [contracts/details-screen.md](./contracts/details-screen.md) D-1..D-5, G-1..G-2, R-1..R-2, P-1..P-3: three-option single-select filter above a `LazyColumn` of rows (display name, system/user mark, "Storage X · Memory Y" with the distinct `figure_unavailable` for null — never zero, D-3); per-filter explanatory empty state (D-4); three-state body with usable filter while loading/unavailable (D-5); grant hint row + `Settings.ACTION_USAGE_ACCESS_SETTINGS` button only while `usageAccessGranted == false` (G-1); row tap fires `ACTION_APPLICATION_DETAILS_SETTINGS` with the row's `package:` URI (R-1); `PullToRefreshBox` wrapping the list from outside with the 003 a11y pair (custom refresh action + live region, P-1/P-2) and lifecycle triggers mirroring Home (P-3); sp-scaled wrapping rows, no fixed heights
- [ ] T010 [US1] Wire the Details tab in `app/src/main/java/com/jktecnologies/androidanalyzer/MainActivity.kt`: construct `AndroidDeviceReaders`-backed `InstalledAppReader`/`UsageAccessStatus` and a remembered `DetailsStateHolder` (main-thread poster), replacing the T003 placeholder `details = { }` slot
- [ ] T011 [US1] Manual verification per [quickstart.md](./quickstart.md) rows M-1 (inventory/mark/count ground truth vs `pm list packages`), M-2 (deny→grant usage access → refresh → storage figures appear, memory stays "Not available"), M-3 (filter switching + zero-apps empty state), M-4 (row tap → system app-info → back restores list+filter), M-5 (pull-to-refresh reconciles after `adb install`/`uninstall`); record the observed outcomes and the ground-truth commands used

**Checkpoint**: Details screen works end-to-end — MVP delivered (SC-001..SC-003 hold).

---

## Phase 4: User Story 2 - Three-Tab Footer Navigation with Restyled Bar (Priority: P2)

**Goal**: One continuous rectangular footer with three working, indicated destinations; back gesture returns Home; settings survive tab switches.

**Independent Test**: quickstart M-6 — three equal square-cornered gapless buttons, exactly one active indication, every screen one tap away, previously chosen settings still in effect on revisit.

### Implementation for User Story 2

- [ ] T012 [US2] Manual verification per [quickstart.md](./quickstart.md) row M-6 (footer geometry per N-2, active indication per N-3, one-tap reachability per N-1, US2-4 settings preservation across switches) including the back gesture from DETAILS and from SETTINGS returning HOME (N-4); record outcomes
- [ ] T013 [US2] Close out US2 in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/AnalyzerApp.kt`: if M-6 exposed any N-1..N-4 deviation, fix it with the smallest diff; otherwise record US2 acceptance scenarios 1–4 as confirmed with no code change

**Checkpoint**: US1 and US2 both hold independently — navigation is trustworthy.

---

## Phase 5: User Story 3 - Home Entry Points with Pre-Applied Filter (Priority: P3)

**Goal**: Home shows separate System/User application counts as two tappable entries; each lands on Details already filtered to that category, overriding any previously chosen filter.

**Independent Test**: quickstart M-7 — two count entries match `pm list packages -s`/`-3`; each tap lands filtered with no full-list flash; Home arrival overrides a manual filter.

### Implementation for User Story 3

- [ ] T014 [P] [US3] Strings in `app/src/main/res/values/strings.xml`: add `figure_user_applications` = "User applications" and `figure_system_applications` = "System applications"; **remove** the now-unused `figure_applications` and `applications_value` (FR-011 replaces 002's combined presentation)
- [ ] T015 [P] [US3] Extend `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/FigureFormatting.kt` and `FigureFormattingTest.kt`: add `userApplicationsValue`/`systemApplicationsValue` (locale-grouped plain counts over `ApplicationInventory.nonSystemCount` / derived `systemCount`); write the failing tests first (red), then implement
- [ ] T016 [US3] Split the applications figure in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/HomeScreen.kt` per H-1/H-2: replace the combined `FigureRow` with two tappable entries — "User applications" and "System applications" — sharing the single `FigureUiState<ApplicationInventory>` (Loading → both placeholders, Unavailable → both indications, Available → the two counts via T015), each row clickable with row-level semantics calling `onOpenApplications(AppCategoryFilter.USER/SYSTEM)`; `HomeScreen` gains the `onOpenApplications: (AppCategoryFilter) -> Unit` parameter
- [ ] T017 [US3] Implement the hand-off in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/AnalyzerApp.kt` per H-3 and in `MainActivity.kt`: shell-owned `pendingDetailsFilter: AppCategoryFilter?` set together with `destination = DETAILS` by the Home callback, consumed once by `DetailsStateHolder.applyPendingFilter` (override rule, FR-012) then cleared; plain tab switches never touch the filter; a fresh process starts at ALL
- [ ] T018 [US3] Manual verification per [quickstart.md](./quickstart.md) row M-7 (two entries vs `pm` totals, pre-applied filter on arrival, no full-list flash, override of a manually chosen filter, zero-user-apps tap → empty state); record outcomes

**Checkpoint**: Home → Details drill-down works with the override rule (SC-004 holds).

---

## Phase 6: User Story 4 - Automatic Refresh Setting (Priority: P4)

**Goal**: Settings keeps Theme and gains an Automatic refresh section (On demand default / 30 s / 1 min / 5 min) that persists and drives a visible-screen-only auto-refresh ticker; pull-to-refresh stays available in every mode.

**Independent Test**: quickstart M-8/M-9 — fresh install shows On demand; a 30 s selection refreshes the visible screen without a gesture, survives restart, never runs while the app is backgrounded.

### Implementation for User Story 4

- [ ] T019 [US4] Create `app/src/main/java/com/jktecnologies/androidanalyzer/domain/RefreshMode.kt` and its test `app/src/test/java/com/jktecnologies/androidanalyzer/domain/RefreshModeTest.kt` covering V-R1/V-R2: `RefreshMode { ON_DEMAND, THIRTY_SECONDS, ONE_MINUTE, FIVE_MINUTES }` (the user-confirmed set, FR-015) with `intervalMillis: Long?` — V-R1 verbatim, "null iff ON_DEMAND" — and `fromPersisted(raw)` — V-R2 verbatim, "missing/unknown/corrupt → ON_DEMAND" (mirrors `ThemePreference.fromPersisted`)
- [ ] T020 [P] [US4] Add `interface RefreshModeStore` to `app/src/main/java/com/jktecnologies/androidanalyzer/data/DeviceReaders.kt` (contract clause 5) and `app/src/main/java/com/jktecnologies/androidanalyzer/data/SharedPreferencesRefreshModeStore.kt`: existing `android_analyzer` prefs file, string key `refresh_mode`, enum-name value, `load()` via `fromPersisted`, `save()` with `apply()` — the `ThemePreferenceStore` shape mirrored exactly
- [ ] T021 [P] [US4] Add the settings strings to `app/src/main/res/values/strings.xml` per R-10: section label "Automatic refresh" + the four option labels ("On demand", "Every 30 seconds", "Every minute", "Every 5 minutes")
- [ ] T022 [US4] Extend `app/src/main/java/com/jktecnologies/androidanalyzer/ui/settings/SettingsScreen.kt` per [contracts/refresh-mode.md](./contracts/refresh-mode.md) S-1..S-4: second radio section reusing `ThemeOptionRow`, new `selectedRefreshMode` / `onRefreshModeSelected` parameters; Theme section untouched. Wire in `MainActivity.kt` per S-2/W-1: construct `RefreshModeStore`, lift loaded mode into Compose state, persist **first** then update state (002 T-027 order)
- [ ] T023 [US4] Implement the ticker in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/AnalyzerApp.kt` per T-1..T-5: one `LaunchedEffect(refreshMode, destination)` that sleeps `intervalMillis` and calls the **visible** screen's `refresh()` (SETTINGS → nothing), gated on lifecycle `RESUMED` via the screens' observer pattern — no tick below RESUMED, zero background work (FR-017); `ON_DEMAND` leaves it inert; mode/destination changes take effect at the next tick boundary with no catch-up burst
- [ ] T024 [US4] Manual verification per [quickstart.md](./quickstart.md) rows M-8 (default On demand + Theme intact; 30 s selection auto-refreshes the visible screen; survives restart; swipe still works in both modes) and M-9 (nothing refreshes while backgrounded; ticking resumes on return); record outcomes

**Checkpoint**: Auto-refresh setting works on both screens, foreground-only (SC-006 holds).

---

## Phase 7: User Story 5 - Processor Core Types (Priority: P5)

**Goal**: Home's processor entry shows per-type core counts where the device distinguishes them, and today's plain total count where it does not.

**Independent Test**: quickstart M-10 — emulator renders the plain total (fallback); tiered hardware renders per-type counts summing to the total.

### Implementation for User Story 5

- [ ] T025 [US5] Create `app/src/main/java/com/jktecnologies/androidanalyzer/domain/CoreTiers.kt` and its test `app/src/test/java/com/jktecnologies/androidanalyzer/domain/CoreTiersTest.kt` covering V-A4: `CoreTier(count, maxFrequencyHz)` + `CoreTiers(totalCount, tiers)` with factory rule verbatim — "totalCount ≥ 1; tiers non-empty; each count ≥ 1; tier counts sum to totalCount; frequencies distinct and > 0" — tiers ascending by frequency; violation → null → Unavailable; the single-tier shape is valid (it is FR-013's fallback, [data-model.md](./data-model.md) §4)
- [ ] T026 [P] [US5] In `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/FigureFormatting.kt` + `FigureFormattingTest.kt` and `strings.xml`: replace `processorValue(CoreCount)` with `processorValue(CoreTiers)` rendering V-A4b — single tier → the plain locale-grouped count (byte-identical to today's rendering), multi-tier → "N cores: a × f₁ + b × f₂" via new `processor_tiers_value` = "%1$d cores: %2$s" with GHz one-decimal locale formatting; failing tests first (red), then implement
- [ ] T027 [US5] Add `fun interface CoreTierReader { fun read(): CoreTiers? }` to `app/src/main/java/com/jktecnologies/androidanalyzer/data/DeviceReaders.kt` and implement it in `AndroidDeviceReaders.kt` per R-04 + contract clause 3: group cores by `/sys/devices/system/cpu/cpu*/cpufreq/cpuinfo_max_freq`; **fallback inside the reader** — failed walk or a single frequency for all cores → single-tier `CoreTiers(Runtime.availableProcessors())`; null only if even that fails
- [ ] T028 [US5] Switch the home processor figure type in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/HomeStateHolder.kt` (`FigureUiState<CoreCount>` → `FigureUiState<CoreTiers>`, reader constructor parameter swapped — cycle logic untouched), adjust `HomeStateHolderTest.kt` accordingly (fail first where the type changes), and pass the new reader in `MainActivity.kt`
- [ ] T029 [US5] Manual verification per [quickstart.md](./quickstart.md) row M-10 (emulator: plain total via the fallback path; big.LITTLE hardware if available: per-type counts summing to the device core count); record outcomes

**Checkpoint**: Processor entry deepens without regressing the 002 rendering (SC-008 holds).

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final gates and whole-feature verification.

- [ ] T030 Run `./scripts/verify.sh` (clean build + full JVM unit suite + AGP lint, `abortOnError = true`) and confirm green — the Constitution V gate before the change set is presented as complete
- [ ] T031 Final cross-cutting verification: run the complete [quickstart.md](./quickstart.md) pass (M-1..M-11) and record the feature verdict against SC-001..SC-008; confirm via `git diff` that `app/build.gradle.kts` and `gradle/libs.versions.toml` are untouched (zero new dependencies, Constitution VII) and that `app/src/main/AndroidManifest.xml` contains exactly one addition — the `PACKAGE_USAGE_STATS` appop declaration from T007 (Constitution VIII)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on T001; **blocks all user stories** (every story navigates through the shell).
- **US1 (Phase 3)**: Depends on Phase 2 (the DETAILS slot must exist to host the screen).
- **US2 (Phase 4)**: Depends on Phase 2 — its implementation *is* Phase 2's shell; verification additionally wants US1's Details tab populated (M-6 taps through it), so run after Phase 3.
- **US3 (Phase 5)**: Depends on US1 (`applyPendingFilter` + the filter UI) and Phase 2 (shell hand-off slot).
- **US4 (Phase 6)**: Depends on Phase 2 (ticker routes by destination); independent of US3/US5 — the ticker needs both holders to exist (T010 delivers the details holder), so run after Phase 3.
- **US5 (Phase 7)**: Depends on nothing but Phase 1/2 for its own slice; keep last in priority order.
- **Polish (Phase 8)**: Depends on all user stories being complete.

### Within Each User Story

- T004/T005 are parallel entry points; T006 → T007 → T008 → T009 → T010 chain (interface → implementation → holder → screen → wiring); T011 verifies.
- T014/T015 parallel, then T016 → T017 → T018.
- T019 → T020/T021 (parallel) → T022 → T023 → T024.
- T025 → T026/T027 (parallel) → T028 → T029.
- Changed-class test tasks (T015, T026, T028) run red before their implementation lands.

### Parallel Opportunities

- T004 (domain + test) ∥ T005 (strings.xml) — different files.
- T014 (strings.xml) ∥ T015 (FigureFormatting + test) — different files.
- T020 (store) ∥ T021 (strings.xml) after T019.
- T026 (formatting + strings) ∥ T027 (readers) after T025.
- US4 (Phase 6) and US5 (Phase 7) are parallelizable as whole phases once Phase 3 is complete — disjoint file sets except `strings.xml`/`MainActivity.kt` touch points (sequence T021 before/after T026 or merge commits cleanly).

## Parallel Example: User Story 5

```bash
# After T025 (CoreTiers model) is green, these can proceed concurrently:
Task: "T026 [P] [US5] processorValue(CoreTiers) + strings"   (FigureFormatting, strings.xml)
Task: "T027 [P] [US5] CoreTierReader + sysfs impl"           (DeviceReaders.kt, AndroidDeviceReaders.kt)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002, T003) — CRITICAL, blocks all stories
3. Complete Phase 3: User Story 1 (T004..T011)
4. **STOP and VALIDATE**: M-1..M-5 pass ⇒ the Details screen delivers the core value (SC-001..SC-003)

### Incremental Delivery

1. Setup + Foundational → three-tab shell with restyled footer
2. + US1 → working per-app inventory screen (MVP)
3. + US2 → footer/navigation verified
4. + US3 → home drill-down with pre-applied filter
5. + US4 → automatic refresh
6. + US5 → processor core types
7. Polish → full gates + quickstart verdict (SC-001..SC-008)

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- V = [data-model.md](./data-model.md) §9 test targets; N/H/T = [contracts/navigation-and-footer.md](./contracts/navigation-and-footer.md) clauses; D/G/R/P = [contracts/details-screen.md](./contracts/details-screen.md) clauses; S/W = [contracts/refresh-mode.md](./contracts/refresh-mode.md) clauses; R = [research.md](./research.md) decisions; M = [quickstart.md](./quickstart.md) rows
- Manual rows (T011, T012, T018, T024, T029, T031) need any Android 15+ device or emulator per quickstart §0 — the dev VM has no attached device, no emulator system images, and no KVM (same situation as features 002/003); record them as blocked-in-VM if none is available
- Verify changed-class tests fail before implementing (T015, T026, T028 red → green)
- Commit after each task or logical group to the feature branch (`feature/004-per-app-info`); the VM never pushes (Constitution III)
- Stop at any checkpoint to validate the story independently
