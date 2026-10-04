# Contract: Verification Entry Point (`scripts/verify.sh`)

**Branch**: `001-android-app-skeleton` (work branch: `feature/001-init`) | **Date**: 2026-10-03
**Governs**: FR-005, FR-006, FR-011 · US2 · SC-002, SC-004, SC-006
**Decision source**: [research.md](../research.md) R-05 (error-strictness), R-06 (local gates), R-10 (entry-point shape)

This is the single documented verification entry point for the project (FR-006). Its
exact command name and behavior are fixed here; implementation must not extend or
rename it without a spec change.

## Command

```bash
./scripts/verify.sh
```

Run from the repository root. POSIX `sh`-compatible script with `set -euo pipefail`,
executable bit set. No arguments, no flags, no modes — invoking it is one command (US2).

## Inputs

| Input | Requirement | On failure |
|---|---|---|
| Arguments | none accepted | n/a (none defined) |
| `JAVA_HOME` | must be set; `$JAVA_HOME/bin/java` must exist and be JDK 21-compatible | **Precheck error** — actionable message (see Precheck below), exit 1 |
| `ANDROID_HOME` **or** `ANDROID_SDK_ROOT` | at least one must be set and point at a recognizable SDK layout (`platforms/` and `build-tools/` present) | **Precheck error** — actionable message, exit 1 |
| Network | only needed on the first run (wrapper + dependency download, R-14); `--offline` valid after first warm run (SC-006) | Gradle fetch failure surfaces as check failure |
| Devices / emulators / credentials | **none required or accepted** | n/a (FR-008) |

## Behavior (fixed check set, in order, fail-fast)

1. **Precheck** — validate `JAVA_HOME` and `ANDROID_HOME`/`ANDROID_SDK_ROOT` as above.
   No Gradle invocation happens until both pass.
2. **Run the fixed check set from the repo root**, in this exact order, stopping at the
   first failure:

   ```bash
   ./gradlew build testDebugUnitTest lint
   ```

   - `build` — compiles and assembles debug/release variants (FR-001's canonical build,
     SC-001 ≤ 5 min).
   - `testDebugUnitTest` — JUnit 4 JVM unit tests (FR-004, no devices).
   - `lint` — AGP built-in Android Lint; lint **errors** fail the build (FR-005,
     R-05); warnings are reported but never fail (US2, spec edge case).

3. **Consolidated output** — the script prints one summary line plus failure details:
   - Success:
     ```
     PASS: build, testDebugUnitTest, lint
     ```
   - Failure at check N: stop immediately, print the failing Gradle task name and the
     named detail the task itself produces —
     - unit tests → failing test class and method, from the Gradle test report
       (`app/build/reports/tests/…`);
     - lint error → `<file>: <issue-id>` pair, from the lint text report
       (`app/build/reports/lint-results.txt`).

## Exit Codes

| Code | Meaning |
|---|---|
| `0` | All three checks passed |
| `1` | Precheck failed, or the first check in the set failed |

Warnings (lint warnings, deprecation notices) **never** affect the exit code (US2).

## Error Message Requirements (US2 scenarios)

| Failure | Required user-visible detail |
|---|---|
| `JAVA_HOME` unset / `java` missing | State which variable is missing and what to install (JDK 21, R-03); point at README prerequisites (FR-011) |
| SDK env unset / layout unrecognized | State which variable is missing and expected layout (`platforms/`, `build-tools/`); point at README |
| Failing unit test | Gradle task `testDebugUnitTest` failed; test class + method named in output |
| Lint error | Gradle task `lint` failed; `<file>: <issue-id>` named in output |

## Relationship to CI

CI (`.github/workflows/ci.yml`, R-13) runs the same three Gradle checks **directly**
(`./gradlew build testDebugUnitTest lint`) — it does **not** invoke `verify.sh`. The
script is the documented *local* entry point; the contract's fixed check set guarantees
local and CI verify the same things (FR-007, US3).
