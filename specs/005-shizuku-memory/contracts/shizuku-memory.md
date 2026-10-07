# Contract: Shizuku Memory Seam (internal Kotlin seam + UserService AIDL)

**Feature**: `005-shizuku-memory` | **Input**: [spec.md](../spec.md) · [research.md](../research.md)

The seam this feature adds to the app's only platform boundary (the 002
[device-readers.md](../../002-home-screen/contracts/device-readers.md) family). The four
Kotlin interfaces live in `data/DeviceReaders.kt`; the `rikka.shizuku.*`-importing
implementations live in `data/shizuku/`; JVM unit tests replace every seam with fakes
(no test touches the Shizuku library — Constitution IV, R-10). The 002 common clauses —
**one-shot**, **null = unavailable**, **main-thread hostile**, **pure mapping** — apply
verbatim to every reader below, with the documented exceptions for the connection cache
(clause 6) and the action seams (clause 5).

---

## New interfaces

```kotlin
// data/DeviceReaders.kt (added to the existing file)

fun interface AppMemoryReader {
    /**
     * One batched privileged read → the aggregated snapshot (package → summed PSS
     * bytes; absent = not running; null value = per-app failure), or null when the
     * pass is unavailable (not authorized / binder dead / timeout / failure).
     */
    fun read(): Map<String, Long?>?
}

fun interface ShizukuAccessStatus {
    /** One state-ladder check (R-03) → NOT_INSTALLED / NOT_RUNNING / OUTDATED /
     *  AWAITING_AUTHORIZATION / AUTHORIZED. Never throws. */
    fun state(): ShizukuAccessState
}

fun interface ShizukuAuthorizer {
    /** Fire-and-forget: Shizuku shows its own dialog. Result arrives via the
     *  change source, never a return value. */
    fun request()
}

fun interface ShizukuChangeSource {
    /** Registers binder-received(sticky)/dead/permission-result listeners; every
     *  event invokes onChange (already main-thread). Returns the unsubscribe. */
    fun listen(onChange: () -> Unit): () -> Unit
}
```

## UserService AIDL (the privileged surface)

```aidl
// app/src/main/aidl/com/jkteknologies/androidanalyzer/data/shizuku/
package com.jkteknologies.androidanalyzer.data.shizuku;

parcelable AppProcessMemory;                          // (processName: String, pssBytes: Long)

interface IAppMemoryService {
    List<AppProcessMemory> readRunningProcessMemory(); // one batched round trip
}
```

`AppMemoryServiceImpl` executes **inside Shizuku's server process** (shell/root uid):
public `ActivityManager.getRunningAppProcesses()` → `getProcessMemoryInfo(pids)` →
`totalPss × 1024L`, skipping `pid ≤ 0`. This one method is the feature's entire
privileged surface (FR-010 — read-only, nothing else).

## Behavioral clauses

1. **Single-pass coupling** (InstalledAppReader): one inventory pass performs one
   `ShizukuAccessStatus.state()` check and — only when `AUTHORIZED` — one
   `AppMemoryReader.read()`; every `memoryBytes` of the pass comes from that snapshot
   through `memoryBytesFor`. In every other state no privileged read is attempted and
   every `memoryBytes` is `null` (FR-001/FR-006, V-S3). The pass's posted state and its
   figures share one epoch-guarded post.
2. **Honest figure semantics** (merge): pass unavailable ⇒ `null`; package absent ⇒
   `0` (installed, not running); present `null` or negative ⇒ `null` (per-app failure /
   validation, FR-008). Units are bytes by construction (KB → bytes happens inside the
   UserService impl; the domain never sees KB).
3. **State ladder is total and terminating** (ShizukuAccessStatus): installed-check →
   `pingBinder` → version floor (`isPreV11() || getVersion() < 13` ⇒ `OUTDATED`) →
   `checkSelfPermission`; every library throw (`IllegalStateException`,
   `RuntimeException`) collapses to `NOT_RUNNING`. The method never throws and never
   blocks meaningfully on v13+ servers (attach-pushed permission cache).
4. **Authorization belongs to Shizuku** (ShizukuAuthorizer): `request()` only forwards
   to Shizuku's dialog; the analyzer stores no credential/token and adds no
   "remember" path (FR-005). A throw (binder died between check and tap) is swallowed
   by the caller — the change source re-reads the state.
5. **Action seams stay UI-adjacent**: neither reader requests grants or launches
   intents; "Open Shizuku" and the request button are UI wiring
   ([details-guidance.md](./details-guidance.md)), keeping the 002 clause-6 shape.
6. **Connection cache is the one stateful exception** (ShizukuMemorySource):
   `AppMemoryReader.read()` binds `IAppMemoryService` lazily (await ≤ 5 s), keeps the
   connection until a call fails (**any `Throwable` drops the cache — rebind happens on
   the next read**) or the process ends, and never unbinds on screen disposal (R-07).
   Everything else about it is one-shot: repeated calls re-read, nothing is memoized
   between calls, and any failure yields `null`. `ponytail:` ceiling — no in-flight
   watchdog; a hung binder call blocks its own pass until process death (upgrade:
   read-timeout wrapper if ever observed on hardware).
7. **Listener lifetime** (ShizukuChangeSource): listeners are registered by the Details
   holder at construction and removed on `shutdown()`; events may repeat (Shizuku
   restarts) and interpretation belongs to the holder (R-04/R-08) — once a state has
   been posted, every event triggers one coalescing refresh (the pass re-reads state
   and figures together); the initial sticky delivery triggers nothing.

## JVM-test obligations

Every interface is fakeable with a lambda; the suite asserts clause 1 (state ↔ memory
coupling, V-S3), clause 2 (merge rules via `memoryBytesFor`, V-S2, plus aggregation
V-S1), clause 4/5 (request delegation + throw survival, V-S6), and clause 7
clause 7 (known-state refresh trigger + sticky no-refresh, V-S4/V-S5) with zero
Android classes on the test classpath. The AIDL pair and the Shizuku library appear
only in manual verification (quickstart M-1..M-9).
