# Feature Specification: Home Screen

**Feature Branch**: `002-home-screen`

**Created**: 2026-10-04

**Status**: Draft

**Input**: User description: "The home screen should show following information: amount of resources available on a device; amount of allocated resources; number of installed applications (non-system apps), and in brackets the total amount of applications including system apps. The application must follow system theme, e.g. dark or white. There must be a settings button in a footer; the only setting available right now would be to change theme: white / dark / system theme. The other button in a footer (placed to the left of the settings button) is 'home screen'."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Device Resource and App Overview (Priority: P1)

As a device owner, I open Android Analyzer and immediately see a broad overview of my
device — how much memory is available and allocated, how much internal storage is free
and used, the battery level and charging state, the processor core count — plus how
many applications are installed on the device, so that I get an at-a-glance summary of
my device without any setup or configuration.

**Why this priority**: This is the core value of the product — the home screen's
information display is the reason the application exists; every other part of this
feature (theming, navigation, settings) exists to support presenting it.

**Independent Test**: Can be fully tested by launching the application on a device or
emulator with a known resource state (memory, internal storage, battery, processor
cores) and a known set of installed applications, and comparing the displayed figures
against the device-reported values. Delivers a working analyzer home screen on its own.

**Acceptance Scenarios**:

1. **Given** the application is launched, **When** the home screen is presented, **Then** it displays the device memory amounts (available and allocated), the internal storage amounts (free and used), the battery level and charging state, and the processor core count, each in human-readable form.
2. **Given** the memory figures available A and allocated B, and the storage figures free F and used U, are displayed, **Then** A + B does not exceed the device's total memory, F + U does not exceed the total internal storage, and no figure is negative.
3. **Given** a device with applications installed, **When** the home screen is presented, **Then** the installed-applications figure shows the number of non-system applications followed by the total number of applications including system applications in brackets (for example, "36 (121)").
4. **Given** a device with no non-system applications installed (for example, a fresh emulator), **When** the home screen is presented, **Then** the figure displays "0" followed by the total count in brackets.

---

### User Story 2 - System Theme Following (Priority: P2)

As a user, I want the application to match my device's dark or light theme
automatically, so that it feels native and is comfortable to read with zero
configuration on my part.

**Why this priority**: Appearance defaults shape the first impression of every launch,
but they present information that already exists — they do not unlock new capability,
so they rank below the data display.

**Independent Test**: Can be fully tested by switching the device between dark and
light modes and observing the application's rendering with the default theme setting.
Delivers a native-feeling appearance with no user action required.

**Acceptance Scenarios**:

1. **Given** the device is in dark mode and the application uses its default theme setting, **When** the application is opened, **Then** its interface renders in the dark theme.
2. **Given** the device is in light mode and the application uses its default theme setting, **When** the application is opened, **Then** its interface renders in the light theme.
3. **Given** the application is open in its default theme setting, **When** the device theme switches between dark and light, **Then** the application follows the switch without requiring a restart.

---

### User Story 3 - Footer Navigation to Settings (Priority: P2)

As a user, I can move between the home screen and the application's settings using two
persistent footer buttons — "Home screen" on the left and "Settings" on the right — so
that I can always reach the app's controls with one tap.

**Why this priority**: Navigation makes the settings reachable and structures the app
for every future screen; it is infrastructure for the settings story and later
features, so it shares the settings tier but precedes it.

**Independent Test**: Can be fully tested by tapping each footer button on each screen
and observing the presented screen and the footer's indication of the current
destination. Delivers a two-screen application shell.

**Acceptance Scenarios**:

1. **Given** the home screen is presented, **When** the footer is inspected, **Then** it contains exactly two buttons — "Home screen" on the left and "Settings" on the right — with the home screen indicated as the current destination.
2. **Given** the home screen is presented, **When** the "Settings" button is tapped, **Then** the settings screen is presented and indicated as the current destination.
3. **Given** the settings screen is presented, **When** the "Home screen" button is tapped, **Then** the home screen is presented again with its figures reflecting the device state at the time of display.

---

### User Story 4 - Manual Theme Selection (Priority: P3)

As a user, I can override the automatic appearance by choosing a theme — light, dark,
or system default — in the settings, so that I control how the application looks
regardless of my device's theme setting.

**Why this priority**: An explicit preference is a refinement of behavior the
application already provides by default; the default already satisfies most users.

**Independent Test**: Can be fully tested by selecting each theme option and observing
the immediate application-wide appearance change, then restarting the application and
observing the persisted selection. Delivers user-controlled appearance.

**Acceptance Scenarios**:

1. **Given** the settings screen is opened for the first time, **When** the theme setting is inspected, **Then** it is the application's only setting and offers exactly three options — light, dark, and system default — with system default selected.
2. **Given** the settings screen is open, **When** the user selects light or dark, **Then** the new theme applies immediately across the application without a restart.
3. **Given** a manual light or dark selection is in effect, **When** the user selects system default, **Then** the application returns to following the device's system theme.
4. **Given** a theme selection has been made, **When** the application is closed and reopened, **Then** the selected theme is still in effect.

### Edge Cases

- What happens when a resource figure cannot be read or the device reports an impossible value? The home screen MUST show a distinct unavailability indication for that figure rather than an invented value, and MUST NOT crash.
- What happens on a device with almost no available resources? The figures must still render correctly, remain non-negative, and not overlap or break the layout.
- What happens when the device's theme changes while the application is open (system default mode)? The application follows without restart; if it was in the background, the correct theme is present on the next resume.
- What happens when the user rapidly switches between the home and settings screens, or between theme options? No crash, no flickering intermediate states, and the final presented screen and theme are consistent with the last user action.
- What happens on a device with a very large number of installed applications? Counts display in full with locale-appropriate number formatting.
- What happens when the system's largest font scale (accessibility setting) is active? All figures, the footer, and the settings screen remain readable without clipped or overlapping content (FR-015).
- What does the settings screen show besides the theme? Nothing — it contains exactly the theme setting; no placeholder or "coming soon" sections.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The home screen MUST display, whenever it is presented, a device resource overview in human-readable form covering: device memory (available and allocated amounts), internal storage (free and used amounts), battery (level and charging state), and processor (core count).
- **FR-002**: Paired figures (memory available + allocated; storage free + used) MUST be derived from a single reading of device state, MUST NOT together exceed the respective device total, and MUST NOT be negative.
- **FR-003**: The resource overview MUST cover exactly four resource kinds — device memory, internal storage, battery, and processor core count. CPU utilization and per-core detail, external/removable storage, and battery health metrics beyond level and charging state are out of scope for this feature.
- **FR-004**: The home screen MUST display the count of installed non-system applications followed, in brackets, by the total count of applications including system applications.
- **FR-005**: The application MUST distinguish system applications from non-system applications without any user action or manual input. An application that was preinstalled on the device MUST remain classified as a system application even if the user has updated it.
- **FR-006**: By default, the application MUST follow the device's current system theme (dark or light) with no user configuration.
- **FR-007**: The application MUST present a persistent footer containing exactly two buttons — "Home screen" positioned on the left and "Settings" positioned on the right — on every screen of this feature.
- **FR-008**: The footer MUST visually indicate which screen is currently presented.
- **FR-009**: The settings screen MUST contain exactly one setting — theme — offering exactly three options: light, dark, and system default, with system default as the initial value.
- **FR-010**: A theme selection MUST take effect immediately across the application and MUST persist across application restarts.
- **FR-011**: Resource and application figures MUST be read on demand every time the home screen is presented to the user — on application launch, on return to the home screen via in-app navigation, and on resume from the background; reads occur only while the home screen is visible. This feature MUST NOT introduce background monitoring, periodic polling, or network activity (Constitution Principles VIII–IX).
- **FR-012**: When a figure cannot be read, the home screen MUST show a distinct unavailability indication for that figure instead of an invented value, and MUST NOT crash.
- **FR-013**: This feature MUST NOT add any permission to the application unless strictly required to enumerate installed applications; any such permission MUST be justified in the feature plan (Constitution Principle VIII).
- **FR-014**: While a figure is being read, the home screen MUST display a neutral per-figure placeholder; each placeholder MUST be replaced by the figure's value — or by its unavailability indication (FR-012) — once that read completes. The home screen layout MUST render without waiting for any read to finish.
- **FR-015**: Every figure, footer button, and theme option MUST be operable and understandable via the platform's screen reader — figures expose name-and-value semantics, and the unavailability indication is announced distinctly from a value. All screens of this feature MUST remain free of clipped or overlapping content at the system's largest font scale.

### Key Entities

- **ResourceSummary**: A snapshot of device resource state at one point in time, covering device memory (available, allocated, total), internal storage (free, used, total), battery (level, charging state), and processor (core count).
- **ApplicationInventory**: A count of installed applications split into non-system count and total count (system count is the difference). Applications preinstalled on the device — including those the user has since updated — belong to the system count.
- **ThemePreference**: The user's persisted theme choice — one of light, dark, or system default — stored locally on the device.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: On a modern Android device or emulator, the home screen presents all figures within 2 seconds of application launch.
- **SC-002**: The displayed resource figures match the device-reported state at display time, and paired figures (memory, storage) never sum above their device totals.
- **SC-003**: The displayed application counts match the device's actual application inventory, verifiable against the system's application list.
- **SC-004**: In system default mode, a device theme switch is reflected in the application without restart — both while the app is in the foreground and on its next resume from the background.
- **SC-005**: Manual theme selections apply immediately (visible within 1 second) and persist across restarts in 100% of test repetitions.
- **SC-006**: With this feature added, the application performs no background work or network activity, and its permission count increases by at most the single application-enumeration permission, if one proves strictly required.

## Assumptions

- **Resource scope (resolved)**: "Resources" covers device memory (RAM: available, allocated, total), internal storage (free, used, total), battery (level and charging state as reported by the device), and processor core count. CPU utilization, external storage, and battery health metrics are excluded (FR-003).
- **Allocated meaning**: "Allocated" is device-wide usage by the system plus all applications, not this application's own usage; storage "used" covers the internal storage consumed by the system, applications, and user data.
- **System-app classification (resolved)**: An application preinstalled on the device counts as a system application even if the user has updated it; only applications the user installed themselves count as non-system (FR-005).
- **Battery verification**: Emulators report a simulated battery; verification compares displayed values against the device-reported ones, and FR-012's unavailability indication applies where a figure is not reported at all.
- **Snapshot behavior (resolved)**: All figures are read as a fresh one-shot snapshot each time the home screen is presented — on launch, on in-app return, and on resume from the background; continuous live updating and manual refresh controls are out of scope for this feature.
- **Formatting**: Human-readable units with locale-aware number formatting; user-interface strings are English at this stage.
- **Theme naming**: The three options are presented as Light ("white"), Dark, and System default ("system theme"); system default is the out-of-the-box value.
- **Footer scope**: The footer is present on both the home screen and the settings screen.
- **App enumeration access**: Enumerating all installed applications, including system ones, may require package-visibility or permission configuration; whether a permission is added is decided and justified in `/speckit-plan` per Constitution Principle VIII.
- **Platform window**: Minimum supported Android remains Android 15 (API 35) as established in feature 001 and Constitution Principle VI.
- **Out of scope**: Per-application details or drill-down, resource history and charts, any additional settings, home-screen widgets, and localization beyond English.

## Clarifications

### Session 2026-10-04

- Q: Which resources does "amount of resources available on a device" cover — device memory only, memory plus internal storage, or a broader set? → A: **Broad overview** — device memory (available/allocated), internal storage (free/used), battery (level and charging state), and processor core count.

### Session 2026-10-05

- Q: When a preinstalled system app has been updated by the user (e.g., via a store), how should it be counted in the non-system vs. system application split? → A: **Count as system** — updated system applications remain system applications; the non-system count covers only user-installed applications.
- Q: While the home screen figures are still being read at launch, what should be displayed in place of each figure? → A: **Per-figure placeholder** — each figure shows a neutral placeholder that is replaced by its value (or its unavailability indication) as soon as its read completes; the screen is never blocked.
- Q: When the app returns to the foreground from the background, should the home screen figures be read again? → A: **Refresh on every view** — fresh one-shot reads on launch, on in-app return, and on resume from the background; no background work or polling.
- Q: Should basic accessibility support be in scope for this feature's screens? → A: **Include basics now** — screen-reader semantics for every figure, footer button, and theme option, plus correct layout at the system's largest font scale (FR-015).
