# Research: Pull-to-Refresh on Home Screen

**Feature**: `003-pull-to-refresh` | **Date**: 2026-10-06 | **Input**: [spec.md](./spec.md)

Resolves the open technical questions raised by the spec's assumptions ("Gesture
interpretation", "Feedback form", "Dependencies and permissions") and the Constitutional
requirement that every dependency be justified in the plan. Each entry records the
decision, rationale, and alternatives considered. Evidence sources: the resolved
Material3 1.4.0 artifact bytecode (Gradle cache inspection, 2026-10-06), the existing
feature 001/002 code and artifacts, and the Android platform's pull-to-refresh
conventions.

Consolidated verdict up front:

- **Zero new dependencies and zero new permissions.** The gesture comes from Material3
  1.4.0 already pinned by the Compose BOM (R-01); all state work happens inside the
  existing `HomeStateHolder` (R-02..R-04).
- **The one real design problem is semantic, not visual:** the 002 read cycle resets
  every figure to its placeholder, which FR-004 forbids for refresh cycles. Solved by a
  second entry point into the same single-pass executor cycle (R-02) plus a coalescing
  guard (R-03) and an epoch guard against stale posts (R-04).

---

## R-01 · Gesture component: Material3 `PullToRefreshBox`

**Decision**: Wrap the home screen's scrollable content in
`PullToRefreshBox(isRefreshing = holder.isRefreshing, onRefresh = holder::refresh)` with
the default Material3 indicator, inside the existing Scaffold content slot (footer
outside, untouched).

**Rationale**:

- **Already on the classpath.** The project resolves Material3 **1.4.0** through Compose
  BOM `2026.09.00` (verified in the Gradle artifact cache:
  `material3-android/1.4.0/…/material3.aar`). Bytecode inspection of that exact artifact
  confirms `androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing,
  onRefresh, modifier, state, contentAlignment, indicator, content)` exists and is
  **stable**: the pull-to-refresh classes carry no experimental opt-in annotation of any
  kind (only `kotlin.Metadata`). No version bump, no new artifact, no opt-in lint
  suppressions (Constitution VII, VIII).
- **Platform-convention behavior for free.** The component implements the standard
  pull-grow-arm-release interaction (FR-002), draws the standard circular indicator
  driven by `isRefreshing` (FR-003), and integrates through the nested-scroll system —
  arming only when the inner scrollable is at its top, which matters because the home
  content is *already* a `verticalScroll` Column (FR-009, see R-08).
- **In-app by construction.** The gesture is a nested-scroll interaction over the app's
  own content; it does not register edge exclusions or window-system gesture overrides,
  so the notification-shade swipe keeps platform precedence (FR-008, R-09).

**Alternatives considered**:

- *Accompanist `SwipeRefreshLayout` wrapper*: deprecated by its own project, requires a
  new third-party dependency for functionality the pinned Material3 already ships.
  Rejected (Constitution VII).
- *Foundation-level `pullToRefresh` modifier + hand-rolled indicator*: available in the
  same Material3 artifact (`pullToRefresh(…)` modifier + `PullToRefreshState`), but
  shifts indicator drawing, threshold animation, and accessibility plumbing onto this
  project for zero user-visible gain. Rejected as unjustified complexity (Constitution
  VII's spirit: don't re-implement what the chosen stack already provides).
- *Custom `NestedScrollConnection`*: a from-scratch re-implementation of nested-scroll
  pull mechanics — more code, more edge cases (fling, threshold, snap-back animation),
  no benefit. Rejected.

## R-02 · Refresh cycle semantics: same pass, no reset

**Decision**: `HomeStateHolder` gains `fun refresh()` that runs the **same** five-read
executor pass as `startReadCycle()` but **does not reset figure states first**. Each
figure's result post overwrites the previous state in place: `Available(vᵒˡᵈ)` →
`Available(vⁿᵉʷ)`, `Unavailable` → `Available`/`Unavailable` (retry, FR-012), and only a
still-`Loading` figure (initial read in flight) keeps its placeholder until its post
lands. A shut-down executor is replaced from the factory exactly as `startReadCycle`
already does (returning to home after dispose).

**Rationale**:

- FR-004 requires previously displayed values to remain visible during a refresh with
  in-place updates; `startReadCycle()`'s reset-to-`Loading` is precisely the placeholder
  flash the spec forbids for this path (and requires for presentation reads — 002
  FR-014 stays untouched).
- Reusing one pass keeps a single read seam: readers, error mapping (`null`/throw →
  `Unavailable`), poster ordering, and the single-thread serialization are all inherited
  unchanged — the fresh-snapshot invariants of FR-005 (paired figures from one platform
  reading each) are guaranteed by the existing domain models, not re-implemented.

**Alternatives considered**:

- *Reset-and-reload for refresh too*: violates FR-004 (placeholder flash on every pull).
  Rejected.
- *A second, parallel executor for refreshes*: violates the single-flight coalescing
  model (FR-006) and adds a thread for no benefit. Rejected.
- *A separate refresh state holder class*: splits the single seam the test suite
  already covers; the two entry points differ by three lines of behavior. Rejected.

## R-03 · Coalescing model and the `isRefreshing` lifecycle

**Decision**: The holder tracks `cycleRunning` (UI-thread-confined) and an observable
`isRefreshing`. `refresh()` behaves as follows: if a pass is running (whether started by
a presentation trigger or a refresh), it only ensures `isRefreshing = true` and returns
— the trigger is **absorbed into the running pass, never queued** (FR-006, US3-1/US3-2).
Otherwise it sets both flags and starts a no-reset pass. Every pass — regardless of its
trigger — ends with one final executor step that posts
`cycleRunning = false; isRefreshing = false` **after** the five figure posts; the main
thread's FIFO posting order therefore guarantees the indicator clears only after the
last figure lands (FR-003, SC-002). Presentation triggers (`startReadCycle`) keep their
002 behavior exactly: they always reset to `Loading` and start a pass, unconditionally —
002's "fresh read on every presentation" contract is not narrowed by this feature.

**Rationale**:

- The spec's coalescing wording ("coalesced into it rather than queued") maps exactly to
  absorb-into-running-pass; a re-queue would run a second full pass seconds after the
  first for no user-visible difference, wasting battery (Constitution IX).
- Absorption covers the US3-2 case (pull during initial load) with no special-casing:
  the initial pass's completion post clears the indicator the pull had set.
- All mutations happen on the UI thread (gesture/lifecycle triggers) or via main-thread
  posts (results), so plain fields suffice — no atomics, no locks; the JVM test fake
  poster keeps this deterministic.

**Alternatives considered**:

- *Queue-one-more semantics*: explicitly rejected by FR-006 ("rather than queued").
- *Debounce/throttle the gesture*: absorption already achieves the effect with less
  machinery; time-based throttles add a tuning knob the spec doesn't ask for. Rejected.

## R-04 · Epoch guard against stale result posts

**Decision**: The holder keeps a monotonically increasing cycle epoch. Each pass
captures the epoch at start; every result/completion post applies **only if the captured
epoch equals the current epoch**; otherwise the post is dropped. Only genuinely new
passes (presentation or refresh-start) bump the epoch — absorption (R-03) does not.

**Rationale**:

- Closes a latent window that exists in the 002 holder and matters for FR-011
  ("updates MUST NOT be applied … in a way that produces stale or jumbled figures on
  return"): if the user leaves home mid-pass and returns, the fresh cycle's synchronous
  reset can interleave in the main-thread queue with posts still arriving from the
  abandoned pass — painting a briefly stale `Available` value after the new cycle's
  placeholders. The epoch check makes superseded posts inert. Cost: one `Int` and one
  comparison per post.
- It is also the mechanism that keeps a *refresh* pass honest: if a presentation cycle
  supersedes an in-flight refresh (navigate away and back mid-pull), the refresh's late
  posts cannot overwrite the newer cycle's figures out of order.

**Alternatives considered**:

- *Do nothing (keep 002 behavior)*: the window is real but narrow; the spec explicitly
  forbids stale application (FR-011, US3-2 "never to stale duplicates"), so it must be
  closed, not tolerated. Rejected.
- *Cancel the executor task on supersede*: `shutdownNow()` interrupts the single thread
  mid-platform-read for no gain (the reads are one-shot, sub-second); dropping posts at
  apply time is simpler and interruption-safe. Rejected.

## R-05 · Accessibility: action-based trigger + spoken status

**Decision**: Two additions, both on the home content inside the pull container:

1. A **custom accessibility action** labeled "Refresh figures" that calls
   `holder.refresh()` — exposed by screen readers in the standard actions menu
   (FR-013's performability requirement, independent of the touch gesture).
2. A **polite live-region status node** whose description follows the cycle: "Refreshing
   figures" while `isRefreshing`, "Figures refreshed" on completion — announced on
   change, giving screen-reader users both the running and completed states (SC-006).

The pull gesture itself remains available as the platform's standard down-then-up
screen-reader gesture; TalkBack focus order, the merged figure semantics (H-8), and the
footer are untouched. All behavior stays correct at the system's largest font scale
because the additions are non-visual or text-based and the layout adds no fixed-size
elements.

**Rationale**: SC-006 demands performability *and* state awareness through the screen
reader; the action + live-region pair is the minimal platform-idiomatic construction
that satisfies both without visual chrome.

**Alternatives considered**:

- *Rely on the indicator's own semantics*: the default indicator conveys the running
   state only while visible and offers no trigger path; completion would go unannounced.
   Rejected as insufficient for SC-006.
- *A visible "Refresh" button*: scope creep beyond the spec (out-of-scope list: no
   refresh controls besides the gesture). Rejected.

## R-06 · New user-visible strings

**Decision**: exactly three English string resources (assumption "English at this
stage"): `refresh_action` = "Refresh figures", `refresh_status_refreshing` =
"Refreshing figures", `refresh_status_done` = "Figures refreshed". No visible-text
changes: the gesture's feedback is the standard indicator (assumption "Feedback form").

## R-07 · Testing strategy

**Decision**: All new logic lands in `HomeStateHolder`, which is JVM-testable through
the established fakes seam (`ResultPoster` + `executorFactory` injection, fake readers —
same pattern as the 002 suite). New unit cases (enumerated in
[data-model.md](./data-model.md) §5): refresh does not reset `Available`/`Unavailable`
states; refresh updates values in place; refresh during a running pass performs exactly
one pass (reader call counts); `isRefreshing` lifecycle true → clears after the last
figure post; absorbed triggers neither queue nor parallelize; epoch drop of superseded
posts; executor replacement after `shutdown()`; `Unavailable` → `Available` recovery on
retry. Gesture feel, indicator behavior, system-shade precedence, and TalkBack behavior
are verified by the manual emulator/device checks in [quickstart.md](./quickstart.md)
(Constitution IV accepts this split: JVM tests where logic lives, scripted manual checks
where only the platform can answer). No new test infrastructure.

## R-08 · Scroll interplay (FR-009 is live, not hypothetical)

**Decision**: rely on `PullToRefreshBox`'s nested-scroll integration; add no custom
scroll logic. **Rationale**: the home content is *already* a `verticalScroll` Column, so
the pull must coexist with scrolling today. Material3's pull modifier node participates
in the nested-scroll chain and arms only when the descendant scrollable has consumed to
its top (the `onPreScroll`/`onPostScroll` node classes in the inspected artifact
implement exactly this contract). The wiring requirement that follows for tasks.md:
`PullToRefreshBox` must wrap the scrollable Column (Box outside, scroll inside).

## R-09 · System top-edge gesture boundary (FR-008)

**Decision**: request nothing — no edge-exclusion rects, no system-bars behavior
overrides, no window-system gesture handling. **Rationale**: the pull gesture is an
in-app nested-scroll interaction over app content; the notification-shade/quick-settings
swipes are system-window gestures that take precedence by platform design. A swipe
starting at the physical top edge belongs to the system before the app sees it. This is
inherently a platform-behavior claim, so quickstart M-7 verifies it manually on a device
where the shade is available.

---

## Post-design Constitution re-check (after Phase 1)

Re-evaluated 2026-10-06 against the Phase 1 artifacts
([data-model.md](./data-model.md), [contracts/refresh-interaction.md](./contracts/refresh-interaction.md),
[quickstart.md](./quickstart.md)):

- All Core Principles I–IX: **PASS** — unchanged verdicts from the plan.md gate table;
  the design adds no dependency (R-01), no permission (SC-005), no background work
  (R-02/R-03 reuse the single one-shot executor pass), and no new data at rest or in
  transit. Runtime additions are one observable flag, one boolean, and one `Int` epoch
  inside an existing UI-thread-confined class (R-02..R-04).
- Complexity tracking table in [plan.md](./plan.md): remains empty — no violations to
  justify.
