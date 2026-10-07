---
description: "Task list for feature 003-pull-to-refresh implementation"
---

# Tasks: Pull-to-Refresh on Home Screen

**Input**: Design documents from `/specs/003-pull-to-refresh/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/refresh-interaction.md](./contracts/refresh-interaction.md), [quickstart.md](./quickstart.md)

**Tests**: Included — Constitution Principle V (non-negotiable local quality gates) plus [quickstart.md](./quickstart.md) §1 make the VR-1..VR-10 unit coverage part of this feature's design ([data-model.md](./data-model.md) §5). Story tests are written first and must FAIL (red) before their implementation task runs.

**Organization**: Tasks grouped by user story. The holder semantics shared by every story sit in the Foundational phase; each story phase then adds its own increment (US1 gesture wiring, US2 feedback verification, US3 accessibility + robustness) and is independently verifiable via its quickstart rows.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Paths are repository-relative (single `app/` module, plan.md Structure Decision)

## Path Conventions

Single Android module: sources under `app/src/main/java/com/jkteknologies/androidanalyzer/`, unit tests under `app/src/test/java/com/jktecnologies/androidanalyzer/`, resources under `app/src/main/res/values/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a green baseline before any change (Constitution V).

- [x] T001 Run `./scripts/verify.sh` from the repository root and confirm the existing suite (build + JVM unit tests + AGP lint) is green before any feature-003 change; record the baseline result

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The complete `HomeStateHolder` refresh contract ([contracts/refresh-interaction.md](./contracts/refresh-interaction.md) C-1..C-8) — every user story depends on these semantics.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### Tests (write first, red)

- [x] T002 Add failing unit tests for the full refresh contract to `app/src/test/java/com/jkteknologies/androidanalyzer/ui/home/HomeStateHolderTest.kt`, extending the existing fakes pattern (`ResultPoster` + `executorFactory` + fake readers) per [data-model.md](./data-model.md) §5: VR-1 (no-reset on refresh), VR-2 (re-read each reader once per pass, in-place posts), VR-3 (absorption: "refresh() while a pass runs is absorbed: it forces `isRefreshing = true` and returns — never queued, never parallel"), VR-4 (pull during presentation pass settles once), VR-5 (isRefreshing clears only via the completion post ordered after the fifth figure post), VR-6 (absorbed trigger changes nothing), VR-7 (posts carrying a stale epoch are dropped), VR-8 (executor replaced after `shutdown()`), VR-9 (Unavailable → Available recovery and stable Unavailable on repeat failure), VR-10 (presentation cycle keeps resetting to `Loading` and supersedes a running refresh). Run `./gradlew :app:testDebugUnitTest` and confirm the new tests fail

### Implementation

- [x] T003 Implement the refresh contract in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/HomeStateHolder.kt`: refactor the read pass into one private `startCycle(resetOnStart)` used by both entry points; add `val isRefreshing` (observable), the `cycleRunning` guard, the monotonic `epoch` with epoch-validity checks on every figure/completion post, and the final completion post (`cycleRunning = false; isRefreshing = false`) as the pass's last executor step; `fun refresh()` = absorption per C-2 ("absorbed: it forces `isRefreshing = true` and returns"), else no-reset pass per C-3, with executor replacement per C-6; `startReadCycle()` behavior stays byte-identical to feature 002 (C-8: reset to `Loading`, always starts, bumps the epoch). All state UI-thread-confined — no synchronization primitives (C-1). Run `./gradlew :app:testDebugUnitTest` and confirm VR-1..VR-10 are green

**Checkpoint**: Holder satisfies C-1..C-8, full unit suite green — user story implementation can begin.

---

## Phase 3: User Story 1 - Reload Information by Pulling Down (Priority: P1) 🎯 MVP

**Goal**: The pull-down-and-release gesture on the home screen reloads every figure from a fresh snapshot.

**Independent Test**: quickstart M-1..M-3 — change device state (install/uninstall an app), pull and release, and the figures update to the new ground truth; a partial pull cancels without reloading; unchanged values never flash placeholders.

### Implementation for User Story 1

- [x] T004 [US1] Wire the gesture in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/HomeScreen.kt`: wrap the scrollable figure Column *from outside* with `PullToRefreshBox(isRefreshing = holder.isRefreshing, onRefresh = holder::refresh)` and the default Material3 indicator (contract W-1..W-4; research R-01 — the API is stable in the pinned Material3 1.4.0, no dependency change). Placement must keep the Box outside the `verticalScroll` so arming at content-top comes from nested scroll (FR-009, R-08); the footer, settings screen, and `AnalyzerApp.kt` stay untouched (FR-007). No edge-exclusion or system-gesture overrides (FR-008, W-4)
- [ ] T005 [US1] Manual verification per [quickstart.md](./quickstart.md) rows M-1 (pull reloads changed application counts / resource ground truth), M-2 (partial pull cancels, no reload, no flicker), M-3 (unchanged values stay visible, no placeholder flash, no layout jump) on an Android 15+ device or emulator; record the observed outcomes and the ground-truth commands used

**Checkpoint**: Pull-to-refresh works end-to-end — MVP delivered (SC-001 holds).

---

## Phase 4: User Story 2 - Visible, Non-Disruptive Refresh Feedback (Priority: P2)

**Goal**: Clear refresh indication, values remaining on screen until replaced, and nothing blocking the user mid-refresh.

**Independent Test**: quickstart M-4/M-5 plus the already-green VR-1..VR-6 unit coverage — indicator appears at release and dismisses ≤ 1 s after figures settle (settle ≤ 2 s), navigation away mid-refresh is safe.

### Implementation for User Story 2

- [ ] T006 [US2] Manual verification per [quickstart.md](./quickstart.md) rows M-4 (indicator timing SC-002/SC-003, values stay visible throughout FR-004, footer and content stay operable while refreshing) and M-5 (navigate to Settings mid-refresh and return — no crash, fresh presentation reads, no stale or jumbled figures FR-011); record outcomes
- [ ] T007 [US2] Close out US2 in `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/HomeStateHolder.kt` and `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/HomeScreen.kt`: if M-4/M-5 exposed any FR-003/FR-004/FR-011 deviation, fix it with a new or adjusted unit test from the VR list reproducing the deviation first; otherwise record US2 acceptance scenarios 1–4 as confirmed with no code change

**Checkpoint**: US1 and US2 both hold independently — feedback is trustworthy.

---

## Phase 5: User Story 3 - Predictable Behavior Under Repeated and Boundary Interactions (Priority: P3)

**Goal**: Repeated pulls coalesce, pulls during initial load settle once, the system shade keeps precedence, and the refresh is screen-reader operable.

**Independent Test**: quickstart M-6..M-9 plus the already-green VR-3/VR-4/VR-7 unit coverage — one indicator run per settle, single-pass settlement during initial load, TalkBack can trigger and hears both states.

### Implementation for User Story 3

- [x] T008 [P] [US3] Add the three accessibility/status strings to `app/src/main/res/values/strings.xml` exactly as specified by research R-06: `refresh_action` = "Refresh figures", `refresh_status_refreshing` = "Refreshing figures", `refresh_status_done` = "Figures refreshed" (contract W-6 — no other visible-text changes)
- [x] T009 [US3] Add the accessibility layer to `app/src/main/java/com/jktecnologies/androidanalyzer/ui/home/HomeScreen.kt` (contract W-5, research R-05): a custom accessibility action labeled `refresh_action` on the home content that calls `holder.refresh()`, and a polite live-region status node that announces `refresh_status_refreshing` while `isRefreshing` is true and `refresh_status_done` when a running refresh completes (FR-013, SC-006); merged figure semantics (H-8) and footer semantics unchanged. Depends on T008 (string resources)
- [ ] T010 [US3] Manual verification per [quickstart.md](./quickstart.md) rows M-6 (rapid repeated pulls → one cycle; cold-launch pull during placeholders → figures settle exactly once), M-7 (top-edge swipe still opens the notification shade), M-8 (TalkBack: action-menu "Refresh figures" runs the refresh; both announcements heard), M-9 (largest font scale and landscape: no clipped or overlapping content); record outcomes

**Checkpoint**: All three stories independently functional; robustness proven.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final gates and whole-feature verification.

- [x] T011 Run `./scripts/verify.sh` (clean build + full JVM unit suite + AGP lint, `abortOnError = true`) and confirm green — the Constitution V gate before the change set is presented as complete
- [ ] T012 Final cross-cutting verification: run the complete [quickstart.md](./quickstart.md) pass (M-1..M-9) and record the feature verdict against SC-001..SC-006; confirm via `git diff` that `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts`, and `gradle/libs.versions.toml` are untouched — zero new permissions and zero new dependencies (FR-010, SC-005, Constitution VII/VIII)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on T001; **blocks all user stories** (every story needs the holder contract).
- **User Story 1 (Phase 3)**: Depends on Phase 2 (T003).
- **User Story 2 (Phase 4)**: Depends on Phase 3 (the gesture must exist for its feedback to be observable). Semantics it verifies are already implemented and unit-proven in Phase 2.
- **User Story 3 (Phase 5)**: Depends on Phase 3 (same reason). Independent of Phase 4 — US2 and US3 may run in either order or in parallel.
- **Polish (Phase 6)**: Depends on all user stories being complete.

### Within Each User Story

- Manual-verification tasks (T005, T006, T010) run after that story's code tasks.
- T007 follows T006 (it consumes M-4/M-5 outcomes); T009 follows T008 (string resources).

### Parallel Opportunities

- T008 (strings.xml only) can run in parallel with any US2/US3 verification task — different files, no dependency.
- US2 (Phase 4) and US3 (Phase 5) are parallelizable as whole phases once US1 is complete.
- Everything else is sequential: the feature touches four tightly coupled files, and T002→T003 / T006→T007 / T008→T009 share files or outputs.

## Parallel Example: User Story 3

```bash
# After US1 is complete, these can proceed concurrently:
Task: "T006 [US2] Manual verification M-4/M-5"          (no file changes)
Task: "T008 [P] [US3] Add accessibility strings"         (strings.xml)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001)
2. Complete Phase 2: Foundational (T002, T003) — CRITICAL, blocks all stories
3. Complete Phase 3: User Story 1 (T004, T005)
4. **STOP and VALIDATE**: M-1..M-3 pass ⇒ the pull gesture reloads information (SC-001)

### Incremental Delivery

1. Setup + Foundational → holder contract green on the JVM
2. + US1 → working pull-to-refresh (MVP)
3. + US2 → trustworthy feedback and navigation safety
4. + US3 → robustness and screen-reader operability
5. Polish → full gates + quickstart verdict (SC-001..SC-006)

---

## Notes

- **Status 2026-10-06 (implementation session)**: T001–T004, T008, T009, T011 complete;
  full automated gate `./scripts/verify.sh` PASS (build + 54 unit tests + lint). T005,
  T006, T010, T012's quickstart rows, and T007's closeout are **blocked in the dev VM**:
  no device is attached and the SDK has no emulator/system images (and no KVM) — the
  same on-device situation as feature 002's T032. T012's automatable half is confirmed:
  `AndroidManifest.xml`, `app/build.gradle.kts`, `gradle/libs.versions.toml` unchanged
  (zero new permissions/dependencies). Remaining rows need any Android 15+ device or
  emulator per quickstart §0.
- [P] tasks = different files, no dependencies (only T008 qualifies)
- VR = [data-model.md](./data-model.md) §5 test targets; C/W = [contracts/refresh-interaction.md](./contracts/refresh-interaction.md) clauses; R = [research.md](./research.md) decisions; M = [quickstart.md](./quickstart.md) rows
- Verify tests fail before implementing (T002 red → T003 green)
- Commit after each task or logical group to the feature branch (`feature/003-refresh`); the VM never pushes (Constitution III)
- Stop at any checkpoint to validate the story independently
