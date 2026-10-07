# Data Model: Pull-to-Refresh on Home Screen

**Feature**: `003-pull-to-refresh` | **Input**: [spec.md](./spec.md) · [research.md](./research.md)

Feature 003 adds **one behavioral entity** (`RefreshCycle`) and **no new persisted or
platform-read data**. The 002 entities (`ResourceSummary` readings, `ApplicationInventory`,
`ThemePreference`) and their validation rules are reused unchanged — this file defines
only what is new: the refresh cycle, its interaction states, and the extended
`FigureUiState` transition rules. Test identifiers (§5) extend 002's V-numbering with
the `VR-` prefix to keep the two features' test lists disjoint.

---

## 1 · RefreshCycle

A user-visible reload pass over the five figure readers. One exists at most at any time
(spec Key Entities; R-02/R-03).

| Field | Type | Meaning |
|-------|------|---------|
| `epoch` | Int (monotonic, starts 0) | Identity of the pass. Bumped only when a new pass actually starts; posts carrying a stale epoch are dropped (R-04). |
| `trigger` | `PRESENTATION` \| `USER_REFRESH` | What started the pass. Controls reset behavior and indicator semantics (below). |
| `resetOnStart` | Boolean (derived: `trigger == PRESENTATION`) | `PRESENTATION` resets every figure to `Loading` (002 FR-014, unchanged). `USER_REFRESH` preserves current states — the no-placeholder-flash rule (FR-004). |

### 1.1 Lifecycle

```text
Idle ──presentation trigger──► Running(epoch=n, reset) ──► Idle
Idle ──refresh trigger───────► Running(epoch=n, no-reset, isRefreshing=true) ──► Idle
Running ──any trigger while running──► absorbed (no state change to the cycle;
                                       refresh triggers additionally force isRefreshing=true)
Running ──last figure post + completion post applied──► Idle
```

Rules:

- **R-L1** Transitions are driven only from the UI thread (gesture, lifecycle) or via
  main-thread result posts — no synchronization primitives needed or allowed (R-03).
- **R-L2** Completion is defined as the pass's final executor step posting
  `cycleRunning=false; isRefreshing=false` *after* the five figure posts; FIFO poster
  order makes "indicator clears" strictly follow "last figure landed" (FR-003).
- **R-L3** A superseded cycle (new pass bumped the epoch) never completes anything — all
  its remaining posts, including its completion post, are dropped (R-04).
- **R-L4** `shutdown()` (host screen disposed) releases the executor; the next pass of
  either trigger replaces it from the factory — a terminated pool is never reused
  (inherited 002 rule).

## 2 · Interaction states (UI-level, conceptual)

The pull interaction as the user perceives it; realized by `PullToRefreshBox` +
`PullToRefreshState` (R-01), not by a new project type:

```text
Idle ──drag down (content at top)──► Pulling(progress 0→1, indicator grows)
Pulling ──release past threshold──► Running(indicator spins; isRefreshing=true)
Pulling ──release before threshold──► Idle          (cancel, no reload — FR-002)
Running ──completion post──► Idle                    (indicator dismissed — FR-003)
```

- The pull is armed **only when the scrollable content is at its topmost position**
  (FR-009); nested scroll routes drag to content scrolling otherwise (R-08).
- The interaction exists **only on the home screen** (FR-007); the settings screen and
  footer are outside the pull container.

## 3 · FigureUiState — extended transition rules

The 002 three-state model (`Loading`, `Available`, `Unavailable`) is unchanged as a
type; the *cycle transition table* gains the refresh column:

| Current figure state | Presentation cycle starts | Refresh cycle starts | Read lands (`Available`) | Read fails (`null`/throw/invalid) |
|----------------------|---------------------------|----------------------|--------------------------|-----------------------------------|
| `Loading` | stays `Loading` | stays `Loading` | → `Available` | → `Unavailable` |
| `Available(v)` | → `Loading` (002 behavior) | **stays `Available(v)`** (FR-004) | → `Available(v′)` in place | → `Unavailable` (FR-012) |
| `Unavailable` | → `Loading` (002 behavior) | **stays `Unavailable`** | → `Available` (recovery, FR-012) | → `Unavailable` |

Additional rules:

- **F-1 (independence)**: figures never wait for one another; each post applies on
  arrival (inherited 002 rule, FR-014-equivalent for refresh).
- **F-2 (epoch)**: every rule in the last two columns applies only when the post's
  epoch equals the current epoch; otherwise the post is a no-op (R-04).
- **F-3 (fresh snapshot)**: `Available(v′)` always derives from a read performed during
  the current pass — never cached, never merged with `v` (FR-005; paired-figure
  invariants remain guaranteed by the 002 domain `create()` validators).

## 4 · Trigger matrix

| Event | Calls | Guard outcome when a pass is running |
|-------|-------|--------------------------------------|
| `ON_RESUME` / entering composition while resumed (002 FR-011) | `startReadCycle()` | **Not guarded**: always resets to `Loading` and queues its pass on the single-thread executor (002 behavior preserved verbatim). Its epoch supersedes the running pass's remaining posts. |
| Pull released past threshold (FR-001) | `refresh()` | **Absorbed**: sets `isRefreshing=true`, starts nothing, queues nothing (FR-006). |
| Accessibility "Refresh figures" action (R-05) | `refresh()` | Same as the gesture — identical entry point by design. |

## 5 · Validation summary — test-target list

JVM unit tests (extend `HomeStateHolderTest`; fakes per R-07). Each row maps to a spec
requirement:

| ID | Rule under test | Spec |
|----|-----------------|------|
| VR-1 | `refresh()` on idle holder with `Available`/`Unavailable` figures leaves every state unchanged until its read lands (no `Loading` flash) | FR-004 |
| VR-2 | `refresh()` re-reads every reader exactly once per started pass and posts values in place | FR-001, FR-005 |
| VR-3 | `refresh()` while a pass is running performs no additional reader calls (absorption) and leaves exactly one pass in flight | FR-006, US3-1 |
| VR-4 | `refresh()` during an in-flight presentation pass is absorbed; figures settle once, from that pass | US3-2 |
| VR-5 | `isRefreshing` becomes true on a started refresh and clears only via the completion post, after the fifth figure post has been applied | FR-003, SC-002 |
| VR-6 | An absorbed trigger while `isRefreshing=true` changes nothing (no re-set, no extra pass) | FR-006 |
| VR-7 | Posts carrying a stale epoch are dropped: supersede a running pass (new `startReadCycle`), then deliver the old pass's late posts → no effect on figures, no completion flip | FR-011, US3-2 |
| VR-8 | `refresh()` after `shutdown()` replaces the executor and completes normally | R-L4 |
| VR-9 | `Unavailable` figure flips to `Available` when the retry read succeeds; stays `Unavailable` when it fails again | FR-012 |
| VR-10 | A presentation cycle starting mid-refresh resets figures to `Loading` (002 behavior unchanged) and its posts land from the newer epoch | §4 matrix |

## 6 · Entities unchanged from feature 002

`MemoryReading`, `StorageReading`, `BatteryReading`, `CoreCount`,
`ApplicationInventory`, `FigureUiState` (type), `ThemePreference` — see
[../002-home-screen/data-model.md](../002-home-screen/data-model.md). No field, validator,
or persistence change is part of feature 003.
