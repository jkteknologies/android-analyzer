# Quickstart: Per-App Information Validation

**Feature**: `004-per-app-info` | **Input**: [spec.md](./spec.md) · [contracts](./contracts/)

How to prove the feature works end-to-end. Local automated gates run entirely in the VM
(Constitution IV/V); manual scenario checks need any Android 15+ device or emulator with
`adb` (no KVM in this VM — a physical device sideload is the faster path; both are
valid). Implementation-level detail lives in tasks.md, not here.

## 0 · Prerequisites

- JDK for Gradle (`JAVA_HOME` → installed JDK; feature 001), Android SDK with
  `platforms;android-37.0` — see the repo README.
- `adb` available; a connected Android 15+ device, or a created AVD.
- Install: `./gradlew installDebug` (debug APK — no release signing in this
  environment).

## 1 · Automated local gates (must pass before any manual check)

```bash
./scripts/verify.sh        # clean build + JVM unit tests + AGP lint (feature 001 entry point)
# equivalently, individually:
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

**Expected**: all green. The suite now additionally covers
[V-A1..V-A4b, V-R1..V-R2, V-D1..V-D6](./data-model.md#9-validation-summary--test-target-list)
through the fakes seam: inventory/filter/tier/mode domain rules, formatting, and the
details holder's cycle, filter, override, and grant-coupling behaviors — all on the JVM,
no emulator.

## 2 · Manual scenario checks

Ground truth via `adb`: user apps `pm list packages -3 | wc -l`, all apps
`pm list packages | wc -l`, system apps `pm list packages -s | wc -l`; per-app storage
via the device's own Settings → Apps → *app* → Storage.

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| M-1 | Full inventory, marks, counts (US1-1, SC-001) | Open the app → footer "Details" | List shows every installed app with a System/User mark; entry count = `pm list packages \| wc -l`; System/User entry counts equal the `-s` / `-3` totals |
| M-2 | Usage-access grant flow (US1-3..5, FR-006..FR-008) | Open Details before granting; note the hint row; grant via the button (Settings → Usage access → Android Analyzer); return, pull-to-refresh | Before: every storage slot shows "Not available", list/filter fully usable. After grant + refresh: storage figures appear; spot-check two apps against device Settings — equal within display rounding (SC-003); every memory slot shows "Not available" |
| M-3 | Filter + empty state (US1-2, SC-002) | Switch All / User / System; on a fresh emulator pick User | Filter applies immediately (< 1 s, no reload); counts under each filter match the Home entries; User on a zero-user-apps device shows the explanatory empty state, not a blank screen |
| M-4 | Row tap → system app info (US1-7, FR-018) | Tap any row; press back | The device's app-info page opens for exactly that app; returning restores the Details list with its filter unchanged; no crash |
| M-5 | Pull-to-refresh on Details (US1-6, FR-009) | `adb install` any APK (or uninstall one); pull the list down and release | Indicator runs; the inventory reconciles to the new device state; values kept on screen during the reload (no placeholder flash) |
| M-6 | Footer: three tabs, restyle (US2-1..3, SC-005) | Inspect the footer; tap each tab from each screen | Three equal-width buttons — Home, Details, Settings — square corners, no gaps, one continuous bar; exactly one active indication; every screen reachable in one tap |
| M-7 | Home drill-down + override (US3, SC-004) | On Home, read the two application entries; tap "System applications"; later set Details to User manually, go Home, tap "System applications" again | Two separate count entries (no combined "N (M)" row); each tap lands on Details already filtered to that category with the filter control reflecting it — no full-list flash; the Home tap overrides the previously chosen filter |
| M-8 | Auto-refresh setting (US4, SC-006) | Settings → check defaults; select "Every 30 seconds"; install an app via `adb`; watch Details untouched; kill and relaunch the app | Fresh state: "On demand" selected, Theme section intact; with 30 s selected the visible screen's counts update within ~30 s with no gesture; the selection survives restart; pull-to-refresh still works in both modes |
| M-9 | No background refresh (US4-5, FR-017) | With 30 s selected, press Home (app to background) for ~2 min, return | Nothing refreshed while invisible (figures unchanged from backgrounding moment until return); ticking resumes on return (next tick within the interval) |
| M-10 | Processor tiers (US5, SC-008) | On the emulator: read the Home processor entry. On big.LITTLE hardware if available: same | Emulator (single cluster): plain total core count — the fallback rendering; tiered hardware: per-type counts (e.g. "8 cores: 4 × 1.8 GHz + 4 × 2.4 GHz") summing to the device's core count |
| M-11 | Accessibility & scale (Edge Cases, FR-009) | TalkBack: footer buttons, filter, rows, "Refresh figures" action on Details; set max font scale + landscape; repeat M-3 | Buttons/rows/filter announce roles, states, and "label: value" pairs; refresh action works from the actions menu; nothing clips or overlaps at max scale |

**Feature verdict**: all automated gates green + every row above behaves as expected ⇒
SC-001..SC-008 hold and the feature is validated.
