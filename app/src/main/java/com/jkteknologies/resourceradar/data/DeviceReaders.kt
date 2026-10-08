package com.jkteknologies.resourceradar.data

import com.jkteknologies.resourceradar.domain.AppInventory
import com.jkteknologies.resourceradar.domain.ApplicationInventory
import com.jkteknologies.resourceradar.domain.BatteryReading
import com.jkteknologies.resourceradar.domain.CoreCount
import com.jkteknologies.resourceradar.domain.CoreTiers
import com.jkteknologies.resourceradar.domain.MemoryReading
import com.jkteknologies.resourceradar.domain.ShizukuAccessState
import com.jkteknologies.resourceradar.domain.StorageReading
import com.jkteknologies.resourceradar.domain.RefreshMode
import com.jkteknologies.resourceradar.domain.ThemePreference

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

/**
 * One sysfs cpufreq grouping (with total-count fallback) → the 004
 * [CoreTiers], or `null` (R-04).
 */
fun interface CoreTierReader {
    fun read(): CoreTiers?
}

/** Full package enumeration → [ApplicationInventory], or `null` (R-01). */
fun interface ApplicationCounter {
    fun count(): ApplicationInventory?
}

/**
 * Full package enumeration → the 004 domain [AppInventory] (sorted, marked),
 * or `null` (004 R-01). One call = one enumeration, one usage-access check,
 * and — only while access is granted — one `StorageStatsManager` query per
 * package (contract app-inventory.md clause 1).
 */
fun interface InstalledAppReader {
    fun read(): AppInventory?
}

/**
 * One AppOps usage-access check (004 R-02): is the `PACKAGE_USAGE_STATS`
 * appop currently granted to this app? The seam never requests the grant —
 * the Settings page is UI wiring (clause 6).
 */
fun interface UsageAccessStatus {
    fun granted(): Boolean
}

/**
 * One batched privileged read (005 contracts/shizuku-memory.md clause 1–2) →
 * the aggregated snapshot (package → summed PSS bytes; an absent key is an
 * installed-but-not-running app; a `null` value is a per-app read failure),
 * or `null` when the whole pass is unavailable (not authorized / binder dead
 * / timeout / failure). One-shot per the 002 common clauses, with the
 * documented stateful-connection exception living in the implementation
 * (clause 6), not in this signature.
 */
fun interface AppMemoryReader {
    fun read(): Map<String, Long?>?
}

/**
 * One Shizuku state-ladder check (005 R-03): installed-check → binder ping →
 * version floor → permission check, with every library throw collapsing to
 * `NOT_RUNNING`. **Never throws** (contract clause 3); may do binder work, so
 * it belongs on the background executor like the readers.
 */
fun interface ShizukuAccessStatus {
    fun state(): ShizukuAccessState
}

/**
 * Fire-and-forget authorization request (005 FR-005): Shizuku shows **its
 * own** dialog; the result never appears as a return value — it arrives as a
 * change-source event, and the actual grant state is re-read from
 * [ShizukuAccessStatus]. Call from the UI thread.
 */
fun interface ShizukuAuthorizer {
    fun request()
}

/**
 * Shizuku change notifications (005 R-04, contract clause 7): binder
 * received (sticky — the registration callback may fire immediately), binder
 * dead, and permission-request results, collapsed into one `onChange`
 * (already dispatched on the main thread by the library). The returned
 * lambda unregisters everything.
 */
fun interface ShizukuChangeSource {
    fun listen(onChange: () -> Unit): () -> Unit
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

/**
 * Persistence for the automatic-refresh selection (004 contract clause 5):
 * the [ThemePreferenceStore] shape mirrored exactly — one string key in the
 * same prefs file, corrupt values resolve to the default via `fromPersisted`.
 */
interface RefreshModeStore {
    /** Current persisted mode; missing/corrupt value resolves to [RefreshMode.ON_DEMAND]. */
    fun load(): RefreshMode

    /** Persists immediately (apply()); idempotent. */
    fun save(mode: RefreshMode)
}
