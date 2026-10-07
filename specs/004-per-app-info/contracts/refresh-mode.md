# Contract: Refresh Mode Setting & Persistence

**Feature**: `004-per-app-info` | **Input**: [spec.md](../spec.md) · [research.md](../research.md)

The Settings-screen half of the automatic-refresh feature: option set, persistence,
and application wiring. Extends the 002 settings S-clauses; the theme section is
untouched (FR-014 "retain the theme option unchanged").

---

## S · Settings screen

1. **Option set** (FR-015, user-confirmed): exactly four rows under a second
   "Automatic refresh" label — "On demand", "Every 30 seconds", "Every minute",
   "Every 5 minutes" — rendered with the existing `ThemeOptionRow` pattern
   (selectable semantics, radio visuals, S-6). No fifth option, no free-form input.
2. **Selection flow**: a tap raises `onRefreshModeSelected(mode)`; the host
   (`MainActivity`) persists **first** (`RefreshModeStore.save`) then updates the
   lifted Compose state — the same save-then-apply order as the theme (002 T-027
   pattern), so a crash between persist and render can only under-apply, never
   display an unpersisted choice.
3. **Default** (FR-014, SC-006): a fresh install shows "On demand" selected
   (`fromPersisted(null) == ON_DEMAND`); a corrupt persisted value resolves the same
   way (V-R2). The default renders with no migration or first-run special case.
4. **Theme section unchanged**: exactly one Theme group with its three options, as
   shipped in 002 — this feature adds a section, it does not restyle the screen
   (FR-014).

## W · Wiring

1. `MainActivity` constructs `RefreshModeStore` alongside the theme store and lifts
   the loaded mode into Compose state; the shell's ticker
   ([navigation-and-footer.md](./navigation-and-footer.md) T-1) reads the lifted
   state, so a selection change takes effect without restart or screen recreation
   (US4-3).
2. The mode is the single source of truth for the ticker and nothing else; screens
   never read it (R-08's shell-ownership rule).
3. Persistence surface: the existing `android_analyzer` `SharedPreferences` file,
   one new string key `refresh_mode` holding the enum name (R-09). No other writer
   exists; `save()` is idempotent.
