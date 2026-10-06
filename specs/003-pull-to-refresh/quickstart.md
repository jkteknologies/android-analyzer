# Quickstart: Pull-to-Refresh Validation

**Feature**: `003-pull-to-refresh` | **Input**: [spec.md](./spec.md) · [contracts/refresh-interaction.md](./contracts/refresh-interaction.md)

How to prove the feature works end-to-end. Local automated gates run entirely in the VM
(Constitution IV/V); manual scenario checks need any Android 15+ device or emulator with
`adb` (no KVM in this VM — a physical device sideload is the faster path, both are
valid). Implementation-level detail lives in tasks.md, not here.

## 0 · Prerequisites

- JDK for Gradle (`JAVA_HOME` → installed JDK; established in feature 001), Android SDK
  with `platforms;android-37.0` — see the repo README.
- `adb` available; a connected Android 15+ device, or a created AVD.
- Install: `./gradlew installDebug` (debug APK — no release signing in this environment).

## 1 · Automated local gates (must pass before any manual check)

```bash
./scripts/verify.sh        # clean build + JVM unit tests + AGP lint (feature 001 entry point)
# equivalently, individually:
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

**Expected**: all green. The suite now additionally covers the refresh contract
[VR-1..VR-10](./data-model.md#5-validation-summary--test-target-list) through the fakes
seam: no-reset semantics, absorption/coalescing, indicator lifecycle ordering, epoch
drops, executor replacement, and `Unavailable`-recovery — all on the JVM, no emulator.

## 2 · Manual scenario checks

Each row maps to acceptance scenarios in the spec. Ground truth for figures via
`adb shell cat /proc/meminfo` (MemTotal, MemAvailable), `adb shell df /data`,
`adb shell dumpsys battery`; application counts via
`adb shell pm list packages -3 | wc -l` (non-system) and
`adb shell pm list packages | wc -l` (total).

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| M-1 | Pull reloads changed state (US1-1/2, SC-001) | Note the Applications figure; install or uninstall any app (`adb install`/`adb uninstall`); pull down on the home screen and release | Indicator runs, then the Applications figure updates to the new counts matching `pm list packages` totals; memory/storage/battery re-read to current ground truth |
| M-2 | Partial pull cancels (US1-3, FR-002) | Pull part-way down; release before the indicator arms | Content springs back; no indicator run; figures unchanged (values did not re-read — no flicker) |
| M-3 | Unchanged values don't flash (US1-4, FR-004) | Pull and release twice with no device-state change in between | Values stay visible throughout — no placeholder ("—") flash; layout does not shift or jump |
| M-4 | Indicator timing + responsiveness (US2-1/2/4, SC-002/003) | Pull and release; watch the indicator and try the footer buttons while it runs | Indicator appears at release and dismisses ≤ 1 s after figures settle (settle ≤ 2 s total); previous values visible the whole time; footer remains tappable, no frozen frames |
| M-5 | Navigate away mid-refresh (US2-3, FR-011) | Trigger a refresh and immediately tap "Settings", then return to "Home screen" | No crash; on return the figures show fresh presentation reads (placeholders then values); no stale or jumbled values |
| M-6 | Rapid pulls + pull during initial load (US3-1/2, FR-006) | (a) Trigger a refresh, then pull-release twice more while it runs. (b) Cold-launch the app and pull-release the moment figures are still "—" | One indicator run per settle; figures settle exactly once; after (b) every figure lands from the single initial pass — no duplicated or stale values |
| M-7 | System top-edge gesture wins (US3-3, FR-008) | Swipe down starting at the very top screen edge (status bar area) | The notification shade opens as usual; the app does not intercept or block it |
| M-8 | Screen-reader operation (US3-4, FR-013, SC-006) | Enable TalkBack; focus the figure list, open actions menu, activate "Refresh figures"; alternatively perform the down-then-up gesture | Refresh runs; "Refreshing figures" is announced, then "Figures refreshed" on completion; figure semantics ("label: value") unchanged |
| M-9 | Largest font scale + landscape (Edge Cases, FR-013) | Set system font scale to maximum; rotate to landscape; repeat M-1 | Gesture, indicator, figures, and footer render and operate without clipped or overlapping content |

**Feature verdict**: all automated gates green + every row above behaves as expected ⇒
SC-001..SC-006 hold and the feature is validated.
