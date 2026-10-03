# Research: 001-android-app-skeleton

**Branch**: `001-android-app-skeleton` (work branch: `feature/001-init`) | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md)

This file resolves every `NEEDS CLARIFICATION` item from the Technical Context in
[plan.md](./plan.md). Each decision uses the format: **Decision / Rationale / Alternatives
considered**. All version pins were verified against authoritative registries on 2026-10-03
(Google Maven `dl.google.com/android/maven2`, Maven Central `repo1.maven.org`,
`gradle.org/releases`). Registry metadata timestamps are cited where relevant.

> Methodology note: the `latest>` tag in `maven-metadata.xml` includes pre-releases
> (alphas/betas/RCs). "Latest stable" below means the highest version without a
> pre-release suffix in the `<version>` list. Doc-page headlines can lag registry
> metadata (see R-01) — the registry list is treated as authoritative.

---

## R-01: Android Gradle Plugin version

**Decision**: AGP **9.4.1** (`com.android.tools.build:gradle:9.4.1`).

**Rationale**: The Google Maven metadata for `com/android/tools/build/gradle` lists the
stable sequence ending `… 9.3.x, 9.4.0, 9.4.1` followed by `9.5.0-alpha01…alpha08`
(metadata timestamp 2026-10-01). The highest stable version is therefore **9.4.1**.
Note: developer.android.com's release-notes headline still reads "Android Gradle plugin
9.4.0 (September 2026)" — an example of headline lag; the registry list is authoritative.
AGP 9.x ships **built-in Kotlin support** (the standalone `org.jetbrains.kotlin.android`
plugin is no longer required; Google documents a "Migrate to built-in Kotlin" path), and
requires Gradle 9.x and JDK 17+ — both satisfied by R-02 and R-03.

**Alternatives considered**:
- *9.4.0* — the headline version; valid but not the latest stable in the registry.
- *9.5.0-alpha08* — pre-release; violates the "latest **stable**" requirement.
- *8.x line* — older major; conflicts with the modern-stack goal (Constitution VI/VII).

## R-02: Gradle version

**Decision**: Gradle **9.8.0** (wrapper `gradle-9.8.0-bin.zip`).

**Rationale**: gradle.org/releases lists 9.8.0 (2026-09-24) as the current stable, ahead
of 9.7.1 / 9.7.0 / 9.6.x / 9.5.1; 8.14.5 (2026-05-07) is the last 8.x. Satisfies AGP 9.x's
Gradle 9.x requirement.

**Alternatives considered**:
- *9.7.1 / earlier 9.x* — stable but superseded; no reason to lag a fresh skeleton.
- *8.14.x* — incompatible with AGP 9.x minimums and an older major.
- *Snapshot/RC* — not stable; rejected.

## R-03: JDK version

**Decision**: JDK **21 LTS** (Eclipse Temurin) locally and in CI.

**Rationale**: JDK 21 is the current LTS line, satisfies AGP 9.x's JDK 17+ requirement,
and is directly available to GitHub Actions via `actions/setup-java` (`temurin`
distribution). The dev VM has no JDK installed; FR-011 documents installation as a
prerequisite rather than a planning blocker.

**Alternatives considered**:
- *JDK 17 LTS* — meets AGP minimum but is the older LTS; 21 has a longer support window.
- *JDK 25* — newest LTS but newer than what Android tooling docs target for the 9.4 line;
  no benefit for a skeleton; rejected for compatibility caution.
- *System-default OpenJDK via distro packages* — version drift risk across machines; a
  named distribution (Temurin) is reproducible.

## R-04: Kotlin version

**Decision**: Kotlin **2.4.20** (stdlib/test framework alignment).

**Rationale**: Maven Central metadata for `org.jetbrains.kotlin:kotlin-stdlib` ends the
stable sequence at `2.4.20`, followed by `2.4.21-RC` and `2.5.0-Beta1` (timestamp
2026-09-30). 2.4.20 is the latest stable. With AGP 9.4.x the Kotlin compiler is provided
by AGP's **built-in Kotlin support**; 2.4.20 pins the `kotlin-stdlib` and
`kotlin-test`/JUnit alignment. If the AGP built-in compiler version and the pinned stdlib
report a skew warning at implementation time, the stdlib pin is aligned via the
AGP-provided property — this is a bounded, implementation-time concern that does not
change the decision.

**Alternatives considered**:
- *2.4.21-RC / 2.5.0-Beta1* — pre-releases; rejected.
- *2.3.x or older* — superseded stable lines; no reason to lag in a new skeleton.

## R-05: Jetpack Compose BOM

**Decision**: `androidx.compose:compose-bom:2026.09.00`.

**Rationale**: Google Maven metadata for `androidx/compose/compose-bom` lists stable
versions ending `… 2025.12.01, 2026.01.01, 2026.02.01, 2026.03.01, 2026.04.01, 2026.05.01,
2026.06.01, 2026.08.00, 2026.09.00` (timestamp 2026-09-09). 2026.09.00 is the latest
stable BOM. The BOM pins all `androidx.compose.*` artifacts (ui, foundation, material3),
which is the minimal-dependency way to keep Compose versions consistent (Constitution VII).

**Alternatives considered**:
- *2026.08.00* — previous stable; no reason to lag a fresh skeleton.
- *Explicit per-artifact Compose versions without BOM* — more pins to maintain and drift
  risk; the BOM exists precisely for this.

## R-06: activity-compose version

**Decision**: `androidx.activity:activity-compose:1.13.0`.

**Rationale**: Google Maven metadata for `androidx/activity/activity-compose` ends the
stable sequence at `1.13.0` (followed by `1.14.0-alpha01…alpha03`; timestamp 2026-09-23).
1.13.0 is the latest stable. Needed for `setContent` in the single `MainActivity`
(US1); the only Activity-level library the skeleton requires.

**Alternatives considered**:
- *1.14.0-alpha0x* — pre-releases; rejected.
- *Rely on transitive resolution only* — non-reproducible; explicit pin in
  `gradle/libs.versions.toml` is required for offline-friendly, deterministic builds
  (FR-010).

## R-07: lifecycle-runtime-ktx version

**Decision**: `androidx.lifecycle:lifecycle-runtime-ktx:2.11.0`.

**Rationale**: Google Maven metadata for
`androidx/lifecycle/lifecycle-runtime-ktx` ends the stable sequence at `2.11.0`
(followed by `2.12.0-alpha01…alpha04`; timestamp 2026-09-23). 2.11.0 is the latest
stable. Used for lifecycle-aware Compose/Activity scope helpers in the single-Activity
skeleton.

**Alternatives considered**:
- *2.12.0-alpha0x* — pre-releases; rejected.
- *Omit lifecycle entirely* — possible, but `lifecycle-runtime-ktx` is the conventional
  baseline for Activity+Compose and is tiny; keeping it avoids rework in the next feature.

## R-08: Test framework

**Decision**: JUnit **4.13.2** for JVM-only unit tests (`testImplementation`), via
`kotlin-test` assertions where convenient.

**Rationale**: JUnit 4.13.2 is the latest 4.x and the standard, device-free JVM test
runtime required by FR-004 (no emulator, no instrumentation). AGP's
`testDebugUnitTest` runs these on the host JVM. No mock, assertion, or coroutine test
libraries are needed for the placeholder test (Constitution VII).

**Alternatives considered**:
- *JUnit 5 (Jupiter)* — not wired into Android unit testing by default; extra setup with
  zero benefit for a placeholder test.
- *Kotest / MockK / Robolectric* — extra dependencies the spec does not require; Robolectric
  additionally drags android-all jars into offline builds (FR-010 risk).

## R-09: Lint configuration

**Decision**: Use AGP's **built-in Android Lint**. `lint` task treats **errors as fatal**
(non-zero exit; FR-005). **Warnings are non-fatal** (reported, do not fail the gate —
per US2). Baseline: none. Custom rule set: none; default severity model only.

**Rationale**: AGP fails `lint` on any issue of severity `error` by default, which is
exactly the FR-005 behavior with zero extra dependencies. A zero-warning policy would
require pinning every default warning forever on a skeleton and contradicts US2's
explicit "warnings do not fail" requirement.

**Alternatives considered**:
- *abortOnError=false* — would violate FR-005; rejected.
- *External linters (detekt, ktlint)* — Constitution VII: minimal deps; built-in lint is
  sufficient for the gate. Can be added in a later feature if the team wants style rules.

## R-10: FR-006 verification entry point (command name & shape — fixed here)

**Decision**: The single documented local verification entry point is
**`scripts/verify.sh`** (POSIX shell, `set -euo pipefail`, executable bit set).

Shape (fixed by this research, to be implemented by `/speckit-tasks` + implement):

1. **Prerequisite precheck** (fast, before Gradle runs) — verifies:
   - `JAVA_HOME` is set and `$JAVA_HOME/bin/java` exists (points at JDK 21 per R-03);
   - `ANDROID_HOME` **or** `ANDROID_SDK_ROOT` is set and contains a recognizable SDK
     layout (e.g. `platforms/`, `build-tools/`).
   - On failure: print an **actionable message** (which variable, what to install,
     where to get it — mirrors the README prerequisites) and exit non-zero. This
     satisfies US2's "precheck failure must exit non-zero with an actionable message".
2. **Fixed check set** — run, in order, from the repo root:
   `./gradlew build testDebugUnitTest lint`
   (Gradle executes duplicate-task mentions once; the three names make the FR-006
   contract explicit and fail fast in this order.)
3. **Consolidated pass/fail output** — on success print one summary line
   (`PASS: build, testDebugUnitTest, lint`). On failure, stop at the first failing
   check and print the **Gradle task name plus the named failure detail** surfaced from
   Gradle's output: for tests, the failing test class/method (parsed from the test
   report path/summary); for lint, the **file path and rule id**
   (`<file>: <issue-id>` from lint's text output/`lint-results.txt`).
4. **Exit code** — non-zero (1) if the precheck or any check fails; **0** only when all
   checks pass. **Warnings never affect the exit code** (US2).

**Rationale**: FR-006 fixes the command name and shape *during `/speckit-plan`*: one
entry point, no ambiguity with the canonical build command (`./gradlew build`, FR-001).
A shell script keeps the gate dependency-free (Constitution VII) and works identically
in the isolated dev VM and CI (US3 calls the same script? No — CI runs the same three
Gradle checks directly; `scripts/verify.sh` is the documented *local* entry point).
The precheck converts the most common cold-start failures (missing JDK/SDK) into
actionable errors instead of cryptic Gradle stack traces (FR-011 support).

**Alternatives considered**:
- *Make target (`make verify`)* — adds an undocumented tool dependency; `make` exists on
  the VM but is not part of the Android toolchain story.
- *Gradle task only (`./gradlew check`)* — cannot provide the prerequisite precheck or
  the consolidated named-failure summary FR-006 requires.
- *Separate scripts per check* — violates FR-006's "one entry point".
- *Naming: `scripts/check.sh` / `verify.sh` vs root-level `verify.sh`* — `scripts/`
  keeps the repo root clean; `verify.sh` matches the spec's verification language.

## R-11: applicationId

**Decision**: `com.jkteknologies.androidanalyzer` (provisional — derived from the
origin remote owner `jkteknologies`).

**Rationale**: Reverse-DNS from the GitHub owner is the conventional id basis; the
placeholder UI text remains exactly "Android Analyzer" (FR-002, clarification
2026-10-03) regardless of id. Marked provisional so a rename is a one-line change in
`app/build.gradle.kts` before first release.

**Alternatives considered**:
- *`com.example.androidanalyzer`* — reserved-looking placeholder ids are discouraged for
  anything shipped (F-Droid publishing in Constitution VIII).
- *Waiting for an explicit product decision* — would leave FR-002/US1 unimplementable;
  a provisional id unblocks the skeleton.

## R-12: Android SDK levels & build-tools

**Decision**: `minSdk = targetSdk = compileSdk = 35` (Android 15), per spec
clarification 2026-10-03. **Build-tools**: not explicitly pinned — AGP 9.4.x selects its
default build-tools automatically; install the SDK packages AGP requests when
 bootstrapping (README documents: platform `android-35`, platform-tools, and the
build-tools AGP requests).

**Rationale**: API 35 is the clarified floor; equal min/target/compile maximizes the
"no compat shims" property (FR-003) — no `appcompat`, no multiplatform desugaring, no
Jetifier. Letting AGP pick build-tools avoids a needless pin that drifts from AGP 9.4.1
expectations.

**Alternatives considered**:
- *minSdk < 35 with support libraries* — directly violates FR-003 and Constitution VI.
- *compileSdk 36* — beyond the clarified API pin; rejected.

## R-13: CI environment

**Decision**: `.github/workflows/ci.yml`: trigger on **every push and every PR**;
runner `ubuntu-latest`; JDK via `actions/setup-java` (Temurin 21, R-03); Gradle caching
via `gradle/actions/setup-gradle` (or `actions/cache` over `~/.gradle/caches`); Android
SDK = the **GitHub-hosted runner's preinstalled Android SDK** (no sdkmanager bootstrap
step needed); job = `./gradlew build testDebugUnitTest lint`. **No emulator, no
devices, no credentials/secrets** (FR-008).

**Rationale**: ubuntu-latest runners ship an Android SDK with recent platforms/build-tools
including API 35, eliminating the slowest setup step and helping the ≤15 min cold-cache
target (SC-003). The three checks mirror the local gate (US2/FR-006 check set) without
depending on `scripts/verify.sh`'s host-specific precheck.

**Alternatives considered**:
- *Self-hosted runner* — violates IV (VM never exposed) and needs credentials; rejected.
- *Emulator/instrumented job* — FR-004/FR-008 forbid devices; rejected.
- *Manual sdkmanager bootstrap* — extra minutes against SC-003; unnecessary given
  preinstalled SDK.

## R-14: Dependency acquisition in the isolated dev VM

**Decision**: First Gradle invocation downloads the Gradle distribution (wrapper) and
all dependencies from Maven Central + Google Maven over the network; the environment
already has `curl`/`unzip`/`zip` (probed 2026-10-03). README documents this as the
expected first-run behavior; offline builds (SC-006, `--offline`) are valid **after**
the first warm run populates `~/.gradle`.

**Rationale**: FR-010 requires a minimal dependency set that downloads cleanly; the
pinned set (R-01–R-08) is 6 runtime libs + 1 test lib, all public, no
credentials-gated repos. The isolated VM has no JDK/SDK preinstalled — that is a
documented prerequisite (FR-011, R-03), not a network problem.

**Alternatives considered**:
- *Vendor all dependencies into the repo* — bloats the repo, against Constitution IX.
- *FlatDir/local-only jars* — non-reproducible and unmaintainable; rejected.

---

## Summary of pinned versions

| Component | Version | Source of truth |
|---|---|---|
| Android Gradle Plugin | 9.4.1 | Google Maven metadata (ts 2026-10-01) |
| Gradle (wrapper) | 9.8.0 | gradle.org/releases (2026-09-24) |
| JDK | 21 LTS (Temurin) | Adoptium/`actions/setup-java` |
| Kotlin (stdlib/test) | 2.4.20 | Maven Central metadata (ts 2026-09-30) |
| Compose BOM | 2026.09.00 | Google Maven metadata (ts 2026-09-09) |
| activity-compose | 1.13.0 | Google Maven metadata (ts 2026-09-23) |
| lifecycle-runtime-ktx | 2.11.0 | Google Maven metadata (ts 2026-09-23) |
| JUnit | 4.13.2 | Maven Central |
| SDK levels | min=target=compile=35 | spec clarification 2026-10-03 |
| build-tools | AGP default (not pinned) | AGP 9.4.x behavior |
