# Quickstart: Build, Verify, and CI

**Branch**: `001-android-app-skeleton` (work branch: `feature/001-init`) | **Date**: 2026-10-03
**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Research**: [research.md](./research.md) · **Contracts**: [contracts/](./contracts/)

This page gets a contributor from clean clone to "all checks pass". It is the human-facing
companion to the verification contract ([contracts/verification.md](./contracts/verification.md));
`README.md` in the repo will restate the essentials (FR-011).

## Prerequisites

| Requirement | Version / detail | Notes |
|---|---|---|
| JDK | **21 LTS** (Eclipse Temurin) | R-03; must be on `JAVA_HOME` |
| Android SDK | cmdline-tools + **platform `android-35`** + platform-tools + **build-tools as requested by AGP** | R-12; build-tools version intentionally not pinned (R-02) |
| `ANDROID_HOME` **or** `ANDROID_SDK_ROOT` | pointing at the SDK root (`platforms/`, `build-tools/` present) | Checked by `scripts/verify.sh` precheck |
| OS | Linux (CI: `ubuntu-latest`) | POSIX shell for `verify.sh` |
| Devices / emulators / accounts | **not required for build, tests, lint, or CI** | FR-004, FR-008 |

> **Environment note (2026-10-03):** the dev VM currently has **no `java`, no Android
> SDK, no `gradle`** installed — all three must be installed before the first run
> (R-14). One-time installs: Temurin 21 via package manager or Adoptium tarball;
> Android cmdline-tools, then `sdkmanager "platforms;android-35" "platform-tools"`.

## Setup

```bash
git clone <repo-url> && cd android-analyzer
# install JDK 21 + Android SDK as above, set JAVA_HOME / ANDROID_HOME
```

No other project-level setup exists — no local config files, no credentials (FR-008).

## First Run

The **first** `./gradlew` invocation downloads the Gradle 9.8.0 wrapper distribution and
all dependencies (6 runtime libs + 1 test lib, R-14) from Google Maven and Maven
Central. This needs network; every later run works with `--offline` (SC-006). Nothing is
written outside the checkout except Gradle caches under `~/.gradle` (Constitution IX).

## Local Verification

**Canonical build** (FR-001, SC-001 ≤ 5 min):

```bash
./gradlew build
```

**Single documented verification entry point** (FR-006, US2; ≤ 10 min, SC-002):

```bash
./scripts/verify.sh
```

Expected success output:

```
PASS: build, testDebugUnitTest, lint
```

Exit code `0`. On any failure the script stops at the first failing Gradle check and
names the detail — failing **test class + method**, or **`<file>: <issue-id>`** for a
lint error — with exit code `1`. Warnings never change the exit code. Prerequisite
problems (missing `JAVA_HOME` / SDK env) produce an actionable precheck message before
Gradle runs. The script accepts no arguments and needs no devices, emulators, or
credentials.

Run from the repository root. `scripts/verify.sh` and CI run the identical fixed check
set (`./gradlew build testDebugUnitTest lint`) — locally and in CI you are testing the
same things.

## CI

`.github/workflows/ci.yml` (R-13): triggers on **every push and every pull request**;
runs on `ubuntu-latest` with Temurin 21 (`actions/setup-java`) and Gradle caching
(`gradle/actions/setup-gradle`); executes `./gradlew build testDebugUnitTest lint`
directly on GitHub-hosted runners with their preinstalled Android SDK. No emulator, no
devices, no secrets. Cold-cache wall time budget: **≤ 15 min** (SC-003).

## Expected Outcomes

| Check | Command / trigger | Success looks like | Budget |
|---|---|---|---|
| Build | `./gradlew build` | `BUILD SUCCESSFUL` | ≤ 5 min (SC-001) |
| Full local verify | `./scripts/verify.sh` | `PASS: build, testDebugUnitTest, lint`, exit 0 | ≤ 10 min (SC-002) |
| CI | push / PR | green run, all three checks pass | ≤ 15 min cold cache (SC-003) |
| App launch (manual, optional) | install on API 35 emulator | "Android Analyzer" placeholder, first frame ≤ 5 s | SC-005 |

All checks green with **zero warnings-as-failures** is the clean baseline (SC-004);
lint warnings may appear and are non-fatal (US2).
