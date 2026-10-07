# Contract: Navigation, Footer & Home Drill-Down

**Feature**: `004-per-app-info` | **Input**: [spec.md](../spec.md) · [research.md](../research.md)

The shell seam: three destinations, the restyled footer, the back gesture, the Home →
Details filter hand-off, and the shell-owned auto-refresh ticker. Extends the 002
[ui-contracts.md](../../002-home-screen/contracts/ui-contracts.md) U-clauses; nothing
in 002's screen contracts is narrowed except the footer visual clauses superseded
below.

---

## N · Destination & footer

1. **Destinations** (FR-001): `Destination` has exactly `HOME`, `DETAILS`, `SETTINGS`;
   the shell state starts at `HOME`; the displayed screen is a pure `when` on the state
   (002 pattern unchanged, no navigation library — R-05).
2. **Footer geometry** (FR-002, superseding 002 A-1's padded/rounded bar): the footer
   is a full-width `Row` with **no outer padding and no spacing between buttons**;
   three buttons of equal width (`weight(1f)`), each `shape = RectangleShape` — one
   continuous square-cornered bar. Default Material3 button heights; no fixed heights
   (a11y A-2 rule preserved).
3. **Footer indication & labels** (FR-003): the current destination's button renders
   filled (`Button`), others tonal (`FilledTonalButton`) — exactly one indicated; the
   `selected` semantic is preserved. Labels are "Home", "Details", "Settings"
   (`footer_home` reworded, `footer_details` new).
4. **Back gesture**: system back from `DETAILS` or `SETTINGS` returns to `HOME`
   (`BackHandler(enabled = destination != HOME)`); from `HOME` it exits as today.

## H · Home entries → Details hand-off (FR-011, FR-012)

1. The Home applications figure renders as **two entries** — "User applications"
   (`nonSystemCount`) and "System applications" (`systemCount`, derived) — replacing
   002's combined "N (M)" row (FR-011). Both entries share the single
   `FigureUiState<ApplicationInventory>`: `Loading` → both placeholders,
   `Unavailable` → both not-available indications, `Available` → the two counts.
2. Each entry is tappable (row-level clickable semantics); tapping calls the shell's
   `onOpenApplications(filter)` with the entry's `AppCategoryFilter`.
3. The shell sets `pendingDetailsFilter = filter`, switches to `DETAILS`, and the
   Details screen applies it once via `applyPendingFilter` — overriding any previously
   chosen filter — then clears the slot (FR-012, R-06). Plain tab switches never touch
   the filter; a fresh process starts at `ALL` (spec assumption).

## T · Auto-refresh ticker (FR-014..FR-017)

1. One ticker in the shell (`LaunchedEffect(refreshMode, destination)`), not in the
   screens: it sleeps `refreshMode.intervalMillis` and then calls the **visible**
   screen's `refresh()` (HOME → home holder, DETAILS → details holder; SETTINGS →
   nothing) — R-08.
2. **Visibility gating** (FR-017): no tick fires while the activity is below `RESUMED`;
   gating uses the same lifecycle-observer pattern the screens use. Zero background
   work — no `WorkManager`, no receivers, no services (Constitution IX).
3. `ON_DEMAND` (`intervalMillis == null`) renders the ticker inert — manual
   pull-to-refresh remains available on both screens in every mode (FR-016).
4. Ticks route into the 003 coalescing guard: a tick during an in-flight pass is
   absorbed, never queued (inherited C-2).
5. The ticker recomposes on mode/destination change; interval or target switches take
   effect from the next tick boundary (no catch-up burst after resume).
