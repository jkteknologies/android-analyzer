# Data Model: Per-App Information and Details Screen

**Feature**: `004-per-app-info` | **Input**: [spec.md](./spec.md) · [research.md](./research.md)

Feature 004 adds four pure domain types (`InstalledApp` + `AppInventory`,
`AppCategoryFilter`, `CoreTiers`, `RefreshMode`), one UI state holder
(`DetailsStateHolder`), and two persisted/derived flags (usage access, filter
selection). The 001–003 entities (`FigureUiState`, the four readings, `CoreCount`,
`ApplicationInventory`, `ThemePreference`, the 003 refresh cycle rules) are reused
unchanged — this file defines only what is new. Test identifiers extend the established
prefixes with `VA-` (validation, domain) and `VD-` (details holder) to keep the
features' test lists disjoint.

---

## 1 · InstalledApp

One application in the Details inventory (FR-004, FR-006/FR-007).

| Field | Type | Meaning / rules |
|-------|------|-----------------|
| `packageName` | String | Identity — non-blank, unique within an `AppInventory` (V-A1). |
| `displayName` | String | Label shown in the list — non-blank (V-A1); duplicates allowed (Edge Cases: two apps, one name). |
| `classification` | `AppClassification` (`SYSTEM` \| `USER`) | The system/user mark (FR-004); produced only by the shared `isSystemApplication` classifier (R-01). |
| `storageBytes` | `Long?` | Total storage consumed (code + data + caches), or `null` = not available — never a substitute value (FR-006/FR-007). `null` whenever usage access is not granted (R-02). |
| `memoryBytes` | `Long?` | Memory consumed or `null`. **`null` on current Android by design** — no public read path (R-03, spec §Clarifications); the field exists so a future platform API can light it up without a UI change. |

**Validation (`create`, V-A1)**: both names non-blank; any non-null byte value `≥ 0`.
Violations map to nothing — a single invalid app fails the whole inventory read
(`AppInventory.create` → `null` → `Unavailable`), per the 002 never-clamp policy
(FR-010).

## 2 · AppInventory

The sorted full inventory (one `InstalledAppReader` pass).

| Field / derived | Type | Meaning / rules |
|-----------------|------|-----------------|
| `apps` | `List<InstalledApp>` | Sorted alphabetically by `displayName` (locale `Collator`, primary strength) in the factory (V-A2) — the list's only ordering rule (spec assumption). |
| `totalCount` | derived | `apps.size` |
| `userCount` / `systemCount` | derived | counts by classification |

**Validation (`create(apps)`, V-A2)**: package names unique; empty list allowed (a
valid empty inventory renders the filter's explanatory empty state, Edge Cases).

Consistency with the home counts (SC-001/SC-002) is guaranteed upstream: both readers
enumerate via the same `QUERY_ALL_PACKAGES` call shape and classify via the same
`isSystemApplication` bit test (R-01) — no cross-check is encoded in the model.

## 3 · AppCategoryFilter

The Details list restriction (FR-005).

`ALL` (default) \| `USER` \| `SYSTEM`; pure function `apply(inventory): List<InstalledApp>`
— `ALL` returns the full sorted list, the others the matching classification subset
(V-A3). Filter changes never trigger reads (FR-005 "immediately", SC-007); selection
lives in `DetailsStateHolder` (§7).

## 4 · CoreTiers

The processor figure's value (FR-013, SC-008); replaces `CoreCount` as the home
processor figure's payload (`CoreCount` itself stays as the reader's fallback source).

| Field | Type | Meaning / rules |
|-------|------|-----------------|
| `tiers` | `List<CoreTier>` | Each `(count: Int, maxFrequencyHz: Long)`, ascending by frequency (V-A4). |
| `totalCount` | Int | Cores overall — equals `Runtime.availableProcessors()` in the fallback path. |

**Validation (`create`, V-A4)**: `totalCount ≥ 1`; tiers non-empty; each `count ≥ 1`;
tier counts sum to `totalCount`; frequencies distinct and > 0. Violation → `null` →
`Unavailable`.

**Display rule (V-A4b)**: `tiers.size == 1` renders as today's plain core count
(the fallback shape is indistinguishable from the 002 figure); `tiers.size > 1` renders
"N cores: a × f₁ + b × f₂" (R-10). The single-tier shape is what the reader produces
when the device does not distinguish core types (R-04) — FR-013's fallback.

## 5 · Usage access + reader-pass flags

Per inventory read pass, the reader also reports the usage-access grant state
(`AppOpsManager` check, R-02) alongside the inventory, so list and hint can never
disagree:

- `DetailsStateHolder.usageAccessGranted: Boolean?` — `null` until the first pass
  lands, then `true`/`false` as of that pass. `false` ⇒ every `storageBytes` in the
  same pass is `null` (V-D6) and the grant hint is shown (FR-008).

## 6 · RefreshMode

The persisted automatic-refresh selection (FR-014/FR-015).

`ON_DEMAND` (default) \| `THIRTY_SECONDS` \| `ONE_MINUTE` \| `FIVE_MINUTES`

- `intervalMillis: Long?` — `null` iff `ON_DEMAND` (V-R1); the ticker's sleep (R-08).
- `fromPersisted(raw: String?)` — missing/corrupt → `ON_DEMAND` (V-R2), mirroring
  `ThemePreference.fromPersisted`; the `SharedPreferences` store delegates to it (R-09).
- Persistence: `RefreshModeStore.load()/save()` over the existing prefs file, key
  `refresh_mode` (R-09). Fresh install → `ON_DEMAND` (FR-014, SC-006).

## 7 · DetailsStateHolder (UI state)

Mirrors `HomeStateHolder` (R-07); all state UI-thread-confined or main-thread posted
(inherited R-L1). Lifecycle = the 003 `RefreshCycle` rules verbatim (reset-on-start for
presentation, no-reset for refresh, absorption, epoch drop, executor stewardship) — see
[../003-pull-to-refresh/data-model.md](../003-pull-to-refresh/data-model.md) §1.

| Member | Type / behavior |
|--------|-----------------|
| `inventory` | `FigureUiState<AppInventory>` — `Loading` placeholders / `Available` sorted list / `Unavailable` indication (FR-010). |
| `filter` | `AppCategoryFilter` Compose state, starts `ALL`; `setFilter(f)` is pure selection — no read triggered (FR-005). |
| `applyPendingFilter(f)` | Consumes a Home arrival: sets `filter = f` (override rule, FR-012). |
| `usageAccessGranted` | `Boolean?` — §5. |
| `isRefreshing` / `refresh()` / `startReadCycle()` / `shutdown()` | 003 semantics, one figure instead of five (FR-009). |

The 003 `FigureUiState` transition table applies unchanged (refresh preserves
`Available`/`Unavailable` in place; posts apply only on current epoch).

## 8 · Navigation state (shell)

`Destination` gains `DETAILS` (R-05). Shell-owned:

- `destination: Destination` — starts `HOME`; footer indication derived from it (FR-003).
- `pendingDetailsFilter: AppCategoryFilter?` — set by a Home entry tap together with
  `destination = DETAILS`, consumed once by `applyPendingFilter`, then cleared (R-06).
  `null` means "no arrival directive" — plain tab switches never touch the filter.

## 9 · Validation summary — test-target list

JVM unit tests (fakes per R-11). Each row maps to a spec requirement:

| ID | Rule under test | Spec |
|----|-----------------|------|
| V-A1 | `InstalledApp.create` rejects blank names and negative bytes; accepts null bytes | FR-006/FR-007 |
| V-A2 | `AppInventory.create` sorts by display name (Collator), rejects duplicate package names, allows the empty list | spec assumption, Edge Cases |
| V-A3 | `AppCategoryFilter.apply` — ALL full list, USER/SYSTEM subsets, ordering preserved | FR-005, SC-002 |
| V-A4 | `CoreTiers.create` enforces sum/positivity/distinct-frequency rules; single-tier shape is valid | FR-013, SC-008 |
| V-A4b | Formatting: single tier renders as plain count; multi-tier renders "N cores: a × f₁ + b × f₂" | FR-013 |
| V-R1 | `RefreshMode.intervalMillis` — null iff ON_DEMAND; exact ms for the three intervals | FR-015 |
| V-R2 | `fromPersisted` — each name round-trips; missing/unknown/corrupt → ON_DEMAND | FR-014, SC-006 |
| V-D1 | Presentation cycle resets inventory to `Loading`; refresh cycle preserves `Available` in place | FR-010, FR-009 |
| V-D2 | Reader `null`/throw → `Unavailable`; recovery on next successful pass | FR-007, FR-010 |
| V-D3 | `setFilter` changes selection only — no reader call (assert fake call counts) | FR-005, SC-007 |
| V-D4 | `applyPendingFilter` overrides a previously chosen filter; plain recomposition does not | FR-012 |
| V-D5 | 003 cycle rules on the inventory figure: absorption, epoch drop, executor replacement, `isRefreshing` ordering | FR-009 |
| V-D6 | A pass reporting `usageAccessGranted=false` carries `storageBytes=null` for every app; `true` pass carries values | FR-006..FR-008 |

## 10 · Entities unchanged from features 001–003

`FigureUiState` (type + 003 transition rules), `MemoryReading`, `StorageReading`,
`BatteryReading`, `CoreCount` (still the reader's fallback source, R-04),
`ApplicationInventory` (home counts), `ThemePreference`, `RefreshCycle` semantics,
`Destination`'s hand-rolled-state pattern (extended, not replaced). See
[../002-home-screen/data-model.md](../002-home-screen/data-model.md) and
[../003-pull-to-refresh/data-model.md](../003-pull-to-refresh/data-model.md).
