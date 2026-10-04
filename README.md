# Android Analyzer

Android app that analyzes resource consumption and suggests improvements.
Current status: **launchable skeleton** (feature `001-android-app-skeleton`) — a single
Activity showing a full-screen "Android Analyzer" placeholder, zero permissions, no
network, no background work.

[![CI](https://github.com/jkteknologies/android-analyzer/actions/workflows/ci.yml/badge.svg)](https://github.com/jkteknologies/android-analyzer/actions/workflows/ci.yml)

## Prerequisites (one-time setup)

| Requirement | Version / detail |
|---|---|
| JDK | **21 or newer** on `JAVA_HOME` (Eclipse Temurin 21 LTS recommended; the dev VM currently runs BellSoft JDK 25) |
| Android SDK | cmdline-tools, then `sdkmanager "platforms;android-37.0" "platform-tools"` — build-tools are selected automatically by AGP on first run (licenses must be accepted via `sdkmanager --licenses`) |
| `ANDROID_HOME` **or** `ANDROID_SDK_ROOT` | must point at the SDK root containing `platforms/` and `build-tools/` |
| Devices / emulators / accounts | **not required** for build, tests, lint, or CI |

> Note: `compileSdk` is 37 because the pinned 2026 library set (Compose BOM
> `2026.09.00`, lifecycle `2.11.0`, activity `1.13.0`) requires it. The app itself
> still installs and targets **Android 15 (API 35)** and newer: `minSdk = targetSdk = 35`.

The **first** `./gradlew` invocation downloads the Gradle 9.8.0 distribution and all
dependencies from Google Maven and Maven Central (network needed once); later runs work
offline (`--offline`) against the warm `~/.gradle` cache.

## Build

The canonical build command (FR-001):

```bash
./gradlew build
```

## Local verification (the one documented entry point)

```bash
./scripts/verify.sh
```

`verify.sh` prechecks `JAVA_HOME` and the Android SDK (actionable message + exit 1 on
problems), then runs the fixed check set in order:

```bash
./gradlew build testDebugUnitTest lint
```

Expected success output — exactly:

```
PASS: build, testDebugUnitTest, lint
```

with exit code `0`. On failure the script stops at the first failing check, names the
failing Gradle task and the detail it produced — the failing **test class + method**
for test failures, or **`<file>: <issue-id>`** for lint errors — and exits `1`.
Lint warnings are reported but never affect the exit code. The script takes no
arguments and needs no devices or credentials.

### Report locations

| Check | Report |
|---|---|
| Unit tests (HTML) | `app/build/reports/tests/testDebugUnitTest/` |
| Unit tests (JUnit XML) | `app/build/test-results/testDebugUnitTest/` |
| Lint (text, per variant) | `app/build/reports/lint-results-debug.txt` |
| Lint (HTML/SARIF) | `app/build/reports/lint-results-debug.{html,sarif}` |

## CI

Every push and every pull request runs the same fixed check set on GitHub-hosted
`ubuntu-latest` runners (see [`.github/workflows/ci.yml`](.github/workflows/ci.yml)):
Temurin JDK 21, Gradle dependency caching, the runner's preinstalled Android SDK, and
`./gradlew build testDebugUnitTest lint` directly. No emulator, no devices, no secrets.

## Manual launch check (optional)

Install the debug APK (`app/build/outputs/apk/debug/app-debug.apk`) on an Android 15+
emulator/device and launch it: a full-screen placeholder reading **"Android Analyzer"**
appears (cold start well under 5 s; identical on rotation; zero permission prompts).

## Expected outcomes

| Check | Command / trigger | Success looks like | Budget | Measured (dev VM, 4 cores/8 GB) |
|---|---|---|---|---|
| Build | `./gradlew build` | `BUILD SUCCESSFUL` | ≤ 5 min clean (SC-001) | 1 m 20 s clean (warm dependency cache) |
| Full local verify | `./scripts/verify.sh` | `PASS: build, testDebugUnitTest, lint`, exit 0 | ≤ 10 min (SC-002) | 26 s incremental / 1 m 22 s from clean |
| Offline build | `./gradlew build --offline` | `BUILD SUCCESSFUL` | valid after first warm run (SC-006) | 39 s clean, fully offline |
| CI | push / PR | green run, all three checks pass | ≤ 15 min cold cache (SC-003) | see the Actions badge above |
| App launch (optional, manual) | install on API 35+ | "Android Analyzer" placeholder ≤ 5 s | SC-005 | not measured (no emulator on dev VM) |
| Permission surface | `aapt2 dump permissions app-debug.apk` | empty output | zero permissions (FR-009) | 0 `uses-permission` entries |

## License

AGPL-3.0 — see [LICENSE](LICENSE).
