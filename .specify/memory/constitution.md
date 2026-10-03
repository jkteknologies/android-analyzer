# Android Analyzer Constitution

## Core Principles

### I. Spec-Driven Development (NON-NEGOTIABLE)

All development MUST go through the speckit framework workflow. Every feature starts as a
specification (`/speckit-specify`), proceeds through planning (`/speckit-plan`) and task
decomposition (`/speckit-tasks`) before implementation (`/speckit-implement`). No feature
code may be written without a corresponding spec and task list. Ad-hoc or undocumented
features MUST NOT be introduced.

**Rationale**: The spec artifacts are the single source of truth for intent; they keep
development in the VM reproducible and reviewable, and they enable consistency checks
before any code reaches the user.

### II. Feature Branch Workflow

All development MUST be performed on feature branches. The main integration branch is
named `master`. Feature work MUST NOT be committed directly to `master`; changes reach
`master` only after the full speckit workflow and quality gates have passed. Branch names
SHOULD be derived from the feature identifier created by the speckit workflow.

**Rationale**: Feature branches keep `master` shippable and make every change traceable
to a specification.

### III. Host-Only Remote Push (NON-NEGOTIABLE)

The virtual machine used for development MUST NOT push to the remote git repository.
Pushing to the remote is permitted exclusively from the host machine. The VM's git
remotes and credentials MUST be configured so remote write operations fail by design.
This restriction MUST NOT be bypassed, and must never be "fixed" by adding credentials
or network routes to the VM.

**Rationale**: This is an explicit security boundary configured by the user. Keeping
remote write access off the development VM limits the blast radius of any compromise or
mistake made during development.

### IV. Isolated VM Development Environment

Development happens inside a virtual machine that has no access to real production
servers and no real test hardware. Code MUST be written so it can be built, tested, and
verified entirely within this environment: unit tests, emulators, and static analysis
only. Features that assume physical devices, production endpoints, or privileged
hardware MUST provide a local, simulated path (mocks, fakes, emulator profiles) so the
workflow is never blocked on unavailable infrastructure. Agents MUST NOT attempt to
discover or connect to production systems.

**Rationale**: Accepting the isolation constraint up front avoids designs that cannot be
validated locally and prevents accidental coupling to environments the VM cannot reach.

### V. Local Quality Gates (NON-NEGOTIABLE)

Every time code is generated and is ready to be suggested to the user, unit tests and
linters MUST be executed locally first. Suggested code that has not passed the local
unit test suite and linters is non-compliant. Failures MUST be fixed (or explicitly
reported with a rationale if unfixable in the current task) before the change is
presented as complete.

**Rationale**: Because there is no CI feedback loop or test hardware available from the
VM, local verification is the only quality signal; it substitutes for the missing
hardware and remote infrastructure.

### VI. Modern Android-Only Targeting

The application targets modern Android only. Supported versions are limited to roughly
the last 3–4 major Android OS releases; older versions MUST NOT be supported and no
compatibility shims for them SHOULD be added. The project MAY adopt modern Android APIs,
Jetpack libraries, and language features without maintaining legacy fallbacks, provided
the minimum supported API level stays within the supported window and is adjusted
forward over time.

**Rationale**: Dropping legacy Android reduces code complexity, avoids untestable code
paths in the VM environment, and keeps the dependency surface small.

### VII. Modern Stack, Minimal Dependencies

The project MUST use the newest stable technology stack, frameworks, and libraries
practicable. Dependencies MUST be kept to a minimum: no library may be added unless it
provides substantial, non-trivial functionality. If only a limited subset of a library's
functionality is required, the preferred solution is to re-implement that functionality
in the project instead of taking on the third-party dependency. Every new dependency
MUST be justified in the feature plan.

**Rationale**: A lean, modern stack minimizes security exposure, eases F-Droid
compliance (fewer non-free or bundled components), and keeps builds fast and auditable
in the isolated VM.

### VIII. F-Droid-First Distribution

The application MUST be suitable for distribution via F-Droid; Google Play is not a
distribution target. The app MUST NOT require proprietary dependencies, must request as
few permissions as possible, and any permission MUST be justified by a user-visible
feature. Advertising, tracking, and non-free network services MUST NOT be included.
New features MUST be evaluated for their permission and F-Droid-compliance impact.

**Rationale**: F-Droid's inclusion policy and its users' expectations (few permissions,
no trackers, free-software builds) are hard product constraints, not style preferences.

### IX. Efficiency and Security

The application MUST be as efficient as possible: it MUST NOT drain battery, and MUST
NOT consume excessive device resources (CPU, memory, storage, network). Background work
MUST be minimized and batched using modern platform mechanisms (e.g., WorkManager,
scheduler-aware jobs). At the same time, the app is expected to be secure: data at rest
and in transit MUST be protected, and security MUST NOT be traded away for performance.
Designs MUST document their battery/resource impact and security considerations.

**Rationale**: As an analyzer of resource consumption, the app must itself be an example
of good resource behavior; an insecure or wasteful analyzer would be self-refuting.

## Additional Constraints

- **Environment**: All builds, tests, and lint runs run locally in the development VM.
  There are no production servers and no physical test devices; emulator and unit-test
  verification is the accepted standard of evidence.
- **Git topology**: `master` is the only long-lived branch. Feature branches created by
  the speckit workflow are the only allowed working branches. Remote push happens only
  from the host (see Principle III).
- **Android support window**: The minimum supported Android version MUST stay within
  the last 3–4 major OS releases and is reviewed when new Android versions ship.
- **Dependency policy**: Each third-party dependency must appear in the feature plan
  with its justification; "nice to have" dependencies are rejected in favor of local
  re-implementation.
- **Permissions**: The AndroidManifest permission list is part of the reviewed surface
  of every feature; any added permission must map to a documented user-facing need.

## Development Workflow

1. Start a feature with the speckit workflow (`/speckit-specify` → `/speckit-plan` →
   `/speckit-tasks`), working on a feature branch.
2. Implement tasks incrementally; after each code change that is ready to be suggested,
   run the unit test suite and linters locally (Principle V) before presenting results.
3. Keep the plan updated with any dependency additions, permission changes, or
   performance-relevant decisions so they can be reviewed against Principles
   VI–VIII.
4. Finalize the feature with all quality gates green; the host operator performs the
   remote push from the host machine (never from the VM).
5. Any deviation from this workflow must be recorded in the feature plan with a
   rationale.

## Governance

- This constitution supersedes all other project practices and conventions; where other
  documents conflict with it, the constitution wins.
- **Amendments**: Changes to this constitution MUST be documented in the file's Sync
  Impact Report, use ISO dates, and follow semantic versioning:
  - MAJOR: removal or redefinition of a principle (backward-incompatible governance).
  - MINOR: new principle or materially expanded guidance.
  - PATCH: clarifications and non-semantic refinements.
- **Compliance review**: Every feature plan and every code-review pass MUST verify
  compliance with Core Principles I–IX. Violations block completion of the feature.
- **Complexity**: Any added complexity (dependency, permission, background work) must be
  justified against Principles VII, VIII, and IX.
- Runtime development guidance lives in the speckit plan artifacts; this file defines
  only the non-negotiable rules.

**Version**: 1.0.0 | **Ratified**: 2026-10-03 | **Last Amended**: 2026-10-03
