# Contract: Navigation, Footer & Links — 006

The app's user-facing behavior surface changed by 006. This is the contract `tasks.md`
implements and the manual checklist verifies. Preceding contract: 004
`contracts/navigation-and-footer.md` (N-1..N-4) — this file supersedes it where they overlap.

## Navigation & footer

**N-1** The footer shows exactly four equal-width buttons in this order: **Home, Details,
Settings, Help** (FR-006). Same visual contract as 004: continuous full-width bar, gapless,
square-cornered, current destination filled vs tonal.

**N-2** The four screens form a horizontal strip in footer order. A horizontal swipe left shows
the next destination; swipe right shows the previous (FR-013). At HOME, swipe right does
nothing; at HELP, swipe left does nothing — no wrap-around.

**N-3** Footer selection follows the *settled* page (R-01): after a swipe completes, the newly
visible destination is highlighted (FR-014). Footer taps animate the strip to the tapped page;
the highlight follows it.

**N-4** Screen state after arrival is identical whether the user tapped or swiped (FR-014,
Edge Cases 5): Home re-reads on re-entry; Details keeps its current selection/filter; Settings
keeps its values; no new persistence is introduced by swiping.

**N-5** Vertical gestures stay vertical: pull-to-refresh works on Home; the Help column (and
its license text) scrolls; neither switches screens (FR-015). Vertical swipes never land on an
adjacent page.

**N-6** System back returns to HOME from any non-HOME destination — including HELP (unchanged
`BackHandler` semantics). Back on HOME exits.

**N-7** Auto-refresh ticker mapping (004 FR-014..017 extended): HOME → home refresh,
DETAILS → details refresh, SETTINGS and HELP → no tick work.

## Links (FR-005, FR-011, FR-016)

**L-1** Three fixed https targets are user-tappable:
- Shizuku hint link (Details guidance row, NOT_INSTALLED state): `https://shizuku.rikka.app`
- Contacts row 1: `https://www.linkedin.com/in/jurijskolomijecs/`
- Contacts row 2: `https://jkteknologies.com/`

**L-2** Tapping any of them opens the device browser via an implicit `ACTION_VIEW` intent and
returns the user to the app unchanged afterwards (no in-app browser).

**L-3** If no handler exists, the app MUST NOT crash: it shows one transient auto-dismissing
snackbar (no dialog). The message is the `link_unavailable` string. (FR-016 — clarify session
2026-10-07.)

**L-4** Link opening adds nothing to the manifest: no new permission, no `<queries>` (implicit
`startActivity` needs neither). Failure detection is `ActivityNotFoundException` only.

## Identity (FR-001..FR-004)

**I-1** Launcher label, activity label, all in-app strings, and therefore the Shizuku
authorization dialog read "Resource Radar"; zero user-visible "Android Analyzer" occurrences
(SC-001).

**I-2** applicationId + code package = `com.jkteknologies.resourceradar`; no
`androidanalyzer` segment anywhere in the build output or source tree (FR-003).

**I-3** Launcher icon = b-blip-bars adaptive icon: full-bleed near-black background layer,
bars+ring foreground inside the mask safe zone, flat-white monochrome layer (themed icons on
Android 13+); renders correctly under launcher masks at any size (FR-004, SC-002).
