# Feature Specification: Shizuku-Sourced Per-App Memory Information

**Feature Branch**: `005-shizuku-memory`

**Created**: 2026-10-07

**Status**: Draft

**Input**: User description: "Implement Shizuku in order to retrieve per-app memory information"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Real Per-App Memory Figures via Shizuku (Priority: P1)

As a device owner, I have Shizuku (an open-source helper application that lends other
apps a privileged, read-only view of the device) installed, running, and authorized for
the analyzer, and when I open the Details screen every listed application shows the
memory it actually occupies right now — replacing the not-available indication that
appears today — so that I can finally see which applications really eat my RAM.

**Why this priority**: The real memory figure is the entire value of this feature; the
Details screen already reserves the slot (Feature 004), so delivering the number is the
core outcome everything else serves.

**Independent Test**: Can be fully tested on a device with Shizuku set up by comparing
each application's displayed memory figure against the device's own per-application
memory report, and by checking that installed-but-not-running applications show zero
rather than not-available. Delivers truthful per-app memory analysis on its own.

**Acceptance Scenarios**:

1. **Given** Shizuku is installed, running, and the analyzer is authorized in it, **When** the Details screen is opened or refreshed, **Then** each listed application shows its current memory occupancy in the slot and formatting Feature 004 defined, replacing the not-available indication.
2. **Given** memory figures are displayed, **When** they are compared with the device's own per-application memory report, **Then** each figure matches that report within display rounding — or shows the not-available indication where an individual read fails — and never shows a wrong number.
3. **Given** an application runs several processes on the device, **When** its entry is displayed, **Then** its memory figure covers all of that application's processes combined.
4. **Given** an application is installed but currently has no running processes, **When** its entry is displayed, **Then** its memory figure reads zero (as a value), because it genuinely occupies no memory.
5. **Given** any existing refresh trigger — pull-down, automatic interval, or entering the screen, **When** it fires, **Then** the per-app memory figures are re-read together with the rest of the displayed information.
6. **Given** the memory read succeeds for some applications but fails for one, **When** the list is displayed, **Then** the failed application shows the not-available indication while the others display their values.

---

### User Story 2 - Guided Shizuku Setup and Authorization (Priority: P2)

As a device owner without Shizuku set up, I open the Details screen and see, in plain
language, what is missing — Shizuku not installed, not running, or awaiting
authorization — together with the matching next action (how to get Shizuku, an action
that opens it, or an action that requests authorization), so that I can reach the
state where the memory figures appear without guessing or leaving the analyzer
confused.

**Why this priority**: This is the bridge that makes Story 1 achievable for a user
starting from nothing; it delivers no figures itself but removes every dead end on
the way to them.

**Independent Test**: Can be fully tested by deliberately setting up each state (no
Shizuku, installed-but-stopped, running-but-unauthorized) and verifying the guidance
names the state, offers the matching action, and that memory figures show not-available
while everything else keeps working in each state. Delivers a self-explanatory setup
path on its own.

**Acceptance Scenarios**:

1. **Given** Shizuku is not installed on the device, **When** the Details screen is opened, **Then** a plain-language element explains that per-app memory needs Shizuku and how to obtain it, the memory figures show the not-available indication, and the list, marks, filter, counts, storage figures, and navigation all keep working.
2. **Given** Shizuku is installed but not running, **When** the Details screen displays its guidance, **Then** the guidance names that Shizuku is stopped and offers an action that opens the Shizuku application so the user can start it.
3. **Given** Shizuku is running but the analyzer is not yet authorized, **When** the Details screen displays its guidance, **Then** it offers an action to request authorization, which triggers Shizuku's own confirmation; the analyzer does not attempt to bypass or replace that confirmation.
4. **Given** the user completes authorization in Shizuku while the Details screen is visible, **When** the confirmation finishes, **Then** the analyzer re-reads on its own and the memory figures appear — with no application restart and no manual refresh required.
5. **Given** the elevated-access state changes, **When** the Details screen is viewed, **Then** the guidance shown always names the current state, never a stale one.

---

### User Story 3 - Graceful Degradation and Recovery While In Use (Priority: P3)

As a device owner using the analyzer, Shizuku stops (for example after a reboot) or my
authorization is revoked while the Details screen is open, and the analyzer tells me so
and reverts the memory figures to not-available without crashing or freezing; when
Shizuku comes back and is still authorized, the figures return on the next refresh —
without me ever restarting the analyzer.

**Why this priority**: Robustness keeps the honest-figures promise under real device
conditions (Shizuku stopping is routine, not exceptional); it ranks last because it
protects value rather than creating it.

**Independent Test**: Can be fully tested by toggling Shizuku's state (stop, revoke,
restart, re-grant) repeatedly while the analyzer stays open, verifying stability, the
reverting and returning of figures, and the guidance tracking the state each time.
Delivers dependable behavior on its own.

**Acceptance Scenarios**:

1. **Given** memory figures are displayed, **When** Shizuku is stopped or the analyzer's authorization is revoked, **Then** the guidance for the new state appears, the memory figures revert to the not-available indication at the latest by the next refresh, and the analyzer neither crashes nor freezes.
2. **Given** Shizuku was stopped and is started again while still authorized, **When** the next refresh or in-app interaction occurs, **Then** the memory figures return without an application restart.
3. **Given** Shizuku's service disappears in the middle of a refresh, **When** that refresh completes, **Then** applications whose memory was already read keep their figures, the unread ones show the not-available indication, and no error screen or stale number results.
4. **Given** at least three consecutive revoke/re-grant cycles while the analyzer stays open, **When** the cycles finish, **Then** the analyzer remains stable and ends showing correct figures for the final state.

---

### Edge Cases

- What happens when Shizuku is installed but stopped (the routine state after a device
  reboot)? The Not-running guidance with the open-Shizuku action is shown; memory
  figures show not-available; the analyzer never tries to start Shizuku itself.
- What happens when the installed Shizuku is too old to provide the needed
  information? It is treated like the not-running state, with guidance asking the user
  to update Shizuku where the version can be detected; memory stays not-available.
- What happens when an application was uninstalled between the inventory read and the
  memory read of the same refresh? The entry disappears on the next inventory refresh
  and never shows a fabricated figure; the analyzer does not crash on the vanished
  application.
- What happens when an application's process list changes while its memory is being
  read? The figure reflects the processes the device reported at read time and stays a
  truthful snapshot, never a mix of moments stitched into one number beyond what the
  device itself reports in one query.
- What happens when the device has multiple user profiles or a work profile? Figures
  cover the applications the analyzer lists (the current user's inventory); memory of
  other users' processes is not attributed to them.
- What happens when the Details screen is left open on a device where Shizuku runs via
  wireless debugging and that link drops? This is indistinguishable from Shizuku
  stopping: guidance plus not-available figures, recovery on the next refresh after
  the link returns.
- What happens when memory values arrive but fail the analyzer's figure validation
  (negative or nonsensical)? The affected figure displays as not-available, consistent
  with the existing never-display-a-wrong-number policy.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: When Shizuku is installed, running, and the analyzer is authorized in it, the Details screen MUST display, for each listed application, the memory that application currently occupies, replacing the not-available indication from Feature 004 and using the analyzer's existing human-readable figure formatting.
- **FR-002**: The per-application memory figure MUST represent the total resident memory the device attributes to that application's running processes; where an application runs multiple processes, the figure MUST cover all of them combined; where the application has no running processes, the figure MUST be zero as a normal value.
- **FR-003**: The analyzer MUST use Shizuku as its sole elevated-access mechanism for this feature and MUST NOT require any other privileged pathway (root, or pairing with a computer performed by the analyzer itself).
- **FR-004**: The Details screen MUST present the current Shizuku state in plain language — Not installed, Not running, Awaiting authorization, or Authorized — each state paired with its matching next action: guidance on obtaining Shizuku, an action that opens the Shizuku application, or an action that requests authorization. The guidance MUST be reachable while the rest of the Details screen remains usable.
- **FR-005**: Requesting authorization MUST happen through Shizuku's own user confirmation; the analyzer MUST NOT bypass or suppress that confirmation and MUST NOT store any credential or token for Shizuku.
- **FR-006**: In every unauthorized state (Not installed, Not running, Awaiting authorization), all Feature 004 behavior MUST remain intact — list, marks, filter, counts, storage figures, row tap, pull-to-refresh — and the memory figures MUST show the not-available indication.
- **FR-007**: Changes in the Shizuku state MUST be reflected without restarting the analyzer, at the latest by the next refresh; when authorization is newly granted while a screen is visible, the analyzer MUST re-read the memory figures by itself.
- **FR-008**: Per-app memory figures MUST follow the established three-state display rules (loading indication, then value or not-available) and the established validation rules — negative or nonsensical values display as not-available, never zero or another substitute.
- **FR-009**: All existing refresh mechanisms — pull-down, automatic interval, and entering the screen — MUST refresh the per-app memory figures together with the rest of the displayed information.
- **FR-010**: The elevated access MUST be used exclusively to read memory information (and the minimal application inventory needed to attribute it); the analyzer MUST NOT use it to modify the device, its settings, or any application.
- **FR-011**: This feature MUST NOT add any new permission to the analyzer's own install-time permission surface; access is governed by the user's authorization inside Shizuku.
- **FR-012**: The Home screen entries, the Settings screen, the footer, and every other existing behavior MUST remain unchanged by this feature; elevated access serves only the Details screen's per-application memory figures.

### Key Entities

- **Shizuku State**: the current condition of the elevated-access service as presented to the user — Not installed, Not running, Awaiting authorization, or Authorized — each with the action that advances it one step.
- **Per-Application Memory**: the resident memory the device attributes to one application's running processes — a non-negative amount that may be zero for a not-running application, or not-available when elevated access is absent or the read fails.
- **Authorization**: the user's grant, made and remembered inside Shizuku, that allows the analyzer to read through it; the analyzer holds no credential and only observes whether the grant exists.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: With Shizuku set up and authorized, after a refresh every application on the Details screen shows a memory figure equal to the device's own per-application memory report (within display rounding) or the not-available indication — never a wrong number — verified against that report.
- **SC-002**: In each unauthorized state, a user of Feature 004 experiences unchanged behavior for everything except the memory figures (not-available) plus one clearly visible guidance element naming the state and its next action.
- **SC-003**: Starting from Shizuku installed and running, the path to displayed figures takes at most two actions inside the analyzer plus Shizuku's own confirmation, with no analyzer restart at any point.
- **SC-004**: Across at least three stop/revoke and restart/re-grant cycles performed while the analyzer stays open, the analyzer never crashes or freezes, figures revert to not-available and return to correct values per FR-007, and the guidance always names the current state.
- **SC-005**: On a device with 200+ installed applications, a full Details refresh including per-app memory figures completes within 10 seconds with the interface remaining responsive throughout (no frozen or blocked screen).
- **SC-006**: The analyzer's install-time permission surface is byte-for-byte unchanged by this feature.

## Assumptions

- Shizuku (open source, available on F-Droid) is the user-chosen elevated-access
  mechanism; obtaining, installing, starting, and keeping Shizuku running are the
  user's actions in Shizuku's own application. The analyzer never starts or updates
  Shizuku programmatically (also a battery-and-background-work decision, Constitution
  IX).
- "Per-app memory" means the total proportional-set-size-style resident memory the
  device itself attributes to the application's running processes — the same notion
  the device's built-in memory report shows. One figure per application, existing
  formatting; no native/cache/kernel breakdown is offered in this feature.
- An installed application with no running processes shows zero as a truthful value,
  not the not-available indication; the not-available indication is reserved for
  reads that cannot be made or trusted.
- The Details screen's existing memory slot and not-available behavior (Feature 004,
  FR-007) is exactly the slot this feature fills; the authorization guidance reuses
  Feature 004's explanation-plus-path pattern (FR-008) rather than inventing a new
  surface, and lives on the Details screen only — no Settings addition in this
  feature.
- Authorization is remembered by Shizuku (so it survives analyzer restarts); the
  analyzer stores nothing about it and simply re-checks the state whenever it needs
  to read or display it.
- Requesting authorization is a deliberate user action in the analyzer's guidance
  (not an automatic prompt on every screen entry), to avoid repeated dialogs for
  users who do not want the feature.
- A package-visibility declaration for the Shizuku application may be needed for the
  analyzer to see whether it is installed; that is not a permission, does not grant
  anything, and will be reviewed in the feature plan (Constitution VIII).
- Within the isolated development VM, full Shizuku-based verification on a real
  device is unavailable; the memory source is therefore designed so its logic is
  verifiable with simulated sources in unit tests, with emulator-based manual checks
  where the environment allows (Constitution IV).
- The supported Android window stays as defined by Constitution VI; Shizuku's own
  support window covers it, and no compatibility work for older Android is in scope.
