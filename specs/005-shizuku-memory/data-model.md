# Data Model: Shizuku-Sourced Per-App Memory Information

**Feature**: `005-shizuku-memory` | **Input**: [spec.md](./spec.md) · [research.md](./research.md)

Feature 005 adds one domain file (`ShizukuMemory.kt`: `ShizukuAccessState`,
`ProcessMemory`, `aggregateProcessMemory`, `memoryBytesFor`), one UserService pair
(`IAppMemoryService` AIDL + `AppMemoryServiceImpl` running inside Shizuku's server
process), one data source (`ShizukuMemorySource`), four reader/action seams, and three
additions to `DetailsStateHolder` (posted state, change-source subscription,
authorization request). The 001–004 entities are reused unchanged except
`InstalledApp.memoryBytes`, whose *semantics* this feature fills in (the field, its
validation, and its rendering are untouched). Test identifiers use the `V-S` prefix to
extend the established `V-A`/`V-D` lists.

---

## 1 · ShizukuAccessState

The guidance matrix's value (FR-004 plus the spec's Outdated edge case, R-03):

| Value | Meaning | Guidance (details-guidance.md §G) |
|-------|---------|-----------------------------------|
| `NOT_INSTALLED` | No Shizuku package on the device | Text: what Shizuku is, free (shizuku.rikka.app), the three steps |
| `NOT_RUNNING` | Installed, binder dead (routine after reboot) | Text + "Open Shizuku" |
| `OUTDATED` | Server older than v13 (incl. pre-v11) | Text: update Shizuku, then return |
| `AWAITING_AUTHORIZATION` | Running, our package not granted | Text + "Allow access" (Shizuku's own dialog) |
| `AUTHORIZED` | Running and granted — figures flow | One quiet line: "Per-app memory via Shizuku" |

Produced only by the `ShizukuAccessStatus` ladder (R-03): installed-check → `pingBinder`
→ version floor → `checkSelfPermission`; every library throw collapses to `NOT_RUNNING`.
Pure enum — no validation rules of its own.

## 2 · ProcessMemory and the snapshot

One running process's privileged reading (FR-002's raw material):

| Field | Type | Meaning / rules |
|-------|------|-----------------|
| `processName` | String | The process's full name — `packageName` for the main process, `packageName:suffix` otherwise |
| `pssBytes` | Long | `Debug.MemoryInfo.totalPss` converted KB → bytes inside the UserService impl (`× 1024L`); `≥ 0` (V-S1 rejects negatives) |

**`AppMemorySnapshot`** (the reader's product) is `Map<String, Long?>?` — package name
to summed PSS bytes:

- `null` (the whole map) — the pass is unavailable: not authorized, binder dead, bind
  timeout, or read failure → every `memoryBytes` of the pass is `null` (V-S3).
- key present, value `v ≥ 0` — the app's processes sum to `v`.
- key present, value `null` — a per-app read failure: renders not-available (US1-6's
  slot at the seam; the current impl never emits it — R-06).
- key absent — the app has no running processes: renders zero (truthful, spec
  assumption).

## 3 · aggregateProcessMemory (pure, V-S1)

`List<ProcessMemory> → Map<String, Long?>`: a process attributes to package `p` iff
`processName == p || processName.startsWith("$p:")`; the target's entries sum. A
negative `pssBytes` on **any** entry of a package maps that package to `null` (never a
negative sum); processes attributing to no package are dropped (they belong to nothing
the inventory lists). Not assoc-commutative concerns — plain left-to-right summation.

## 4 · memoryBytesFor (pure, V-S2)

The single merge rule between a snapshot and one inventory row (R-06):

```kotlin
fun memoryBytesFor(snapshot: Map<String, Long?>?, packageName: String): Long?
```

`snapshot == null` → `null`; absent key → `0L`; present `null` → `null`; present `v < 0`
→ `null`; present `v ≥ 0` → `v`. Called once per app inside the `InstalledAppReader`
pass — only when the same pass's state ladder said `AUTHORIZED`; otherwise the snapshot
is not even requested and every `memoryBytes` is `null` (coupling pinned by V-S3, the
004 V-D6 pattern).

## 5 · InstalledApp.memoryBytes — semantics superseded

The 004 field and its validation stand (non-null ⇒ `≥ 0`, V-A1); 004's "null on current
Android by design" note is superseded by this feature: `null` now means *not readable
this pass* (no/failed Shizuku access), zero means *installed but not running*, and a
value means *summed PSS of the running processes at read time* (FR-001/FR-002,
SC-001). The `ponytail:` placeholder comment in `AndroidDeviceReaders` is replaced by
the merge call.

## 6 · UserService entities (the privileged pair)

| Entity | Where it lives | Rules |
|--------|----------------|-------|
| `IAppMemoryService` (AIDL) | `app/src/main/aidl/.../data/shizuku/` | One method: `List<AppProcessMemory> readRunningProcessMemory()` — one batched privileged round trip; no other surface (FR-010 keeps the privileged role to exactly this) |
| `AppProcessMemory` (Parcelable) | AIDL declaration + Kotlin class in `data/shizuku/` | `(processName: String, pssBytes: Long)`; hand-written `CREATOR` (no `kotlin-parcelize`, R-11); maps 1:1 to the domain `ProcessMemory` |
| `AppMemoryServiceImpl` | `data/shizuku/` | Loaded by the Shizuku server into its process (v13 `Context` constructor — the R-03 floor); calls public `ActivityManager.getRunningAppProcesses()` + `getProcessMemoryInfo(pids)`; skips `pid ≤ 0`; converts `totalPss` KB → bytes; every failure throws to the caller (the analyzer side turns it into the null pass) |

## 7 · ShizukuMemorySource (data source with a connection cache)

The `AppMemoryReader` implementation (R-07). Stateful by necessity — the only stateful
member of the reader family, so it is documented here and in the contract: a lazily
bound `IAppMemoryService` connection (await ≤ 5 s on first bind), kept until a call
fails (any `Throwable` drops the cached connection — rebind happens on the next read)
or our process ends. `read()` returns the aggregated snapshot (`aggregateProcessMemory`
over the mapped list) or `null` on timeout / `Throwable`. No watchdog on an in-flight
call (R-07's `ponytail:` ceiling).

## 8 · DetailsStateHolder (additions)

Everything from 004 data-model §7 stands. Added members (R-08):

| Member | Type / behavior |
|--------|-----------------|
| `shizukuAccess` | `ShizukuAccessState?` — `null` until the first pass lands; then the state as of that pass, posted together with `inventory` and `usageAccessGranted` (one epoch-guarded post — guidance and figures cannot disagree) |
| `requestAuthorization()` | Delegates to `ShizukuAuthorizer.request()`; a throw is swallowed (the change source re-reads the state). The dialog is Shizuku's own (FR-005) |
| change-source subscription | `ShizukuChangeSource.listen` registered at construction (callbacks posted to main); `onShizukuChanged` triggers **one coalescing `refresh()`** — but only once a state has been posted (the pass re-reads state and figures together, so guidance and figures can never disagree, FR-007/SC-003); the subscription's initial sticky delivery finds no posted state and triggers nothing (V-S5); `shutdown()` unsubscribes |

The four new constructor seams (`ShizukuAccessStatus`, `ShizukuAuthorizer`,
`ShizukuChangeSource`, and `AppMemoryReader` consumed inside
`AndroidDeviceReaders.installedAppReader`) are all lambdas/objects — JVM-fakeable, no
Android class needed (R-10).

## 9 · Validation summary — test-target list

| ID | Rule under test | Spec |
|----|-----------------|------|
| V-S1 | `aggregateProcessMemory`: multi-process summing incl. `pkg:` suffixes; negative entry ⇒ that package maps `null`; unattributable processes dropped | FR-002, Edge Cases |
| V-S2 | `memoryBytesFor`: null-pass ⇒ null; absent ⇒ 0; present value ⇒ value; present-null ⇒ null; negative ⇒ null | FR-002/FR-006/FR-008, US1-3/4/6 |
| V-S3 | Authorized pass couples `shizukuAccess = AUTHORIZED` with memory values (zero for absent packages); every non-authorized state couples with all-`null` memory | FR-001/FR-006, SC-002 |
| V-S4 | A change event once a state has been posted triggers exactly one (absorbed) refresh; events while a pass runs are absorbed; events before any state is posted trigger none; repeated fires re-read idempotently | FR-007, SC-003/SC-004 |
| V-S5 | The subscription's initial sticky callback (no posted state yet) triggers no refresh — no double pass at startup | R-08 |
| V-S6 | `requestAuthorization` delegates to the authorizer seam and survives a throwing one | FR-004/FR-005, Edge Cases |

## 10 · Entities unchanged from features 001–004

`FigureUiState` (+ 003 transition rules), all readings, `CoreCount`/`CoreTiers`,
`ApplicationInventory`, `InstalledApp`/`AppInventory`/`AppCategoryFilter` (fields,
factories, ordering — only §5's semantics note is superseded), `ThemePreference`,
`RefreshMode` (+ store), `Destination` and the shell (untouched by this feature).
See [../002-home-screen/data-model.md](../002-home-screen/data-model.md),
[../003-pull-to-refresh/data-model.md](../003-pull-to-refresh/data-model.md), and
[../004-per-app-info/data-model.md](../004-per-app-info/data-model.md).
