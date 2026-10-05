# Contract: Device Readers (internal Kotlin seam)

**Feature**: `002-home-screen` | **Input**: [data-model.md](../data-model.md) · [research.md](../research.md)

This is the app's only "API-like" seam: the interface family between the UI/state layer
and the Android platform. Implementation lives in `data/`, consumption in the home state
holder; unit tests replace every reader with fakes (R-14). The app exposes no interfaces
to other apps or systems (no exported components, no content providers — Constitution
VIII).

## Common behavioral clauses (apply to every reader)

1. **One-shot**: a call performs exactly one platform read and returns. No caching
   between calls, no listeners, no receivers left behind (FR-011).
2. **Null = unavailable**: a reader returns `null` when the platform provides no usable
   value; any thrown exception is caught by the caller and treated identically
   (FR-012). Readers never invent or clamp values.
3. **Main-thread hostile**: calls block (binder / filesystem / package scan) and MUST be
   invoked from the background executor (R-09). Signatures are synchronous by design —
   threading policy belongs to the caller, keeping the contract JVM-test-friendly.
4. **Pure mapping**: implementations contain no state, no logging of values, and no
   permission requests beyond the declared manifest set (R-01).

## Interfaces

```kotlin
// data/DeviceReaders.kt

fun interface MemoryReader {
    /** One ActivityManager.MemoryInfo read → MemoryReading, or null. */
    fun read(): MemoryReading?
}

fun interface StorageReader {
    /** One StatFs read of the data partition → StorageReading, or null. */
    fun read(): StorageReading?
}

fun interface BatteryReader {
    /** One sticky ACTION_BATTERY_CHANGED read → BatteryReading, or null. */
    fun read(): BatteryReading?
}

fun interface CoreCountReader {
    /** Runtime.availableProcessors() → CoreCount (never null in practice). */
    fun read(): CoreCount?
}

fun interface ApplicationCounter {
    /** Full package enumeration → ApplicationInventory, or null (R-01). */
    fun count(): ApplicationInventory?
}

interface ThemePreferenceStore {
    /** Current persisted preference; missing/corrupt value resolves to SYSTEM. */
    fun load(): ThemePreference
    /** Persists immediately (apply()); idempotent. */
    fun save(preference: ThemePreference)
}
```

## Producing implementations (reference for tasks)

| Interface | Platform source | Notes |
|---|---|---|
| `MemoryReader` | `ActivityManager.getMemoryInfo` | `totalMem`, `availMem`; validate §V-4 before returning |
| `StorageReader` | `StatFs(Environment.getDataDirectory())` | `totalBytes`, `availableBytes` |
| `BatteryReader` | `registerReceiver(null, IntentFilter(ACTION_BATTERY_CHANGED))` | level/scale → percent; status → charging |
| `CoreCountReader` | `Runtime.getRuntime().availableProcessors()` | |
| `ApplicationCounter` | `PackageManager.getInstalledApplications(0)` | classify by `FLAG_SYSTEM`; requires `QUERY_ALL_PACKAGES` (R-01) |
| `ThemePreferenceStore` | `SharedPreferences` | single string key (R-07) |

## Consumers

- **Home state holder** (`ui/home/HomeStateHolder.kt`): owns the executor, starts read
  cycles on the FR-011 triggers, maps `null`/exception → `FigureUiState.Unavailable`,
  posts results to main-thread Compose state. It depends only on the interfaces above.
- **Theme application** (`ui/theme/AppTheme.kt` + `MainActivity`): loads the preference
  at creation, saves on change, resolves the effective scheme (data-model §4).

## Fidelity notes

- The reader seam is intentionally synchronous and value-returning — it must stay
  trivially fakeable (`fun interface` lambdas) for JVM tests; do not add Android types
  to the signatures or the domain models.
- If a future feature needs live monitoring, it gets a *separate* observer contract —
  these one-shot readers must not grow callbacks (FR-011's no-monitoring rule).
