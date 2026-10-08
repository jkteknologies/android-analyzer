---

description: "Task list for feature 006 — Pre-Publication Rebrand, Help Screen & Swipe Navigation"
---

# Tasks: 006 Rebrand, Help Screen & Swipe Navigation

**Branch**: `feature/006-ui-improvements`

**Input**: Design documents from `/specs/006-rebrand-help-swipe/`

**Prerequisites**: plan.md, spec.md (5 user stories), research.md (R-01..R-09), data-model.md, contracts/navigation-and-links.md, quickstart.md

**Tests**: The existing 111-test JUnit suite must stay green throughout (spec SC-007). One new unit test is mandated by research R-07 (license verbatim, T013). Device-only checks are manual M-items from quickstart.md, executed by the host (T021).

**Organization**: Tasks grouped by user story. **US1 (the package rename) executes first**: every later task references post-rename paths (`app/src/main/java/com/jkteknologies/resourceradar/…`).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1..US5 per spec.md)
- ⚠️ **Homoglyph hazard**: the company segment is `jkteknologies` — with a **k**. Never write `jktec…`. All greps in verification tasks double as drift checks.

## Path Conventions

Single Gradle module: `app/src/main/java|aidl|res/`, `app/src/test/java/`, docs at repo root. Full layout in plan.md §Project Structure.

---

## Phase 1: Setup

**Purpose**: Prove the baseline before any change (Constitution V).

- [X] T001 Run the baseline gates from repo root: `export ANDROID_HOME=/home/dev/Android/Sdk && ./gradlew testDebugUnitTest lintDebug` — confirm all 111 tests pass and lint reports zero errors. If the baseline is red, stop and fix before any 006 work.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The shared link-opening infrastructure (contracts L-2/L-3/L-4) — used by US3 (Contacts) and US4 (Shizuku hint). No story work before this is complete.

- [X] T002 Add the shared link opener and failure surface: create `app/src/main/java/com/jkteknologies/resourceradar/ui/LinkOpener.kt` with one helper per research R-05 — builds `Intent(Intent.ACTION_VIEW, uri)`, calls `context.startActivity`, catches `ActivityNotFoundException` and reports failure (no crash). Add string `link_unavailable` ("Couldn't find an app to open this link.") to `app/src/main/res/values/strings.xml`, hoist a `SnackbarHostState` into `AnalyzerApp`'s Scaffold in `ui/AnalyzerApp.kt` (`snackbarHost` parameter, auto-dismissing default), and expose it to screens as an `onLinkUnavailable: () -> Unit` callback. No permission, no `<queries>` entry — failure detection is the catch only (contract L-4).

**Checkpoint**: `./gradlew lintDebug` green; app behavior unchanged.

---

## Phase 3: User Story 1 — App is branded "Resource Radar" (Priority: P1) 🎯 MVP

**Goal**: Display name, technical identity, and docs all read "Resource Radar"; zero "Android Analyzer" anywhere user-visible (FR-001..FR-003, SC-001).

**Independent Test**: Install the build → launcher label, in-app strings, and the Shizuku authorization prompt all read "Resource Radar"; `applicationId`/package = `com.jkteknologies.resourceradar`; suite green (spec US1 Independent Test).

### Implementation for User Story 1

- [X] T003 [US1] In `app/build.gradle.kts`, rename `namespace` and `applicationId` from `com.jkteknologies.androidanalyzer` to `com.jkteknologies.resourceradar` (R-08; the `${applicationId}`-parameterized manifest entries — Shizuku provider authority, DYNAMIC_RECEIVER removal — need no edits).
- [X] T004 [US1] Move the whole source trees one mechanical step (R-08): `app/src/main/java/com/jkteknologies/androidanalyzer/` → `…/resourceradar/`, `app/src/main/aidl/com/jkteknologies/androidanalyzer/` → `…/resourceradar/`, `app/src/test/java/com/jkteknologies/androidanalyzer/` → `…/resourceradar/`; rewrite every `package`/`import` line in all moved `.kt` files and both `.aidl` files' (`AppProcessMemory.aidl`, `IAppMemoryService.aidl`) package declarations. `git mv` the directories so history follows.
- [X] T005 [P] [US1] Rebrand user-visible strings in `app/src/main/res/values/strings.xml`: `app_name` → "Resource Radar", and the two hints embedding the old name — `shizuku_hint_not_installed` ("…then allow Resource Radar there.") and `shizuku_hint_awaiting` ("Allow Resource Radar in Shizuku…"). Also rename the style id `Theme.AndroidAnalyzer` → `Theme.ResourceRadar` in `app/src/main/res/values/themes.xml` and its `@style/` reference in `app/src/main/AndroidManifest.xml` (internal id hygiene, R-08).
- [X] T006 [P] [US1] Update `README.md`: line 1 heading and the line-98 manual-checklist row read "Resource Radar" (spec Assumption: docs are part of the rebrand).
- [X] T007 [US1] Verify US1: `grep -rin "androidanalyzer" app/ *.kts` returns zero hits; `grep -rn "Android Analyzer" app/src README.md` returns zero hits; rerun T001's gates — all 111 tests pass on the renamed packages, lint zero errors (R-08: the rename must not cost a single test).

**Checkpoint**: Fully rebranded, correctly identified app — US1's acceptance scenarios 1–3 verifiable on device (host M-3), scenario 4 by T007's greps.

---

## Phase 4: User Story 2 — New launcher icon (Priority: P1)

**Goal**: b-blip-bars adaptive icon: full-bleed background, safe-zone foreground, flat monochrome layer (FR-004, SC-002; research R-06).

**Independent Test**: Install the build → launcher shows the blip-bars icon, correct under the launcher's mask; themed icon tints flat (spec US2 Independent Test; device-only → host M-1/M-2).

### Implementation for User Story 2

- [X] T008 [P] [US2] Replace `app/src/main/res/drawable/ic_launcher_background.xml` with a full-bleed vector rectangle filled `#0D0D0F` in the 108 dp adaptive viewport (the SVG's rounded corners are dropped — launcher masks supply the shape, R-06).
- [X] T009 [P] [US2] Replace `app/src/main/res/drawable/ic_launcher_foreground.xml` with the ring + bars paths from `design/logos/b-blip-bars.svg` scaled into the 108 dp viewport with all content inside the 66 dp center safe zone; keep the red sweep gradient in this layer.
- [X] T010 [US2] Create `app/src/main/res/drawable/ic_launcher_monochrome.xml` — flat white single-color variant of the same bars+ring artwork — and point the existing `<monochrome>` element in `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` at it (themed icons tint a dedicated flat layer correctly; a gradient layer would render as a blob, R-06).
- [X] T011 [US2] Verify US2: `./gradlew lintDebug assembleDebug` green (lint validates the vector drawables); leave M-1 (icon at launcher sizes/mask) and M-2 (themed icon tint) for the host checklist — not runnable in the VM.

**Checkpoint**: Branded icon ships in the build; US2 independently testable on device.

---

## Phase 5: User Story 3 — Help screen (Priority: P2)

**Goal**: Fourth footer destination "Help": one scrollable screen with About, Limitations, Per-app memory, Contacts (two tappable links), License (verbatim AGPL) — in that order (FR-006..FR-012, contracts N-1).

**Independent Test**: Open Help from the footer → five sections in order, both contact links open the browser, license text byte-matches `LICENSE` (spec US3 Independent Test; link round-trip is host M-8).

### Implementation for User Story 3

- [ ] T012 [P] [US3] Add the Help content strings to `app/src/main/res/values/strings.xml`: five section titles (`help_title_about`, `help_title_limitations`, `help_title_per_app_memory`, `help_title_contacts`, `help_title_license`), the three section bodies (About per FR-008: device resource overview, per-app details, Shizuku-based per-app memory; Limitations per FR-009: Shizuku required for per-app memory, usage-access grant needed, figures are estimates; Per-app memory per FR-010: install/start Shizuku, grant access, open an app's details), and Contacts labels for the author LinkedIn entry and the company website. English only; fixed URLs `https://www.linkedin.com/in/jurijskolomijecs/` and `https://jkteknologies.com/` (FR-011).
- [ ] T013 [P] [US3] Copy `LICENSE` → `app/src/main/res/raw/license.txt` byte-identical (`cmp` clean), and add the failing-first unit test `app/src/test/java/com/jkteknologies/resourceradar/ui/help/LicenseVerbatimTest.kt` asserting the repo `LICENSE` and `res/raw/license.txt` are byte-equal (plain filesystem reads — no Robolectric, no new dependency; research R-07, SC-005). Run it and watch it pass only once the resource exists.
- [ ] T014 [US3] Create `app/src/main/java/com/jkteknologies/resourceradar/ui/help/HelpScreen.kt`: one `verticalScroll` Column with exactly five titled sections in spec order (About, Limitations, Per-app memory, Contacts, License); Contacts = two rows using T002's opener via `onLinkUnavailable`; License = one `Text` rendering `res/raw/license.txt` read once via `remember { … openRawResource(R.raw.license) … }` (R-07). No search, index, or collapsible sections (spec Assumption).
- [ ] T015 [US3] Wire the destination in `app/src/main/java/com/jkteknologies/resourceradar/ui/AnalyzerApp.kt`: append `HELP` to the `Destination` enum (last — enum order is footer and swipe order), add the fourth equal-width footer button, add `Destination.HELP -> Unit` to the auto-refresh ticker's `when` (contract N-7), and render the HelpScreen slot with its `onLinkUnavailable` callback. `BackHandler` needs no change (returns to HOME from anything non-HOME, N-6).

**Checkpoint**: Footer shows four destinations; Help complete and readable; suite + lint green. US3 verifiable except the on-device link/browser round-trip (M-8).

---

## Phase 6: User Story 4 — One-tap Shizuku download link (Priority: P2)

**Goal**: The Shizuku not-installed hint's website address is tappable and opens https://shizuku.rikka.app in the browser (FR-005, contracts L-1/L-2).

**Independent Test**: On a device without Shizuku, open an app's details → the address in the hint is visibly tappable; one tap opens the site; back returns to the app unchanged (host M-8).

### Implementation for User Story 4

- [ ] T016 [US4] Split `shizuku_hint_not_installed` in `app/src/main/res/values/strings.xml` into prefix / URL / suffix resources so the visible wording is unchanged except the rebrand (T005) — the URL text itself becomes the link anchor (research R-04).
- [ ] T017 [US4] In `app/src/main/java/com/jkteknologies/resourceradar/ui/details/DetailsScreen.kt`, render the NOT_INSTALLED guidance text as an `AnnotatedString` with a `LinkAnnotation.Url` on the address segment (`TextLinkStyles` from the theme), whose interaction listener calls T002's opener (failure → `onLinkUnavailable`, FR-016). Thread `onLinkUnavailable` from `AnalyzerApp`'s details slot. Other hint states remain plain text.

**Checkpoint**: Suite + lint green; the FR-005 behavior is device-verified via M-8.

---

## Phase 7: User Story 5 — Swipe between screens (Priority: P3)

**Goal**: Horizontal swipes move between the four destinations in footer order, clamped at the ends; footer follows the settled page; state parity with taps (FR-013/FR-014/FR-015, contracts N-2..N-5).

**Independent Test**: On each main screen swipe left/right → adjacent screen + footer highlight change; ends don't wrap; pull-to-refresh and license scrolling unaffected (host M-4..M-7).

### Implementation for User Story 5

- [ ] T018 [US5] In `app/src/main/java/com/jkteknologies/resourceradar/ui/AnalyzerApp.kt`, replace the content `when (destination)` with a `HorizontalPager` (`rememberPagerState(pageCount = { Destination.entries.size })`) whose per-page content is the existing screen lambdas unchanged (state parity by construction, R-03); make `pagerState.settledPage` the navigation truth via `snapshotFlow { pagerState.settledPage }` → `destination` (footer/back handler keep working unchanged), and make footer taps call `pagerState.animateScrollToPage(index)` (R-01). No `beyondBoundsPageCount`, no saved-state additions, no new dependency.
- [ ] T019 [US5] Verify US5: rerun the full gates (`testDebugUnitTest` + `lintDebug`) — suite green, ticker still keys on the derived destination; the gesture matrix (M-4 swipes + clamping, M-5 pull-to-refresh coexistence, M-6 license-scroll coexistence, M-7 Details no-selection state, M-10 back behavior) goes to the host checklist — the pager × pull-to-refresh interplay (research R-02) is the feature's main device risk.

**Checkpoint**: All five user stories functionally complete; only device-only checks remain.

---

## Phase 8: Polish & Cross-Cutting Concerns

- [ ] T020 Run the complete automated section of `specs/006-rebrand-help-swipe/quickstart.md`: both Gradle gates, all four identity/verbatim checks (grep `androidanalyzer` → zero, grep "Android Analyzer" → zero, `cmp LICENSE app/src/main/res/raw/license.txt` silent, `grep -c "Resource Radar" strings.xml` ≥ 1).
- [ ] T021 Hand the host the manual checklist (quickstart.md M-1..M-11) covering the spec's acceptance scenarios — M-9 (no-browser snackbar) optional. Host records results; after M-1..M-8 pass, the host merges `feature/006-ui-improvements` to `master` (push happens from the host only, Constitution III).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (T001)**: immediate; baseline must be green.
- **Foundational (T002)**: after T001; blocks US3 and US4 (US1/US2 don't consume it but the phase is cheap and keeps ordering simple).
- **US1 (T003..T007)**: first story — every later task uses post-rename paths (R-08). Blocks everything.
- **US2 (T008..T011)**: after US1 (paths); touches only drawables — fully parallel with US3/US4.
- **US3 (T012..T015)**: after US1 + T002; **blocks US5** (HELP destination must exist before the pager covers it).
- **US4 (T016, T017)**: after US1 + T002; independent of US3 except both wire `AnalyzerApp.kt` (T015 vs T017 threading) — keep sequential or coordinate.
- **US5 (T018, T019)**: after US3.
- **Polish (T020, T021)**: after all stories.

### User Story Dependencies

```text
T001 → T002 → US1 ─┬→ US2 ──────────────────────┐
                   ├→ US3 → US5 ─────────────────┼→ T020 → T021
                   └→ US4 ───────────────────────┘
```

### Parallel Opportunities

- Within US1: T005 ∥ T006 (res/docs vs source tree), both after T003/T004.
- Within US2: T008 ∥ T009 (different drawables).
- Within US3: T012 ∥ T013 (strings.xml vs raw+test).
- Across stories after US1: US2 runs entirely in parallel with the US3 → US5 chain; US4 parallel to US3 until the shared `AnalyzerApp.kt` wiring.

---

## Implementation Strategy

### MVP First (US1 + US2)

The two P1 stories are the publication-blocking rebrand: complete Setup → Foundational → US1 → US2, validate on device (M-1..M-3). The app is correctly branded at this point even if nothing else lands.

### Incremental Delivery

US3 adds the Help value → US4 the Shizuku path → US5 the navigation polish. Each story ends with its checkpoint gates green; commit after each story's checkpoint (repo convention: one or a few commits per story).

### Single-Developer Order (this repo's reality)

Strictly sequential T001 → T021 in ID order is correct and simplest; the [P] markers only matter if work is ever split.

## Notes

- Commit after each story checkpoint (Constitution V: gates green before presenting).
- No task may add a dependency or a manifest permission — research R-01..R-09 fixed both at zero.
- `tasks.md` checkboxes are updated by `$speckit-implement` as work completes.
