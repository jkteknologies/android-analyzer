# Contract: UI (screens, footer, theme behavior)

**Feature**: `002-home-screen` | **Input**: [spec.md](../spec.md) · [data-model.md](../data-model.md)

The user-facing contract: what each screen must show and do. This is what
[quickstart.md](../quickstart.md) validates manually and what tasks derive UI work from.
All labels are English string resources (spec assumption "Formatting").

## 1 · App shell

| # | Clause | Source |
|---|---|---|
| U-1 | Single Activity hosts everything; a persistent footer is present on **every** screen of this feature. | FR-007 |
| U-2 | Footer contains exactly two buttons: **"Home screen"** on the left, **"Settings"** on the right — nothing else. | FR-007 |
| U-3 | The current destination is visually indicated (e.g. filled vs outlined/tonal button); the indication updates on every navigation. | FR-008 |
| U-4 | Tapping "Settings" presents the settings screen; tapping "Home screen" presents the home screen with figures reflecting device state at display time (a fresh read cycle runs). | US3 |
| U-5 | System back gesture on settings returns to home. | R-08 |
| U-6 | Rapid alternating taps between the two buttons: final screen matches the last tap; no crash, no intermediate-state flicker. | Edge case |

## 2 · Home screen

| # | Clause | Source |
|---|---|---|
| H-1 | Shows exactly five figure groups: **Memory** (available + allocated), **Internal storage** (free + used), **Battery** (level + charging state), **Processor** (core count), **Applications** (non-system count + total in brackets). No other figures, no per-app drill-down, no charts. | FR-001, FR-003 |
| H-2 | Byte amounts are human-readable and locale-aware (e.g. "1.5 GB"); counts use locale grouping; battery is a percent. | Spec assumption, R-12 |
| H-3 | Applications figure renders as `"N (M)"` (e.g. `"36 (121)"`; `"0 (121)"` on a fresh emulator). | FR-004 |
| H-4 | On presentation the layout renders immediately; every figure shows the neutral placeholder until its own read lands. | FR-014 |
| H-5 | A figure whose read fails shows the distinct "Not available" indication — never a fake value; the other figures are unaffected. | FR-012 |
| H-6 | Figures re-read on: launch, in-app return to home, resume from background. No polling, no manual refresh control, nothing runs while home is not visible. | FR-011 |
| H-7 | On near-full devices figures stay non-negative, correct, and the layout does not break. | Edge case, FR-002 |
| H-8 | Each figure exposes screen-reader semantics "label: value" (name-and-value); the unavailability indication is announced as such, distinctly from any value. | FR-015 |
| H-9 | At the system's largest font scale (2.0), nothing clips or overlaps; content scrolls if needed. | FR-015 |

## 3 · Settings screen

| # | Clause | Source |
|---|---|---|
| S-1 | Contains exactly one setting — **Theme** — with exactly three options: **Light**, **Dark**, **System default**; no other sections, no placeholders, no "coming soon". | FR-009, edge case |
| S-2 | Initial state (first launch, never configured): **System default** selected. | FR-009 |
| S-3 | Selecting Light or Dark applies the theme app-wide immediately (next frame; no restart). Selecting System default returns to following the device theme. | FR-010, US4 |
| S-4 | The selection persists across app close/reopen. | FR-010 |
| S-5 | Rapid option toggling: no crash/flicker; final appearance matches the last selection. | Edge case |
| S-6 | Each option row is screen-reader operable (selectable semantics, state announced). | FR-015 |

## 4 · Theme behavior (cross-screen)

| # | Clause | Source |
|---|---|---|
| T-1 | Default (System default): app renders dark when the device is in dark mode, light otherwise, with no user configuration. | FR-006 |
| T-2 | A system theme switch while the app is foreground is followed without restart; if backgrounded, the correct theme is present on next resume. | US2, edge case |
| T-3 | Effective scheme resolution: `LIGHT→Light`, `DARK→Dark`, `SYSTEM→device setting` (data-model §4). | FR-010 |
| T-4 | Material 3 light/dark color schemes; no dynamic color in this feature. | R-06 |

## 5 · Accessibility contract (cross-screen)

| # | Clause | Source |
|---|---|---|
| A-1 | Every figure, footer button, and theme option is operable and understandable via TalkBack. | FR-015 |
| A-2 | Text uses sp-scaled typography; containers wrap; no fixed pixel heights on text-bearing elements. | FR-015 |

## 6 · Out of contract (explicitly absent)

CPU utilization or per-core detail, external/removable storage, battery health, app
drill-down, history/charts, additional settings, localization beyond English, widgets,
background refresh (FR-003, spec "Out of scope").
