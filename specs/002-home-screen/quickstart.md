# Quickstart: Home Screen Validation

**Feature**: `002-home-screen` | **Input**: [spec.md](./spec.md) · [contracts/ui-contracts.md](./contracts/ui-contracts.md)

How to prove the feature works end-to-end. Local automated gates run entirely in the VM
(Constitution IV/V); manual scenario checks need any Android 15+ device or emulator with
`adb`. Implementation-level detail lives in tasks.md, not here.

## 0 · Prerequisites

- JDK for Gradle (`JAVA_HOME` → installed JDK; established in feature 001), Android SDK
  with `platforms;android-37.0` (compile target) — see the repo README.
- `adb` available; a connected Android 15+ device, or a created AVD. Note: the dev VM has
  no KVM, so an emulator boots slowly — a physical device sideload is the faster path;
  both are valid for the checks below.
- Install: `./gradlew installDebug` (debug APK — no release signing in this environment).

## 1 · Automated local gates (must pass before any manual check)

```bash
./scripts/verify.sh        # clean build + JVM unit tests + AGP lint (feature 001 entry point)
# equivalently, individually:
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

**Expected**: all green. The unit suite covers data-model validation rules
[V-1..V-10](./data-model.md#7-validation-summary-test-target-list), the classification
rule (FR-005), formatting ("36 (121)"), theme resolution, and figure-state transitions —
all via the fakes seam of [contracts/device-readers.md](./contracts/device-readers.md).

## 2 · Manual scenario checks

Each row maps to acceptance scenarios in the spec. Compare displayed figures against the
shell-reported ground truth (R-15). After each device-state change below, re-enter the
home screen (footer button or re-launch) so a fresh read cycle runs (FR-011).

| # | Scenario | Steps | Expected |
|---|---|---|---|
| M-1 | Resource figures (US1-1/2, SC-001/002) | Launch app; compare figures vs `adb shell cat /proc/meminfo` (MemTotal, MemAvailable), `adb shell df /data`, `adb shell dumpsys battery` | All figures present ≤ 2 s after launch; available + allocated ≤ MemTotal; free + used ≤ /data total; battery % and charging state match `dumpsys battery` |
| M-2 | App counts (US1-3/4, SC-003) | Compare Applications figure vs `adb shell pm list packages \| wc -l` (total) and `adb shell pm list packages -3 \| wc -l` (non-system) | Displayed `"N (M)"` equals the two shell counts |
| M-3 | Placeholder behavior (FR-014) | Cold-launch the app (or `adb shell am force-stop` + relaunch) and watch the first frames | Screen renders immediately; figures appear as neutral placeholders and fill in independently |
| M-4 | System theme following (US2-1/2/3, SC-004) | Default settings; `adb shell cmd uimode night yes` / `night no` while app is foreground; then background the app, switch, and resume | App switches dark/light live without restart; correct theme on next resume |
| M-5 | Footer navigation (US3) | Tap "Settings" / "Home screen" alternately; check current-destination indication; system-back from settings | Exactly two footer buttons, left/right per FR-007; indication matches shown screen; back gesture returns home; rapid taps stay consistent (edge case) |
| M-6 | Theme setting & persistence (US4, SC-005, FR-009/010) | Settings → select Light, Dark, System default; each time observe immediate change; force-stop and relaunch after a manual selection | Only one setting with exactly three options; applies < 1 s; selection survives restart; System default returns to following the device |
| M-7 | Largest font scale (FR-015) | `adb shell settings put system font_scale 2.0` (revert with 1.0 afterwards); walk home + settings screens | Nothing clipped/overlapping; content scrolls if needed; figures readable |
| M-8 | Screen reader (FR-015, A-1) | Enable TalkBack; swipe through home figures, footer, settings options | Each figure announced as label + value (or "Not available"); buttons/options announce role and state |

### Unavailability indication (FR-012) — optional drill

On an emulator you can simulate a missing battery read is hard; instead rely on the unit
suite's fake-driven `Unavailable` tests plus this observational check: no launch on any
tested device/emulator may show a negative figure, a value > total, or a crash when a
figure is slow — those are the FR-012/edge-case observable surfaces.

## 3 · What "done" looks like

- `./scripts/verify.sh` exits 0 on a clean tree.
- M-1 through M-7 pass on at least one Android 15+ device or emulator (M-8 if TalkBack is
  available).
- Manifest diff for this feature shows exactly one added permission
  (`QUERY_ALL_PACKAGES`, justified in [research.md R-01](./research.md)) and no other
  manifest surface growth (SC-006).
