---
description: "Task list for feature 005-shizuku-memory implementation"
---

# Tasks: Shizuku-Sourced Per-App Memory Information

**Input**: Design documents from `/specs/005-shizuku-memory/`

**Prerequisites**: [plan.md](./plan.md) (required), [spec.md](./spec.md) (required), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/shizuku-memory.md](./contracts/shizuku-memory.md), [contracts/details-guidance.md](./contracts/details-guidance.md), [quickstart.md](./quickstart.md)

**Tests**: Included — Constitution Principle V (non-negotiable local quality gates) plus [quickstart.md](./quickstart.md) §1 make the V-S unit coverage part of this feature's design ([data-model.md](./data-model.md) §9). For **new** pure classes the class and its test file are created in one task and driven green together (a test referencing a missing class cannot compile, so compile-red would prove nothing); **changed** classes (`DetailsStateHolder`, `DetailsStateHolderTest`) get their test extension in the same task as the change, red-pointed by the new assertions before the implementation lines land. The Shizuku-library surface itself has no JVM tests by design (Constitution IV seam split, R-10) — it is covered by the manual matrix.

**Organization**: Tasks grouped by user story. The dependency/AIDL/manifest integration, the pure domain functions, and the four reader seams sit in the **Foundational** phase because every story builds on them (004 put the shared shell there for the same reason). US1 then delivers the real figures end-to-end (MVP), US2 the guidance row with its actions, US3 the reactive degradation/recovery. Two Phase-1 refinements are baked in here and reflected back into the docs: a change event with a known state triggers **one coalescing refresh** (not a transition-specific one), and the UserService connection **self-invalidates on a failed call** (no listener wiring for the cache).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1..US3)
- Paths are repository-relative (single `app/` module, plan.md Structure Decision)

## Path Conventions

Single Android module: sources under `app/src/main/java/com/jkteknologies/androidanalyzer/`, AIDL under `app/src/main/aidl/com/jkteknologies/androidanalyzer/`, unit tests under `app/src/test/java/com/jkteknologies/androidanalyzer/`, resources under `app/src/main/res/values/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm a green baseline before any change (Constitution V).

- [X] T001 Run `./scripts/verify.sh` from the repository root and confirm the existing suite (build + JVM unit tests + AGP lint) is green before any feature-005 change; record the baseline result — **PASS 2026-10-07** (`PASS: build, testDebugUnitTest, lint`, BUILD SUCCESSFUL in 13s)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The dependency, manifest, AIDL, pure domain logic, and seam interfaces every story builds on (plan.md Structure; contracts [shizuku-memory.md](./contracts/shizuku-memory.md)).

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T002 Add the Shizuku client dependency in `gradle/libs.versions.toml` and `app/build.gradle.kts` per R-01/R-11: one version entry `shizuku = "13.1.5"`; library entries `shizuku-api` and `shizuku-provider` (group `dev.rikka.shizuku`, version.ref `shizuku`); `implementation(libs.shizuku.api)` + `implementation(libs.shizuku.provider)` in the app module with a comment citing the Constitution VII justification (the client protocol of the user-requested mechanism, MIT); switch on `buildFeatures { aidl = true }` (needed by T004). Verify with `./gradlew :app:assembleDebug` (dependency resolves over the network — first fetch) — **PASS 2026-10-07** (BUILD SUCCESSFUL in 1m 56s, 36 tasks)
- [X] T003 Declare the library-required provider in `app/src/main/AndroidManifest.xml` per R-05, exact form: `<provider android:name="rikka.shizuku.ShizukuProvider" android:authorities="${applicationId}.shizuku" android:multiprocess="false" android:enabled="true" android:exported="true" android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />` (attributes fixed by the library — `attachInfo()` throws otherwise), preceded by a comment mirroring the 002/004 permission notes: what the provider is (binder-push receiver), that it adds **no** permission (SC-006) and **no** `<queries>` (QUERY_ALL_PACKAGES already covers `moe.shizuku.privileged.api`), Constitution VIII
- [X] T004 Create the UserService AIDL pair in `app/src/main/aidl/com/jkteknologies/androidanalyzer/data/shizuku/` per [contracts/shizuku-memory.md](./contracts/shizuku-memory.md): `AppProcessMemory.aidl` (`parcelable AppProcessMemory;`) and `IAppMemoryService.aidl` (`List<AppProcessMemory> readRunningProcessMemory();` — the feature's entire privileged surface, FR-010). Verify `./gradlew :app:compileDebugKotlin` generates the Stub/Proxy — **PASS 2026-10-07** (BUILD SUCCESSFUL in 17s)
- [X] T005 [P] Create `app/src/main/java/com/jkteknologies/androidanalyzer/domain/ShizukuMemory.kt` and its test `app/src/test/java/com/jkteknologies/androidanalyzer/domain/ShizukuMemoryTest.kt` covering V-S1/V-S2 ([data-model.md](./data-model.md) §1–4): `ShizukuAccessState` enum exactly `NOT_INSTALLED, NOT_RUNNING, OUTDATED, AWAITING_AUTHORIZATION, AUTHORIZED`; `ProcessMemory(processName: String, pssBytes: Long)`; `aggregateProcessMemory(List<ProcessMemory>): Map<String, Long?>` with the attribution rule verbatim — "a process belongs to package `p` iff `processName == p || processName.startsWith(\"$p:\")`", entries summed per package, "a negative `pssBytes` on any entry of a package maps that package to `null`", unattributable processes dropped; `memoryBytesFor(snapshot: Map<String, Long>?, packageName: String): Long?` with the merge rule verbatim — "`snapshot == null` → `null`; absent key → `0L`; present `null` → `null`; present `v < 0` → `null`; present `v ≥ 0` → `v`". No Android imports (JVM-testable)
- [X] T006 Add the four seam interfaces to `app/src/main/java/com/jkteknologies/androidanalyzer/data/DeviceReaders.kt` per [contracts/shizuku-memory.md](./contracts/shizuku-memory.md) (depends on T005 for `ShizukuAccessState`): `fun interface AppMemoryReader { fun read(): Map<String, Long?>? }`, `fun interface ShizukuAccessStatus { fun state(): ShizukuAccessState }` ("never throws" — clause 3), `fun interface ShizukuAuthorizer { fun request() }`, `fun interface ShizukuChangeSource { fun listen(onChange: () -> Unit): () -> Unit }`, each with the contract's KDoc clauses and subject to the 002 common clauses (one-shot, null = unavailable, main-thread hostile, pure mapping) plus clause 5 (no intents/grant requests from the seam)

**Checkpoint**: dependency integrated, AIDL compiles, pure domain rules green, seams declared — user story implementation can begin.

---

## Phase 3: User Story 1 - Real Per-App Memory Figures via Shizuku (Priority: P1) 🎯 MVP

**Goal**: With Shizuku installed, running, and the analyzer authorized, every Details row shows the real memory its app occupies (summed PSS, zero for not-running); everything behaves exactly as 004 when not.

**Independent Test**: quickstart M-2 variant — authorize the analyzer inside Shizuku's own app (Allowed applications), pull-to-refresh on Details: figures appear and match `adb shell dumpsys meminfo <pkg>` TOTAL PSS within display rounding; an installed-but-not-running app shows 0; memory stays "Not available" in every unauthorized state (M-1).

### Implementation for User Story 1

- [X] T007 [P] [US1] Create `app/src/main/java/com/jkteknologies/androidanalyzer/data/shizuku/AppProcessMemory.kt` per [data-model.md](./data-model.md) §6: `data class AppProcessMemory(val processName: String, val pssBytes: Long) : Parcelable` with a **hand-written** `CREATOR` (no `kotlin-parcelize`, R-11) and a `toDomain()` mapping to `domain.ProcessMemory`
- [X] T008 [US1] Create `app/src/main/java/com/jkteknologies/androidanalyzer/data/shizuku/AppMemoryServiceImpl.kt` (depends on T004 + T007): implements the generated `IAppMemoryService.Stub`, v13 `Context` constructor (the R-03 server floor); one `readRunningProcessMemory()` = `context.getSystemService(ActivityManager::class.java).getRunningAppProcesses()` filtered to `pid > 0` → `getProcessMemoryInfo(pids)` → one `AppProcessMemory(processName, totalPss * 1024L)` per process (KB → bytes happens here — the domain never sees KB, contract clause 2); failures throw to the caller (the analyzer side maps them to the null pass). Add the `ponytail:`/R-02 note that this class runs inside the Shizuku server process as shell/root and must stay to exactly this read-only surface (FR-010)
- [X] T009 [P] [US1] Create `app/src/main/java/com/jkteknologies/androidanalyzer/data/shizuku/ShizukuAccess.kt` (parallel-safe with T007/T008 — no dependency between them): `const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"` plus the three `rikka.shizuku.*`-importing seam implementations per R-03/R-04 — the status ladder verbatim: `getPackageInfo(SHIZUKU_PACKAGE, 0)` `NameNotFoundException` → `NOT_INSTALLED`; `!Shizuku.pingBinder()` → `NOT_RUNNING`; `Shizuku.isPreV11() || Shizuku.getVersion() < 13` → `OUTDATED`; `Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED` → `AWAITING_AUTHORIZATION`; else `AUTHORIZED`; **every** library throw collapses to `NOT_RUNNING`, the method never throws; the authorizer: `Shizuku.requestPermission(1)` (fire-and-forget, Shizuku's own dialog, FR-005) with `IllegalStateException` swallowed; the change source: registers `addBinderReceivedListenerSticky` + `addBinderDeadListener` + `addRequestPermissionResultListener`, each → `onChange` (library already dispatches on main), returned lambda removes all three (clause 7)
- [X] T010 [US1] Create `app/src/main/java/com/jkteknologies/androidanalyzer/data/shizuku/ShizukuMemorySource.kt` (depends on T004/T008): implements `AppMemoryReader` per R-07 and contract clause 6 — lazy `Shizuku.bindUserService(UserServiceArgs(ComponentName(applicationId, AppMemoryServiceImpl::class.java.name)).version(1), connection)` awaited on a latch ≤ 5 s; the connection is cached; `read()` (background executor only) calls `readRunningProcessMemory()`, maps to domain, and returns `aggregateProcessMemory(...)`; **any `Throwable` → drop the cached connection and return `null`** (self-invalidation — rebind happens on the next read); no unbind on screen disposal, no in-flight watchdog (`ponytail:` ceiling per R-07)
- [X] T011 [US1] Extend `app/src/main/java/com/jkteknologies/androidanalyzer/data/AndroidDeviceReaders.kt` per contract clause 1 (depends on T005/T006/T009/T010): construct and expose `shizukuAccessStatus` and `appMemoryReader`; in `installedAppReader`, replace the `memoryBytes = null` placeholder (data-model §5) with — `ShizukuAccessStatus.state()` read once per pass; **only when `AUTHORIZED`** one `AppMemoryReader.read()`; per app `memoryBytes = memoryBytesFor(snapshot, packageName)`; in every other state no privileged read is attempted and every `memoryBytes` is `null`
- [X] T012 [US1] Extend `app/src/main/java/com/jkteknologies/androidanalyzer/ui/details/DetailsStateHolder.kt`, its test `app/src/test/java/com/jkteknologies/androidanalyzer/ui/details/DetailsStateHolderTest.kt`, and wire `app/src/main/java/com/jkteknologies/androidanalyzer/MainActivity.kt` (depends on T011): constructor gains `shizukuAccessStatus`, `shizukuAuthorizer`, `shizukuChangeSource` (authorizer/change-source stored for US2/US3; only the status is used this phase); each pass reads `state()` on the executor and posts `shizukuAccess: ShizukuAccessState?` **in the same epoch-guarded post** as `inventory` + `usageAccessGranted` (data-model §8 — guidance and figures cannot disagree); V-S3 tests via fakes: an `AUTHORIZED` fixture with memory values (incl. zero for an absent package) posts state + values together; a non-authorized fixture posts that state with all-`null` memory; one post per pass. `MainActivity` passes the T009/T010 implementations from `AndroidDeviceReaders`

**Checkpoint**: figures flow end-to-end (M-2) — MVP delivered (SC-001 holds against `dumpsys meminfo` ground truth).

---

## Phase 4: User Story 2 - Guided Shizuku Setup and Authorization (Priority: P2)

**Goal**: Every Shizuku state is named in plain language on the Details screen with its matching next action; authorization happens only through Shizuku's own dialog.

**Independent Test**: quickstart M-1 (no Shizuku installed: guidance + everything else works), M-4 (not-running: "Open Shizuku" opens it; return shows the new state), M-5 (outdated: update guidance, optional — needs an old Shizuku build).

### Implementation for User Story 2

- [X] T013 [P] [US2] Add the guidance strings to `app/src/main/res/values/strings.xml` per R-09 / [contracts/details-guidance.md](./contracts/details-guidance.md) §S: `shizuku_hint_not_installed` (names Shizuku, free on F-Droid, the install → start → allow steps), `shizuku_hint_outdated`, `shizuku_hint_not_running`, `shizuku_hint_awaiting`, `shizuku_hint_authorized` (one quiet line, e.g. "Per-app memory via Shizuku"), labels `shizuku_open` ("Open Shizuku") and `shizuku_allow` ("Allow access"). English only (002 formatting assumption). (Parallel-safe with T015 — different files)
- [X] T014 [US2] Add `ShizukuGuidanceRow` to `app/src/main/java/com/jkteknologies/androidanalyzer/ui/details/DetailsScreen.kt` per [contracts/details-guidance.md](./contracts/details-guidance.md) §G (depends on T013): renders below the usage-access hint and above the filter once `holder.shizukuAccess != null`, absent before the first pass; state mapping exactly §G2 — `NOT_INSTALLED`/`OUTDATED` text only, `NOT_RUNNING` text + `shizuku_open` button firing `PackageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)`, `AWAITING_AUTHORIZATION` text + `shizuku_allow` button calling `holder.requestAuthorization()`, `AUTHORIZED` the quiet line, no button; the 004 `GrantHintRow` layout (text `weight(1f)` + optional button, sp-scaled, no fixed heights); polite live region on the guidance text (§G5); the row never blocks the list, filter, or figures (§G6)
- [X] T015 [US2] Add `requestAuthorization()` to `DetailsStateHolder.kt` + the V-S6 tests in `DetailsStateHolderTest.kt` (depends on T012's seams): delegates to `ShizukuAuthorizer.request()`; a throwing authorizer is swallowed (binder died between check and tap — the change source or next pass re-reads the state); the dialog is Shizuku's own — no credential, no "remember" path (FR-005)

**Checkpoint**: every state names itself and advances one step (US2-1..4 hold; SC-002/SC-003's in-app path ≤ two actions).

---

## Phase 5: User Story 3 - Graceful Degradation and Recovery While In Use (Priority: P3)

**Goal**: State changes while the analyzer is open — Shizuku stopped, authorization revoked or granted — reflect without a restart; a grant re-reads by itself; death mid-refresh never crashes or shows a wrong number.

**Independent Test**: quickstart M-6 (3+ revoke/stop → restart/re-grant cycles with the analyzer open: no crash, figures revert and return, guidance always current) and M-7 (force-stop Shizuku mid-pull-to-refresh: pass completes, unread figures "Not available", no error screen).

### Implementation for User Story 3

- [X] T016 [US3] Wire the change source into `DetailsStateHolder.kt` + the V-S4/V-S5 tests in `DetailsStateHolderTest.kt` (depends on T012; single task — one file pair): subscribe at construction via `ShizukuChangeSource.listen { poster.post(::onShizukuChanged) }`, unsubscribe in `shutdown()`; `onShizukuChanged()` rule per data-model §8 — **once a state has been posted, every change event triggers exactly one coalescing `refresh()`** (the pass re-reads state + figures together, so guidance and figures can never disagree; absorption/epoch guard handles events landing mid-pass); **the initial sticky delivery finds no posted state and triggers nothing** (V-S5 — no double pass at startup); tests: fake change source firing after a known state → exactly one absorbed refresh (fake reader call count), firing before any post → zero refreshes, repeated identical fires → idempotent re-reads, dispose → unsubscribe asserted

**Checkpoint**: revoke/re-grant cycles hold without restarts (SC-004); FR-007's both clauses verified.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: The Constitution V gate and the manual handoff.

- [X] T017 Run `./scripts/verify.sh` (clean build + full JVM unit suite + AGP lint, `abortOnError = true`) and confirm green — the Constitution V gate before the change set is presented as complete; record the result — **PASS 2026-10-07** (`PASS: build, testDebugUnitTest, lint`; 110 unit tests, 0 failures)
- [ ] T018 Execute the quickstart manual matrix M-1..M-9 on a device or emulator with Shizuku set up per [quickstart.md](./quickstart.md) §0 (or hand it to the host operator if the VM path is impractical — 004 precedent); record per-row results here — **HANDED TO HOST 2026-10-07**: the VM has no Shizuku setup or test device; all automated gates green, the Shizuku-runtime behaviors (provider handshake, dialog, binder death, PSS ground truth) await M-1..M-9 on host hardware

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: no dependencies — start immediately
- **Foundational (Phase 2)**: T002 → T003 (provider class needs the dependency on the classpath for lint); T004 after T002 (AIDL needs `aidl = true`); T005 independent; T006 after T005 (imports `ShizukuAccessState`) — **blocks all user stories**
- **User Stories (Phases 3–5)**: strictly in priority order — US2's row reads the state US1's pass posts; US3's refresh rule rides US1's holder seams. US1 internal order: T007 → T008 → T010; T009 parallel with T007/T008; T011 after T005/T006/T009/T010; T012 last
- **Polish (Phase 6)**: after all stories

### User Story Dependencies

- **US1 (P1)**: after Foundational — no story dependencies (MVP)
- **US2 (P2)**: after US1 (renders `holder.shizukuAccess`; calls `requestAuthorization()` on US1's seams)
- **US3 (P3)**: after US1 (subscription + refresh ride the holder's seams and pass machinery); independent of US2

### Parallel Opportunities

- Phase 2: T005 ∥ T003/T004 (different files, no mutual dependency)
- Phase 3: T007 ∥ T009, T008 ∥ T009 (different files; T008 needs T007, T010 needs T008+T009)
- Phase 4: T013 ∥ T015 (different files); T014 after T013 (uses the strings)
- All manual matrix rows are independent of each other

---

## Parallel Example: User Story 1

```bash
# Launch together (different files, no dependency between them):
Task: "T007 AppProcessMemory parcelable in app/src/main/java/com/jkteknologies/androidanalyzer/data/shizuku/AppProcessMemory.kt"
Task: "T009 ShizukuAccess seam implementations in app/src/main/java/com/jkteknologies/androidanalyzer/data/shizuku/ShizukuAccess.kt"

# Then sequentially: T008 (needs T007) → T010 (needs T008+T009) → T011 → T012
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (baseline green)
2. Complete Phase 2: Foundational (dependency + AIDL + domain + seams)
3. Complete Phase 3: US1 — figures flow end-to-end
4. **STOP and VALIDATE**: quickstart M-2 against `dumpsys meminfo` ground truth

### Incremental Delivery

1. Setup + Foundational → seam-level green suite
2. US1 → figures (MVP!) → validate M-2/M-3
3. US2 → guidance + actions → validate M-1/M-4 (M-5 optional)
4. US3 → reactivity → validate M-6/M-7
5. Polish → full gate + remaining matrix rows (M-8/M-9)

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps tasks to spec user stories for traceability
- Verify with `./scripts/verify.sh` after every task that touches code (Constitution V)
- Commit after each task or logical group
- Stop at any checkpoint to validate the story independently
- The Shizuku-library surface (provider handshake, dialog, binder death) is manual-matrix territory — never mock it in JVM tests, fake the seams instead (R-10)
