# Contract: Details Screen

**Feature**: `004-per-app-info` | **Input**: [spec.md](../spec.md) · [research.md](../research.md)

The Details tab's UI wiring: inventory list, filter, per-app figures, grant hint, row
tap, and pull-to-refresh. State behavior lives in
[data-model.md](../data-model.md) §7 (`DetailsStateHolder`); the 003 refresh-interaction
contract applies to this screen with "the five figure posts" read as "the inventory
post". Verified by AGP lint + the quickstart manual matrix (R-11).

---

## D · List & filter

1. **Content** (FR-004): one scrollable list (`LazyColumn`) showing every installed
   application from `inventory` filtered by `AppCategoryFilter.apply`; each row shows
   the display name, a clear system/user mark, and the two figure slots
   (FR-006). Sp-scaled text, wrapping rows, no fixed heights (002 A-2 rule).
2. **Filter control** (FR-005): a three-option single-select control (All / User /
   System) above the list, reflecting and driving `DetailsStateHolder.filter`;
   selection changes recompose the list only — no read is triggered (V-D3).
3. **Figure rendering** (FR-006/FR-007): storage and memory render as
   `label value` pairs using the injected byte formatter (`formatBytes` seam, R-10);
   a `null` byte value renders the existing distinct "Not available" indication
   (`figure_unavailable`) — never zero, never blank (V-D6).
4. **Empty state** (Edge Cases): a filter matching zero applications renders one
   explanatory line naming the active filter — not a blank screen (US3-5).
5. **Loading/unavailable** (FR-010): the list body follows the three-state rules —
   `Loading` shows the neutral placeholder surface, `Unavailable` shows the
   not-available indication; both keep the filter control usable.

## G · Usage-access grant path (FR-008)

1. When `usageAccessGranted == false`, exactly one hint row renders above the list:
   what is missing + a button opening `Settings.ACTION_USAGE_ACCESS_SETTINGS`
   (R-02). The list, marks, filter, and counts stay fully usable while ungranted.
2. The hint disappears on the first pass that reports access granted; the grant
   flow's effect is verified by quickstart M-2 (grant → refresh → figures appear).

## R · Row tap (FR-018)

1. Tapping an application row fires `ACTION_APPLICATION_DETAILS_SETTINGS` with the
   `package:` URI for that row's package name (R-06 — the user-confirmed decision);
   the analyzer adds no per-app screen of its own.
2. Returning from the system page restores the Details list with its filter unchanged
   (V-D4's complement; US1-7). A row whose app was uninstalled since the last refresh
   is the system page's own case to answer — the analyzer must not crash (Edge Cases).

## P · Pull-to-refresh (FR-009)

1. The scrollable list is wrapped in `PullToRefreshBox` from outside (003 W-1 pattern):
   `isRefreshing = holder.isRefreshing`, `onRefresh = holder::refresh`, default
   indicator. Arming only at list-top comes from the component's nested-scroll
   integration.
2. The 003 accessibility pair applies to this screen verbatim: the "Refresh figures"
   custom action and the polite live-region announcements reuse the existing string
   resources (R-10) — a screen-reader trigger path independent of the gesture.
3. Lifecycle triggers mirror Home: `ON_RESUME` + entering-composition-while-resumed
   start the presentation cycle; dispose shuts the executor down (003 R-L4).
