# Quickstart: Shizuku Per-App Memory Validation

**Feature**: `005-shizuku-memory` | **Input**: [spec.md](./spec.md) · [contracts](./contracts/)

How to prove the feature works end-to-end. Local automated gates run entirely in the VM
(Constitution IV/V); the manual matrix needs an Android 15+ device or emulator with
`adb` plus Shizuku installed there. Implementation-level detail lives in tasks.md, not
here.

## 0 · Prerequisites

- JDK for Gradle (`JAVA_HOME`), Android SDK with `platforms;android-37.0` — repo README.
- `adb` available; a connected Android 15+ device, or a created AVD (`/dev/kvm` exists in
  this VM; a physical device sideload is still the faster path — both are valid).
- Install the analyzer: `./gradlew installDebug` (debug APK, no release signing here).
- **Shizuku setup on the test device** (the feature's external dependency, spec
  assumption):
  1. Sideload Shizuku v13.6.0+ (F-Droid APK or GitHub release): `adb install
     shizuku.apk`.
  2. Start the Shizuku server via adb (the documented path for development):
     `adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh`
     (on the emulator the path is `/storage/emulated/0/Android/data/...` — same file).
  3. Open the Shizuku app once and confirm it reports "Running".
- Ground-truth commands used below: `adb shell dumpsys meminfo <pkg>` (the device's own
  per-app report — read the TOTAL PSS line), `adb shell dumpsys meminfo --checkin` for
  process lists, `adb shell am force-stop moe.shizuku.privileged.api` (stops server and
  app).

## 1 · Automated local gates (must pass before any manual check)

```bash
./scripts/verify.sh        # clean build + JVM unit tests + AGP lint (feature 001 entry point)
# equivalently, individually:
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

**Expected**: all green. The suite additionally covers
[V-S1..V-S6](./data-model.md#9-validation-summary--test-target-list) through the fakes
seam: aggregation and merge rules (absent → 0, failure → null, negative → null), the
state ↔ memory coupling per pass, transition-triggered auto refresh, the sticky
no-refresh guard, and request delegation — all on the JVM, no Shizuku.

## 2 · Manual scenario checks

| # | Scenario | Steps | Expected |
|---|----------|-------|----------|
| M-1 | No Shizuku: honest default (US2-1, SC-002) | With Shizuku not installed, open the app → Details | One guidance row: Shizuku named, free on F-Droid, the three steps; every memory slot shows "Not available"; list, marks, filter, counts, storage figures, row tap, pull-to-refresh all work exactly as in 004 |
| M-2 | Full flow: install → start → allow → figures (US1-1..4, SC-001/SC-003) | Set up Shizuku (§0), open Details, tap "Allow access", confirm in Shizuku's dialog | After the dialog: figures appear **by themselves** (no restart, no manual pull — FR-007); spot-check 3 apps against `adb shell dumpsys <pkg> meminfo`'s TOTAL PSS — equal within display rounding, never a wrong number; an installed-but-not-running app (verify via the checkin process list) shows 0, not "Not available" |
| M-3 | Multi-process summing (US1-3, FR-002) | Pick a multi-process app (e.g. one with `:service` processes in `dumpsys meminfo --checkin`) | The analyzer's figure equals the sum of that app's process PSS entries (≈ the device report's app TOTAL) |
| M-4 | Guidance per state + open action (US2-2..3) | With Shizuku stopped (`am force-stop`), open Details and read the row; tap "Open Shizuku"; start it there; return | "Not running" text + button; the button opens the Shizuku app; on return the next read shows the new state (authorized → figures) without an app restart |
| M-5 | Outdated (Edge Cases, R-03) | Optional: install an old Shizuku (< v13) | Guidance asks to update Shizuku; memory stays "Not available"; everything else works |
| M-6 | Revoke / stop mid-use, 3+ cycles (US3, SC-004) | With figures displayed: revoke the analyzer in Shizuku's app; then re-allow; then `am force-stop` Shizuku; then restart via §0 — repeat ≥ 3 times, analyzer open throughout | Each change: guidance updates (at the latest by the next refresh), figures revert to "Not available" and return, no crash or freeze across all cycles; the guidance always names the current state |
| M-7 | Death mid-refresh (US3-3) | Start a pull-to-refresh and immediately `am force-stop` Shizuku | The refresh completes; already-read figures stay, unread show "Not available"; no error screen; the row flips to "Not running" |
| M-8 | Performance at inventory scale (SC-005) | On a device/emulator with a large app set (or after bulk-installing test APKs), time a full Details refresh with figures | Completes within 10 s; the UI stays responsive throughout (list scrolls during refresh) |
| M-9 | Accessibility (Edge Cases, guidance contract §G5) | TalkBack: read the guidance row in each state; change state (M-6) with focus elsewhere | The guidance announces state changes (polite live region); the row's button keeps button semantics; nothing clips at max font scale |

**Feature verdict**: all automated gates green + every row above behaves as expected ⇒
SC-001..SC-006 hold and the feature is validated.
