# Data Model: 001-android-app-skeleton

**Branch**: `001-android-app-skeleton` (work branch: `feature/001-init`) | **Date**: 2026-10-03 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

## Domain Entities

**Not applicable.** Per the spec ("Key Entities: Not applicable — the skeleton involves no
persistent data"), this feature introduces **no domain entities, no persistence, and no
state transitions**. Entity modeling is explicitly deferred to future Analyzer features.
There is no database, no DataStore/SharedPreferences, no files written by the app, and no
network payloads to model (FR-009/FR-010).

## Skeleton Surface Model

Although there are no domain entities, the skeleton still has a small, verifiable
configuration surface. It is documented here so `/speckit-tasks` and implementation have
one authoritative reference; none of these are runtime data.

### S-1: Gradle build configuration (static, repo-owned)

| Field | Value | Source | Governs |
|---|---|---|---|
| `applicationId` | `com.jkteknologies.androidanalyzer` (provisional) | research.md R-11 | Installable package identity (US1) |
| `minSdk` | `35` | spec clarification 2026-10-03, R-12 | FR-003 minimum supported version |
| `targetSdk` | `35` | R-12 | Runtime behavior pin |
| `compileSdk` | `35` | R-12 | FR-003, no compat shims |
| `compileOptions` / Kotlin `jvmTarget` | `21` (JDK 21 LTS) | R-03 | Toolchain alignment |
| `versionName` | `0.1.0` (provisional skeleton version) | convention | Display version only |
| `versionCode` | `1` | convention | Install/upgrade bookkeeping |

**Validation rules**: values must match research.md pins exactly (single source of truth:
`gradle/libs.versions.toml` for library/plugin versions; `app/build.gradle.kts` for SDK
levels). Any drift fails Constitution VII review.

### S-2: Manifest declaration (static, repo-owned)

| Field | Value | Governs |
|---|---|---|
| `<uses-permission>` elements | **none** (zero) | FR-009, SC-006 |
| Declared components | exactly one `<activity>` (`MainActivity`), `exported="true"`, launcher intent filter | US1 launchability |
| `android:label` | `@string/app_name` = "Android Analyzer" | FR-002 |
| Network config | none (no `INTERNET` permission implied anywhere) | FR-010 |

**Validation rule**: `aapt2 dump permissions app-debug.apk` (or Android Studio App
Inspection) MUST report zero permissions; lint's default `MissingPermission`-family and
the manifest merger report zero `<uses-permission>` nodes.

### S-3: UI state (runtime, trivially small)

`PlaceholderScreen` is a **stateless** composable:

```mermaid
erDiagram
    MainActivity ||--|| PlaceholderScreen : "setContent { }"
    PlaceholderScreen ||--|| "Material 3 Surface" : "renders"
    "Material 3 Surface" ||--|| "Text(\"Android Analyzer\")" : "displays app_name"
```

- **States**: exactly one — the static placeholder. No `ViewModel`, no `StateFlow`, no
  saved-instance state, no configuration-change handling beyond Compose defaults.
- **Transitions**: none. The screen is identical on every launch.
- **Invariants**: text shown is the string resource `app_name` ("Android Analyzer",
  FR-002); first frame within 5 s of cold launch on an API 35 emulator (SC-005); no
  side effects (no network, no work scheduling — FR-010).

### S-4: Test subject surface (device-free, FR-004)

| Subject | Kind | What is verifiable on the JVM |
|---|---|---|
| `PlaceholderScreenTest` | JUnit 4 unit test (`src/test`) | Skeleton's verifiable logic: string-resource constants, pure helper functions extracted from the screen if any. UI rendering itself is **not** asserted (no Robolectric/instrumentation — FR-004, R-08). |

**Validation rule**: `./gradlew testDebugUnitTest` passes with ≥ 1 test; failures name
class/method (US2 scenario 2). Tests must not touch Android framework classes that
require a device (framework `android.*` stubs throw `RuntimeException` on the JVM by
design — tests stay above that line).

## Relationships to Contracts

The observable behavior implied by S-2/S-3 is formalized as user-facing contracts in
[contracts/](./contracts/):

- [`contracts/verification.md`](./contracts/verification.md) — CLI contract for
  `scripts/verify.sh` (FR-006) and the CI workflow (FR-007/FR-008).
- [`contracts/ui-placeholder.md`](./contracts/ui-placeholder.md) — user-visible UI and
  permission-surface contract for US1/FR-002/FR-009.
