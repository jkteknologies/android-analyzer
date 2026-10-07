# Feature Specification: Per-App Information and Details Screen

**Feature Branch**: `004-per-app-info`

**Created**: 2026-10-07

**Status**: Draft

**Input**: User description: "The user wants to further extend the Android application. There must be 3 options in footer now: home, details, settings. Home screen should be extended with following: Memory - show available and allocated memory (already exists); Internal storage - show free and used storage (already exists); Battery - battery percentage and charging status (already exists); Processor - if possible, should show amount of various types of processors, e.g. slow, fast, whatever else; System Applications - a number of system applications; User Applications - a number of user applications. If system applications or user applications is clicked, this should navigate to 'details' category for selected app type. Details screen should list all the applications installed on a device with a mark whether it's system or user application. There must be an option to filter by user / system application. For each application it should show amount of memory and storage consumed by the app. If user is navigated to details screen from home screen by clicking on 'user applications' or 'system applications', the filter must be in place right away. Settings screen should keep theme option + possibility to select automated information refresh. By default it should be 'on demand', e.g. the user has to swipe either on home screen, or in details screen. Additionally the user wants to update style of footer, it should not be rounded, but rectangle buttons without free space between them."

## Clarifications

### Session 2026-10-07

- Q: How should the Details screen handle the per-app "memory consumed" figure, given
  Android does not expose other applications' RAM usage to an ordinary app? → A: Keep
  the figure and display the explicit not-available indication (confirmed by the user;
  consistent with the never-display-a-wrong-number policy, and the real value appears
  automatically if a device ever exposes it).
- Q: Which automatic refresh intervals should the Settings screen offer? → A: On demand
  (the default), 30 seconds, 1 minute, and 5 minutes (confirmed by the user; encoded in
  FR-015).
- Q: What happens when the user taps an individual application row in the Details list?
  → A: The device's own settings open on that application's information page (confirmed
  by the user; encoded in FR-018).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Application Inventory with Per-App Figures (Priority: P1)

As a device owner, I open the Details tab and see every application installed on my
device — each one marked as a system or a user application — together with the amount of
storage and memory that application consumes, and I can filter the list to see only user
or only system applications, so that I can find out what is installed on my device and
what it costs in resources.

**Why this priority**: Per-application visibility is the core new information this
feature adds; the whole navigation and home-screen work exists to make this screen
reachable and quick to drill into.

**Independent Test**: Can be fully tested by opening the Details tab on a device or
emulator with a known set of installed applications (at least one user application plus
the pre-installed system applications) and comparing the list contents, marks, counts and
figures against the device's own application list and storage information. Delivers a
working per-app analyzer on its own.

**Acceptance Scenarios**:

1. **Given** a device with user and system applications installed, **When** the Details tab is opened, **Then** every installed application is listed with its display name and a clear mark of whether it is a system or a user application, and the number of entries matches the number of applications the device reports as installed.
2. **Given** the full list is displayed, **When** the user selects the User filter, **Then** only user applications remain listed; when the System filter is selected, only system applications remain; when All is selected (the default), the full list returns.
3. **Given** per-app figures are readable on the device, **When** an application's entry is displayed, **Then** it shows the storage that application consumes and the memory it consumes, in the same human-readable form the Home screen already uses.
4. **Given** a figure cannot be read for an application on this device, **When** its entry is displayed, **Then** the entry shows an explicit not-available indication for that figure instead of a value — never zero or another substitute number.
5. **Given** reading per-app figures requires an access the user must grant on the device and it is not yet granted, **When** the user opens the Details tab, **Then** the application explains what is missing and offers a path to the relevant device setting, while the list, marks and filter continue to work; after the user grants the access and the screen is refreshed, the figures appear.
6. **Given** the Details tab is displaying applications, **When** the user pulls the screen content downward past the gesture's trigger distance and releases, **Then** the application inventory and all displayed figures are re-read and updated, following the same pull-to-refresh behavior as the Home screen.
7. **Given** the Details list is displayed, **When** the user taps an application's row, **Then** the device's own settings open on that application's information page, and returning to the analyzer restores the Details list with its filter unchanged.

---

### User Story 2 - Three-Tab Footer Navigation with Restyled Bar (Priority: P2)

As a user of the application, I see a footer with three options — Home, Details and
Settings — rendered as one continuous rectangular bar of buttons with no rounding and no
gaps between them, and tapping any option takes me to that screen, so that all three
screens are always one tap away and the footer looks like a single solid strip.

**Why this priority**: Navigation is the structural backbone that makes the new Details
screen reachable from everywhere, and the restyle is part of the same footer change; it
ranks below the Details screen itself because it enables value rather than deliver it.

**Independent Test**: Can be fully tested by tapping each footer option in turn on every
screen and observing that the displayed screen switches, the active option is indicated,
and the footer renders as one gapless rectangular bar. Delivers complete three-way
navigation on its own.

**Acceptance Scenarios**:

1. **Given** the application is open on any screen, **When** the user taps the Home, Details or Settings footer option, **Then** the corresponding screen is displayed.
2. **Given** any screen is displayed, **When** the user looks at the footer, **Then** the currently shown screen's option is visually indicated as active, and exactly one option is indicated.
3. **Given** the footer is displayed, **When** it is inspected, **Then** its buttons are rectangular (no rounded corners) and adjacent buttons touch with no free space between them, forming one continuous bar.
4. **Given** the user switches between screens via the footer, **When** the Settings screen is revisited, **Then** the previously chosen theme and refresh settings are still in effect.

---

### User Story 3 - Home Entry Points with Pre-Applied Filter (Priority: P3)

As a device owner viewing the Home screen, I see the number of system applications and
the number of user applications as two separate entries, and tapping either one takes me
straight to the Details screen with the matching filter already applied — System
applications for the system count, User applications for the user count — so that I can
go from "how many?" to "which ones?" in a single tap.

**Why this priority**: This turns the existing counts into a drill-down into the Details
screen; it depends on both the Details screen and its filter existing, so it ranks below
them.

**Independent Test**: Can be fully tested by comparing the two Home counts against the
device's application list, then tapping each entry and verifying the arriving Details
screen already shows only the matching applications with the filter control reflecting
it. Delivers home-to-details drill-down on its own.

**Acceptance Scenarios**:

1. **Given** the Home screen is displayed, **When** the application entries are read, **Then** one entry shows the number of installed system applications and a separate entry shows the number of installed user applications, replacing the previous combined count-with-total presentation.
2. **Given** the Home screen shows a system count S and a user count U, **When** the Details screen shows all applications, **Then** the number of system-marked entries equals S and the number of user-marked entries equals U.
3. **Given** the Home screen is displayed, **When** the user taps the System Applications entry, **Then** the Details screen opens already filtered to system applications, with the filter control showing System and no unfiltered full list flashing beforehand; tapping the User Applications entry behaves the same way for user applications.
4. **Given** the Details screen is already open with a filter the user chose, **When** the user goes Home and taps an application entry, **Then** the arriving filter is the one matching the tapped entry, overriding the previously chosen one.
5. **Given** a device with no user applications installed (for example, a fresh emulator), **When** the Home screen is displayed, **Then** the User Applications entry shows 0, and tapping it opens the Details screen with an explanatory empty state for the User filter.

---

### User Story 4 - Automatic Refresh Setting (Priority: P4)

As a user, I can choose in Settings between refreshing information on demand (the
default — I pull down on the Home or Details screen to refresh) and an automatic refresh
at a fixed interval, so that the figures keep themselves current without my gesture when
I want that, and my choice is remembered.

**Why this priority**: Automatic refresh is a convenience over the existing manual
refresh; it changes no information, only how current it stays, so it ranks below the
screens and drill-down that carry the new value.

**Independent Test**: Can be fully tested by changing the device state (for example,
installing an application), leaving the visible screen untouched, and observing that with
an interval selected the figures update by themselves within the interval, while with the
default On-demand mode they do not change until a pull-down. Delivers self-updating
screens on its own.

**Acceptance Scenarios**:

1. **Given** the application is freshly installed, **When** the Settings screen is opened, **Then** the refresh option is set to On demand, and the theme option from before is still present and working.
2. **Given** the refresh mode is On demand, **When** the device state changes and the Home or Details screen stays untouched, **Then** the displayed figures do not change until the user pulls down to refresh.
3. **Given** the user selects a fixed refresh interval, **When** the Home or Details screen is visible and the device state changes, **Then** the visible screen's figures update automatically within the selected interval without any user gesture, and the pull-down gesture still triggers an immediate refresh.
4. **Given** the user selects a fixed refresh interval, **When** the application is closed and reopened, **Then** the selected mode is still in effect.
5. **Given** a fixed refresh interval is selected, **When** the application is not visible on screen, **Then** no background refreshing occurs, and refreshing resumes on the visible screen when the application is next used.

---

### User Story 5 - Processor Core Types (Priority: P5)

As a device owner, I see on the Home screen not just how many processor cores my device
has, but how many of each type — for example how many slower and how many faster cores —
where the device distinguishes them, so that I understand the makeup of my device's
processor rather than only its size.

**Why this priority**: This deepens an existing informational entry with detail the user
explicitly asked for "if possible"; it adds no new capability and is bounded by what the
device exposes, so it ranks last.

**Independent Test**: Can be fully tested on a device or emulator whose processor
distinguishes core types by comparing the per-type counts against the device's reported
core configuration, and on a device that does not distinguish types by verifying the
total core count still displays. Delivers processor-type information on its own.

**Acceptance Scenarios**:

1. **Given** a device whose processor has distinguishable core types (for example slower and faster cores), **When** the Home screen is displayed, **Then** the processor entry shows the number of cores of each type, and the per-type counts sum to the device's total core count.
2. **Given** a device whose processor does not expose distinguishable core types, **When** the Home screen is displayed, **Then** the processor entry continues to show the total core count as it does today, with no error or partial information.

---

### Edge Cases

- What happens when the access needed for per-app figures is never granted? The list,
  marks, filter and counts keep working; every figure that cannot be read shows the
  not-available indication; the explanation and path to the device setting remain
  reachable from the Details screen without blocking its use.
- What happens when a filter matches zero applications (for example User on a fresh
  emulator)? The Details screen shows an explanatory empty state naming the active
  filter, not a blank screen.
- What happens when applications are installed or uninstalled while the Details list is
  displayed? The list reflects the state as of its last refresh; the next refresh —
  manual or automatic — reconciles it, and a completed refresh never leaves stale
  entries.
- What happens on a device with hundreds of installed applications? The list scrolls
  smoothly, and filtering and the Home counts complete immediately.
- What happens when two applications share the same display name? Both are listed; each
  entry carries its own mark and figures.
- What happens when the user arrives from the Home screen while the Details screen had a
  different filter selected? The filter matching the tapped Home entry wins.
- What happens when the automatic refresh interval elapses while the application is not
  visible? No background refresh runs; the visible screen refreshes automatically the
  next time the application is used.
- What happens when the user taps a row for an application that was uninstalled since
  the last refresh? The device's settings handle the missing application; the analyzer
  does not crash and returns to a working Details list.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The application MUST provide a footer with exactly three destinations — Home, Details, and Settings — each reachable in one tap from every screen.
- **FR-002**: The footer MUST render as one continuous rectangular bar: buttons MUST have square corners (no rounding) and there MUST be no free space between adjacent buttons.
- **FR-003**: The footer MUST visually indicate which of the three destinations is currently displayed, with exactly one option indicated at any time.
- **FR-004**: The Details screen MUST list every application installed on the device — both user and system applications — showing each application's display name and a clear mark of whether it is a system or a user application.
- **FR-005**: The Details screen MUST offer a filter with the options All (the default), User, and System; changing the filter MUST immediately reduce the displayed list to the matching applications.
- **FR-006**: For each listed application, the Details screen MUST show the storage consumed by that application (its code, data, and caches) and the memory it consumes, in human-readable form consistent with the Home screen's existing figure formatting.
- **FR-007**: Where an application's figure cannot be read on the device, its entry MUST show an explicit not-available indication for that figure; it MUST NOT display zero or any substitute value.
- **FR-008**: Where reading per-application figures requires an access the user must grant on the device, the application MUST explain what is missing and offer a path to the relevant device setting; the list, marks, and filter MUST be fully usable without that grant.
- **FR-009**: The Details screen MUST support the same pull-down refresh gesture and feedback as the Home screen (Feature 003), re-reading the application inventory and all displayed figures.
- **FR-010**: All figures on the Details screen MUST follow the established three-state display rules (loading placeholder, then value or not-available) and the established validation rules (no negative or nonsensical values; a figure failing validation displays as not-available).
- **FR-011**: The Home screen MUST show a System Applications entry with the number of installed system applications and a User Applications entry with the number of installed user applications; these two entries replace the combined user-count-with-total-in-brackets presentation from Feature 002.
- **FR-012**: Tapping the System Applications entry MUST open the Details screen with the System filter already applied, and tapping the User Applications entry MUST open it with the User filter already applied; an arrival from the Home screen MUST override any filter previously chosen on the Details screen.
- **FR-013**: The Home screen's processor entry MUST show, where the device distinguishes them, the number of cores of each processor type (for example slower and faster cores); where the device does not distinguish core types, it MUST continue to show the total core count.
- **FR-014**: The Settings screen MUST retain the theme option unchanged from Feature 002 and MUST add an automatic-refresh option whose default is On demand — information refreshes only when the user pulls down on the Home or Details screen.
- **FR-015**: In addition to On demand, the user MUST be able to select a fixed automatic refresh interval — 30 seconds, 1 minute, or 5 minutes; the selected mode MUST persist across application restarts.
- **FR-016**: While a fixed interval is selected, the information on the currently visible screen — Home or Details — MUST refresh automatically at that interval without any gesture; the pull-down gesture MUST remain available in every mode.
- **FR-017**: Automatic refresh MUST operate only while the application is visible; no refresh work may run while it is in the background.
- **FR-018**: Tapping an application row in the Details list MUST open that application's information page in the device's own settings; after returning from it, the analyzer MUST still show the Details list with its filter unchanged.

### Key Entities

- **Installed Application**: an application present on the device; its display name; its classification as a system or user application; the storage it consumes (a size, or not-available); the memory it consumes (an amount, or not-available).
- **Application Filter**: the Details screen's list restriction — All, User, or System.
- **Refresh Mode**: how information gets refreshed — On demand (pull-down gesture) or a fixed automatic interval.
- **Processor Core Tiers**: the Home screen's processor breakdown — a set of core types (for example slower/faster) with a core count each, or a single total when the device does not distinguish types.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After a refresh, the Details screen lists exactly as many applications as the device reports installed, with the system/user split matching the Home screen's System and User counts.
- **SC-002**: Switching between the All, User, and System filters applies immediately, and the number of entries under each filter equals the corresponding Home count (all = system + user).
- **SC-003**: Once any required access is granted, each application's displayed storage figure equals the device's own reported size for that application (within display rounding), and each memory figure either equals the device's reported value or shows the not-available indication — never a wrong number.
- **SC-004**: Tapping a Home application entry lands on the Details screen already filtered to that category, with no unfiltered list shown in between, verifiable on every arrival.
- **SC-005**: All three screens are reachable in one tap from any screen, the active footer option is indicated, and the footer renders as one gapless rectangular bar on every screen.
- **SC-006**: A fresh installation starts in On-demand mode; a selected refresh mode persists across restarts; with an interval selected, a visible screen's stale figures update automatically within the selected interval with no user gesture.
- **SC-007**: On a device or emulator with 200+ installed applications, the Details list scrolls and filters without perceptible lag (a filter change visibly applies in under a second).
- **SC-008**: On hardware that distinguishes processor core types, the Home screen shows per-type core counts that sum to the total core count; on hardware that does not, the total core count continues to display.

## Assumptions

- Per-application "memory consumed" means the memory the application currently occupies
  where the device exposes that to an ordinary app; where the device does not expose it,
  the not-available indication is shown, consistent with Feature 002's policy of never
  displaying a wrong number.
- Per-application "storage consumed" means the application's code, data, and caches
  combined; on current Android this typically requires the user to grant the analyzer
  usage-data access, which is why FR-008 mandates the explanation and setting path. The
  inventory itself (names, marks, filter, counts) requires no special grant.
- The automatic refresh intervals offered alongside On demand are 30 seconds, 1 minute,
  and 5 minutes (confirmed with the user on 2026-10-07).
- The Details list is ordered alphabetically by application display name; All is the
  default filter until the user changes it or arrives from a Home entry.
- The system/user classification reuses the definition already established and shipped
  in Feature 002, so the Home counts and the Details marks cannot disagree.
- Existing Home figures (memory, internal storage, battery), the theme setting, and the
  Feature 003 pull-to-refresh behavior on Home are unchanged except where this spec
  states otherwise; the pull-to-refresh behavior extends to the Details screen as-is.
- Devices expose processor core types as groups of cores (commonly two or three tiers,
  often described as slower/faster cores); where a device exposes no distinction, the
  existing total core count remains the display.
