# Contract: Refresh Interaction (Home Screen)

**Feature**: `003-pull-to-refresh` | **Input**: [spec.md](../spec.md) · [research.md](../research.md)

The seam this feature adds — everything `$speckit-tasks` builds and the JVM test suite
checks against. Two halves: the **state-holder API** (C-clauses, JVM-testable through
the existing fakes seam) and the **UI wiring** (W-clauses, verified by lint + the
quickstart manual checks). The 002 contracts
([device-readers.md](../../002-home-screen/contracts/device-readers.md),
[ui-contracts.md](../../002-home-screen/contracts/ui-contracts.md)) remain fully in
force; this contract extends them.

---

## C · `HomeStateHolder` refresh API

```kotlin
val isRefreshing: Boolean            // observable (Compose state), read on UI thread
fun refresh()                        // user-initiated reload trigger
fun startReadCycle()                 // 002 presentation trigger — semantics unchanged
```

Common clauses — they apply to the API as a whole:

1. **Thread confinement**: `refresh()`, `startReadCycle()`, and `shutdown()` are called
   only from the UI thread; all state mutations happen there or via main-thread posts.
   No synchronization primitives (data-model R-L1).
2. **One pass at most**: at any instant at most one reader pass is in flight on the
   single-thread executor. `refresh()` while a pass runs is **absorbed**: it forces
   `isRefreshing = true` and returns — never queued, never parallel (FR-006).
3. **No reset on refresh**: a refresh-started pass does not touch current figure
   states; each figure is overwritten in place when its read lands. Placeholders apply
   only to figures still `Loading` (FR-004).
4. **Indicator lifecycle**: `isRefreshing` is true from a refresh trigger (start or
   absorption) until the running pass's completion post applies, which is ordered after
   the pass's five figure posts on the main-thread queue (FR-003).
5. **Epoch validity**: every result/completion post applies only if its captured epoch
   is current; a superseded pass's posts are inert (FR-011, R-04).
6. **Executor stewardship**: a pass never runs on a shut-down executor — the pool is
   replaced from the factory first (inherited 002 rule; VR-8).
7. **Read semantics**: reads are the 002 one-shot reader calls — same error mapping
   (`null`/throw/invalid → `Unavailable`), same per-figure independence, same
   fresh-snapshot invariants (FR-005, FR-012; device-readers.md clauses 1–4).
8. **Presentation trigger unchanged**: `startReadCycle()` keeps its exact 002 behavior
   (reset to `Loading`, always starts a pass, bumps the epoch) — feature 003 must not
   narrow 002's read-on-every-presentation contract.

## W · Home screen gesture wiring

1. **Placement**: `PullToRefreshBox` wraps the home screen's scrollable figure column
   *from outside* (Box → scrollable Column), inside the Scaffold content slot; the
   footer, the settings screen, and `AnalyzerApp` are untouched by this feature
   (FR-007, R-08).
2. **Binding**: `isRefreshing = holder.isRefreshing`, `onRefresh = holder::refresh`,
   default Material3 indicator, default thresholds — the platform's pull-to-refresh
   convention (FR-001/FR-002/FR-003, R-01).
3. **Scroll coexistence**: no custom scroll or gesture logic; arming at content-top
   comes from the component's nested-scroll integration (FR-009, R-08).
4. **System gestures**: no edge exclusions, no window gesture overrides — the
   notification shade keeps platform precedence over app content (FR-008, R-09).
5. **Accessibility**: the home content exposes a custom accessibility action labeled
   `refresh_action` ("Refresh figures") that calls `holder.refresh()`; a polite
   live-region status node announces `refresh_status_refreshing` while refreshing and
   `refresh_status_done` on completion (FR-013, SC-006, R-05). Merged figure semantics
   (H-8) and the footer semantics are unchanged.
6. **Strings**: exactly the three new resources of R-06; no visible-text layout
   changes beyond the standard indicator.
7. **No new surface area**: no new composables outside `ui/home`, no new classes
   outside `HomeStateHolder`/`HomeScreen`, no manifest or permission changes, no
   dependency changes (Constitution VII/VIII).
