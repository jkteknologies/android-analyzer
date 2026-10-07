# Implementation Plan: Pull-to-Refresh on Home Screen

**Branch**: `003-pull-to-refresh` | **Date**: 2026-10-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-pull-to-refresh/spec.md`

## Summary

Add the platform-standard pull-to-refresh gesture to the home screen: pulling the figure
list down past the trigger distance and releasing re-reads all five figures as a fresh
snapshot, with a visible indicator, values kept on screen during the reload, and
repeated triggers coalesced into the running cycle. Technically this is achieved with
**zero new dependencies and zero new permissions**: Material3 1.4.0 — already on the
classpath via the pinned Compose BOM — ships a stable `PullToRefreshBox` (verified
against the resolved artifact bytecode, research R-01). The core work is semantic, in
[HomeStateHolder](../../app/src/main/java/com/jkteknologies/androidanalyzer/ui/home/HomeStateHolder.kt):
the existing read cycle resets every figure to `Loading` (placeholder), which feature
002 requires for presentation reads but feature 003 forbids for refresh reads
(FR-004). The holder therefore gains a second entry point, `refresh()`, that reuses the
same single-thread executor pass but preserves current figure states, plus an observable
`isRefreshing` flag for the indicator, a coalescing guard (FR-006), and an epoch guard
that drops result posts from superseded cycles so navigating away mid-read can never
paint stale figures on return (FR-011, R-04). The settings screen, footer, readers,
and domain models are untouched.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (AGP 9.4.1 built-in Kotlin support; JVM target 21)

**Primary Dependencies**: Jetpack Compose via BOM `2026.09.00` — Compose UI/Foundation
1.12.x, **Material3 1.4.0** (resolved version, verified in the Gradle artifact cache);
activity-compose 1.13.0; lifecycle-runtime-ktx 2.11.0; JUnit 4.13.2. Feature 003 adds
**none** of these and no others (R-01, Constitution VII).

**Storage**: N/A — no persistence changes; the theme store and all domain models are
untouched by this feature.

**Testing**: JUnit 4 JVM unit tests through the existing fakes seam
([contracts/device-readers.md](../002-home-screen/contracts/device-readers.md) +
`ResultPoster`/`executorFactory` injection in `HomeStateHolder`); AGP lint with
`abortOnError = true`. Entry point: `./scripts/verify.sh` (feature 001).

**Target Platform**: Android 15+ (`minSdk 35`, `targetSdk 35`, `compileSdk 37`),
single-activity Compose app.

**Project Type**: mobile-app (local device analyzer; no network, no backend).

**Performance Goals**: a triggered refresh presents settled figures within 2 s of the
trigger (SC-003); the refresh indication appears within 1 s of the triggering release
and disappears within 1 s of completion (SC-002); all platform reads stay off the main
thread (existing executor pattern, unchanged).

**Constraints**: offline-capable (no network at all); no new permissions (SC-005,
Constitution VIII); no background work beyond finishing in-flight one-shot local reads
(FR-010, Constitution IX); 8 GB VM build environment (root `gradle.properties` 2 g heap
— pre-existing).

**Scale/Scope**: 2 screens, 5 figure groups, single-user local app. Touched surfaces:
`HomeStateHolder.kt`, `HomeScreen.kt`, `strings.xml`, `HomeStateHolderTest.kt` — four
files.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | Verdict | Evidence |
|---|-----------|---------|----------|
| I | Spec-driven development | PASS | [spec.md](./spec.md) authored via `$speckit-specify`, clarified (0 open items), this plan precedes any code. |
| II | Feature branch workflow | PASS | Work happens on `feature/003-refresh` (created from `master` after 002 merged); no direct-to-`master` commits. |
| III | Host-only remote push | PASS | VM never pushes; plan and tasks contain no push steps. |
| IV | Isolated VM development | PASS | All automated verification is JVM unit tests + lint in the VM; gesture feel is verified via documented emulator/`adb` manual checks (quickstart.md) — no production or physical-lab dependency. |
| V | Local quality gates | PASS | `./scripts/verify.sh` (clean build + unit tests + lint) is the required gate before any change is presented as complete (quickstart.md §1). |
| VI | Modern Android-only | PASS | `minSdk 35` unchanged; uses current stable platform UI APIs only. |
| VII | Modern stack, minimal dependencies | PASS | **Zero new dependencies**: `PullToRefreshBox` ships in the already-pinned Material3 1.4.0 and is stable (non-experimental) there — verified by inspecting the resolved artifact (R-01). Third-party refresh libraries rejected (R-01). |
| VIII | F-Droid-first distribution | PASS | **Zero new permissions**; no network, tracking, or proprietary components; refresh is a local, user-visible interaction (SC-005). |
| IX | Efficiency and security | PASS | Reads are user-initiated one-shot local reads on the existing single background thread; no polling/background jobs; epoch guard (R-04) prevents wasted duplicate UI work; no new data at rest or in transit. |

**Gate result: PASS — no violations. Phase 0 may proceed.** (Re-check after Phase 1
recorded at the end of research.md §"Post-design Constitution re-check".)

## Project Structure

### Documentation (this feature)

```text
specs/003-pull-to-refresh/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/
│   └── refresh-interaction.md   # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/jktecnologies/androidanalyzer/
├── ui/
│   ├── AnalyzerApp.kt                  # UNTOUCHED (footer/shell — FR-007)
│   └── home/
│       ├── HomeScreen.kt               # Wires PullToRefreshBox + a11y action/status
│       ├── HomeStateHolder.kt          # refresh(), isRefreshing, coalescing, epoch guard
│       └── FigureFormatting.kt         # UNTOUCHED
├── domain/                             # UNTOUCHED (FigureUiState semantics extended in
│   └── Figures.kt                      #   data-model.md, not in code)
├── data/                               # UNTOUCHED (readers are one-shot, reused as-is)
└── MainActivity.kt                     # UNTOUCHED (holder construction unchanged)

app/src/main/res/values/strings.xml     # +3 accessibility/status strings (R-06)
app/src/test/java/com/jktecnologies/androidanalyzer/
└── ui/home/HomeStateHolderTest.kt      # +refresh/coalescing/epoch unit tests (R-07)
```

**Structure Decision**: the existing single-module feature-package layout
(`ui/home` owning screen + state holder, `domain` pure models, `data` platform seam) is
kept; feature 003 is a behavior change to one screen and one state holder, so no new
packages, modules, or abstractions are introduced (Constitution VII).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations — table intentionally empty.
