# Contract: Details Guidance Row (Shizuku states)

**Feature**: `005-shizuku-memory` | **Input**: [spec.md](../spec.md) · [research.md](../research.md)

The one UI surface this feature adds: a state-driven guidance row on the Details screen,
plus the wiring of its two actions. State behavior lives in
[data-model.md](../data-model.md) §8 (`DetailsStateHolder`); the memory slot's rendering
is untouched (004 details-screen.md D-3 already renders value-or-not-available, and zero
renders as a normal value). Verified by AGP lint + the quickstart manual matrix (R-10).

---

## G · Guidance row (FR-004/FR-005/FR-007)

1. **Placement and shape**: one row directly below the usage-access hint (when that
   shows) and above the filter — the 004 `GrantHintRow` layout (text + optional
   button, `weight(1f)` text, sp-scaled, no fixed heights). Renders once
   `holder.shizukuAccess != null`; before the first pass lands the row is absent.
2. **State mapping** (exactly the data-model §1 table): `NOT_INSTALLED` /
   `OUTDATED` render text only; `NOT_RUNNING` renders text + the "Open Shizuku"
   button; `AWAITING_AUTHORIZATION` renders text + the "Allow access" button;
   `AUTHORIZED` renders one quiet line and no button. The text names the current
   state in plain language — never a stale one (US2-5).
3. **Open action** (`NOT_RUNNING`): `PackageManager.getLaunchIntentForPackage(
   SHIZUKU_PACKAGE)` + `startActivity` — returning from Shizuku re-enters via
   `ON_RESUME`, which starts a read cycle (existing 004 pattern); no result is
   awaited.
4. **Allow action** (`AWAITING_AUTHORIZATION`): calls
   `holder.requestAuthorization()` — Shizuku's own dialog appears; the analyzer adds
   nothing around it (FR-005). A grant lands via the change source and triggers the
   auto re-read (data-model §8, FR-007); a denial leaves the state unchanged.
5. **Accessibility**: the guidance text carries a polite live region so state changes
   (Shizuku stopped, authorization granted/revoked) are announced without focus
   changes; the row's button keeps default button semantics. The 003 refresh a11y
   pair on this screen is untouched.
6. **Non-interference**: in every state the list, marks, filter, counts, storage
   figures, row tap, and pull-to-refresh remain fully usable (FR-006); the row never
   blocks or overlays them.

## S · Strings (R-09)

New resources only; no existing string changes: `shizuku_hint_not_installed` (names
Shizuku, free via shizuku.rikka.app, the install → start → allow steps), `shizuku_hint_outdated`,
`shizuku_hint_not_running`, `shizuku_hint_awaiting`, `shizuku_hint_authorized`, and the
two labels `shizuku_open`, `shizuku_allow`. English only (002 formatting assumption).
