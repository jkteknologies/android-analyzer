# Contract: App Inventory Readers & Stores (internal Kotlin seam)

**Feature**: `004-per-app-info` | **Input**: [spec.md](../spec.md) · [research.md](../research.md)

The seam this feature adds to the app's only platform boundary (the 002
[device-readers.md](../../002-home-screen/contracts/device-readers.md) family). All
interfaces live in `data/DeviceReaders.kt`, implementations in
`data/AndroidDeviceReaders.kt` / `data/SharedPreferencesRefreshModeStore.kt`, and JVM
unit tests replace every one with fakes. The 002 common clauses apply verbatim to every
reader below: **one-shot**, **null = unavailable**, **main-thread hostile**,
**pure mapping**.

---

## New interfaces

```kotlin
// data/DeviceReaders.kt (added to the existing file)

fun interface InstalledAppReader {
    /** One full enumeration → AppInventory (sorted, marked), or null. */
    fun read(): AppInventory?
}

fun interface CoreTierReader {
    /** One sysfs grouping (with total-count fallback) → CoreTiers, or null. */
    fun read(): CoreTiers?
}

fun interface UsageAccessStatus {
    /** One AppOps check → is usage access currently granted? */
    fun granted(): Boolean
}

interface RefreshModeStore {
    /** Current persisted mode; missing/corrupt value resolves to ON_DEMAND. */
    fun load(): RefreshMode
    /** Persists immediately (apply()); idempotent. */
    fun save(mode: RefreshMode)
}
```

## Behavioral clauses

1. **Single-pass figures** (InstalledAppReader): one `read()` call performs one package
   enumeration, one usage-access check, and — only when access is granted — one
   `StorageStatsManager` query per package, filling every `storageBytes` from the same
   snapshot. When access is not granted, no per-package storage call is made and every
   `storageBytes` is `null` (R-02, FR-006/FR-008). `memoryBytes` is `null` always
   (R-03).
2. **Shared classification**: the system/user mark and the home counts classify through
   the same `isSystemApplication` bit test — never a second rule (FR-011/FR-012, SC-001).
3. **Fallback inside the tier reader** (CoreTierReader): a failed or
   single-frequency sysfs walk yields single-tier `CoreTiers(total)` from
   `Runtime.availableProcessors()`; `null` only if even that fails (FR-013, R-04).
4. **Sorting in the domain**: the reader hands the domain factory unsorted entries;
   `AppInventory.create` owns the Collator ordering (V-A2) — readers stay pure mappings.
5. **Store clauses** (RefreshModeStore): same shape as `ThemePreferenceStore` —
   `load()` delegates corrupt values to `fromPersisted`, `save()` uses `apply()`, one
   string key `refresh_mode` in the existing `android_analyzer` prefs file (R-09).
6. **No grant requests from the seam**: readers and stores never launch intents or
   dialogs; the usage-access grant path is UI wiring
   ([details-screen.md](./details-screen.md) W-4, FR-008).

## JVM-test obligations

Every interface is fakeable with a lambda/object; the suite asserts clause 1 (grant
state ↔ null storageBytes coupling, V-D6), clause 3 (fallback shape), clause 4
(ordering), and clause 5 (default/corrupt mapping) without any Android class on the
test classpath.
