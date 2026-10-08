# Quickstart: 006 Rebrand, Help Screen & Swipe Navigation

Local validation for feature 006. Everything here runs in the dev VM (Constitution IV/V);
device-only checks are at the bottom for the host.

## Prerequisites

- JDK 25 at `/usr/lib/jvm/bellsoft-java25.x86_64` (JAVA_HOME already points there).
- Android SDK via gitignored `local.properties` (`sdk.dir=/home/dev/Android/Sdk`).
- `ANDROID_HOME` is **not** exported by default — export it per shell.
- Root `gradle.properties` already carries the 2 GB heap fix (D8/R8 OOM guard).

## Automated gates (must be green before any task is "done")

```bash
export ANDROID_HOME=/home/dev/Android/Sdk
cd /home/dev/git/android-analyzer
./gradlew testDebugUnitTest   # existing 111 tests + new 006 tests
./gradlew lintDebug           # abortOnError = true
```

Expected: all tests pass (zero regressions from the package rename — R-08) and lint reports
zero errors.

## Identity & verbatim-content checks (spec SC-001/SC-005, R-07/R-08)

```bash
# old technical identity gone from source/build surface (also guards jktek(c) drift)
grep -rin "androidanalyzer" app/ build.gradle.kts settings.gradle.kts   # expect: no hits

# old display name gone from user-visible text + docs
grep -rn "Android Analyzer" app/src README.md                           # expect: no hits

# license embedded verbatim (byte-identical)
cmp LICENSE app/src/main/res/raw/license.txt                            # expect: silent

# new strings present
grep -c "Resource Radar" app/src/main/res/values/strings.xml            # expect: >= 1
```

(The `cmp` pair is also asserted by a unit test so it can't rot silently — R-07.)

## What each manual item proves (host, on an Android 15+ phone)

Install: `./gradlew assembleDebug` → sideload `app/build/outputs/apk/debug/app-debug.apk`.

| # | Action | Expected (contract ref) |
|---|--------|--------------------------|
| M-1 | Fresh install, view launcher | label "Resource Radar", icon is the blip-bars design, no double-rounding under the launcher mask (I-1, I-3) |
| M-2 | Long-press icon → themed/monochrome icon (Android 13+ launcher, themed icons on) | icon tints as a flat shape, not a colored blob (I-3) |
| M-3 | Start Shizuku authorization from Details | dialog names "Resource Radar" (I-1) |
| M-4 | On each of Home/Details/Settings/Help: swipe left, swipe right | adjacent screen + footer highlight follow; clamped at both ends (N-2, N-3) |
| M-5 | Home: pull-to-refresh, then swipe | pull still refreshes, swipe still pages — no gesture theft (N-5) |
| M-6 | Help: scroll the license text fast, then swipe mid-scroll | text scrolls; only a horizontal drag pages (N-5) |
| M-7 | Tap Details state before choosing an app (swipe there from Home) | existing no-selection state, no crash (004 behavior preserved) |
| M-8 | Tap the Shizuku link, then both Contacts links | browser opens each URL; back returns to the app unchanged (L-1, L-2) |
| M-9 | (Optional) disable browsers via `adb shell pm disable-user …browser…`, tap a link | snackbar appears, app alive (L-3) |
| M-10 | System back on Help / Settings / Details | returns to Home; back on Home exits (N-6) |
| M-11 | Settings auto-refresh ≠ "On demand", sit on Help | no network/CPU churn attributable to ticking (N-7) |

M-1..M-8 cover every acceptance scenario in the spec; M-9..M-11 are edge-case confirmations.
