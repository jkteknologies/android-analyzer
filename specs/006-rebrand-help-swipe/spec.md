# Feature Specification: Pre-Publication Rebrand, Help Screen & Swipe Navigation

**Feature Branch**: `feature/006-ui-improvements`

**Created**: 2026-10-07

**Status**: Draft

**Input**: User description: "Before publishing to the F-Droid store: rename the application to 'Resource Radar' (display name and code package), apply the new logo (design/logos/b-blip-bars.svg), make the Shizuku URL clickable so users without Shizuku can navigate to it and download it, add a 'Help' screen to the footer (About, Limitations, Per-app memory, Contacts, License), and make screens switchable by swiping left and right."

## Clarifications

### Session 2026-10-07

- Q: How should the b-blip-bars artwork be delivered as the launcher icon? → A: Adaptive icon — background + foreground layers rebuilt from the SVG, plus a monochrome layer for Android 13+ themed icons.
- Q: Does this feature include release versioning (bumping versionName/versionCode toward the publication release)? → A: Out of scope — versioning happens as part of the separate publication step.
- Q: When a link is tapped but no browser exists to open it, what should the graceful failure indication be? → A: Transient snackbar (or toast) with a "cannot open link" message that auto-dismisses; no dialog.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - App is branded "Resource Radar" (Priority: P1)

A user installs the app and everything that names the application — the launcher label, in-app text, and system dialogs (e.g., the Shizuku authorization prompt) — reads "Resource Radar". The application's technical identity (its code package and application ID) is renamed to a Resource Radar-derived identifier so the F-Droid listing carries the new brand. Because the app has never been published, no upgrade/migration path from the old identity is needed.

**Why this priority**: The rename is the headline change and everything user-visible depends on it; publishing under the old name would immediately require a rebrand later. The technical identity must be settled before the store listing is created.

**Independent Test**: Can be fully tested by installing the build and checking the launcher label, the in-app strings, and a Shizuku authorization prompt; delivers a correctly branded app.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the user views the device launcher, **Then** the app label reads "Resource Radar".
2. **Given** the app is running, **When** the user views any screen or hint that previously named the app (e.g., Shizuku hints), **Then** it reads "Resource Radar" and no occurrence of the old name "Android Analyzer" remains anywhere user-visible.
3. **Given** the user grants per-app memory access, **When** the Shizuku authorization dialog appears, **Then** the app name shown in that system dialog reads "Resource Radar".
4. **Given** the built application, **When** its technical identity is inspected, **Then** the code package and application ID derive from "Resource Radar" and the old identifier is gone.

---

### User Story 2 - New launcher icon (Priority: P1)

A user scanning the launcher recognizes the app by its new logo: the b-blip-bars artwork. The icon follows the modern launcher icon standard so it renders correctly on current Android launchers at all launcher sizes.

**Why this priority**: The icon is half of the brand rename; shipping the new name under the old (or a default) icon would look unfinished at publication time.

**Independent Test**: Can be fully tested by installing the build and visually comparing the launcher icon against the approved design at launcher sizes; delivers a recognizable brand icon.

**Acceptance Scenarios**:

1. **Given** a fresh install, **When** the user views the launcher, **Then** the app icon is the b-blip-bars design.
2. **Given** a modern Android launcher, **When** the icon is displayed at any launcher size or masked per launcher style, **Then** it renders correctly (not blurry, not clipped).

---

### User Story 3 - Help screen (Priority: P2)

A user who wants to understand the app opens the new "Help" destination in the footer and finds five sections in one scrollable screen, in this order: **About** (briefly: what the app does — device resource overview, per-app details, Shizuku-based per-app memory), **Limitations** (briefly: what the app cannot do today), **Per-app memory** (how to retrieve per-app memory information, including the Shizuku steps), **Contacts** (author's LinkedIn profile and company website, both tappable), and **License** (the complete AGPL v3 license text, verbatim from the project's LICENSE file).

**Why this priority**: The Help screen is the main new user value of this feature — it makes the app self-explanatory and reachable for support — but it builds on the renamed, branded app.

**Independent Test**: Can be fully tested by opening the Help tab and reading the five sections, tapping both contact links, and comparing the license text against the LICENSE file; delivers a self-documenting app.

**Acceptance Scenarios**:

1. **Given** the app is open, **When** the user looks at the footer, **Then** a "Help" destination is present alongside Home, Details, and Settings.
2. **Given** the Help screen is open, **When** the user scrolls it, **Then** all five sections (About, Limitations, Per-app memory, Contacts, License) appear in that order with content for each.
3. **Given** the Contacts section, **When** the user taps the LinkedIn entry, **Then** the author's LinkedIn profile opens; **When** the user taps the company website entry, **Then** jkteknologies.com opens.
4. **Given** the License section, **When** the user scrolls through it, **Then** the full AGPL v3 text is shown, matching the LICENSE file verbatim (nothing truncated or reworded).
5. **Given** the License section is long, **When** the user scrolls, **Then** the screen scrolls smoothly and no content is cut off.

---

### User Story 4 - One-tap Shizuku download link (Priority: P2)

A user without Shizuku who opens an app's details sees the hint that per-app memory needs the Shizuku app. The website address in that hint is tappable: one tap opens the Shizuku website in the device browser so the user can download Shizuku.

**Why this priority**: It directly removes the biggest adoption blocker for the app's advanced feature, but it touches only one hint and depends on nothing else in this feature.

**Independent Test**: Can be fully tested by opening an app's details on a device without Shizuku and tapping the link; delivers a one-tap path to the Shizuku download page.

**Acceptance Scenarios**:

1. **Given** Shizuku is not installed, **When** the user views the per-app memory hint, **Then** the Shizuku website address is visibly tappable.
2. **Given** the tappable address, **When** the user taps it, **Then** the Shizuku website (shizuku.rikka.app) opens in the device browser, and returning brings the user back to the app unchanged.

---

### User Story 5 - Swipe between screens (Priority: P3)

A user can move between the main screens (Home, Details, Settings, Help) by swiping horizontally — swipe left for the next screen, swipe right for the previous one — in the same order as the footer. The footer selection follows the visible screen, so swiping and tapping stay consistent.

**Why this priority**: It is a convenience/navigation polish; the footer already provides full navigation, so the app is perfectly usable without swiping.

**Independent Test**: Can be fully tested by swiping left and right on each main screen and observing the screen and footer selection change; delivers touch-first navigation.

**Acceptance Scenarios**:

1. **Given** the Home screen, **When** the user swipes left, **Then** the next screen in footer order is shown and the footer highlights it.
2. **Given** any screen except the first, **When** the user swipes right, **Then** the previous screen in footer order is shown and the footer highlights it.
3. **Given** the first screen, **When** the user swipes right, **Then** nothing happens (no wrap-around); **Given** the last screen, **When** the user swipes left, **Then** nothing happens.
4. **Given** footer order (Home, Details, Settings, Help), **When** the user swipes from Help to Settings, **Then** Settings shows its normal content, exactly as if it had been opened from the footer.

### Edge Cases

- What happens when the user swipes vertically (scrolling the Help screen or the long license text)? Vertical scrolling must scroll normally; only horizontal swipes switch screens.
- What happens when a link is tapped on a device with no browser (or no handler for the link)? The app must not crash; the user gets a graceful "cannot open" indication.
- What happens when the user swipes onto the Details screen without having selected an app? The Details screen shows its existing no-selection state; no crash, no stale content from a previously viewed app unless that is already its current behavior.
- What happens when a horizontal swipe starts inside horizontally-scrollable content (if any exists)? The inner content consumes the gesture first; the screen must not switch mid-scroll.
- Does swiping discard the current screen's state (e.g., Home's loaded data, Details' selected app, Settings' unsaved-looking toggles)? No — swiping must behave exactly like tapping the corresponding footer destination, preserving state as that flow already does.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The application MUST present the display name "Resource Radar" everywhere the app is named to the user: launcher label, all in-app strings, and system-visible identity (e.g., dialogs shown by other apps such as the Shizuku authorization prompt).
- **FR-002**: The application MUST no longer contain any user-visible occurrence of the old name "Android Analyzer".
- **FR-003**: The application's technical identity (code package name and application ID) MUST be renamed to an identifier derived from "Resource Radar"; the old identifier MUST NOT be retained. No data migration from the old identity is required (app never published).
- **FR-004**: The launcher icon MUST be the b-blip-bars logo artwork delivered as an adaptive icon — full-bleed background layer plus the artwork's foreground inside the mask safe zone, rebuilt from the SVG — and MUST include a monochrome layer so Android 13+ themed icons work; it MUST render correctly at launcher sizes and with launcher masking.
- **FR-005**: The Shizuku not-installed hint MUST present the Shizuku website address (shizuku.rikka.app) as a tappable link that opens https://shizuku.rikka.app in the device browser and returns the user to the app afterwards.
- **FR-006**: The footer MUST include a fourth destination named "Help" alongside Home, Details, and Settings.
- **FR-007**: The Help screen MUST present exactly these sections in this order: About, Limitations, Per-app memory, Contacts, License.
- **FR-008**: The About section MUST briefly describe what the application does (device-wide resource overview, per-app details, Shizuku-based per-app memory).
- **FR-009**: The Limitations section MUST briefly describe the app's current limitations (e.g., per-app memory requires Shizuku, usage-access grant needed for per-app data usage, figures are estimates, not forensics).
- **FR-010**: The Per-app memory section MUST briefly explain how the user retrieves per-app memory information, including installing/starting Shizuku, granting access, and opening an app's details.
- **FR-011**: The Contacts section MUST present the author's LinkedIn profile (https://www.linkedin.com/in/jurijskolomijecs/) and the company website (https://jkteknologies.com/), and each MUST be tappable, opening in the device browser.
- **FR-012**: The License section MUST display the complete AGPL v3 license text, verbatim identical to the project's LICENSE file.
- **FR-013**: A horizontal swipe on any main screen MUST switch to the adjacent destination in footer order (left = next, right = previous), without wrap-around at the first and last destinations.
- **FR-014**: After a swipe, the footer selection MUST highlight the newly visible screen, and the swipe MUST leave the screen's state exactly as tapping that footer destination would.
- **FR-015**: Vertical scroll gestures MUST NOT trigger screen switching; horizontal swipe detection MUST NOT break scrolling of long content (especially the License section).
- **FR-016**: Tapping any in-app link MUST NOT crash the app when no handler (e.g., browser) is available; the user MUST see a transient snackbar (or equivalent toast) indicating the link cannot be opened, which auto-dismisses — no modal dialog.

### Key Entities

Not applicable — this feature changes branding, static help content, and navigation; it introduces no new data entities and does not alter stored data. (The Help screen's content is static text shipped with the app.)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of user-visible app-name occurrences read "Resource Radar"; a search of the shipped app's user-facing text (launcher label, in-app strings, system dialogs) finds zero occurrences of "Android Analyzer".
- **SC-002**: The launcher icon visually matches the approved b-blip-bars design when inspected at launcher sizes.
- **SC-003**: A user without Shizuku reaches the Shizuku website with a single tap from the per-app memory hint (previously zero tappable path).
- **SC-004**: A user can find the author's contact links with two interactions: open Help, scroll to Contacts.
- **SC-005**: The in-app license text matches the LICENSE file verbatim (100% identical text, no truncation).
- **SC-006**: Every main screen is reachable by swipe alone, and a swipe transition completes about as fast as tapping the footer destination (no perceptible delay).
- **SC-007**: Existing functionality is unaffected: the full local unit-test suite and linters pass after the change.

## Assumptions

- **New technical identity**: the code package and application ID become `com.jkteknologies.resourceradar`, preserving the existing company-domain prefix convention. Safe because the app was never published; already-sideloaded test installs are simply uninstalled/reinstalled.
- **Shizuku link target**: https://shizuku.rikka.app — the address already referenced in the existing hint text.
- **Help layout**: one scrollable screen with the five sections stacked in the given order; no search, index, or collapsible sections for this first version. The full license text is embedded (roughly 20 KB — negligible size impact).
- **Swipe mapping**: swipe order equals footer order (Home → Details → Settings → Help); no wrap-around at the ends.
- **Logo placement**: the b-blip-bars artwork is applied as the launcher icon, delivered as an adaptive icon (background + foreground layers plus monochrome, rebuilt from the SVG); in-app screens keep the text-based header. Source artwork lives at `design/logos/b-blip-bars.svg` with prepared 48 px and 512 px PNG renders alongside it.
- **Links** open in the device browser via the platform's standard mechanism; no in-app browser is added (no new permission needed).
- **Language**: Help content is written in English, matching the app's existing single-language strings.
- **Documentation**: repository docs that name the app (e.g., README) are updated to the new name as part of the rebrand, since the publication depends on them.
- **Release versioning**: out of scope — this feature ships no `versionName`/`versionCode` bump; version numbers are handled by the separate publication step.
