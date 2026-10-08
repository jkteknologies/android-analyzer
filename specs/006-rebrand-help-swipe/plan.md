# Implementation Plan: Pre-Publication Rebrand, Help Screen & Swipe Navigation

**Branch**: `feature/006-ui-improvements` | **Date**: 2026-10-07 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/006-rebrand-help-swipe/spec.md`

## Summary

Rebrand the app to "Resource Radar" — new display name everywhere user-visible, new technical
identity `com.jkteknologies.resourceradar` (code package + applicationId, no migration — never
published), and the b-blip-bars artwork as an adaptive launcher icon (background + foreground +
monochrome layers rebuilt from the SVG). Add a fourth "Help" footer destination: one scrollable
screen with About, Limitations, Per-app memory, Contacts (two tappable links), and the verbatim
AGPL v3 text (embedded as a raw resource, byte-identical to `LICENSE`). Make the Shizuku
not-installed hint's website address tappable. Make the four destinations swipeable via
`HorizontalPager` from the already-included `compose-foundation`, with the pager as the single
source of truth for navigation so footer taps and swipes can never diverge. Dead links fail
gracefully into an auto-dismissing snackbar (FR-016). Zero new dependencies, zero new permissions.

## Technical Context

**Language/Version**: Kotlin 2.4.20 (built into AGP 9.4.1 — no standalone Kotlin plugin), JVM 21 target

**Primary Dependencies**: Compose BOM 2026.09.00 (`ui`, `foundation`, `material3`), activity-compose 1.13.0, lifecycle-runtime-ktx 2.11.0, Shizuku api/provider 13.1.5, JUnit 4.13.2. **No new dependencies** — `HorizontalPager` (foundation) and `LinkAnnotation` (ui-text) ship in the existing set (research R-01/R-04).

**Storage**: SharedPreferences only (theme + refresh mode stores, features 002/004). Unchanged by this feature — no data model or storage changes; the identity rename deliberately leaves no migration path (spec FR-003: app never published).

**Testing**: JUnit 4 JVM unit tests (`./gradlew testDebugUnitTest`, currently 111 passing) + built-in AGP lint (`abortOnError = true`) + a host-run manual checklist for device-only checks (launcher icon, swipe feel, real browser round-trips).

**Target Platform**: Android 15+ only (minSdk 35, targetSdk 35, compileSdk 37). Monochrome themed-icon layer is API 33+ — safely inside the supported window (Constitution VI).

**Project Type**: Single-module Android mobile app (one `app/` Gradle module, no variants beyond debug/release).

**Performance Goals**: Swipe transition parity with a footer tap (spec SC-006 — pager animation, no added latency); zero added background work: the auto-refresh ticker's destination mapping gains HELP → no-op (Constitution IX); the ~20 KB license text is read once when Help enters composition.

**Constraints**: F-Droid-first (Constitution VIII): no new manifest permissions — implicit `ACTION_VIEW` browser intents need none and no `<queries>` entry; old name must reach zero user-visible occurrences (spec SC-001); no compatibility shims (Constitution VI).

**Scale/Scope**: One module. Touched surfaces: build identity (`namespace`, `applicationId`, theme style name), the whole `com/jkteknologies/androidanalyzer` → `com/jkteknologies/resourceradar` tree (main + aidl + test), launcher icon drawables, `strings.xml` (rename + new Help/link strings), one new screen (`ui/help/HelpScreen.kt`), the shell (`AnalyzerApp.kt`: pager + footer + snackbar host), Details hint link, README (2 lines), 1 new raw resource (`license.txt`), ~2 new unit tests.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Spec-driven development | PASS | spec.md complete with 3 recorded clarifications; this plan precedes any code |
| II. Feature branch workflow | PASS | working on `feature/006-ui-improvements`; no direct `master` commits |
| III. Host-only remote push | PASS | plan performs no remote git operations; VM cannot push by design |
| IV. Isolated VM environment | PASS | all automated gates run locally (unit tests + lint); device-only checks packaged as a host-run manual checklist; no production/system dependencies introduced |
| V. Local quality gates | PASS | quickstart.md requires `testDebugUnitTest` + `lintDebug` green before completion |
| VI. Modern Android-only | PASS | minSdk 35 unchanged; monochrome icon layer (API 33+) is additive within the supported window; no legacy shims |
| VII. Modern stack, minimal dependencies | PASS | zero new dependencies: pager and link annotations come from already-included Compose modules; license is a plain raw resource, not a viewer library |
| VIII. F-Droid-first distribution | PASS | no new permissions; link opening uses the platform `ACTION_VIEW` intent (no `<queries>`, no in-app browser); Help/AGPL display strengthens distribution compliance |
| IX. Efficiency and security | PASS | no background work added (HELP tick is a no-op); one-shot 20 KB read on screen entry; external navigation restricted to three fixed https URLs; no new data at rest |

## Project Structure

### Documentation (this feature)

```text
specs/006-rebrand-help-swipe/
├── plan.md              # This file ($speckit-plan command output)
├── research.md          # Phase 0 output ($speckit-plan command)
├── data-model.md        # Phase 1 output ($speckit-plan command)
├── quickstart.md        # Phase 1 output ($speckit-plan command)
├── contracts/           # Phase 1 output ($speckit-plan command)
└── tasks.md             # Phase 2 output ($speckit-tasks command - NOT created by $speckit-plan)
```

### Source Code (repository root)

```text
app/src/main/java/com/jkteknologies/resourceradar/     # RENAMED from .../androidanalyzer (R-08)
├── MainActivity.kt                                     # unchanged wiring, new package
├── data/  domain/                                      # package rename only (incl. data/shizuku + AIDL)
└── ui/
    ├── AnalyzerApp.kt            # Destination gains HELP; HorizontalPager shell; footer 4th button; snackbar host (R-01/R-09)
    ├── help/
    │   └── HelpScreen.kt         # NEW: 5 sections in order; Contacts links; license text (R-07)
    ├── details/DetailsScreen.kt  # NOT_INSTALLED hint URL becomes a link (R-04)
    ├── home/  settings/          # package rename only
    └── theme/AppTheme.kt         # package rename only
app/src/main/aidl/com/jkteknologies/resourceradar/data/shizuku/*.aidl   # RENAMED + package lines (R-08)
app/src/main/res/
├── drawable/ic_launcher_background.xml    # REPLACED: full-bleed #0D0D0F layer (R-06)
├── drawable/ic_launcher_foreground.xml    # REPLACED: bars+ring in mask safe zone (R-06)
├── drawable/ic_launcher_monochrome.xml    # NEW: flat white bars+ring for themed icons (R-06)
├── mipmap-anydpi-v26/ic_launcher.xml      # monochrome slot points at the new drawable (R-06)
├── raw/license.txt                        # NEW: byte-identical copy of LICENSE (R-07)
└── values/strings.xml                     # app_name + 2 hints rebranded; Help/link strings added
app/src/test/java/com/jkteknologies/resourceradar/      # RENAMED + new tests (R-07/R-08)
design/logos/b-blip-bars.svg             # read-only source artwork (input to R-06)
README.md                                # 2 app-name lines rebranded (spec Assumptions)
```

**Structure Decision**: Single Gradle module, existing layering preserved (`ui` / `data` / `domain`) — this feature adds one UI subtree (`ui/help/`), icon resources, and one raw resource; everything else is identity rename or shell extension inside files that already exist.

## Complexity Tracking

> No constitution violations — table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
