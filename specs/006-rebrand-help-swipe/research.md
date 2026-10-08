# Research: 006 Rebrand, Help Screen & Swipe Navigation

Resolved unknowns for feature 006. Every decision keeps Constitution VII (zero new dependencies)
and VIII (zero new permissions). Format per speckit-plan Phase 0.

## R-01: Swipe container

**Decision**: `HorizontalPager` (androidx.compose.foundation) with `rememberPagerState { Destination.entries.size }`, placed in `AnalyzerApp`'s content Box. `pagerState.settledPage` is the single source of truth: a `snapshotFlow { pagerState.settledPage }` writes `destination`; footer taps call `pagerState.animateScrollToPage(index)`. No Navigation library, no custom gesture code.

**Rationale**: The shell already keeps a hand-rolled `Destination` enum + footer with no Navigation dependency (004 data-model §8); a pager keyed on the same enum extends it without changing the state model. HorizontalPager is in `compose-foundation`, already an implementation dependency — Constitution VII satisfied by construction. Using `settledPage` (not `currentPage`) means the footer highlights a page only after it fully arrives, which is what a user compares against a tap.

**Alternatives considered**:
- `navigation-compose` + HorizontalPager — rejected: adds a dependency for nothing; navigation state here is one enum.
- Custom `pointerInput` drag detector with velocity thresholds — rejected: reimplements the pager's gesture arbitration (axis locking, slop, animation) poorly; more code, worse feel.
- ViewFlipper/ViewPager2 (View system) — rejected: drags View-system machinery into a Compose app; needs interop wrapping.

## R-02: Gesture arbitration (pager vs pull-to-refresh vs license scroll)

**Decision**: Defaults only. Each page keeps its existing scroll container: Home's `PullToRefreshBox` (material3, 003), Help's vertical `verticalScroll` column. HorizontalPager locks the drag axis natively — a mostly-vertical drag is never claimed by the pager, a mostly-horizontal drag never reaches the vertical scroll. No overlap thresholds, no custom nested-scroll code.

**Rationale**: Compose's drag and nested-scroll systems already negotiate orthogonal axes; adding custom logic would risk breaking 003's verified pull-to-refresh behavior (spec SC-007: existing functionality unaffected).

**Alternatives considered**: custom `pointerInput` axis gating on Home — rejected: the platform already solves it; hand-rolled gating is where regressions live.

**Risk note**: Home's pull-to-refresh is vertical and the pager horizontal — expected to compose cleanly, but this is the one interaction 006 must prove manually on device (M-item: pull-to-refresh works on Home while swipes work everywhere).

## R-03: State parity between swipe and footer tap (FR-014)

**Decision**: Pages render the exact same composables the current `when (destination)` renders, one per page, inside the pager — same lambdas, same `pendingDetailsFilter` flow, same Home entering-composition read trigger. Default `beyondBoundsPageCount = 0` keeps today's disposal semantics: a non-adjacent screen leaves composition exactly as it does on a footer tab switch today.

**Rationale**: Parity by construction — both input paths converge on the same `pagerState`; no `rememberSaveable` is introduced, so swiping cannot invent state persistence that tapping doesn't have (spec Edge Cases bullet 5).

**Alternatives considered**: keeping screens in composition with `beyondBoundsPageCount` or saved-state — rejected: changes Home's read-on-entry semantics, which the spec explicitly forbids diverging from.

## R-04: Tappable URL inside the Shizuku hint

**Decision**: Split `shizuku_hint_not_installed` into prefix / URL / suffix string resources composed into an `AnnotatedString` with a `LinkAnnotation.Url` (`TextLinkStyles` for underline/color from the theme). Rendered by the existing guidance-row `Text`. The link's interaction listener runs the shared `openUrl` helper (R-05). The hint text wording is otherwise unchanged (rebranded per R-08).

**Rationale**: `LinkAnnotation` is the current (post-deprecation of `ClickableText`) Compose way to embed a link in text, ships in the existing `compose-ui` text module, and gives per-link click interception — needed so the failure path (FR-016) is ours, not a crash inside `UriHandler`.

**Alternatives considered**:
- Separate "Open shizuku.rikka.app" button row — rejected: spec FR-005 pins the address inside the hint being tappable; a button changes the reviewed 005 guidance-row design.
- `ClickableText` — rejected: deprecated; clunkier annotation API.

## R-05: Link opening + graceful failure (FR-016)

**Decision**: One shared helper in `ui/` (e.g. `rememberLinkOpener`): builds `Intent(ACTION_VIEW, uri)`, `try { context.startActivity(it) } catch (e: ActivityNotFoundException) { snackbar("…") }`. `AnalyzerApp`'s Scaffold gains a `SnackbarHostState` + `snackbarHost`; Details and Help receive one `onLinkUnavailable: () -> Unit` (message string lives in strings.xml). No `<queries>` entry, no `resolveActivity` call — `startActivity` does not require package visibility, so catching `ActivityNotFoundException` is the complete failure surface.

**Rationale**: Implicit-view intents need no permission and no visibility declaration (Constitution VIII); try/catch keeps the no-browser path a deliberate, testable behavior instead of a crash. Snackbar is the clarify session's accepted mechanism (spec FR-016: transient, auto-dismissing, no dialog).

**Alternatives considered**: `LocalUriHandler.openUri` — rejected as the primary path: its failure mode throws inside framework code with no clean per-link interception point (we keep it out; our helper does the same job with the catch).

## R-06: Adaptive launcher icon from b-blip-bars.svg

**Decision**: Replace the three drawables behind the existing `mipmap-anydpi-v26/ic_launcher.xml` (its `<monochrome>` slot already exists):
- `ic_launcher_background.xml` — full-bleed `#0D0D0F` vector rectangle (the SVG's 112-corner rounding is **dropped**: launchers apply their own mask; baking a shape double-rounds).
- `ic_launcher_foreground.xml` — the ring + bars paths from the SVG, rescaled into the 108 dp viewport with all content inside the 66 dp center safe zone; the red sweep gradient stays in the color layers (VectorDrawable gradients are fine at minSdk 35).
- `ic_launcher_monochrome.xml` — flat white (single-path) variant of the same artwork; the existing `<monochrome>` element re-points at it. Colored/gradient art renders as an opaque blob in themed-icon tinting, which is why it needs its own layer.
48 px / 512 px PNG renders stay untouched as design sources; the shipped icon is the vector pair.

**Rationale**: The adaptive-icon plumbing already exists from feature 001 — only the layers change. Full-bleed layers + safe-zone content is precisely spec FR-004/SC-002 ("renders correctly at launcher sizes and with launcher masking", clarify session option A).

**Alternatives considered**:
- Shipping the 512 px PNG as a legacy icon — rejected: fails masking (spec FR-004), clarify session option C.
- Reusing the color foreground as monochrome — rejected: gradient content tints badly; a flat path costs one small file.

## R-07: Verbatim AGPL embedding (FR-012, SC-005)

**Decision**: Copy `LICENSE` → `app/src/main/res/raw/license.txt`, byte-identical (`cmp` clean). HelpScreen reads it once: `remember { context.resources.openRawResource(R.raw.license).bufferedReader().use(BufferededReader::readText) }` inside the scrollable column as one `Text`. A new JVM unit test asserts byte-equality between the repo `LICENSE` and `res/raw/license.txt` (plain filesystem reads — both are repo files; no Robolectric, no new test dependency).

**Rationale**: A raw resource is the standard Android mechanism for large verbatim text; reading it at screen entry (not app start) keeps startup untouched (Constitution IX). The unit test turns SC-005 ("100% identical") into a locally enforceable gate, per ponytail's "one runnable check" rule.

**Alternatives considered**:
- String resource (`<string name="license">`) — rejected: 20 KB XML entity escaping is fragile and diffs horribly; raw file is byte-faithful.
- Reading the root `LICENSE` at runtime — impossible: it isn't packaged in the APK.
- WebView/markdown viewer library — rejected: new dependency (Constitution VII) for plain text.

## R-08: Technical identity rename (FR-003, SC-001)

**Decision**: One mechanical, self-contained step moves `com.jkteknologies.androidanalyzer` → `com.jkteknologies.resourceradar` everywhere: `namespace` + `applicationId` in `app/build.gradle.kts`; the `app/src/main/java/`, `app/src/main/aidl/`, `app/src/test/java/` directory trees and every `package`/`import` line in them; both `.aidl` files' package declarations; the theme style name `Theme.AndroidAnalyzer` → `Theme.ResourceRadar` (internal, hygiene); `strings.xml` `app_name` and the two Shizuku hints that embed "Android Analyzer"; README's two app-name lines. `README.md:1` heading and `README.md:98` checklist row included per spec Assumptions (docs are part of the rebrand). Verification: repo-wide grep for `androidanalyzer` and `Android Analyzer` returns zero hits (outside git history), full build + 111 existing tests + lint green — the rename must not cost a single test.

**Rationale**: The identifier appears in AIDL package names and the Shizuku provider authority (`${applicationId}.shizuku` — parameterized, needs no edit), so a whole-tree move done in one step is less error-prone than piecemeal edits. No migration: the app was never published; SharedPreferences stores ride on the fresh identity (already-sideloaded installs are uninstalled/reinstalled — spec Assumption).

**Alternatives considered**: keeping the old code package and renaming only the display name — explicitly reversed by the user in the clarify history (app-name decision): the F-Droid listing carries the technical identity, spec FR-003 mandates it gone.

**Homoglyph hazard**: the company segment is `jkteknologies` — with a **k**. All edits must preserve it; the post-rename grep doubles as the c/k drift check.

## R-09: Help screen + destination wiring (FR-006..FR-011)

**Decision**:
- `Destination` enum gains `HELP` **last** (`HOME, DETAILS, SETTINGS, HELP`) — enum order is footer order and swipe order (spec US5); pager count derives from `Destination.entries`, so the three can never disagree.
- `ui/help/HelpScreen.kt`: one `verticalScroll` Column, five titled sections in spec order. About / Limitations / Per-app memory bodies are ordinary string resources (English-only per spec Assumption); Contacts is two link rows (LinkedIn profile, jkteknologies.com) reusing R-04/R-05; License is R-07's raw text. No search, index, or collapsible sections (spec Assumption).
- FooterBar gains the fourth `FooterButton` (equal `weight(1f)` widths continue unchanged).
- Auto-refresh ticker's `when (destination)` gains `HELP -> Unit` (no refreshable data — Constitution IX).
- `BackHandler` needs no edit: it already returns to HOME from anything that isn't HOME.
- No screen lambdas change signature except Details/Help gaining `onLinkUnavailable` (R-05).

**Rationale**: Everything reuses the existing shell primitives — the feature adds a screen and a destination, not a navigation framework.

**Alternatives considered**: separate Help entry point (menu, About dialog) — rejected: spec FR-006 pins the footer destination; nothing else was requested (YAGNI).

## Open items

None — all NEEDS CLARIFICATION items resolved. Manual-only risks flagged: R-02 (pull-to-refresh × pager) and icon masking/themed-icon rendering (R-06) go to the host manual checklist.
