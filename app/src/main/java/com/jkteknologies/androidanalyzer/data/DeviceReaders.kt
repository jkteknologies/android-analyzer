package com.jkteknologies.androidanalyzer.data

import com.jkteknologies.androidanalyzer.domain.ApplicationInventory
import com.jkteknologies.androidanalyzer.domain.BatteryReading
import com.jkteknologies.androidanalyzer.domain.CoreCount
import com.jkteknologies.androidanalyzer.domain.MemoryReading
import com.jkteknologies.androidanalyzer.domain.StorageReading
import com.jkteknologies.androidanalyzer.domain.ThemePreference

/**
 * The app's only seam between the UI/state layer and the Android platform
 * (contracts/device-readers.md). Implementations live in `data/`, consumption
 * happens in the home state holder and theme wiring; JVM unit tests replace
 * every reader with fakes (research.md R-14).
 *
 * Common behavioral clauses — they apply to EVERY reader below:
 *
 * 1. **One-shot**: a call performs exactly one platform read and returns. No
 *    caching between calls, no listeners, no receivers left behind (FR-011).
 * 2. **Null = unavailable**: a reader returns `null` when the platform provides
 *    no usable value; any thrown exception is caught by the caller and treated
 *    identically (FR-012). Readers never invent or clamp values.
 * 3. **Main-thread hostile**: calls block (binder / filesystem / package scan)
 *    and MUST be invoked from the background executor (R-09). Signatures are
 *    synchronous by design — threading policy belongs to the caller, keeping
 *    the contract JVM-test-friendly.
 * 4. **Pure mapping**: implementations contain no state, no logging of values,
 *    and no permission requests beyond the declared manifest set (R-01).
 */

/** One `ActivityManager.MemoryInfo` read → [MemoryReading], or `null`. */
fun interface MemoryReader {
    fun read(): MemoryReading?
}

/** One `StatFs` read of the data partition → [StorageReading], or `null`. */
fun interface StorageReader {
    fun read(): StorageReading?
}

/** One sticky `ACTION_BATTERY_CHANGED` read → [BatteryReading], or `null`. */
fun interface BatteryReader {
    fun read(): BatteryReading?
}

/** `Runtime.availableProcessors()` → [CoreCount] (never `null` in practice). */
fun interface CoreCountReader {
    fun read(): CoreCount?
}

/** Full package enumeration → [ApplicationInventory], or `null` (R-01). */
fun interface ApplicationCounter {
    fun count(): ApplicationInventory?
}

/**
 * Persistence for the single theme preference (R-07). Unlike the one-shot
 * readers above this is a stateful store, but it stays equally fakeable.
 */
interface ThemePreferenceStore {
    /** Current persisted preference; missing/corrupt value resolves to [ThemePreference.SYSTEM]. */
    fun load(): ThemePreference

    /** Persists immediately (apply()); idempotent. */
    fun save(preference: ThemePreference)
}
