# Contract: Placeholder UI & Permission Surface

**Branch**: `001-android-app-skeleton` (work branch: `feature/001-init`) | **Date**: 2026-10-03
**Governs**: FR-002, FR-003, FR-009, FR-010 · US1 · SC-005, SC-006
**Decision source**: [research.md](../research.md) R-04 (Compose-first), R-07 (BOM/material3), R-11 (applicationId/label), R-12 (SDK 35)

The user-visible behavior of the launchable skeleton, fixed so implementation and
future review have one authoritative description.

## Composition Structure

```
MainActivity (sole Activity, exported, launcher intent)
└── setContent { }                       — activity-compose (R-05 pin 1.13.0)
    └── MaterialTheme (Material 3 bridge theme)
        └── PlaceholderScreen             — composable in ui/ package
            └── Surface (fills entire screen)
                └── Text("Android Analyzer")  — string resource @string/app_name
```

Exactly one declared component in `AndroidManifest.xml`; no `ViewModel`, no fragments,
no XML layouts beyond the minimal theme bridge.

## Observable Behavior

| ID | Guarantee | Verification |
|---|---|---|
| U-1 | Launching the app shows a full-screen placeholder with the text exactly **"Android Analyzer"** (FR-002, spec clarification 2026-10-03) | Manual/emulator launch; string sourced from `res/values/strings.xml` `app_name` |
| U-2 | Cold launch to first frame ≤ **5 s** on an API 35 emulator (SC-005) | Manual timing or `adb shell am start -W` |
| U-3 | App installs and runs on Android 15 (API 35)+; `minSdk = targetSdk = compileSdk = 35`, no compat shims (FR-003) | Install on API 35 emulator; build config matches [data-model.md](../data-model.md) S-1 |
| U-4 | App requests **zero permissions** — no `INTERNET`, nothing (FR-009, SC-006) | `aapt2 dump permissions` on built APK = empty; lint clean |
| U-5 | App performs no network access, background work, or work scheduling (FR-010) | Manifest inspection; no worker/receiver/service declared |
| U-6 | Rotation/process-death shows the identical static placeholder (stateless screen) | Manual rotation; no state to lose |

## Package Surface

| Element | Path (under `app/src/main/java/com/jkteknologies/androidanalyzer/`) | Notes |
|---|---|---|
| `MainActivity` | `MainActivity.kt` | Sole entry point; hosts `setContent` |
| `PlaceholderScreen` | `ui/PlaceholderScreen.kt` | Stateless composable; takes no parameters |
| `app_name` | `res/values/strings.xml` | Exactly "Android Analyzer" (FR-002) |
| Theme bridge | `res/values/themes.xml` + `res/mipmap-anydpi-v26/` | Minimal Material 3 bridge; launcher icon |

Nothing else is in scope. Analyzer functionality, theming beyond the bridge, and
localization are explicitly out of scope (spec).
