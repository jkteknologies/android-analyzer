# Research: Shizuku-Sourced Per-App Memory Information

**Feature**: `005-shizuku-memory` | **Date**: 2026-10-07 | **Input**: [spec.md](./spec.md)

Resolves the open technical questions behind the spec's assumptions: how to integrate
Shizuku (dependency, manifest surface, permission model), how to actually read another
app's memory through it, how the four-state guidance maps onto the library's API, and
where every piece sits in the app's existing reader/state-holder architecture. Evidence
sources: the Shizuku-API sources and official demo on GitHub (v13.1.5), the Shizuku app
repo (v13.6.0, Apache-2.0), AOSP `frameworks/base` (permission gates verified server-side
in `ActivityManagerService` / `packages/Shell/AndroidManifest.xml`), real open-source
integrations (App Manager, Inure, TaskManager, RootlessJamesDSP), Maven Central metadata,
and the existing 001–004 code and artifacts. Each entry records the decision, rationale,
and alternatives considered.

Consolidated verdict up front:

- **One dependency family**: `dev.rikka.shizuku:api` + `:provider` 13.1.5 (MIT) — the
  client protocol library for the mechanism the user explicitly requested (R-01).
- **Memory comes from a UserService** executing the *public*
  `ActivityManager.getRunningAppProcesses()` + `getProcessMemoryInfo()` inside Shizuku's
  server process with shell identity — zero hidden-API stubs, zero dumpsys parsing (R-02).
- **Server v13+ required**: below that, the guidance asks for a Shizuku update (the
  spec's Outdated edge case, R-03).
- **Manifest grows one provider declaration**; the permission list is byte-for-byte
  unchanged (SC-006) and no `<queries>` is needed (R-05).

---

## R-01 · Dependency: the Shizuku client library, versioned and licensed

**Decision**: add `dev.rikka.shizuku:api:13.1.5` and `dev.rikka.shizuku:provider:13.1.5`
(Maven Central; pinned in `gradle/libs.versions.toml`, one version entry for both — the
library family versions together). The client library is **MIT** (verified in the POM
and repo `LICENSE`), so it is F-Droid-compatible and adds no copyleft obligation to the
app. The Shizuku *app/server* is Apache-2.0 (with trademark-ish reservations on name and
icon, which we do not reuse) and is itself distributed on F-Droid — consistent with the
spec's assumption that users obtain it there.

**Rationale**:

- The user chose Shizuku by name (spec FR-003); the client library **is** its protocol —
  binder push (`ShizukuProvider`), attachment handshake (v11/v13 protocol, permission
  caching), `ShizukuBinderWrapper`/transact forwarding, and the permission-dialog flow.
  Re-implementing that by hand would duplicate the library with none of its maintenance.
- 13.1.5 is the latest stable as of 2026-10 (no newer release exists; verified against
  `maven-metadata.xml`) and requires server ≥ v11, with v13 adding the attach-time
  permission cache and the `Context` UserService constructor we rely on (R-03).
- The library is small (api + aidl + provider), does no networking, and requests nothing
  at install time — Constitution VII's "substantial, non-trivial functionality" bar is
  met by definition here.

**Alternatives considered**:

- *Hand-rolled binder protocol*: strictly worse (see above). Rejected.
- *`dev.rikka.tools.refine` + hidden-API stub module*: solves a problem the UserService
  route makes disappear (R-02); it would add a Gradle plugin for nothing. Rejected.
- *Older 12.x client line*: forfeits the v13 server features we use; no reason. Rejected.

## R-02 · Reading per-app memory: UserService with public SDK calls

**Decision**: per-app PSS is read via a **Shizuku UserService**. We define
`IAppMemoryService` (AIDL, one method returning `List<AppProcessMemory>`, one parcelable
with `processName` + `pssBytes`) and an implementation class `AppMemoryServiceImpl` that
Shizuku loads and runs **inside its server process** (uid 2000 shell, or root). There, it
calls the **public SDK** `context.getSystemService(ActivityManager::class.java)`
`.getRunningAppProcesses()` and `.getProcessMemoryInfo(pids)`, mapping each live pid's
`Debug.MemoryInfo.totalPss` (KB → bytes). The analyzer binds the service
(`Shizuku.bindUserService`), calls the one method — one batched privileged round trip
per refresh — and aggregates per package app-side (pure domain function, R-06).

**Rationale**:

- The restriction on seeing other apps is enforced **server-side by calling uid**, not by
  hiding the API: `AMS.getRunningAppProcesses()`/`getProcessMemoryInfo()` gate on
  `REAL_GET_TASKS`, and `com.android.shell` holds it (AOSP `packages/Shell/
  AndroidManifest.xml`). Code running in the Shizuku server process *is* that uid, so
  the public SDK methods work unmodified — the Shizuku README's own model ("the
  privilege is determined by Android permissions").
- PSS cannot be read cheaper: as shell, `/proc/<pid>/smaps*` is SELinux-blocked; only
  system_server's `getProcessMemoryInfo` machinery (or `dumpsys meminfo`, which wraps
  it) produces PSS. RSS from `/proc/<pid>/status` would be readable directly but is a
  *different number* than the device's own memory report — violating SC-001's
  "equals the device's own report" and the never-display-a-wrong-number policy.
- Zero vendored hidden API. The in-process alternative — `SystemServiceHelper.
  getSystemService("activity")` through a `ShizukuBinderWrapper` — requires vendoring a
  compile-time copy of `android.app.IActivityManager` (~650 lines in App Manager's
  trimmed form, 1,041-line AIDL with 64 imports in AOSP), because AIDL transaction codes
  are assigned by method order and **cannot be trimmed**. Every Android release that
  inserts methods shifts the codes; a mismatch calls the *wrong* method through shell
  privilege. That is a standing correctness/security liability for zero user-visible
  gain over the UserService. The Shizuku documentation itself positions UserService as
  the replacement for command-running and the stub-free path ("UserService can replace
  newProcess in all cases").
- One UserService call returns every process's memory in one shot — matching the
  one-pass architecture of every existing reader (clause "one-shot" in
  contracts/device-readers.md) and the SC-005 budget (R-07).

**Alternatives considered**:

- *Vendored `IActivityManager` + `ShizukuBinderWrapper`*: works today (App Manager ships
  it), but see the transaction-code fragility above; also drags AOSP-derived code into
  the repo that must track every Android release. Rejected for a 15-Android-window app.
- *`dumpsys meminfo --checkin` parsing*: `Shizuku.newProcess` is deprecated and **private**
  in 13.1.5 (planned removal in API 14; TaskManager reaches it by reflection — fragile);
  and dumpsys output is an explicitly unstable contract across Android versions and OEM
  builds. Rejected as primary path.
- *`procstats` (`IProcessStats`)*: reports time-averaged per-app memory, not the current
  occupancy the spec demands ("currently occupies"). Wrong semantics. Rejected.
- *Raw RSS from `/proc`*: wrong number (see above). Rejected.

## R-03 · Shizuku state machine: detection, and the v13 floor

**Decision**: a domain enum `ShizukuAccessState { NOT_INSTALLED, NOT_RUNNING, OUTDATED,
AWAITING_AUTHORIZATION, AUTHORIZED }` computed by one Android-side check:

1. `PackageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)` throws `NameNotFoundException` →
   `NOT_INSTALLED` (visibility guaranteed by 002's `QUERY_ALL_PACKAGES`, R-05).
2. `!Shizuku.pingBinder()` → `NOT_RUNNING` (the routine state after a reboot).
3. `Shizuku.isPreV11() || Shizuku.getVersion() < 13` → `OUTDATED` — guidance asks the
   user to update Shizuku (spec Edge Cases: "where the version can be detected"; the
   spec's four FR-004 states plus this edge-case-armed variant).
4. `Shizuku.checkSelfPermission() != PERMISSION_GRANTED` → `AWAITING_AUTHORIZATION`.
5. Otherwise `AUTHORIZED`.

Every step is wrapped: any `IllegalStateException`/`RuntimeException` from the library's
`requireService()` (binder vanished mid-check) maps to `NOT_RUNNING`. The check runs on
the background executor (it may do a synchronous IPC on pre-13 servers; on v13+ the
permission state is pushed at attach and cached) and the resulting state is posted to the
UI by the read pass — the same pattern as `usageAccessGranted` (004 data-model §5).

**Rationale**:

- The v13 floor is honest and cheap: the UserService route wants the v13 `Context`
  constructor (pre-13 servers instantiate the no-arg constructor, and we need a Context
  to reach `ActivityManager`); v13+ servers also push cached permission state (no sync
  IPC per check). Current Shizuku is 13.6.0 (May 2026) — the floor excludes only
  years-old servers, and the guidance tells their users exactly what to do.
- The five states map 1:1 onto the spec's guidance matrix (FR-004) plus its own edge
  case; "Outdated" shares the Not-running presentation style (text + implied action:
  update instead of start).
- `pingBinder()`/`isPreV11()`/`getBinder()` are the only safe pre-binder calls in the
  library — the ordering above never touches `requireService()` before the binder is
  confirmed alive.

**Alternatives considered**:

- *Support servers v11–12 (no-arg UserService constructor + static ActivityManager
  access)*: there is no public static route to an `ActivityManager`; we would need
  hidden `ActivityThread` reflection — exactly what this feature avoids. Rejected.
- *Fold Outdated into Not_running ("not running or too old")*: one fewer string, but the
  guidance then tells a user whose Shizuku *is* running to "start it" — a wrong
  instruction. Rejected.
- *Prompt-to-update when `getVersion() < getLatestServiceVersion()` (i.e. < 13 while
  running 13.0.x)*: works fine, nagging for nothing. Rejected (YAGNI).

## R-04 · Permission and lifecycle wiring: request through Shizuku only

**Decision**: three seams over the library's static API (all in `data/shizuku/
ShizukuAccess.kt`, the only file family importing `rikka.shizuku.*`):

- `ShizukuAuthorizer.request()` → `Shizuku.requestPermission(code)` — fire-and-forget
  IPC; Shizuku shows **its own** dialog; we store nothing (FR-005). `IllegalStateException`
  (binder died between state check and tap) is swallowed — the change source refreshes
  the state.
- `ShizukuChangeSource.listen(onChanged): () -> Unit` → registers
  `addBinderReceivedListenerSticky`, `addBinderDeadListener`, and
  `addRequestPermissionResultListener` (all dispatched on the main thread by the
  library); any of them fires `onChanged`; the returned lambda removes all three.
  Request results are not interpreted beyond "something changed" — the holder re-reads
  the actual state from `ShizukuAccessStatus`, so a denial simply leaves the state at
  `AWAITING_AUTHORIZATION` (grant → `AUTHORIZED` + auto re-read, FR-007).
- `ShizukuAccessStatus.state()` → the R-03 ladder.

The holder subscribes at construction (posting callbacks through the existing
`ResultPoster`) and unsubscribes in `shutdown()` — listeners live exactly as long as the
Details screen's state holder, never longer (Constitution IX).

**Rationale**:

- Shizuku's permission model is runtime-permission-shaped but self-implemented
  (`checkSelfPermission()` no-arg — grants are per-app-package); the canonical demo flow
  is exactly check → request → listen. Anything else would fight the library.
- Collapsing all three listeners into one `onChanged` keeps the seam a one-method
  interface (JVM-fakeable with a lambda) and keeps *interpretation* of what changed in
  the holder, where the epoch/coalescing machinery already lives. Listeners may fire
  repeatedly (Shizuku restarts) — the holder's dedupe guard (only state *transitions*
  act, R-08) makes that harmless.
- In-flight privileged calls fail with `DeadObjectException`/`RuntimeException` when the
  binder dies; every privileged call site catches `Throwable` → `null` → the honest
  not-available indication, and the dead-listener corrects the guidance immediately.

**Alternatives considered**:

- *Interpret `grantResult` directly and skip the state re-check*: trusts a pushed event
  over observed state — two sources of truth for one flag. Rejected.
- *Poll the state on a timer*: background work for events the library already pushes
  (Constitution IX). Rejected.

## R-05 · Manifest surface: one provider, no permission, no queries

**Decision**: the manifest gains exactly the library-required declaration

```xml
<provider
    android:name="rikka.shizuku.ShizukuProvider"
    android:authorities="${applicationId}.shizuku"
    android:multiprocess="false"
    android:enabled="true"
    android:exported="true"
    android:permission="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

and nothing else. No `<uses-permission>` is added (SC-006 holds: the install-time list
stays `QUERY_ALL_PACKAGES` + `PACKAGE_USAGE_STATS`). No `<queries>` element is added:
the app already holds `QUERY_ALL_PACKAGES` (002), which subsumes package visibility for
`moe.shizuku.privileged.api` — the only things we do with that package are the
installed-check and `getLaunchIntentForPackage()` for the "Open Shizuku" action.

**Rationale**:

- The provider is how the Shizuku server pushes its binder into our process
  (`attachInfo()` throws at runtime if `multiprocess` or `exported` are wrong, so the
  attributes are fixed by the library, not chosen by us). The `android:permission`
  attribute is a **guard on callers of our provider** (signature-level, held by shell),
  not a permission we request — it keeps anyone but Shizuku's server from reaching it.
- The binder-push flow itself needs no package visibility (the official demo omits
  `<queries>`); visibility is only needed for *our* PackageManager calls, which
  `QUERY_ALL_PACKAGES` already covers. One fewer manifest line and one fewer reviewed
  surface.
- Sui (the root-only Shizuku variant) is auto-initialized by `ShizukuProvider` since
  library 12.1.0 — nothing to declare for it.

**Alternatives considered**:

- *Also declare `<queries><package android:name="moe.shizuku.privileged.api"/></queries>`*
  (the pattern used by RootlessJamesDSP/AmbientMusicMod, which lack
  `QUERY_ALL_PACKAGES`): redundant here. Rejected.
- *Opt out of automatic Sui initialization*: no reason — it is free parity. Rejected.

## R-06 · Figure semantics: summed PSS, honest zero, honest null

**Decision**: the domain owns two pure functions (JVM-tested, V-S1/V-S2):

- `aggregateProcessMemory(processes: List<ProcessMemory>): Map<String, Long?>` — sums
  `pssBytes` per owning package: a process belongs to package `p` when its name equals
  `p` or starts with `"p:"` (the `pkg:service` convention); processes whose name
  attributes to no listed package are ignored app-side (they cannot be attributed
  honestly).
- `memoryBytesFor(snapshot: Map<String, Long?>?, packageName: String): Long?` — the one
  merge rule for `InstalledApp.memoryBytes`: `snapshot == null` (pass unavailable) →
  `null`; package absent → `0` (installed, not running — a truthful value, spec
  assumption); package present with a negative value → `null` (validation failure, FR-008);
  package present with `null` → `null` (per-app read failure, US1-6's slot at the seam);
  otherwise the value.

The `InstalledAppReader` pass applies `memoryBytesFor` only when the same pass's state
check said `AUTHORIZED` (mirroring 004's usage-access coupling, V-D6's analog V-S3);
`totalPss` is converted KB → bytes inside the UserService impl (`* 1024L`) so the domain
never sees units.

**Rationale**:

- PSS summed over an app's processes is exactly what the device's own report shows for
  that app (`dumpsys meminfo <pkg>`'s TOTAL PSS) — the SC-001 ground truth.
- The absent → zero rule is what makes "not running" distinguishable from "couldn't
  read"; conflating them would either display a wrong 0 or a wrong not-available.
- The nullable map value costs one nullable type and keeps US1-6 (partial failure
  displays per-app not-available) testable at the seam even though today's UserService
  implementation cannot produce it (a vanished pid is simply absent from the list —
  honestly zero). The seam permits, the tests pin, the impl currently never emits.

**Alternatives considered**:

- *Store RSS instead of PSS*: wrong number vs the device's report (R-02). Rejected.
- *Report only the main process*: wrong for every multi-process app (spec FR-002).
  Rejected.
- *Merge by uid instead of process-name prefix*: `getRunningAppProcesses` gives
  `processName`/`uid`/`pid`; uid works but adds a uid→packages mapping step the prefix
  rule does not need for the standard single-package-per-uid world of modern Android.
  Rejected (simplicity; prefix rule is what real integrations use).

## R-07 · UserService lifecycle: lazy bind, cached connection, rebind on death

**Decision**: `ShizukuMemorySource` (the `AppMemoryReader` impl) binds
`IAppMemoryService` lazily on the first authorized read and **keeps the connection**
until a call fails — any `Throwable` drops the cached connection and the next read
rebinds — or our process ends. `read()` on the background executor: ensure bound (await the
`ServiceConnection` callback up to 5 s on a latch), call the one AIDL method, map to
domain `ProcessMemory` list, return `null` on any failure (timeout, `RuntimeException`,
dead binder). No watchdog on the call itself, no unbind on Details disposal.

**Rationale**:

- A UserService process is spawned server-side on bind — rebinding per refresh would
  fork a JVM per pull-down (battery + latency, Constitution IX). Caching while healthy
  makes every refresh after the first one a single binder round trip.
- Not unbinding on `shutdown()`: the holder is disposed on every tab switch (existing
  003 semantics) — unbinding there would respawn the service on each tab return. The
  server owns the service process's lifetime against our process's binder link; our
  process death reaps it. `ShizukuBinderWrapper`'s documented property (resolves the
  service at call time) plus the dead-listener invalidation covers Shizuku restarts.
- The 5 s bind wait bounds the cold-start case inside SC-005's 10 s budget; a timeout
  yields this-pass `null` (not-available) and the next refresh retries — degradation,
  never a wrong number.
- `getProcessMemoryInfo` is the throttled, smaps-reading call — one batched invocation
  per pass (all pids, no chunking) is what `dumpsys meminfo` itself does and stays well
  inside the budget on 200-process devices.
- `ponytail:` ceiling: no in-flight-call watchdog — a hung binder call blocks that
  executor pass until the process dies; Shizuku/AMS deaths surface as
  `DeadObjectException` and are handled. Upgrade path: a read timeout wrapper around
  the latch+call if real devices ever show hangs.

**Alternatives considered**:

- *Bind per read, unbind after*: simplest lifecycle, worst resource behavior (see
  above). Rejected.
- *Bind eagerly at app start*: privileged work before the user asks for any figure —
  rejected (Constitution IX: nothing without a user-visible reason).
- *Chunk `getProcessMemoryInfo` into batches*: AMS takes the full array; chunking adds
  code for a problem the stock `dumpsys` path doesn't have. Rejected until measured.

## R-08 · State holder integration: one posted flag, transition-driven auto refresh

**Decision**: `DetailsStateHolder` gains three constructor seams
(`ShizukuAccessStatus`, `ShizukuAuthorizer`, `ShizukuChangeSource`) and:

- `shizukuAccess: ShizukuAccessState?` — posted by every read pass next to
  `usageAccessGranted` (null until the first pass; the guidance row renders once known).
- Each pass reads the state first (state + inventory + memory in one epoch-guarded
  post — list, guidance, and figures cannot disagree).
- `onShizukuChanged()` (the change-source callback, main-thread posted): **once a state
  has been posted, every change event triggers one coalescing `refresh()`** (no reset;
  an in-flight pass absorbs the event). The refresh's pass re-reads the state and the
  figures together on the executor, so guidance and figures can never disagree — and a
  grant re-reads by itself (FR-007). The sticky binder-received callback that fires
  immediately at subscription finds no posted state and therefore triggers nothing — no
  double pass at startup (V-S5).
- `requestAuthorization()` → `ShizukuAuthorizer.request()` (UI button); any throw is
  swallowed — the state re-reads via the change source.
- `shutdown()` additionally unsubscribes the change source.

**Rationale**:

- FR-007's two clauses map exactly: state changes reflect "at the latest by the next
  refresh" (every pass re-reads the state; the dead/received listeners make it
  immediate), and a new grant re-reads by itself (the permission-result listener fires
  `onShizukuChanged` → refresh — no restart, no manual gesture; SC-003).
- Known-state gating is the cheapest correct dedupe: repeated listener fires (Shizuku
  restarts, sticky re-delivery) land in the coalescing guard, and a redundant refresh
  is one idempotent background pass — cheaper than transition-detection bookkeeping
  (which would need its own state re-read before deciding whether to refresh).
- The state-check must not run on the UI thread (`pingBinder` is a binder call;
  pre-13 servers do a sync IPC) — so the callback never reads the state itself; the
  refresh pass does, on the executor.

**Alternatives considered**:

- *Re-check the state synchronously inside the listener callback*: puts a binder call
  on the main thread. Rejected.
- *Expose the Shizuku state as a separate observable steam beside the holder*: a second
  source of truth for guidance-vs-figures consistency (004's V-D6 lesson). Rejected.

## R-09 · Guidance UI and strings

**Decision**: one `ShizukuGuidanceRow` on the Details screen (below the usage-access
row, above the filter), rendering from `holder.shizukuAccess`:

| State | Text (new string resource) | Action |
|---|---|---|
| `null` (not yet known) | — (row hidden) | — |
| `NOT_INSTALLED` | `shizuku_hint_not_installed` — names Shizuku, that it is free on F-Droid, and the three steps (install, start, allow) | none |
| `OUTDATED` | `shizuku_hint_outdated` — update Shizuku, then return | none |
| `NOT_RUNNING` | `shizuku_hint_not_running` | `shizuku_open` → `getLaunchIntentForPackage(SHIZUKU_PACKAGE)` |
| `AWAITING_AUTHORIZATION` | `shizuku_hint_awaiting` | `shizuku_allow` → `holder.requestAuthorization()` |
| `AUTHORIZED` | `shizuku_hint_authorized` — one quiet line ("Per-app memory via Shizuku") | none |

The row reuses the 004 `GrantHintRow` layout (text + optional button, sp-scaled, no
fixed heights) and adds a polite live region on the text so state changes are announced
(US2-5/US3-1 for TalkBack users). The memory slot's rendering is untouched —
`memoryBytes` already renders value-or-`figure_unavailable` (004 D-3), and a zero
renders as a normal value.

**Rationale**: every state names itself in plain language (FR-004, US2-5); the actions
are exactly the spec's matching next steps (US2-1..4); the Authorized line is the spec's
"present the state" clause made visible without a banner-sized cost. The usage-access
row and the Shizuku row stay separate rows — different problems, different grants.

**Alternatives considered**:

- *Hide the row when authorized*: saves one quiet line but leaves FR-004's "Authorized"
  presentation implicit. Rejected (spec letter is cheap to satisfy).
- *A Settings-screen status section*: the spec scoped guidance to Details (spec
  assumption). Rejected.

## R-10 · Testing strategy

**Decision**: all new logic is JVM-testable through the four seams — no test touches
`rikka.shizuku.*`:

- `domain/ShizukuMemoryTest.kt` (V-S1/V-S2): aggregation (multi-process summing,
  `pkg:` prefix attribution, unattributable processes ignored) and `memoryBytesFor`
  (null-pass, absent→0, present value, present-null→null, negative→null).
- `DetailsStateHolderTest` extended (V-S3..V-S6): authorized pass couples state + memory
  values (+ zero for absent packages); non-authorized pass couples state + all-null
  memory; change-source transition into AUTHORIZED triggers exactly one absorbed
  refresh; the initial sticky callback does not; `requestAuthorization` delegates to the
  fake authorizer and survives a throwing one.
- Everything Shizuku-runtime (provider handshake, real dialog, binder death mid-call,
  UserService spawn, PSS values on silicon) is the quickstart manual matrix M-1..M-9
  (Constitution IV accepts this split; it is the same split 004 used for
  `StorageStatsManager`).

No new test infrastructure; no Robolectric (nothing in the new surface needs the
Android framework on the JVM — that is what the seams are for).

## R-11 · Dependency and permission ledger (Constitution VII/VIII)

**Decision**: dependency delta = `dev.rikka.shizuku:api:13.1.5` + `:provider:13.1.5`
(MIT, R-01), vendored AIDL of our own (`IAppMemoryService`, `AppProcessMemory`), and
nothing else — no `kotlin-parcelize` (hand-written `CREATOR`), no hidden-API stub
module, no `rikka.tools.refine`. Manifest delta = the `ShizukuProvider` declaration
(R-05). Permission delta = **none**; the reviewed install-time surface stays
`QUERY_ALL_PACKAGES` (002) + `PACKAGE_USAGE_STATS` (004), and the new privileged access
is governed by the user's in-Shizuku grant — requested only through Shizuku's own
dialog (FR-005/FR-011).

---

## Post-design Constitution re-check (after Phase 1)

Re-evaluated 2026-10-07 against the Phase 1 artifacts
([data-model.md](./data-model.md),
[contracts/shizuku-memory.md](./contracts/shizuku-memory.md),
[contracts/details-guidance.md](./contracts/details-guidance.md),
[quickstart.md](./quickstart.md)):

- All Core Principles I–IX: **PASS** — verdicts unchanged from the plan.md gate table.
  The design's only dependency is the Shizuku client family (R-01, VII), the permission
  surface is untouched (R-05, VIII), privileged reads are batched, read-only, and
  listener-driven with zero background work (R-04/R-07, IX), and every Shizuku-touching
  behavior sits behind JVM-fakeable seams with the manual matrix covering the runtime
  (R-10, IV).
- Complexity tracking table in [plan.md](./plan.md): remains empty — no violations to
  justify.
