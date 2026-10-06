# Data Model: Home Screen

**Feature**: `002-home-screen` | **Date**: 2026-10-05 | **Input**: [spec.md](./spec.md) · [research.md](./research.md)

All domain types live in the `domain` layer as plain Kotlin (no Android imports), which
is what makes them unit-testable on the JVM. Android-typed readers
([contracts/device-readers.md](./contracts/device-readers.md)) produce them.

---

## 1 · `FigureUiState<T>` — per-figure presentation state

The universal three-state lifecycle of every displayed figure (FR-012, FR-014).

| State | Meaning | Rendering |
|---|---|---|
| `Loading` | Read in flight; no value yet | Neutral placeholder (em-dash `—`) |
| `Available(value: T)` | Read completed successfully | Formatted value (R-12) |
| `Unavailable` | Read failed or returned a nonsensical value | Distinct indication ("Not available") |

**State transitions** (strict):

```text
read cycle starts:  any ──▶ Loading
read completes:     Loading ──▶ Available(value)   (value valid)
read fails:         Loading ──▶ Unavailable        (reader threw / returned null / validation failed)
terminal:           Available and Unavailable are terminal until the next read cycle
```

**Rules**:

- Figures are independent: each of the five figures (memory ×1 pair, storage ×1 pair,
  battery, core count, app count — see grouping below) transitions on its own; no
  figure waits for another (FR-014).
- The containing screen renders immediately with all figures in `Loading`.
- A value that fails its entity validation below maps to `Unavailable`, never to a
  clamped or invented value (FR-012).

## 2 · Resource readings (domain values)

Each reading is produced from a **single platform read** (FR-002); paired derived
figures therefore cannot disagree with the total.

### 2.1 `MemoryReading`

| Field | Type | Constraint |
|---|---|---|
| `totalBytes` | `Long` | `> 0` |
| `availableBytes` | `Long` | `0 ≤ availableBytes ≤ totalBytes` |
| `allocatedBytes` (derived) | `Long` | `= totalBytes − availableBytes` |

Source: one `ActivityManager.MemoryInfo` (`totalMem`, `availMem`) — R-02.
Validation failure (e.g. `totalBytes ≤ 0`, `availableBytes > totalBytes`) → the whole
memory figure pair renders `Unavailable`.

**Display pair**: "Memory — available A / allocated B" (A = `availableBytes`,
B = `allocatedBytes`; invariant `A + B = totalBytes ≤ device total`, both ≥ 0 — FR-002).

### 2.2 `StorageReading`

| Field | Type | Constraint |
|---|---|---|
| `totalBytes` | `Long` | `> 0` |
| `availableBytes` | `Long` | `0 ≤ availableBytes ≤ totalBytes` |
| `usedBytes` (derived) | `Long` | `= totalBytes − availableBytes` |

Source: one `StatFs` on `Environment.getDataDirectory()` (`totalBytes`,
`availableBytes`) — R-03. Validation failure → both storage figures `Unavailable`.

**Display pair**: "Internal storage — free F / used U" (FR-002 invariants as above).

### 2.3 `BatteryReading`

| Field | Type | Constraint |
|---|---|---|
| `levelPercent` | `Int` | `0 ≤ levelPercent ≤ 100` |
| `charging` | `Boolean` | `EXTRA_STATUS ∈ {CHARGING, FULL}` |

Source: one sticky `ACTION_BATTERY_CHANGED` read (`EXTRA_LEVEL`, `EXTRA_SCALE`,
`EXTRA_STATUS`) — R-04. `null` intent, `level < 0`, or `scale ≤ 0` → `Unavailable`
(FR-012; emulator fallback path).

### 2.4 `CoreCount`

| Field | Type | Constraint |
|---|---|---|
| `count` | `Int` | `≥ 1` |

Source: `Runtime.getRuntime().availableProcessors()` — R-05.

### 2.5 `ResourceSummary` (composition, not an aggregate read)

```text
ResourceSummary = {
  memory:    FigureUiState<MemoryReading>,   // drives the available+allocated pair
  storage:   FigureUiState<StorageReading>,  // drives the free+used pair
  battery:   FigureUiState<BatteryReading>,
  processor: FigureUiState<CoreCount>,
}
```

FR-003 fixes the resource kinds to exactly these four; anything else (CPU utilization,
external storage, battery health) is absent by design.

## 3 · `ApplicationInventory`

| Field | Type | Constraint |
|---|---|---|
| `nonSystemCount` | `Int` | `≥ 0` |
| `totalCount` | `Int` | `≥ 0` |
| `systemCount` (derived) | `Int` | `= totalCount − nonSystemCount ≥ 0` |

**Invariant**: `nonSystemCount ≤ totalCount`.

Source: `PackageManager.getInstalledApplications(0)`; classification is the pure
function `isSystemApplication(flags: Int): Boolean = (flags and FLAG_SYSTEM) != 0`
(R-01) — updated preinstalled apps keep `FLAG_SYSTEM`, so they stay in `systemCount`
(FR-005).

**Display string**: `"{nonSystemCount} ({totalCount})"` — e.g. `"36 (121)"`, `"0 (121)"`
on a fresh emulator (FR-004, US1 scenarios 3–4). Counts formatted with
locale grouping (R-12). Read failure → `FigureUiState.Unavailable` for the whole
figure.

## 4 · `ThemePreference` & effective theme

```text
ThemePreference = LIGHT | DARK | SYSTEM     // SYSTEM is the default (FR-009)

EffectiveTheme(themePreference, systemInDarkMode):
  LIGHT  → Light
  DARK   → Dark
  SYSTEM → systemInDarkMode ? Dark : Light   // re-evaluated on every system change (FR-006)
```

Persistence (R-07): `SharedPreferences` key `"theme_preference"` holding the enum name;
missing/corrupt value → `SYSTEM`. Write on selection (`apply()`), read at activity
creation. Selected option applies on next recomposition — immediate, no restart
(FR-010).

## 5 · `Destination` (app shell state)

```text
Destination = HOME | SETTINGS        // initial: HOME
```

- Footer renders both buttons always; the current `Destination` is visually indicated
  (FR-007/FR-008).
- `SETTINGS → HOME` also via system back (`BackHandler`) — R-08.
- Entering `HOME` from either direction triggers a fresh read cycle (FR-011).

## 6 · App UI state (single source of truth in composition)

```text
AppUiState = {
  destination: Destination,
  themePreference: ThemePreference,     // persisted on change
  // effectiveTheme computed at theme-application point from themePreference + isSystemInDarkTheme()
}
```

## 7 · Validation summary (test target list)

| # | Rule | Source |
|---|---|---|
| V-1 | `Loading → Available/Unavailable` only; `Available/Unavailable` terminal per cycle; new cycle resets to `Loading` | FR-014 |
| V-2 | Memory/storage: derived figure = total − counterpart, computed from one reading | FR-002 |
| V-3 | All byte figures ≥ 0; pair sums = total (hence ≤ device total) | FR-002 |
| V-4 | `availableBytes ≤ totalBytes`, `totalBytes > 0`, else `Unavailable` | FR-012, edge case |
| V-5 | `0 ≤ levelPercent ≤ 100` else `Unavailable`; missing battery read → `Unavailable` | FR-012 |
| V-6 | `CoreCount ≥ 1` | §2.4 |
| V-7 | `nonSystemCount ≤ totalCount`; both ≥ 0 | FR-004 |
| V-8 | Classification: `FLAG_SYSTEM` set (incl. updated system apps) ⇒ system app | FR-005 |
| V-9 | Display string exact form `"N (M)"` with locale grouping | FR-004, edge case |
| V-10 | Corrupt/missing persisted theme value falls back to `SYSTEM` | FR-009 |

## 8 · No persistence beyond theme

The feature stores nothing else: resource readings and app counts are transient
per-presentation snapshots (spec assumption "Snapshot behavior"); no history, no cache,
no files (Constitution IX).
