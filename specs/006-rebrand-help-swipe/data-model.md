# Data Model: 006 Rebrand, Help Screen & Swipe Navigation

The spec's Key Entities section declares **not applicable**: this feature introduces no new data
entities and alters no stored data. This document therefore records the state surface it *does*
touch — navigation state, and the static content/resource inventory — so `tasks.md` and the
consistency check have one authoritative list.

## 1. Stored data: unchanged

| Store | Owner | 006 change |
|-------|-------|-----------|
| SharedPreferences (theme) | `SharedPreferencesThemeStore` (002) | none — file rides on the new applicationId after reinstall |
| SharedPreferences (refresh mode) | `SharedPreferencesRefreshModeStore` (004) | none — same |
| Shizuku authorization grant | held by the Shizuku app, keyed by applicationId | invalidated by design (FR-003 rename); user re-grants on the reinstalled app — no migration (spec: never published) |

## 2. Navigation state (shell)

**Destination enum** (`ui/AnalyzerApp.kt`) — extended:

```text
HOME → DETAILS → SETTINGS → HELP     # linear strip; enum order == footer order == swipe order
```

- Single source of truth: `pagerState.settledPage` (research R-01); `destination` derives from it.
- Transitions: adjacent moves via swipe (clamped — no wrap at either end, FR-013); arbitrary
  moves via footer tap / Home→Details entry directive (`pendingDetailsFilter`, unchanged).
- Back: any non-HOME destination → HOME (existing `BackHandler`, unchanged by adding HELP).
- Auto-refresh mapping: HOME → refreshHome, DETAILS → refreshDetails, SETTINGS/HELP → no-op.
- Lifecycle semantics per page: identical to today's tab-switch disposal (research R-03) —
  Home re-reads on re-entry; Details keeps its holder state while composed; no new saved state.

## 3. Static content inventory (all shipped in the APK, no runtime input)

| Item | Source | Surface |
|------|--------|---------|
| Display name "Resource Radar" | `strings.xml app_name` | launcher label, activity label, Shizuku auth dialog (system reads the label) |
| Rebranded hint strings | `strings.xml shizuku_hint_not_installed` / `shizuku_hint_awaiting` | Details guidance row |
| Help section titles + About/Limitations/Per-app-memory bodies | new `strings.xml` entries (English only) | `ui/help/HelpScreen.kt` |
| Contact targets | constants: `https://www.linkedin.com/in/jurijskolomijecs/`, `https://jkteknologies.com/` (spec FR-011) | Help Contacts rows |
| Shizuku link target | `https://shizuku.rikka.app` (spec FR-005) | Details hint link |
| AGPL v3 text | `res/raw/license.txt` — byte-identical copy of repo `LICENSE` | Help License section (one `Text`) |
| Launcher icon layers | `ic_launcher_background/foreground/monochrome.xml` vectors from `design/logos/b-blip-bars.svg` | adaptive icon (`mipmap-anydpi-v26/ic_launcher.xml`) |

**Validation rules** (from spec, enforced by tests/greps in quickstart.md):
- `license.txt` byte-equals `LICENSE` (unit test, SC-005).
- Zero user-visible occurrences of "Android Analyzer" (grep over `res/` + README, SC-001).
- Zero repo-tree occurrences of the old package segment `androidanalyzer` (FR-003; also guards
  the jktek**n**ologies homoglyph drift).

## 4. Identity rename map (FR-003)

| From | To |
|------|-----|
| package/namespace/applicationId `com.jkteknologies.androidanalyzer` | `com.jkteknologies.resourceradar` |
| `Theme.AndroidAnalyzer` | `Theme.ResourceRadar` |
| AIDL package `…androidanalyzer.data.shizuku` | `…resourceradar.data.shizuku` (file contents + tree) |
| display name "Android Analyzer" (strings + README) | "Resource Radar" |

`${applicationId}.shizuku` provider authority and the `${applicationId}.DYNAMIC_RECEIVER…`
removal directives are parameterized — no manifest edits needed for the rename.
